package com.shimmerresearch.driver;

import static org.junit.Assert.*;

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import com.shimmerresearch.driver.shimmer2r3.ButtonLogMode;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;

/**
 * DEV-971: configuration byte 232, the button mode, log triggers and TTL
 * input. The driver has to read every value as the firmware does
 * (log-and-stream-common Configuration/shimmer_button_log_mode.c), so the
 * cases below are the table in that repo's
 * docs/SHIMMER3_CONFIGURATION_INFOMEM.md section 8.3, row for row: the 16
 * values the firmware keeps, what a press does, what starts logging, and the
 * userButtonEnable the firmware holds byte 217 at.
 *
 * @author Mark Nolan
 */
public class API_00036_ButtonLogModeTest {

	/** What a press does, in the table's words */
	private enum PRESS {
		/** As userButtonEnable says: the legacy mode */
		LEGACY,
		NOTHING,
		/** Nothing starts, but the LED shows the press: logging is off */
		LED_ONLY,
		STARTS_STOPS,
		DOUBLE_STARTS_STOPS,
	}

	/** One row of the table */
	private static class Row {
		final int value;
		final PRESS press;
		final boolean recorded;
		final boolean undockStarts;
		final boolean loggingOff;
		/** null: unchanged, the legacy mode */
		final Boolean userButtonEnableHeldAt;

		Row(int value, PRESS press, boolean recorded, boolean undockStarts, boolean loggingOff, Boolean userButtonEnableHeldAt) {
			this.value = value;
			this.press = press;
			this.recorded = recorded;
			this.undockStarts = undockStarts;
			this.loggingOff = loggingOff;
			this.userButtonEnableHeldAt = userButtonEnableHeldAt;
		}
	}

	private static final Row[] TABLE = {
		new Row(0x00, PRESS.LEGACY,              false, false, false, null),
		new Row(0x01, PRESS.NOTHING,             false, false, false, true),
		new Row(0x09, PRESS.NOTHING,             false, true,  false, false),
		new Row(0x11, PRESS.NOTHING,             false, false, true,  true),
		new Row(0x02, PRESS.STARTS_STOPS,        false, false, false, true),
		new Row(0x0A, PRESS.STARTS_STOPS,        false, true,  false, true),
		new Row(0x12, PRESS.LED_ONLY,            false, false, true,  true),
		new Row(0x03, PRESS.DOUBLE_STARTS_STOPS, false, false, false, true),
		new Row(0x0B, PRESS.DOUBLE_STARTS_STOPS, false, true,  false, true),
		new Row(0x13, PRESS.LED_ONLY,            false, false, true,  true),
		new Row(0x04, PRESS.DOUBLE_STARTS_STOPS, true,  false, false, true),
		new Row(0x0C, PRESS.DOUBLE_STARTS_STOPS, true,  true,  false, true),
		new Row(0x14, PRESS.NOTHING,             true,  false, true,  true),
		new Row(0x05, PRESS.NOTHING,             true,  false, false, true),
		new Row(0x0D, PRESS.NOTHING,             true,  true,  false, false),
		new Row(0x15, PRESS.NOTHING,             true,  false, true,  true),
	};

	private static String hex(int value) {
		return String.format("0x%02X", value);
	}

	/** Each row stands with bit 5 set too, which only turns the TTL input on */
	private static void checkRow(Row row, int value) {
		String name = hex(value);
		assertEquals(name + " is kept as it is", value, ButtonLogMode.correct(value));
		assertTrue(name + " is valid", ButtonLogMode.isValid(value));
		assertEquals(name + " TTL input", (value & 0x20)!=0, ButtonLogMode.isTtlInputEnabled(value));
		assertEquals(name + " presses recorded", row.recorded, ButtonLogMode.isRecordingButtonPresses(value));
		assertEquals(name + " logging off", row.loggingOff, ButtonLogMode.isLoggingDisabled(value));

		for(boolean userButtonEnable:new boolean[] {false, true}){
			String state = name + " with userButtonEnable " + userButtonEnable;
			boolean held = row.userButtonEnableHeldAt==null ? userButtonEnable : row.userButtonEnableHeldAt;
			assertEquals(state + ": the userButtonEnable held", held, ButtonLogMode.getNearestUserButtonEnable(value, userButtonEnable));

			boolean pressStarts = row.press==PRESS.STARTS_STOPS || row.press==PRESS.DOUBLE_STARTS_STOPS
					|| (row.press==PRESS.LEGACY && userButtonEnable);
			// The firmware acts on the userButtonEnable it holds
			assertEquals(state + ": a press starts logging", pressStarts, ButtonLogMode.isPressStartingLogging(value, held));

			boolean undockStarts = row.press==PRESS.LEGACY ? !userButtonEnable : row.undockStarts;
			assertEquals(state + ": leaving the dock starts logging", undockStarts, ButtonLogMode.isUndockStartingLogging(value, held, false));
			assertFalse(state + ": single touch suppresses the undock start", ButtonLogMode.isUndockStartingLogging(value, held, true));
		}

		// Single touch needs a press that can start logging
		boolean singleTouch = row.press==PRESS.LEGACY || row.press==PRESS.STARTS_STOPS || row.press==PRESS.DOUBLE_STARTS_STOPS;
		assertEquals(name + " single touch allowed", singleTouch, ButtonLogMode.isSingleTouchAllowed(value));
	}

	@Test
	public void theSixteenValuesTheFirmwareKeeps() {
		for(Row row:TABLE){
			checkRow(row, row.value);
		}
	}

	@Test
	public void eachWithTheTtlInputOn() {
		for(Row row:TABLE){
			checkRow(row, row.value | ButtonLogMode.MASK_TTL_INPUT);
		}
	}

	@Test
	public void theTableIsEveryValueTheFirmwareKeeps() {
		Set<Integer> valid = new TreeSet<Integer>();
		for(int value=0;value<256;value++){
			if(ButtonLogMode.isValid(value)){
				valid.add(value);
			}
		}
		Set<Integer> expected = new TreeSet<Integer>();
		for(Row row:TABLE){
			expected.add(row.value);
			expected.add(row.value | ButtonLogMode.MASK_TTL_INPUT);
		}
		assertEquals(expected, valid);
	}

	/** Restates the section 8.3 correction independently of ButtonLogMode, for all 256 values */
	@Test
	public void everyOtherValueIsCorrectedAsTheFirmwareDoes() {
		for(int value=0;value<256;value++){
			int expected;
			int buttonMode = value & 0x07;
			int logTriggers = (value >> 3) & 0x03;
			if((value & 0x80)!=0){
				expected = 0; // bit 7 set: the whole byte reads as 0, TTL input off
			}
			else if(buttonMode==0 || buttonMode>5 || logTriggers==3){
				expected = value & 0x20; // legacy, reserved: bits 0-4 cleared, TTL input as it is
			}
			else {
				expected = value & 0x3F; // bit 6 is unused and cleared
			}
			assertEquals(hex(value), expected, ButtonLogMode.correct(value));
			assertEquals(hex(value) + " button mode", expected & 0x07, ButtonLogMode.getButtonMode(value));
			assertEquals(hex(value) + " log triggers", (expected >> 3) & 0x03, ButtonLogMode.getLogTriggers(value));
		}
	}

	@Test
	public void erasedMemoryReadsAsLegacy() {
		assertEquals(0, ButtonLogMode.correct(0xFF));
		assertFalse(ButtonLogMode.isValid(0xFF));
		assertEquals("bit 7 takes the TTL input with it", 0, ButtonLogMode.correct(0xA0));
		assertEquals("a byte read from a Java byte[]", 0, ButtonLogMode.correct((byte) 0xFF));
	}

	@Test
	public void encodeIsWhatTheFirmwareKeeps() {
		assertEquals("the button off", 0x01, ButtonLogMode.encode(ButtonLogMode.BUTTON_MODE_DISABLED, ButtonLogMode.LOG_TRIGGERS_HOST, false));
		assertEquals("the button off and logging off", 0x11, ButtonLogMode.encode(ButtonLogMode.BUTTON_MODE_DISABLED, ButtonLogMode.LOG_TRIGGERS_NONE, false));
		assertEquals("legacy drops the log triggers", 0x00, ButtonLogMode.encode(ButtonLogMode.BUTTON_MODE_LEGACY, ButtonLogMode.LOG_TRIGGERS_NONE, false));
		assertEquals("the TTL input alone", 0x20, ButtonLogMode.encode(ButtonLogMode.BUTTON_MODE_LEGACY, ButtonLogMode.LOG_TRIGGERS_HOST, true));
		assertEquals("a reserved mode reads as legacy", 0x20, ButtonLogMode.encode(6, ButtonLogMode.LOG_TRIGGERS_HOST_OR_UNDOCK, true));
		assertEquals("reserved log triggers read as legacy", 0x00, ButtonLogMode.encode(ButtonLogMode.BUTTON_MODE_DISABLED, 3, false));
		assertEquals("an out-of-range mode is left 0", 0x00, ButtonLogMode.encode(9, ButtonLogMode.LOG_TRIGGERS_NONE, false));
	}

	@Test
	public void fieldsSetOneAtATimeInEitherOrder() {
		int triggersFirst = ButtonLogMode.compose(0, ButtonLogMode.BUTTON_MODE_LEGACY, ButtonLogMode.LOG_TRIGGERS_NONE, false);
		triggersFirst = ButtonLogMode.compose(triggersFirst, ButtonLogMode.BUTTON_MODE_DISABLED, ButtonLogMode.getRawLogTriggers(triggersFirst), false);
		assertEquals(0x11, triggersFirst);

		int modeFirst = ButtonLogMode.compose(0, ButtonLogMode.BUTTON_MODE_DISABLED, ButtonLogMode.LOG_TRIGGERS_HOST, false);
		modeFirst = ButtonLogMode.compose(modeFirst, ButtonLogMode.getRawButtonMode(modeFirst), ButtonLogMode.LOG_TRIGGERS_NONE, false);
		assertEquals(0x11, modeFirst);

		assertEquals("erased memory gives nothing to keep", 0x01, ButtonLogMode.compose(0xFF, ButtonLogMode.BUTTON_MODE_DISABLED, ButtonLogMode.getRawLogTriggers(0xFF), ButtonLogMode.isRawTtlInputEnabled(0xFF)));
	}

	@Test
	public void sr48_6_0HasNoButtonEdgesAndNoTtlInput() {
		ExpansionBoardDetails sr48_6_0 = new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 6, 0);
		assertEquals("recorded double press becomes double press", 0x03, ButtonLogMode.correctForBoard(0x04, HW_ID.SHIMMER_3R, sr48_6_0));
		assertEquals("recorded alone becomes disabled", 0x11, ButtonLogMode.correctForBoard(0x15, HW_ID.SHIMMER_3R, sr48_6_0));
		assertEquals("the TTL input goes", 0x0B, ButtonLogMode.correctForBoard(0x2C, HW_ID.SHIMMER_3R, sr48_6_0));
		assertEquals("a mode without recording stays", 0x02, ButtonLogMode.correctForBoard(0x02, HW_ID.SHIMMER_3R, sr48_6_0));

		ExpansionBoardDetails sr48_7_0 = new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 7, 0);
		assertEquals("a later SR48 has both", 0x24, ButtonLogMode.correctForBoard(0x24, HW_ID.SHIMMER_3R, sr48_7_0));
		assertEquals("the rule is Shimmer3R's", 0x24, ButtonLogMode.correctForBoard(0x24, HW_ID.SHIMMER_3, sr48_6_0));
		assertEquals("an unknown board has both", 0x24, ButtonLogMode.correctForBoard(0x24, HW_ID.SHIMMER_3R, null));
	}

}
