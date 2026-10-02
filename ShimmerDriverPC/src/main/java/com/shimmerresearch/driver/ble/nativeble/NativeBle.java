package com.shimmerresearch.driver.ble.nativeble;

/**
 * JNI declarations for the shimmerble native library, built from ShimmerBLENativeLib/ in this
 * repository.
 * <p>
 * This class is the contract with the native code. The Rust function names are derived from this
 * package and class name, and Rust constructs {@link NativeBleEvent} through its constructor, so
 * moving or renaming this class, or changing that constructor, breaks the native library. Keep
 * everything that may change elsewhere (see {@link NativeBleRadio} and {@link BleCentral}).
 * <p>
 * The library must be loaded with {@link NativeBleLoader#load()} before any of these are called.
 */
final class NativeBle {

	private NativeBle() {
	}

	static native String nativeVersion();

	/** Starts the native runtime and opens the first Bluetooth adapter. Safe to call more than once. */
	static native void init() throws NativeBleException;

	/** The adapter's power state, e.g. "PoweredOn". */
	static native String adapterState() throws NativeBleException;

	static native void startScan() throws NativeBleException;

	static native void stopScan() throws NativeBleException;

	/**
	 * Reports devices already connected to this machine that expose any of the services, as
	 * {@link NativeBleEvent#TYPE_DEVICE_FOUND} events. A connected device does not advertise, so a
	 * scan never finds it. Returns how many were found.
	 */
	static native int retrieveConnected(String[] serviceUuids) throws NativeBleException;

	/**
	 * Connects to a device previously reported by a scan, subscribes to {@code notifyUuid} and
	 * returns a handle for the other calls.
	 */
	static native long connect(String deviceId, String serviceUuid, String writeUuid, String notifyUuid,
			int timeoutMs) throws NativeBleException;

	/** Writes to the write characteristic, split into chunks that fit the negotiated MTU. */
	static native void write(long handle, byte[] data) throws NativeBleException;

	/** The negotiated ATT MTU, or 0 if the handle is not connected. */
	static native int mtu(long handle) throws NativeBleException;

	/** Closes the link. No {@link NativeBleEvent#TYPE_DISCONNECTED} event follows. */
	static native void disconnect(long handle) throws NativeBleException;

	/** The next event, or null if none arrived within {@code timeoutMs}. */
	static native NativeBleEvent nextEvent(int timeoutMs) throws NativeBleException;
}
