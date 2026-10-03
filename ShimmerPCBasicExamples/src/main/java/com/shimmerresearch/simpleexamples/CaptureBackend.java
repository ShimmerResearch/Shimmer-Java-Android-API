package com.shimmerresearch.simpleexamples;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;

/**
 * One way for {@link ShimmerBLECaptureExample} to talk to a device, so the same app can run
 * today's driver ({@link DriverCaptureBackend}) or the DEV-1134 protocol state machine
 * ({@link ProtocolCaptureBackend}) over the same native BLE transport.
 */
interface CaptureBackend {

	/** Called on backend threads; implementations of the UI hand them to the Swing thread. */
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
	void connect(NativeBleDevice device, Listener listener);

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

	/** True if this backend can change the device's configuration. */
	boolean canConfigure();
}
