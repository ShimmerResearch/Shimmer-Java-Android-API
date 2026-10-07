package com.shimmerresearch.simpleexamples;

import java.util.List;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;

/**
 * One way for {@link ShimmerBLECaptureExample} to talk to a device, so the same app can run the
 * ShimmerBluetooth driver ({@link ShimmerBluetoothCaptureDriver}) or the LogAndStream protocol from the Rust
 * library (RustLogAndStreamCaptureDriver, when the build has it) over the same native BLE
 * transport.
 */
interface CaptureDriver {

	/** Called on driver threads; implementations of the UI hand them to the Swing thread. */
	interface Listener {
		void onState(String state);

		/** The handshake finished; the device can stream. */
		void onReady();

		void onSample(ObjectCluster sample);

		void onError(String message);
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

	double getSamplingRate();

	int getMtu();

	/** Packet reception rate in percent, or NaN if not known yet. */
	double getPacketReceptionRate();

	/** The device whose channels can be plotted, or null until connected. */
	ShimmerDevice getDeviceForPlot();

	/**
	 * For a driver with no ShimmerDevice to describe its channels: the signals that can be
	 * plotted, each {device name, channel, CAL or UNCAL, units} as the plot manager takes them.
	 * Null if {@link #getDeviceForPlot()} describes them instead, or until connected.
	 */
	default List<String[]> getSignalsForPlot() {
		return null;
	}

	/** True if this driver can change the device's configuration. */
	boolean canConfigure();
}
