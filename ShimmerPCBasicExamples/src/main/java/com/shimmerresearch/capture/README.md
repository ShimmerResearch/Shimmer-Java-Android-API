# Shimmer Capture on the Java driver

Shimmer Capture built on this repository's Java driver, in Swing. It does what shimmer-host-core's
`bindings/CAPTURE.md` says every Capture app does, as far as the Java driver can do it today. It
shows the same figures under the same names and writes the same CSV files, so a result from this app
compares with the host-core apps'. A stream that loses samples in one app and not in the other is the
link or the platform, not the app. DEV-1473.

It connects a Shimmer3 or Shimmer3R over **Bluetooth Classic**, one device at a time, through the
driver's Bluetooth manager (`BasicShimmerBluetoothManagerPc`, its BLE server not started).

## Running it

From `ShimmerPCBasicExamples`:

```
./gradlew runCapture
```

Or in Eclipse, run `com.shimmerresearch.capture.ShimmerCaptureJava` as a Java application.

Pick the paired Shimmer's serial port, then click **Connect**:

- **Windows:** its outgoing COM port (Bluetooth settings, More Bluetooth settings, COM Ports).
- **macOS:** `/dev/cu.Shimmer3R-XXXX`.
- **Linux:** `/dev/rfcomm<n>`.

A Shimmer holds one link at a time, so close Consensys, and anything else connected to it, first.

Recordings go to `Shimmer Capture` in Documents.

## What it does

- **Figures,** CAPTURE.md's table, on the left:
  - Device, Link (`Classic, <port>`), State, Rate Hz, Rx /s, Samples, Missing, Reception, PRR,
    Delay ms, Last sample, Battery, Link lost, Errors, Data rate, Hardware / firmware.
  - Missing, Reception, Delay, Rx /s and Last sample are counted exactly as CAPTURE.md defines them.
    The tests use host-core's Java Capture test inputs and results.
- **Stream tab:**
  - Start, Stop and Record to CSV.
  - The plot, in CAPTURE.md's panels by channel name: 5, 10 or 30 s, calibrated or raw, Clear.
- **Device tab:**
  - Read battery, Read status, Toggle red LED (then the status, whose flag says whether it is on).
  - Read clock, Set clock (from this host's, then read back), Read Bluetooth module.
  - Start logging (the clock set first unless it logs already) and Stop logging, each followed by a
    status read.
  - The sampling rate: 51.2 to 1024 Hz, written as the driver writes it.
  - **Sensors...** and **Settings...**: the driver's own configuration dialogs.
  - The chosen device's battery (volts, percent, charger), the status's nine flags, its MAC, its
    Bluetooth module and its clock.
- **The log** at the bottom.

**The recording** is CAPTURE.md's, so one script reads any app's files:
- **Name:** `<Shimmer3R|Shimmer3>_<MAC last 4>_<yyyymmdd_hhmmss>.csv`, the start in local time.
- **Header rows:** `HostTime_ms`, `TIMESTAMP`, then each channel the driver decodes as
  `<channel>_CAL`, then `<channel>_RAW`, in the driver's own order. The raw `Timestamp` is `TIMESTAMP`
  alone. Then a row of units.
- **Rows:** one per sample, each value written as JavaScript writes a number. RFC 4180 cells, CRLF
  line ends.
- **When it closes:** when the recording stops, the stream stops, or the link drops.

The driver reports most answers by updating its own state rather than with an event. So the window
reads that state back twice a second, and a figure or detail can take up to half a second to show an
answer.

## Not yet

What CAPTURE.md asks for that this app does not do:

**The Java driver has no such command yet:**
- the data-rate test (the Data rate figure shows `-`);
- the factory self-test;
- the names (the expansion board EEPROM's name record) and the reboot on disconnect;
- the SD card over Bluetooth (browse, download, delete);
- the SD configuration file's update (the driver knows `UPD_SDLOG_CFG_COMMAND` but has no call that
  sends it);
- the Bluetooth module's update;
- calibration editing as CAPTURE.md has it.

**Not built in this app yet:**
- **Several devices at once.**
- **BLE:** the manager's gRPC BLE server.
- **USB / the dock protocol,** and the multi-slot base.
- **The clock drift,** and the clock's round trip: the clock difference is taken from when the read
  was sent.
- **The configuration editor:** CAPTURE.md's sensor names, problems and the bytes changed, rather than
  the driver's dialogs.
- **The Reception chart.**
- **The event log's** filters, Save and Copy summary.
- **The recorded sensor,** and with it the no-hardware check CAPTURE.md asks for.
- **The state names:** they are the driver's, mapped to CAPTURE.md's. CONNECTED and SDLOGGING are
  READY, a stream with or without logging is STREAMING, and a lost link is DISCONNECTED, counted in
  Link lost. The driver has no STARTING, STOPPING or TESTING.

## Tests

`./gradlew :test --tests 'com.shimmerresearch.capture.*'` runs `CaptureRulesTest`. It covers:
- the recording's numbers, cells, file name, columns and units;
- the figures' arithmetic;
- the plot panels;
- the state mapping.

CI does not build this module, so run the tests locally.

## What is where

| File | What it is |
|---|---|
| `ShimmerCaptureJava` | The window and `main` |
| `CaptureDevice` | One device on the driver: the link, the commands, what it shows; no Swing |
| `ReceptionStats` | The figures' arithmetic |
| `CsvRecording` | The CSV |
| `Panels`, `PlotView` | The plot |
