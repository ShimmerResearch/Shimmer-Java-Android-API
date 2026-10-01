package com.shimmerresearch.sensors;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerObject.SDLogHeader;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilParseData;

/**
 * A NeuroLynQ node's PPG (DEV-1061): the counts of an SR68's MAX86176, read through its
 * MAX32674C sensor hub and logged beside GSR and heart rate in a GQ file
 * (verisense-firmware docs/VERISENSE_NEUROLYNQ_STORAGE.md sections 3.2 and 4.2).
 * <p>
 * A record holds one to three 24-bit little-endian counts, one for each channel header
 * byte 15 enables - bit 0 green, bit 1 IR, bit 2 red - in that order, between GSR and
 * heart rate; header bit {@link SDLogHeader#NEUROLYNQ_PPG} says the block is there. The
 * counts are raw: nothing in the file says what LED current or ADC range they were taken
 * at, so there is nothing to convert them with yet. Heart rate stays on
 * {@link SensorECGToHRFw}, where a GQ's is.
 * <p>
 * Files only: a node streams GSR and heart rate over the air, never PPG.
 */
public class SensorPPGNeuroLynQ extends AbstractSensor implements Serializable {

	private static final long serialVersionUID = -2715183478126653214L;

	/** Header byte 15's bits */
	public static final int MASK_GREEN = 0x01;
	public static final int MASK_IR = 0x02;
	public static final int MASK_RED = 0x04;
	public static final int MASK_ALL = MASK_GREEN | MASK_IR | MASK_RED;

	//--------- Sensor specific variables start --------------
	public static class GuiLabelSensors {
		public static final String PPG = "PPG";
	}

	public static class ObjectClusterSensorName {
		public static final String PPG_GREEN = "PPG_Green";
		public static final String PPG_IR = "PPG_IR";
		public static final String PPG_RED = "PPG_Red";

		/** In the mask's bit order, which is the order a record holds them in */
		public static final List<String> IN_RECORD_ORDER = Collections.unmodifiableList(
				Arrays.asList(PPG_GREEN, PPG_IR, PPG_RED));
	}

	public static class DatabaseChannelHandles {
		public static final String PPG_GREEN = "PPG_Green";
		public static final String PPG_IR = "PPG_IR";
		public static final String PPG_RED = "PPG_Red";
	}

	public static class DatabaseConfigHandle {
		public static final String PPG_MASK = "NeuroLynQ_PPG_Mask";
	}
	//--------- Sensor specific variables end --------------

	//--------- Sensor info start --------------
	/** Logged, never streamed: no streaming bitmap */
	public static final SensorDetailsRef sensorPpgNeuroLynQ = new SensorDetailsRef(
			0,
			SDLogHeader.NEUROLYNQ_PPG,
			GuiLabelSensors.PPG,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoNeuroLynQNode,
			null,
			ObjectClusterSensorName.IN_RECORD_ORDER);

	public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Shimmer3.SENSOR_ID.NEUROLYNQ_PPG, sensorPpgNeuroLynQ);
		mSensorMapRef = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	public static final ChannelDetails channelPpgGreen = createChannel(
			ObjectClusterSensorName.PPG_GREEN, DatabaseChannelHandles.PPG_GREEN);
	public static final ChannelDetails channelPpgIr = createChannel(
			ObjectClusterSensorName.PPG_IR, DatabaseChannelHandles.PPG_IR);
	public static final ChannelDetails channelPpgRed = createChannel(
			ObjectClusterSensorName.PPG_RED, DatabaseChannelHandles.PPG_RED);

	private static ChannelDetails createChannel(String objectClusterName, String databaseHandle) {
		ChannelDetails channel = new ChannelDetails(
				objectClusterName,
				objectClusterName,
				databaseHandle,
				CHANNEL_DATA_TYPE.UINT24, 3, CHANNEL_DATA_ENDIAN.LSB,
				CHANNEL_UNITS.NO_UNITS,
				Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL));
		channel.mDefaultUncalUnit = CHANNEL_UNITS.NO_UNITS;
		return channel;
	}

	public static final Map<String, ChannelDetails> mChannelMapRef;
	static {
		Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.PPG_GREEN, channelPpgGreen);
		aMap.put(ObjectClusterSensorName.PPG_IR, channelPpgIr);
		aMap.put(ObjectClusterSensorName.PPG_RED, channelPpgRed);
		mChannelMapRef = Collections.unmodifiableMap(aMap);
	}
	//--------- Channel info end --------------

	/**
	 * The channels a header's byte 15 enables, in record order. Only its low three bits
	 * count, as the firmware and the web SDK's validator count them.
	 */
	public static List<String> channelsForMask(int mask) {
		List<String> channels = new java.util.ArrayList<String>();
		for (int bit = 0; bit < ObjectClusterSensorName.IN_RECORD_ORDER.size(); bit++) {
			if ((mask & (1 << bit)) != 0) {
				channels.add(ObjectClusterSensorName.IN_RECORD_ORDER.get(bit));
			}
		}
		return channels;
	}

	/**
	 * The mask a node reads out of its InfoMem, as its firmware reads it
	 * (verisense-firmware gq_file_header.c, GqFile_ppgMask()): 0xFF, a byte never
	 * written, and any value with none of the three bits set are green only.
	 */
	public static int maskFromInfoMem(int stored) {
		stored &= 0xFF;
		if (stored == 0xFF || (stored & MASK_ALL) == 0) {
			return MASK_GREEN;
		}
		return stored & MASK_ALL;
	}

	/** Green only: what a node logs until configured otherwise, and all a heart rate needs.
	 * IR and red are for SpO2, and each channel adds a third to a file. */
	private int mChannelMask = MASK_GREEN;

	public SensorPPGNeuroLynQ(ShimmerVerObject svo) {
		super(SENSORS.PPG_NEUROLYNQ, svo);
		initialise();
	}

	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(mSensorMapRef, mChannelMapRef);
		applyChannelMask();
	}

	/**
	 * The channels a file logs, from its header's byte 15: only those are parsed and
	 * stored, so a green-only node's database has no IR or red columns.
	 */
	public void setChannelMask(int mask) {
		mChannelMask = mask & MASK_ALL;
		applyChannelMask();
	}

	public int getChannelMask() {
		return mChannelMask;
	}

	private void applyChannelMask() {
		SensorDetails sensorDetails = mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.NEUROLYNQ_PPG);
		if (sensorDetails == null) {
			return;
		}
		sensorDetails.mListOfChannels.clear();
		for (String channel : channelsForMask(mChannelMask)) {
			sensorDetails.mListOfChannels.add(mChannelMapRef.get(channel));
		}
	}

	@Override
	public void generateSensorGroupMapping() {
		//NOT USED IN THIS CLASS
	}

	@Override
	public void generateConfigOptionsMap() {
		//NOT USED IN THIS CLASS
	}

	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] sensorByteArray, COMMUNICATION_TYPE commType,
			ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		int index = 0;
		for (ChannelDetails channelDetails : sensorDetails.mListOfChannels) {
			byte[] channelByteArray = new byte[channelDetails.mDefaultNumBytes];
			System.arraycopy(sensorByteArray, index, channelByteArray, 0, channelDetails.mDefaultNumBytes);
			double counts = (double) UtilParseData.parseData(channelByteArray,
					channelDetails.mDefaultChannelDataType, channelDetails.mDefaultChannelDataEndian);
			objectCluster.addUncalData(channelDetails, counts);
			objectCluster.addCalData(channelDetails, counts);
			objectCluster.incrementIndexKeeper();
			index = index + channelDetails.mDefaultNumBytes;
		}
		return objectCluster;
	}

	/**
	 * Not here: a docked node is configured as a ShimmerGQ_802154, which has a GQ's
	 * sensors - the node streams what a GQ streams - and so owns the mask's InfoMem byte
	 * itself. This class is the file's side.
	 */
	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		//NOT USED IN THIS CLASS
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		//NOT USED IN THIS CLASS
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		return null;
	}

	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		return null;
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		//The node's rate, from its header
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		return mSensorMap.containsKey(sensorId);
	}

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		return false;
	}

	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		return null;
	}

	@Override
	public ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		return null;
	}

	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		LinkedHashMap<String, Object> mapOfConfig = new LinkedHashMap<String, Object>();
		mapOfConfig.put(DatabaseConfigHandle.PPG_MASK, (double) mChannelMask);
		return mapOfConfig;
	}

	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		if (mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.PPG_MASK)) {
			setChannelMask(maskFromInfoMem(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.PPG_MASK)).intValue()));
		}
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		return false;
	}

	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		//NOT USED IN THIS CLASS
	}
}
