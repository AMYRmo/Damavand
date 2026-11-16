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
