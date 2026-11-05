# Aether engine — run the vendored aether core binary as a local SOCKS server,
# then bridge it into the VPN TUN through sing-box's mixed inbound (the same
# layering PattNG uses: app -> aether SOCKS -> exit).
#
# Design (kept small on purpose):
#  - AetherEngine: starts File(nativeLibraryDir)/libaether.so as a child process
#    with a generated command line: socks listener on 127.0.0.1:<port>,
#    optional psiphon/tor modes via arguments, logs to LogBus, exit callback.
#  - DamavandVpnService gains KIND_AETHER: build a sing-box config whose only
#    outbound is socks -> 127.0.0.1:aetherPort, TUN inbound unchanged.
#  - VpnManager: KIND_AETHER const + dispatch + label.
#  - ServersScreen AddServerSheet: "Aether" option (psiphon built into aether
#    core runs by default mode) — creates a RawJson profile marking aether mode.
#  - ProfileSource.Aether(serializable) + DeepLinkParser scheme aether.
#
# The aether binary ships via tools/get-cores.sh: patterniha/Aether releases
# publish libaether.so per ABI (aether-android release assets), sha256-verified.
import json
import urllib.request
from pathlib import Path

TOKEN = Path('/tmp/.ghtoken').read_text().strip()
HDR = {"Authorization": f"token {TOKEN}", "Accept": "application/vnd.github+json"}


def get(url):
    req = urllib.request.Request(url, headers=HDR)
    return urllib.request.urlopen(req, timeout=30).read()


rels = json.loads(get("https://api.github.com/repos/patterniha/Aether/releases?per_page=5"))
for r in rels:
    assets = [(a['name'], a['size'], a['browser_download_url']) for a in r['assets']]
    print(r['tag_name'])
    for name, size, url in assets[:10]:
        print('   ', name, size)
