package com.shimmerresearch.sensors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.calibration.CalibDetails;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic.CALIBRATION_SCALE_FACTOR;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.verisense.sensors.ISensorConfig;

public abstract class AbstractSensor implements Serializable{
	
	/** * */
	private static final long serialVersionUID = 3465427544416038676L;

	public enum SENSORS{
		GSR("GSR"),
		ECG_TO_HR("ECG to Heart Rate"),
		EXG("EXG"),
		CLOCK("Shimmer Clock"),
		SYSTEM_TIMESTAMP("PC time"),
		MPU9X50("MPU Accel"),
		BMP180("BMP180"),
		KIONIXKXRB52042("Analog Accelerometer"),
		LSM303("LSM303"),
		PPG("PPG"), 
		TEMPLATE("Template sensor - not a real sensor of course!"),
		ADC("ADC"),
		Battery("Battery"),
		Bridge_Amplifier("Bridge Amplifier"),
		MMA776X("Shimmer2r Accelerometer"),
		KIONIXKXTC92050("Analog Accelerometer"),
		
		NONIN_ONYX_II("Nonin Onyx II"),
		QTI_DIRECT_TEMP("QTI DirectTemp"),
		KEYBOARD_MOUSE("Keyboard/Mouse Listener"),
		KEYBOARD("KeyboardListener"),
		BMP280("BMP280"),
		STC3100("STC3100"),
		WEBCAM_FRAME_NUMBER("Frame Number"),
		HOST_CPU_USAGE("Cpu Usage"),
		SWEATCH_ADC("Sweatch ADC"),
		SHIMMER2R_MAG("Shimmer2r Mag"),
		SHIMMER2R_GYRO("Shimmer2r Gyro"), 
		LIS2DW12("LIS2DW12"),
		LSM6DS3("LSM6DS3"),
		MAX86150("MAX86150"),
		MAX86916("MAX86916"),
		BIOZ("MAX30001"),
		ADXL371("ADXL371"),
		LIS3MDL("LIS3MDL"), //to be changed
		LIS2MDL("LIS2MDL"),
		LSM6DSV("LSM6DSV"),
		VD6283("VD6283"),
		MLX90632("MLX90632"),
		BMP390("BMP390"),
		BMP581("BMP581");
		
	    private final String text;

	    /** @param text */
	    private SENSORS(final String text) {
	        this.text = text;
	    }

	    /* (non-Javadoc)
	     * @see java.lang.Enum#toString()
	     */
	    @Override
	    public String toString() {
	        return text;
	    }
	}
	
	//General Configurations options that are normally used in conjunction with a sensorId
	public class GuiLabelConfigCommon{
		public static final String RATE = "Rate";
		public static final String RANGE = "Range";
		public static final String CALIBRATION_PER_SENSOR = "Calibration";
		public static final String CALIBRATION_ALL = "Calibration all";
		public static final String CALIBRATION_CURRENT_PER_SENSOR = "Calibration Current";
	}
	
	public static class DatabaseChannelHandlesCommon{
		public static final String NONE = "";
		public static final String TIMESTAMP_SYSTEM = "System_Timestamp";
	}
	
	// --------------- Abstract methods start ----------------
	/** call either createLocalSensorMap() or createLocalSensorMapWithCustomParser() inside depending if a custom parser is needed. */
	public abstract void generateSensorMap();
	public abstract void generateConfigOptionsMap();
	public abstract void generateSensorGroupMapping();

	/** for use only if a custom parser is required, i.e. for calibrated data. Use in conjunction with createLocalSensorMapWithCustomParser()*/ 
	public abstract ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType, ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pctimeStampMs);
//	public abstract ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] sensorByteArray, COMMUNICATION_TYPE commType, ObjectCluster objectCluster);
	
	public abstract void checkShimmerConfigBeforeConfiguring();
	public abstract void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType);
	public abstract void configBytesParse(ShimmerDevice shimmerDevice, byte[] configBytes, COMMUNICATION_TYPE commType);
	public abstract Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet);
	public abstract Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel);

	public abstract void setSensorSamplingRate(double samplingRateHz);
	/** A sensor ID is needed as some sensor classes contain a number of sensors
	 * @param sensorId
	 * @param isSensorEnabled
	 * @return
	 */
	public abstract boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled);
	/** TODO populate in individual AbstractSensor classes the relevant entries from ShimmerObject */
	public abstract boolean checkConfigOptionValues(String stringKey);

	@Deprecated //TODO remove below? old approach?
	public abstract Object getSettings(String componentName, COMMUNICATION_TYPE commType);
	@Deprecated //TODO remove below? old approach?
	public abstract ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType);
	
	public abstract boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType);
	
	public abstract LinkedHashMap<String, Object> generateConfigMap();
	public abstract void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer);

	// --------------- Abstract methods end ----------------	

	public SENSORS mSensorType = null;
	protected String mSensorName = "";
	protected ShimmerVerObject mShimmerVerObject = new ShimmerVerObject();
	
	protected static boolean mEnableCalibration = true;
	protected boolean mIsDebugOutput = false;
	
//	protected Double mMaxSetShimmerSamplingRate = 51.2;
	
	//TODO implement below?
//	protected int mFirmwareSensorIdentifier; // this is how the firmware identifies the sensor
	
	public TreeMap<Integer, SensorDetails> mSensorMap = new TreeMap<Integer, SensorDetails>();
	public HashMap<String,ConfigOptionDetailsSensor> mConfigOptionsMap = new HashMap<String,ConfigOptionDetailsSensor>();
    public LinkedHashMap<Integer, SensorGroupingDetails> mSensorGroupingMap = new LinkedHashMap<Integer, SensorGroupingDetails>();
	public TreeMap<Integer, TreeMap<Integer, CalibDetails>> mCalibMap = new TreeMap<Integer, TreeMap<Integer, CalibDetails>>(); 

    protected ShimmerDevice mShimmerDevice = null;
    
	public AbstractSensor(SENSORS sensorType){
		mSensorType = sensorType;
		setSensorName(sensorType.toString());
	}
	
	public AbstractSensor(SENSORS sensorType, ShimmerVerObject svo){
		this(sensorType);
		if(svo!=null){
			mShimmerVerObject = svo;
		}
	}

	public AbstractSensor(SENSORS sensorType, ShimmerDevice shimmerDevice) {
		this(sensorType, shimmerDevice.getShimmerVerObject());
		this.mShimmerDevice  = shimmerDevice;
	}
	
	/**
	 * Initialises all the maps in the sensor class. Contents used to be based
	 * in the AbstractSensor contructor but this led to problems trying to
	 * access variables in the specifc classes because they hadn't been
	 * initialise fully yet?
	 */
	public void initialise(){
		if(mShimmerVerObject!=null){
			generateSensorMap();
			generateConfigOptionsMap();
			generateSensorGroupMapping();

			generateCalibMap();
			setDefaultConfigAllSensors();
		}
	}
	
	private void setDefaultConfigAllSensors() {
		for(Integer sensorId:mSensorMap.keySet()){
			setDefaultConfigForSensor(sensorId, false);
		}
	}
	
	public HashMap<String, ConfigOptionDetailsSensor> getConfigMap() {
		return mConfigOptionsMap;
	}
	
	public void setSensorName(String sensorName) {
		mSensorName = sensorName;
	}

	public String getSensorName() {
		return mSensorName;
	}
	
	public int getNumberOfEnabledChannels(COMMUNICATION_TYPE commType){
		int count = 0;
		Iterator<SensorDetails> iterator = mSensorMap.values().iterator();
		while(iterator.hasNext()){
			SensorDetails sensorEnabledDetails = iterator.next();
			if (sensorEnabledDetails.isEnabled(commType)){
				count += sensorEnabledDetails.mListOfChannels.size();
			}
		}
		return count;
	}

	public boolean isAnySensorChannelEnabled(COMMUNICATION_TYPE commType){
		return (getNumberOfEnabledChannels(commType)>0? true:false);
	}

	public void setIsEnabledAllSensors(COMMUNICATION_TYPE commType, boolean state){
		Iterator<SensorDetails> iterator = mSensorMap.values().iterator();
		while(iterator.hasNext()){
			SensorDetails sensorDetails = iterator.next();
			sensorDetails.setIsEnabled(commType, state);
		}
	}
	
	public boolean setIsEnabledSensor(COMMUNICATION_TYPE commType, boolean state, int sensorId){
		SensorDetails sensorDetails = mSensorMap.get(sensorId);
		if(sensorDetails!=null){
			sensorDetails.setIsEnabled(commType, state);
			return true;
		}
		return false;
	}
	
	@Deprecated //?? not sure whether it's be to do this here or in ShimmerDevice
	public void updateStateFromEnabledSensorsVars(COMMUNICATION_TYPE commType, long enabledSensors, long derivedSensors) {
		for(SensorDetails sensorDetails:mSensorMap.values()){
			//check as to whether the sensor originates from the Shimmer data packet or the API
//			if(sensorDetails.isApiSensor()){
//				continue;
//			}
			
			sensorDetails.updateFromEnabledSensorsVars(commType, enabledSensors, derivedSensors);
			
//			boolean state = false;
//			if(sensorDetails.isDerivedChannel()){
//				//TODO check enabledSensors as well if required???
//				state = (derivedSensors & sensorDetails.mDerivedSensorBitmapID)>0? true:false;
//			}
//			else {
//				state = (enabledSensors & sensorDetails.mSensorDetailsRef.mSensorBitmapIDStreaming)>0? true:false;
//			}
//			sensorDetails.setIsEnabled(commType, state);
		}
		
//		//TODO: enabledSensors should be directed at channels coming from the Shimmer, derivedSensors at channels from the API 
//		//TODO move to abstract or override in the extended sensor classes so complexities like EXG can be handled
//		mIsEnabled = false;
//		boolean state = (enabledSensors & mSensorBitmapIDStreaming)>0? true:false;
//		mIsEnabled = state;
//		setSensorChannelsState(commType, state);
	}
	
	
	public void updateSensorGroupingMap() {
		for(SensorGroupingDetails sensorGroup:mSensorGroupingMap.values()) {
			// Ok to clear here because variable is initiated in the class
			sensorGroup.mListOfConfigOptionKeysAssociated = new ArrayList<String>();
			for (Integer sensorKey:sensorGroup.mListOfSensorIdsAssociated) {
				SensorDetails sensorDetails = mSensorMap.get(sensorKey);
				if(sensorDetails!=null && sensorDetails.mSensorDetailsRef!=null && sensorDetails.mSensorDetailsRef.mListOfConfigOptionKeysAssociated!=null){
					for (String configOption:sensorDetails.mSensorDetailsRef.mListOfConfigOptionKeysAssociated) {
						// do not add duplicates
						if (!(sensorGroup.mListOfConfigOptionKeysAssociated.contains(configOption))) {
							sensorGroup.mListOfConfigOptionKeysAssociated.add(configOption);
						}
					}
				}
				
				//TODO handle mListOfCompatibleVersionInfo here?
//				sensorGroup.mListOfCompatibleVersionInfo = listOfCompatibleVersionInfo;
			}
		}
	}

	public String getSensorGuiFriendlyLabel(int sensorId) {
		SensorDetails sensorDetails = mSensorMap.get(sensorId);
		if(sensorDetails!=null){
			return sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel;
		}
		return null;
	}
	
	public SensorDetails getSensorDetails(int sensorId){
		SensorDetails sensorDetails = mSensorMap.get(sensorId);
		return sensorDetails;
	}

	public List<ShimmerVerObject> getSensorListOfCompatibleVersionInfo(int sensorId) {
		SensorDetails sensorDetails = mSensorMap.get(sensorId);
		if(sensorDetails!=null){
			return sensorDetails.mSensorDetailsRef.mListOfCompatibleVersionInfo;
		}
		return null;
	}
	
	public void createLocalSensorMap(Map<Integer, SensorDetailsRef> sensorMapRef, Map<String, ChannelDetails> channelMapRef) {
		mSensorMap = new TreeMap<Integer, SensorDetails>();
		for(int sensorId:sensorMapRef.keySet()){
			SensorDetailsRef sensorDetailsRef = sensorMapRef.get(sensorId);
			SensorDetails sensorDetails = new SensorDetails(false, 0, sensorDetailsRef);
			updateSensorDetailsWithChannels(sensorDetails, channelMapRef);
			mSensorMap.put(sensorId, sensorDetails);
		}
	}
	
	public void createLocalSensorMapWithCustomParser(Map<Integer, SensorDetailsRef> sensorMapRef, Map<String, ChannelDetails> channelMapRef) {
		mSensorMap = new TreeMap<Integer, SensorDetails>();
		for(int sensorId:sensorMapRef.keySet()){
			SensorDetailsRef sensorDetailsRef = sensorMapRef.get(sensorId);
			SensorDetails sensorDetails = new SensorDetails(false, 0, sensorDetailsRef){
				@Override
				public ObjectCluster processData(byte[] rawData, COMMUNICATION_TYPE commType, ObjectCluster object, boolean isTimeSyncEnabled, double pcTimestampMs) {
//					System.out.println("PARSING\t" + this.mSensorDetails.mGuiFriendlyLabel);
					return processDataCustom(this, rawData, commType, object, isTimeSyncEnabled, pcTimestampMs);
//					return super.processData(rawData, commType, object);
				}
			};
			updateSensorDetailsWithChannels(sensorDetails, channelMapRef);
			mSensorMap.put(sensorId, sensorDetails);
		}
	}

	public void updateSensorDetailsWithChannels(SensorDetails sensorDetails, Map<String, ChannelDetails> channelMapRef){
		List<String> listOfChannelsRef = sensorDetails.mSensorDetailsRef.mListOfChannelsRef;
		for(String channelKey:listOfChannelsRef){
			ChannelDetails channelDetails = channelMapRef.get(channelKey);
			if(channelDetails!=null){
				sensorDetails.mListOfChannels.add(channelDetails);
			}
		}
	}
	
	public HashMap<String, ConfigOptionDetailsSensor> getConfigOptionsMap() {
		return mConfigOptionsMap;
	}
	
	public LinkedHashMap<Integer, SensorGroupingDetails> getSensorGroupingMap() {
		return mSensorGroupingMap;
	}

	public boolean isSensorEnabled(int sensorId){
		//below Shouldn't be needed
//		if(mShimmerDevice!=null){
//			return mShimmerDevice.isSensorEnabled(sensorId);
//		}

		if(mSensorMap!=null) {
			SensorDetails sensorDetails = mSensorMap.get(sensorId);
			if(sensorDetails!=null){
				return sensorDetails.isEnabled();
			}
		}		
		return false;
	}
	
	public void setSamplingRateFromShimmer(double maxSetRate) {
//		mMaxSetShimmerSamplingRate = maxSetRate;
		setSensorSamplingRate(maxSetRate);
	}
	
	protected double getSamplingRateShimmer() {
//		return mMaxSetShimmerSamplingRate;
		if(mShimmerDevice!=null){
			return mShimmerDevice.getSamplingRateShimmer();
		}
		return 128.0;//Double.NaN;
	}
	
	public int getHardwareVersion() {
		return mShimmerVerObject.mHardwareVersion;
	}
	
	public void updateSensorDetailsWithCommsTypes(List<COMMUNICATION_TYPE> listOfSupportedCommsTypes) {
		for(SensorDetails sensorDetails:mSensorMap.values()){
			sensorDetails.updateSensorDetailsWithCommsTypes(listOfSupportedCommsTypes);
		}
	}
	
	public Object setConfigValueUsingConfigLabel(String configLabel, Object valueToSet){
		return setConfigValueUsingConfigLabel(Configuration.Shimmer3.SENSOR_ID.RESERVED_ANY_SENSOR, configLabel, valueToSet);
	}
	
	public Object getConfigValueUsingConfigLabel(String configLabel){
		return getConfigValueUsingConfigLabel(Configuration.Shimmer3.SENSOR_ID.RESERVED_ANY_SENSOR, configLabel);
	}

	/** Quickly implemented method to print channel data to the console
	 * @param objectCluster 
	 * @param listOfChannelOCNAndType a list of channel objectClusterNames to print to the console
	 */
	public void consolePrintChannelsCal(ObjectCluster objectCluster, List<String[]> listOfChannelOCNAndType) {
		String textToPrint = "";
		for(String[] channelOCN:listOfChannelOCNAndType){
			textToPrint += channelOCN[0] + "_" + channelOCN[1] + ":";
			FormatCluster formatCluster = (FormatCluster)ObjectCluster.returnFormatCluster(objectCluster.getCollectionOfFormatClusters(channelOCN[0]), channelOCN[1]);
			if(formatCluster==null){
				textToPrint += "null";
			}
			else {
				double tempCal = formatCluster.mData;
				textToPrint += tempCal;
			}
			textToPrint += "\t";
		}
		//TODO use the consolePrintLn system 
		System.out.println(textToPrint);
	}
	
	
	protected void setCalibration(TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfSensorCalibration) {
		mCalibMap = new TreeMap<Integer, TreeMap<Integer, CalibDetails>>();
		for(Integer sensorId:mSensorMap.keySet()){
			TreeMap<Integer, CalibDetails> mapOfCalibPerSensor = mapOfSensorCalibration.get(sensorId);
			if(mapOfCalibPerSensor!=null){
				setCalibrationMapPerSensor(sensorId, mapOfCalibPerSensor);
			}
		}
	}

	public void setCalibrationMapPerSensor(int sensorId, TreeMap<Integer, CalibDetails> mapOfSensorCalibration) {
		mCalibMap.put(sensorId, mapOfSensorCalibration);
//		System.out.println("Calib make check");
	}

	public CalibDetails getCalibForSensor(int sensorId, int range) {
		TreeMap<Integer, CalibDetails> calibDetailsPerSensor = getCalibrationMapForSensor(sensorId); 
		if(calibDetailsPerSensor!=null){
			CalibDetails calibDetails = calibDetailsPerSensor.get(range);
			return calibDetails;
		}
		return null;
	}

	public TreeMap<Integer, CalibDetails> getCalibrationMapForSensor(int sensorId) {
		TreeMap<Integer, CalibDetails> calibDetailsPerSensor = mCalibMap.get(sensorId); 
		return calibDetailsPerSensor;
	}
	
	
	//--------- Optional methods to override in Sensor Class start --------
	public void generateCalibMap(){
		mCalibMap = new TreeMap<Integer, TreeMap<Integer, CalibDetails>>();
    	//NOT USED IN THIS CLASS
    	//USED in {Kinematic sensors}
	}
	
	/**
	 * Checks a specific sensor class to see if it is using it's default
	 * calibration parameters. Used, for example, in SensorLSM, SensorMPU and
	 * SensorKionix
	 * 
	 * @param sensorId
	 * @return boolean indicating that the sensor is using default calibration
	 *         parameters.
	 */
	public boolean isSensorUsingDefaultCal(int sensorId) {
		return false;
	}

	public void handleSpecialCasesAfterSensorMapCreate() {
    	//NOT USED IN THIS CLASS
    	//USED in {SensorEXG}
	}
	
	public void handleSpecCasesBeforeSensorMapUpdateGeneral(ShimmerDevice shimmerDevice) {
    	//NOT USED IN THIS CLASS
    	//USED in {SensorEXG}
	}

    public boolean handleSpecCasesBeforeSensorMapUpdatePerSensor(ShimmerDevice shimmerDevice, Integer sensorId){
    	//NOT USED IN THIS CLASS
    	//USED in {SensorPPG, SensorEXG}
    	return false;
    }
    
    
	public void handleSpecCasesAfterSensorMapUpdateFromEnabledSensors() {
		//NOT IN THIS CLASS
		//USED in {SensorPPG, SensorEXG}
	}

	
	public void handleSpecCasesUpdateEnabledSensors() {
		//TODO Auto-generated method stub
		//USED in {SensorEXG}
	}
	
	public int handleSpecCasesBeforeSetSensorState(int sensorId, boolean state) {
		//NOT USED IN THIS CLASS
    	//USED in {SensorPPG}	
		return sensorId;
	}
	
//	public byte[] generateAllCalibByteArray() {
//		if(mCalibMap.isEmpty()){
//			return null;
//		}
//		
//		byte[] calibBytesAllPerSensor = new byte[]{};
//		for(Integer sensorId:mCalibMap.keySet()){
//			TreeMap<Integer, CalibDetails> calMapPerSensor = mCalibMap.get(sensorId);
////			SensorDetails sensorDetais = mSensorMap.get(sensorId);
////			Integer calibSensorKey = sensorDetais.mSensorDetailsRef.mCalibSensorKey;
////			if(sensorId!=0){
//				for(Integer range:calMapPerSensor.keySet()){
//					byte[] calibSensorKeyBytes = new byte[2];
//					calibSensorKeyBytes[0] = (byte)((sensorId>>0)&0xFF);
//					calibSensorKeyBytes[1] = (byte)((sensorId>>8)&0xFF);
//					
//					CalibDetails calDetailsPerRange = calMapPerSensor.get(range);		
//					byte[] calibBytesPerRange = calDetailsPerRange.generateCalParamByteArrayWithTimestamp();
//
//					//Create a new calibration param array
//					byte[] calibBytesPacketPerRange = new byte[calibSensorKeyBytes.length+calibBytesPerRange.length];
//					System.arraycopy(calibSensorKeyBytes, 0, calibBytesPacketPerRange, 0, calibSensorKeyBytes.length);
//					System.arraycopy(calibBytesPerRange, 0, calibBytesPacketPerRange, calibSensorKeyBytes.length, calibBytesPerRange.length);
//					
//					//Copy new calib param array to end of exisiting array
//					byte[] newCalibBytesAllPerSensor = new byte[calibBytesAllPerSensor.length+calibBytesPacketPerRange.length];
//					System.arraycopy(calibBytesAllPerSensor, 0, newCalibBytesAllPerSensor, 0, calibBytesAllPerSensor.length);
//					System.arraycopy(calibBytesPacketPerRange, 0, newCalibBytesAllPerSensor, calibBytesAllPerSensor.length, calibBytesPacketPerRange.length);
//					calibBytesAllPerSensor = newCalibBytesAllPerSensor;
//				}
////			}
//		}
//		return calibBytesAllPerSensor;
//	}
//	
//	//TODO
//	public boolean parseAllCalibByteArray(int sensorId, int rangeValue, byte[] calibTimeBytesTicks, byte[] calibBytes) {
//		if(!mSensorMap.containsKey(sensorId)){
//			return false;
//		}
//		
//		TreeMap<Integer, CalibDetails> mapOfSensorCalib = mCalibMap.get(sensorId);
//		if(mapOfSensorCalib==null){
//			mCalibMap.put(sensorId, new TreeMap<Integer, CalibDetails>());
//		}
//		
//		mapOfSensorCalib = mCalibMap.get(sensorId);
//		CalibDetails calibDetailsPerRange = mapOfSensorCalib.get(rangeValue);
//		if(calibDetailsPerRange==null){
//			//TODO UNKOWN RANGE
////			mapOfSensorCalib.put(range, new CalibDetailsKinematic(rangeValue, rangeString))
//		}
//		
//		calibDetailsPerRange = mapOfSensorCalib.get(rangeValue);
//		if(calibDetailsPerRange!=null && calibDetailsPerRange instanceof CalibDetailsKinematic){
//			calibDetailsPerRange.setCalibTimeFromMs(calibTimeBytesTicks, calibTime);
////			System.out.println("Set Calib Time");
////			System.out.println("Check");
//			((CalibDetailsKinematic)calibDetailsPerRange).parseCalParamByteArray(calibBytes);
//		}
//		
//		return true;
//	}

	protected void setAllCalibSensitivityScaleFactor(CALIBRATION_SCALE_FACTOR sensitivityScaleFactor) {
		for(Integer sensorId:mCalibMap.keySet()){
			setCalibSensitivityScaleFactor(sensorId, sensitivityScaleFactor);
		}
	}

	protected void setCalibSensitivityScaleFactor(int sensorId, CALIBRATION_SCALE_FACTOR sensitivityScaleFactor) {
		TreeMap<Integer, CalibDetails> calibMapPerSensor = mCalibMap.get(sensorId);
		if(calibMapPerSensor!=null){
			for(CalibDetails calibMapPerRange:calibMapPerSensor.values()){
				((CalibDetailsKinematic)calibMapPerRange).setSensitivityScaleFactor(sensitivityScaleFactor);
			}
		}
	}
	
	public Object setConfigValueUsingConfigLabelCommon(Integer sensorId, String configLabel, Object valueToSet) {
		Object returnValue = null;
		switch(configLabel){
			case(GuiLabelConfigCommon.CALIBRATION_ALL):
				setCalibration((TreeMap<Integer, TreeMap<Integer, CalibDetails>>) valueToSet);
				//TODO decide whether to include the below
//				returnValue = valueToSet;
				break;
			case(GuiLabelConfigCommon.CALIBRATION_PER_SENSOR):
				setCalibrationMapPerSensor(sensorId, (TreeMap<Integer, CalibDetails>) valueToSet);
				break;
	        default:
	        	break;
		}
		return returnValue;
	}
	public Object getConfigValueUsingConfigLabelCommon(Integer sensorId, String configLabel) {
		Object returnValue = null;
		switch(configLabel){
			case(GuiLabelConfigCommon.CALIBRATION_PER_SENSOR):
				returnValue = getCalibrationMapForSensor(sensorId);
				break;
			case(GuiLabelConfigCommon.CALIBRATION_ALL):
				returnValue = mCalibMap;
	        	break;
			default:
				break;
		}
		return returnValue;
	}
	
	public static void addCalibDetailsToDbMap(
			LinkedHashMap<String, Object> mapOfConfig, 
			CalibDetailsKinematic calibDetails, 
			List<String> listOfCalibHandles, 
			String calibTimeHandle) {
		addCalibDetailsToDbMap(mapOfConfig, calibDetails, calibTimeHandle,
				listOfCalibHandles.get(0), listOfCalibHandles.get(1), listOfCalibHandles.get(2), 
				listOfCalibHandles.get(3), listOfCalibHandles.get(4), listOfCalibHandles.get(5), 
				listOfCalibHandles.get(6), listOfCalibHandles.get(7), listOfCalibHandles.get(8), 
				listOfCalibHandles.get(9), listOfCalibHandles.get(10), listOfCalibHandles.get(11), 
				listOfCalibHandles.get(12), listOfCalibHandles.get(13), listOfCalibHandles.get(14));
	}

	public static void addCalibDetailsToDbMap(
			LinkedHashMap<String, Object> mapOfConfig, CalibDetailsKinematic calibDetails, 
			String calibTimeHandle,
			String offsetX, String offsetY, String offsetZ,
			String gainX, String gainY, String gainZ,
			String alignXx, String alignXy, String alignXz,
			String alignYx, String alignYy, String alignYz,
			String alignZx, String alignZy, String alignZz) {

		mapOfConfig.put(offsetX, calibDetails.getValidOffsetVector()[0][0]);
		mapOfConfig.put(offsetY, calibDetails.getValidOffsetVector()[1][0]);
		mapOfConfig.put(offsetZ, calibDetails.getValidOffsetVector()[2][0]);

		mapOfConfig.put(gainX, calibDetails.getValidSensitivityMatrix()[0][0]);
		mapOfConfig.put(gainY, calibDetails.getValidSensitivityMatrix()[1][1]);
		mapOfConfig.put(gainZ, calibDetails.getValidSensitivityMatrix()[2][2]);

		mapOfConfig.put(alignXx, calibDetails.getValidAlignmentMatrix()[0][0]);
		mapOfConfig.put(alignXy, calibDetails.getValidAlignmentMatrix()[0][1]);
		mapOfConfig.put(alignXz, calibDetails.getValidAlignmentMatrix()[0][2]);
		
		mapOfConfig.put(alignYx, calibDetails.getValidAlignmentMatrix()[1][0]);
		mapOfConfig.put(alignYy, calibDetails.getValidAlignmentMatrix()[1][1]);
		mapOfConfig.put(alignYz, calibDetails.getValidAlignmentMatrix()[1][2]);
		
		mapOfConfig.put(alignZx, calibDetails.getValidAlignmentMatrix()[2][0]);
		mapOfConfig.put(alignZy, calibDetails.getValidAlignmentMatrix()[2][1]);
		mapOfConfig.put(alignZz, calibDetails.getValidAlignmentMatrix()[2][2]);
		
		if(!calibTimeHandle.isEmpty()){
			mapOfConfig.put(calibTimeHandle, calibDetails.getCalibTimeMs());
		}
	}
	
	public static void addCalibDetailsToDbMap(
			LinkedHashMap<String, Object> configMapForDb,
			List<String> listOfCalibHandles,
			double[][] offsetVector, 
			double[][] sensitivityMatrix, 
			double[][] alignmentMatrix) {
		
		configMapForDb.put(listOfCalibHandles.get(0), offsetVector[0][0]);
		configMapForDb.put(listOfCalibHandles.get(1), offsetVector[1][0]);
		configMapForDb.put(listOfCalibHandles.get(2), offsetVector[2][0]);

		configMapForDb.put(listOfCalibHandles.get(3), sensitivityMatrix[0][0]);
		configMapForDb.put(listOfCalibHandles.get(4), sensitivityMatrix[1][1]);
		configMapForDb.put(listOfCalibHandles.get(5), sensitivityMatrix[2][2]);
	
		configMapForDb.put(listOfCalibHandles.get(6), alignmentMatrix[0][0]);
		configMapForDb.put(listOfCalibHandles.get(7), alignmentMatrix[0][1]);
		configMapForDb.put(listOfCalibHandles.get(8), alignmentMatrix[0][2]);
		
		configMapForDb.put(listOfCalibHandles.get(9), alignmentMatrix[1][0]);
		configMapForDb.put(listOfCalibHandles.get(10), alignmentMatrix[1][1]);
		configMapForDb.put(listOfCalibHandles.get(11), alignmentMatrix[1][2]);
		
		configMapForDb.put(listOfCalibHandles.get(12), alignmentMatrix[2][0]);
		configMapForDb.put(listOfCalibHandles.get(13), alignmentMatrix[2][1]);
		configMapForDb.put(listOfCalibHandles.get(14), alignmentMatrix[2][2]);
	}


	public void parseCalibDetailsKinematicFromDb(LinkedHashMap<String, Object> mapOfConfigPerShimmer, int sensorId, int range, List<String> listOfCalibHandles) {
		this.parseCalibDetailsKinematicFromDb(mapOfConfigPerShimmer, sensorId, range, listOfCalibHandles, "");
	}

	/** 
	 * @see ShimmerObject.parseCalibDetailsKinematicFromDb
	 * */
	public void parseCalibDetailsKinematicFromDb(LinkedHashMap<String, Object> mapOfConfigPerShimmer, int sensorId, int range, List<String> listOfCalibHandles, String calibTimeHandle) {
		parseCalibDetailsKinematicFromDb(
				mapOfConfigPerShimmer, sensorId, range, calibTimeHandle,
				listOfCalibHandles.get(0), listOfCalibHandles.get(1), listOfCalibHandles.get(2), 
				listOfCalibHandles.get(3), listOfCalibHandles.get(4), listOfCalibHandles.get(5), 
				listOfCalibHandles.get(6), listOfCalibHandles.get(7), listOfCalibHandles.get(8), 
				listOfCalibHandles.get(9), listOfCalibHandles.get(10), listOfCalibHandles.get(11), 
				listOfCalibHandles.get(12), listOfCalibHandles.get(13), listOfCalibHandles.get(14));
	}

	/** 
	 * @see ShimmerObject.parseCalibDetailsKinematicFromDb
	 * */
	public void parseCalibDetailsKinematicFromDb(
			LinkedHashMap<String, Object> mapOfConfigPerShimmer, int sensorId, int range,  String calibTimeHandle,
			String offsetX, String offsetY, String offsetZ, 
			String gainX, String gainY, String gainZ, 
			String alignXx, String alignXy, String alignXz, 
			String alignYx, String alignYy, String alignYz, 
			String alignZx, String alignZy, String alignZz) {
		
		CalibDetails calibDetails = getCalibForSensor(sensorId, range);
		parseCalibDetailsKinematicFromDb(
				calibDetails, mapOfConfigPerShimmer, calibTimeHandle,
				offsetX, offsetY, offsetZ, 
				gainX, gainY, gainZ, 
				alignXx, alignXy, alignXz, 
				alignYx, alignYy, alignYz, 
				alignZx, alignZy, alignZz);
	}
	
	public static void parseCalibDetailsKinematicFromDb(
			CalibDetails calibDetails,
			LinkedHashMap<String, Object> mapOfConfigPerShimmer, String calibTimeHandle,
			String offsetX, String offsetY, String offsetZ, 
			String gainX, String gainY, String gainZ, 
			String alignXx, String alignXy, String alignXz, 
			String alignYx, String alignYy, String alignYz, 
			String alignZx, String alignZy, String alignZz) {

		if(calibDetails!=null && calibDetails instanceof CalibDetailsKinematic){
			((CalibDetailsKinematic) calibDetails).parseCalibDetailsKinematicFromDb(mapOfConfigPerShimmer,
					calibTimeHandle,
					offsetX, offsetY, offsetZ, 
					gainX, gainY, gainZ, 
					alignXx, alignXy, alignXz, 
					alignYx, alignYy, alignYz, 
					alignZx, alignZy, alignZz);
		}
	}

	public double calcMaxSamplingRate() {
		return Double.POSITIVE_INFINITY;
	}

	//--------- Optional methods to override in Sensor Class end -------- 

	
	public static String parseFromDBColumnToGUIChannel(Map<String, ChannelDetails> channelMapRef, String databaseChannelHandle) {
		for(ChannelDetails channelDetails:channelMapRef.values()){
			if(channelDetails.getDatabaseChannelHandle().equals(databaseChannelHandle)){
				return channelDetails.mObjectClusterName;
			}
		}
		return "";
	}

	public static String parseFromGUIChannelsToDBColumn(Map<String, ChannelDetails> channelMapRef, String objectClusterName) {
		ChannelDetails channelDetails = channelMapRef.get(objectClusterName);
		if(channelDetails!=null){
			return channelDetails.getDatabaseChannelHandle();
		}
		return "";
	}
	
	public CalibDetailsKinematic getCurrentCalibDetailsIfKinematic(int sensorId, int range){
		CalibDetails calibPerSensor = getCalibForSensor(sensorId, range);
		if(calibPerSensor!=null && calibPerSensor instanceof CalibDetailsKinematic){
			return (CalibDetailsKinematic) calibPerSensor;
		}
		return null;
	}
	
	public void addConfigOption(ConfigOptionDetailsSensor configOptionDetails) {
		mConfigOptionsMap.put(configOptionDetails.mGuiHandle, configOptionDetails); 
	}
	
	
	public void setSensorConfig(ISensorConfig sensorConfig) {
		// Can be overridden by extended class
	}
	
	public List<ISensorConfig> getSensorConfig() {
		// Can be overridden by extended class
		return new ArrayList<>();
	}
	


}
