package com.shimmerresearch.pcDriver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;

/**
 * DEV-1459: status byte 1 bits 1-2, audio recording and audio fault
 * (log-and-stream-common SHIMMER3R_AUDIO.md §5). No hardware.
 */
public class API_00048_Shimmer3rAudioStatusTest {

	/** Reaches the status parser, which the driver calls on each status frame */
	private static class StatusShimmer extends ShimmerPC {
		StatusShimmer(int major, int minor, int internal) {
			super("COM99");
			setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, major, minor, internal));
		}

		StatusShimmer parse(int statusByte1) {
			parseStatusByte(new byte[] {0, (byte) statusByte1});
			return this;
		}

		StatusShimmer answer(int state, int fault) {
			byte[] audioStatus = new byte[32];
			audioStatus[0] = 1;
			audioStatus[2] = (byte) state;
			audioStatus[3] = (byte) fault;
			parseAudioStatus(audioStatus);
			return this;
		}
	}

	@Test
	public void recordingAndFaultFromByte1() {
		StatusShimmer shimmer = new StatusShimmer(1, 1, 26);
		assertTrue(shimmer.parse(0x02).isAudioRecording());
		assertFalse(shimmer.isAudioFault());

		shimmer.parse(0x04);
		assertFalse(shimmer.isAudioRecording());
		assertTrue(shimmer.isAudioFault());

		shimmer.parse(0x01);
		assertTrue(shimmer.isUsbPluggedIn());
		assertFalse(shimmer.isAudioRecording());
		assertFalse(shimmer.isAudioFault());
	}

	@Test
	public void ignoredBeforeV1_01_026() {
		StatusShimmer shimmer = new StatusShimmer(1, 1, 25).parse(0x06);
		assertFalse(shimmer.isAudioRecording());
		assertFalse(shimmer.isAudioFault());
	}

	/** v1.01.026 records audio by its version; v1.01.025 only if a development build answers GET_AUDIO_STATUS */
	@Test
	public void audioSupportByVersionOrAnswer() {
		assertTrue(new StatusShimmer(1, 1, 26).isAudioSupported());
		assertFalse(new StatusShimmer(1, 1, 26).isAudioStatusToAsk());
		assertFalse(new StatusShimmer(1, 1, 24).isAudioSupported());
		assertFalse(new StatusShimmer(1, 1, 24).isAudioStatusToAsk());

		StatusShimmer devBuild = new StatusShimmer(1, 1, 25);
		assertTrue(devBuild.isAudioStatusToAsk());
		assertFalse("unknown until it answers", devBuild.isAudioSupported());
		assertTrue(devBuild.answer(2, 0).isAudioSupported());
		assertTrue(devBuild.isAudioRecording());
		assertFalse(devBuild.isAudioFault());

		StatusShimmer release = new StatusShimmer(1, 1, 25);
		release.setIsAudioStatusAnswered(false);
		assertFalse("no answer: no audio", release.isAudioSupported());
	}

	@Test
	public void audioStatusFault() {
		StatusShimmer shimmer = new StatusShimmer(1, 1, 25).answer(4, 3);
		assertFalse(shimmer.isAudioRecording());
		assertTrue(shimmer.isAudioFault());
	}
}
