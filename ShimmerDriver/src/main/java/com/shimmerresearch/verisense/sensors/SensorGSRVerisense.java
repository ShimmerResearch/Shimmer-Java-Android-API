package com.shimmerresearch.verisense.sensors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense;
import com.shimmerresearch.driver.Configuration.Verisense.CompatibilityInfoForMaps;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.sensors.SensorGSR;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.communication.payloads.OperationalConfigPayload.OP_CONFIG_BYTE_INDEX;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX;
import com.shimmerresearch.verisense.sensors.SensorBattVoltageVerisense.ADC_OVERSAMPLING_RATES;
import com.shimmerresearch.verisense.sensors.SensorBattVoltageVerisense.ADC_SAMPLING_RATES;

public class SensorGSRVerisense extends SensorGSR {

	private static final long serialVersionUID = -3937042079000714506L;

	//--------- Sensor specific variables start --------------
	private GSR_RANGE gsrRange = GSR_RANGE.AUTO_RANGE;
	private ADC_SAMPLING_RATES sensorSamplingRate = ADC_SAMPLING_RATES.OFF;
	private ADC_OVERSAMPLING_RATES adcOversamplingRate = ADC_OVERSAMPLING_RATES.DISABLED;
	//--------- Sensor specific variables end --------------
	
	public static enum GSR_RANGE implements ISensorConfig {
		RANGE_0("8k" + UtilShimmer.UNICODE_OHMS + " to 63k" + UtilShimmer.UNICODE_OHMS, 0, 8.0, 63.0),
		RANGE_1("63k" + UtilShimmer.UNICODE_OHMS + " to 220k" + UtilShimmer.UNICODE_OHMS, 1, 63.0, 220.0),
		RANGE_2("220k" + UtilShimmer.UNICODE_OHMS + " to 680k" + UtilShimmer.UNICODE_OHMS, 2, 220.0, 680.0),
		RANGE_3("680k" + UtilShimmer.UNICODE_OHMS + " to 4.7M" + UtilShimmer.UNICODE_OHMS, 3, 680.0, 4700.0),
		AUTO_RANGE("Auto-Range", 4, 8.0, 4700.0);
		
		String label;
		Integer configValue;
		double minInputKohms, maxInputKohms;
		
		static Map<String, Integer> REF_MAP = new HashMap<>();
		static {
			for (GSR_RANGE e : values()) {
				REF_MAP.put(e.label, e.configValue);
			}
		}

		static Map<Integer, GSR_RANGE> BY_CONFIG_VALUE = new HashMap<>();
		static {
			for (GSR_RANGE e : values()) {
				BY_CONFIG_VALUE.put(e.configValue, e);
			}
		}

		private GSR_RANGE(String label, int configValue, double minInputKohms, double maxInputKohms) {
			this.label = label;
			this.configValue = configValue;
			this.minInputKohms = minInputKohms;
			this.maxInputKohms = maxInputKohms;
		}
		
		public static String[] getLabels() {
			return REF_MAP.keySet().toArray(new String[REF_MAP.keySet().size()]);
		}
		
		public static Integer[] getConfigValues() {
			return REF_MAP.values().toArray(new Integer[REF_MAP.values().size()]);
		}

		public static GSR_RANGE getForConfigValue(int configValue) {
			return BY_CONFIG_VALUE.get(UtilShimmer.nudgeInteger(configValue, RANGE_0.configValue, AUTO_RANGE.configValue));
		}
	}

	// Values appropriate to the Verisense Pulse+ (SR68). The Verisense GSR+ (SR62) uses Shimmer3 values.
	public static final double[] VERISENSE_PULSE_PLUS_GSR_REF_RESISTORS_KOHMS = new double[] {
			21.0, 		//Range 0
			150.0, 		//Range 1
			562.0, 		//Range 2
			1740.0}; 	//Range 3
	public static final int VERISENSE_PULSE_PLUS_GSR_UNCAL_LIMIT_RANGE3 = 1134;

	//--------- Sensor info start --------------
	public static final SensorDetailsRef SENSOR_GSR_VERISENSE = new SensorDetailsRef(
			(long)Verisense.SensorBitmap.GSR, 
			(long)Verisense.SensorBitmap.GSR, 
			GuiLabelSensors.GSR,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr,
			new ArrayList<Integer>(),
			Arrays.asList(GuiLabelConfig.GSR_RANGE),
			Arrays.asList(
					ObjectClusterSensorName.GSR_RESISTANCE,
					ObjectClusterSensorName.GSR_CONDUCTANCE,
					ObjectClusterSensorName.GSR_RANGE),
			true);
	
	public static final Map<Integer, SensorDetailsRef> SENSOR_MAP_REF_VERISENSE;
	static {
		Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Verisense.SENSOR_ID.GSR, SensorGSRVerisense.SENSOR_GSR_VERISENSE);
		SENSOR_MAP_REF_VERISENSE = Collections.unmodifiableMap(aMap);
	}
	//--------- Sensor info end --------------

	//--------- Constructors for this class start --------------
	public SensorGSRVerisense(ShimmerVerObject svo) {
		super(svo);

		// The Pulse+ (SR68) and the second-generation Verisense IMU (SR61-5/6) share the
		// same DC-based GSR front-end with its own feedback-resistor set; the GSR+ (SR62)
		// uses the Shimmer3 values. An SR61 only gets this sensor class registered when
		// VerisenseDevice.doesHwSupportGsr() has already confirmed HW minor >= 5, so no
		// minor-revision check is needed here. Verified against a 33k/100k/470k resistor
		// sweep on an SR61-5 (DEV-793 datasets B4a-c).
		if (svo.getHardwareVersion() == HW_ID.VERISENSE_PULSE_PLUS
				|| svo.getHardwareVersion() == HW_ID.VERISENSE_IMU) {
			setCurrentGsrRefResistorsKohms(VERISENSE_PULSE_PLUS_GSR_REF_RESISTORS_KOHMS);
			setCurrentGsrUncalLimitRange3(VERISENSE_PULSE_PLUS_GSR_UNCAL_LIMIT_RANGE3);
		}
	}
	//--------- Constructors for this class end --------------
	
	//--------- Abstract methods implemented start --------------
	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(SENSOR_MAP_REF_VERISENSE, mChannelMapRef);
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {
		
		ConfigByteLayoutGsr cbl = new ConfigByteLayoutGsr(shimmerDevice, commType);
		
		if(cbl.idxGsrRange>=0 && cbl.idxAdcRate>=0) {
			setGsrRange(GSR_RANGE.getForConfigValue((configBytes[cbl.idxGsrRange] >> cbl.bitShiftGSRRange) & cbl.maskGSRRange));
			
			setSensorSamplingRate(ADC_SAMPLING_RATES.getForConfigValue((configBytes[cbl.idxAdcRate] >> 0) & cbl.maskAdcRate));
		}
	}
	
	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType) {

		ConfigByteLayoutGsr cbl = new ConfigByteLayoutGsr(shimmerDevice, commType);

		if(cbl.idxGsrRange>=0 && cbl.idxAdcRate>=0) {
			configBytes[cbl.idxGsrRange] &= ~(cbl.maskGSRRange << cbl.bitShiftGSRRange);
			configBytes[cbl.idxGsrRange] |= (byte) ((getGsrRange().configValue & cbl.maskGSRRange) << cbl.bitShiftGSRRange);
	
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
	public void setSensorConfig(ISensorConfig sensorConfig) {
		if(sensorConfig instanceof GSR_RANGE) {
			setGsrRange((GSR_RANGE)sensorConfig);
		} else if(sensorConfig instanceof ADC_SAMPLING_RATES) {
			setSensorSamplingRate((ADC_SAMPLING_RATES)sensorConfig);
		} else if(sensorConfig instanceof ADC_OVERSAMPLING_RATES) {
			setAdcOversamplingRate((ADC_OVERSAMPLING_RATES)sensorConfig);
		} else {
			super.setSensorConfig(sensorConfig);
		}
	}

	@Override
	public List<ISensorConfig> getSensorConfig() {
		List<ISensorConfig> listOfSensorConfig = super.getSensorConfig();
		listOfSensorConfig.add(getGsrRange());
		listOfSensorConfig.add(getAdcOversamplingRate());
		listOfSensorConfig.add(getSensorSamplingRate());
		return listOfSensorConfig;
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
			setGsrRange(GSR_RANGE.AUTO_RANGE);
			setAdcOversamplingRate(ADC_OVERSAMPLING_RATES.X64);

			return true;
		}
		return false;
	}
	
	@Override
	public void generateSensorGroupMapping() {
		
		int groupIndex = Configuration.Verisense.LABEL_SENSOR_TILE.GSR.ordinal();
		
		if(mShimmerVerObject.isShimmerGenVerisense()){
			mSensorGroupingMap.put(groupIndex, new SensorGroupingDetails(
					LABEL_SENSOR_TILE.GSR,
					Arrays.asList(
							Configuration.Verisense.SENSOR_ID.GSR),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr));
		}
		super.updateSensorGroupingMap();
	}

	//--------- Abstract methods implemented end --------------

	public GSR_RANGE getGsrRange() {
		return gsrRange;
	}

	public void setGsrRange(GSR_RANGE gsrRange) {
		this.gsrRange = gsrRange;
		super.setGSRRange(gsrRange.configValue);
	}

	private class ConfigByteLayoutGsr {
		public int idxAdcRate = -1, idxGsrRange = -1, maskAdcRate = 0x3F, maskGSRRange = 0x07, bitShiftGSRRange = 5;
		
		public ConfigByteLayoutGsr(ShimmerDevice shimmerDevice, COMMUNICATION_TYPE commType) {
			if(shimmerDevice instanceof VerisenseDevice) {
				VerisenseDevice verisenseDevice = (VerisenseDevice) shimmerDevice;
				if(verisenseDevice.isPayloadDesignV12orAbove()) {
					if(commType==COMMUNICATION_TYPE.SD) {
						idxAdcRate = PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG13;
						idxGsrRange = PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG5;
					} else {
						idxAdcRate = OP_CONFIG_BYTE_INDEX.ADC_SAMPLE_RATE;
						idxGsrRange = OP_CONFIG_BYTE_INDEX.GSR_RANGE_SETTING;
					}
				}
			}
		}
	}

}
