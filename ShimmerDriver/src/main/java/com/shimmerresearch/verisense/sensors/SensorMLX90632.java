package com.shimmerresearch.verisense.sensors;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX;

/**
 * Decoder for the MLX90632 skin temperature sensor (Verisense data-block sensor
 * id = 9, second-generation hardware). Data block = N samples x 4 bytes:
 * object (skin/target) int16 then ambient int16, both little-endian
 * centi-degrees Celsius (the firmware applies the chip's EEPROM calibration
 * before quantising, so the host conversion is simply /100). The firmware only
 * ever emits FULL blocks of {@link #NUM_SAMPLES_PER_BLOCK} samples (partial
 * blocks are discarded on stop - hal_slowSensorSampler.c), so the on-disk
 * block size is fixed.
 * <p>
 * UNCAL = raw centi-degrees C counts; CAL = degrees Celsius. Config comes from
 * payload header byte 32 (SKIN_TEMP_CONFIG): bit 0 measType (0 = medical,
 * best accuracy ~25-42.5 C; 1 = extended range), bits 3:1 the MLX90632
 * refresh-rate code (0..7 = 0.5/1/2/4/8/16/32/64 Hz). The OUTPUT sample rate
 * the firmware delivers is refresh / sub-measurements (medical = 2,
 * extended = 3) - mirrors the web SDK's SensorMLX90632.ts. As with the
 * ambient light, the parser refines the achieved rate per payload from
 * temp-block tick spacing; the header-derived value seeds the timing.
 */
public class SensorMLX90632 extends AbstractSensor {

	private static final long serialVersionUID = 7160245712894913684L;

	public static final int BYTES_PER_SAMPLE = 4;
	/** Firmware NUM_TEMP_SAMPLES_PER_BLOCK (hal_slowSensorSampler.h). */
	public static final int NUM_SAMPLES_PER_BLOCK = 16;
	/** Fixed on-disk sensor-data size of a skin-temp block (no count prefix). */
	public static final int BLOCK_DATA_BYTES = NUM_SAMPLES_PER_BLOCK * BYTES_PER_SAMPLE;

	/** Refresh-rate code (header byte 32 bits 3:1) -> chip refresh Hz. */
	public static final double[] REFRESH_HZ_TABLE = {0.5, 1, 2, 4, 8, 16, 32, 64};
	/**
	 * Output-rate bounds implied by the refresh table across BOTH modes: the
	 * slowest configuration is REFRESH_HZ_TABLE[0] (0.5 Hz) divided by
	 * {@link #SUB_MEASUREMENTS_EXTENDED} (3) = 0.167 Hz, and the fastest is
	 * REFRESH_HZ_TABLE[7] (64 Hz) divided by {@link #SUB_MEASUREMENTS_MEDICAL}
	 * (2) = 32 Hz. A parser can therefore bound this sensor's output rate from
	 * the payload header before it has measured anything - and unlike the
	 * VD6283's, the rate itself IS recoverable from the header (the refresh code
	 * is stored), so {@link #getRateFreq()} is a real estimate rather than only an
	 * upper bound.
	 */
	public static final double MIN_OUTPUT_RATE_HZ = 0.5/3;
	public static final double MAX_OUTPUT_RATE_HZ = 32.0;

	/** Sub-measurements per output: medical mode = 2, extended mode = 3. */
	public static final int SUB_MEASUREMENTS_MEDICAL = 2;
	public static final int SUB_MEASUREMENTS_EXTENDED = 3;

	private int refreshRateCode = 5; // 16 Hz chip refresh (firmware default)
	private boolean extendedMode = false;

	public class GuiLabelSensors {
		public static final String SKIN_TEMP = "Skin Temperature";
	}

	public static class ObjectClusterSensorName {
		public static String SKIN_TEMP_OBJECT = "SkinTemp_Object";
		public static String SKIN_TEMP_AMBIENT = "SkinTemp_Ambient";
	}

	public static class DatabaseChannelHandles {
		public static final String SKIN_TEMP_OBJECT = "MLX90632_OBJECT";
		public static final String SKIN_TEMP_AMBIENT = "MLX90632_AMBIENT";
	}

	//--------- Sensor info start --------------
	public static final SensorDetailsRef SENSOR_MLX90632 = new SensorDetailsRef(
			Configuration.Verisense.SensorBitmap.MLX90632,
			Configuration.Verisense.SensorBitmap.MLX90632,
			GuiLabelSensors.SKIN_TEMP,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV,
			// listOfSensorIdsConflicting - none (gen-1 convention, e.g. SENSOR_GSR_VERISENSE)
			new java.util.ArrayList<Integer>(),
			Arrays.asList(),
			Arrays.asList(ObjectClusterSensorName.SKIN_TEMP_OBJECT,
					ObjectClusterSensorName.SKIN_TEMP_AMBIENT),
			false);

	public static final Map<Integer, SensorDetailsRef> SENSOR_MAP_REF;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Verisense.SENSOR_ID.MLX90632, SensorMLX90632.SENSOR_MLX90632);
		SENSOR_MAP_REF = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	public static final ChannelDetails CHANNEL_SKIN_TEMP_OBJECT = new ChannelDetails(
			ObjectClusterSensorName.SKIN_TEMP_OBJECT, ObjectClusterSensorName.SKIN_TEMP_OBJECT, DatabaseChannelHandles.SKIN_TEMP_OBJECT,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.DEGREES_CELSIUS,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL));
	public static final ChannelDetails CHANNEL_SKIN_TEMP_AMBIENT = new ChannelDetails(
			ObjectClusterSensorName.SKIN_TEMP_AMBIENT, ObjectClusterSensorName.SKIN_TEMP_AMBIENT, DatabaseChannelHandles.SKIN_TEMP_AMBIENT,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.DEGREES_CELSIUS,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL));

	public static final Map<String, ChannelDetails> CHANNEL_MAP_REF;
	static {
		Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.SKIN_TEMP_OBJECT, CHANNEL_SKIN_TEMP_OBJECT);
		aMap.put(ObjectClusterSensorName.SKIN_TEMP_AMBIENT, CHANNEL_SKIN_TEMP_AMBIENT);
		CHANNEL_MAP_REF = Collections.unmodifiableMap(aMap);
	}
	//--------- Channel info end --------------

	public SensorMLX90632(ShimmerDevice shimmerDevice) {
		super(SENSORS.MLX90632, shimmerDevice);
		initialise();
	}

	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(SENSOR_MAP_REF, CHANNEL_MAP_REF);
	}

	@Override
	public void generateConfigOptionsMap() {
		mConfigOptionsMap.clear();
	}

	@Override
	public void generateSensorGroupMapping() {
		// no GUI grouping needed yet (SD-parse only)
	}

	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType, ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);

		// Stored values are centi-degrees Celsius (calibration already applied on-chip).
		double objectCentiC = objectCluster.getFormatClusterValue(CHANNEL_SKIN_TEMP_OBJECT, CHANNEL_TYPE.UNCAL);
		double ambientCentiC = objectCluster.getFormatClusterValue(CHANNEL_SKIN_TEMP_AMBIENT, CHANNEL_TYPE.UNCAL);
		int indexBase = objectCluster.getIndexKeeper() - 2;
		objectCluster.addCalData(CHANNEL_SKIN_TEMP_OBJECT, objectCentiC / 100.0, indexBase);
		objectCluster.addCalData(CHANNEL_SKIN_TEMP_AMBIENT, ambientCentiC / 100.0, indexBase + 1);

		return objectCluster;
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		if(commType == COMMUNICATION_TYPE.SD && isSensorEnabled(Configuration.Verisense.SENSOR_ID.MLX90632)) {
			int skinTempConfig = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.SKIN_TEMP_CONFIG] & 0xFF;
			extendedMode = (skinTempConfig & 0x01) != 0;
			refreshRateCode = (skinTempConfig >> 1) & 0x07;
		}
	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		// SD-parse only for now.
	}

	public double getRefreshHz() {
		return REFRESH_HZ_TABLE[refreshRateCode & 0x07];
	}

	public boolean isExtendedMode() {
		return extendedMode;
	}

	public String getMeasTypeString() {
		return extendedMode ? "Extended" : "Medical";
	}

	/**
	 * Header-derived output sample rate (Hz): the chip refresh rate divided by
	 * the sub-measurements per output (medical = 2, extended = 3). Seeds
	 * data-block timing; the parser refines the achieved rate per payload from
	 * temp-block tick spacing.
	 */
	public double getRateFreq() {
		return getRefreshHz() / (extendedMode ? SUB_MEASUREMENTS_EXTENDED : SUB_MEASUREMENTS_MEDICAL);
	}

	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		if(GuiLabelConfigCommon.RATE.equals(configLabel)) {
			return getRateFreq();
		}
		return super.getConfigValueUsingConfigLabelCommon(sensorId, configLabel);
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		return super.setConfigValueUsingConfigLabelCommon(sensorId, configLabel, valueToSet);
	}

	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		// no-op
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		// rate is refresh-code driven; nothing to set here
	}

	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		return null;
	}

	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		// no-op
	}

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		return false;
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		return false;
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		return false;
	}

	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		return null;
	}

	@Override
	public com.shimmerresearch.sensors.ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		return null;
	}
}
