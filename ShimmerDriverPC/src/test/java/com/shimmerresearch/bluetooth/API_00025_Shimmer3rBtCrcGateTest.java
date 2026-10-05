package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * DEV-976: Shimmer3R LogAndStream v0.00.002 to v1.00.010 turn the Bluetooth
 * link CRC off by themselves whenever sensing stops, and nothing tells the
 * host. The stop's own ACK still carries the CRC; every reply after it is bare,
 * so a driver still expecting the CRC loses sync after every stop. v1.00.011 is
 * the first release that keeps it. The driver must not turn a CRC on for the
 * earlier releases, at connect or when asked to.
 * <p>
 * Shimmer3 and Shimmer3R LogAndStream version numbers overlap, and no Shimmer3
 * firmware clears the CRC at a stop, so the gate has to check the hardware too.
 * <p>
 * No hardware: the connect path writes the default CRC mode only when
 * isBtCrcModeSupported() is true, so the gate and the instruction stack are
 * what these check. It lives in this package because the instruction stack is a
 * protected member of ShimmerBluetooth.
 *
 * @author Mark Nolan
 */
public class API_00025_Shimmer3rBtCrcGateTest {

	private static ShimmerPC device(int hardwareVersion, int fwMajor, int fwMinor, int fwInternal) {
		ShimmerPC device = new ShimmerPC("COM99");
		device.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(hardwareVersion, FW_ID.LOGANDSTREAM, fwMajor, fwMinor, fwInternal));
		return device;
	}

	private static String describe(ShimmerPC device) {
		return device.getHardwareVersionParsed() + " " + device.getFirmwareVersionParsed();
	}

	/** The argument of every SET_CRC_COMMAND on the instruction stack, in order */
	private static List<Integer> queuedCrcModes(ShimmerPC device) {
		List<Integer> modes = new ArrayList<Integer>();
		for(byte[] instruction:device.getListofInstructions()){
			if(instruction!=null && instruction[0]==ShimmerObject.SET_CRC_COMMAND){
				modes.add((int) instruction[1]);
			}
		}
		return modes;
	}

	private static void assertCrcRefused(ShimmerPC device) {
		String label = describe(device);
		assertFalse(label, device.isBtCrcModeSupported());
		assertFalse(label, device.writeBtCommsCrcMode(BT_CRC_MODE.ONE_BYTE_CRC));
		assertFalse(label, device.enableBtCommsOneByteCrc());
		assertFalse(label, device.enableBtCommsTwoByteCrc());
		assertEquals(label + ": nothing is sent", Arrays.asList(), queuedCrcModes(device));
	}

	private static void assertCrcAllowed(ShimmerPC device) {
		String label = describe(device);
		assertTrue(label, device.isBtCrcModeSupported());
		assertTrue(label, device.writeBtCommsCrcMode(BT_CRC_MODE.ONE_BYTE_CRC));
		assertEquals(label, Arrays.asList(BT_CRC_MODE.ONE_BYTE_CRC.ordinal()), queuedCrcModes(device));
	}

	@Test
	public void shimmer3rBeforeV1_00_011IsRefused() {
		assertCrcRefused(device(HW_ID.SHIMMER_3R, 1, 0, 10)); // the last release that clears it at a stop
		assertCrcRefused(device(HW_ID.SHIMMER_3R, 1, 0, 7));
		assertCrcRefused(device(HW_ID.SHIMMER_3R, 0, 0, 2)); // the first release
	}

	@Test
	public void shimmer3rFromV1_00_011IsAllowed() {
		assertCrcAllowed(device(HW_ID.SHIMMER_3R, 1, 0, 11));
		// v1.01.000: the internal number restarts at each minor release, so it
		// cannot decide on its own
		assertCrcAllowed(device(HW_ID.SHIMMER_3R, 1, 1, 0));
		assertCrcAllowed(device(HW_ID.SHIMMER_3R, 2, 0, 0));
	}

	@Test
	public void shimmer3IsUnchanged() {
		// The same version numbers on a Shimmer3, whose firmware clears the CRC
		// only at startup and on disconnect. v1.00.008 is also what the Shimmer3R
		// v1.00.008 side build reports, as it claims to be a Shimmer3.
		assertCrcAllowed(device(HW_ID.SHIMMER_3, 1, 0, 8));
		assertCrcAllowed(device(HW_ID.SHIMMER_3, 1, 0, 10));
		// The Shimmer3 floor for SET_CRC_COMMAND has not moved
		assertCrcAllowed(device(HW_ID.SHIMMER_3, 0, 13, 7));
		assertFalse(device(HW_ID.SHIMMER_3, 0, 13, 6).isBtCrcModeSupported());
	}

	@Test
	public void turningTheCrcOffIsNeverRefused() {
		ShimmerPC device = device(HW_ID.SHIMMER_3R, 1, 0, 10);
		assertTrue(device.writeBtCommsCrcMode(BT_CRC_MODE.OFF));
		assertTrue(device.disableBtCommsCrc());
		assertEquals(Arrays.asList(BT_CRC_MODE.OFF.ordinal(), BT_CRC_MODE.OFF.ordinal()), queuedCrcModes(device));
	}
}
