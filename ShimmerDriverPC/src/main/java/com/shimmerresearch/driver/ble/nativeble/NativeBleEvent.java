package com.shimmerresearch.driver.ble.nativeble;

import com.shimmerresearch.driverUtilities.UtilShimmer;

/**
 * An event from the native BLE library, in the order it happened.
 * <p>
 * Constructed by the native code: the constructor signature and the TYPE_* values are part of the
 * contract with ShimmerBLENativeLib/src/lib.rs and must not change on one side only.
 */
public final class NativeBleEvent {

	/** A device was seen while scanning. Repeats as advertisements arrive. */
	public static final int TYPE_DEVICE_FOUND = 1;
	/** Bytes notified by a connected device. */
	public static final int TYPE_BYTES = 2;
	/** The link dropped without being asked to. */
	public static final int TYPE_DISCONNECTED = 3;

	/** {@link #rssi} when the platform did not report one. */
	public static final int RSSI_UNKNOWN = Integer.MIN_VALUE;

	public final int type;
	/** The connection handle, or 0 for scan events. */
	public final long handle;
	/** Device ID (scan events only): the MAC address on Windows, a CoreBluetooth UUID on macOS. */
	public final String id;
	/** Advertised name (scan events only). */
	public final String name;
	/** MAC address (scan events only), empty where the platform hides it. */
	public final String address;
	public final int rssi;
	/** Notified bytes ({@link #TYPE_BYTES} only). */
	public final byte[] data;
	/** Why the link dropped ({@link #TYPE_DISCONNECTED} only). */
	public final String message;

	public NativeBleEvent(int type, long handle, String id, String name, String address, int rssi, byte[] data,
			String message) {
		this.type = type;
		this.handle = handle;
		this.id = id;
		this.name = name;
		this.address = address;
		this.rssi = rssi;
		this.data = data;
		this.message = message;
	}

	public static NativeBleEvent deviceFound(String id, String name, String address, int rssi) {
		return new NativeBleEvent(TYPE_DEVICE_FOUND, 0, id, name, address, rssi, null, null);
	}

	public static NativeBleEvent bytes(long handle, byte[] data) {
		return new NativeBleEvent(TYPE_BYTES, handle, null, null, null, RSSI_UNKNOWN, data, null);
	}

	public static NativeBleEvent disconnected(long handle, String reason) {
		return new NativeBleEvent(TYPE_DISCONNECTED, handle, null, null, null, RSSI_UNKNOWN, null, reason);
	}

	@Override
	public String toString() {
		switch (type) {
		case TYPE_DEVICE_FOUND:
			return "DeviceFound[" + name + " id=" + id + " address=" + address + " rssi=" + rssi + "]";
		case TYPE_BYTES:
			return "Bytes[handle=" + handle + " " + UtilShimmer.bytesToHexStringWithSpacesFormatted(data) + "]";
		case TYPE_DISCONNECTED:
			return "Disconnected[handle=" + handle + " " + message + "]";
		default:
			return "NativeBleEvent[type=" + type + "]";
		}
	}
}
