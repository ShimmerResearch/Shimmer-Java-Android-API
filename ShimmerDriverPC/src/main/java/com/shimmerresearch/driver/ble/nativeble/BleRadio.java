package com.shimmerresearch.driver.ble.nativeble;

/**
 * The BLE operations {@link BleCentral} needs. {@link NativeBleRadio} implements them over the
 * native library; tests substitute a fake so they never load native code.
 */
public interface BleRadio {

	void startScan() throws NativeBleException;

	void stopScan() throws NativeBleException;

	/** Reports already-connected devices exposing any of the services as scan results; returns how many. */
	int retrieveConnected(String[] serviceUuids) throws NativeBleException;

	long connect(String deviceId, BleUartProfile profile, int timeoutMs) throws NativeBleException;

	void write(long handle, byte[] data) throws NativeBleException;

	int mtu(long handle) throws NativeBleException;

	void disconnect(long handle) throws NativeBleException;

	/** The next event, or null if none arrived within {@code timeoutMs}. */
	NativeBleEvent nextEvent(int timeoutMs) throws NativeBleException;
}
