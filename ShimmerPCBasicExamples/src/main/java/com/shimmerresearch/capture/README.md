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
  - The plot, in CAPTURE.md's panels by channel name: 5, 10 or 30 s, calibrated or raw, Clear. A
    Shimmer3R's `Sound_Level` (nominal dB(A)) is in the Other panel, without the samples where the
    unit has no level yet.
  - **Event markers and audio...**: the event channel and the microphone (below).
- **Device tab:**
  - Read battery, Read status, Toggle red LED (then the status, whose flag says whether it is on).
  - Read clock, Set clock (from this host's, then read back), Read Bluetooth module.
  - Start logging (the clock set first unless it logs already) and Stop logging, each followed by a
    status read.
  - The sampling rate: 51.2 to 1024 Hz, written as the driver writes it.
  - **Sensors...** and **Settings...**: the driver's own configuration dialogs.
  - The chosen device's battery (volts, percent, charger), the status's nine flags, its MAC, its
    Bluetooth module and its clock.
  - Where the unit records audio, the status's two audio flags too: Recording audio and Audio
    fault. The unit sends its status whenever either changes.
  - Whether the firmware has the event markers (yes, no, or not asked yet) and audio recording
    (yes or no), from what the driver asks at connect.
- **The log** at the bottom.

**The recording** is CAPTURE.md's, so one script reads any app's files:
- **Name:** `<Shimmer3R|Shimmer3>_<MAC last 4>_<yyyymmdd_hhmmss>.csv`, the start in local time.
- **Header rows:** `HostTime_ms`, `TIMESTAMP`, then each channel the driver decodes as
  `<channel>_CAL`, then `<channel>_RAW`, in the driver's own order. The raw `Timestamp` is `TIMESTAMP`
  alone. Then a row of units.
- **Rows:** one per sample, each value written as JavaScript writes a number. RFC 4180 cells, CRLF
  line ends. `Sound_Level_CAL` is `NaN` where the unit has no level yet, and `Sound_Level_RAW` is
  then `-32768`.
- **When it closes:** when the recording stops, the stream stops, the link drops, or the window is
  closed.

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
- **Event markers and audio,** which CAPTURE.md also lists as not yet in any app: the records saved
  as CSV; an event file opened from disk is here, but not a WAV header or a fingerprint file; and
  the `events/` and `audio/` files downloaded with their session (no SD card over Bluetooth in the
  driver).
- **Audio recording's "not asked yet":** the driver keeps none, so a v1.01.025 unit shows no until
  it has answered GET_AUDIO_STATUS, which the driver asks at connect.

## Tests

`./gradlew :test --tests 'com.shimmerresearch.capture.*'` runs `CaptureRulesTest`. It covers:
- the recording's numbers, cells, file name, columns and units, the sound level's among them;
- the figures' arithmetic;
- the plot panels;
- the state mapping;
- the microphone: the four audio modes, when Write asks before raw audio, what byte 233 is written
  as (with and without the answer yes, and the sound level bit), and the audio flags as shown.

CI does not build this module, so run the tests locally.

## What is where

| File | What it is |
|---|---|
| `ShimmerCaptureJava` | The window and `main` |
| `CaptureDevice` | One device on the driver: the link, the commands, what it shows; no Swing |
| `ReceptionStats` | The figures' arithmetic |
| `CsvRecording` | The CSV |
| `Panels`, `PlotView` | The plot |
| `simpleexamples.EventMarkersFrame` | The event markers and audio window, shared with `SensorMapsExample` |
| `simpleexamples.MicrophoneSettings` | Byte 233's rules: the audio modes, raw audio, the sound level; no Swing |

## Event markers and audio

The Stream tab's **Event markers and audio...** opens the window for CAPTURE.md's "Event markers
and audio recording" (`EventMarkersFrame`: DEV-971, from ShimmerResearch/Shimmer-Java-Android-API#343,
and DEV-1478). It covers a Shimmer3R on LogAndStream v1.01.026 or later, and the top line says
whether the unit has each part.

**Event markers:**
- byte 232: the button mode, what starts logging, and the TTL input;
- opt in and out of live records, with sequence gaps flagged;
- the status, and an event file read from a card.

**Audio,** configuration byte 233:
- **Audio recording:** Off, Fingerprint only, Fingerprint and raw audio, or Raw audio only. That is
  what the unit saves in `audio/` on its card while it logs.
- **Sound level:** the `Sound_Level` channel, from v1.01.026. Writing it asks the unit for its
  channel list again, since it adds or removes a channel.
- **Write** writes the whole configuration and reads it back, so the line below it shows what the
  unit kept. Raw audio records speech: where the new mode keeps raw audio and the unit's own does
  not, Write asks first and writes nothing if the answer is no. The driver's own rule otherwise
  writes the mode without the raw audio.
- **Read audio status** (GET_AUDIO_STATUS), from READY only: the driver reads its reply between
  commands, not between data packets. It shows the Recording audio and Audio fault flags.

While streaming, each button press and release, and each TTL edge, is also drawn on every plot panel
shown, as a dashed line labelled `press`, `release`, `TTL high` or `TTL low`. The plot's x axis is the
samples' device time, and a record carries the same clock in full: its low 24 bits line up with a
packet's timestamp.

To try the markers:
1. Connect, then open Event markers and audio.
2. Set the button to mode 5 (every press recorded) and click Write.
3. Click Opt in.
4. Start streaming and press the button.

Markers are not written to the CSV.

To try the microphone:
1. Connect, then open Event markers and audio.
2. Set Audio recording to Fingerprint only, tick Sound level, and click Write.
3. Check the line under the buttons for what the unit kept.
4. Start streaming: `Sound_Level` appears in the Other panel.
5. Stop streaming, start logging on the Device tab, then click Read audio status to see whether it
   is recording audio.
