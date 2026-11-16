# Vendored anet (github.com/wlynxg/anet v0.0.5)

Copied verbatim from anet v0.0.5 (commit 839bc3a), except for one patch
in `interface_android.go`: the `//go:linkname zoneCache net.zoneCache`
declaration and its two `zoneCache.update(...)` calls were removed.

Why: Go 1.27 removed `net.zoneCache` from the stdlib, so gomobile's
link of the 32-bit arm core failed with
`link: github.com/wlynxg/anet: invalid reference to net.zoneCache`
(CI run 43695337904). Upstream anet is unmaintained since 2025-01-09
and has no fix. The stdlib zone cache no longer exists to refresh, so
dropping those updates changes nothing; the `golang.org/x/net`
zone cache (`zoneCacheX`) is kept and still works.

Wired in via a `replace` directive in `core/go.mod`:
`replace github.com/wlynxg/anet => ./third_party/anet`
