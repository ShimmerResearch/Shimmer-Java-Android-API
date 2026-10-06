package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * DEV-1143: Shimmer3R LogAndStream v1.00.024 to v1.00.049 build the unsolicited
 * status push in a six-byte buffer. With a 2-byte link CRC the push is seven
 * bytes, so the sensor hardfaults the next time it pushes: on docking or
 * undocking, the user button, a trial-duration expiry or a low battery (DEV-621,
 * fixed in v1.00.050). The driver must never send those releases a 2-byte
 * SET_CRC_COMMAND: a 2-byte default falls back to 1 byte when connecting, and
 * asking for 2 bytes is refused.
 * <p>
 * The overrun needs two status bytes, which start at v1.00.024 and which no
 * Shimmer3 sends, so a Shimmer3 with the same version numbers keeps 2 bytes.
 * <p>
 * No hardware: the connect path writes getBtCrcModeToUseOnConnect(), so that
 * and the instruction stack are what these check. It lives in this package
 * because both are protected members of ShimmerBluetooth.
 *
 * @author Mark Nolan
 */
public class API_00026_Shimmer3rTwoByteCrcGateTest {

	private final BT_CRC_MODE mDefaultBtCrcMode = ShimmerBluetooth.getDefaultBtCrcModeIfFwSupported();

	@After
	public void restoreDefaultBtCrcMode() {
		// The default is static, so one set here would carry into later tests
		ShimmerBluetooth.setDefaultBtCrcModeToUseIfFwSupported(mDefaultBtCrcMode);
	}

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

	private static ShimmerPC[] overrunning() {
		return new ShimmerPC[] {
				device(HW_ID.SHIMMER_3R, 1, 0, 24), // the first with two status bytes
				device(HW_ID.SHIMMER_3R, 1, 0, 49)  // the last with selfcmd[6]
		};
	}

	private static ShimmerPC[] withRoom() {
		return new ShimmerPC[] {
				device(HW_ID.SHIMMER_3R, 1, 0, 23), // one status byte
				device(HW_ID.SHIMMER_3R, 1, 0, 50), // the buffer fix
				// The internal number restarts at each minor release, so it cannot
				// decide on its own
				device(HW_ID.SHIMMER_3R, 1, 1, 0),
				device(HW_ID.SHIMMER_3, 1, 0, 30)   // one status byte
		};
	}

	@Test
	public void twoByteCrcIsRefusedWhereItOverrunsThePush() {
		for(ShimmerPC device:overrunning()){
			String label = describe(device);
			assertTrue(label, device.isTwoByteCrcOverrunningStatusPush());
			assertFalse(label, device.enableBtCommsTwoByteCrc());
			assertFalse(label, device.writeBtCommsCrcMode(BT_CRC_MODE.TWO_BYTE_CRC));
			assertEquals(label + ": nothing is sent", Arrays.asList(), queuedCrcModes(device));
		}
	}

	@Test
	public void oneByteCrcAndOffAreStillSentWhereTwoBytesOverrun() {
		for(ShimmerPC device:overrunning()){
			String label = describe(device);
			assertTrue(label, device.enableBtCommsOneByteCrc());
			assertTrue(label, device.disableBtCommsCrc());
			assertEquals(label, Arrays.asList(BT_CRC_MODE.ONE_BYTE_CRC.ordinal(), BT_CRC_MODE.OFF.ordinal()), queuedCrcModes(device));
		}
	}

	@Test
	public void twoByteCrcIsSentWhereThePushHasRoom() {
		for(ShimmerPC device:withRoom()){
			String label = describe(device);
			assertFalse(label, device.isTwoByteCrcOverrunningStatusPush());
			assertTrue(label, device.enableBtCommsTwoByteCrc());
			assertEquals(label, Arrays.asList(BT_CRC_MODE.TWO_BYTE_CRC.ordinal()), queuedCrcModes(device));
		}
	}

	@Test
	public void twoByteDefaultFallsBackToOneByteWhenConnecting() {
		ShimmerBluetooth.setDefaultBtCrcModeToUseIfFwSupported(BT_CRC_MODE.TWO_BYTE_CRC);
		for(ShimmerPC device:overrunning()){
			assertEquals(describe(device), BT_CRC_MODE.ONE_BYTE_CRC, device.getBtCrcModeToUseOnConnect());
		}
		// Only that connection falls back: the setting stands for the next device
		assertEquals(BT_CRC_MODE.TWO_BYTE_CRC, ShimmerBluetooth.getDefaultBtCrcModeIfFwSupported());
		for(ShimmerPC device:withRoom()){
			assertEquals(describe(device), BT_CRC_MODE.TWO_BYTE_CRC, device.getBtCrcModeToUseOnConnect());
		}
	}

	@Test
	public void otherDefaultsAreUnchangedWhenConnecting() {
		for(BT_CRC_MODE btCrcMode:new BT_CRC_MODE[] {BT_CRC_MODE.OFF, BT_CRC_MODE.ONE_BYTE_CRC}){
			ShimmerBluetooth.setDefaultBtCrcModeToUseIfFwSupported(btCrcMode);
			for(ShimmerPC device:overrunning()){
				assertEquals(describe(device), btCrcMode, device.getBtCrcModeToUseOnConnect());
			}
		}
	}
}
