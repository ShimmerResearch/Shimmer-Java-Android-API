package com.shimmerresearch.driver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;

/**
 * The SR-number rule that decides whether a Shimmer3R carries a BMP581 when
 * the Shimmer hasn't said so in-band (0xA7 NACKed or unanswered, and every SD
 * log file). It has to match the firmware's own rule,
 * ShimBrd_isBmp581PresentPerSrNumber() in log-and-stream-common
 * Boards/shimmer_boards.c, so the cases below are copied from that repo's
 * Test/host/test_boards.c test_bmp581_gate(). Before DEV-1110 the driver's rule
 * had no SR38 (Proto3 Deluxe) entry.
 *
 * @author Mark Nolan
 */
public class API_00020_Bmp581SrRuleTest {

	private static final ShimmerVerObject S3R_FW_1_1_6 = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 6);

	private static boolean rule(ShimmerVerObject svo, int srId, int rev, int revSpecial) {
		return ShimmerObject.isSupportedBmp581(svo, new ExpansionBoardDetails(srId, rev, revSpecial));
	}

	private static void check(int srId, int rev, int revSpecial, boolean expected, String why) {
		assertEquals("SR" + srId + "-" + rev + "-" + revSpecial + ": " + why, expected, rule(S3R_FW_1_1_6, srId, rev, revSpecial));
	}

	@Test
	public void imuSr31FromEleven2() {
		check(HW_ID_SR_CODES.SHIMMER3, 11, 1, false, "one minor below the line");
		check(HW_ID_SR_CODES.SHIMMER3, 11, 2, true, "the first board with it");
		check(HW_ID_SR_CODES.SHIMMER3, 11, 3, true, "a later minor keeps it");
		check(HW_ID_SR_CODES.SHIMMER3, 12, 0, true, "a later major keeps it");
		check(HW_ID_SR_CODES.SHIMMER3, 10, 9, false, "an earlier major never has it");
	}

	@Test
	public void proto3DeluxeSr38FromFour2() {
		check(HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, 4, 1, false, "one minor below the line");
		check(HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, 4, 2, true, "the first board with it");
		check(HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, 5, 0, true, "a later major keeps it");
	}

	@Test
	public void exgSr47FromEight2() {
		check(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 7, 2, false, "SR47 has no 7-2 dev build");
		check(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 8, 1, false, "one minor below the line");
		check(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 8, 2, true, "the first board with it");
		check(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 9, 0, true, "a later major keeps it");
	}

	@Test
	public void bridgeAmpSr49FromFour2() {
		check(HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED, 4, 1, false, "one minor below the line");
		check(HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED, 4, 2, true, "the first board with it");
	}

	@Test
	public void gsrSr48HasTwoWindows() {
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 6, 0, false, "the earliest proto");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 7, 0, false, "below the dev build");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 7, 1, false, "the BOOT0 ECO rev, still no BMP581");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 7, 2, true, "the dev build that carries it");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 7, 3, true, "above the dev build, same major");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 0, false, "LATER board, but back to the BMP390");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 1, false, "still the BMP390");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2, true, "production line picks it up again");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 3, true, "and keeps it");
		check(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 9, 0, true, "a later major keeps it");
	}

	@Test
	public void boardWithNoRule() {
		check(HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI, 9, 9, false, "no rule for this board ID");
	}

	@Test
	public void shimmer3HostNeverMatches() {
		// A daughter card can be moved between hosts: the same EEPROM bytes on a
		// Shimmer3 must not claim a BMP581.
		ShimmerVerObject s3 = new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 6);
		assertFalse(rule(s3, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2));
		assertFalse(rule(s3, HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, 4, 2));
		assertFalse(rule(s3, HW_ID_SR_CODES.SHIMMER3, 11, 2));
	}

	@Test
	public void unprogrammedCardNeverMatches() {
		// 0xFF,0xFF,0xFF would satisfy every ">=" if the board ID weren't matched first
		check(0xFF, 0xFF, 0xFF, false, "an unprogrammed card does not claim a BMP581");
		check(0x00, 0x00, 0x00, false, "nor does an empty one");
	}

	@Test
	public void firmwareGuard() {
		// Not in the firmware's test (the firmware knows its own version): the
		// pre-compensated output only exists from LogAndStream_Shimmer3R v1.01.006.
		assertFalse(rule(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 5), HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2));
		assertTrue(rule(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 6), HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2));
		assertTrue(rule(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 7), HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2));
		assertTrue(rule(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 2, 0), HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, 4, 2));
		assertFalse(rule(null, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2));
		assertFalse(ShimmerObject.isSupportedBmp581(S3R_FW_1_1_6, null));
	}

	@Test
	public void coefficientLengthPerSensorId() {
		assertEquals(22, ShimmerObject.getPressureCalibCoefficientByteLength(ShimmerObject.PRESSURE_SENSOR_ID.BMP180));
		assertEquals(24, ShimmerObject.getPressureCalibCoefficientByteLength(ShimmerObject.PRESSURE_SENSOR_ID.BMP280));
		assertEquals(21, ShimmerObject.getPressureCalibCoefficientByteLength(ShimmerObject.PRESSURE_SENSOR_ID.BMP390));
		assertEquals(0, ShimmerObject.getPressureCalibCoefficientByteLength(ShimmerObject.PRESSURE_SENSOR_ID.BMP581));
		assertEquals(-1, ShimmerObject.getPressureCalibCoefficientByteLength(4));
	}
}
