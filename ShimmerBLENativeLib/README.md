# ShimmerBLENativeLib

The `shimmerble` native library: BLE for the Java driver, loaded into the JVM through JNI, in
place of the gRPC BLE servers (`ShimmerBLEGrpc` in Shimmer-C-API and SwiftAPI). It is written in
Rust on [btleplug](https://github.com/deviceplug/btleplug), which uses each platform's own Bluetooth
API: WinRT on Windows, CoreBluetooth on macOS, BlueZ on Linux. Background and the decision record:
DEV-1132.

The library compiles to plain machine code, so users need nothing installed - not Rust, not a
runtime. Only whoever changes the code here needs the Rust toolchain.

## How the pieces fit

```
ShimmerBLENative (pcDriver)          a ShimmerBluetooth for Shimmer3 / Shimmer3R
  -> BleCentral                      one dispatcher thread; routes events to connections
     -> NativeBleRadio / NativeBle   JNI declarations
        -> shimmerble.dll / libshimmerble.dylib
           src/lib.rs                JNI entry points
           src/engine.rs             btleplug on a tokio runtime, behind a blocking API
```

The Java side lives in `ShimmerDriverPC`, package `com.shimmerresearch.driver.ble.nativeble`, and
not in `ShimmerDriver`, which Android also consumes.

Design rules that matter when changing it:

- **Java pulls; Rust never calls into the JVM.** Events go into one queue that `BleCentral`'s
  dispatcher drains with `NativeBle.nextEvent`. No native thread is ever attached to the JVM.
- **No panic crosses into Java.** Every JNI entry point runs inside `guard()`, which turns errors
  and panics into a `NativeBleException`.
- **The JNI names are a contract.** Rust's function names are derived from
  `com.shimmerresearch.driver.ble.nativeble.NativeBle`, and Rust constructs `NativeBleEvent`
  through its constructor (`EVENT_CTOR_SIG` in `lib.rs`). Rename or move either Java class, or
  change that constructor, and the library breaks.
- **Devices are scanned before they are connected.** btleplug cannot connect to an address it has
  not seen advertising on Windows or macOS.
- **Device IDs are opaque strings**: the MAC address on Windows, a CoreBluetooth UUID on macOS
  (macOS never exposes MAC addresses). Devices are matched by advertised name, which also picks
  the BLE service (`BleUartProfile`).
- **Writes are split to fit the negotiated MTU** (MTU minus 3 bytes per write).

## Build

The compiler version is pinned in `rust-toolchain.toml`; rustup installs it on first use.

| | |
|---|---|
| Install Rust (once) | [rustup](https://rustup.rs). Windows also needs the MSVC build tools (Visual Studio Build Tools, "Desktop development with C++"). macOS needs the Xcode command line tools. |
| Build for this machine | `cargo build --release`, or `./gradlew buildNative` in `ShimmerDriverPC`, which also copies the library to `ShimmerDriverPC/src/main/resources/native/<platform>/` |
| Build for the other OS | Not possible locally: macOS builds need Apple's SDK, which is licensed for Apple hardware only. Use the **Native BLE library** workflow (`.github/workflows/native-ble.yml`), which builds Windows x64 and macOS arm64 on GitHub-hosted runners. |

The Windows library links the C runtime statically (`.cargo/config.toml`), so it needs no Visual
C++ redistributable.

**`LNK1104: cannot open file 'libcmt.lib'`** means Rust picked a Visual Studio install without the
desktop C++ libraries. This happens when there is more than one install (e.g. VS Community with
only some workloads, plus VS Build Tools). Build from the developer environment of the install that
has them, and Rust will use that one:

```
cmd /c "call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat" && cargo build --release"
```

Built libraries are not committed yet (`.gitignore`); whether to commit them or publish them is
an open question on DEV-1132.

## How Java finds the library

`NativeBleLoader` picks the platform folder from the **JVM's** `os.name` and `os.arch` (an Intel
JVM under Rosetta needs the Intel library), then looks, in order:

1. at the `-Dshimmer.ble.lib=<file>` system property, if set
2. next to the jar or classes folder: `shimmerble.dll` or `native/<platform>/shimmerble.dll`
3. in the jar at `/native/<platform>/`, extracted once to `~/.shimmer/native/shimmerble-<version>/`

After loading, it checks the library's version against `NativeBleLoader.EXPECTED_NATIVE_VERSION`
and fails loudly on a mismatch. **Bump both together**: `version` in `Cargo.toml` and that constant.

## Try it

`blecli` uses the same engine without Java, which separates a Bluetooth problem from a driver one:

```
cargo run --release --bin blecli -- scan
cargo run --release --bin blecli -- probe Shimmer3R-2F31      # inquiry 0x01: expects ACK 0xFF, then 0x02
cargo run --release --bin blecli -- stream Shimmer3R-2F31 20  # start 0x07 for 20 s, bytes/s, stop 0x20
```

From Java (in `ShimmerPCBasicExamples`, after `./gradlew buildNative` in `ShimmerDriverPC`):

```
./gradlew runBleCapture                                   # Shimmer Capture-style app
./gradlew runBleTest --args="--device Shimmer3R-2F31"     # hardware test matrix, Markdown report
./gradlew runBleTest --args="--device Shimmer3R-2F31 --transport grpc --mac E8EB1B712F31 --grpc-server <path>/ShimmerBLEGrpc.exe"
```

The second test command runs the same tests over the gRPC server, as the baseline to compare against.

### macOS

macOS asks for Bluetooth permission per application. Run from Terminal and allow Terminal under
System Settings > Privacy & Security > Bluetooth. Launching from an IDE may fail if the IDE does not
declare Bluetooth usage. A packaged app (Consensys) needs `NSBluetoothAlwaysUsageDescription` in its
`Info.plist`.

## Third-party licences

btleplug is BSD-3-Clause (with MIT/Apache-2.0 parts), and its dependencies are permissively
licensed. Shipping the library means shipping their notices; generate them before the Consensys
integration, e.g. with `cargo about`.
