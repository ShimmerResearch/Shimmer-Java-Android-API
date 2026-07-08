package com.shimmerresearch.sensors.bmpX80;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.bluetooth.BtCommandDetails;
import com.shimmerresearch.driver.ConfigByteLayout;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.ActionSetting;
import com.shimmerresearch.sensors.bmpX80.SensorBMPX80.GuiLabelConfig;
import com.shimmerresearch.sensors.bmpX80.SensorBMPX80.GuiLabelSensors;
import com.shimmerresearch.sensors.bmpX80.SensorBMPX80.LABEL_SENSOR_TILE;

/**
 * Pressure/Temperature sensor class for the Bosch BMP581, fitted to up-rev'd
 * Shimmer3R boards in place of the BMP390.
 *
 * Unlike BMP180/BMP280/BMP390, the BMP581 streams already-compensated pressure
 * (Pa) and temperature (deg C) directly, so there are no calibration
 * coefficients to fetch, store or apply (see {@link CalibDetailsBmp581}). The
 * on-wire packet layout (3 bytes pressure + 3 bytes temperature, both 24-bit)
 * matches the BMP390, so channel sizes/offsets are unchanged.
 *
 * @author Shimmer
 */
public class SensorBMP581 extends SensorBMPX80 {

	private static final long serialVersionUID = 5921564795919021001L;

	//--------- Sensor specific variables start --------------
	public static class DatabaseChannelHandles {
		public static final String PRESSURE_BMP581 = "BMP581_Pressure";
		public static final String TEMPERATURE_BMP581 = "BMP581_Temperature";
	}

	public static final class DatabaseConfigHandle {
		public static final String PRESSURE_PRECISION_BMP581 = "BMP581_Pressure_Precision";
		public static final String PRESSURE_RATE = "BMP581_Pres_Rate";
		// NB: no PAR_* calibration handles - BMP581 has no downloadable coefficients.
	}

	public static final class ObjectClusterSensorName {
		public static final String TEMPERATURE_BMP581 = "Temperature_BMP581";
		public static final String PRESSURE_BMP581 = "Pressure_BMP581";
	}
	//--------- Sensor specific variables end --------------

	//--------- Constructors for this class start --------------
	public SensorBMP581(ShimmerVerObject svo) {
		super(SENSORS.BMP581, svo);
		initialise();
	}

	public SensorBMP581(ShimmerDevice shimmerDevice) {
		super(SENSORS.BMP581, shimmerDevice);
		initialise();
	}
	//--------- Constructors for this class end --------------

	//--------- Bluetooth commands start --------------
	// Oversampling only. There is deliberately NO GET_PRESSURE_CALIBRATION_COEFFICIENTS
	// command - the BMP581 firmware NACKs it because the sensor self-compensates.
	public static final byte SET_PRESSURE_OVERSAMPLING_RATIO_COMMAND = (byte) 0x52;
	public static final byte PRESSURE_OVERSAMPLING_RATIO_RESPONSE    = (byte) 0x53;
	public static final byte GET_PRESSURE_OVERSAMPLING_RATIO_COMMAND = (byte) 0x54;

	public static final Map<Byte, BtCommandDetails> mBtGetCommandMap;
	static {
		Map<Byte, BtCommandDetails> aMap = new LinkedHashMap<Byte, BtCommandDetails>();
		aMap.put(GET_PRESSURE_OVERSAMPLING_RATIO_COMMAND, new BtCommandDetails(GET_PRESSURE_OVERSAMPLING_RATIO_COMMAND, "GET_PRESSURE_OVERSAMPLING_RATIO_COMMAND", PRESSURE_OVERSAMPLING_RATIO_RESPONSE));
		mBtGetCommandMap = Collections.unmodifiableMap(aMap);
	}

	public static final Map<Byte, BtCommandDetails> mBtSetCommandMap;
	static {
		Map<Byte, BtCommandDetails> aMap = new LinkedHashMap<Byte, BtCommandDetails>();
		aMap.put(SET_PRESSURE_OVERSAMPLING_RATIO_COMMAND, new BtCommandDetails(SET_PRESSURE_OVERSAMPLING_RATIO_COMMAND, "SET_PRESSURE_OVERSAMPLING_RATIO_COMMAND"));
		mBtSetCommandMap = Collections.unmodifiableMap(aMap);
	}
	//--------- Bluetooth commands end --------------

	//--------- Configuration options start --------------
	// BMP581 gains two extra oversampling steps (x64, x128) vs the BMP390 (0..5).
	public static final String[] ListofPressureResolutionBMP581 = {"Lowest Power","Low","Standard","High","High Res","x32","x64","Highest Res"};
	public static final Integer[] ListofPressureResolutionConfigValuesBMP581 = {0,1,2,3,4,5,6,7};
	public static final String[] ListofPressureRateBMP581 = SensorBMP390.ListofPressureRateBMP390;
	public static final Integer[] ListofPressureRateConfigValuesBMP581 = SensorBMP390.ListofPressureRateConfigValuesBMP390;

	public static final ConfigOptionDetailsSensor configOptionPressureResolutionBMP581 = new ConfigOptionDetailsSensor(
			SensorBMPX80.GuiLabelConfig.PRESSURE_RESOLUTION,
			DatabaseConfigHandle.PRESSURE_PRECISION_BMP581,
			ListofPressureResolutionBMP581,
			ListofPressureResolutionConfigValuesBMP581,
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoBMP581);

	public static final ConfigOptionDetailsSensor configOptionPressureRateBMP581 = new ConfigOptionDetailsSensor(
			SensorBMPX80.GuiLabelConfig.PRESSURE_RATE,
			DatabaseConfigHandle.PRESSURE_RATE,
			ListofPressureRateBMP581,
			ListofPressureRateConfigValuesBMP581,
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoBMP581);
	//--------- Configuration options end --------------

	//--------- Sensor info start --------------
	public static final SensorDetailsRef sensorBmp581 = new SensorDetailsRef(
			0x04 << (2 * 8),
			0x04 << (2 * 8),
			GuiLabelSensors.PRESS_TEMP_BMPX80,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoBMP581,
			Arrays.asList(GuiLabelConfig.PRESSURE_RESOLUTION),
			Arrays.asList(ObjectClusterSensorName.TEMPERATURE_BMP581,
					ObjectClusterSensorName.PRESSURE_BMP581));

	public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_BMP581_PRESSURE, sensorBmp581);
		mSensorMapRef = Collections.unmodifiableMap(aMap);
	}

	public static final SensorGroupingDetails sensorGroupBmp581 = new SensorGroupingDetails(
			LABEL_SENSOR_TILE.PRESSURE_TEMPERATURE,
			Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_BMP581_PRESSURE),
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoBMP581);
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	public static final ChannelDetails channelBmp581Press = new ChannelDetails(
			ObjectClusterSensorName.PRESSURE_BMP581,
			ObjectClusterSensorName.PRESSURE_BMP581,
			DatabaseChannelHandles.PRESSURE_BMP581,
			CHANNEL_DATA_TYPE.UINT24, 3, CHANNEL_DATA_ENDIAN.MSB,
			CHANNEL_UNITS.KPASCAL,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL));

	public static final ChannelDetails channelBmp581Temp = new ChannelDetails(
			ObjectClusterSensorName.TEMPERATURE_BMP581,
			ObjectClusterSensorName.TEMPERATURE_BMP581,
			DatabaseChannelHandles.TEMPERATURE_BMP581,
			CHANNEL_DATA_TYPE.UINT24, 3, CHANNEL_DATA_ENDIAN.MSB,
			CHANNEL_UNITS.DEGREES_CELSIUS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL));

	public static final Map<String, ChannelDetails> mChannelMapRef;
	static {
		Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.PRESSURE_BMP581, channelBmp581Press);
		aMap.put(ObjectClusterSensorName.TEMPERATURE_BMP581, channelBmp581Temp);
		mChannelMapRef = Collections.unmodifiableMap(aMap);
	}
	//--------- Channel info end --------------

	@Override
	public void setPressureResolution(int i) {
		if (ArrayUtils.contains(ListofPressureResolutionConfigValuesBMP581, i)) {
			mPressureResolution = i;
		}
		updateCurrentPressureCalibInUse();
	}

	@Override
	public List<Double> getPressTempConfigValuesLegacy() {
		// BMP581 has no calibration coefficients to expose.
		return new ArrayList<Double>();
	}

	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(mSensorMapRef, mChannelMapRef);
	}

	@Override
	public void generateConfigOptionsMap() {
		addConfigOption(configOptionPressureResolutionBMP581);
		addConfigOption(configOptionPressureRateBMP581);
	}

	@Override
	public void generateSensorGroupMapping() {
		mSensorGroupingMap = new LinkedHashMap<Integer, SensorGroupingDetails>();
		if (mShimmerVerObject.isShimmerGen3R()) {
			mSensorGroupingMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PRESSURE_TEMPERATURE_BMP581.ordinal(), sensorGroupBmp581);
		}
		super.updateSensorGroupingMap();
	}

	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType,
			ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pctimeStampMs) {
		objectCluster = sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pctimeStampMs);

		for (ChannelDetails channelDetails : sensorDetails.mListOfChannels) {
			if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.PRESSURE_BMP581)) {
				double raw = ((FormatCluster) ObjectCluster.returnFormatCluster(objectCluster.getCollectionOfFormatClusters(ObjectClusterSensorName.PRESSURE_BMP581), channelDetails.mChannelFormatDerivedFromShimmerDataPacket.toString())).mData;
				// BMP581 pressure is pre-compensated: raw/64 = Pa, /1000 -> kPa
				double calPressure = raw / 64000.0;
				objectCluster.addCalData(channelDetails, calPressure, objectCluster.getIndexKeeper() - 2);
				objectCluster.incrementIndexKeeper();
			}
			if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.TEMPERATURE_BMP581)) {
				double raw = ((FormatCluster) ObjectCluster.returnFormatCluster(objectCluster.getCollectionOfFormatClusters(ObjectClusterSensorName.TEMPERATURE_BMP581), channelDetails.mChannelFormatDerivedFromShimmerDataPacket.toString())).mData;
				// BMP581 temperature is pre-compensated: raw/65536 = deg C
				double calTemp = raw / 65536.0;
				objectCluster.addCalData(channelDetails, calTemp, objectCluster.getIndexKeeper() - 1);
				objectCluster.incrementIndexKeeper();
			}
		}

		super.consolePrintChannelsCal(objectCluster, Arrays.asList(
				new String[]{ObjectClusterSensorName.PRESSURE_BMP581, CHANNEL_TYPE.UNCAL.toString()},
				new String[]{ObjectClusterSensorName.TEMPERATURE_BMP581, CHANNEL_TYPE.UNCAL.toString()},
				new String[]{ObjectClusterSensorName.PRESSURE_BMP581, CHANNEL_TYPE.CAL.toString()},
				new String[]{ObjectClusterSensorName.TEMPERATURE_BMP581, CHANNEL_TYPE.CAL.toString()}));

		return objectCluster;
	}

	@Override
	public void generateCalibMap() {
		mCalibDetailsBmpX80 = new CalibDetailsBmp581(mShimmerDevice.mMacIdFromUart);
		super.generateCalibMap();
	}

	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		if (!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_BMP581_PRESSURE)) {
			setDefaultBmp581PressureSensorConfig(false);
		}
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		Object returnValue = null;
		switch (configLabel) {
			case (GuiLabelConfig.PRESSURE_RESOLUTION):
				setPressureResolution((int) valueToSet);
				returnValue = valueToSet;
				break;
		}
		return returnValue;
	}

	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		Object returnValue = null;
		switch (configLabel) {
			case (GuiLabelConfig.PRESSURE_RESOLUTION):
				returnValue = getPressureResolution();
				break;
		}
		return returnValue;
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		if (mSensorMap.containsKey(sensorId)) {
			if (sensorId == Configuration.Shimmer3.SENSOR_ID.SHIMMER_BMP581_PRESSURE) {
				setDefaultBmp581PressureSensorConfig(isSensorEnabled);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		return mConfigOptionsMap.containsKey(stringKey);
	}

	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		ActionSetting actionSetting = new ActionSetting(commType);
		switch (componentName) {
			case (GuiLabelConfig.PRESSURE_RESOLUTION):
				setPressureResolution((int) valueToSet);
				break;
		}
		return actionSetting;
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		LinkedHashMap<String, Object> mapOfConfig = new LinkedHashMap<String, Object>();
		mapOfConfig.put(DatabaseConfigHandle.PRESSURE_PRECISION_BMP581, getPressureResolution());
		// No PAR_* rows - BMP581 has no calibration coefficients.
		return mapOfConfig;
	}

	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		if (mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.PRESSURE_PRECISION_BMP581)) {
			setPressureResolution(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.PRESSURE_PRECISION_BMP581)).intValue());
		}
	}

	//--------- Sensor specific methods start --------------
	private void setDefaultBmp581PressureSensorConfig(boolean isSensorEnabled) {
		if (isSensorEnabled) {
		} else {
			mPressureResolution = 0;
		}
	}

	public static String parseFromDBColumnToGUIChannel(String databaseChannelHandle) {
		return AbstractSensor.parseFromDBColumnToGUIChannel(mChannelMapRef, databaseChannelHandle);
	}

	public static String parseFromGUIChannelsToDBColumn(String objectClusterName) {
		return AbstractSensor.parseFromGUIChannelsToDBColumn(mChannelMapRef, objectClusterName);
	}
	//--------- Sensor specific methods end --------------

	// Config-byte pack/parse is identical to BMP390: the resolution field is already
	// 3 bits wide (LSB 2 bits + MSB 1 bit), so it holds the BMP581 range 0..7 unchanged.
	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		ConfigByteLayout configByteLayout = shimmerDevice.getConfigByteLayout();
		if (configByteLayout instanceof ConfigByteLayoutShimmer3) {
			ConfigByteLayoutShimmer3 configByteLayoutCast = (ConfigByteLayoutShimmer3) configByteLayout;

			int lsbPressureResolution = (configBytes[configByteLayoutCast.idxConfigSetupByte3]
					>> configByteLayoutCast.bitShiftBMPX80PressureResolution)
					& configByteLayoutCast.maskBMPX80PressureResolution;

			int msbPressureResolution = (configBytes[configByteLayoutCast.idxConfigSetupByte4]
					>> configByteLayoutCast.bitShiftBMP390PressureResolution)
					& configByteLayoutCast.maskBMP390PressureResolution;

			setPressureResolution(((msbPressureResolution << 2) | lsbPressureResolution));
		}
	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		ConfigByteLayout configByteLayout = shimmerDevice.getConfigByteLayout();
		if (configByteLayout instanceof ConfigByteLayoutShimmer3) {
			ConfigByteLayoutShimmer3 configByteLayoutCast = (ConfigByteLayoutShimmer3) configByteLayout;
			configBytes[configByteLayoutCast.idxConfigSetupByte4] |= (byte) (((getPressureResolution() >> 2) & configByteLayoutCast.maskBMP390PressureResolution) << configByteLayoutCast.bitShiftBMP390PressureResolution);
			configBytes[configByteLayoutCast.idxConfigSetupByte3] |= (byte) ((getPressureResolution() & configByteLayoutCast.maskBMPX80PressureResolution) << configByteLayoutCast.bitShiftBMPX80PressureResolution);
		}
	}

}
