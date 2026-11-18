package com.shimmerresearch.managers.bluetoothManager;

import static org.junit.Assert.*;

import org.junit.Test;

/** Which connection handles are serial ports, and so looked up as one (DEV-1389) */
public class SerialPortHandleTest {

	@Test
	public void aNeurosmartUnitsNameIsASerialPortOnMacOs() {
		assertTrue(ShimmerBluetoothManager.isSerialPortHandle("Neurosmart-2F31-BLE"));
		assertTrue(ShimmerBluetoothManager.isSerialPortHandle("Neurosmart-2F31-BT"));
	}

	@Test
	public void theHandlesRecognisedBeforeAreUnchanged() {
		assertTrue(ShimmerBluetoothManager.isSerialPortHandle("COM5"));
		assertTrue(ShimmerBluetoothManager.isSerialPortHandle("Shimmer3R-2F31-BLE"));
		assertFalse("a BLE address", ShimmerBluetoothManager.isSerialPortHandle("00000000-0000-0000-0000-e747aa7d2f31"));
		assertFalse("not the exact NeuroSmart form", ShimmerBluetoothManager.isSerialPortHandle("Neurosmart-2F31"));
	}

}
