module litecore

go 1.27

require github.com/xtls/xray-core v1.260327.1-0.20261109052709-676eaf66f8f0

// gomobile bind needs x/mobile in this module's dependency graph
// (it is installed with `go install ...@latest`, which does not add
// it). The tool directive keeps `go mod tidy` from dropping it.
require golang.org/x/mobile v0.0.0-20261011034528-197770444867

tool golang.org/x/mobile/cmd/gobind

// Vendored anet: upstream v0.0.5 linknames net.zoneCache, which Go 1.27
// removed from the stdlib (breaks the 32-bit arm link). See
// third_party/anet/README.md.
replace github.com/wlynxg/anet => ./third_party/anet

// 3.2.8 (Auto, Damavand/ZedSecure-faithful): the stock Xray core has
// no "autoselect" outbound — Damavand's Auto runs on CluvexStudio's
// Xray-core fork, which adds proxy/autoselect (measured switching
// with a switch margin, transparent retry, status JSON, pinning) plus
// infra/conf/autoselect.go (the JSON parser). Same module path, so
// the replace only swaps the implementation; pinned to the exact
// commit zedcore-r6 builds (2026-11-02). main/distro/all already
// blank-imports proxy/autoselect, so no code change is needed to
// register the protocol.
replace github.com/xtls/xray-core => github.com/CluvexStudio/Xray-core v0.0.0-20261102141629-7b4c3ceb1534

// The fork's go.mod pins amneziawg-go to a placeholder version
// (v3.0.0-00010101000000-000000000000) plus a RELATIVE replace
// (../../reference/amneziawg-go) that only exists inside ZedSecure's
// monorepo checkout — as a fetched module both are unusable, and Go
// refuses the zero revision. ZedSecure's pins.txt shows the reference
// is plain upstream amneziawg-go @ b5928efb (2026-08-28), so this
// replace points at the same-day upstream tag instead.
replace github.com/amnezia-vpn/amneziawg-go/v3 => github.com/amnezia-vpn/amneziawg-go/v3 v3.1.20260828
