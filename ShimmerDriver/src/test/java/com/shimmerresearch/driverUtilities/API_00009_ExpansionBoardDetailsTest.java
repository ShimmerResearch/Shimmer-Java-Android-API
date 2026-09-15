package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;

/**
 * Tests for {@link ExpansionBoardDetails#isExpansionBoardValid()}.
 * 
 * <p>DEV-1019: a Shimmer3 with no expansion board EEPROM fitted used to report
 * its daughter-card ID as all 0x00 and now reports all 0xFF, so both have to be
 * treated as "no board details available".
 * 
 * @author Mark Nolan
 *
 */
public class API_00009_ExpansionBoardDetailsTest {

	private static final Logger logger = Logger.getLogger(API_00009_ExpansionBoardDetailsTest.class.getName());

	/** An unprogrammed EEPROM reads back as 0xFF - the DEV-1019 firmware behaviour. */
	@Test
	public void testAllFfIsInvalid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(0xFF, 0xFF, 0xFF);
		assertFalse(expBrd.isExpansionBoardValid());
		logger.info("Executing testAllFfIsInvalid -> PASS");
	}

	/** The same board as read off the wire, where the bytes are masked with 0xFF. */
	@Test
	public void testAllFfFromByteArrayIsInvalid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF});
		assertFalse(expBrd.isExpansionBoardValid());
		logger.info("Executing testAllFfFromByteArrayIsInvalid -> PASS");
	}

	/** The 16-byte array Consensys writes when clearing an expansion board's details. */
	@Test
	public void testGeneratedEmptyArrayIsInvalid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(ExpansionBoardDetails.generateExpBrdIdArrayEmpty());
		assertFalse(expBrd.isExpansionBoardValid());
		logger.info("Executing testGeneratedEmptyArrayIsInvalid -> PASS");
	}

	/** Pre-DEV-1019 firmware, and older database rows, carry zeros for the same state. */
	@Test
	public void testAllZerosIsInvalid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(0, 0, 0);
		assertFalse(expBrd.isExpansionBoardValid());
		logger.info("Executing testAllZerosIsInvalid -> PASS");
	}

	/** Nothing read yet - the default state of the class. */
	@Test
	public void testUnknownIsInvalid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails();
		assertFalse(expBrd.isExpansionBoardValid());
		logger.info("Executing testUnknownIsInvalid -> PASS");
	}

	@Test
	public void testLogFileIsInvalid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(
				HW_ID_SR_CODES.LOG_FILE, HW_ID_SR_CODES.LOG_FILE, HW_ID_SR_CODES.LOG_FILE);
		assertFalse(expBrd.isExpansionBoardValid());
		logger.info("Executing testLogFileIsInvalid -> PASS");
	}

	/** A real, programmed board stays valid. */
	@Test
	public void testProgrammedBoardIsValid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 4, 0);
		assertTrue(expBrd.isExpansionBoardValid());
		logger.info("Executing testProgrammedBoardIsValid -> PASS");
	}

	@Test
	public void testProgrammedBoardFromGeneratedArrayIsValid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(
				ExpansionBoardDetails.generateExpBrdIdArray(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 4, 0));
		assertTrue(expBrd.isExpansionBoardValid());
		logger.info("Executing testProgrammedBoardFromGeneratedArrayIsValid -> PASS");
	}

	/**
	 * Only an all-0xFF triplet is rejected. SR255.1.0 is the "this Shimmer3 has
	 * no expansion board" entry that the database layer synthesises, and it has
	 * to stay valid or playback and config review break for those recordings.
	 */
	@Test
	public void testSrNoneWithRealRevisionIsValid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(HW_ID_SR_CODES.NONE, 1, 0);
		assertTrue(expBrd.isExpansionBoardValid());
		logger.info("Executing testSrNoneWithRealRevisionIsValid -> PASS");
	}

	/** A single 0xFF field is not enough to invalidate a board with a real SR number. */
	@Test
	public void testProgrammedBoardWithFfSpecialRevIsValid() {
		ExpansionBoardDetails expBrd = new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, 4, 0xFF);
		assertTrue(expBrd.isExpansionBoardValid());
		logger.info("Executing testProgrammedBoardWithFfSpecialRevIsValid -> PASS");
	}

}
