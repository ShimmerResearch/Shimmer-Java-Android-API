package com.shimmerresearch.shimmer3.communication;

import static org.junit.Assert.fail;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.pcDriver.ShimmerPC;

/** Shared helpers for the byte-communication simulator tests. */
public class ByteCommunicationTestUtils {

	/** Generous on purpose: a connect to the simulator normally finishes in well under a second,
	 *  but a loaded CI runner can stall it for several. Only a genuine hang should hit this. */
	public static final long CONNECT_TIMEOUT_MS = 30000;

	/**
	 * Connects and blocks until the device has finished its post-connect config read (infomem,
	 * calibration, etc.), i.e. isInitialised() is set and the state has moved to CONNECTED.
	 * isConnected() alone is not enough - it turns true before that read has run, so a fixed
	 * sleep made these tests flaky on slow runners.
	 */
	public static void connectAndWaitUntilInitialised(ShimmerPC device) {
		device.connect("","");
		long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
		while (!(device.isInitialised() && device.getBluetoothRadioState()==BT_STATE.CONNECTED)) {
			if (System.currentTimeMillis() > deadline) {
				fail("Device did not finish initialising within " + CONNECT_TIMEOUT_MS + " ms (state: " + device.getBluetoothRadioState() + ")");
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				fail("Interrupted while waiting for the device to initialise");
			}
		}
	}

}
