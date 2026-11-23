package com.shimmerresearch.pcDriver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.driver.ble.nativeble.ByteCommunicationNativeBle;
import com.shimmerresearch.shimmer3.communication.ByteCommunicationJSSC;

/** Which transport ShimmerPC opens: its COM port, or native BLE once a BLE device is set (DEV-1504) */
public class API_00054_ShimmerPCTransportTest {

	private static final BleScanResult DEVICE = new BleScanResult("E7:47:AA:7D:2F:31", "Shimmer3R-2F31-BLE", "E7:47:AA:7D:2F:31", -50);

	@Test
	public void theComPortByDefault() {
		ShimmerPC shimmer = new ShimmerPC("COM32");
		assertNull(shimmer.getNativeBleDevice());
		assertTrue(shimmer.createByteCommunication("COM32") instanceof ByteCommunicationJSSC);
	}

	@Test
	public void nativeBleOnceABleDeviceIsSet() {
		ShimmerPC shimmer = new ShimmerPC("COM32");
		shimmer.setNativeBleDevice(DEVICE);
		assertSame(DEVICE, shimmer.getNativeBleDevice());
		ByteCommunicationNativeBle transport = (ByteCommunicationNativeBle) shimmer.createByteCommunication("COM32");
		assertEquals("Shimmer3R-2F31-BLE", transport.getDeviceName());
	}

	@Test
	public void theComPortAgainOnceTheBleDeviceIsCleared() {
		ShimmerPC shimmer = new ShimmerPC("COM32");
		shimmer.setNativeBleDevice(DEVICE);
		shimmer.setNativeBleDevice(null);
		assertTrue(shimmer.createByteCommunication("COM32") instanceof ByteCommunicationJSSC);
	}

	@Test
	public void theComPortStaysTheDevicesIdentityOverBle() {
		ShimmerPC shimmer = new ShimmerPC("COM32");
		shimmer.setNativeBleDevice(DEVICE);
		assertEquals("COM32", shimmer.getComPort());
	}

}
