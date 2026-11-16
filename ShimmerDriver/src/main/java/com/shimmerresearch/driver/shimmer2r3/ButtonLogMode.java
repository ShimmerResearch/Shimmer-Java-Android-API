package com.shimmerresearch.driver.shimmer2r3;

import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;

/**
 * Configuration byte 232 (NV_BUTTON_LOG_MODE) on a Shimmer3 or Shimmer3R
 * running LogAndStream with the event channel (DEV-971): what the user button
 * does, what else may start SD logging, and whether the TTL input is recorded.
 *
 * <pre>
 *    bits 0-2  button mode   BUTTON_MODE_*
 *    bits 3-4  log triggers  LOG_TRIGGERS_*, read only when the button mode is not legacy
 *    bit 5     TTL input recorded, whatever the button mode
 *    bit 6     unused, cleared
 *    bit 7     must be 0
 * </pre>
 *
 * 0 means "as before": userButtonEnable (byte 217 bit 5) decides both what a
 * press does and whether leaving the dock starts logging. Anything the
 * firmware cannot act on reads as 0 too: bit 7 set (erased memory, and
 * firmware before the byte, is 0xFF), a reserved value, or log triggers under
 * the legacy button mode.
 *
 * A port of log-and-stream-common Configuration/shimmer_button_log_mode.c,
 * through the web SDK's devices/events/buttonLogMode.ts, kept rule for rule so
 * the driver reads every one of the 256 values as the firmware does. The table
 * and the reasons behind each rule are in log-and-stream-common
 * docs/SHIMMER3_CONFIGURATION_INFOMEM.md section 8.3.
 *
 * @author Mark Nolan
 */
public class ButtonLogMode {

	/** Offset of the byte in the configuration bytes (128 + 104) */
	public static final int CONFIG_BYTE_INDEX = 232;

	public static final int MASK_BUTTON_MODE = 0x07;
	public static final int BIT_SHIFT_LOG_TRIGGERS = 3;
	public static final int MASK_LOG_TRIGGERS = 0x18;
	public static final int MASK_TTL_INPUT = 0x20;
	public static final int MASK_INVALID = 0x80;

	/** userButtonEnable decides, as before the byte existed */
	public static final int BUTTON_MODE_LEGACY = 0;
	/** A press does nothing, and the LED shows nothing while it is held */
	public static final int BUTTON_MODE_DISABLED = 1;
	/** A press starts and stops logging */
	public static final int BUTTON_MODE_SINGLE_PRESS = 2;
	/** A double press starts and stops logging */
	public static final int BUTTON_MODE_DOUBLE_PRESS = 3;
	/** A double press starts and stops logging, and every press and release is recorded */
	public static final int BUTTON_MODE_DOUBLE_PRESS_RECORDED = 4;
	/** Every press and release is recorded, and a press never starts or stops logging */
	public static final int BUTTON_MODE_RECORDED = 5;

	/** A host command only */
	public static final int LOG_TRIGGERS_HOST = 0;
	/** A host command, or leaving the dock (on Shimmer3R, unplugging USB-C) */
	public static final int LOG_TRIGGERS_HOST_OR_UNDOCK = 1;
	/** Nothing: SD logging is off */
	public static final int LOG_TRIGGERS_NONE = 2;

	private static boolean isButtonModeBuilt(int buttonMode) {
		return buttonMode>=BUTTON_MODE_DISABLED && buttonMode<=BUTTON_MODE_RECORDED;
	}

	private static boolean canPressStartLogging(int buttonMode) {
		return buttonMode==BUTTON_MODE_SINGLE_PRESS
				|| buttonMode==BUTTON_MODE_DOUBLE_PRESS
				|| buttonMode==BUTTON_MODE_DOUBLE_PRESS_RECORDED;
	}

	/**
	 * The byte the firmware acts on, and writes back after a configuration
	 * write: raw itself when it is valid, 0 when bit 7 is set, and otherwise the
	 * button mode and log triggers cleared when either is not valid (a legacy
	 * byte's log triggers are not read, so they are cleared too). Bit 5 stands on
	 * its own unless bit 7 is set. Bit 6 is cleared.
	 */
	public static int correct(int raw) {
		int value = raw & 0xFF;
		if((value & MASK_INVALID)!=0){
			return 0;
		}
		int buttonMode = value & MASK_BUTTON_MODE;
		int logTriggers = (value & MASK_LOG_TRIGGERS) >> BIT_SHIFT_LOG_TRIGGERS;
		int ttlInput = value & MASK_TTL_INPUT;
		if(!isButtonModeBuilt(buttonMode) || logTriggers>LOG_TRIGGERS_NONE){
			return ttlInput;
		}
		return value & (MASK_BUTTON_MODE | MASK_LOG_TRIGGERS | MASK_TTL_INPUT);
	}

	/** Whether the firmware keeps the value as it is */
	public static boolean isValid(int raw) {
		return correct(raw)==(raw & 0xFF);
	}

	/** The button mode the firmware acts on (bits 0-2 of the corrected byte) */
	public static int getButtonMode(int raw) {
		return correct(raw) & MASK_BUTTON_MODE;
	}

	/** The log triggers the firmware acts on: 0 whenever the button mode is legacy */
	public static int getLogTriggers(int raw) {
		return (correct(raw) & MASK_LOG_TRIGGERS) >> BIT_SHIFT_LOG_TRIGGERS;
	}

	/** Whether the TTL input's edges are recorded */
	public static boolean isTtlInputEnabled(int raw) {
		return (correct(raw) & MASK_TTL_INPUT)!=0;
	}

	/** Whether every press and release goes to the event channel (modes 4 and 5) */
	public static boolean isRecordingButtonPresses(int raw) {
		int buttonMode = getButtonMode(raw);
		return buttonMode==BUTTON_MODE_DOUBLE_PRESS_RECORDED || buttonMode==BUTTON_MODE_RECORDED;
	}

	/** Whether SD logging is off: log triggers 2, which only a non-legacy mode reads */
	public static boolean isLoggingDisabled(int raw) {
		return getLogTriggers(raw)==LOG_TRIGGERS_NONE;
	}

	/**
	 * Whether a press (or, in the double-press modes, a double press) starts
	 * and stops logging. Logging off wins over a mode whose press would: the
	 * LED still shows the press, and nothing starts.
	 */
	public static boolean isPressStartingLogging(int raw, boolean userButtonEnable) {
		int buttonMode = getButtonMode(raw);
		if(buttonMode==BUTTON_MODE_LEGACY){
			return userButtonEnable;
		}
		return canPressStartLogging(buttonMode) && !isLoggingDisabled(raw);
	}

	/**
	 * Whether leaving the dock, or on Shimmer3R unplugging USB-C, starts
	 * logging. Never with single touch, where the sync centre's press starts
	 * every node.
	 */
	public static boolean isUndockStartingLogging(int raw, boolean userButtonEnable, boolean singleTouchStart) {
		if(singleTouchStart){
			return false;
		}
		if(getButtonMode(raw)==BUTTON_MODE_LEGACY){
			return !userButtonEnable;
		}
		return getLogTriggers(raw)==LOG_TRIGGERS_HOST_OR_UNDOCK;
	}

	/**
	 * The userButtonEnable the firmware holds byte 217 at for this byte, so a
	 * host or firmware that knows only byte 217 never falls back to an undock
	 * start. False only where a press cannot start logging and leaving the dock
	 * does; otherwise true. A legacy byte leaves userButtonEnable as it is.
	 */
	public static boolean getNearestUserButtonEnable(int raw, boolean userButtonEnable) {
		int buttonMode = getButtonMode(raw);
		if(buttonMode==BUTTON_MODE_LEGACY){
			return userButtonEnable;
		}
		return !(!canPressStartLogging(buttonMode) && getLogTriggers(raw)==LOG_TRIGGERS_HOST_OR_UNDOCK);
	}

	/**
	 * Whether single touch can work: it needs a press that can start logging.
	 * Firmware built with single touch clears singleTouchStart when this is
	 * false. No LogAndStream release is built with it, and each clears
	 * singleTouchStart whatever this byte holds.
	 */
	public static boolean isSingleTouchAllowed(int raw) {
		int buttonMode = getButtonMode(raw);
		if(buttonMode==BUTTON_MODE_LEGACY){
			return true;
		}
		return canPressStartLogging(buttonMode) && !isLoggingDisabled(raw);
	}

	/**
	 * The corrected byte with press recording taken out, as the firmware does
	 * on a board with no button edges to record (SR48-6-0): a recorded double
	 * press becomes a plain double press, and recording alone becomes disabled.
	 */
	public static int withoutPressRecording(int raw) {
		int fixed = correct(raw);
		int rest = fixed & ~MASK_BUTTON_MODE & 0xFF;
		switch(fixed & MASK_BUTTON_MODE){
			case BUTTON_MODE_DOUBLE_PRESS_RECORDED:
				return rest | BUTTON_MODE_DOUBLE_PRESS;
			case BUTTON_MODE_RECORDED:
				return rest | BUTTON_MODE_DISABLED;
			default:
				return fixed;
		}
	}

	/**
	 * The byte for a set of settings, corrected the way the firmware will
	 * correct it, so what a host writes is what it reads back. A legacy button
	 * mode drops the log triggers, and an out-of-range value makes the button
	 * mode and log triggers read as legacy while the TTL bit stands.
	 */
	public static int encode(int buttonMode, int logTriggers, boolean ttlInputEnabled) {
		return correct(compose(0, buttonMode, logTriggers, ttlInputEnabled));
	}

	/**
	 * The byte with its three fields replaced, before correction, so fields can
	 * be set one at a time in any order: log triggers set while the button mode
	 * is still legacy survive until the mode is set. Bits 6 and 7 come out
	 * clear; an out-of-range button mode or log triggers leaves that field 0.
	 */
	public static int compose(int raw, int buttonMode, int logTriggers, boolean ttlInputEnabled) {
		int value = (raw & MASK_INVALID)!=0 ? 0 : raw & (MASK_BUTTON_MODE | MASK_LOG_TRIGGERS | MASK_TTL_INPUT);
		value &= ~(MASK_BUTTON_MODE | MASK_LOG_TRIGGERS | MASK_TTL_INPUT);
		if(buttonMode>=0 && buttonMode<=MASK_BUTTON_MODE){
			value |= buttonMode;
		}
		if(logTriggers>=0 && logTriggers<=(MASK_LOG_TRIGGERS >> BIT_SHIFT_LOG_TRIGGERS)){
			value |= logTriggers << BIT_SHIFT_LOG_TRIGGERS;
		}
		if(ttlInputEnabled){
			value |= MASK_TTL_INPUT;
		}
		return value;
	}

	/** The raw fields of a byte, bit 7 making all of them 0, as a setter starts from them */
	public static int getRawButtonMode(int raw) {
		return (raw & MASK_INVALID)!=0 ? 0 : raw & MASK_BUTTON_MODE;
	}

	public static int getRawLogTriggers(int raw) {
		return (raw & MASK_INVALID)!=0 ? 0 : (raw & MASK_LOG_TRIGGERS) >> BIT_SHIFT_LOG_TRIGGERS;
	}

	public static boolean isRawTtlInputEnabled(int raw) {
		return (raw & MASK_INVALID)==0 && (raw & MASK_TTL_INPUT)!=0;
	}

	/**
	 * Whether a board's button edges reach the firmware. Not on SR48-6-0, a
	 * Shimmer3R whose button is wired to BOOT0, whose interrupt is used for dock
	 * detection (ShimBrd_isBoardSr48_6_0, log-and-stream-common
	 * Boards/shimmer_boards.c). A board whose identity is unknown gets it.
	 */
	public static boolean isButtonEdgeRecordingSupported(int hardwareVersion, ExpansionBoardDetails board) {
		return !isSr48_6_0(hardwareVersion, board);
	}

	/** Whether a board has the TTL input. Not on SR48-6-0 (ShimBrd_isTtlInputSupported). */
	public static boolean isTtlInputSupported(int hardwareVersion, ExpansionBoardDetails board) {
		return !isSr48_6_0(hardwareVersion, board);
	}

	private static boolean isSr48_6_0(int hardwareVersion, ExpansionBoardDetails board) {
		return hardwareVersion==HW_ID.SHIMMER_3R
				&& board!=null
				&& board.getExpansionBoardId()==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED
				&& board.getExpansionBoardRev()==6
				&& board.getExpansionBoardRevSpecial()==0;
	}

	/**
	 * The byte the firmware keeps on a board: corrected, with press recording
	 * and the TTL input taken out where the board has neither
	 * (ShimConfig_checkAndCorrectConfig, log-and-stream-common
	 * Configuration/shimmer_config.c).
	 */
	public static int correctForBoard(int raw, int hardwareVersion, ExpansionBoardDetails board) {
		int value = correct(raw);
		if(!isButtonEdgeRecordingSupported(hardwareVersion, board)){
			value = withoutPressRecording(value);
		}
		if(!isTtlInputSupported(hardwareVersion, board)){
			value &= ~MASK_TTL_INPUT & 0xFF;
		}
		return value;
	}

}
