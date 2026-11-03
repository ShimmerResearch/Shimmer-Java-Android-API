package com.shimmerresearch.driver;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * DEV-99: the Shimmer3R audio mode in config byte 233 (log-and-stream-common SHIMMER3R_AUDIO.md §1).
 * Bits 0-1 are the mode, bits 2-3 a gain preset (only 0 so far) and bit 7 must be clear; the firmware
 * runs anything else as off, so the driver reads it as off too. Raw audio is written only by an app
 * allowed to record it, otherwise the fingerprint is kept and the raw audio dropped.
 */
public class API_00029_Shimmer3rAudioModeConfigByteTest {

	private static final int IDX_AUDIO_MODE = 233;

	private static ShimmerPC device(int fwInternal) {
		ShimmerPC device = new ShimmerPC("COM99");
		device.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, fwInternal));
		return device;
	}

	private static int parse(int audioModeByte) {
		ShimmerPC device = device(25);
		byte[] configBytes = device.configBytesGenerate(true, COMMUNICATION_TYPE.SD);
		configBytes[IDX_AUDIO_MODE] = (byte) audioModeByte;
		device.configBytesParse(configBytes, COMMUNICATION_TYPE.SD);
		return device.getAudioMode();
	}

	private static int written(int audioMode) {
		ShimmerPC device = device(25);
		device.setConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.AUDIO_MODE, audioMode);
		return device.configBytesGenerate(true, COMMUNICATION_TYPE.SD)[IDX_AUDIO_MODE] & 0xFF;
	}

	@After
	public void resetRawAudioAllowed() {
		ShimmerObject.setRawAudioAllowed(false);
	}

	@Test
	public void readsTheFourModes() {
		for (int mode = Configuration.Shimmer3.AUDIO_MODE_OFF; mode <= Configuration.Shimmer3.AUDIO_MODE_RAW; mode++) {
			assertEquals(mode, parse(mode));
		}
	}

	@Test
	public void unsupportedValuesReadAsOff() {
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_OFF, parse(0xFF)); // erased
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_OFF, parse(0x81));
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_OFF, parse(0x05)); // a gain preset not yet defined
	}

	@Test
	public void rawAudioIsDroppedUnlessAllowed() {
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT, written(Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT));
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT, written(Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT_AND_RAW));
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_OFF, written(Configuration.Shimmer3.AUDIO_MODE_RAW));
		ShimmerObject.setRawAudioAllowed(true);
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT_AND_RAW, written(Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT_AND_RAW));
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_RAW, written(Configuration.Shimmer3.AUDIO_MODE_RAW));
	}

	@Test
	public void olderFirmwareHasNoAudioMode() {
		ShimmerPC device = device(24);
		device.setConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.AUDIO_MODE, Configuration.Shimmer3.AUDIO_MODE_FINGERPRINT);
		assertEquals(Configuration.Shimmer3.AUDIO_MODE_OFF, device.getAudioMode());
	}

}
