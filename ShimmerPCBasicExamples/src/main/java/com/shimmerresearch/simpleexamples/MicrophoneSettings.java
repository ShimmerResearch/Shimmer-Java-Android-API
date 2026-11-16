package com.shimmerresearch.simpleexamples;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.ConfigByteLayout;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.soundlevel.SensorSoundLevel;

/**
 * A Shimmer3R's microphone settings, configuration byte 233, as the event
 * markers and audio window writes them: the audio recording mode (DEV-99,
 * DEV-1459) and the sound level channel (DEV-1413). No Swing, so that the
 * rules can be tested without a window.
 * <ul>
 * <li>A unit records audio on LogAndStream_Shimmer3R v1.01.026 or later, or
 * on a v1.01.025 development build that answered GET_AUDIO_STATUS at
 * connect ({@link ShimmerDevice#isAudioSupported()}).</li>
 * <li>Only v1.01.026 or later has the sound level channel.</li>
 * <li>Raw audio records speech, so a raw mode is written only where the user
 * agreed to it, or where the unit already keeps raw audio. The driver's own
 * rule ({@link ShimmerObject#getAudioModeToWrite()}) otherwise writes the
 * mode without the raw audio.</li>
 * </ul>
 */
public final class MicrophoneSettings {

	private MicrophoneSettings() {
	}

	/** The driver's names for the four audio modes, Off first, indexed by mode */
	public static List<String> modeNames() {
		return Collections.unmodifiableList(Arrays.asList(Configuration.Shimmer3.ListofAudioMode));
	}

	/** Whether the unit records audio from its microphone */
	public static boolean recordsAudio(ShimmerDevice device) {
		return device != null && device.isAudioSupported();
	}

	/** Whether the unit's firmware has the sound level channel: v1.01.026 or later */
	public static boolean hasSoundLevel(ShimmerDevice device) {
		return device != null
				&& device.isVerCompatibleWithAnyOf(Configuration.Shimmer3.CompatibilityInfoForMaps.listOfCompatibleVersionInfoSoundLevel);
	}

	/** Whether the sound level is turned on in the driver's copy of the configuration */
	public static boolean isSoundLevelOn(ShimmerDevice device) {
		return hasSoundLevel(device) && device.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL);
	}

	/**
	 * The audio mode in byte 233 as last read from the unit, whatever has been
	 * set on the driver since; Off before the first read.
	 */
	public static int modeAsRead(ShimmerDevice device) {
		AbstractSensor sensor = device == null ? null : device.getSensorClass(AbstractSensor.SENSORS.SOUND_LEVEL);
		if (!(sensor instanceof SensorSoundLevel)) {
			return Configuration.Shimmer3.AUDIO_MODE_OFF;
		}
		return ((SensorSoundLevel) sensor).getAudioModeAndGain() & 0x03;
	}

	/** Whether writing {@code mode} asks first: it keeps raw audio, and the unit's own mode does not */
	public static boolean asksFirst(int mode, int modeAsRead) {
		return ShimmerObject.isRawAudioMode(mode) && !ShimmerObject.isRawAudioMode(modeAsRead);
	}

	/** Whether the driver may write raw audio for this write: the mode keeps it, and the user agreed or the unit keeps it already */
	static boolean rawAudioAllowed(int mode, int modeAsRead, boolean agreed) {
		return ShimmerObject.isRawAudioMode(mode) && (agreed || ShimmerObject.isRawAudioMode(modeAsRead));
	}

	/**
	 * Sets the audio mode and the sound level on the driver's copy of the
	 * configuration, then writes the whole configuration and reads it back,
	 * and asks for the channel list again (an inquiry), since the sound level
	 * adds or removes a channel. The firmware corrects what it does not keep,
	 * so the read-back is what the unit holds. The sound level is left as it is
	 * on firmware without it.
	 *
	 * @param agreed whether the user agreed to raw audio, where {@link #asksFirst} asked
	 */
	public static void write(final ShimmerBluetooth shimmer, final int mode, final boolean soundLevel, boolean agreed) {
		withRawAudioAllowed(rawAudioAllowed(mode, modeAsRead(shimmer), agreed), new Runnable() {
			public void run() {
				set(shimmer, mode, soundLevel);
				shimmer.writeConfigBytes(true);
			}
		});
		if (hasSoundLevel(shimmer)) {
			shimmer.inquiry();
		}
	}

	/** Byte 233 as {@link #write} would send it, without sending it */
	public static int byteToWrite(final ShimmerBluetooth shimmer, final int mode, final boolean soundLevel, boolean agreed) {
		final byte[][] config = new byte[1][];
		withRawAudioAllowed(rawAudioAllowed(mode, modeAsRead(shimmer), agreed), new Runnable() {
			public void run() {
				set(shimmer, mode, soundLevel);
				config[0] = shimmer.generateConfigBytesForWritingToShimmer();
			}
		});
		ConfigByteLayout layout = shimmer.getConfigByteLayout();
		if (!(layout instanceof ConfigByteLayoutShimmer3) || ((ConfigByteLayoutShimmer3) layout).idxAudioConfig <= 0) {
			return -1;
		}
		return config[0][((ConfigByteLayoutShimmer3) layout).idxAudioConfig] & 0xFF;
	}

	private static void set(ShimmerObject shimmer, int mode, boolean soundLevel) {
		shimmer.setAudioMode(mode);
		if (hasSoundLevel(shimmer)) {
			shimmer.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, soundLevel);
		}
	}

	/** The driver's raw audio switch is for the whole process, so it is set for this one write and put back */
	private static void withRawAudioAllowed(boolean allowed, Runnable write) {
		boolean was = ShimmerObject.isRawAudioAllowed();
		ShimmerObject.setRawAudioAllowed(allowed);
		try {
			write.run();
		} finally {
			ShimmerObject.setRawAudioAllowed(was);
		}
	}

	/** What the speech question says */
	public static String speechQuestion(String modeName) {
		return "<html>" + modeName + " keeps the microphone's raw audio on the SD card, and raw audio records speech.<br>"
				+ "Everyone who may be recorded should know, and agree, before this unit logs.<br><br>"
				+ "Write " + modeName + "?</html>";
	}

	/** The status's two audio flags, as the window shows them */
	public static String audioFlags(boolean recording, boolean fault) {
		return "recording audio " + (recording ? "yes" : "no") + ", audio fault " + (fault ? "yes" : "no");
	}

}
