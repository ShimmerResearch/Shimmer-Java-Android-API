package com.shimmerresearch.simpleexamples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.simpleexamples.bletest.BleTransport;

/**
 * Runs both {@link ShimmerBLECaptureExample} backends against a real device, headless: connect,
 * stream, stop, disconnect, then compare what each delivered. Checks the capture app's glue
 * before anyone clicks through it.
 *
 * <pre>
 * CaptureBackendSmokeTest &lt;device name&gt; [seconds per backend]
 * </pre>
 */
public class CaptureBackendSmokeTest {

	private static final String[] COMPARED = { "Accel_LN_X", "Accel_LN_Y", "Accel_LN_Z", "Gyro_X", "Gyro_Y", "Gyro_Z",
			"Mag_X", "Mag_Y", "Mag_Z", "Battery" };

	static final class Run implements CaptureBackend.Listener {
		final List<ObjectCluster> samples = Collections.synchronizedList(new ArrayList<ObjectCluster>());
		final List<String> errors = Collections.synchronizedList(new ArrayList<String>());
		volatile boolean ready = false;

		@Override
		public void onState(String state) {
			System.out.println("    state: " + state);
		}

		@Override
		public void onReady() {
			ready = true;
		}

		@Override
		public void onSample(ObjectCluster sample) {
			samples.add(sample);
		}

		@Override
		public void onError(String message) {
			System.out.println("    ERROR: " + message);
			errors.add(message);
		}
	}

	public static void main(String[] args) throws Exception {
		String name = args.length > 0 ? args[0] : "Shimmer3R";
		int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 5;
		BleCentral central = BleCentral.getDefault();

		Run driver = run(new DriverCaptureBackend(), central, name, seconds);
		Thread.sleep(3000);
		Run stateMachine = run(new ProtocolCaptureBackend(), central, name, seconds);

		boolean ok = driver.errors.isEmpty() && stateMachine.errors.isEmpty() && !driver.samples.isEmpty()
				&& !stateMachine.samples.isEmpty();
		if (ok) {
			List<String> a = driver.samples.get(0).getChannelNamesByInsertionOrder();
			List<String> b = stateMachine.samples.get(0).getChannelNamesByInsertionOrder();
			System.out.println("same channels: " + a.equals(b) + " (" + a.size() + ")");
			ok = a.equals(b);
			System.out.println(String.format("%-12s %14s %14s", "channel", "driver mean", "state machine"));
			for (String channel : COMPARED) {
				System.out.println(String.format("%-12s %14.4f %14.4f", channel, mean(driver.samples, channel),
						mean(stateMachine.samples, channel)));
			}
		}
		System.out.println(ok ? "SMOKE TEST PASSED" : "SMOKE TEST FAILED");
		System.exit(ok ? 0 : 1);
	}

	private static Run run(CaptureBackend backend, BleCentral central, String name, int seconds) throws Exception {
		System.out.println("== " + backend.label());
		Run run = new Run();
		NativeBleDevice device = BleTransport.scanFor(central, name, 20000);
		backend.connect(device, run);
		waitUntil(30000, () -> run.ready || !run.errors.isEmpty());
		if (!run.ready) {
			return run;
		}
		System.out.println("    connected; MTU " + backend.getMtu() + ", " + backend.getSamplingRate() + " Hz, plot device "
				+ (backend.getDeviceForPlot() != null));
		backend.startStreaming();
		waitUntil(10000, backend::isStreaming);
		long started = System.currentTimeMillis();
		Thread.sleep(seconds * 1000L);
		backend.stopStreaming();
		waitUntil(10000, () -> !backend.isStreaming());
		double rate = run.samples.size() / ((System.currentTimeMillis() - started) / 1000.0);
		System.out.println(String.format("    %d samples, %.1f /s, reception %.1f%%", run.samples.size(), rate,
				backend.getPacketReceptionRate()));
		backend.disconnect();
		return run;
	}

	private static double mean(List<ObjectCluster> samples, String channel) {
		double sum = 0;
		int n = 0;
		synchronized (samples) {
			for (ObjectCluster oc : samples) {
				double v = oc.getFormatClusterValue(channel, "CAL");
				if (!Double.isNaN(v)) {
					sum += v;
					n++;
				}
			}
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	private static void waitUntil(long timeoutMs, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
	}
}
