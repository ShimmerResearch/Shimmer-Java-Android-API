package com.shimmerresearch.verisense.sensors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense;
import com.shimmerresearch.driver.Configuration.Verisense.CompatibilityInfoForMaps;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.sensors.SensorADC;
import com.shimmerresearch.sensors.SensorBattVoltage;
import com.shimmerresearch.sensors.SensorADC.MICROCONTROLLER_ADC_PROPERTIES;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.communication.payloads.OperationalConfigPayload.OP_CONFIG_BYTE_INDEX;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX;

public class SensorBattVoltageVerisense extends SensorBattVoltage {

	private static final long serialVersionUID = -3662637648902741983L;

	//--------- Sensor specific variables start --------------
	private MICROCONTROLLER_ADC_PROPERTIES microcontrollerAdcProperties = null;
	private ADC_SAMPLING_RATES sensorSamplingRate = ADC_SAMPLING_RATES.OFF;
	private ADC_OVERSAMPLING_RATES adcOversamplingRate = ADC_OVERSAMPLING_RATES.DISABLED;

	//--------- Sensor specific variables end --------------

	public static int ADC_BYTE_BUFFER_SIZE = 192;
	
	public static enum ADC_SAMPLING_RATES implements ISensorConfig {
		OFF("Off", 0, Double.NaN, Integer.MIN_VALUE),
		FREQ_32768_0_HZ("32768.0 Hz", 1, 32768.0, 1),
		FREQ_16384_0_HZ("16384.0 Hz", 2, 16384.0, 2),
		FREQ_8192_0_HZ("8192.0 Hz", 3, 8192.0, 4),
		FREQ_6553_6_HZ("6553.6 Hz", 4, 6553.6, 5),
		FREQ_4096_0_HZ("4096.0 Hz", 5, 4096.0, 8),
		FREQ_3276_8_HZ("3276.8 Hz", 6, 3276.8, 10),
		FREQ_2048_0_HZ("2048.0 Hz", 7, 2048.0, 16),
		FREQ_1638_4_HZ("1638.4 Hz", 8, 1638.4, 20),
		FREQ_1310_72_HZ("1310.72 Hz", 9, 1310.72, 25),
		FREQ_1024_0_HZ("1024.0 Hz", 10, 1024.0, 32),
		FREQ_819_2_HZ("819.2 Hz", 11, 819.2, 40),
		FREQ_655_36_HZ("655.36 Hz", 12, 655.36, 50),
		FREQ_512_0_HZ("512.0 Hz", 13, 512.0, 64),
		FREQ_409_6_HZ("409.6 Hz", 14, 409.6, 80),
		FREQ_327_68_HZ("327.68 Hz", 15, 327.68, 100),
		FREQ_256_0_HZ("256.0 Hz", 16, 256.0, 128),
		FREQ_204_8_HZ("204.8 Hz", 17, 204.8, 160),
		FREQ_163_84_HZ("163.84 Hz", 18, 163.84, 200),
		FREQ_128_0_HZ("128.0 Hz", 19, 128.0, 256),
		FREQ_102_4_HZ("102.4 Hz", 20, 102.4, 320),
		FREQ_81_92_HZ("81.92 Hz", 21, 81.92, 400),
		FREQ_64_0_HZ("64.0 Hz", 22, 64.0, 512),
		FREQ_51_2_HZ("51.2 Hz", 23, 51.2, 640),
		FREQ_40_96_HZ("40.96 Hz", 24, 40.96, 800),
		FREQ_32_0_HZ("32.0 Hz", 25, 32.0, 1024),
		FREQ_25_6_HZ("25.6 Hz", 26, 25.6, 1280),
		FREQ_20_48_HZ("20.48 Hz", 27, 20.48, 1600),
		FREQ_16_0_HZ("16.0 Hz", 28, 16.0, 2048),
		FREQ_12_8_HZ("12.8 Hz", 29, 12.8, 2560),
		FREQ_10_24_HZ("10.24 Hz", 30, 10.24, 3200),
		FREQ_8_0_HZ("8.0 Hz", 31, 8.0, 4096),
		FREQ_6_4_HZ("6.4 Hz", 32, 6.4, 5120),
		FREQ_5_12_HZ("5.12 Hz", 33, 5.12, 6400),
		FREQ_4_0_HZ("4.0 Hz", 34, 4.0, 8192),
		FREQ_3_2_HZ("3.2 Hz", 35, 3.2, 10240),
		FREQ_2_56_HZ("2.56 Hz", 36, 2.56, 12800),
		FREQ_2_0_HZ("2.0 Hz", 37, 2.0, 16384),
		FREQ_1_6_HZ("1.6 Hz", 38, 1.6, 20480),
		FREQ_1_28_HZ("1.28 Hz", 39, 1.28, 25600),
		FREQ_1_0_HZ("1.0 Hz", 40, 1.0, 32768),
		FREQ_0_8_HZ("0.8 Hz", 41, 0.8, 40960),
		FREQ_0_64_HZ("0.64 Hz", 42, 0.64, 51200);
		
		private String label;
		public int configValue;
		public double freqHz;
		public int clockTicks;

		static Map<Integer, ADC_SAMPLING_RATES> BY_CONFIG_VALUE = new HashMap<>();
		static {
			for (ADC_SAMPLING_RATES e : values()) {
				BY_CONFIG_VALUE.put(e.configValue, e);
			}
		}

		private ADC_SAMPLING_RATES(String label, int configValue, double freqHz, int clockTicks){
			this.label = label;
			this.configValue = configValue;
			this.freqHz = freqHz;
			this.clockTicks = clockTicks;
		}
		
		public String getLabel() {
			return label;
		}
		
		public static ADC_SAMPLING_RATES getConfigValueForFreq(double freqHz) {
			List<ADC_SAMPLING_RATES> listOfAdcSamplingRates = Arrays.asList(ADC_SAMPLING_RATES.values());
			Collections.reverse(listOfAdcSamplingRates);
			for(ADC_SAMPLING_RATES adcSamplingRate : listOfAdcSamplingRates) {
				if(freqHz<=adcSamplingRate.freqHz) {
					return adcSamplingRate;
				}
			}
			return null;
		}

		public static ADC_SAMPLING_RATES getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, OFF.configValue, FREQ_0_64_HZ.configValue));
		}
	}

	
	public static enum ADC_OVERSAMPLING_RATES implements ISensorConfig {
		DISABLED("Disabled", 0),
		X2("2x", 1),
		X4("4x", 2),
		X8("8x", 3),
		X16("16x", 4),
		X32("32x", 5),
		X64("64x", 6),
		X128("128x", 7),
		X256("256x", 8);
		
		private String label;
		public int configValue;
		public double freqHz;
		public int clockTicks;

		static Map<Integer, ADC_OVERSAMPLING_RATES> BY_CONFIG_VALUE = new HashMap<>();
		static {
			for (ADC_OVERSAMPLING_RATES e : values()) {
				BY_CONFIG_VALUE.put(e.configValue, e);
			}
		}

		private ADC_OVERSAMPLING_RATES(String label, int configValue){
			this.label = label;
			this.configValue = configValue;
		}
		
		public String getLabel() {
			return label;
		}
		
		public static ADC_OVERSAMPLING_RATES getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, DISABLED.configValue, X256.configValue));
		}
	}

	public static class ObjectClusterSensorNameVerisense {
		public static final String USB_CONNECTION_STATE = "USB_Connection_State";
		public static final String CHARGER_STATE = "Charger_State";
	}

	//--------- Sensor info start --------------
	public static final SensorDetailsRef SENSOR_BATTERY_VOLTAGE_VERISENSE = new SensorDetailsRef(
			Verisense.SensorBitmap.VBATT,
			Verisense.SensorBitmap.VBATT,
			GuiLabelSensors.BATTERY,
			null,
			null,
			Arrays.asList(ObjectClusterSensorName.BATTERY,
					ObjectClusterSensorName.BATT_PERCENTAGE,
					ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE));

	public static final Map<Integer, SensorDetailsRef> SENSOR_MAP_REF_VERISENSE;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Verisense.SENSOR_ID.VBATT, SensorBattVoltageVerisense.SENSOR_BATTERY_VOLTAGE_VERISENSE);  
		SENSOR_MAP_REF_VERISENSE = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Channel info start --------------
	public static final ChannelDetails CHANNEL_USB_CONNECTION_STATE = new ChannelDetails(
			ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE,
			ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE,
			ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE,
			CHANNEL_UNITS.NO_UNITS,
			Arrays.asList(CHANNEL_TYPE.CAL),
			true,
			false);

	public static final ChannelDetails CHANNEL_CHARGER_STATE = new ChannelDetails(
			ObjectClusterSensorNameVerisense.CHARGER_STATE,
			ObjectClusterSensorNameVerisense.CHARGER_STATE,
			ObjectClusterSensorNameVerisense.CHARGER_STATE,
			CHANNEL_UNITS.NO_UNITS,
			Arrays.asList(CHANNEL_TYPE.CAL),
			true,
			false);
	// --------- Channel info end --------------
	
	
	//--------- Constructors for this class start --------------
	public SensorBattVoltageVerisense(ShimmerDevice shimmerDevice) {
		super(shimmerDevice);
		
		microcontrollerAdcProperties = MICROCONTROLLER_ADC_PROPERTIES.getMicrocontrollerAdcPropertiesForShimmerVersionObject(shimmerDevice.getShimmerVerObject());
	}
	//--------- Constructors for this class end --------------

	
	//--------- Abstract methods implemented start --------------
	@Override
	public void generateSensorMap() {
		Map<String, ChannelDetails> channelMapRefVerisense = new LinkedHashMap<String, ChannelDetails>();
		channelMapRefVerisense.putAll(mChannelMapRef);
		
		//TODO battery percentage not supported yet in Verisense Zinc-air and NiMH based products
		if(mShimmerVerObject.getHardwareVersion()!=HW_ID.VERISENSE_GSR_PLUS) {
			channelMapRefVerisense.remove(ObjectClusterSensorName.BATT_PERCENTAGE);
		}
		
		if(mShimmerDevice.getHardwareVersion()==HW_ID.VERISENSE_GSR_PLUS) {
			channelMapRefVerisense.put(ObjectClusterSensorNameVerisense.CHARGER_STATE, CHANNEL_CHARGER_STATE);
		} else {
			channelMapRefVerisense.put(ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE, CHANNEL_USB_CONNECTION_STATE);
		}

		
		super.createLocalSensorMapWithCustomParser(SENSOR_MAP_REF_VERISENSE, channelMapRefVerisense);
	}
	
	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType, ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		
		sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);

		for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
			if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.BATTERY)){
				double unCalData = ((FormatCluster)ObjectCluster.returnFormatCluster(objectCluster.getCollectionOfFormatClusters(channelDetails.mObjectClusterName), channelDetails.mChannelFormatDerivedFromShimmerDataPacket.toString())).mData;
				
				// Mask the lower 12-bits so that we're free to use the upper 4 bits for charging status/USB in/out etc.
				int battAdcValue = ((int)unCalData) & 4095;
				
				double calData = SensorADC.calibrateAdcChannelToMillivolts(battAdcValue, microcontrollerAdcProperties);
				
				if(mShimmerDevice.getShimmerVerObject().getHardwareVersion()==HW_ID.VERISENSE_GSR_PLUS) {
					getShimmerBattStatusDetails().setBattAdcValue(battAdcValue);

					// Multiply by 1.988 because the battery voltage is divided by 2 before entering the BMD-340 And the value is not quite equal to 2 due to the components used in the circuit
					calData *= BATTERY_VOLTAGE_DIVIDER_RATIO;
				} else if(VerisenseDevice.isSecondGenerationHardware(
						mShimmerDevice.getShimmerVerObject().getHardwareVersion(), mShimmerDevice.getExpansionBoardRev())) {
					// Second-generation hardware (SR61-5/6, SR68-9/10) battery-sense divider.
					// Matches the firmware's own conversion in hal_asm_battery.c (measureBatteryVoltage).
					// Deliberately gated on the HARDWARE revision (not isPayloadDesignV13orAbove):
					// the divider is a property of the board's battery-sense circuit, present
					// regardless of which firmware happens to be flashed - the firmware applies
					// the same hardware-keyed rule.
					calData *= BATTERY_VOLTAGE_DIVIDER_RATIO_GEN2;
				}
				objectCluster.addCalData(channelDetails, calData, objectCluster.getIndexKeeper()-1);

			} else if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.BATT_PERCENTAGE)){
				double estimatedChargePercentage = (double)getShimmerBattStatusDetails().getEstimatedChargePercentage();
				if(!Double.isNaN(estimatedChargePercentage) && !Double.isInfinite(estimatedChargePercentage)){
					objectCluster.addCalData(channelDetails, estimatedChargePercentage);
					objectCluster.incrementIndexKeeper();
				}

			} else if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE)
					|| channelDetails.mObjectClusterName.equals(ObjectClusterSensorNameVerisense.CHARGER_STATE)){
				double unCalData = ((FormatCluster)ObjectCluster.returnFormatCluster(objectCluster.getCollectionOfFormatClusters(channelBattVolt.mObjectClusterName), channelBattVolt.mChannelFormatDerivedFromShimmerDataPacket.toString())).mData;
	
				if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorNameVerisense.USB_CONNECTION_STATE)){
					// The USB connection state is stored in Bit 15 of the two byte uncalibrated data
					objectCluster.addCalData(channelDetails, (((int)unCalData) >> 15) & 0x01);
					objectCluster.incrementIndexKeeper();
				} else if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorNameVerisense.CHARGER_STATE)){
					// The charging chip state, if supported, is stored in Bit 14 and Bit 13 of the two byte uncalibrated data
					int chargingStatus = (((int)unCalData) >> 13) & 0x03;
					objectCluster.addCalData(channelDetails, chargingStatus);
					objectCluster.incrementIndexKeeper();
	
					getShimmerBattStatusDetails().setChargingStatus(chargingStatus);
	
				}
			}
		}

		return objectCluster;
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		ConfigByteLayoutVerisenseAdc cbl = new ConfigByteLayoutVerisenseAdc(shimmerDevice, commType);
		if(cbl.idxAdcRate>=0) {
			setSensorSamplingRate(ADC_SAMPLING_RATES.getForConfigValue((configBytes[cbl.idxAdcRate] >> 0) & cbl.maskAdcRate));
		}
	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		ConfigByteLayoutVerisenseAdc cbl = new ConfigByteLayoutVerisenseAdc(shimmerDevice, commType);
		if(cbl.idxAdcRate>=0) {
			configBytes[cbl.idxAdcRate] &= ~cbl.maskAdcRate;
			configBytes[cbl.idxAdcRate] |= (byte)getSensorSamplingRate().configValue & cbl.maskAdcRate;
		}
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		Object returnValue = null;

		switch (configLabel) {
		case (GuiLabelConfigCommon.RATE):
			setSensorSamplingRate((double) valueToSet);
			returnValue = valueToSet;
			break;
		default:
			super.setConfigValueUsingConfigLabel(sensorId, configLabel, valueToSet);
			break;
		}
		
		return returnValue;
	}

	
	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		Object returnValue = null;
		switch (configLabel) {
		case (GuiLabelConfigCommon.RATE):
			returnValue = getSensorSamplingRateHz();
			break;
		default:
			returnValue = super.getConfigValueUsingConfigLabel(sensorId, configLabel);
			break;
		}

		return returnValue;
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		setSensorSamplingRate(ADC_SAMPLING_RATES.getConfigValueForFreq(samplingRateHz));
		super.setSensorSamplingRate(sensorSamplingRate.freqHz);
	}
	
	@Override
	public void setSamplingRateFromShimmer(double maxSetRate) {
		if(Double.isFinite(maxSetRate)) {
			setSensorSamplingRate(ADC_SAMPLING_RATES.getConfigValueForFreq(maxSetRate));
			super.setSamplingRateFromShimmer(sensorSamplingRate.freqHz);
		}
	}
	
	public void setSensorSamplingRate(ADC_SAMPLING_RATES adcSamplingRate){
		this.sensorSamplingRate = adcSamplingRate;
	}
	
	public double getSensorSamplingRateHz() {
		return sensorSamplingRate.freqHz;
	}

	public ADC_SAMPLING_RATES getSensorSamplingRate() {
		return sensorSamplingRate;
	}

	public void setAdcOversamplingRate(ADC_OVERSAMPLING_RATES adcOversamplingRate){
		this.adcOversamplingRate = adcOversamplingRate;
	}

	public ADC_OVERSAMPLING_RATES getAdcOversamplingRate() {
		return adcOversamplingRate;
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		if(mSensorMap.containsKey(sensorId)){
			if(isSensorEnabled) {
				setSensorSamplingRate(ADC_SAMPLING_RATES.FREQ_51_2_HZ);
			} else {
				setSensorSamplingRate(ADC_SAMPLING_RATES.OFF);
			}

			return true;
		}
		return false;
	}
	
	@Override
	public void generateSensorGroupMapping() {
		
		int groupIndex = Configuration.Verisense.LABEL_SENSOR_TILE.VBATT.ordinal();
		
		if(mShimmerVerObject.isShimmerGenVerisense()){
			mSensorGroupingMap.put(groupIndex, new SensorGroupingDetails(
					LABEL_SENSOR_TILE.BATTERY_MONITORING,
					Arrays.asList(
							Configuration.Verisense.SENSOR_ID.VBATT),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoVbatt));
		}
		super.updateSensorGroupingMap();
	}

	//--------- Abstract methods implemented end --------------
	
	private class ConfigByteLayoutVerisenseAdc {
		public int idxAdcRate = -1, maskAdcRate = 0x3F;
		
		public ConfigByteLayoutVerisenseAdc(ShimmerDevice shimmerDevice, COMMUNICATION_TYPE commType) {
			if(shimmerDevice instanceof VerisenseDevice) {
				VerisenseDevice verisenseDevice = (VerisenseDevice) shimmerDevice;
				if(verisenseDevice.isPayloadDesignV12orAbove()) {
					if(commType==COMMUNICATION_TYPE.SD) {
						idxAdcRate = PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG13;
					} else {
						idxAdcRate = OP_CONFIG_BYTE_INDEX.ADC_SAMPLE_RATE;
					}
				}
			}
		}
	}

}

