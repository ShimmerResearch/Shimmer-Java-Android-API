package com.shimmerresearch.driver.ble.nativeble;

/** Receives what a connected device sends. Called on the {@link BleCentral} dispatcher thread. */
public interface BleConnectionListener {

	void onBytes(byte[] data);

	/** The link dropped without being asked to. Not called after {@link BleCentral#disconnect(long)}. */
	void onDisconnected(String reason);
}
