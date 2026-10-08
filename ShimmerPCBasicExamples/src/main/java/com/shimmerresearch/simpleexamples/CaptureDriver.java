package com.shimmerresearch.simpleexamples;

import java.util.List;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;

/**
 * One way for {@link ShimmerBLECaptureExample} to talk to a device, so the same app can run the
 * ShimmerBluetooth driver ({@link ShimmerBluetoothCaptureDriver}) or the LogAndStream protocol from the Rust
 * library (RustLogAndStreamCaptureDriver, when the build has it) over the same native BLE
 * transport. One instance drives one device; the app makes one per connection.
 */
interface CaptureDriver {

	/** Called on driver threads; implementations of the UI hand them to the Swing thread. */
	interface Listener {
		void onState(String state);

		/** The handshake finished; the device can stream. */
		void onReady();

		/**
		 * One data packet, named ({@link ObjectCluster#getShimmerName()}) for the device's
		 * advertised BLE name, as {@link CaptureDriver#getSignalsForPlot()} names its signals.
		 */
		void onSample(ObjectCluster sample);

		void onError(String message);

		/** Something worth a line in the app's log, not an error. */
		default void onInfo(String message) {
		}

		/**
		 * The firmware's data-rate test (see {@link CaptureDriver#canTestDataRate()}): its progress
		 * while it runs, then once with {@code finished} set. {@code text} gives every figure, or why
		 * the test ended early.
		 */
		default void onDataRate(boolean finished, double kibPerSecond, long missingPackets, String text) {
		}
	}

	/** Short name for titles and file names. */
	String label();

	/** Starts connecting; the outcome arrives through the listener. */
	void connect(BleScanResult device, Listener listener);

	void disconnect();

	void startStreaming() throws Exception;

	void stopStreaming();

	boolean isConnected();

	boolean isStreaming();

	/** Connected and ready for a command: not streaming, starting, stopping, configuring or testing. */
	boolean isIdle();

	double getSamplingRate();

	int getMtu();

	/** Packet reception rate in percent, as the driver itself counts it, or NaN if not known yet. */
	double getPacketReceptionRate();

	/** The hardware and firmware, as one line, or null until the handshake finished. */
	String getDeviceSummary();

	/**
	 * The signals that can be plotted, each {device name, channel, CAL or UNCAL, units} as the plot
	 * manager takes them, the device name being the advertised BLE name its samples carry. Null
	 * until connected.
	 */
	List<String[]> getSignalsForPlot();

	/** True if this driver can change the device's configuration. */
	boolean canConfigure();

	/** True if this driver can run the firmware's data-rate (throughput) test. */
	default boolean canTestDataRate() {
		return false;
	}

	/** Runs the data-rate test for this long; the figures arrive through {@link Listener#onDataRate}. */
	default void startDataRateTest(int durationMs) {
		throw new UnsupportedOperationException(label() + " has no data-rate test");
	}
}
