package com.shimmerresearch.driver.ble.nativeble;

/** {@link BleRadio} over the shimmerble native library. */
public class NativeBleRadio implements BleRadio {

	/** Loads the native library and opens the Bluetooth adapter. */
	public NativeBleRadio() throws NativeBleException {
		NativeBleLoader.load();
		NativeBle.init();
	}

	/** The adapter's power state, e.g. "PoweredOn". */
	public String getAdapterState() throws NativeBleException {
		return NativeBle.adapterState();
	}

	@Override
	public void startScan() throws NativeBleException {
		NativeBle.startScan();
	}

	@Override
	public void stopScan() throws NativeBleException {
		NativeBle.stopScan();
	}

	@Override
	public int retrieveConnected(String[] serviceUuids) throws NativeBleException {
		return NativeBle.retrieveConnected(serviceUuids);
	}

	@Override
	public long connect(String deviceId, BleUartProfile profile, int timeoutMs) throws NativeBleException {
		return NativeBle.connect(deviceId, profile.serviceUuid, profile.writeUuid, profile.notifyUuid, timeoutMs);
	}

	@Override
	public void write(long handle, byte[] data) throws NativeBleException {
		NativeBle.write(handle, data);
	}

	@Override
	public int mtu(long handle) throws NativeBleException {
		return NativeBle.mtu(handle);
	}

	@Override
	public void disconnect(long handle) throws NativeBleException {
		NativeBle.disconnect(handle);
	}

	@Override
	public NativeBleEvent nextEvent(int timeoutMs) throws NativeBleException {
		return NativeBle.nextEvent(timeoutMs);
	}
}
