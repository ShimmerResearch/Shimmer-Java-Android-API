package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.shimmer2r3.ButtonLogMode;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * DEV-971: configuration byte 232, the button mode, log triggers and TTL
 * input, through a full configuration write. The write builds its image from
 * zeros, so before DEV-971 every configure from Consensys or Android put 0
 * there and turned a Shimmer's button back on. It now carries the byte read
 * from the Shimmer, or the one set, holds userButtonEnable at the button
 * mode's nearest equivalent and clears singleTouchStart, as the firmware does.
 * Before anything is read, a Bluetooth write leaves the byte out, so the
 * Shimmer keeps its own.
 * <p>
 * No hardware. It lives in this package because the instruction list is a
 * protected member of ShimmerBluetooth.
 *
 * @author Mark Nolan
 */
public class API_00046_ButtonLogModeConfigWriteTest {

	private static final int IDX_BUTTON_LOG_MODE = 232;
	private static final int IDX_TRIAL_CONFIG0 = 217;
	private static final int IDX_TRIAL_CONFIG1 = 218;
	private static final int USER_BUTTON_ENABLE = 0x20;
	private static final int SINGLE_TOUCH_START = 0x80;

	/** A Shimmer3R on a release whose builds can carry the event channel */
	private static final ShimmerVerObject S3R_EVENT_CHANNEL_CAPABLE = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);

	/** Lets a test give the probe's answer */
	private static class ProbedShimmer extends ShimmerPC {
		private static final long serialVersionUID = 1L;
		ProbedShimmer() {
			super("COM99");
		}
		void probeAnswered(Boolean isEventChannelSupported) {
			setEventChannelSupported(isEventChannelSupported);
		}
	}

	private static ProbedShimmer shimmer(ShimmerVerObject svo) {
		ProbedShimmer shimmer = new ProbedShimmer();
		shimmer.setShimmerVersionObjectAndCreateSensorMap(svo);
		return shimmer;
	}

	/**
	 * A configuration as a Shimmer on that firmware would answer it, with the
	 * given bytes 232, 217 and 218: the image a second, unread Shimmer makes
	 */
	private static byte[] configFromShimmer(ShimmerVerObject svo, int buttonLogMode, int trialConfig0, int trialConfig1) {
		ShimmerPC source = new ShimmerPC("COM98");
		source.setShimmerVersionObjectAndCreateSensorMap(svo);
		byte[] config = source.generateConfigBytesForWritingToShimmer().clone();
		config[IDX_BUTTON_LOG_MODE] = (byte) buttonLogMode;
		config[IDX_TRIAL_CONFIG0] = (byte) trialConfig0;
		config[IDX_TRIAL_CONFIG1] = (byte) trialConfig1;
		return config;
	}

	private static ProbedShimmer readFrom(ShimmerVerObject svo, int buttonLogMode, int trialConfig0, int trialConfig1) {
		ProbedShimmer shimmer = shimmer(svo);
		shimmer.configBytesParse(configFromShimmer(svo, buttonLogMode, trialConfig0, trialConfig1), COMMUNICATION_TYPE.BLUETOOTH);
		return shimmer;
	}

	private static int written(ShimmerPC shimmer, int idx) {
		return shimmer.generateConfigBytesForWritingToShimmer()[idx] & 0xFF;
	}

	private static String hex(int value) {
		return String.format("0x%02X", value);
	}

	@Test
	public void theByteReadGoesBackOnAFullWrite() {
		ProbedShimmer shimmer = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x11, USER_BUTTON_ENABLE, 0);
		assertTrue(shimmer.isButtonLogModeKnown());
		assertEquals(ButtonLogMode.BUTTON_MODE_DISABLED, shimmer.getButtonMode());
		assertEquals(ButtonLogMode.LOG_TRIGGERS_NONE, shimmer.getLogTriggers());
		assertEquals("before DEV-971 this was 0", 0x11, written(shimmer, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void everyValueTheFirmwareKeepsGoesBack() {
		for(int value=0;value<256;value++){
			if(!ButtonLogMode.isValid(value)){
				continue;
			}
			ProbedShimmer shimmer = readFrom(S3R_EVENT_CHANNEL_CAPABLE, value, USER_BUTTON_ENABLE, 0);
			assertEquals(hex(value), value, written(shimmer, IDX_BUTTON_LOG_MODE));
		}
	}

	@Test
	public void userButtonEnableIsHeldAtTheModesEquivalent() {
		ProbedShimmer undockStart = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x09, USER_BUTTON_ENABLE, 0);
		undockStart.setButtonStart(true);
		assertEquals("0x09: nothing on a press, logging on an undock", 0, written(undockStart, IDX_TRIAL_CONFIG0) & USER_BUTTON_ENABLE);
		assertFalse(undockStart.isButtonStart());

		ProbedShimmer disabled = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x01, 0, 0);
		assertEquals("0x01: held at 1, so firmware without the byte never adds an undock start", USER_BUTTON_ENABLE, written(disabled, IDX_TRIAL_CONFIG0) & USER_BUTTON_ENABLE);
		assertTrue(disabled.isButtonStart());

		ProbedShimmer legacy = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x00, 0, 0);
		assertEquals("legacy leaves it as it is", 0, written(legacy, IDX_TRIAL_CONFIG0) & USER_BUTTON_ENABLE);
	}

	/** LogAndStream is built without single touch, and clears it whatever byte 232 holds */
	@Test
	public void singleTouchIsClearedInEveryMode() {
		int[] values = {
				0x00,	// legacy
				0x01,	// no press can start logging
				0x12,	// a press that could, but logging is off
				0x02,	// a press that starts logging
				0x03,	// a double press that does
		};
		for(int value:values){
			ProbedShimmer shimmer = readFrom(S3R_EVENT_CHANNEL_CAPABLE, value, USER_BUTTON_ENABLE, SINGLE_TOUCH_START);
			shimmer.setSingleTouch(true);
			assertEquals(hex(value), 0, written(shimmer, IDX_TRIAL_CONFIG1) & SINGLE_TOUCH_START);
			assertFalse(hex(value), shimmer.isSingleTouch());
		}
	}

	@Test
	public void aValueSetIsWrittenWhateverTheOrderOfTheFields() {
		ProbedShimmer modeFirst = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x00, USER_BUTTON_ENABLE, 0);
		modeFirst.setButtonMode(ButtonLogMode.BUTTON_MODE_DISABLED);
		modeFirst.setLogTriggers(ButtonLogMode.LOG_TRIGGERS_NONE);
		assertEquals(0x11, written(modeFirst, IDX_BUTTON_LOG_MODE));

		ProbedShimmer triggersFirst = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x00, USER_BUTTON_ENABLE, 0);
		triggersFirst.setLogTriggers(ButtonLogMode.LOG_TRIGGERS_NONE);
		assertEquals("legacy does not read them yet", ButtonLogMode.LOG_TRIGGERS_HOST, triggersFirst.getLogTriggers());
		triggersFirst.setButtonMode(ButtonLogMode.BUTTON_MODE_DISABLED);
		assertEquals(ButtonLogMode.LOG_TRIGGERS_NONE, triggersFirst.getLogTriggers());
		assertEquals(0x11, written(triggersFirst, IDX_BUTTON_LOG_MODE));

		ProbedShimmer ttl = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x11, USER_BUTTON_ENABLE, 0);
		ttl.setTtlInputEnabled(true);
		assertEquals("the other fields stay", 0x31, written(ttl, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void theConfigLabelsSetAndGetTheFields() {
		ProbedShimmer shimmer = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x00, USER_BUTTON_ENABLE, 0);
		shimmer.setConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.LOG_TRIGGERS, ButtonLogMode.LOG_TRIGGERS_NONE);
		shimmer.setConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.BUTTON_MODE, ButtonLogMode.BUTTON_MODE_RECORDED);
		shimmer.setConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.TTL_INPUT, true);
		assertEquals(0x35, written(shimmer, IDX_BUTTON_LOG_MODE));
		assertEquals(ButtonLogMode.BUTTON_MODE_RECORDED, shimmer.getConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.BUTTON_MODE));
		assertEquals(ButtonLogMode.LOG_TRIGGERS_NONE, shimmer.getConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.LOG_TRIGGERS));
		assertEquals(true, shimmer.getConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.TTL_INPUT));
	}

	@Test
	public void erasedMemoryIsKeptAsReadAndReadsAsLegacy() {
		ProbedShimmer shimmer = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0xFF, 0, SINGLE_TOUCH_START);
		assertFalse(shimmer.isButtonLogModeMeaningful());
		assertEquals(ButtonLogMode.BUTTON_MODE_LEGACY, shimmer.getButtonMode());
		assertEquals(0xFF, written(shimmer, IDX_BUTTON_LOG_MODE));
		assertEquals("legacy leaves userButtonEnable", 0, written(shimmer, IDX_TRIAL_CONFIG0) & USER_BUTTON_ENABLE);
		assertEquals("the firmware clears single touch all the same", 0, written(shimmer, IDX_TRIAL_CONFIG1) & SINGLE_TOUCH_START);
	}

	@Test
	public void aByteTheFirmwareWouldCorrectIsTrustedOnlyAfterTheProbe() {
		// Reserved button mode 7 with the TTL input on: no firmware writes it
		ProbedShimmer unprobed = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x27, USER_BUTTON_ENABLE, 0);
		assertFalse(unprobed.isTtlInputEnabled());
		assertEquals("kept as read", 0x27, written(unprobed, IDX_BUTTON_LOG_MODE));

		ProbedShimmer probed = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x27, USER_BUTTON_ENABLE, 0);
		probed.probeAnswered(true);
		assertTrue(probed.isTtlInputEnabled());
		assertEquals("as the firmware corrects it", 0x20, written(probed, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void sr48_6_0HasNeitherInput() {
		ProbedShimmer shimmer = shimmer(S3R_EVENT_CHANNEL_CAPABLE);
		shimmer.setExpansionBoardDetails(new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 6, 0));
		shimmer.configBytesParse(configFromShimmer(S3R_EVENT_CHANNEL_CAPABLE, 0x24, USER_BUTTON_ENABLE, 0), COMMUNICATION_TYPE.BLUETOOTH);
		assertEquals(0x03, written(shimmer, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void firmwareThatCannotHaveTheByteIsWrittenAsBefore() {
		ShimmerVerObject[] older = {
				new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 51),
				new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 16),
		};
		for(ShimmerVerObject svo:older){
			ProbedShimmer shimmer = readFrom(svo, 0x11, USER_BUTTON_ENABLE, 0);
			assertFalse(svo.getFirmwareVersionParsed(), shimmer.isButtonLogModeKnown());
			assertEquals(svo.getFirmwareVersionParsed(), 0, written(shimmer, IDX_BUTTON_LOG_MODE));
		}
		ProbedShimmer s3 = readFrom(new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 9), 0x11, USER_BUTTON_ENABLE, 0);
		assertEquals("a Shimmer3 release that can", 0x11, written(s3, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void firmwareThatCannotHaveTheByteKeepsBytes217And218() {
		ShimmerVerObject[] older = {
				new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 51),
				new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 16),
				new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.SDLOG, 0, 19, 0),
		};
		for(ShimmerVerObject svo:older){
			ProbedShimmer shimmer = readFrom(svo, 0x00, 0, SINGLE_TOUCH_START);
			shimmer.setButtonMode(ButtonLogMode.BUTTON_MODE_DISABLED);
			assertEquals(svo.getFirmwareVersionParsed(), 0, written(shimmer, IDX_TRIAL_CONFIG0) & USER_BUTTON_ENABLE);
			assertEquals(svo.getFirmwareVersionParsed(), SINGLE_TOUCH_START, written(shimmer, IDX_TRIAL_CONFIG1) & SINGLE_TOUCH_START);
		}
	}

	// ---- The Bluetooth write's SET_INFOMEM chunks

	/** [address, length] of each SET_INFOMEM queued: [0x8C, length, address LSB, address MSB, data...] */
	private static List<int[]> infoMemChunks(ShimmerBluetooth shimmer) {
		List<int[]> chunks = new ArrayList<int[]>();
		for(byte[] instruction:shimmer.getListofInstructions()){
			if(instruction!=null && instruction[0]==ShimmerObject.SET_INFOMEM_COMMAND){
				chunks.add(new int[] {(instruction[2]&0xFF) | ((instruction[3]&0xFF)<<8), instruction[1]&0xFF});
			}
		}
		return chunks;
	}

	private static byte[] infoMemChunkAt(ShimmerBluetooth shimmer, int address) {
		for(byte[] instruction:shimmer.getListofInstructions()){
			if(instruction!=null && instruction[0]==ShimmerObject.SET_INFOMEM_COMMAND
					&& ((instruction[2]&0xFF) | ((instruction[3]&0xFF)<<8))==address){
				return instruction;
			}
		}
		fail("no SET_INFOMEM at " + address);
		return null;
	}

	private static void assertChunks(String message, int[][] expected, ShimmerBluetooth shimmer) {
		List<int[]> chunks = infoMemChunks(shimmer);
		List<String> actual = new ArrayList<String>();
		for(int[] chunk:chunks){
			actual.add(Arrays.toString(chunk));
		}
		List<String> wanted = new ArrayList<String>();
		for(int[] chunk:expected){
			wanted.add(Arrays.toString(chunk));
		}
		assertEquals(message, wanted, actual);
	}

	/** The 128-byte chunks of the Shimmer's image, as the write has always sent them */
	private static int[][] allChunks(ShimmerBluetooth shimmer) {
		int length = shimmer.generateConfigBytesForWritingToShimmer().length;
		int[][] chunks = new int[length/128][];
		for(int i=0;i<chunks.length;i++){
			chunks[i] = new int[] {i*128, 128};
		}
		return chunks;
	}

	/** The same, with the chunk at 128 sent as the parts given */
	private static int[][] chunk128As(ShimmerBluetooth shimmer, int[]... parts) {
		List<int[]> chunks = new ArrayList<int[]>();
		for(int[] chunk:allChunks(shimmer)){
			if(chunk[0]==128){
				chunks.addAll(Arrays.asList(parts));
			}
			else {
				chunks.add(chunk);
			}
		}
		return chunks.toArray(new int[0][]);
	}

	/** Either side of byte 232 */
	private static int[][] chunksWithout232(ShimmerBluetooth shimmer) {
		return chunk128As(shimmer, new int[] {128, 104}, new int[] {233, 23});
	}

	/** Either side of bytes 232 and 233: a Shimmer3R's, before a read (API_00047) */
	private static int[][] chunksWithout232And233(ShimmerBluetooth shimmer) {
		return chunk128As(shimmer, new int[] {128, 104}, new int[] {234, 22});
	}

	/** Either side of byte 233 */
	private static int[][] chunksWithout233(ShimmerBluetooth shimmer) {
		return chunk128As(shimmer, new int[] {128, 105}, new int[] {234, 22});
	}

	@Test
	public void beforeAReadTheBluetoothWriteLeavesTheShimmersByte() {
		ProbedShimmer shimmer = shimmer(S3R_EVENT_CHANNEL_CAPABLE);
		assertFalse(shimmer.isButtonLogModeKnown());
		shimmer.writeConfigBytes(false);
		assertChunks("a configuration written on connect, before the read; a Shimmer3R's byte 233 is left out too",
				chunksWithout232And233(shimmer), shimmer);

		// The bytes either side of 232 and 233 are the image's
		byte[] image = shimmer.generateConfigBytesForWritingToShimmer();
		for(byte[] instruction:shimmer.getListofInstructions()){
			if(instruction!=null && instruction[0]==ShimmerObject.SET_INFOMEM_COMMAND){
				int address = (instruction[2]&0xFF) | ((instruction[3]&0xFF)<<8);
				int length = instruction[1]&0xFF;
				assertArrayEquals("chunk at " + address, Arrays.copyOfRange(image, address, address+length), Arrays.copyOfRange(instruction, 4, 4+length));
			}
		}

		ProbedShimmer s3 = shimmer(new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 17));
		s3.writeConfigBytes(false);
		assertChunks("the first Shimmer3 release that can", chunksWithout232(s3), s3);
	}

	@Test
	public void onceReadOrSetTheBluetoothWriteHasTheByte() {
		ProbedShimmer read = readFrom(S3R_EVENT_CHANNEL_CAPABLE, 0x11, USER_BUTTON_ENABLE, 0);
		read.writeConfigBytes(false);
		assertChunks("read", allChunks(read), read);

		ProbedShimmer set = shimmer(S3R_EVENT_CHANNEL_CAPABLE);
		set.setButtonMode(ButtonLogMode.BUTTON_MODE_DISABLED);
		set.writeConfigBytes(false);
		assertChunks("set, with byte 233 still unread", chunksWithout233(set), set);
		byte[] chunk = infoMemChunkAt(set, 128);
		assertEquals("set, before anything was read", 0x01, chunk[4 + IDX_BUTTON_LOG_MODE - 128] & 0xFF);
	}

	@Test
	public void olderFirmwareIsWrittenInWholeChunksAsBefore() {
		ShimmerVerObject[] older = {
				new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 51),
				new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 16),
		};
		for(ShimmerVerObject svo:older){
			ProbedShimmer shimmer = shimmer(svo);
			shimmer.writeConfigBytes(false);
			assertChunks(svo.getFirmwareVersionParsed(), allChunks(shimmer), shimmer);
		}
	}

}
