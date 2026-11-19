package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.DEVICE_TYPE;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.SH_SEARCH;

/**
 * A paired device is a Shimmer by its Bluetooth name (DEV-1389). NeuroSmart's
 * Shimmer3R units are renamed in the factory to Neurosmart-XXXX-BT and
 * Neurosmart-XXXX-BLE, XXXX being the end of the unit's MAC address. Only
 * that exact form is added; every name recognised before is unchanged.
 */
public class API_00050_BluetoothNameClassificationTest {

	private static DEVICE_TYPE classicType(String name) {
		return new BluetoothDeviceDetails("COM5", "E747AA7D2F31", name).mDeviceTypeDetected;
	}

	private static DEVICE_TYPE bleType(String name) {
		return new BluetoothDeviceDetails("N/A", "00000000-0000-0000-0000-e747aa7d2f31", name).mDeviceTypeDetected;
	}

	@Test
	public void aNeurosmartUnitIsAShimmer3OverClassicAndBle() {
		assertEquals(DEVICE_TYPE.SHIMMER3, classicType("Neurosmart-2F31-BT"));
		assertEquals(DEVICE_TYPE.SHIMMER3, bleType("Neurosmart-2F31-BLE"));
		assertEquals("the MAC suffix in either case", DEVICE_TYPE.SHIMMER3, classicType("Neurosmart-2f31-BT"));
	}

	@Test
	public void onlyTheExactNeurosmartFormIsAccepted() {
		String[] notOurs = {
				"Neurosmart",
				"Neurosmart-2F31",
				"Neurosmart-2F31-SPP",
				"Neurosmart-2F31-BT2",
				"Neurosmart-12345-BT",
				"Neurosmart-2G31-BT",
				"Neurosmart-2F31-BT ",
				"MyNeurosmart-2F31-BT",
				"neurosmart-2F31-BT",
				"NEUROSMART-2F31-BLE",
		};
		for(String name:notOurs) {
			assertEquals(name, DEVICE_TYPE.UNKOWN, classicType(name));
			assertFalse(name, SH_SEARCH.BT.isNeurosmartName(name));
		}
		assertFalse(SH_SEARCH.BT.isNeurosmartName(null));
	}

	@Test
	public void theNamesRecognisedBeforeAreUnchanged() {
		assertEquals(DEVICE_TYPE.SHIMMER3, classicType("Shimmer3-6813"));
		assertEquals(DEVICE_TYPE.SHIMMER3, classicType("Shimmer3R-2F31-BT"));
		assertEquals(DEVICE_TYPE.SHIMMER3, bleType("Shimmer3R-2F31-BLE"));
		assertEquals(DEVICE_TYPE.SHIMMER3, bleType("S3BLE-6813"));
		assertEquals(DEVICE_TYPE.SHIMMER4, classicType("Shimmer4-1A2B"));
		assertEquals(DEVICE_TYPE.SHIMMER_ECG_MD, classicType("ShimmerECGmd-1A2B"));
		assertEquals(DEVICE_TYPE.RN42, classicType("RN42-1A2B"));
		assertEquals(DEVICE_TYPE.VERISENSE, bleType("Verisense-19092501A2B3"));
		assertEquals(DEVICE_TYPE.NONIN_ONYX_II, classicType("Nonin_Medical_Inc._123456"));
		assertEquals(DEVICE_TYPE.UNKOWN, classicType("JBL Flip 5"));
	}

	@Test
	public void aNeurosmartNameUsedAsTheAddressIsKeptAsItIs() {
		// macOS connects by the device's name, which then stands in for its address
		BluetoothDeviceDetails device = new BluetoothDeviceDetails("Neurosmart-2F31-BLE", "Neurosmart-2F31-BLE", "Neurosmart-2F31-BLE");
		assertEquals("Neurosmart-2F31-BLE", device.mShimmerMacId);
		assertEquals(DEVICE_TYPE.SHIMMER3, device.mDeviceTypeDetected);
		BluetoothDeviceDetails shimmer = new BluetoothDeviceDetails("Shimmer3R-2F31-BLE", "Shimmer3R-2F31-BLE", "Shimmer3R-2F31-BLE");
		assertEquals("as before", "Shimmer3R-2F31-BLE", shimmer.mShimmerMacId);
	}

}
