package com.shimmerresearch.simpleexamples;

import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.tools.bluetooth.BasicShimmerBluetoothManagerPc;

/**
 * Lets the driver's existing configuration dialogs (EnableSensorsDialog, SensorConfigDialog) write
 * to a {@link com.shimmerresearch.pcDriver.ShimmerBLENative}.
 * <p>
 * Those dialogs configure through a BasicShimmerBluetoothManagerPc, which looks the device up in
 * its own map of devices it connected itself. This one answers with the device it was given
 * instead, and is created without starting the gRPC BLE server.
 */
public class ShimmerBLENativeConfigManager extends BasicShimmerBluetoothManagerPc {

	private volatile ShimmerDevice mDevice;

	public ShimmerBLENativeConfigManager() {
		super(false);
	}

	public void setDevice(ShimmerDevice device) {
		mDevice = device;
	}

	@Override
	public ShimmerDevice getShimmerDeviceBtConnected(String connectionHandle) {
		return mDevice;
	}
}
