package com.shimmerresearch.driver.ble.nativeble;

/** Receives scan results. Called on the {@link BleCentral} dispatcher thread. */
public interface BleScanListener {

	/** Called for every advertisement, so the same device repeats with updated name or RSSI. */
	void onDeviceFound(BleScanResult device);
}
