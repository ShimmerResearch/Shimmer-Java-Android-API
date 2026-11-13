package com.shimmerresearch.sensors.soundlevel;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.shimmerresearch.driver.ConfigByteLayout;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.ActionSetting;

/**
 * The Shimmer3R sound level channel (DEV-1413): the A-weighted sound level
 * from the microphone, LAeq over the most recent completed 128 ms block, in
 * dB(A) SPL. LogAndStream_Shimmer3R v1.01.026 is the first release with it.
 * <p>
 * <b>Nominal.</b> The firmware adds the microphone's datasheet sensitivity and
 * nothing more; no unit has been checked against a calibrated sound level
 * meter.
 * <p>
 * <b>On the wire.</b> Channel ID 0x38, two bytes, a signed 16-bit
 * little-endian count of 0.01 dB(A). 0x8000 (-32768) means there is no level:
 * while capture settles (about the first 256 ms), or when it is stopped or
 * has failed. {@link #calibrateSoundLevel(double)} makes that NaN, never
 * -327.68. A value describes the block that ended 0-128 ms before the
 * packet's timestamp, plus the main loop's latency (up to about 0.2 s more
 * while an SD card is slow); nothing here corrects for that. The firmware
 * places the channel just before the ADS7028 block (GSR, the ADCs and the
 * battery), so the battery stays last, but the decoder takes its position
 * from the channel list, as for any channel. A Shimmer3 never sends it.
 * <p>
 * <b>Enabling it.</b> Not a sensor bitmap bit: configuration byte 233 bit 4.
 * Byte 233 also holds the DEV-99 audio mode (bits 0-1) and gain preset (bits
 * 2-3), which this class keeps as read so that a full configuration write,
 * which builds its image from zeros, gives the Shimmer back its own, on any
 * Shimmer3R firmware.
 * <p>
 * <b>Offered from v1.01.026.</b> The sensor and its tile are only offered on
 * LogAndStream_Shimmer3R v1.01.026 and later
 * ({@link CompatibilityInfoForMaps#listOfCompatibleVersionInfoSoundLevel}).
 * Releases before it (DEV-99) store byte 233 untouched, so there bit 4 would
 * read back set while no channel is sent. On them, a bit 4 that is already
 * set reads as off, and the next configuration write clears it, so a later
 * firmware update does not turn the channel on unasked. The channel list,
 * not the bit, is still what the decoder goes by.
 * <p>
 * This class is only created for a Shimmer3R. A Shimmer3's byte 233 stays 0.
 */
public class SensorSoundLevel extends AbstractSensor {

	private static final long serialVersionUID = 6915432740389468093L;

	/** The channel ID in the inquiry response and in the SD header's channel list */
	public static final byte CHANNEL_ID = 0x38;
	/** The raw count that means there is no level: 0x8000 as a signed 16-bit value */
	public static final int NO_LEVEL = -32768;
	/** Counts per dB(A) */
	public static final double COUNTS_PER_DB = 100.0;

	/** Byte 233's audio mode and gain preset as read (bits 0-3, DEV-99), written back as they are */
	private int mAudioModeAndGain = 0;

	public class GuiLabelSensors{
		public static final String SOUND_LEVEL = "Sound Level";
	}

	public class LABEL_SENSOR_TILE{
		public static final String SOUND_LEVEL = GuiLabelSensors.SOUND_LEVEL;
	}

	public static class ObjectClusterSensorName{
		public static final String SOUND_LEVEL = "Sound_Level";
	}

	public static class DatabaseChannelHandles{
		public static final String SOUND_LEVEL = "Sound_Level";
	}

	//--------- Sensor info start --------------
	/** No sensor bitmap bit: enabled through configuration byte 233 bit 4 */
	public static final SensorDetailsRef sensorSoundLevel = new SensorDetailsRef(
			0,
			0,
			GuiLabelSensors.SOUND_LEVEL,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoSoundLevel,
			null,
			Arrays.asList(ObjectClusterSensorName.SOUND_LEVEL));

	public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, SensorSoundLevel.sensorSoundLevel);
		mSensorMapRef = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	public static final ChannelDetails channelSoundLevel = new ChannelDetails(
			ObjectClusterSensorName.SOUND_LEVEL,
			ObjectClusterSensorName.SOUND_LEVEL,
			DatabaseChannelHandles.SOUND_LEVEL,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB,
			CHANNEL_UNITS.DECIBELS_A,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL),
			CHANNEL_ID);

	public static final Map<String, ChannelDetails> mChannelMapRef;
	static {
		Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.SOUND_LEVEL, SensorSoundLevel.channelSoundLevel);
		mChannelMapRef = Collections.unmodifiableMap(aMap);
	}
	//--------- Channel info end --------------

	public static final SensorGroupingDetails sensorGroupSoundLevel = new SensorGroupingDetails(
			LABEL_SENSOR_TILE.SOUND_LEVEL,
			Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL),
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoSoundLevel);

	//--------- Constructors for this class start --------------
	public SensorSoundLevel(ShimmerDevice shimmerDevice) {
		super(SENSORS.SOUND_LEVEL, shimmerDevice);
		initialise();
	}
	//--------- Constructors for this class end --------------

	/**
	 * A raw sound level count in nominal dB(A) SPL, or NaN for the no-level
	 * value (0x8000) and for anything that is not a signed 16-bit count.
	 */
	public static double calibrateSoundLevel(double rawCount) {
		if(Double.isNaN(rawCount) || rawCount<=NO_LEVEL || rawCount>Short.MAX_VALUE) {
			return Double.NaN;
		}
		return rawCount/COUNTS_PER_DB;
	}

	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(mSensorMapRef, mChannelMapRef);
	}

	@Override
	public void generateConfigOptionsMap() {
		// No configuration options: the channel is on or off
	}

	@Override
	public void generateSensorGroupMapping() {
		mSensorGroupingMap = new LinkedHashMap<Integer, SensorGroupingDetails>();
		mSensorGroupingMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.SOUND_LEVEL.ordinal(), sensorGroupSoundLevel);
		super.updateSensorGroupingMap();
	}

	/** Used where a device parses through its sensor classes; the Shimmer3R stream and SD paths decode in ShimmerObject.buildMsg() */
	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType,
			ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);

		if(mEnableCalibration){
			for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
				if(channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.SOUND_LEVEL)){
					FormatCluster rawFormatCluster = (FormatCluster) ObjectCluster.returnFormatCluster(
							objectCluster.getCollectionOfFormatClusters(channelDetails.mObjectClusterName),
							channelDetails.mChannelFormatDerivedFromShimmerDataPacket.toString());
					if(rawFormatCluster!=null){
						objectCluster.addCalData(channelDetails, calibrateSoundLevel(rawFormatCluster.mData), objectCluster.getIndexKeeper()-1);
					}
				}
			}
		}
		return objectCluster;
	}

	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		// Nothing depends on it
	}

	private static ConfigByteLayoutShimmer3 getAudioConfigLayout(ShimmerDevice shimmerDevice, byte[] configBytes) {
		ConfigByteLayout configByteLayout = shimmerDevice.getConfigByteLayout();
		if(configByteLayout instanceof ConfigByteLayoutShimmer3){
			ConfigByteLayoutShimmer3 configByteLayoutCast = (ConfigByteLayoutShimmer3) configByteLayout;
			if(configByteLayoutCast.idxAudioConfig>0 && configBytes!=null && configBytes.length>configByteLayoutCast.idxAudioConfig){
				return configByteLayoutCast;
			}
		}
		return null;
	}

	/** Whether this firmware records audio (DEV-99): LogAndStream_Shimmer3R v1.01.025 or later */
	private static boolean isAudioModeSupported(ShimmerDevice shimmerDevice) {
		return shimmerDevice instanceof ShimmerObject
				&& shimmerDevice.isVerCompatibleWithAnyOf(Configuration.Shimmer3.configOptionAudioMode.mListOfCompatibleVersionInfo);
	}

	/** Whether this firmware has the channel: LogAndStream_Shimmer3R v1.01.026 or later */
	private static boolean isChannelSupported(ShimmerDevice shimmerDevice) {
		return shimmerDevice.isVerCompatibleWithAnyOf(CompatibilityInfoForMaps.listOfCompatibleVersionInfoSoundLevel);
	}

	/**
	 * Byte 233: bits 0-3 as read (0 if the byte read was not valid) and bit 4
	 * from this sensor's state, on firmware that has the channel; bits 5-7 are
	 * 0, as the firmware wants them.
	 */
	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		ConfigByteLayoutShimmer3 configByteLayoutCast = getAudioConfigLayout(shimmerDevice, configBytes);
		if(configByteLayoutCast!=null){
			int value = mAudioModeAndGain & configByteLayoutCast.maskAudioModeAndGain;
			// a mode changed here goes through the raw audio rule; one set elsewhere goes back as read
			if(isAudioModeSupported(shimmerDevice) && ((ShimmerObject) shimmerDevice).getAudioMode()!=(value & configByteLayoutCast.maskAudioMode)){
				value = (value & ~configByteLayoutCast.maskAudioMode) | (((ShimmerObject) shimmerDevice).getAudioModeToWrite() & configByteLayoutCast.maskAudioMode);
			}
			if(isChannelSupported(shimmerDevice) && isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL)){
				value |= configByteLayoutCast.maskSoundLevel << configByteLayoutCast.bitShiftSoundLevel;
			}
			configBytes[configByteLayoutCast.idxAudioConfig] = (byte) value;
		}
	}

	/**
	 * Keeps byte 233's bits 0-3 for the next write and takes this sensor's
	 * state from bit 4, on firmware that has the channel; before v1.01.026 the
	 * sensor reads as off whatever bit 4 holds. Bit 7 set (erased flash reads
	 * 0xFF) makes the firmware read the whole byte as 0, and so does this.
	 */
	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		ConfigByteLayoutShimmer3 configByteLayoutCast = getAudioConfigLayout(shimmerDevice, configBytes);
		if(configByteLayoutCast!=null){
			int value = configBytes[configByteLayoutCast.idxAudioConfig] & 0xFF;
			if((value & configByteLayoutCast.maskAudioConfigInvalid)!=0){
				value = 0;
			}
			mAudioModeAndGain = value & configByteLayoutCast.maskAudioModeAndGain;
			if(isAudioModeSupported(shimmerDevice)){
				((ShimmerObject) shimmerDevice).setAudioMode(mAudioModeAndGain & configByteLayoutCast.maskAudioMode);
			}

			SensorDetails sensorDetails = mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL);
			if(sensorDetails!=null){
				sensorDetails.setIsEnabled(isChannelSupported(shimmerDevice)
						&& ((value >> configByteLayoutCast.bitShiftSoundLevel) & configByteLayoutCast.maskSoundLevel)!=0);
			}
		}
	}

	/**
	 * The sensor bitmap has no bit for this sensor, so the bitmap-driven
	 * update would always turn it off. Its state comes from byte 233 instead.
	 */
	@Override
	public boolean handleSpecCasesBeforeSensorMapUpdatePerSensor(ShimmerDevice shimmerDevice, Integer sensorId) {
		return sensorId!=null && sensorId==Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL;
	}

	/** Byte 233's audio mode and gain preset (bits 0-3, DEV-99) as last read, which a configuration write gives back */
	public int getAudioModeAndGain() {
		return mAudioModeAndGain;
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		return super.setConfigValueUsingConfigLabelCommon(sensorId, configLabel, valueToSet);
	}

	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		return super.getConfigValueUsingConfigLabelCommon(sensorId, configLabel);
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		// One level per 128 ms block whatever the sampling rate
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		return mSensorMap.containsKey(sensorId);
	}

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		return mConfigOptionsMap.containsKey(stringKey);
	}

	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		return null;
	}

	@Override
	public ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		return new ActionSetting(commType);
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		return false;
	}

	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		return new LinkedHashMap<String, Object>();
	}

	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		// Nothing beyond the enabled state, which the sensor map carries
	}

}
