# ShimmerPCBasicExamples

There are a number of examples
- ECGToHRExample
- PPGToHRExample
- SensorMapsExample
- ShimmerBLECaptureExample - Shimmer3/Shimmer3R over BLE through the native library (no gRPC server); see ../shimmerble/README.md.
  Two drivers: ShimmerBluetooth (ShimmerBLENative), and the Rust LogAndStream library
  (shimmer-logandstream). A bundled Java binding and native library support macOS arm64 without
  a Rust checkout. A sibling shimmer-logandstream checkout takes precedence when present;
  build its native library with `cargo build --release`. `-PnoShimmerLogAndStream` omits Rust.
  `./gradlew runCaptureSmoke --args="Shimmer3R-2F31 5"` runs both drivers headless and compares
  every decoded channel.

  Several devices at once, each with the driver chosen when it is connected, so the two can run
  side by side: select one or more in the scan list and Connect (they connect one after another).
  Each device has a row in the table: the configured and received rates, samples missing by gaps
  in the device's own timestamps (counted the same for both drivers, beside the driver's own PRR),
  the arrival delay (growing when the link falls behind), time since the last sample, link losses
  and errors; a cell turns amber or red when it needs attention, and each heading's tooltip says
  what it measures. The Reception tab charts reception, received rate and delay each second, per
  device; the Signals tab plots chosen signals from every device on one time axis. "Stream for"
  stops streaming after a set time, so runs compare; each stream's figures go to the Log tab;
  "Copy summary" puts this machine's details and every device's figures on the clipboard for a
  ticket. "Data-rate test" runs the firmware's throughput test (Rust library only) on every
  selected device at once. With "Log to CSV", each device logs to its own file.
- bletest.BleHardwareTestApp - BLE hardware test matrix and report (`./gradlew runBleTest --args="--help"`)

## Quick start: Apple Silicon Mac

Install an arm64 JDK 17 and set `JAVA_HOME` to it. Clone this repository on
`DEV-1134_shimmer3r_protocol_state_machine`, keeping the directory name
`Shimmer-Java-Android-API`. No Rust installation or private protocol repository is needed.
From Terminal:

```sh
cd Shimmer-Java-Android-API/ShimmerPCBasicExamples
./gradlew runBleCapture
```

Gradle downloads Java dependencies on the first run. Allow Terminal Bluetooth access when asked.
Both driver choices should appear. Power on the devices and disconnect them from other computers,
browser tabs and apps. Scan, select a driver, select a device, and Connect; repeat for the second
device. Set Stream for to 60 and click Start all.

For a headless check before opening the app:

```sh
./gradlew runCaptureSmoke --args="Shimmer3R-2F31 5"
./gradlew runCaptureSmoke --args="Shimmer3-3E36 5"
```

In Eclipse, import this folder as an Existing Gradle Project with Java 17, refresh Gradle after
pulling, and run ShimmerBLECaptureExample. Eclipse needs its own Bluetooth permission.
The bundled binaries are for macOS arm64 only; Intel Macs and other operating systems need
matching native libraries. See `libs/SHIMMER_LOGANDSTREAM.md` for binary provenance and updates.
