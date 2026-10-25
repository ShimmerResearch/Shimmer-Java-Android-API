# Java HIL: the Java driver on real Shimmers

Hardware-in-the-loop tests of the Java driver (`ShimmerDriver` with `ShimmerDriverPC`). A PC connects
to real Shimmer3 and Shimmer3R units over their paired Bluetooth Classic serial ports, through the
same API Consensys and MATLAB use (`ShimmerPC`), **with the driver's timers on** as an app runs it.
One command builds the tests, runs every scenario on every unit, and prints pass or fail.

```powershell
.\tools\hil\run-java-hil.ps1
```

It exits 0 when everything passed, 1 when anything failed, errored, timed out or was skipped, and 2
when the bench isn't set up.

The CI tests run against byte simulators, and since DEV-1198 (#325, #326) with the status timer off.
This is where the driver meets real firmware, a real radio and its own timers.

## Before every Java API release (the gate)

**Release Java API** won't publish a commit that hasn't passed this on hardware. Each round:

1. On the bench, on `master` with a clean working tree and every unit in the rig, run:
   ```powershell
   git checkout master; git pull
   .\tools\hil\run-java-hil.ps1 -Report
   ```
   With `-Report`, the result is posted to GitHub as the commit status **`java-hil`** on that commit:
   passed or failed, with each unit's firmware. It refuses to report anything but the full gate run:
   - every scenario, on at least one Shimmer3 and one Shimmer3R;
   - the default rate, duration, link CRC and loss limit, and a fresh build (no `-SkipBuild`);
   - a clean working tree, pushed.
2. Run **Release Java API**. It looks up that status for the commit it's releasing and stops before
   building anything unless it passed. The release notes say what was tested.

If anything merges after step 1, the commit changes, so test again. In an emergency, put a Jira key in
**hil_waiver** to release without a pass; the release notes record the waiver. Dry runs only warn.

## What it tests

| Scenario | What it does | Fails when |
|---|---|---|
| `streamRest` | Connects, writes an accel-only config at `-Rate` (default 256 Hz) the way Consensys's Bluetooth manager does, streams for `-Seconds` (default 30) with the unit lying still, then stops and disconnects. | It can't connect or configure; more than `-MaxLossPercent` (1 %) of packets are lost; a timestamp doesn't increase, or leaps more than a second ahead of when it arrived (a bad unwrap, not loss); the timestamps give no rate, or one more than 1 % off the configured rate; the stream goes silent for over a second before the test stops it; accel is more than 1 m/s² from 9.81; or the driver logs a parser error or a command timeout. |
| `midStreamSet` | On a streaming unit, three times: starts SD logging **the way Consensys does** (stop streaming, start logging on the quiet link, stream again; DEV-436), stops it mid-stream, and reads the battery mid-stream. | One of those calls fails, or a battery reply isn't decoded within 5 s; logging doesn't start or stop, or streaming doesn't come back; a packet is lost around a battery read, or more than `-MaxLossPercent` elsewhere (a single sample skipped right where the battery reply arrives is reported, not failed: a Shimmer3 gives up a sample slot to answer a battery read, DEV-1297); a timestamp fault in any stretch between restarts; or the driver logs a parser error or a command timeout. Packets missing around an SD logging stop are reported, not failed: the unit pauses while it closes its file. Needs an SD card in the unit. |
| `reconnectLoop` | Connects, streams for 5 s and disconnects, five times on the same `ShimmerPC`. Every connect starts the driver's status timer, which is when the DEV-1266 lock inversion is most likely. | Any cycle fails to connect within 30 s, loses packets, fails the timestamp checks, or the driver logs a parser error or command timeout. |
| `inStream` | While streaming: first each in-stream reply on its own, three rounds: status (`0x8A 0x71`), directory (`0x8A 0x88`) and battery (`0x8A 0x94`). Then five overlaps as Consensys meets them: with the unit streaming and logging (logging started the Consensys way), stop SD logging mid-stream and request status and battery at once, so in-stream replies, including any status the unit pushes as logging stops, arrive while that command waits for its ACK (DEV-1262). | A reply doesn't arrive within 3 s or isn't decoded; an overlap doesn't complete within 5 s (both replies decoded, logging stopped, the queue empty); the driver logs a command timeout; or the stream fails the checks above. |
| `queueStress` | While streaming, four app threads call `readBattery()` and `readStatusLogAndStream()` and check the queue length, in tight loops for 60 s, keeping the queue under 8. Each of those calls locks the device and then the instruction list, so one is nearly always under way while the IO thread fetches the next instruction. That is the window DEV-1266's lock inversion needs (#334). No SD logging is involved. | The calls and replies stop for 10 s, or the JVM finds a deadlock; the queue doesn't drain afterwards. Without the DEV-1266 fix a Shimmer3R deadlocked within 0.3 s in every run. |
| `rollover` | Configures as `streamRest` does and streams for 10 minutes with the unit lying still. That is past the 512 s wrap of the unit's 3-byte timestamp counter from wherever it starts, so the driver's unwrap has to carry the timeline across it (DEV-1023, the same rule wrong in four APIs). The C# HIL's `stream.rollover` does the same. | The stream fails `streamRest`'s checks, or the link is lost; the unit's raw counter never wrapped (counted from the TIMESTAMP channel's raw ticks, so a shortened run doesn't pass). |
| `idleStart` | Connects and configures, then leaves the link idle for 60 s, with the driver's timers running as they do in an app, before streaming 10 s. The C# and shimmer-logandstream HILs check the same: a Bluetooth Classic link left quiet otherwise answers its next command late. The report counts the status timer's requests during the wait. | The link is lost during the wait, or the driver logs a command timeout or parser error in it; streaming starts more than 500 ms after `startStreaming()`; or the stream fails `streamRest`'s checks. |
| `samplingRates` | Writes 51.2, 256 and 512 Hz in turn, each the way Consensys's Bluetooth manager writes a configuration, and streams 5 s at each, as the shimmer-logandstream HIL's `config.sampling_rate` does. Then it writes the unit's own rate back, accel only as every scenario leaves it. | A configuration doesn't take within 20 s, or the unit reads back another rate; a stream loses packets, fails the timestamp checks, or has a rate more than 1 % off the one written; the driver logs a parser error or command timeout; or the unit's own rate isn't written back. |

Every API call runs under a watchdog. If one doesn't return within 10 s, the scenario stops, and its
events hold every thread's stack with the locks each holds and waits on, plus any deadlock the JVM
detects (DEV-1266). If the whole run still doesn't finish, the runner stops Gradle after
`-ScenarioTimeoutSeconds` (900) and records TIMEOUT.

A connect is never retried. If the COM port can't be opened, the scenario fails and says why:
- **"Port busy"**: another program on this PC already has the port open: another HIL, Consensys, a
  serial terminal. The unit is in use, so a retry would only produce results from a shared unit.
- **CONNECTION_FAILED** for any other reason: the unit is off, out of range, or connected to
  another host such as a phone.

Every connect, in every scenario, is also checked for DEV-1275: the first CONNECTED must come only after the driver's NOTIFICATION_SHIMMER_FULLY_INITIALIZED, which marks the end of its connect sequence. `connectedBeforeReady` counts the connects that broke this, and any one fails the scenario.

Each report counts what the session covered: connects, in-stream replies by type, statuses and
battery readings decoded, the status timer's requests, and command timeouts. Its events also say
which driver timers were running after connect, after configure and while streaming.

A Shimmer3R answers a clock write (`SET_RWC`) while it streams with out-of-order timestamps (DEV-1245, firmware). Up to 0.11.11_beta, `startSDLogging()` while streaming wrote the clock first. Since DEV-1337 the driver doesn't send a clock write to a streaming Shimmer3R; the connect sequence sets the clock. These scenarios start logging the Consensys way regardless.

The stream checks are the Android HIL's (`StreamAnalysis`, the same class in ShimmerAndroidAPI's
`shimmerHilTest`), so the two judge a stream by the same rules. `StreamAnalysisTest` runs in CI, as
does `TimestampWrapsTest`, for the wrap count `rollover` relies on.

### What it does not test

- BLE: on the PC it goes through the gRPC BLE server (`ShimmerGRPC`); not in v1.
- Status the firmware pushes on its own (dock, undock, button press), which needs someone at the
  bench; the managed USB hub in `bench-tools/hub` could do that later.
- The SD and file parsers, and the Android API's own Classic parser (the Android HIL covers that).

## Choosing what to test

| | Default | Option |
|---|---|---|
| Units | every unit in the rig file | `-Unit s3r-86f8` |
| Scenarios | all eight (`rollover` alone streams 10 minutes a unit) | `-Scenario inStream` |
| Sampling rate, stream duration | 256 Hz, 30 s | `-Rate 512`, `-Seconds 60` |
| Link CRC | the driver's default (1 byte) | `-Crc off`, `-Crc 2` |
| Pause between runs | 10 s | `-SettleSeconds 30` |
| Record the result for the release gate | no | `-Report` (full run on a clean, pushed commit only) |

## One-time setup

1. **Pair each unit with the PC** in Windows Settings → Bluetooth (PIN `1234`). Windows creates an
   outgoing serial port for it; the script finds that port from the unit's MAC.
2. **Prepare each unit:** SD sync and Bluetooth disable off (either keeps Bluetooth off, DEV-1228), an
   SD card in for `midStreamSet`, lying still during `streamRest`, `rollover` and `idleStart`.
3. **List the units:** copy `rig-java.example.json` to `rig-java.json` and fill in each name, MAC
   and `type` (`Shimmer3` or `Shimmer3R`). Add `comPort` only to override the port the script finds.
   That file is gitignored.
4. **The checkout's folder must be named `Shimmer-Java-Android-API`**, because
   `ShimmerDriverPC/settings.gradle` finds its sibling projects by that name. In a worktree, make a
   junction with that name and run the script through it:
   ```powershell
   New-Item -ItemType Junction -Path C:\wt\Shimmer-Java-Android-API -Target C:\wt\<your-worktree>
   C:\wt\Shimmer-Java-Android-API\tools\hil\run-java-hil.ps1
   ```
5. **JDK 11 or newer.** The script uses `JAVA_HOME` if it qualifies, otherwise the first one it finds
   under Amazon Corretto, Eclipse Adoptium or Java in Program Files.

**Nothing else may use the units during a run.** That includes Consensys, the Android HIL's phone,
and the other HILs that share this bench (C#, shimmer-logandstream).

Only a second Classic connection is refused outright, as "Port busy". A second host connecting over
BLE is not:
- a Shimmer3R that is streaming over Classic accepts the BLE link;
- it runs the commands sent over BLE and replies down the Classic link, which corrupts this run's
  stream;
- the BLE host sees no replies at all.

On 2026-10-21 that is how two HILs sharing a unit produced a bug report about the unit locking up
after a radio switch (DEV-1276).

## Output

The run's folder, `tools/hil/out/<timestamp>/`, holds:
- `summary.json` and the table the script prints;
- `reports/`, per scenario and unit:
  - `<scenario>_<unit>.json`: versions, the CRC mode in use, loss, rate, accel, the counts above, a
    timeline of events and the failures;
  - `.csv`: every sample;
  - `.driver.log`: every line the driver logged, verbose or not;
- `*.console.txt` and `*.junit.xml`: Gradle's output and the JUnit result for each run;
- `build.log`.

## Running one scenario by hand

```powershell
cd C:\wt\Shimmer-Java-Android-API\ShimmerDriverPC
.\gradlew :hilTest --no-watch-fs --tests com.shimmerresearch.hil.JavaHilScenariosTest.inStream "-Phil.port=COM36" "-Phil.type=Shimmer3R" "-Phil.unit=s3r-86f8"
```

Every `-Phil.<name>` reaches the tests as `hil.<name>`; the names are listed in
`JavaHilScenariosTest`. Without `hil.port` every scenario is skipped, and the normal `test` task
leaves them out. `--no-watch-fs` matters when you edit through a worktree's real path but build
through the junction: Gradle's file watcher misses those edits and would run stale classes.
