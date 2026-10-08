# ShimmerPCBasicExamples

There are a number of examples
- ECGToHRExample
- PPGToHRExample
- SensorMapsExample
- ShimmerBLECaptureExample - Shimmer3/Shimmer3R over BLE through the native library (no gRPC server); see ../shimmerble/README.md.
  Two drivers: ShimmerBluetooth (ShimmerBLENative), and the Rust LogAndStream library
  (shimmer-logandstream). The Rust one appears only when a checkout of shimmer-logandstream sits
  beside this repository (`C:\dev\shimmer-logandstream`), with its library built there
  (`cargo build --release`, again after pulling it); `-PnoShimmerLogAndStream` builds without it.
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
