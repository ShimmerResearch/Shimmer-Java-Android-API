# Shimmer-Java-Android-API

Java driver and API for Shimmer devices, shared by PC and Android consumers.

## Build
**JDK 11**, **Gradle 8.10.2**. Each subproject carries its own wrapper — there is none at the repo root:
```
cd ShimmerDriverPC && ./gradlew build -i
```
CI (`gradle.yml`) builds **only `ShimmerDriverPC`**, and only on `master` / PRs into it. That means a
change to another subproject can merge without ever being compiled by CI — build the affected
subproject locally before you claim it works.

Test results land in `**/build/test-results/test/*.xml`.

## Subprojects
| Project | Role |
|---|---|
| `ShimmerDriver` | Core, platform-neutral driver |
| `ShimmerDriverPC` | PC-side driver — the only one CI builds |
| `ShimmerBluetoothManager` | Connection management |
| `ShimmerLSL` | Lab Streaming Layer integration |
| `JavaShimmerConnect`, `ShimmerTCP`, `ShimmerTCPExample` | Connectivity apps/examples |
| `ShimmerPCBasicExamples` | Start here for usage — `SensorMapsExample`, `ShimmerPCExample` |

## Eclipse-bound
Every subproject carries a `.classpath`/`.project`, and an Eclipse workspace registers each one by
absolute path. Relocating the repo therefore means re-importing it in Eclipse, not just moving the
folder.

## Consumed as a submodule
`ASM_PC` includes this repo at its root (DEV-928), pinned to an exact commit and built from source
rather than consumed as a published artifact. A working copy of this repo may therefore be either a
standalone clone or that submodule — the submodule sits in **detached HEAD** at the pinned commit,
which is normal. Driver changes made there follow the two-step flow in ASM_PC's own CLAUDE.md, and a
driver change can break ASM_PC's build, so check there too.

## API conventions
The README documents a long-running deprecation: state and data are delivered via
`ShimmerBluetooth.MSG_IDENTIFIER_*` handler messages, **not** the old `Shimmer.MESSAGE_*` /
`Shimmer.STATE_*` constants. Follow the current form in new code.
