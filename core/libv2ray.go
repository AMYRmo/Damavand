// Package libv2ray is the Damavand Lite core: a thin gomobile wrapper
// around xtls/xray-core, exposing exactly the API the Lite app uses
// (the same API the old 2dust libv2ray exposed, so the Kotlin side is
// unchanged).
//
// Why a custom core: the app used to ship zedcore.aar, a mega-core with
// Xray + sing-box + Psiphon + Tailscale + OpenConnect + OpenVPN + gVisor
// (108 MB .so) while Lite only ever runs Xray. A plain Xray core is
// ~33 MB (the size v2rayNG ships), which is what takes the installed
// app from ~184 MB to under 120 MB.
//
// The wrapper does three things the raw core does not:
//   - initCoreEnv points Xray at the app's asset dir (geo files).
//   - StartLoop injects the stats/policy section the config needs for
//     outbound traffic counters (the app's config builder omits it).
//   - MeasureDelay probes a URL through the running instance (the same
//     core.Dial pattern libXray's ping batch uses).
package libv2ray

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	xrayNet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	featureStats "github.com/xtls/xray-core/features/stats"
	_ "github.com/xtls/xray-core/main/distro/all"
)

// InitCoreEnv points Xray at the directory holding geoip.dat /
// geosite.dat (the app's filesDir/assets). The second argument is the
// config location; the app passes "" (config arrives as a JSON string).
func InitCoreEnv(assetDir string, configDir string) {
	if assetDir != "" {
		_ = os.Setenv("XRAY_LOCATION_ASSET", assetDir)
	}
	if configDir != "" {
		_ = os.Setenv("XRAY_LOCATION_CONFIG", configDir)
	}
}

// CoreCallbackHandler is called by the core on start/stop. The app's
// handler returns 0 for everything; Shutdown is where the app learns
// the core went away.
type CoreCallbackHandler interface {
	OnEmitStatus(status int64, message string) int64
	Startup() int64
	Shutdown() int64
}

// CoreController owns one Xray instance.
type CoreController struct {
	handler      CoreCallbackHandler
	mu           sync.Mutex
	instance     *core.Instance
	running      bool
	outboundTags []string
}

// NewCoreController creates a controller for the given handler.
func NewCoreController(handler CoreCallbackHandler) *CoreController {
	return &CoreController{handler: handler}
}

// StartLoop starts the core from a JSON config (the tun fd is unused:
// Lite runs the core behind a SOCKS inbound and bridges the tun in
// Java, so fd is always 0).
func (c *CoreController) StartLoop(config string, fd int) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.instance != nil {
		_ = c.instance.Close()
		c.instance = nil
		c.running = false
	}
	_ = fd

	config = injectStats(config)
	cfg, err := core.LoadConfig("json", strings.NewReader(config))
	if err != nil {
		return err
	}
	instance, err := core.New(cfg)
	if err != nil {
		return err
	}
	if err := instance.Start(); err != nil {
		_ = instance.Close()
		return err
	}
	c.instance = instance
	c.running = true
	c.outboundTags = outboundTags(config)
	if c.handler != nil {
		c.handler.Startup()
	}
	return nil
}

// StopLoop stops the running instance, if any.
func (c *CoreController) StopLoop() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.instance == nil {
		return nil
	}
	err := c.instance.Close()
	c.instance = nil
	c.running = false
	if c.handler != nil {
		c.handler.Shutdown()
	}
	return err
}

// IsRunning reports whether the core is up.
func (c *CoreController) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running && c.instance != nil && c.instance.IsRunning()
}

// MeasureDelay GETs url through the running instance and returns the
// elapsed milliseconds, or -1 when the probe fails.
func (c *CoreController) MeasureDelay(url string) int64 {
	c.mu.Lock()
	instance := c.instance
	running := c.running
	c.mu.Unlock()
	if !running || instance == nil {
		return -1
	}
	transport := &http.Transport{
		DisableKeepAlives: true,
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			if network != "tcp" && network != "tcp4" && network != "tcp6" {
				return nil, errors.New("unsupported network for delay probe: " + network)
			}
			destination, err := xrayNet.ParseDestination("tcp:" + address)
			if err != nil {
				return nil, err
			}
			return core.Dial(ctx, instance, destination)
		},
	}
	defer transport.CloseIdleConnections()
	client := &http.Client{Transport: transport, Timeout: 10 * time.Second}
	start := time.Now()
	resp, err := client.Get(url)
	if err != nil {
		return -1
	}
	_, _ = io.Copy(io.Discard, resp.Body)
	_ = resp.Body.Close()
	return time.Since(start).Milliseconds()
}

// QueryAllOutboundTrafficStats returns "tag,dir,bytes;" entries for
// every proxy outbound (uplink + downlink), the format the app parses
// for live speeds. Counters that do not exist yet report 0.
func (c *CoreController) QueryAllOutboundTrafficStats() string {
	c.mu.Lock()
	instance := c.instance
	running := c.running
	tags := append([]string(nil), c.outboundTags...)
	c.mu.Unlock()
	if !running || instance == nil {
		return ""
	}
	feature := instance.GetFeature((*featureStats.Manager)(nil))
	if feature == nil {
		return ""
	}
	manager, ok := feature.(featureStats.Manager)
	if !ok {
		return ""
	}
	var b strings.Builder
	for _, tag := range tags {
		up := manager.GetCounter("outbound>>>" + tag + ">>>traffic>>>uplink")
		down := manager.GetCounter("outbound>>>" + tag + ">>>traffic>>>downlink")
		var upVal, downVal int64
		if up != nil {
			upVal = up.Value()
		}
		if down != nil {
			downVal = down.Value()
		}
		b.WriteString(tag)
		b.WriteString(",uplink,")
		b.WriteString(strconv.FormatInt(upVal, 10))
		b.WriteString(";")
		b.WriteString(tag)
		b.WriteString(",downlink,")
		b.WriteString(strconv.FormatInt(downVal, 10))
		b.WriteString(";")
	}
	return b.String()
}

// injectStats adds the stats + policy section the traffic counters
// need. The app's config builder omits it, so the wrapper adds it
// here; a config that fails to parse is returned unchanged.
func injectStats(configJSON string) string {
	var config map[string]interface{}
	if err := json.Unmarshal([]byte(configJSON), &config); err != nil {
		return configJSON
	}
	config["stats"] = map[string]interface{}{}
	config["policy"] = map[string]interface{}{
		// No-user traffic (SOCKS noauth, the Lite tun path) reads the
		// SYSTEM policy, not level 0 — both must enable stats, or the
		// counters exist but never move (proven on the AWS server
		// 2026-11-15: fetch 200 OK, counters stayed 0; with the system
		// policy the same fetch shows uplink 1818 / downlink 6396).
		"system": map[string]interface{}{
			"statsOutboundUplink":   true,
			"statsOutboundDownlink": true,
		},
		"levels": map[string]interface{}{
			"0": map[string]interface{}{
				"statsOutboundUplink":   true,
				"statsOutboundDownlink": true,
			},
		},
	}
	out, err := json.Marshal(config)
	if err != nil {
		return configJSON
	}
	return string(out)
}

// outboundTags lists the proxy outbound tags in the config (the ones
// whose traffic the app displays), skipping the builtins.
func outboundTags(configJSON string) []string {
	var config struct {
		Outbounds []struct {
			Tag string `json:"tag"`
		} `json:"outbounds"`
	}
	if err := json.Unmarshal([]byte(configJSON), &config); err != nil {
		return []string{"proxy"}
	}
	tags := []string{}
	for _, ob := range config.Outbounds {
		if ob.Tag == "" || ob.Tag == "direct" || ob.Tag == "block" {
			continue
		}
		tags = append(tags, ob.Tag)
	}
	if len(tags) == 0 {
		return []string{"proxy"}
	}
	return tags
}
