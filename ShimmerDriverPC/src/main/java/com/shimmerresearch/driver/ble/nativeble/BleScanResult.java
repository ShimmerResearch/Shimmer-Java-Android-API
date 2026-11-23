package com.shimmerresearch.driver.ble.nativeble;

/** A device seen while scanning. */
public final class BleScanResult {

	private final String mId;
	private final String mName;
	private final String mAddress;
	private final int mRssi;

	public BleScanResult(String id, String name, String address, int rssi) {
		mId = id;
		mName = name == null ? "" : name;
		mAddress = address == null ? "" : address;
		mRssi = rssi;
	}

	/** The ID to connect with: the MAC address on Windows, a CoreBluetooth UUID on macOS. */
	public String getId() {
		return mId;
	}

	/** The advertised name, e.g. "Shimmer3R-2F31-BLE". Empty if the device has not sent one yet. */
	public String getName() {
		return mName;
	}

	/** The MAC address, or empty where the platform hides it (macOS). */
	public String getAddress() {
		return mAddress;
	}

	/** The MAC address without separators, e.g. "E8EB1B713E36", or empty if unknown. */
	public String getMacId() {
		return mAddress.replace(":", "").toUpperCase();
	}

	/** Signal strength in dBm, or {@link NativeBleEvent#RSSI_UNKNOWN}. */
	public int getRssi() {
		return mRssi;
	}

	/** The profile to connect with, or null if this is not a known Shimmer device. */
	public BleUartProfile getProfile() {
		return BleUartProfile.fromDeviceName(mName);
	}

	@Override
	public String toString() {
		String where = mAddress.isEmpty() ? mId : mAddress;
		String signal = mRssi == NativeBleEvent.RSSI_UNKNOWN ? "" : " " + mRssi + " dBm";
		return (mName.isEmpty() ? "(no name)" : mName) + " [" + where + "]" + signal;
	}
}
