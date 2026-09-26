package com.shimmerresearch.sensors;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.shimmerresearch.bluetooth.BtCommandDetails;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_SOURCE;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.sensors.SensorADC.MICROCONTROLLER_ADC_PROPERTIES;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ShimmerDevice;

public class SensorGSR extends AbstractSensor {

	private static final long serialVersionUID = 1773291747371088953L;

	//--------- Sensor specific variables start --------------
	/** Shimmer3 Values have been reverted to 2r values */
	public static boolean isShimmer3and4UsingShimmer2rVal = true;
	
	public int mGSRRange = 4; 					// 4 = Auto
	
	private MICROCONTROLLER_ADC_PROPERTIES microcontrollerAdcProperties = null;
	
	private double[] currentGsrRefResistorsKohms = SHIMMER3_GSR_REF_RESISTORS_KOHMS;
	private double[][] currentGsrResistanceKohmsMinMax = SHIMMER3_GSR_RESISTANCE_MIN_MAX_KOHMS;
	private int currentGsrUncalLimitRange3 = GSR_UNCAL_LIMIT_RANGE3;
	
	public static final double[] SHIMMER3_GSR_REF_RESISTORS_KOHMS = new double[] {
			40.2, 		//Range 0
			287.0, 		//Range 1
			1000.0, 	//Range 2
			3300.0}; 	//Range 3
	public static final double[][] SHIMMER3_GSR_RESISTANCE_MIN_MAX_KOHMS = new double[][] {
			{8.0, 63.0}, 		//Range 0
			{63.0, 220.0}, 		//Range 1
			{220.0, 680.0}, 	//Range 2
			{680.0, 4700.0}}; 	//Range 3
	/**
	 * The first code above the 0.5 V amplifier reference at the Shimmer3's 3.0 V full scale (0.5 V is
	 * code 682.5). A code below it, on any range, is an open circuit and decodes as range 3 at this
	 * limit: see {@link #calibrateGsrDataToKOhmsWithOpenCircuitLimit}. Also used by the ShimmerGQ and
	 * the Verisense GSR+ (SR62).
	 */
	public static final int GSR_UNCAL_LIMIT_RANGE3 = 683;
	
	public class GuiLabelConfig{
		public static final String GSR_RANGE = "GSR Range";
		public static final String SAMPLING_RATE_DIVIDER_GSR = "GSR Divider";
	}
	
	public class GuiLabelSensors{
		public static final String GSR = "GSR";
	}
	
	public class LABEL_SENSOR_TILE{
		public static final String GSR = "GSR+";
	}
	
	public static class DatabaseChannelHandles{
		public static final String GSR_RESISTANCE = "F5437a_Int_A1_GSR";
		public static final String GSR_CONDUCTANCE = "GSR_Conductance";
		
		// A legacy issue means this conductances based channel needs to be same
		// DB column name as usually used by the Resistance channel just for GQ
		public static final String GSR_GQ = DatabaseChannelHandles.GSR_RESISTANCE;
	}
	
	public static final class DatabaseConfigHandle{
		public static final String GSR_RANGE = "F5437a_Int_A1_GSR_Range";
	}
	
	public static class ObjectClusterSensorName{
		public static String GSR_RESISTANCE = "GSR_Skin_Resistance";
		public static String GSR_CONDUCTANCE = "GSR_Skin_Conductance";
		public static String GSR_RANGE = "GSR_Range";
		public static String GSR_ADC_VALUE = "GSR_ADC_Value";
		
//		public static String GSR_GQ = SensorGSR.ObjectClusterSensorName.GSR_RESISTANCE;//"GSR";
		public static String GSR_GQ = "GSR";
	}	
	//--------- Sensor specific variables end --------------
	
	//--------- Bluetooth commands start --------------
	public static final byte SET_GSR_RANGE_COMMAND			   		= (byte) 0x21;
	public static final byte GSR_RANGE_RESPONSE			   			= (byte) 0x22;
	public static final byte GET_GSR_RANGE_COMMAND			   		= (byte) 0x23;
	
	  public static final Map<Byte, BtCommandDetails> mBtGetCommandMap;
	    static {
	        Map<Byte, BtCommandDetails> aMap = new LinkedHashMap<Byte, BtCommandDetails>();
	        aMap.put(GET_GSR_RANGE_COMMAND, new BtCommandDetails(GET_GSR_RANGE_COMMAND, "GET_GSR_RANGE_COMMAND", GSR_RANGE_RESPONSE));
	        mBtGetCommandMap = Collections.unmodifiableMap(aMap);
	    }
	    
	    public static final Map<Byte, BtCommandDetails> mBtSetCommandMap;
	    static {
	        Map<Byte, BtCommandDetails> aMap = new LinkedHashMap<Byte, BtCommandDetails>();
	        aMap.put(SET_GSR_RANGE_COMMAND, new BtCommandDetails(SET_GSR_RANGE_COMMAND, "SET_GSR_RANGE_COMMAND"));
	        mBtSetCommandMap = Collections.unmodifiableMap(aMap);
	    }
	//--------- Bluetooth commands end --------------

	//--------- Configuration options start --------------
	public static final String[] ListofGSRRangeResistance = {
		"8k" + UtilShimmer.UNICODE_OHMS + " to 63k" + UtilShimmer.UNICODE_OHMS,
		"63k" + UtilShimmer.UNICODE_OHMS + " to 220k" + UtilShimmer.UNICODE_OHMS,
		"220k" + UtilShimmer.UNICODE_OHMS + " to 680k" + UtilShimmer.UNICODE_OHMS,
		"680k" + UtilShimmer.UNICODE_OHMS + " to 4.7M" + UtilShimmer.UNICODE_OHMS,
		"Auto Range"};
	public static final String[] ListofGSRRangeConductance = {
		"125" + UtilShimmer.UNICODE_MICRO + "S to 15.9" + UtilShimmer.UNICODE_MICRO + "S",
		"15.9" + UtilShimmer.UNICODE_MICRO + "S to 4.5" + UtilShimmer.UNICODE_MICRO + "S",
		"4.5" + UtilShimmer.UNICODE_MICRO + "S to 1.5" + UtilShimmer.UNICODE_MICRO + "S",
		"1.5" + UtilShimmer.UNICODE_MICRO + "S to 0.2" + UtilShimmer.UNICODE_MICRO + "S",
		"Auto Range"};
	public static final Integer[] ListofGSRRangeConfigValues = {0,1,2,3,4};

	public static final ConfigOptionDetailsSensor configOptionGsrRange = new ConfigOptionDetailsSensor(
			SensorGSR.GuiLabelConfig.GSR_RANGE,
			SensorGSR.DatabaseConfigHandle.GSR_RANGE,
			ListofGSRRangeResistance, 
			ListofGSRRangeConfigValues, 
			ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr);
	//--------- Configuration options end --------------

	//--------- Sensor info start --------------
	public static final SensorDetailsRef sensorGsrRef = new SensorDetailsRef(
			0x04<<(0*8), 
			0x04<<(0*8), 
			GuiLabelSensors.GSR,
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr,
			Arrays.asList(
					Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1,
					Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14,
					Configuration.Shimmer3.SENSOR_ID.HOST_ECG,
					Configuration.Shimmer3.SENSOR_ID.HOST_EMG,
					Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST,
					Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM,
					Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION,
					Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR,
//					Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT,
//					Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT,
//					Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT,
//					Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT,
					Configuration.Shimmer3.SENSOR_ID.SHIMMER_RESISTANCE_AMP,
					Configuration.Shimmer3.SENSOR_ID.SHIMMER_BRIDGE_AMP),
			Arrays.asList(GuiLabelConfig.GSR_RANGE),
			Arrays.asList(
					//Comment in/out channel you want to appear as normal Shimmer channels
					ObjectClusterSensorName.GSR_RESISTANCE,
					ObjectClusterSensorName.GSR_CONDUCTANCE,
					ObjectClusterSensorName.GSR_RANGE,
					ObjectClusterSensorName.GSR_GQ
					//ObjectClusterSensorName.GSR_ADC_VALUE
					),
			true);
	
    public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
    static {
        Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
		aMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR, SensorGSR.sensorGsrRef);
		mSensorMapRef = Collections.unmodifiableMap(aMap);
    }
	//--------- Sensor info end --------------
    
	//--------- Channel info start --------------
    
    //TODO only use one channel details for GSR and have a list of supported units inside it. Also have a variable stating which one(s) are currently selected
	public static final ChannelDetails channelGsrKOhms = new ChannelDetails(
			ObjectClusterSensorName.GSR_RESISTANCE,
			ObjectClusterSensorName.GSR_RESISTANCE,
			DatabaseChannelHandles.GSR_RESISTANCE,
			CHANNEL_DATA_TYPE.UINT16, 2, CHANNEL_DATA_ENDIAN.LSB,
			CHANNEL_UNITS.KOHMS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL),
			0x1C);

	public static final ChannelDetails channelGsrMicroSiemensGq = new ChannelDetails(
			ObjectClusterSensorName.GSR_GQ,
			ObjectClusterSensorName.GSR_GQ,
			DatabaseChannelHandles.GSR_GQ,
			CHANNEL_DATA_TYPE.UINT16, 2, CHANNEL_DATA_ENDIAN.LSB,
			CHANNEL_UNITS.U_SIEMENS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL),
			0x1C);

	public static final ChannelDetails channelGsrMicroSiemens = new ChannelDetails(
			ObjectClusterSensorName.GSR_CONDUCTANCE,
			ObjectClusterSensorName.GSR_CONDUCTANCE,
			DatabaseChannelHandles.GSR_CONDUCTANCE,
			CHANNEL_UNITS.U_SIEMENS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL));
	{
		//TODO add to constructor
		channelGsrMicroSiemens.mChannelSource = CHANNEL_SOURCE.API;
	}

	public static final ChannelDetails channelGsrRange = new ChannelDetails(
			ObjectClusterSensorName.GSR_RANGE,
			ObjectClusterSensorName.GSR_RANGE,
			CHANNEL_UNITS.NO_UNITS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL));
	{
		//TODO add to constructor
		channelGsrMicroSiemens.mChannelSource = CHANNEL_SOURCE.API;
	}
	
	public static final ChannelDetails channelGsrAdc = new ChannelDetails(
			ObjectClusterSensorName.GSR_ADC_VALUE,
			ObjectClusterSensorName.GSR_ADC_VALUE,
			ObjectClusterSensorName.GSR_ADC_VALUE,
			CHANNEL_UNITS.MILLIVOLTS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL));
	{
		//TODO add to constructor
		channelGsrMicroSiemens.mChannelSource = CHANNEL_SOURCE.API;
	}

	/** used for Shimmer3 in Shimmer*/
    public static final Map<String, ChannelDetails> mChannelMapRef;
    static {
        Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.GSR_RESISTANCE, SensorGSR.channelGsrKOhms);
		aMap.put(ObjectClusterSensorName.GSR_CONDUCTANCE, SensorGSR.channelGsrMicroSiemens);
		aMap.put(ObjectClusterSensorName.GSR_RANGE, SensorGSR.channelGsrRange);
		aMap.put(ObjectClusterSensorName.GSR_ADC_VALUE, SensorGSR.channelGsrAdc);
		mChannelMapRef = Collections.unmodifiableMap(aMap);
    }

    public static final Map<String, ChannelDetails> mChannelMapRefGq;
    static {
        Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
		aMap.put(ObjectClusterSensorName.GSR_RESISTANCE, SensorGSR.channelGsrMicroSiemensGq);
		mChannelMapRefGq = Collections.unmodifiableMap(aMap);
    }

	//--------- Channel info end --------------

    
	//--------- Constructors for this class start --------------
	/** Constructor for this Sensor
	 * @param svo
	 */
	public SensorGSR(ShimmerVerObject svo) {
		super(SENSORS.GSR, svo);
		initialise();
		
		microcontrollerAdcProperties = MICROCONTROLLER_ADC_PROPERTIES.getMicrocontrollerAdcPropertiesForShimmerVersionObject(svo);
	}
	//--------- Constructors for this class end --------------

	
	//--------- Abstract methods implemented start --------------
	@Override
	public void generateSensorMap() {
		//Allow NeuroLynQ to just use a single GSR channel based on MicroSiemens
		if(mShimmerVerObject.isShimmerGenGq()){
			Map<String, ChannelDetails> channelMapRef = new LinkedHashMap<String, ChannelDetails>();
			channelMapRef.put(SensorGSR.channelGsrMicroSiemensGq.mObjectClusterName, SensorGSR.channelGsrMicroSiemensGq);
			super.createLocalSensorMapWithCustomParser(mSensorMapRef, channelMapRef);
		}
		else{
			super.createLocalSensorMapWithCustomParser(mSensorMapRef, mChannelMapRef);
		}
	}

	
	@Override
	public void generateConfigOptionsMap() {
		addConfigOption(configOptionGsrRange);
	}

	
	@Override
	public void generateSensorGroupMapping() {
		
		int groupIndex = Configuration.Shimmer3.LABEL_SENSOR_TILE.GSR.ordinal();
		
		if(mShimmerVerObject.isShimmerGenGq()){
			mSensorGroupingMap.put(groupIndex, new SensorGroupingDetails(
					LABEL_SENSOR_TILE.GSR,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr));
		}
		else if((mShimmerVerObject.isShimmerGen3() || mShimmerVerObject.isShimmerGen4())){
			mSensorGroupingMap.put(groupIndex, new SensorGroupingDetails(
					LABEL_SENSOR_TILE.GSR,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR,
							//TODO PPG not working here because it's not contained within this sensor class
								Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr));
		}
		super.updateSensorGroupingMap();	
	}


	@Override
	//TODO needs to be updated to match ShimmerObject
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] sensorByteArray, COMMUNICATION_TYPE commType, ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		int index = 0;
		for (ChannelDetails channelDetails:sensorDetails.mListOfChannels){
			//first process the data originating from the Shimmer sensor
//			byte[] channelByteArray = new byte[channelDetails.mDefaultNumBytes];
//			System.arraycopy(sensorByteArray, index, channelByteArray, 0, channelDetails.mDefaultNumBytes);
//			objectCluster = SensorDetails.processShimmerChannelData(channelByteArray, channelDetails, objectCluster);
			
			//next process other data
			if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.GSR_RESISTANCE)
					|| channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.GSR_GQ)){

				byte[] channelByteArray = new byte[channelDetails.mDefaultNumBytes];
				System.arraycopy(sensorByteArray, index, channelByteArray, 0, channelDetails.mDefaultNumBytes);
				double rawData = UtilParseData.parseData(channelByteArray, channelDetails.mDefaultChannelDataType, channelDetails.mDefaultChannelDataEndian);
				int gsrAdcValueUnCal = ((int)rawData & 4095); 
				objectCluster.addUncalData(channelDetails, gsrAdcValueUnCal);
				
				int currentGSRRange = getGSRRange();
				if (currentGSRRange==4){
					//Mask upper 2 bits of the 16-bit packet and then bit shift down
					currentGSRRange=(49152 & (int)rawData)>>14; 
				}
				
				if(sensorDetails.mListOfChannels.contains(channelGsrRange)){
//					double rangeToSave = newGSRRange>=0? newGSRRange:mGSRRange;
					objectCluster.addUncalData(channelGsrRange, currentGSRRange);
					objectCluster.addCalData(channelGsrRange, currentGSRRange);
					objectCluster.incrementIndexKeeper();
				}
				if(sensorDetails.mListOfChannels.contains(channelGsrAdc)){
					objectCluster.addUncalData(channelGsrAdc, gsrAdcValueUnCal);
					objectCluster.addCalData(channelGsrAdc, SensorADC.calibrateAdcChannelToMillivolts(gsrAdcValueUnCal, microcontrollerAdcProperties));
					objectCluster.incrementIndexKeeper();
				}
				
				double gsrResistanceKOhms = 0.0;
				double gsrConductanceUSiemens = 0.0;
				//TODO can remove old code
//				if(SensorGSR.isSupportedImprovedGsrCalibration(mShimmerVerObject)) {
					//Limit the GSR value for open circuit situations. 
					if(currentGSRRange==3 && gsrAdcValueUnCal<currentGsrUncalLimitRange3) {
						gsrAdcValueUnCal = currentGsrUncalLimitRange3;
					}
//					gsrAdcValueUnCal = SensorGSR.nudgeGsrADC(gsrAdcValueUnCal, currentGSRRange);

					//...and on ranges 0-2 too, where only the resistance changes (DEV-1070)
					gsrResistanceKOhms = SensorGSR.calibrateGsrDataToKOhmsWithOpenCircuitLimit(gsrAdcValueUnCal, currentGSRRange, currentGsrUncalLimitRange3, microcontrollerAdcProperties, currentGsrRefResistorsKohms);
					gsrResistanceKOhms = SensorGSR.nudgeGsrResistance(gsrResistanceKOhms, getGSRRange(), currentGsrResistanceKohmsMinMax);
					gsrConductanceUSiemens = SensorGSR.convertkOhmToUSiemens(gsrResistanceKOhms);
//				} else {
//					double[] p1p2 = getGSRCoefficientsFromUsingGSRRange(mShimmerVerObject, currentGSRRange);
//					double p1 = p1p2[0];
//					double p2 = p1p2[1];
//
//					gsrResistanceKOhms = SensorGSR.calibrateGsrDataToResistance(gsrAdcValueUnCal,p1,p2);
//					gsrConductanceUSiemens = SensorGSR.calibrateGsrDataToSiemens(gsrAdcValueUnCal,p1,p2);
//				}
				
				double calData = 0.0;
				//This section is needed for GQ since the primary GSR KOHMS channel is replaced by U_SIEMENS 
				if(channelDetails.mDefaultCalUnits.equals(Configuration.CHANNEL_UNITS.KOHMS)){
					calData = gsrResistanceKOhms;
				}
				else if(channelDetails.mDefaultCalUnits.equals(Configuration.CHANNEL_UNITS.U_SIEMENS)){
					calData = gsrConductanceUSiemens;
				}
				objectCluster.addCalData(channelDetails, calData);
				objectCluster.incrementIndexKeeper();

				
				if(sensorDetails.mListOfChannels.contains(channelGsrMicroSiemens)){
					objectCluster.addUncalData(channelGsrMicroSiemens, gsrAdcValueUnCal);
					objectCluster.addCalData(channelGsrMicroSiemens, gsrConductanceUSiemens);
					objectCluster.incrementIndexKeeper();
				}
				
			}
			index = index + channelDetails.mDefaultNumBytes;
			
			
			// All GSR channels are calculated after the
			// ObjectClusterSensorName.GSR channel is process (shown above) -
			// therefore no need to cycle through the rest of the channels.
			continue;
		}
		
		//Debugging
//		super.consolePrintChannelsCal(objectCluster, Arrays.asList(
//				new String[]{ObjectClusterSensorName.GSR_CONDUCTANCE, CHANNEL_TYPE.UNCAL.toString()},
//				new String[]{ObjectClusterSensorName.GSR, CHANNEL_TYPE.UNCAL.toString()})); 
		
		return objectCluster;
	}
	

	public static double convertkOhmToUSiemens(double gsrResistanceKOhms) {
//		gsrConductanceUSiemens = (1.0/gsrResistanceKOhms)*1000;
		return 1000.0/gsrResistanceKOhms;
	}
	
	public static double convertUSiemensTokOhm(double gsrUSiemens) {
		return 1000.0/gsrUSiemens;
	}

	public static double nudgeGsrResistance(double gsrResistanceKOhms, int gsrRangeSetting, double[][] gsrResistanceKohmsMinMax) {
		if (gsrRangeSetting == 4) {
			/* If auto-range is enabled, limit the lower range of the resistance due to circuit design */
			return Math.max(gsrResistanceKohmsMinMax[0][0], gsrResistanceKOhms);
		} else {
			double[] minMax = gsrResistanceKohmsMinMax[gsrRangeSetting];
			return UtilShimmer.nudgeDouble(gsrResistanceKOhms, minMax[0], minMax[1]);
		}
	}

//	public static int nudgeGsrADC(int gsrAdcValueUnCal, int gsrRangeSetting) {
//		int[] minMax = SHIMMER3_GSR_RESISTANCE_MIN_MAX_ADC[gsrRangeSetting];
//		return UtilShimmer.nudgeInteger(gsrAdcValueUnCal, minMax[1], minMax[0]);
//	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] mInfoMemBytes, COMMUNICATION_TYPE commType) {
		int idxConfigSetupByte3 =	9;
		int bitShiftGSRRange =		1;
		int maskGSRRange =			0x07;
		
		mInfoMemBytes[idxConfigSetupByte3] |= (byte) ((mGSRRange & maskGSRRange) << bitShiftGSRRange);
	}

	
	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] mInfoMemBytes, COMMUNICATION_TYPE commType) {
		int idxConfigSetupByte3 =	9;
		int bitShiftGSRRange =		1;
		int maskGSRRange =			0x07;
		
		mGSRRange = (mInfoMemBytes[idxConfigSetupByte3] >> bitShiftGSRRange) & maskGSRRange;
	}
	
	
	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		Object returnValue = null;

		switch(configLabel){
			case(GuiLabelConfig.GSR_RANGE):
	    		setGSRRange((int)valueToSet);
				returnValue = valueToSet;
	        	break;
	        default:
	        	break;
		}

		
		// TODO Auto-generated method stub
		return returnValue;
	}

	
	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		Object returnValue = null;
		switch(configLabel){
			case(GuiLabelConfig.GSR_RANGE):
				returnValue = getGSRRange(); //TODO: check with RM re firmware bug?? -> //RS (25/05/2016): Still relevant?
		    	break;
	        default:
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
		if(mSensorMap.containsKey(sensorId)){
			setDefaultGsrSensorConfig(isSensorEnabled);
			return true;
		}
		return false;
	}
	

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		if(mConfigOptionsMap.containsKey(stringKey)){
			return true;
		}
		return false;
	}


	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		
		// TODO Auto-generated method stub
		ActionSetting actionSetting = new ActionSetting(commType);
		switch(componentName){
			case(GuiLabelConfig.GSR_RANGE):
				if (commType == COMMUNICATION_TYPE.BLUETOOTH){
					
				} else if (commType == COMMUNICATION_TYPE.DOCK){
					
				} else if (commType == COMMUNICATION_TYPE.CLASS){
					//this generates the infomem
					
				}
			break;
		}
		return actionSetting;

	}
	
	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		LinkedHashMap<String, Object> mapOfConfig = new LinkedHashMap<String, Object>();
		
		mapOfConfig.put(SensorGSR.DatabaseConfigHandle.GSR_RANGE, (double) getGSRRange());
		
		return mapOfConfig;
	}

	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.GSR_RANGE)){
			setGSRRange(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.GSR_RANGE)).intValue());
		}
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		return false;
	}	
	
	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		// If Shimmer name is default, update with MAC ID if available.
//		if(mShimmerUserAssignedName.equals(DEFAULT_SHIMMER_NAME)){
//			setDefaultShimmerName();
//		}
		if(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR)) {
			setDefaultGsrSensorConfig(false);
		}
	}
	//--------- Abstract methods implemented end --------------


	//--------- Sensor specific methods start --------------
	@Deprecated
	public static double calibrateGsrDataToResistance(double gsrUncalibratedData, double p1, double p2){
//		gsrUncalibratedData = (double)((int)gsrUncalibratedData & 4095); 
//		//the following polynomial is deprecated and has been replaced with a more accurate linear one, see GSR user guide for further details
//		//double gsrCalibratedData = (p1*Math.pow(gsrUncalibratedData,4)+p2*Math.pow(gsrUncalibratedData,3)+p3*Math.pow(gsrUncalibratedData,2)+p4*gsrUncalibratedData+p5)/1000;
//		//the following is the new linear method see user GSR user guide for further details
//		double gsrCalibratedData = (1/((p1*gsrUncalibratedData)+p2)*1000); //kohms 
		
		double gsrCalibratedDatauS = calibrateGsrDataToSiemens(gsrUncalibratedData, p1, p2);
		double gsrCalibratedData = (1/(gsrCalibratedDatauS)*1000); //kohms 

		return gsrCalibratedData;  
	}

	@Deprecated
	public static double calibrateGsrDataToSiemens(double gsrUncalibratedData, double p1, double p2){
		double gsrUncalibratedDataLcl = (double)((int)gsrUncalibratedData & 4095); 
		//the following polynomial is deprecated and has been replaced with a more accurate linear one, see GSR user guide for further details
		//double gsrCalibratedData = (p1*Math.pow(gsrUncalibratedData,4)+p2*Math.pow(gsrUncalibratedData,3)+p3*Math.pow(gsrUncalibratedData,2)+p4*gsrUncalibratedData+p5)/1000;
		//the following is the new linear method see user GSR user guide for further details
		double gsrCalibratedData = (((p1*gsrUncalibratedDataLcl)+p2)); //microsiemens 
		return gsrCalibratedData;  
	}

	/** Based on circuit theory of the GSR non-inverting amplifier.  
	 * 
	 * @param gsrUncalibratedData
	 * @param range
	 * @param microcontrollerAdcProperties 
	 * @return
	 */
	public static double calibrateGsrDataToKOhmsUsingAmplifierEq(double gsrUncalibratedData, int range, MICROCONTROLLER_ADC_PROPERTIES microcontrollerAdcProperties, double[] gsrRefResistorsKohms){
		double rFeedback = gsrRefResistorsKohms[range];
		double volts = SensorADC.calibrateAdcChannelToVolts(gsrUncalibratedData, microcontrollerAdcProperties);
		double rSource = rFeedback/((volts/0.5)-1.0);
		return rSource;
	}

	/**
	 * {@link #calibrateGsrDataToKOhmsUsingAmplifierEq}, reading an open circuit as open on every
	 * range (DEV-1070).
	 * <p>
	 * The amplifier equation has no positive solution at or below the amplifier's reference: no skin
	 * resistance can pull the output under it, so a code there means the electrodes are open. Range 3
	 * has long raised such a code to its open-circuit limit, the first code above the reference, so
	 * that an open circuit decodes as thousands of MOhm. Ranges 0-2 did not, and in auto-range they
	 * see these codes too. When the electrodes come off, the device climbs one range at a time, and
	 * the firmware repeats the sample that triggered each switch through the 80 ms settling time,
	 * tagged with the range it was measured on. On ranges 0-2 the equation gave those samples a
	 * negative resistance, which {@link #nudgeGsrResistance} floors at 8 kOhm: the highest
	 * conductance the device can report, for an open circuit.
	 * <p>
	 * So a code below the limit is decoded as range 3 at the limit, whatever range it was measured
	 * on, and an open circuit reads the same on every range as the settled range 3 does. The 8 kOhm
	 * floor can then no longer be reached, because range 0 at full scale already decodes to just
	 * above it (8.04 kOhm). Codes at or above the limit decode on their own range, as before. The
	 * test compares codes rather than volts, so it does not depend on which reference the equation
	 * divides by.
	 *
	 * @param gsrUncalLimitRange3 the front end's range-3 open-circuit limit: {@link #GSR_UNCAL_LIMIT_RANGE3},
	 *        or {@code SensorGSRVerisense.VERISENSE_PULSE_PLUS_GSR_UNCAL_LIMIT_RANGE3} on gen-2
	 */
	public static double calibrateGsrDataToKOhmsWithOpenCircuitLimit(double gsrUncalibratedData, int range, int gsrUncalLimitRange3, MICROCONTROLLER_ADC_PROPERTIES microcontrollerAdcProperties, double[] gsrRefResistorsKohms){
		if(gsrUncalibratedData<gsrUncalLimitRange3) {
			return calibrateGsrDataToKOhmsUsingAmplifierEq(gsrUncalLimitRange3, 3, microcontrollerAdcProperties, gsrRefResistorsKohms);
		}
		return calibrateGsrDataToKOhmsUsingAmplifierEq(gsrUncalibratedData, range, microcontrollerAdcProperties, gsrRefResistorsKohms);
	}

	/**TODO test method no functioning properly yet
	 * @param gsrkOhms
	 * @return
	 */
	public static int uncalibrateGsrDataTokOhmsUsingAmplifierEq(double gsrkOhms, MICROCONTROLLER_ADC_PROPERTIES microcontrollerAdcProperties, double[] gsrRefResistorsKohms, double[][] gsrResistanceKohmsMinMax){
		int range = 0;
		for(int i=0;i<gsrResistanceKohmsMinMax.length;i++) {
			double[] minMax = gsrResistanceKohmsMinMax[i];
			if(gsrkOhms>minMax[0] && gsrkOhms<minMax[1]) {
				range = i;
//				System.err.println(gsrkOhms + " " + range);
				break;
			}
		}

		double rFeedback = gsrRefResistorsKohms[range];
		double volts = ((rFeedback / gsrkOhms) + 1.0) * 0.5;
		
		int gsrUncalibratedData = SensorADC.uncalibrateAdcChannelFromVolts(volts, microcontrollerAdcProperties);
		//Add range
		gsrUncalibratedData += (range<<14);
		return gsrUncalibratedData;
	}

	private void setDefaultGsrSensorConfig(boolean isSensorEnabled) {
		//RS (30/5/2016): from ShimmerObject
		if(isSensorEnabled) {
		}
		else{
			mGSRRange=4;
		}
	}
	
	
	public void setGSRRange(int valueToSet){
		mGSRRange = valueToSet;
	}
	
	
	public int getGSRRange(){
		return mGSRRange;
	}
	
	/**
	 * @param svo
	 * @param currentGSRRange
	 * @param newGSRRange
	 * @return
	 */
	// TODO for improved efficiency, implement this such that the comparison
	// against the ShimmerVerObject does not have to be done per data sample and
	// instead it should be done once on initialisation. This would need to be
	// done in SensorGSR and for legacy support in ShimmerObject
	@Deprecated
	public static double[] getGSRCoefficientsFromUsingGSRRange(ShimmerVerObject svo, int currentGSRRange) {
		double p1 = 0.0;
		double p2 = 0.0;
		
		if (currentGSRRange==0) { 
			// the polynomial function used for calibration has been deprecated, it is replaced with a linear function
			if (svo.isShimmerGen2() || SensorGSR.isShimmer3and4UsingShimmer2rVal){
				p1 = 0.0373;
				p2 = -24.9915;
			} 
			else { //Values have been reverted to 2r values
				p1 = 0.0363;
				p2 = -24.8617;
			}
		} 
		else if (currentGSRRange==1) {
			if (svo.isShimmerGen2() || SensorGSR.isShimmer3and4UsingShimmer2rVal){
				p1 = 0.0054;
				p2 = -3.5194;
			} 
			else {
				p1 = 0.0051;
				p2 = -3.8357;
			}
		} 
		else if (currentGSRRange==2) {
			if (svo.isShimmerGen2() || SensorGSR.isShimmer3and4UsingShimmer2rVal){
				p1 = 0.0015;
				p2 = -1.0163;
			} 
			else {
				p1 = 0.0015;
				p2 = -1.0067;
			}
		} 
		else if (currentGSRRange==3) {
			if (svo.isShimmerGen2() || SensorGSR.isShimmer3and4UsingShimmer2rVal){
				p1 = 4.5580e-04;
				p2 = -0.3014;
			} 
			else {
				p1 = 4.4513e-04;
				p2 = -0.3193;
			}
		}
		return new double[]{p1, p2};
	}

	public double[] getCurrentGsrRefResistorsKohms() {
		return currentGsrRefResistorsKohms;
	}


	public void setCurrentGsrRefResistorsKohms(double[] currentGsrRefResistorsKohms) {
		this.currentGsrRefResistorsKohms = currentGsrRefResistorsKohms;
	}


	public double[][] getCurrentGsrResistanceKohmsMinMax() {
		return currentGsrResistanceKohmsMinMax;
	}


	public void setCurrentGsrResistanceKohmsMinMax(double[][] currentGsrResistanceKohmsMinMax) {
		this.currentGsrResistanceKohmsMinMax = currentGsrResistanceKohmsMinMax;
	}
	
	public int getCurrentGsrUncalLimitRange3() {
		return currentGsrUncalLimitRange3;
	}


	public void setCurrentGsrUncalLimitRange3(int currentGsrUncalLimitRange3) {
		this.currentGsrUncalLimitRange3 = currentGsrUncalLimitRange3;
	}

//	@Deprecated
//	public static boolean isSupportedImprovedGsrCalibration(ShimmerVerObject svo) {
//		if(svo.compareVersions(HW_ID.SHIMMER_3, FW_ID.SDLOG, 0, 19, 0)
//				|| svo.compareVersions(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 0, 11, 0)
//				|| svo.compareVersions(HW_ID.SHIMMER_GQ_802154_LR, FW_ID.GQ_802154, 0, 4, 1)
//				|| svo.compareVersions(HW_ID.SHIMMER_GQ_802154_NR, FW_ID.GQ_802154, 0, 4, 1)){
//			return true;
//		}
//		return false;
//	}

	//--------- Sensor specific methods end --------------

	//--------- Optional methods to override in Sensor Class start --------
	//--------- Optional methods to override in Sensor Class end --------

}
	
