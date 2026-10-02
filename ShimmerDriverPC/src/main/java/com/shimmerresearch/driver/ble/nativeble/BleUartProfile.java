package com.shimmerresearch.driver.ble.nativeble;

/**
 * The BLE service each device family uses as a serial byte pipe: the host writes commands to one
 * characteristic and receives data as notifications on another.
 * <p>
 * The UUIDs match those used by the gRPC BLE servers (Shimmer-C-API ShimmerBLEGrpc).
 */
public enum BleUartProfile {

	SHIMMER3R("Shimmer3R",
			"65333333-a115-11e2-9e9a-0800200ca100",
			"65333333-a115-11e2-9e9a-0800200ca102",
			"65333333-a115-11e2-9e9a-0800200ca101"),
	/** Microchip transparent UART, used by the Shimmer3 BLE radio. */
	SHIMMER3("Shimmer3",
			"49535343-fe7d-4ae5-8fa9-9fafd205e455",
			"49535343-8841-43f4-a8d4-ecbe34729bb3",
			"49535343-1e4d-4bd9-ba61-23c647249616"),
	/** Nordic UART Service. */
	VERISENSE("Verisense",
			"6e400001-b5a3-f393-e0a9-e50e24dcca9e",
			"6e400002-b5a3-f393-e0a9-e50e24dcca9e",
			"6e400003-b5a3-f393-e0a9-e50e24dcca9e");

	public final String label;
	public final String serviceUuid;
	/** The characteristic the host writes commands to. */
	public final String writeUuid;
	/** The characteristic the device sends data on. */
	public final String notifyUuid;

	BleUartProfile(String label, String serviceUuid, String writeUuid, String notifyUuid) {
		this.label = label;
		this.serviceUuid = serviceUuid;
		this.writeUuid = writeUuid;
		this.notifyUuid = notifyUuid;
	}

	/**
	 * Picks the profile from an advertised name such as "Shimmer3R-2F31-BLE", or returns null if
	 * the name is not a known device family.
	 */
	public static BleUartProfile fromDeviceName(String name) {
		if (name == null) {
			return null;
		}
		// Shimmer3R first: "Shimmer3R" also contains "Shimmer".
		if (name.contains("Shimmer3R")) {
			return SHIMMER3R;
		}
		if (name.contains("Verisense")) {
			return VERISENSE;
		}
		if (name.contains("Shimmer")) {
			return SHIMMER3;
		}
		return null;
	}

	/** True for the families {@code ShimmerBLENative} drives (Shimmer3 and Shimmer3R). */
	public boolean isShimmer3Family() {
		return this == SHIMMER3 || this == SHIMMER3R;
	}
}
