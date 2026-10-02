package com.shimmerresearch.simpleexamples.bletest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The hardware tests from DEV-1132's test matrix. Each runs against whichever
 * {@link BleTransport} it is given, so the same numbers can be taken for the native library and
 * for the gRPC baseline.
 */
public class HardwareTests {

	static final int CONNECT_TIMEOUT_MS = 45000;
	static final int DISCONNECT_TIMEOUT_MS = 15000;
	static final int STREAM_TIMEOUT_MS = 15000;
	/** Marker {@link KillAppChild} prints once it is streaming. */
	static final String CHILD_STREAMING = "KILLAPP-CHILD STREAMING";

	private final TestOptions mOptions;
	private final BleTransport mTransport;
	private final BufferedReader mOperator = new BufferedReader(new InputStreamReader(System.in));

	public HardwareTests(TestOptions options, BleTransport transport) {
		mOptions = options;
		mTransport = transport;
	}

	/** Connect and disconnect repeatedly: every cycle must initialise and close cleanly. */
	public TestResult connectLoop(DeviceUnderTest dut) {
		TestResult r = new TestResult("connect");
		List<Long> connectMs = new ArrayList<Long>();
		int failures = 0;
		for (int i = 1; i <= mOptions.iterations; i++) {
			try {
				long c = dut.connect(CONNECT_TIMEOUT_MS);
				long d = dut.disconnect(DISCONNECT_TIMEOUT_MS);
				connectMs.add(c);
				r.detail(String.format("cycle %2d: connected+initialised in %d ms, disconnected in %d ms", i, c, d));
			} catch (Exception e) {
				failures++;
				r.detail(String.format("cycle %2d: FAILED - %s", i, e.getMessage()));
				dut.closeQuietly();
			}
			sleep(1000);
		}
		String timing = connectMs.isEmpty() ? "" : String.format("; connect min/median/max %d/%d/%d ms",
				Collections.min(connectMs), median(connectMs), Collections.max(connectMs));
		String summary = (mOptions.iterations - failures) + "/" + mOptions.iterations + " cycles" + timing;
		return failures == 0 ? r.pass(summary) : r.fail(summary);
	}

	/** Stream for a long time; the packet reception rate must stay above the threshold. */
	public TestResult longStream(DeviceUnderTest dut) {
		TestResult r = new TestResult("stream");
		try {
			dut.connect(CONNECT_TIMEOUT_MS);
			double rate = dut.getShimmer().getSamplingRateShimmer();
			r.detail(String.format("sampling rate %.2f Hz, %d s stream", rate, mOptions.streamSeconds));
			dut.startStreaming(STREAM_TIMEOUT_MS);
			long started = System.currentTimeMillis();
			long lastPackets = 0;
			for (int s = 10; s <= mOptions.streamSeconds; s += 10) {
				sleep(started + s * 1000L - System.currentTimeMillis());
				if (DeviceUnderTest.NOT_CONNECTED.contains(dut.getState())) {
					return r.fail("link dropped after " + (System.currentTimeMillis() - started) / 1000 + " s");
				}
				long packets = dut.getPackets();
				r.detail(String.format("%4d s: %6.1f packets/s, reception %.2f%%", s, (packets - lastPackets) / 10.0,
						dut.getPacketReceptionRate()));
				lastPackets = packets;
			}
			double seconds = (System.currentTimeMillis() - started) / 1000.0;
			dut.stopStreaming(STREAM_TIMEOUT_MS);
			double prr = dut.getPacketReceptionRate();
			long expected = Math.round(rate * seconds);
			String summary = String.format("reception %.2f%% (%d packets, ~%d expected over %.0f s at %.2f Hz)", prr,
					dut.getPackets(), expected, seconds, rate);
			dut.disconnect(DISCONNECT_TIMEOUT_MS);
			return prr >= mOptions.minReceptionRate ? r.pass(summary) : r.fail(summary + " - below " + mOptions.minReceptionRate + "%");
		} catch (Exception e) {
			dut.closeQuietly();
			return r.fail(e.getMessage());
		}
	}

	/**
	 * Kill another process mid-stream, then connect from this one. With the gRPC transport the
	 * killed app's server is orphaned and keeps the link; with native BLE the OS closes it.
	 */
	public TestResult killApp() {
		TestResult r = new TestResult("kill");
		Process child = null;
		try {
			List<String> cmd = new ArrayList<String>(Arrays.asList(javaExecutable(), "-cp",
					System.getProperty("java.class.path")));
			String libOverride = System.getProperty("shimmer.ble.lib");
			if (libOverride != null) {
				cmd.add("-Dshimmer.ble.lib=" + libOverride);
			}
			cmd.add(KillAppChild.class.getName());
			cmd.addAll(mOptions.childArgs());
			child = new ProcessBuilder(cmd).redirectErrorStream(true).start();

			final CountDownLatch streaming = new CountDownLatch(1);
			final String[] childDeviceId = new String[1];
			final Process watched = child;
			Thread reader = new Thread(() -> {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(watched.getInputStream()))) {
					String line;
					while ((line = in.readLine()) != null) {
						System.out.println("  [kill child] " + line);
						if (line.contains(CHILD_STREAMING)) {
							int at = line.indexOf(" id=");
							childDeviceId[0] = at < 0 ? null : line.substring(at + 4).trim();
							streaming.countDown();
						}
					}
				} catch (IOException e) {
					// The stream closes when the child is killed.
				}
			}, "kill-child-output");
			reader.setDaemon(true);
			reader.start();

			if (!streaming.await(120, TimeUnit.SECONDS)) {
				return r.fail("the child process never started streaming");
			}
			sleep(3000);
			child.destroyForcibly();
			child.waitFor(10, TimeUnit.SECONDS);
			r.detail("child process killed while streaming");
			sleep(3000);

			// The killed app's link may still be open, so the device may not advertise: native
			// reconnects by the ID the child used.
			String knownId = mTransport.name().equals("native") ? childDeviceId[0] : mOptions.mac;
			r.detail("reconnecting with ID " + knownId);
			DeviceUnderTest dut = mTransport.open(mOptions.device, knownId);
			long c = dut.connect(CONNECT_TIMEOUT_MS);
			dut.startStreaming(STREAM_TIMEOUT_MS);
			sleep(5000);
			long packets = dut.getPackets();
			dut.stopStreaming(STREAM_TIMEOUT_MS);
			dut.disconnect(DISCONNECT_TIMEOUT_MS);
			String summary = "reconnected from a new process in " + c + " ms, " + packets + " packets in 5 s";
			return packets > 0 ? r.pass(summary) : r.fail(summary);
		} catch (Exception e) {
			return r.fail("could not use the device after the other app was killed: " + e.getMessage());
		} finally {
			if (child != null && child.isAlive()) {
				child.destroyForcibly();
			}
		}
	}

	/**
	 * The operator switches the device off mid-stream: the loss must be reported promptly. Then
	 * they switch it back on: it must reconnect without restarting the app.
	 */
	public List<TestResult> lossAndReconnect(DeviceUnderTest dut) {
		TestResult loss = new TestResult("loss");
		TestResult reconnect = new TestResult("reconnect");
		try {
			dut.connect(CONNECT_TIMEOUT_MS);
			dut.startStreaming(STREAM_TIMEOUT_MS);
			sleep(3000);
			prompt("Switch OFF " + mOptions.device + " now, then press Enter.");
			long lostMs;
			try {
				// ShimmerBLENative reports CONNECTION_LOST; ShimmerGRPC only ever reports DISCONNECTED.
				lostMs = dut.waitForState(DeviceUnderTest.NOT_CONNECTED, 60000);
			} catch (Exception e) {
				dut.closeQuietly();
				loss.fail("the app was not told the link dropped within 60 s (state " + dut.getState() + ")");
				return Arrays.asList(loss, reconnect.skip("needs the loss test to pass first"));
			}
			sleep(500);
			String lossSummary = dut.getState() + " " + lostMs + " ms after Enter";
			if (lostMs <= mOptions.lossTimeoutSeconds * 1000L) {
				loss.pass(lossSummary);
			} else {
				loss.fail(lossSummary + " - over " + mOptions.lossTimeoutSeconds + " s");
			}

			prompt("Switch " + mOptions.device + " back ON, wait for it to start advertising, then press Enter.");
			long c = dut.connect(CONNECT_TIMEOUT_MS);
			dut.startStreaming(STREAM_TIMEOUT_MS);
			sleep(5000);
			long packets = dut.getPackets();
			dut.stopStreaming(STREAM_TIMEOUT_MS);
			dut.disconnect(DISCONNECT_TIMEOUT_MS);
			String summary = "reconnected in the same process in " + c + " ms, " + packets + " packets in 5 s";
			reconnect = packets > 0 ? reconnect.pass(summary) : reconnect.fail(summary);
		} catch (Exception e) {
			dut.closeQuietly();
			if (loss.summary.isEmpty()) {
				loss.fail(e.getMessage());
				reconnect.skip("needs the loss test to pass first");
			} else {
				reconnect.fail(e.getMessage());
			}
		}
		return Arrays.asList(loss, reconnect);
	}

	/** Two devices streaming at once must both stay above the reception threshold. */
	public TestResult multiDevice(DeviceUnderTest first, DeviceUnderTest second) {
		TestResult r = new TestResult("multi");
		try {
			first.connect(CONNECT_TIMEOUT_MS);
			second.connect(CONNECT_TIMEOUT_MS);
			first.startStreaming(STREAM_TIMEOUT_MS);
			second.startStreaming(STREAM_TIMEOUT_MS);
			sleep(mOptions.multiSeconds * 1000L);
			first.stopStreaming(STREAM_TIMEOUT_MS);
			second.stopStreaming(STREAM_TIMEOUT_MS);
			double a = first.getPacketReceptionRate();
			double b = second.getPacketReceptionRate();
			first.disconnect(DISCONNECT_TIMEOUT_MS);
			second.disconnect(DISCONNECT_TIMEOUT_MS);
			String summary = String.format("%s %.2f%%, %s %.2f%% over %d s", first.label(), a, second.label(), b,
					mOptions.multiSeconds);
			boolean ok = a >= mOptions.minReceptionRate && b >= mOptions.minReceptionRate;
			return ok ? r.pass(summary) : r.fail(summary);
		} catch (Exception e) {
			first.closeQuietly();
			second.closeQuietly();
			return r.fail(e.getMessage());
		}
	}

	private void prompt(String message) throws IOException {
		System.out.println();
		System.out.println(">>> " + message);
		if (mOperator.readLine() == null) {
			throw new IOException("no operator input (stdin closed); run with an interactive console");
		}
	}

	private static String javaExecutable() {
		String exe = System.getProperty("os.name").toLowerCase().startsWith("windows") ? "java.exe" : "java";
		return new File(new File(System.getProperty("java.home"), "bin"), exe).getAbsolutePath();
	}

	private static long median(List<Long> values) {
		List<Long> sorted = new ArrayList<Long>(values);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	static void sleep(long ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
