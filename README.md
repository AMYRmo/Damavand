# Damavand v2 — rewrite

دماوند 🗿 — بازنویسی کامل کلاینت VPN اندروید دماوند، روی شاخه‌ی `rewrite/v2`.

This repository is the from-scratch **Damavand v2 rewrite**: Android-only
Kotlin + Jetpack Compose, cores **Xray, Psiphon, Aether, Mihomo and
Whitedns (CottenDNS engine)**, every core's profiles usable standalone and
as hops in user-built chains, restrained Liquid Glass in the UI.

**The build specification is the architecture document** (kept with the
research reports, outside this tree):

- `~/workspace/Damavand-rewrite/architecture.md` — modules, runtime, data
  model, chain engine, UI, quality plan, implementation phases.
- `~/workspace/Damavand-rewrite/design-research.md` — design system +
  Liquid Glass plan.
- `~/workspace/Damavand-rewrite/core-audit.md` — v1 core-integration audit.
- `~/workspace/Damavand-rewrite/whitedns-and-chain.md` — WhiteDNS/CottenDNS
  research + chain design.
- `~/workspace/Damavand-rewrite/phase0-cottendns.md` /
  `phase0-aether.md` — Phase-0 spike verdicts.

## Layout

```
app/     the only Android module (applicationId com.amyrmo.damavand)
model/   pure Kotlin/JVM: profile model, CoreKind, link codecs
logic/   pure Kotlin/JVM: config generators, chain planner (testable in CI)
tools/   core fetch/build scripts (see tools/cores.txt for provenance)
```

## Build

Cores are **not** committed; fetch them first (CI does this automatically):

```bash
bash tools/get-cores.sh android   # zedcore.aar (Xray + sing-box + Psiphon)
bash tools/get-aether.sh          # Aether + psiphon-tunnel-core + lyrebird
bash tools/fetch-psiphon-servers.sh
bash tools/get-mihomo.sh          # Mihomo v1.19.33
bash tools/get-cottendns.sh       # CottenDNS client, pinned commit, needs Go ≥ 1.26
bash tools/strip-cores.sh
./gradlew :model:test :logic:test # JVM unit suites (JDK 21, no SDK needed)
./gradlew :app:assembleRelease    # arm64-v8a APK
```

Status: Phase 0 (spikes + skeleton). See the architecture doc §7 for the
phase plan and definitions of done.
