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
 * Decoder for the VD6283TX45 ambient light sensor (Verisense data-block sensor
 * id = 7, second-generation hardware). Data block = N samples x 18 bytes: 6
 * channels x 24-bit LE raw ALS counts in order RED, VISIBLE, BLUE, GREEN, IR,
 * CLEAR. The firmware only ever emits FULL blocks of
 * {@link #NUM_SAMPLES_PER_BLOCK} samples (partial blocks are discarded on stop
 * - hal_slowSensorSampler.c), so the on-disk block size is fixed.
 * <p>
 * UNCAL = raw 24-bit counts. CAL = counts normalised for the configured gain
 * and exposure (both recorded in payload header bytes 30/31), plus the derived
 * illuminance (lux) and correlated colour temperature (CCT) computed from the
 * normalised RED/GREEN/BLUE channels via the VD6283 ALS-to-XYZ matrix and
 * McCamy's polynomial - ported from firmware App_vd6283tx.c and kept in step
 * with the web SDK's SensorVD6283.ts.
 * <p>
 * NOTE (dark channel): when the op-config dark-channel bit is set (header byte
 * 30 bit 7) the chip routes the covered-photodiode DARK baseline onto the
 * second slot INSTEAD of the visible reading. The slot's column is currently
 * always named {@code Light_Visible}; the "Slot1 = Visible|Dark" entry in the
 * CSV sensor-config line records which reading it actually carries. RED, GREEN
 * and BLUE are unaffected, so lux/CCT remain valid in either mode. No
 * dark-enabled recording exists yet - revisit the naming when one does.
 * <p>
 * The sample rate is configured in the op config (LIGHT_SAMPLE_RATE_INDEX) but
 * is NOT mirrored into the stored payload header, and the achieved rate also
 * differs from both the configured rate and 1/exposure (the chip adds dead
 * time per measurement, e.g. ~110 ms/sample for the 100 ms exposure). The
 * getRateFreq() value here is therefore only the exposure-limited ESTIMATE
 * used to seed data-block timing; the parser refines it per payload from the
 * spacing of consecutive light-block timestamps
 * (PayloadContentsDetailsV8orAbove.refineLightSamplingRateFromBlockTicks).
 */
public class SensorVD6283 extends AbstractSensor {

	private static final long serialVersionUID = 5804427848836576803L;

	public static final int NUM_CHANNELS = 6;
	public static final int BYTES_PER_CHANNEL = 3;
	public static final int BYTES_PER_SAMPLE = NUM_CHANNELS * BYTES_PER_CHANNEL;
	/** Firmware NUM_LIGHT_SAMPLES_PER_BLOCK (hal_slowSensorSampler.h). */
	public static final int NUM_SAMPLES_PER_BLOCK = 10;
	/** Fixed on-disk sensor-data size of a light block (no count prefix). */
	public static final int BLOCK_DATA_BYTES = NUM_SAMPLES_PER_BLOCK * BYTES_PER_SAMPLE;

	/** Op-config index -> exposure us (firmware vd6283_exposureIndexToUs). */
	public static final int[] EXPOSURE_US_TABLE = {100000, 1600, 6400, 12800, 25600, 51200, 102400, 204800};
	/** Op-config index -> 8.8 fixed-point gain (firmware vd6283_gainIndexToValue). */
	public static final int[] GAIN_8P8_TABLE = {0x0100, 0x01AB, 0x0280, 0x0500, 0x0A00, 0x1900, 0x3200, 0x42AB};
	/** Reference exposure the normalisation is relative to (firmware VD6283TX_DEFAULT_EXPO). */
	public static final double DEFAULT_EXPO_US = 100800;
	/** ALS-counts -> XYZ matrix (firmware App_vd6283tx.c). Rows are X, Y, Z. */
	private static final double[][] XYZ_MATRIX = {
			{0.205570, 0.416700, -0.143816},
			{-0.028752, 0.506372, -0.120614},
			{-0.552625, 0.335866, 0.494781}};

	/** Poll ceiling in continuous mode (firmware slow-sensor sampler). */
	public static final double MAX_SAMPLE_RATE_HZ = 20.0;

	public static final String UNITS_LUX = "lux";
	public static final String UNITS_KELVIN = "Kelvin";

	private int gainIndex = 0;
	private int exposureIndex = 0;
	private boolean darkChannelEnabled = false;

	public class GuiLabelSensors {
		public static final String LIGHT = "Light";
	}

	public static class ObjectClusterSensorName {
		public static String LIGHT_RED = "Light_Red";
		public static String LIGHT_VISIBLE = "Light_Visible";
		public static String LIGHT_BLUE = "Light_Blue";
		public static String LIGHT_GREEN = "Light_Green";
		public static String LIGHT_IR = "Light_IR";
		public static String LIGHT_CLEAR = "Light_Clear";
		public static String LIGHT_LUX = "Light_Lux";
		public static String LIGHT_CCT = "Light_CCT";
	}

	public static class DatabaseChannelHandles {
		public static final String LIGHT_RED = "VD6283_RED";
		public static final String LIGHT_VISIBLE = "VD6283_VISIBLE";
		public static final String LIGHT_BLUE = "VD6283_BLUE";
		public static final String LIGHT_GREEN = "VD6283_GREEN";
		public static final String LIGHT_IR = "VD6283_IR";
		public static final String LIGHT_CLEAR = "VD6283_CLEAR";
		public static final String LIGHT_LUX = "VD6283_LUX";
		public static final String LIGHT_CCT = "VD6283_CCT";
	}

	//--------- Sensor info start --------------
	public static final SensorDetailsRef SENSOR_VD6283 = new SensorDetailsRef(
			Configuration.Verisense.SensorBitmap.VD6283,
			Configuration.Verisense.SensorBitmap.VD6283,
			GuiLabelSensors.LIGHT,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV,
			Arrays.asList(Configuration.Verisense.SENSOR_ID.VD6283),
			Arrays.asList(),
			Arrays.asList(ObjectClusterSensorName.LIGHT_RED,
					ObjectClusterSensorName.LIGHT_VISIBLE,
					ObjectClusterSensorName.LIGHT_BLUE,
					ObjectClusterSensorName.LIGHT_GREEN,
					ObjectClusterSensorName.LIGHT_IR,
					ObjectClusterSensorName.LIGHT_CLEAR,
					ObjectClusterSensorName.LIGHT_LUX,
					ObjectClusterSensorName.LIGHT_CCT),
			false);

	public static final Map<Integer, SensorDetailsRef> SENSOR_MAP_REF;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Verisense.SENSOR_ID.VD6283, SensorVD6283.SENSOR_VD6283);
		SENSOR_MAP_REF = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	private static ChannelDetails createRawChannel(String objectClusterName, String databaseName) {
		return new ChannelDetails(objectClusterName, objectClusterName, databaseName,
				CHANNEL_DATA_TYPE.UINT24, BYTES_PER_CHANNEL, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.NO_UNITS,
				Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL));
	}

	public static final ChannelDetails CHANNEL_LIGHT_RED = createRawChannel(ObjectClusterSensorName.LIGHT_RED, DatabaseChannelHandles.LIGHT_RED);
	public static final ChannelDetails CHANNEL_LIGHT_VISIBLE = createRawChannel(ObjectClusterSensorName.LIGHT_VISIBLE, DatabaseChannelHandles.LIGHT_VISIBLE);
	public static final ChannelDetails CHANNEL_LIGHT_BLUE = createRawChannel(ObjectClusterSensorName.LIGHT_BLUE, DatabaseChannelHandles.LIGHT_BLUE);
	public static final ChannelDetails CHANNEL_LIGHT_GREEN = createRawChannel(ObjectClusterSensorName.LIGHT_GREEN, DatabaseChannelHandles.LIGHT_GREEN);
	public static final ChannelDetails CHANNEL_LIGHT_IR = createRawChannel(ObjectClusterSensorName.LIGHT_IR, DatabaseChannelHandles.LIGHT_IR);
	public static final ChannelDetails CHANNEL_LIGHT_CLEAR = createRawChannel(ObjectClusterSensorName.LIGHT_CLEAR, DatabaseChannelHandles.LIGHT_CLEAR);

	/** Computed channel - no bytes in the data packet (CAL only). */
	public static final ChannelDetails CHANNEL_LIGHT_LUX = new ChannelDetails(
			ObjectClusterSensorName.LIGHT_LUX, ObjectClusterSensorName.LIGHT_LUX, DatabaseChannelHandles.LIGHT_LUX,
			UNITS_LUX, Arrays.asList(CHANNEL_TYPE.CAL));
	/** Computed channel - no bytes in the data packet (CAL only). */
	public static final ChannelDetails CHANNEL_LIGHT_CCT = new ChannelDetails(
			ObjectClusterSensorName.LIGHT_CCT, ObjectClusterSensorName.LIGHT_CCT, DatabaseChannelHandles.LIGHT_CCT,
			UNITS_KELVIN, Arrays.asList(CHANNEL_TYPE.CAL));

	public static final Map<String, ChannelDetails> CHANNEL_MAP_REF;
	static {
		Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.LIGHT_RED, CHANNEL_LIGHT_RED);
		aMap.put(ObjectClusterSensorName.LIGHT_VISIBLE, CHANNEL_LIGHT_VISIBLE);
		aMap.put(ObjectClusterSensorName.LIGHT_BLUE, CHANNEL_LIGHT_BLUE);
		aMap.put(ObjectClusterSensorName.LIGHT_GREEN, CHANNEL_LIGHT_GREEN);
		aMap.put(ObjectClusterSensorName.LIGHT_IR, CHANNEL_LIGHT_IR);
		aMap.put(ObjectClusterSensorName.LIGHT_CLEAR, CHANNEL_LIGHT_CLEAR);
		aMap.put(ObjectClusterSensorName.LIGHT_LUX, CHANNEL_LIGHT_LUX);
		aMap.put(ObjectClusterSensorName.LIGHT_CCT, CHANNEL_LIGHT_CCT);
		CHANNEL_MAP_REF = Collections.unmodifiableMap(aMap);
	}
	//--------- Channel info end --------------

	public SensorVD6283(ShimmerDevice shimmerDevice) {
		super(SENSORS.VD6283, shimmerDevice);
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
		// UNCAL for the six 24-bit count channels; the two computed channels have no
		// bytes in the packet so processDataCommon adds a harmless 0 for them.
		objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);

		double red = objectCluster.getFormatClusterValue(CHANNEL_LIGHT_RED, CHANNEL_TYPE.UNCAL);
		double visibleOrDark = objectCluster.getFormatClusterValue(CHANNEL_LIGHT_VISIBLE, CHANNEL_TYPE.UNCAL);
		double blue = objectCluster.getFormatClusterValue(CHANNEL_LIGHT_BLUE, CHANNEL_TYPE.UNCAL);
		double green = objectCluster.getFormatClusterValue(CHANNEL_LIGHT_GREEN, CHANNEL_TYPE.UNCAL);
		double ir = objectCluster.getFormatClusterValue(CHANNEL_LIGHT_IR, CHANNEL_TYPE.UNCAL);
		double clear = objectCluster.getFormatClusterValue(CHANNEL_LIGHT_CLEAR, CHANNEL_TYPE.UNCAL);

		// CAL = gain/exposure-normalised counts (comparable across configurations).
		int indexBase = objectCluster.getIndexKeeper() - 8;
		objectCluster.addCalData(CHANNEL_LIGHT_RED, normalizeForXyz(red), indexBase);
		objectCluster.addCalData(CHANNEL_LIGHT_VISIBLE, normalizeForXyz(visibleOrDark), indexBase + 1);
		objectCluster.addCalData(CHANNEL_LIGHT_BLUE, normalizeForXyz(blue), indexBase + 2);
		objectCluster.addCalData(CHANNEL_LIGHT_GREEN, normalizeForXyz(green), indexBase + 3);
		objectCluster.addCalData(CHANNEL_LIGHT_IR, normalizeForXyz(ir), indexBase + 4);
		objectCluster.addCalData(CHANNEL_LIGHT_CLEAR, normalizeForXyz(clear), indexBase + 5);

		// lux/CCT derive from RED/GREEN/BLUE, so the dark-channel selection (which
		// only affects the second slot) leaves them valid in either mode.
		double[] luxCct = computeLuxCct(red, green, blue);
		objectCluster.addCalData(CHANNEL_LIGHT_LUX, luxCct[0], indexBase + 6);
		objectCluster.addCalData(CHANNEL_LIGHT_CCT, luxCct[1], indexBase + 7);

		return objectCluster;
	}

	/** Normalise a raw channel count for the XYZ transform (gain + exposure divided
	 * out). Float division - slightly more precise than the firmware's integer
	 * division of the 16.8/8.8 fixed-point values; differences are small. */
	public double normalizeForXyz(double count) {
		double gain8p8 = GAIN_8P8_TABLE[gainIndex & 0x07];
		double expoScale = DEFAULT_EXPO_US / getExposureUs();
		return (expoScale * (count / 256.0)) / (gain8p8 / 256.0);
	}

	/** Illuminance (lux, XYZ Y clamped to >= 0) and CCT (Kelvin, McCamy; 0 if
	 * undefined) from raw RED/GREEN/BLUE counts. */
	public double[] computeLuxCct(double red, double green, double blue) {
		double r = normalizeForXyz(red);
		double g = normalizeForXyz(green);
		double b = normalizeForXyz(blue);

		double X = XYZ_MATRIX[0][0]*r + XYZ_MATRIX[0][1]*g + XYZ_MATRIX[0][2]*b;
		double Y = XYZ_MATRIX[1][0]*r + XYZ_MATRIX[1][1]*g + XYZ_MATRIX[1][2]*b;
		double Z = XYZ_MATRIX[2][0]*r + XYZ_MATRIX[2][1]*g + XYZ_MATRIX[2][2]*b;

		double lux = Y < 0 ? 0 : Y;
		double norm = X + Y + Z;
		double cct = 0;
		if (norm != 0) {
			double x = X / norm;
			double y = Y / norm;
			double n = (x - 0.3320) / (0.1858 - y);
			cct = 449*n*n*n + 3525*n*n + 6823.3*n + 5520.33;
		}
		return new double[] {lux, cct};
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		if(commType == COMMUNICATION_TYPE.SD && isSensorEnabled(Configuration.Verisense.SENSOR_ID.VD6283)) {
			int gainAndDark = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.LIGHT_GAIN_AND_DARK] & 0xFF;
			gainIndex = gainAndDark & 0x07;
			darkChannelEnabled = (gainAndDark & 0x80) != 0;
			exposureIndex = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.LIGHT_EXPOSURE] & 0xFF;
		}
	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		// SD-parse only for now.
	}

	public int getExposureUs() {
		return EXPOSURE_US_TABLE[(exposureIndex >= 0 && exposureIndex < EXPOSURE_US_TABLE.length) ? exposureIndex : 0];
	}

	public double getGain() {
		return GAIN_8P8_TABLE[gainIndex & 0x07] / 256.0;
	}

	public boolean isDarkChannelEnabled() {
		return darkChannelEnabled;
	}

	/**
	 * Exposure-limited sample-rate ESTIMATE (Hz). The configured rate is not in
	 * the stored payload header and the chip adds per-measurement dead time, so
	 * this only seeds data-block timing - the parser refines the rate per payload
	 * from consecutive light-block timestamps.
	 */
	public double getRateFreq() {
		return Math.min(MAX_SAMPLE_RATE_HZ, 1e6 / getExposureUs());
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
		// rate is exposure/op-config driven; nothing to set here
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
