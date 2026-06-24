package com.shimmerresearch.verisense.sensors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.calibration.CalibDetails;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic;
import com.shimmerresearch.driver.calibration.UtilCalibration;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.ActionSetting;

/**
 * Second-generation Verisense IMU: LSM6DSV (accelerometer + gyroscope) with the
 * LIS2MDL magnetometer read via the LSM6DSV sensor hub. Used on SR68-9/10 and
 * SR61-5/6.
 * <p>
 * Unlike {@link SensorLSM6DS3}, the on-device payload stores a variable-length
 * tagged FIFO ([TAG_CNT][X][Y][Z]) interleaving the three streams; the byte-level
 * extraction + per-stream timestamping lives in
 * {@code VerisenseDevice.parseDataBlockDataLsm6dsv(...)}. This class provides the
 * channel definitions, configuration and calibration. Calibration matches the
 * firmware/SDK nominal model: value = raw / sensitivity (identity alignment,
 * zero offset) where sensitivity = 32768/(FS*9.80665) for accel, 32768/FS for
 * gyro and 1/0.15 for mag.
 *
 * @author Mark Nolan
 */
public class SensorLSM6DSV extends AbstractSensor {

	private static final long serialVersionUID = 6336129814111379815L;

	public static final String ACCEL_ID = "Accel2";

	/** Same ODR setting drives accel and gyro. */
	protected LSM6DSV_RATE rate = LSM6DSV_RATE.RATE_60_HZ;
	protected LSM6DSV_ACCEL_RANGE rangeAccel = LSM6DSV_ACCEL_RANGE.RANGE_4G;
	protected LSM6DSV_GYRO_RANGE rangeGyro = LSM6DSV_GYRO_RANGE.RANGE_500DPS;
	protected LIS2MDL_RATE rateMag = LIS2MDL_RATE.RATE_15_HZ;

	public static enum LSM6DSV_RATE implements ISensorConfig {
		POWER_DOWN("Power-down", 0b0000, 0.0),
		RATE_1_875_HZ("1.875Hz", 0b0001, 1.875),
		RATE_7_5_HZ("7.5Hz", 0b0010, 7.5),
		RATE_15_HZ("15.0Hz", 0b0011, 15.0),
		RATE_30_HZ("30.0Hz", 0b0100, 30.0),
		RATE_60_HZ("60.0Hz", 0b0101, 60.0),
		RATE_120_HZ("120.0Hz", 0b0110, 120.0),
		RATE_240_HZ("240.0Hz", 0b0111, 240.0),
		RATE_480_HZ("480.0Hz", 0b1000, 480.0),
		RATE_960_HZ("960.0Hz", 0b1001, 960.0),
		RATE_1920_HZ("1920.0Hz", 0b1010, 1920.0),
		RATE_3840_HZ("3840.0Hz", 0b1011, 3840.0),
		RATE_7680_HZ("7680.0Hz", 0b1100, 7680.0);

		public String label;
		public Integer configValue;
		public double freqHz;

		public static Map<String, Integer> REF_MAP = new HashMap<>();
		static {
			for (LSM6DSV_RATE e : values()) { REF_MAP.put(e.label, e.configValue); }
		}
		static Map<Integer, LSM6DSV_RATE> BY_CONFIG_VALUE = new HashMap<>();
		static {
			for (LSM6DSV_RATE e : values()) { BY_CONFIG_VALUE.put(e.configValue, e); }
		}

		private LSM6DSV_RATE(String label, Integer configValue, double freqHz) {
			this.label = label; this.configValue = configValue; this.freqHz = freqHz;
		}
		public static String[] getLabels() { return REF_MAP.keySet().toArray(new String[REF_MAP.keySet().size()]); }
		public static Integer[] getConfigValues() { return REF_MAP.values().toArray(new Integer[REF_MAP.values().size()]); }
		public static LSM6DSV_RATE getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, POWER_DOWN.configValue, RATE_7680_HZ.configValue));
		}
	}

	public static enum LSM6DSV_ACCEL_RANGE implements ISensorConfig {
		RANGE_2G(UtilShimmer.UNICODE_PLUS_MINUS + " 2g", 0),
		RANGE_4G(UtilShimmer.UNICODE_PLUS_MINUS + " 4g", 1),
		RANGE_8G(UtilShimmer.UNICODE_PLUS_MINUS + " 8g", 2),
		RANGE_16G(UtilShimmer.UNICODE_PLUS_MINUS + " 16g", 3);

		String label; Integer configValue;
		static Map<String, Integer> REF_MAP = new HashMap<>();
		static { for (LSM6DSV_ACCEL_RANGE e : values()) { REF_MAP.put(e.label, e.configValue); } }
		static Map<Integer, LSM6DSV_ACCEL_RANGE> BY_CONFIG_VALUE = new HashMap<>();
		static { for (LSM6DSV_ACCEL_RANGE e : values()) { BY_CONFIG_VALUE.put(e.configValue, e); } }
		private LSM6DSV_ACCEL_RANGE(String label, Integer configValue) { this.label = label; this.configValue = configValue; }
		public static String[] getLabels() { return REF_MAP.keySet().toArray(new String[REF_MAP.keySet().size()]); }
		public static Integer[] getConfigValues() { return REF_MAP.values().toArray(new Integer[REF_MAP.values().size()]); }
		public static LSM6DSV_ACCEL_RANGE getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, RANGE_2G.configValue, RANGE_16G.configValue));
		}
	}

	public static enum LSM6DSV_GYRO_RANGE implements ISensorConfig {
		RANGE_125DPS(UtilShimmer.UNICODE_PLUS_MINUS + " 125dps", 0),
		RANGE_250DPS(UtilShimmer.UNICODE_PLUS_MINUS + " 250dps", 1),
		RANGE_500DPS(UtilShimmer.UNICODE_PLUS_MINUS + " 500dps", 2),
		RANGE_1000DPS(UtilShimmer.UNICODE_PLUS_MINUS + " 1000dps", 3),
		RANGE_2000DPS(UtilShimmer.UNICODE_PLUS_MINUS + " 2000dps", 4);

		String label; Integer configValue;
		static Map<String, Integer> REF_MAP = new HashMap<>();
		static { for (LSM6DSV_GYRO_RANGE e : values()) { REF_MAP.put(e.label, e.configValue); } }
		static Map<Integer, LSM6DSV_GYRO_RANGE> BY_CONFIG_VALUE = new HashMap<>();
		static { for (LSM6DSV_GYRO_RANGE e : values()) { BY_CONFIG_VALUE.put(e.configValue, e); } }
		private LSM6DSV_GYRO_RANGE(String label, Integer configValue) { this.label = label; this.configValue = configValue; }
		public static String[] getLabels() { return REF_MAP.keySet().toArray(new String[REF_MAP.keySet().size()]); }
		public static Integer[] getConfigValues() { return REF_MAP.values().toArray(new Integer[REF_MAP.values().size()]); }
		public static LSM6DSV_GYRO_RANGE getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, RANGE_125DPS.configValue, RANGE_2000DPS.configValue));
		}
	}

	/** Magnetometer output (sensor-hub) rate code. */
	public static enum LIS2MDL_RATE implements ISensorConfig {
		RATE_15_HZ("15.0Hz", 0, 15.0),
		RATE_30_HZ("30.0Hz", 1, 30.0),
		RATE_60_HZ("60.0Hz", 2, 60.0),
		RATE_120_HZ("120.0Hz", 3, 120.0);

		String label; Integer configValue; double freqHz;
		static Map<String, Integer> REF_MAP = new HashMap<>();
		static { for (LIS2MDL_RATE e : values()) { REF_MAP.put(e.label, e.configValue); } }
		static Map<Integer, LIS2MDL_RATE> BY_CONFIG_VALUE = new HashMap<>();
		static { for (LIS2MDL_RATE e : values()) { BY_CONFIG_VALUE.put(e.configValue, e); } }
		private LIS2MDL_RATE(String label, Integer configValue, double freqHz) { this.label = label; this.configValue = configValue; this.freqHz = freqHz; }
		public static String[] getLabels() { return REF_MAP.keySet().toArray(new String[REF_MAP.keySet().size()]); }
		public static Integer[] getConfigValues() { return REF_MAP.values().toArray(new Integer[REF_MAP.values().size()]); }
		public static LIS2MDL_RATE getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, RATE_15_HZ.configValue, RATE_120_HZ.configValue));
		}
	}

	public class GuiLabelSensors {
		public static final String ACCEL2 = "Accelerometer2";
		public static final String GYRO = "Gyroscope";
		public static final String MAG = "Magnetometer";
	}

	public static class LABEL_SENSOR_TILE {
		public static final String ACCEL2_GYRO_MAG = "ACCEL2 GYRO MAG";
	}

	public static class DatabaseChannelHandles {
		public static final String LSM6DSV_ACC_X = "LSM6DSV_ACC_X";
		public static final String LSM6DSV_ACC_Y = "LSM6DSV_ACC_Y";
		public static final String LSM6DSV_ACC_Z = "LSM6DSV_ACC_Z";
		public static final String LSM6DSV_GYRO_X = "LSM6DSV_GYRO_X";
		public static final String LSM6DSV_GYRO_Y = "LSM6DSV_GYRO_Y";
		public static final String LSM6DSV_GYRO_Z = "LSM6DSV_GYRO_Z";
		public static final String LIS2MDL_MAG_X = "LIS2MDL_MAG_X";
		public static final String LIS2MDL_MAG_Y = "LIS2MDL_MAG_Y";
		public static final String LIS2MDL_MAG_Z = "LIS2MDL_MAG_Z";
	}

	public class GuiLabelConfig {
		public static final String LSM6DSV_RATE = "Accel_Gyro_Rate_Gen2";
		public static final String LSM6DSV_ACCEL_RANGE = "Accel_Range_Gen2";
		public static final String LSM6DSV_GYRO_RANGE = "Gyro_Range_Gen2";
		public static final String LIS2MDL_RATE = "Mag_Rate_Gen2";
	}

	public static class ObjectClusterSensorName {
		public static String LSM6DSV_ACC_X = ACCEL_ID + "_X";
		public static String LSM6DSV_ACC_Y = ACCEL_ID + "_Y";
		public static String LSM6DSV_ACC_Z = ACCEL_ID + "_Z";
		public static String LSM6DSV_GYRO_X = "Gyro_X";
		public static String LSM6DSV_GYRO_Y = "Gyro_Y";
		public static String LSM6DSV_GYRO_Z = "Gyro_Z";
		public static String LIS2MDL_MAG_X = "Mag_X";
		public static String LIS2MDL_MAG_Y = "Mag_Y";
		public static String LIS2MDL_MAG_Z = "Mag_Z";
	}

	public static final class DatabaseConfigHandle {
		public static final String LSM6DSV_RANGE = "LSM6DSV_Range";
		public static final String LSM6DSV_RATE = "LSM6DSV_Rate";
		public static final String LIS2MDL_RATE = "LIS2MDL_Rate";
	}

	public static final ConfigOptionDetailsSensor CONFIG_OPTION_ACCEL_RANGE = new ConfigOptionDetailsSensor(
			SensorLSM6DSV.GuiLabelConfig.LSM6DSV_ACCEL_RANGE,
			SensorLSM6DSV.DatabaseConfigHandle.LSM6DSV_RANGE,
			LSM6DSV_ACCEL_RANGE.getLabels(), LSM6DSV_ACCEL_RANGE.getConfigValues(),
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV);

	public static final ConfigOptionDetailsSensor CONFIG_OPTION_GYRO_RANGE = new ConfigOptionDetailsSensor(
			SensorLSM6DSV.GuiLabelConfig.LSM6DSV_GYRO_RANGE,
			SensorLSM6DSV.DatabaseConfigHandle.LSM6DSV_RANGE,
			LSM6DSV_GYRO_RANGE.getLabels(), LSM6DSV_GYRO_RANGE.getConfigValues(),
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV);

	public static final ConfigOptionDetailsSensor CONFIG_OPTION_RATE = new ConfigOptionDetailsSensor(
			SensorLSM6DSV.GuiLabelConfig.LSM6DSV_RATE,
			SensorLSM6DSV.DatabaseConfigHandle.LSM6DSV_RATE,
			LSM6DSV_RATE.getLabels(), LSM6DSV_RATE.getConfigValues(),
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV);

	public static final ConfigOptionDetailsSensor CONFIG_OPTION_MAG_RATE = new ConfigOptionDetailsSensor(
			SensorLSM6DSV.GuiLabelConfig.LIS2MDL_RATE,
			SensorLSM6DSV.DatabaseConfigHandle.LIS2MDL_RATE,
			LIS2MDL_RATE.getLabels(), LIS2MDL_RATE.getConfigValues(),
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV);

	// ----------------- Calibration Start -----------------------
	// Identity alignment + zero offset so calibrated = raw / sensitivity, matching
	// the firmware/SDK nominal model (and the validated standalone decoder).
	public static final double[][] DEFAULT_OFFSET_VECTOR_LSM6DSV = {{0},{0},{0}};
	public static final double[][] DEFAULT_ALIGNMENT_MATRIX_LSM6DSV = {{1,0,0},{0,1,0},{0,0,1}};

	// Accel sensitivity (LSB per m/s^2) = 32768/(FS_g*9.80665)
	public static final double[][] SENS_ACCEL_2G  = {{1670.703,0,0},{0,1670.703,0},{0,0,1670.703}};
	public static final double[][] SENS_ACCEL_4G  = {{835.3517,0,0},{0,835.3517,0},{0,0,835.3517}};
	public static final double[][] SENS_ACCEL_8G  = {{417.6759,0,0},{0,417.6759,0},{0,0,417.6759}};
	public static final double[][] SENS_ACCEL_16G = {{208.8379,0,0},{0,208.8379,0},{0,0,208.8379}};

	// Gyro sensitivity (LSB per dps) = 32768/FS_dps
	public static final double[][] SENS_GYRO_125DPS  = {{262.144,0,0},{0,262.144,0},{0,0,262.144}};
	public static final double[][] SENS_GYRO_250DPS  = {{131.072,0,0},{0,131.072,0},{0,0,131.072}};
	public static final double[][] SENS_GYRO_500DPS  = {{65.536,0,0},{0,65.536,0},{0,0,65.536}};
	public static final double[][] SENS_GYRO_1000DPS = {{32.768,0,0},{0,32.768,0},{0,0,32.768}};
	public static final double[][] SENS_GYRO_2000DPS = {{16.384,0,0},{0,16.384,0},{0,0,16.384}};

	// Mag sensitivity (LSB per uT) = 1/0.15
	public static final double[][] SENS_MAG = {{6.666667,0,0},{0,6.666667,0},{0,0,6.666667}};

	public CalibDetailsKinematic calibDetailsAccel2g = new CalibDetailsKinematic(
			LSM6DSV_ACCEL_RANGE.RANGE_2G.configValue, LSM6DSV_ACCEL_RANGE.RANGE_2G.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_ACCEL_2G, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsAccel4g = new CalibDetailsKinematic(
			LSM6DSV_ACCEL_RANGE.RANGE_4G.configValue, LSM6DSV_ACCEL_RANGE.RANGE_4G.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_ACCEL_4G, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsAccel8g = new CalibDetailsKinematic(
			LSM6DSV_ACCEL_RANGE.RANGE_8G.configValue, LSM6DSV_ACCEL_RANGE.RANGE_8G.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_ACCEL_8G, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsAccel16g = new CalibDetailsKinematic(
			LSM6DSV_ACCEL_RANGE.RANGE_16G.configValue, LSM6DSV_ACCEL_RANGE.RANGE_16G.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_ACCEL_16G, DEFAULT_OFFSET_VECTOR_LSM6DSV);

	public CalibDetailsKinematic calibDetailsGyro125dps = new CalibDetailsKinematic(
			LSM6DSV_GYRO_RANGE.RANGE_125DPS.configValue, LSM6DSV_GYRO_RANGE.RANGE_125DPS.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_GYRO_125DPS, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsGyro250dps = new CalibDetailsKinematic(
			LSM6DSV_GYRO_RANGE.RANGE_250DPS.configValue, LSM6DSV_GYRO_RANGE.RANGE_250DPS.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_GYRO_250DPS, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsGyro500dps = new CalibDetailsKinematic(
			LSM6DSV_GYRO_RANGE.RANGE_500DPS.configValue, LSM6DSV_GYRO_RANGE.RANGE_500DPS.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_GYRO_500DPS, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsGyro1000dps = new CalibDetailsKinematic(
			LSM6DSV_GYRO_RANGE.RANGE_1000DPS.configValue, LSM6DSV_GYRO_RANGE.RANGE_1000DPS.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_GYRO_1000DPS, DEFAULT_OFFSET_VECTOR_LSM6DSV);
	public CalibDetailsKinematic calibDetailsGyro2000dps = new CalibDetailsKinematic(
			LSM6DSV_GYRO_RANGE.RANGE_2000DPS.configValue, LSM6DSV_GYRO_RANGE.RANGE_2000DPS.label,
			DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_GYRO_2000DPS, DEFAULT_OFFSET_VECTOR_LSM6DSV);

	public CalibDetailsKinematic calibDetailsMag = new CalibDetailsKinematic(
			0, "Default", DEFAULT_ALIGNMENT_MATRIX_LSM6DSV, SENS_MAG, DEFAULT_OFFSET_VECTOR_LSM6DSV);

	public CalibDetailsKinematic mCurrentCalibDetailsAccel = calibDetailsAccel4g;
	public CalibDetailsKinematic mCurrentCalibDetailsGyro = calibDetailsGyro500dps;
	public CalibDetailsKinematic mCurrentCalibDetailsMag = calibDetailsMag;
	// ----------------- Calibration end -----------------------

	//--------- Sensor info start --------------
	public static final SensorDetailsRef SENSOR_LSM6DSV_ACCEL = new SensorDetailsRef(
			Configuration.Verisense.SensorBitmap.LSM6DSV_ACCEL,
			Configuration.Verisense.SensorBitmap.LSM6DSV_ACCEL,
			GuiLabelSensors.ACCEL2,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV,
			Arrays.asList(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL),
			Arrays.asList(GuiLabelConfig.LSM6DSV_ACCEL_RANGE, GuiLabelConfig.LSM6DSV_GYRO_RANGE, GuiLabelConfig.LSM6DSV_RATE),
			Arrays.asList(ObjectClusterSensorName.LSM6DSV_ACC_X, ObjectClusterSensorName.LSM6DSV_ACC_Y, ObjectClusterSensorName.LSM6DSV_ACC_Z),
			false);

	public static final SensorDetailsRef SENSOR_LSM6DSV_GYRO = new SensorDetailsRef(
			Configuration.Verisense.SensorBitmap.LSM6DSV_GYRO,
			Configuration.Verisense.SensorBitmap.LSM6DSV_GYRO,
			GuiLabelSensors.GYRO,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV,
			Arrays.asList(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO),
			Arrays.asList(GuiLabelConfig.LSM6DSV_ACCEL_RANGE, GuiLabelConfig.LSM6DSV_GYRO_RANGE, GuiLabelConfig.LSM6DSV_RATE),
			Arrays.asList(ObjectClusterSensorName.LSM6DSV_GYRO_X, ObjectClusterSensorName.LSM6DSV_GYRO_Y, ObjectClusterSensorName.LSM6DSV_GYRO_Z),
			false);

	public static final SensorDetailsRef SENSOR_LIS2MDL_MAG = new SensorDetailsRef(
			Configuration.Verisense.SensorBitmap.LSM6DSV_MAG,
			Configuration.Verisense.SensorBitmap.LSM6DSV_MAG,
			GuiLabelSensors.MAG,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV,
			Arrays.asList(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG),
			Arrays.asList(GuiLabelConfig.LIS2MDL_RATE),
			Arrays.asList(ObjectClusterSensorName.LIS2MDL_MAG_X, ObjectClusterSensorName.LIS2MDL_MAG_Y, ObjectClusterSensorName.LIS2MDL_MAG_Z),
			false);

	public static final Map<Integer, SensorDetailsRef> SENSOR_MAP_REF;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL, SensorLSM6DSV.SENSOR_LSM6DSV_ACCEL);
		aMap.put(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO, SensorLSM6DSV.SENSOR_LSM6DSV_GYRO);
		aMap.put(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG, SensorLSM6DSV.SENSOR_LIS2MDL_MAG);
		SENSOR_MAP_REF = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	public static final ChannelDetails CHANNEL_LSM6DSV_ACCEL_X = new ChannelDetails(
			ObjectClusterSensorName.LSM6DSV_ACC_X, ObjectClusterSensorName.LSM6DSV_ACC_X, DatabaseChannelHandles.LSM6DSV_ACC_X,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.METER_PER_SECOND_SQUARE,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));
	public static final ChannelDetails CHANNEL_LSM6DSV_ACCEL_Y = new ChannelDetails(
			ObjectClusterSensorName.LSM6DSV_ACC_Y, ObjectClusterSensorName.LSM6DSV_ACC_Y, DatabaseChannelHandles.LSM6DSV_ACC_Y,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.METER_PER_SECOND_SQUARE,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));
	public static final ChannelDetails CHANNEL_LSM6DSV_ACCEL_Z = new ChannelDetails(
			ObjectClusterSensorName.LSM6DSV_ACC_Z, ObjectClusterSensorName.LSM6DSV_ACC_Z, DatabaseChannelHandles.LSM6DSV_ACC_Z,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.METER_PER_SECOND_SQUARE,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));

	public static final ChannelDetails CHANNEL_LSM6DSV_GYRO_X = new ChannelDetails(
			ObjectClusterSensorName.LSM6DSV_GYRO_X, ObjectClusterSensorName.LSM6DSV_GYRO_X, DatabaseChannelHandles.LSM6DSV_GYRO_X,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.DEGREES_PER_SECOND,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));
	public static final ChannelDetails CHANNEL_LSM6DSV_GYRO_Y = new ChannelDetails(
			ObjectClusterSensorName.LSM6DSV_GYRO_Y, ObjectClusterSensorName.LSM6DSV_GYRO_Y, DatabaseChannelHandles.LSM6DSV_GYRO_Y,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.DEGREES_PER_SECOND,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));
	public static final ChannelDetails CHANNEL_LSM6DSV_GYRO_Z = new ChannelDetails(
			ObjectClusterSensorName.LSM6DSV_GYRO_Z, ObjectClusterSensorName.LSM6DSV_GYRO_Z, DatabaseChannelHandles.LSM6DSV_GYRO_Z,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.DEGREES_PER_SECOND,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));

	public static final ChannelDetails CHANNEL_LIS2MDL_MAG_X = new ChannelDetails(
			ObjectClusterSensorName.LIS2MDL_MAG_X, ObjectClusterSensorName.LIS2MDL_MAG_X, DatabaseChannelHandles.LIS2MDL_MAG_X,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.LOCAL_FLUX,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));
	public static final ChannelDetails CHANNEL_LIS2MDL_MAG_Y = new ChannelDetails(
			ObjectClusterSensorName.LIS2MDL_MAG_Y, ObjectClusterSensorName.LIS2MDL_MAG_Y, DatabaseChannelHandles.LIS2MDL_MAG_Y,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.LOCAL_FLUX,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));
	public static final ChannelDetails CHANNEL_LIS2MDL_MAG_Z = new ChannelDetails(
			ObjectClusterSensorName.LIS2MDL_MAG_Z, ObjectClusterSensorName.LIS2MDL_MAG_Z, DatabaseChannelHandles.LIS2MDL_MAG_Z,
			CHANNEL_DATA_TYPE.INT16, 2, CHANNEL_DATA_ENDIAN.LSB, CHANNEL_UNITS.LOCAL_FLUX,
			Arrays.asList(CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL, CHANNEL_TYPE.DERIVED));

	public static final Map<String, ChannelDetails> CHANNEL_MAP_REF;
	static {
		Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.LSM6DSV_ACC_X, CHANNEL_LSM6DSV_ACCEL_X);
		aMap.put(ObjectClusterSensorName.LSM6DSV_ACC_Y, CHANNEL_LSM6DSV_ACCEL_Y);
		aMap.put(ObjectClusterSensorName.LSM6DSV_ACC_Z, CHANNEL_LSM6DSV_ACCEL_Z);
		aMap.put(ObjectClusterSensorName.LSM6DSV_GYRO_X, CHANNEL_LSM6DSV_GYRO_X);
		aMap.put(ObjectClusterSensorName.LSM6DSV_GYRO_Y, CHANNEL_LSM6DSV_GYRO_Y);
		aMap.put(ObjectClusterSensorName.LSM6DSV_GYRO_Z, CHANNEL_LSM6DSV_GYRO_Z);
		aMap.put(ObjectClusterSensorName.LIS2MDL_MAG_X, CHANNEL_LIS2MDL_MAG_X);
		aMap.put(ObjectClusterSensorName.LIS2MDL_MAG_Y, CHANNEL_LIS2MDL_MAG_Y);
		aMap.put(ObjectClusterSensorName.LIS2MDL_MAG_Z, CHANNEL_LIS2MDL_MAG_Z);
		CHANNEL_MAP_REF = Collections.unmodifiableMap(aMap);
	}
	//--------- Channel info end --------------

	public SensorLSM6DSV(ShimmerDevice shimmerDevice) {
		super(SENSORS.LSM6DSV, shimmerDevice);
		initialise();
	}

	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(SENSOR_MAP_REF, CHANNEL_MAP_REF);
	}

	@Override
	public void generateConfigOptionsMap() {
		mConfigOptionsMap.clear();
		addConfigOption(CONFIG_OPTION_ACCEL_RANGE);
		addConfigOption(CONFIG_OPTION_GYRO_RANGE);
		addConfigOption(CONFIG_OPTION_RATE);
		addConfigOption(CONFIG_OPTION_MAG_RATE);
	}

	@Override
	public void generateSensorGroupMapping() {
		int groupIndex = Configuration.Verisense.LABEL_SENSOR_TILE.ACCEL2_GYRO.ordinal();
		if(mShimmerVerObject.isShimmerGenVerisense()) {
			mSensorGroupingMap.put(groupIndex, new SensorGroupingDetails(
					LABEL_SENSOR_TILE.ACCEL2_GYRO_MAG,
					Arrays.asList(
							Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO,
							Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL,
							Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoLSM6DSV));
		}
		super.updateSensorGroupingMap();
	}

	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType, ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		String guiLabel = sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel;
		if(guiLabel.equals(GuiLabelSensors.ACCEL2) && mCurrentCalibDetailsAccel!=null) {
			objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);
			calibrateXyz(objectCluster, mCurrentCalibDetailsAccel, CHANNEL_LSM6DSV_ACCEL_X, CHANNEL_LSM6DSV_ACCEL_Y, CHANNEL_LSM6DSV_ACCEL_Z, false);
		} else if(guiLabel.equals(GuiLabelSensors.GYRO) && mCurrentCalibDetailsGyro!=null) {
			objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);
			calibrateXyz(objectCluster, mCurrentCalibDetailsGyro, CHANNEL_LSM6DSV_GYRO_X, CHANNEL_LSM6DSV_GYRO_Y, CHANNEL_LSM6DSV_GYRO_Z, true);
		} else if(guiLabel.equals(GuiLabelSensors.MAG) && mCurrentCalibDetailsMag!=null) {
			objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);
			calibrateXyz(objectCluster, mCurrentCalibDetailsMag, CHANNEL_LIS2MDL_MAG_X, CHANNEL_LIS2MDL_MAG_Y, CHANNEL_LIS2MDL_MAG_Z, true);
		}
		return objectCluster;
	}

	/**
	 * Adds default-calibrated (CAL) data. When {@code addDerived} is true also adds
	 * a DERIVED copy - mirrors the gen-1 LSM6DS3 behaviour where gyro/mag emit a
	 * DERIVED ("_CAL") output file while accel emits the default-cal file.
	 */
	private void calibrateXyz(ObjectCluster objectCluster, CalibDetailsKinematic calibDetails, ChannelDetails chX, ChannelDetails chY, ChannelDetails chZ, boolean addDerived) {
		double[] unCal = new double[3];
		unCal[0] = objectCluster.getFormatClusterValue(chX, CHANNEL_TYPE.UNCAL);
		unCal[1] = objectCluster.getFormatClusterValue(chY, CHANNEL_TYPE.UNCAL);
		unCal[2] = objectCluster.getFormatClusterValue(chZ, CHANNEL_TYPE.UNCAL);
		double[] cal = UtilCalibration.calibrateInertialSensorData(unCal, calibDetails.getDefaultMatrixMultipliedInverseAMSM(), calibDetails.getDefaultOffsetVector());
		objectCluster.addCalData(chX, cal[0], objectCluster.getIndexKeeper()-3);
		objectCluster.addCalData(chY, cal[1], objectCluster.getIndexKeeper()-2);
		objectCluster.addCalData(chZ, cal[2], objectCluster.getIndexKeeper()-1);
		if(addDerived) {
			objectCluster.addData(chX.mObjectClusterName, CHANNEL_TYPE.DERIVED, chX.mDefaultCalUnits, cal[0], objectCluster.getIndexKeeper()-3, false);
			objectCluster.addData(chY.mObjectClusterName, CHANNEL_TYPE.DERIVED, chY.mDefaultCalUnits, cal[1], objectCluster.getIndexKeeper()-2, false);
			objectCluster.addData(chZ.mObjectClusterName, CHANNEL_TYPE.DERIVED, chZ.mDefaultCalUnits, cal[2], objectCluster.getIndexKeeper()-1, false);
		}
	}

	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		// no-op
	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		// TODO config-write support (streaming/op-config) to be added with the gen-2 config UI.
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		if(isAnyChannelEnabled()) {
			if(commType == COMMUNICATION_TYPE.SD) {
				// Payload header bytes 18..20 (LSM6DSV_CFG_0..2). PAYLOAD_CONFIG_BYTE_INDEX is
				// relative to the config region (rel 14/15/16 = abs 18/19/20).
				int cfg0 = configBytes[com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG3] & 0xFF;
				int cfg1 = configBytes[com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG4] & 0xFF;
				int cfg2 = configBytes[com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG5] & 0xFF;
				// Accel and gyro share the LSM6DSV ODR. Whichever is enabled carries the
				// rate; the disabled one's ODR field is 0 (power-down), so take the non-zero.
				int accelOdr = cfg0 & 0x0F;
				int gyroOdr = cfg1 & 0x0F;
				setRateConfigValue(accelOdr != 0 ? accelOdr : gyroOdr);
				setAccelRangeConfigValue((cfg0 >> 4) & 0x03);
				setGyroRangeConfigValue((cfg1 >> 4) & 0x0F);
				setMagRateConfigValue(cfg2 & 0x03);
			}
		}
	}

	private boolean isAnyChannelEnabled() {
		return isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG);
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		Object returnValue = null;
		switch(configLabel) {
			case(GuiLabelConfig.LSM6DSV_RATE): setRateConfigValue((int)valueToSet); break;
			case(GuiLabelConfig.LSM6DSV_ACCEL_RANGE): setAccelRangeConfigValue((int)valueToSet); break;
			case(GuiLabelConfig.LSM6DSV_GYRO_RANGE): setGyroRangeConfigValue((int)valueToSet); break;
			case(GuiLabelConfig.LIS2MDL_RATE): setMagRateConfigValue((int)valueToSet); break;
			default: returnValue = super.setConfigValueUsingConfigLabelCommon(sensorId, configLabel, valueToSet); break;
		}
		return returnValue;
	}

	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		Object returnValue = null;
		switch(configLabel) {
			case(GuiLabelConfig.LSM6DSV_RATE): returnValue = getRateConfigValue(); break;
			case(GuiLabelConfig.LSM6DSV_ACCEL_RANGE): returnValue = getAccelRangeConfigValue(); break;
			case(GuiLabelConfig.LSM6DSV_GYRO_RANGE): returnValue = getGyroRangeConfigValue(); break;
			case(GuiLabelConfig.LIS2MDL_RATE): returnValue = getMagRateConfigValue(); break;
			case(GuiLabelConfigCommon.CALIBRATION_CURRENT_PER_SENSOR):
				if(sensorId==Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO) { returnValue = mCurrentCalibDetailsGyro; }
				else if(sensorId==Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL) { returnValue = mCurrentCalibDetailsAccel; }
				else if(sensorId==Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG) { returnValue = mCurrentCalibDetailsMag; }
				break;
			case(GuiLabelConfigCommon.RATE): returnValue = getRateFreq(); break;
			default: returnValue = super.getConfigValueUsingConfigLabelCommon(sensorId, configLabel); break;
		}
		return returnValue;
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		if(samplingRateHz==0) {
			setRate(LSM6DSV_RATE.POWER_DOWN);
		} else {
			LSM6DSV_RATE chosen = LSM6DSV_RATE.POWER_DOWN;
			for(LSM6DSV_RATE r : LSM6DSV_RATE.values()) {
				if(r==LSM6DSV_RATE.POWER_DOWN) { continue; }
				chosen = r;
				if(r.freqHz>=samplingRateHz) { break; }
			}
			setRate(chosen);
		}
	}

	public double getRateFreq() {
		return rate.freqHz;
	}

	public void setRate(LSM6DSV_RATE rate) { this.rate = rate; }
	public int getRateConfigValue() { return rate.configValue; }
	public void setRateConfigValue(int configValue) { setRate(LSM6DSV_RATE.getForConfigValue(configValue)); }

	public int getAccelRangeConfigValue() { return rangeAccel.configValue; }
	public void setAccelRangeConfigValue(int configValue) {
		rangeAccel = LSM6DSV_ACCEL_RANGE.getForConfigValue(configValue);
		updateCurrentAccelCalibInUse();
	}
	public int getGyroRangeConfigValue() { return rangeGyro.configValue; }
	public void setGyroRangeConfigValue(int configValue) {
		rangeGyro = LSM6DSV_GYRO_RANGE.getForConfigValue(configValue);
		updateCurrentGyroCalibInUse();
	}
	public int getMagRateConfigValue() { return rateMag.configValue; }
	public void setMagRateConfigValue(int configValue) { rateMag = LIS2MDL_RATE.getForConfigValue(configValue); }

	public double getMagRateFreq() { return rateMag.freqHz; }

	public String getAccelRangeString() { return rangeAccel.label; }
	public String getGyroRangeString() { return rangeGyro.label; }
	public String getMagRateString() { return rateMag.label; }

	@Override
	public void generateCalibMap() {
		super.generateCalibMap();
		TreeMap<Integer, CalibDetails> calibMapAccel = new TreeMap<Integer, CalibDetails>();
		calibMapAccel.put(calibDetailsAccel2g.mRangeValue, calibDetailsAccel2g);
		calibMapAccel.put(calibDetailsAccel4g.mRangeValue, calibDetailsAccel4g);
		calibMapAccel.put(calibDetailsAccel8g.mRangeValue, calibDetailsAccel8g);
		calibMapAccel.put(calibDetailsAccel16g.mRangeValue, calibDetailsAccel16g);
		setCalibrationMapPerSensor(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL, calibMapAccel);

		TreeMap<Integer, CalibDetails> calibMapGyro = new TreeMap<Integer, CalibDetails>();
		calibMapGyro.put(calibDetailsGyro125dps.mRangeValue, calibDetailsGyro125dps);
		calibMapGyro.put(calibDetailsGyro250dps.mRangeValue, calibDetailsGyro250dps);
		calibMapGyro.put(calibDetailsGyro500dps.mRangeValue, calibDetailsGyro500dps);
		calibMapGyro.put(calibDetailsGyro1000dps.mRangeValue, calibDetailsGyro1000dps);
		calibMapGyro.put(calibDetailsGyro2000dps.mRangeValue, calibDetailsGyro2000dps);
		setCalibrationMapPerSensor(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO, calibMapGyro);

		TreeMap<Integer, CalibDetails> calibMapMag = new TreeMap<Integer, CalibDetails>();
		calibMapMag.put(calibDetailsMag.mRangeValue, calibDetailsMag);
		setCalibrationMapPerSensor(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG, calibMapMag);

		updateCurrentAccelCalibInUse();
		updateCurrentGyroCalibInUse();
		updateCurrentMagCalibInUse();
	}

	public void updateCurrentAccelCalibInUse() {
		mCurrentCalibDetailsAccel = getCurrentCalibDetailsIfKinematic(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL, getAccelRangeConfigValue());
	}
	public void updateCurrentGyroCalibInUse() {
		mCurrentCalibDetailsGyro = getCurrentCalibDetailsIfKinematic(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO, getGyroRangeConfigValue());
	}
	public void updateCurrentMagCalibInUse() {
		mCurrentCalibDetailsMag = getCurrentCalibDetailsIfKinematic(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG, 0);
	}

	public CalibDetailsKinematic getCurrentCalibDetailsIfKinematic(int sensorId, int range) {
		CalibDetails calibDetails = getCalibForSensor(sensorId, range);
		if(calibDetails instanceof CalibDetailsKinematic) {
			return (CalibDetailsKinematic) calibDetails;
		}
		return null;
	}

	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		// TODO Auto-generated method stub
	}

	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		return null;
	}

	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		return null;
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		return false;
	}

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		return true;
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		return false;
	}

}
