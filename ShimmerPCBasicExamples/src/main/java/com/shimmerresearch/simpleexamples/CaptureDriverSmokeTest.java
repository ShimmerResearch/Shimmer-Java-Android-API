package com.shimmerresearch.simpleexamples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.simpleexamples.bletest.BleTransport;

/**
 * Runs every {@link ShimmerBLECaptureExample} driver against a real device, headless: connect,
 * stream, stop, disconnect, then compare what each delivered. Checks the capture app's glue
 * before anyone clicks through it. The Rust LogAndStream driver runs too when the build has it.
 *
 * <pre>
 * CaptureDriverSmokeTest &lt;device name&gt; [seconds per driver]
 * </pre>
 */
public class CaptureDriverSmokeTest {

	private static final String[] COMPARED = { "Accel_LN_X", "Accel_LN_Y", "Accel_LN_Z", "Gyro_X", "Gyro_Y", "Gyro_Z",
			"Mag_X", "Mag_Y", "Mag_Z", "Battery" };

	static final class Run implements CaptureDriver.Listener {
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

		List<String> labels = new ArrayList<String>();
		List<Run> runs = new ArrayList<Run>();
		List<CaptureDriver> drivers = new ArrayList<CaptureDriver>();
		drivers.add(new ShimmerBluetoothCaptureDriver());
		CaptureDriver rust = ShimmerBLECaptureExample.newRustLogAndStreamDriver();
		if (rust != null) {
			drivers.add(rust);
		} else {
			System.out.println("(the Rust LogAndStream driver is not in this build)");
		}
		for (CaptureDriver driver : drivers) {
			if (!runs.isEmpty()) {
				Thread.sleep(3000);
			}
			labels.add(driver.label());
			runs.add(run(driver, central, name, seconds));
		}

		boolean ok = true;
		for (Run r : runs) {
			ok &= r.errors.isEmpty() && !r.samples.isEmpty();
		}
		if (ok) {
			List<String> reference = runs.get(0).samples.get(0).getChannelNamesByInsertionOrder();
			for (int i = 1; i < runs.size(); i++) {
				List<String> channels = runs.get(i).samples.get(0).getChannelNamesByInsertionOrder();
				boolean same = reference.equals(channels);
				System.out.println(labels.get(i) + " has the driver's channels: " + same + " (" + channels.size() + ")");
				ok &= same;
			}
			StringBuilder header = new StringBuilder(String.format("%-18s", "mean of"));
			for (String label : labels) {
				header.append(String.format(" %18s", label));
			}
			System.out.println(header);
			for (String channel : COMPARED) {
				StringBuilder row = new StringBuilder(String.format("%-18s", channel));
				for (Run r : runs) {
					row.append(String.format(" %18.4f", mean(r.samples, channel)));
				}
				System.out.println(row);
			}
		}
		System.out.println(ok ? "SMOKE TEST PASSED" : "SMOKE TEST FAILED");
		System.exit(ok ? 0 : 1);
	}

	private static Run run(CaptureDriver driver, BleCentral central, String name, int seconds) throws Exception {
		System.out.println("== " + driver.label());
		Run run = new Run();
		BleScanResult device = BleTransport.scanFor(central, name, 20000);
		driver.connect(device, run);
		waitUntil(30000, () -> run.ready || !run.errors.isEmpty());
		if (!run.ready) {
			return run;
		}
		List<String[]> signals = driver.getSignalsForPlot();
		System.out.println("    connected; MTU " + driver.getMtu() + ", " + driver.getSamplingRate() + " Hz, plot device "
				+ (driver.getDeviceForPlot() != null) + ", plot signals " + (signals == null ? "none" : signals.size()));
		if (driver.getDeviceForPlot() == null && signals == null) {
			run.errors.add("nothing to plot from: no device and no signals");
		}
		driver.startStreaming();
		waitUntil(10000, driver::isStreaming);
		long started = System.currentTimeMillis();
		Thread.sleep(seconds * 1000L);
		driver.stopStreaming();
		waitUntil(10000, () -> !driver.isStreaming());
		double rate = run.samples.size() / ((System.currentTimeMillis() - started) / 1000.0);
		System.out.println(String.format("    %d samples, %.1f /s, reception %.1f%%", run.samples.size(), rate,
				driver.getPacketReceptionRate()));
		driver.disconnect();
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
