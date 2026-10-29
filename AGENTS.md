# Shimmer-Java-Android-API

Java driver and API for Shimmer devices, shared by PC and Android consumers.

## Build
**JDK 11**, **Gradle 8.14.3**. Each Gradle subproject carries its own wrapper, all pinned to that
version — there is none at the repo root:
```
cd ShimmerDriverPC && ./gradlew build -i
```
CI (`gradle.yml`) runs only on `master` / PRs into it, using the committed wrappers: `./gradlew test`
in `ShimmerDriver`, then `./gradlew build -i` in `ShimmerDriverPC`, whose `settings.gradle` includes
`:ShimmerDriver` and `:ShimmerBluetoothManager`. CI therefore compiles and tests **those three
subprojects and no others**. A change to `ShimmerLSL`, `JavaShimmerConnect`, `ShimmerTCP` or
`ShimmerPCBasicExamples` can merge without ever being compiled by CI — build the affected subproject
locally before you claim it works. `ShimmerTCPExample` and `simmerradiotest` have no Gradle build at
all: they are legacy Eclipse ADT Android projects.

Test results land in `**/build/test-results/test/*.xml`.

## Releasing
Releases are cut by the **Release Java API** workflow (`release.yml`), not from a developer machine,
and need no uprev PR:
1. Run the workflow on `master`. Leave `version` blank for the next patch after the last `API_v`
   release, or type one. It tests and builds as CI does, publishes all three artifacts to GitHub
   Packages in one `gradlew publish`, tags `API_v<version>`, and creates the GitHub Release with the
   jars attached (not marked Latest — that stays with Consensys).
2. It also builds `ShimmerJavaClass.jar` for MATLAB and opens a PR replacing
   `Shimmer-MATLAB-ID/libs/ShimmerJavaClass.jar` (needs the `MATLAB_REPO_TOKEN` secret). Before
   merging it, run a MATLAB example on a Shimmer3 and a Shimmer3R; MATLAB cannot run in CI. Merging
   publishes a MATLAB API release (Shimmer-MATLAB-ID's `release.yml`).
3. It also opens a PR in `ShimmerAndroidAPI` moving the default driver version in
   `ShimmerAndroidInstrumentDriver/build.gradle` to this one (needs the `ANDROID_REPO_TOKEN` secret),
   so that repo's CI runs its unit tests and builds every example against it. The PR lists the steps
   that stay manual. Android's `Shimmer.java` has its own Bluetooth stream parser, so a parser, CRC or
   ACK fix in this release must be ported there by hand (see `ShimmerAndroidAPI`'s AGENTS.md). After
   merging, run the Android HIL at master's new commit, then `ShimmerAndroidAPI`'s own Release
   workflow, which builds against the newest Java release unless told otherwise. That release cannot
   be automatic: its gate needs a passing bench run on its exact commit and driver.

The version is not stored in the source: the three publishing blocks read `-PreleaseVersion`, which
the workflow passes, and fall back to `local`. The `API_v` tags are the record of what was released.

`ShimmerJavaClass.jar` must be Java 8 bytecode, because MATLAB may load it on Java 8. Build it locally
with `cd ShimmerDriverPC && ./gradlew shadowJar -PmatlabJava8`, which compiles all three projects with
`--release 8` — that also rejects any API newer than Java 8, so `Process.onExit()`, `var` and the like
fail the build here instead of throwing `NoSuchMethodError` inside MATLAB.

## Subprojects
| Project | Role |
|---|---|
| `ShimmerDriver` | Core, platform-neutral driver |
| `ShimmerDriverPC` | PC-side driver — CI builds it along with `ShimmerDriver` and `ShimmerBluetoothManager` |
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
