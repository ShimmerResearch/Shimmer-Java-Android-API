package com.shimmerresearch.driver;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;

import com.shimmerresearch.algorithms.AbstractAlgorithm;
import com.shimmerresearch.algorithms.AlgorithmResultObject;
import com.shimmerresearch.algorithms.gyroOnTheFlyCal.GyroOnTheFlyCalLoader;
import com.shimmerresearch.algorithms.AlgorithmDetails;
import com.shimmerresearch.algorithms.AlgorithmDetails.SENSOR_CHECK_METHOD;
import com.shimmerresearch.algorithms.AlgorithmLoaderInterface;
import com.shimmerresearch.algorithms.orientation.OrientationModule6DOFLoader;
import com.shimmerresearch.algorithms.orientation.OrientationModule9DOFLoader;
import com.shimmerresearch.algorithms.verisense.gyroAutoCal.GyroOnTheFlyCalLoaderVerisense;
import com.shimmerresearch.bluetooth.DataProcessingInterface;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.radioProtocol.CommsProtocolRadio;
import com.shimmerresearch.comms.serialPortInterface.AbstractSerialPortHal;
import com.shimmerresearch.comms.wiredProtocol.UartComponentPropertyDetails;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.calibration.CalibDetails;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic;
import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;
import com.shimmerresearch.driverUtilities.AssembleShimmerConfig;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.EepromSensorSettingsDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails;
import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails.BATTERY_LEVEL;
import com.shimmerresearch.driverUtilities.ShimmerLogDetails;
import com.shimmerresearch.driverUtilities.ShimmerSDCardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ConfigOptionDetails;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.DEVICE_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.sensors.SensorSystemTimeStamp;
import com.shimmerresearch.sensors.SensorShimmerClock;
import com.shimmerresearch.shimmerConfig.FixedShimmerConfigs;
import com.shimmerresearch.shimmerConfig.FixedShimmerConfigs.FIXED_SHIMMER_CONFIG_MODE;
import com.shimmerresearch.verisense.sensors.ISensorConfig;

public abstract class ShimmerDevice extends BasicProcessWithCallBack implements Serializable{

	private static final long serialVersionUID = 5087199076353402591L;
	
	public static final String DEFAULT_DOCKID = "Default.01";
	public static final int DEFAULT_SLOTNUMBER = -1;
	public static final String DEFAULT_SHIMMER_NAME = "Shimmer";
	public static final String DEFAULT_SHIMMER_NAME_WITH_ERROR = DEFAULT_SHIMMER_NAME + "_" + "FFFF";
	public static final String DEFAULT_EXPERIMENT_NAME = "DefaultTrial";
	public static final String DEFAULT_MAC_ID = "";
	public static final String DEVICE_ID = "Device_ID";
	public static final String STRING_CONSTANT_PENDING = "Pending";
	public static final String STRING_CONSTANT_UNKNOWN = "Unknown";
	public static final String STRING_CONSTANT_NOT_AVAILABLE = "N/A";
	public static final String STRING_CONSTANT_SD_ERROR = "SD Error";
	public static final double IRREGULAR_SAMPLING_RATE = 0.0;
	protected static final int MAX_CALIB_DUMP_MAX = 4096;
	
	public static final String INVALID_TRIAL_NAME_CHAR = "[^A-Za-z0-9._]";
	
	private static boolean mEnableProcessMarkers = true;
	
	/**Holds unique location information on a dock or COM port number for Bluetooth connection*/
	public String mUniqueID = "";
	
	private boolean mIsPlaybackDevice = false;		
	private boolean mIsEnabledAlgorithmModulesDuringPlayback = false;
	
	/** A shimmer device will have multiple sensors, depending on HW type and revision, these type of sensors can change, this holds a list of all the sensors for different versions. This only works with classes which implements the ShimmerHardwareSensors interface. E.g. ShimmerGQ. A single AbstractSensor (e.g., for MPU92X50) class can contain multiple SensorDetails (e.g., Accel, gyro etc.) */
	protected LinkedHashMap<SENSORS, AbstractSensor> mMapOfSensorClasses = new LinkedHashMap<SENSORS, AbstractSensor>();
	protected LinkedHashMap<Integer, SensorDetails> mSensorMap = new LinkedHashMap<Integer, SensorDetails>();
	/** The contents of Parser are kept in a fixed order based on the SENSOR_ID */ 
	protected HashMap<COMMUNICATION_TYPE, TreeMap<Integer, SensorDetails>> mParserMap = new HashMap<COMMUNICATION_TYPE, TreeMap<Integer, SensorDetails>>();
	protected Map<String, ConfigOptionDetailsSensor> mConfigOptionsMapSensors = new HashMap<String, ConfigOptionDetailsSensor>();
	protected TreeMap<Integer, SensorGroupingDetails> mSensorGroupingMap = new TreeMap<Integer, SensorGroupingDetails>();
	
	/** Contains all loaded Algorithm modules */
	protected Map<String, AbstractAlgorithm> mMapOfAlgorithmModules = new HashMap<String, AbstractAlgorithm>();
	protected ArrayList<String> mAlgorithmProcessingSequence = null;
	/** All supported channels based on hardware, expansion board and firmware */
//	protected Map<String, AlgorithmDetails> mMapOfAlgorithmDetails = new LinkedHashMap<String, AlgorithmDetails>();
	/** for tile generation in GUI configuration */ 
	protected TreeMap<Integer, SensorGroupingDetails> mMapOfAlgorithmGrouping = new TreeMap<Integer, SensorGroupingDetails>();
	protected Map<String, ConfigOptionDetails> mConfigOptionsMapAlgorithms = new HashMap<String, ConfigOptionDetails>();

	public List<COMMUNICATION_TYPE> mListOfAvailableCommunicationTypes = new ArrayList<COMMUNICATION_TYPE>();

	/** Used in UART command through the base/dock*/
	public String mMacIdFromUart = DEFAULT_MAC_ID;
	public String mShimmerUserAssignedName = "";//DEFAULT_SHIMMER_NAME; // This stores the user assigned name
	public String mAlternativeName = "";
	public HashMap<COMMUNICATION_TYPE, Double> mMapOfSamplingRatesShimmer = new HashMap<COMMUNICATION_TYPE, Double>(); // 51.2Hz is the default sampling rate 
//	protected double mSamplingRateShimmer; 	                                        	// 51.2Hz is the default sampling rate 
	{
		mMapOfSamplingRatesShimmer.put(COMMUNICATION_TYPE.SD, 51.2);
		mMapOfSamplingRatesShimmer.put(COMMUNICATION_TYPE.BLUETOOTH, 51.2);
	}

	public String mDockID = DEFAULT_DOCKID;
	public DEVICE_TYPE mDockType = DEVICE_TYPE.UNKOWN;
	public int mSlotNumber = DEFAULT_SLOTNUMBER;

	public ShimmerVerObject mShimmerVerObject = new ShimmerVerObject();
	//TODO use entry in ShimmerVerObject instead?
	public ExpansionBoardDetails mExpansionBoardDetails = new ExpansionBoardDetails();
	public EepromSensorSettingsDetails mEepromSensorSettingsDetails = new EepromSensorSettingsDetails(); 
	public ShimmerBattStatusDetails mShimmerBattStatusDetails = new ShimmerBattStatusDetails(); 
	public ShimmerSDCardDetails mShimmerSDCardDetails = new ShimmerSDCardDetails(); 

	public boolean mReadHwFwSuccess = false;
	private boolean mConfigurationReadSuccess = false;
	public boolean mReadDaughterIDSuccess = false;
	public boolean writeRealWorldClockFromPcTimeSuccess = false;
	public boolean mReadBtVerSuccess = false;
	public boolean mReadBtModeSuccess = false;
	
	protected boolean mIsConnected = false;
	protected boolean mIsSensing = false;
	protected boolean mIsStreaming = false;											// This is used to monitor whether the device is in streaming mode
	protected boolean mIsInitialised = false;
	private boolean mIsDocked = false;
	private boolean mIsUsbPluggedIn = false;
	private boolean mIsDockCommsSuccessful = false;
	public boolean mIsUsbDfuMode= false;
	protected boolean mHaveAttemptedToReadConfig = false;

	//BSL related start
	public String mActivityLog = "";
	public int mFwImageWriteProgress = 0;
	public int mFwImageTotalSize = 0;
	public float mFwImageWriteSpeed = 0;
	public String mFwImageWriteCurrentAction = "";
	public List<MsgDock> mListOfFailMsg = new ArrayList<MsgDock>();
	//BSL related end
	
	public List<ShimmerException> mListOfDeviceExceptions = new ArrayList<ShimmerException>();
	
	//TODO: are these variables too specific to different versions of Shimmer HW?
	public long mShimmerRealTimeClockConFigTime = 0;
	public long mShimmerLastReadRtcValueMilliSecs = 0;
	public String mShimmerLastReadRtcValueParsed = "";
	protected ConfigByteLayout mConfigByteLayout;// = new InfoMemLayoutShimmer3(); //default
	protected byte[] mConfigBytes = ConfigByteLayout.createConfigByteArrayEmpty(512);
	/**shows the original contents of the Infomem any configuration is changed */
	protected byte[] mInfoMemBytesOriginal = ConfigByteLayout.createConfigByteArrayEmpty(512);
	
	public byte[] mCalibBytes = new byte[]{};
	public HashMap<Integer, String> mCalibBytesDescriptions = new HashMap<Integer, String>();

	protected String mTrialName = "";
	protected long mConfigTime; //this is in milliseconds, utc

	//-------- Packet reception rate start --------------
	public static final double DEFAULT_RECEPTION_RATE = 0.0;//100.0;
	public long mPacketReceivedCountCurrent = 0;
	public long mPacketExpectedCountCurrent = 0;
	private double mPacketReceptionRateCurrent = DEFAULT_RECEPTION_RATE;
	
	public long mPacketReceivedCountOverall = 0;
	public long mPacketExpectedCountOverall = 0;
	private double mPacketReceptionRateOverall = DEFAULT_RECEPTION_RATE;
	
	private long mPacketLossCountPerTrial = 0;		//Used by ShimmerBluetooth
	//-------- Packet reception rate end --------------
	
	//Events markers
	protected long mEventMarkersCodeLast = 0;
	protected boolean mEventMarkersIsPulse = false;
	public final static int EVENT_MARKER_DEFAULT = -1; // using -1 as the default event marker value as as a value of 0 was hanging the plots and the software
	public long mEventMarkers = EVENT_MARKER_DEFAULT;
	
	public transient ObjectCluster mLastProcessedObjectCluster = null;
	public List<ShimmerLogDetails> mListofLogs = new ArrayList<ShimmerLogDetails>();

	protected long mEnabledSensors = (long)0;												// This stores the enabled sensors
	protected long mDerivedSensors = (long)0;	
	public static boolean mEnableDerivedSensors = true;
	
	protected String mComPort = "";
	public transient CommsProtocolRadio mCommsProtocolRadio = null;
	public BT_STATE mBluetoothRadioState = BT_STATE.DISCONNECTED;
	public DOCK_STATE mDockState = DOCK_STATE.UNDOCKED;
	private boolean mUpdateOnlyWhenStateChanges=false;
	
	//TODO:
	public enum DOCK_STATE{
		DOCKED("Docked"),
		UNDOCKED("Undocked");
//		RECORDING("Recording");
		
	    private final String text;

	    /**
	     * @param text
	     */
	    private DOCK_STATE(final String text) {
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
	
	public enum SD_STATE{
		LOGGING("Logging"),
		NOT_LOGGING("Not_Logging");
//		RECORDING("Recording");
		
	    private final String text;

	    /**
	     * @param text
	     */
	    private SD_STATE(final String text) {
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
	
	public enum SENSING_STATE{
		SENSING("Sensing"),
		NOT_SENSING("Not Sensing");
//		RECORDING("Recording");
		
	    private final String text;

	    /**
	     * @param text
	     */
	    private SENSING_STATE(final String text) {
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
	
	protected int mInternalExpPower=-1;													// This shows whether the internal exp power is enabled.
	
	protected DataProcessingInterface mDataProcessing;

	public boolean mVerboseMode = true;
//	public static TreeMap<Integer,String> mMapOfErrorCodes = new TreeMap<Integer,String>();

	protected FIXED_SHIMMER_CONFIG_MODE mFixedShimmerConfigMode = FIXED_SHIMMER_CONFIG_MODE.NONE;
	protected LinkedHashMap<String, Object> mFixedShimmerConfigMap = null;

	protected boolean mAutoStartStreaming = false;
	public boolean mIsTrialDetailsStoredOnDevice = true;
	
	public static final int RECONNECT_ATTEMPTS_MAX = 3;
	public int mNumConnectionAttempts = -1;

	private static final List<AlgorithmLoaderInterface> OPEN_SOURCE_ALGORITHMS = Arrays.asList(
			new GyroOnTheFlyCalLoader(),
			new OrientationModule6DOFLoader(), 
			new OrientationModule9DOFLoader(),
			new GyroOnTheFlyCalLoaderVerisense());

	public static final class DatabaseConfigHandle{
		public static final String TRIAL_NAME = "Trial_Name";
		
		public static final String SHIMMER_NAME = "Shimmer_Name";
		
		public static final String SAMPLE_RATE = "Sample_Rate";
		public static final String ENABLE_SENSORS = "Enable_Sensors";
		public static final String DERIVED_SENSORS = "Derived_Sensors";
		
		public static final String USER_BUTTON = "User_Button";
		public static final String RTC_SOURCE = "Rtc_Source"; 
		public static final String SYNC_CONFIG = "Sync";
		public static final String MASTER_CONFIG = "Master";
		public static final String SINGLE_TOUCH_START = "Single_Touch_Start";
		public static final String TXCO = "Txco";
		public static final String BROADCAST_INTERVAL = "Broadcast_Interval";
		public static final String BAUD_RATE = "Baud_Rate";
		public static final String TRIAL_ID = "Trial_Id";
		public static final String N_SHIMMER = "NShimmer";
		public static final String LOW_POWER_AUTOSTOP = "Low_Power_Autostop";
		
		public static final String SHIMMER_VERSION = "Shimmer_Version";
		public static final String FW_VERSION = "FW_ID";
		public static final String FW_VERSION_MAJOR = "FW_Version_Major";
		public static final String FW_VERSION_MINOR = "FW_Version_Minor";
		public static final String FW_VERSION_INTERNAL = "FW_Version_Internal";
		
		public static final String INITIAL_TIMESTAMP = "Initial_TimeStamp";
		public static final String EXP_BOARD_ID = "Exp_Board_Id";
		public static final String EXP_BOARD_REV = "Exp_Board_Rev";
		public static final String EXP_BOARD_REV_SPEC = "Exp_Board_Rev_Special";
		
		public static final String CONFIG_TIME = "Config_Time";
		
		
		//Shimmer3 specific
		public static final String EXP_PWR = "Exp_PWR";
		public static final String REAL_TIME_CLOCK_DIFFERENCE = "RTC_Difference";
	}

	// --------------- Abstract Methods Start --------------------------
	
	/**
	 * Performs a deep copy of the parent class by Serializing
	 * @return ShimmerDevice the deep copy of the current ShimmerDevice (should
	 *         be substituted by the extended ShimmerDevice instance)
	 * @see java.io.Serializable
	 */
	public abstract ShimmerDevice deepClone();

	// Device sensor map related
	public abstract void sensorAndConfigMapsCreate();
	/**
	 * @param object in some cases additional details might be required for building the packer format, e.g. inquiry response
	 */
	protected abstract void interpretDataPacketFormat(Object object,COMMUNICATION_TYPE commType);
	public abstract void configBytesParse(byte[] configBytes, COMMUNICATION_TYPE commType);
	public abstract byte[] configBytesGenerate(boolean generateForWritingToShimmer, COMMUNICATION_TYPE commType);
	public abstract void createConfigBytesLayout();
	protected abstract void dataHandler(ObjectCluster ojc);

	// --------------- Abstract Methods End --------------------------

	/**
	 * Constructor for this class
	 */
	public ShimmerDevice(){
		setThreadName(this.getClass().getSimpleName());
		setupDataProcessing();
	}
	
	// --------------- Get/Set Methods Start --------------------------
	
	public void setShimmerVersionObject(ShimmerVerObject sVO) {
		mShimmerVerObject = sVO;
	}
	
	public void setShimmerVersionObjectAndCreateSensorMap(ShimmerVerObject sVO) {
		setShimmerVersionObject(sVO);
		sensorAndConfigMapsCreate();
	}

	/** setShimmerVerionObject should be used instead
	 * @param hardwareVersion the mHardwareVersion to set
	 */
	public void setHardwareVersion(int hardwareVersion) {
		ShimmerVerObject sVO = new ShimmerVerObject(hardwareVersion, getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal());
		setShimmerVersionObject(sVO);
	}

	public void setHardwareVersionAndCreateSensorMaps(int hardwareVersion) {
		ShimmerVerObject sVO = new ShimmerVerObject(hardwareVersion, getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal());
		setShimmerVersionObjectAndCreateSensorMap(sVO);
	}

	public void clearShimmerVersionObject() {
		setShimmerVersionObject(new ShimmerVerObject());
	}
	
	public void clearShimmerVersionObjectAndCreateSensorMaps() {
//		clearShimmerVersionObject();
//		sensorAndConfigMapsCreate();
		
		//Below is the same as above
		setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject());
	}
	
	
	//TODO draft code
	private void updateSensorDetailsWithCommsTypes() {
		if(mMapOfSensorClasses!=null){
			for(AbstractSensor absensorSensor:mMapOfSensorClasses.values()){
				absensorSensor.updateSensorDetailsWithCommsTypes(mListOfAvailableCommunicationTypes);
			}
		}
	}
	
	//TODO draft code
	private void updateSamplingRatesMapWithCommsTypes() {
		//Add if not there
		for(COMMUNICATION_TYPE commType:mListOfAvailableCommunicationTypes){
			if(!mMapOfSamplingRatesShimmer.containsKey(commType)){
				mMapOfSamplingRatesShimmer.put(commType, getSamplingRateShimmer());
			}
		}
		
		//Remove if not supported
		Iterator<COMMUNICATION_TYPE> iterator = mMapOfSamplingRatesShimmer.keySet().iterator();
		while(iterator.hasNext()){
			COMMUNICATION_TYPE commType = iterator.next();
			if(!mListOfAvailableCommunicationTypes.contains(commType)){
				iterator.remove();
			}
		}

	}


	
	public void generateSensorAndParserMaps(){
		//Update sensorMap from all supported sensors that were generated in mMapOfSensorClasses
		generateSensorMap();
		//Create Parser from enabled sensors
		generateParserMap();
		generateConfigOptionsMap();
		generateSensorGroupingMap();
	}

	private void generateSensorMap() {
		mSensorMap = new LinkedHashMap<Integer, SensorDetails>();
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
			mSensorMap.putAll(abstractSensor.mSensorMap);
		}
	}

	public void generateParserMap() {
		//TODO switch ParserMap value to list of sensorIds as below?
//		HashMap<COMMUNICATION_TYPE, List<Integer>> parseMap = new HashMap<COMMUNICATION_TYPE, List<Integer>>(); 

		mParserMap = new HashMap<COMMUNICATION_TYPE, TreeMap<Integer, SensorDetails>>();
//		for(COMMUNICATION_TYPE commType:COMMUNICATION_TYPE.values()){
		for(COMMUNICATION_TYPE commType:mListOfAvailableCommunicationTypes){
			for(Integer sensorId:mSensorMap.keySet()){
				SensorDetails sensorDetails = getSensorDetails(sensorId);
				if(sensorDetails.isEnabled(commType)){
					TreeMap<Integer, SensorDetails> parserMapPerComm = mParserMap.get(commType);
					if(parserMapPerComm==null){
						parserMapPerComm = new TreeMap<Integer, SensorDetails>();
						mParserMap.put(commType, parserMapPerComm);
					}
					parserMapPerComm.put(sensorId, sensorDetails);
				}
			}
		}
		
		updateExpectedDataPacketSize();
		//Debugging
//		printSensorParserAndAlgoMaps();
	}

	public void generateConfigOptionsMap() {
		mConfigOptionsMapSensors = new HashMap<String, ConfigOptionDetailsSensor>();
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
			HashMap<String, ConfigOptionDetailsSensor> configOptionsMapPerSensor = abstractSensor.getConfigOptionsMap();
				
			if(configOptionsMapPerSensor!=null && configOptionsMapPerSensor.keySet().size()>0){
//				mConfigOptionsMap.putAll(configOptionsMapPerSensor);
				loadCompatibleConfigOptionGroupEntries(configOptionsMapPerSensor);
				
				// taking out duplicates for orientation algorithm config options 
//					for(String s: configOptionsMapPerSensor.keySet()){
//						if(mConfigOptionsMap.containsKey(s)){
//							//do nothing 
//						}else{
//							mConfigOptionsMap.put(s,configOptionsMapPerSensor.get(s));
//						}				
//					
				
			//	}
			}	
		}
	}
	
	//New approach - should not be run when using ShimmerObject
	public void generateSensorGroupingMap(){
		mSensorGroupingMap = new TreeMap<Integer, SensorGroupingDetails>(); 
		
		Iterator<AbstractSensor> iteratorSensors = mMapOfSensorClasses.values().iterator();
		while(iteratorSensors.hasNext()){
			AbstractSensor sensor = iteratorSensors.next();
//			mSensorGroupingMap.putAll(sensor.getSensorGroupingMap());
			loadCompatibleSensorGroupEntries(sensor.getSensorGroupingMap());
		}
	}

	protected void loadCompatibleSensorGroupEntries(Map<Integer, SensorGroupingDetails> groupMapRef) {
		if(groupMapRef!=null){
			Iterator<Entry<Integer, SensorGroupingDetails>> iteratorSensorGroupMap = groupMapRef.entrySet().iterator();
			while(iteratorSensorGroupMap.hasNext()){
				Entry<Integer, SensorGroupingDetails> entry = iteratorSensorGroupMap.next();
				if(isVerCompatibleWithAnyOf(entry.getValue().mListOfCompatibleVersionInfo)){
					mSensorGroupingMap.put(entry.getKey(), entry.getValue());
				}
			}
		}
	}

	protected void loadCompatibleConfigOptionGroupEntries(Map<String, ConfigOptionDetailsSensor> configOptionMapRef) {
		if(configOptionMapRef!=null){
			Iterator<Entry<String, ConfigOptionDetailsSensor>> iteratorConfigOptionMap = configOptionMapRef.entrySet().iterator();
			while(iteratorConfigOptionMap.hasNext()){
				Entry<String, ConfigOptionDetailsSensor> entry = iteratorConfigOptionMap.next();
				if(isVerCompatibleWithAnyOf(entry.getValue().mListOfCompatibleVersionInfo)){
					mConfigOptionsMapSensors.put(entry.getKey(), entry.getValue());
				} else {
//					System.out.println("Not compatible:" + entry.getKey());
				}
			}
		}
	}

	/**	
	 * @return the mSensorGroupingMap
	 */
	public TreeMap<Integer, SensorGroupingDetails> getSensorGroupingMap() {
		return mSensorGroupingMap;
	}

	/** 
	 * @return the mConfigOptionsMap
	 */
	public Map<String, ConfigOptionDetailsSensor> getConfigOptionsMap() {
		return mConfigOptionsMapSensors;
	}
	
	/** 
	 * @return the mConfigOptionsMap
	 */
	public Map<String, ConfigOptionDetails> getConfigOptionsMapAlorithms() {
		return mConfigOptionsMapAlgorithms;
	}
	
	public void setBattStatusDetails(ShimmerBattStatusDetails shimmerBattStatusDetails) {
		mShimmerBattStatusDetails = shimmerBattStatusDetails;
	}
	
	public void clearBattStatusDetails() {
		setBattStatusDetails(new ShimmerBattStatusDetails());
	}

	/**
	 * @param macIdFromUart the mMacIdFromUart to set
	 */
	public void setMacIdFromUart(String macIdFromUart) {
		this.mMacIdFromUart = macIdFromUart;
		updateThreadName();
	}
	
	/**
	 * @param shimmerUserAssignedName the mShimmerUserAssignedName to set
	 */
	public void setShimmerUserAssignedName(String shimmerUserAssignedName) {
		if(!shimmerUserAssignedName.isEmpty()){
			//Remove any invalid characters
			shimmerUserAssignedName = shimmerUserAssignedName.replaceAll(INVALID_TRIAL_NAME_CHAR, "");

			//Don't allow the first char to be numeric - causes problems with MATLAB variable names
			if(UtilShimmer.isNumeric("" + shimmerUserAssignedName.charAt(0))){
				shimmerUserAssignedName = "S" + shimmerUserAssignedName; 
			}
		}
		else{
			shimmerUserAssignedName = ShimmerObject.DEFAULT_SHIMMER_NAME + "_" + this.getMacIdFromUartParsed();
		}

		//Limit the name to 12 Char
		if(shimmerUserAssignedName.length()>12) {
			setShimmerUserAssignedNameNoLengthCheck(shimmerUserAssignedName.substring(0, 12));
		}
		else { 
			setShimmerUserAssignedNameNoLengthCheck(shimmerUserAssignedName);
		}
	}
	
	public void setShimmerUserAssignedNameNoLengthCheck(String shimmerUserAssignedName) {
		String nameToSet = shimmerUserAssignedName.replace(" ", "_");
		mShimmerUserAssignedName = nameToSet; 
	}
	
	public void setShimmerUserAssignedNameWithMac(String shimmerUserAssignedName) {
		//Don't allow the first char to be numeric - causes problems with MATLAB variable names
		if(shimmerUserAssignedName.length()==0 || UtilShimmer.isNumeric("" + shimmerUserAssignedName.charAt(0))){
			shimmerUserAssignedName = "S" + shimmerUserAssignedName; 
		}
		
		//Remove any invalid characters
		shimmerUserAssignedName = shimmerUserAssignedName.replaceAll(INVALID_TRIAL_NAME_CHAR, "");
			
		//Limit the name to 12 Char
		String addition = "_" + getMacIdParsed();
		if((shimmerUserAssignedName.length()+addition.length())>12) {
			setShimmerUserAssignedNameNoLengthCheck(shimmerUserAssignedName.substring(0, (12-addition.length())) + addition);
		}
		else { 
			setShimmerUserAssignedNameNoLengthCheck(shimmerUserAssignedName + addition);
		}
	}
	
	public void setLastReadRealTimeClockValue(long time) {
		mShimmerLastReadRtcValueMilliSecs = time;
		mShimmerLastReadRtcValueParsed = UtilShimmer.fromMilToDateExcelCompatible(Long.toString(time), false);
	}
	
	//TODO test to see is this is working
	public boolean isReadRealTimeClockSet(){
		//Picking the year 01/01/2015 as an arbitrary number 
		return mShimmerLastReadRtcValueMilliSecs>1420070400000L? true:false;
	}

	/**
	 * @param shimmerInfoMemBytes the shimmerInfoMemBytes to set
	 */
	public void setShimmerInfoMemBytes(byte[] shimmerInfoMemBytes) {
		configBytesParse(shimmerInfoMemBytes);
	}
	
	//------------------- Event marker code Start -------------------------------
	/**
	 * @param eventCode
	 * @param eventType //2 is the code for the pulse event, 1 is the code for the toggle event
	 */
	public void setEventTriggered(long eventCode, int eventType){
		mEventMarkersCodeLast = eventCode;
		
		if(mEventMarkers > 0){
			mEventMarkers = mEventMarkers + eventCode; 
		}
		else{
			mEventMarkers = mEventMarkers + eventCode + (-EVENT_MARKER_DEFAULT); 
		}
		
		//TOGGLE(1),
		//PULSE(2);
		//event type is defined in UtilDb, defined it here too
		if(eventType == 2){ 
			mEventMarkersIsPulse = true;
		}
	}
	
	public void setEventUntrigger(long eventCode){
		mEventMarkers = mEventMarkers - eventCode;
		if(mEventMarkers == 0){
			mEventMarkers = EVENT_MARKER_DEFAULT; // using -1 as the default event marker value as as a value of 0 was hanging the plots and the software
		}
	}
	
	public void untriggerEventIfLastOneWasPulse(){
		if(mEventMarkersIsPulse){
			mEventMarkersIsPulse = false;
			setEventUntrigger(mEventMarkersCodeLast);
		}
	}
	
	public void processEventMarkerCh(ObjectCluster objectCluster) {
		if(mEnableProcessMarkers){
			//event marker channel
			//		objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EVENT_MARKER,CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.NO_UNITS, mEventMarkers);
			objectCluster.addCalDataToMap(SensorShimmerClock.channelEventMarker, mEventMarkers);
			untriggerEventIfLastOneWasPulse();
		}
	}

	//------------------- Event marker code End -------------------------------
	
	
	//------------------- Communication route related Start -------------------------------
	/** If the device instance is already created use this to add a dock communication type to the instance
	 * @param dockID
	 * @param slotNumber
	 */
	public void addDOCKCoummnicationRoute(String dockId, int slotNumber) {
		setDockInfo(dockId, slotNumber);
		addCommunicationRoute(COMMUNICATION_TYPE.DOCK);
	}
	
	public void clearDockInfo(String dockId, int slotNumber){
		setDockInfo(dockId, slotNumber);
//		setDockInfo(DEFAULT_DOCKID, DEFAULT_SLOTNUMBER);
	}
	
	public void setDockInfo(String dockId, int slotNumber){
		mDockID = dockId;
		parseDockType();
		mSlotNumber = slotNumber;
		mUniqueID = mDockID + "." + String.format("%02d", mSlotNumber);
	}

	public void addCommunicationRoute(COMMUNICATION_TYPE communicationType) {
		if(!mListOfAvailableCommunicationTypes.contains(communicationType)){
			mListOfAvailableCommunicationTypes.add(communicationType);
		}
//		Collections.sort(mListOfAvailableCommunicationTypes);
		
		if(communicationType==COMMUNICATION_TYPE.DOCK){
			if(mDockID.contains(DEVICE_TYPE.SHIMMER3R.toString())) {
				setIsUsbPluggedIn(true);
			}else {
				setIsDocked(true);
			}
		}
		
		//TODO temp here -> check if the best place for it
		updateSensorDetailsWithCommsTypes();
		updateSamplingRatesMapWithCommsTypes();
	}

	public void setCommunicationRoute(COMMUNICATION_TYPE communicationType) {
		mListOfAvailableCommunicationTypes.clear();
		addCommunicationRoute(communicationType);
	}

	public void setCommunicationRoutes(List<COMMUNICATION_TYPE> communicationTypes) {
		mListOfAvailableCommunicationTypes.clear();
		for(COMMUNICATION_TYPE communicationType:communicationTypes) {
			addCommunicationRoute(communicationType);
		}
	}

	public void removeCommunicationRoute(COMMUNICATION_TYPE communicationType) {
		if(mListOfAvailableCommunicationTypes.contains(communicationType)){
			mListOfAvailableCommunicationTypes.remove(communicationType);
		}
//		Collections.sort(mListOfAvailableCommunicationTypes);
		
		if(communicationType==COMMUNICATION_TYPE.DOCK){			
			setIsUsbPluggedIn(false);
			setIsDocked(false);
			setFirstDockRead();
			clearDockInfo(mDockID, mSlotNumber);
		}
		
		//TODO temp here -> check if the best place for it
		updateSensorDetailsWithCommsTypes();
		updateSamplingRatesMapWithCommsTypes();
	}
	
	public List<COMMUNICATION_TYPE> getCommunicationRoutes() {
		return mListOfAvailableCommunicationTypes;
	}
	
	public boolean isCommunicationRouteAvailable(COMMUNICATION_TYPE commType) {
		return mListOfAvailableCommunicationTypes.contains(commType);
	}

	//------------------- Communication route related End -------------------------------
	
	//------------------- SD card related Start -------------------------------
	public boolean isFirstSdAccess() {
		return mShimmerSDCardDetails.isFirstSdAccess();
	}

	public void setFirstSdAccess(boolean state) {
		this.mShimmerSDCardDetails.setFirstSdAccess(state);
	}

	public boolean isSDErrorOrNotPresent() {
		return (mShimmerSDCardDetails.isSDError() || !mShimmerSDCardDetails.isSDPresent());
	}

	public boolean isSDError() {
		return mShimmerSDCardDetails.isSDError();
	}

	public void setIsSDError(boolean state) {
		this.mShimmerSDCardDetails.setIsSDError(state);
	}

	public boolean isSDPresent() {
		return this.mShimmerSDCardDetails.isSDPresent();
	}

	public void setIsSDPresent(boolean state) {
		this.mShimmerSDCardDetails.setIsSDPresent(state);
	}

	public boolean isSDLogging(){
		return mShimmerSDCardDetails.isSDLogging();
	}	

	public void setIsSDLogging(boolean state){
		mShimmerSDCardDetails.setIsSDLogging(state);
	}	

	public void setShimmerDriveInfo(ShimmerSDCardDetails shimmerSDCardDetails) {
		mShimmerSDCardDetails = shimmerSDCardDetails;
	}
	
	public ShimmerSDCardDetails getShimmerSDCardDetails(){
		return mShimmerSDCardDetails;
	}
	
	public long getDriveTotalSpace() {
		return mShimmerSDCardDetails.getDriveTotalSpace();
	}

	public String getDriveUsedSpaceParsed() {
		return mShimmerSDCardDetails.getDriveUsedSpaceParsed();
	}

	public long getDriveUsedSpace() {
		return mShimmerSDCardDetails.getDriveUsedSpace();
	}

	public long getDriveUsedSpaceKB() {
		return mShimmerSDCardDetails.getDriveUsedSpaceKB();
	}

	public String getDriveTotalSpaceParsed() {
		return mShimmerSDCardDetails.getDriveTotalSpaceParsed();
	}

	public long getDriveUsableSpace() {
		return mShimmerSDCardDetails.getDriveUsableSpace();
	}

	public long getDriveFreeSpace() {
		return mShimmerSDCardDetails.getDriveFreeSpace();
	}
	
	public void setDriveUsedSpaceKB(long driveUsedSpaceKB) {
		mShimmerSDCardDetails.setDriveUsedSpaceKB(driveUsedSpaceKB);
	}

	public void setDriveUsedSpace(long driveUsedSpace) {
		mShimmerSDCardDetails.setDriveUsedSpaceBytes(driveUsedSpace);
	}

	public String getDriveCapacityParsed() {
		String strPending = STRING_CONSTANT_PENDING;
		
		if(!isSupportedSdCardAccess()){
			return STRING_CONSTANT_UNKNOWN;
		}

		if(isSDErrorOrNotPresent()){
			return STRING_CONSTANT_SD_ERROR;
		}
		else {
			String driveUsedSpaceParsed = getDriveUsedSpaceParsed();
			if (!driveUsedSpaceParsed.isEmpty()) {
				// The extra check below was added for NeuroLynQ in order to
				// increase the granularity of the reported used space while
				// logging. 
				if(this.isShimmerGenGq() && isSDLogging()){
//					driveUsedSpaceParsed = Long.toString(getDriveUsedSpace()) + " B";
					driveUsedSpaceParsed = Long.toString(getDriveUsedSpaceKB()) + " KB";
				}
				
				return (driveUsedSpaceParsed + " / " + getDriveTotalSpaceParsed());
			}
			else{
				return (isHaveAttemptedToRead() ? STRING_CONSTANT_UNKNOWN : (isFirstSdAccess() ? strPending: STRING_CONSTANT_UNKNOWN));
			}
		}
	}
	
    public boolean isSDSpaceIncreasing() {
    	return mShimmerSDCardDetails.isSDSpaceIncreasing();
    }

	//------------------- SD card related End -------------------------------

	
	public boolean isConfigurationReadSuccess(){
		return mConfigurationReadSuccess;
	}
	
	public void setConfigurationReadSuccess(boolean configurationReadSuccess){
		mConfigurationReadSuccess = configurationReadSuccess;
	}
	
	public ShimmerVerObject getShimmerVerObject() {
		return mShimmerVerObject;
	}

	/**
	 * @return the mShimmerVersion
	 */
	public int getHardwareVersion() {
		return mShimmerVerObject.mHardwareVersion;
	}

	/**
	 * Get the FW Identifier. It is equal to 3 when LogAndStream, and equal to 1 when BTStream. 
	 * @return The FW identifier
	 */
	public int getFirmwareIdentifier() {
		return mShimmerVerObject.mFirmwareIdentifier;
	}
	
	public int getFirmwareVersionMajor(){
		return mShimmerVerObject.mFirmwareVersionMajor;
	}
	
	public int getFirmwareVersionMinor(){
		return mShimmerVerObject.mFirmwareVersionMinor;
	}

	public int getFirmwareVersionInternal(){
		return mShimmerVerObject.mFirmwareVersionInternal;
	}
	
	
	public int getFirmwareVersionCode(){
		return mShimmerVerObject.mFirmwareVersionCode;
	}
	
	public String getFirmwareVersionParsed(){
		return mShimmerVerObject.mFirmwareVersionParsed;
	}
	

	public String getHardwareVersionParsed(){
		return mShimmerVerObject.getHardwareVersionParsed();
	}

	

	public ExpansionBoardDetails getExpansionBoardDetails(){
		return mExpansionBoardDetails;
	}

	public int getExpansionBoardId(){
		return mExpansionBoardDetails.mExpansionBoardId;
	}
	
	public int getExpansionBoardRev(){
		return mExpansionBoardDetails.mExpansionBoardRev;
	}
	
	public int getExpansionBoardRevSpecial(){
		return mExpansionBoardDetails.mExpansionBoardRevSpecial;
	}
	
	/**
	 * @return the mExpansionBoardParsed
	 */
	public String getExpansionBoardParsed() {
		return mExpansionBoardDetails.getExpansionBoardParsed();
	}
	
	public String getExpansionBoardParsedWithVer() {
		return mExpansionBoardDetails.getExpansionBoardParsedWithVer();
	}

	public String getExpansionBoardVerParsed() {
		return mExpansionBoardDetails.getBoardVerString();
	}

	public void clearExpansionBoardDetails(){
		mExpansionBoardDetails = new ExpansionBoardDetails();
	}

	public void setExpansionBoardDetailsAndCreateSensorMap(ExpansionBoardDetails eBD){
		setExpansionBoardDetails(eBD);
		sensorAndConfigMapsCreate();
	}
	
	public void setExpansionBoardDetails(ExpansionBoardDetails eBD){
		mExpansionBoardDetails  = eBD;
	}
	
	
	
	public ShimmerBattStatusDetails getBattStatusDetails() {
		return mShimmerBattStatusDetails;
	}

	/**
	 * @return the mChargingState
	 */
	public String getChargingStateParsed() {
		return mShimmerBattStatusDetails.getChargingStatusParsed();
	}

	/**
	 * @return the mBattVoltage
	 */
	public String getBattVoltage() {
		return mShimmerBattStatusDetails.getBattVoltageParsed();
	}

	/**
	 * @return the mEstimatedChargePercentageParsed
	 */
	public String getEstimatedChargePercentageParsed() {
		return mShimmerBattStatusDetails.getEstimatedChargePercentageParsed();
	}
	
	/**
	 * @return the mEstimatedChargePercentage
	 */
	public Double getEstimatedChargePercentage() {
		return mShimmerBattStatusDetails.getEstimatedChargePercentage();
	}
	
	public BATTERY_LEVEL getEstimatedBatteryLevel() {
		return mShimmerBattStatusDetails.getEstimatedBatteryLevel();
	}
	
	/**
	 * @param docked the mDocked to set
	 */
	public boolean setIsDocked(boolean docked) {
		boolean changed=false;
		if (mIsDocked!=docked){
			changed = true;
		}
		mIsDocked = docked;
		if (mIsDocked){
			mDockState = DOCK_STATE.DOCKED;
		}else {
			mDockState = DOCK_STATE.UNDOCKED;
		}
		if(changed){
			stateHandler(mDockState);
		}
		if(!mUpdateOnlyWhenStateChanges){
			return true;
		}
		return changed;
	}
	
	public boolean setIsUsbPluggedIn(boolean usbPluggedIn) {
		boolean changed=false;
		if (mIsUsbPluggedIn!=usbPluggedIn){
			changed = true;
		}
		mIsUsbPluggedIn = usbPluggedIn;
		return changed;

	}

	public void stateHandler(Object obj){
		
	}
	
	/**
	 * @return the mDocked
	 */
	public boolean isDocked() {
		return mIsDocked;
	}
	
	public boolean isUsbPluggedIn() {
		return mIsUsbPluggedIn;
	}
	
	public boolean isUsbDfuMode(){
		return mIsUsbDfuMode;
	}

	public void setUsbDfuMode(boolean state) {
		mIsUsbDfuMode = state;
	}

	public void setIsConnected(boolean state) {
		mIsConnected = state;
	}

	/**
	 * @return the mIsConnected
	 */
	public boolean isConnected() {
		return mIsConnected;
	}


    /** Returns true if device is streaming (Bluetooth)
     * @return
     */
    public boolean isStreaming(){
    	return mIsStreaming;
    }

	public void setIsStreaming(boolean state) {
		mIsStreaming = state;
	}

	/**Only used for LogAndStream
	 * @return
	 */
	public boolean isSensing(){
		return (mIsSensing || isSDLogging() || isStreaming());
	}
	
	public void setIsSensing(boolean state) {
		mIsSensing = state;
	}
	
	/**
	 * @param isInitialized the mSuccessfullyInitialized to set
	 */
	public void setIsInitialised(boolean isInitialized) {
		mIsInitialised = isInitialized;
	}
	
	/**
	 * @return the mIsInitialized
	 */
	public boolean isInitialised() {
		return mIsInitialised;
	}
	
	/**
	 * @return the mHaveAttemptedToRead
	 */
	public boolean isHaveAttemptedToRead() {
		return haveAttemptedToRead();
	}

	/**
	 * @return the mHaveAttemptedToRead
	 */
	public boolean haveAttemptedToRead() {
		return mHaveAttemptedToReadConfig;
	}
	
	public boolean isShimmerError(){
		if(isSDErrorOrNotPresent()){
			return true;
		}
		return false;
	}
	
	/**
	 * @param haveAttemptedToRead the mHaveAttemptedToRead to set
	 */
	public void setHaveAttemptedToReadConfig(boolean haveAttemptedToRead) {
		mHaveAttemptedToReadConfig = haveAttemptedToRead;
	}

	/**
	 * @return the mMacIdFromUart
	 */
	public String getMacIdFromUart() {
		return mMacIdFromUart;
	}

	/**
	 * @return the mMacIdFromUartParsed
	 */
	public String getMacIdFromUartParsed() {
		if (mMacIdFromUart!=null){
			int length = mMacIdFromUart.length();
			if(length>=12) {
				//			return this.mMacIdFromUart.substring(8, 12);
				return this.mMacIdFromUart.substring(length-4, length);
			}		
		}
		return "0000";
	}

	/**
	 * @return the mShimmerUserAssignedName
	 */
	public String getShimmerUserAssignedName() {
		return mShimmerUserAssignedName;
	}
	
	/**
	 * @return the mShimmerInfoMemBytes
	 */
	public byte[] getShimmerConfigBytes() {
		return mConfigBytes;
	}

	public byte[] getShimmerInfoMemBytesOriginal() {
		return mInfoMemBytesOriginal;
	}

	public HashMap<Integer, String> getMapOfConfigByteDescriptions() {
		if(mConfigByteLayout!=null){
			return mConfigByteLayout.getMapOfByteDescriptions();
		}
		return null;
	}
	
	
	/** Use getPacketReceptionRateOverall() instead
	 * @return
	 */
	@Deprecated
	public double getPacketReceptionRate(){
		return getPacketReceptionRateOverall();
	}

	public double getPacketReceptionRateOverall(){
		return mPacketReceptionRateOverall;
	}

	public void resetEventMarkerValuetoDefault(){
		mEventMarkers = EVENT_MARKER_DEFAULT;
	}
	
	protected void resetShimmerClock() {
		AbstractSensor abstractSensor = getSensorClass(AbstractSensor.SENSORS.CLOCK);
		if(abstractSensor!=null && abstractSensor instanceof SensorShimmerClock){
			SensorShimmerClock shimmerClock = (SensorShimmerClock)abstractSensor;
			shimmerClock.resetShimmerClock();
		}
	}

	protected void resetPacketLossVariables() {
		mPacketLossCountPerTrial = 0;
		resetPacketReceptionOverallVariables();
		resetPacketReceptionCurrentVariables();
	}
	
	public void incrementPacketsReceivedCounters(){
		incrementPacketReceivedCountCurrent();
		incrementPacketReceivedCountOverall();
	}

	public void setPacketExpectedCountOverall(long packetReceivedCountOverall) {
		mPacketExpectedCountOverall = packetReceivedCountOverall;
	}
	
	public long getPacketReceivedCountOverall() {
		return mPacketReceivedCountOverall;
	}
	
	private void incrementPacketReceivedCountOverall() {
		mPacketReceivedCountOverall += 1;
	}

	public void resetPacketReceptionOverallVariables(){
		mPacketExpectedCountOverall = 0;
		mPacketReceivedCountOverall = 0;
		setPacketReceptionRateOverall(DEFAULT_RECEPTION_RATE);
	}

	public void setPacketReceptionRateOverall(double packetReceptionRateTrial){
		mPacketReceptionRateOverall = UtilShimmer.nudgeDouble(packetReceptionRateTrial, 0.0, 100.0);
//		mPacketReceptionRateOverall = packetReceptionRateTrial;
	}


	public long getPacketReceivedCountCurrent() {
		return mPacketReceivedCountCurrent;
	}

	public void incrementPacketReceivedCountCurrent() {
		mPacketReceivedCountCurrent += 1;
	}

	public void incrementPacketExpectedCountCurrent() {
		mPacketExpectedCountCurrent += 1;
	}

	public void resetPacketReceptionCurrentCounters(){
		mPacketExpectedCountCurrent = 0;
		mPacketReceivedCountCurrent = 0;
	}

	public void resetPacketReceptionCurrentVariables(){
		resetPacketReceptionCurrentCounters();
		setPacketReceptionRateCurrent(DEFAULT_RECEPTION_RATE); 
	}

	public void setPacketReceptionRateCurrent(double packetReceptionRateCurrent){
		// Need to keep in range because the Bluetooth data is processed in
		// blocks and not necessarily at a fixed number of packets per second.
		// Probably the same for other communication protocols
		mPacketReceptionRateCurrent = UtilShimmer.nudgeDouble(packetReceptionRateCurrent, 0.0, 100.0);
//		mPacketReceptionRateCurrent = packetReceptionRateCurrent;
	}

	public double getPacketReceptionRateCurrent(){
		return mPacketReceptionRateCurrent;
	}

	
	public void setPacketLossCountPerTrial(long packetLossCountPerTrial){
		mPacketLossCountPerTrial = packetLossCountPerTrial;
	}

	public long getPacketLossCountPerTrial(){
		return mPacketLossCountPerTrial;
	}

	
	
	 /**
	 * @return the mConfigTime
	 */
	public long getConfigTime() {
		return mConfigTime;
	}

	/** Returns the config time by converting the long to a String
	 * @return
	 */
	public String getConfigTimeLongString() {
		return Long.toString(getConfigTime());
	}

	 /**
	 * @return the mConfigTime in a parsed String format (yyyy-MM-dd hh:MM:ss)
	 */
	public String getConfigTimeParsed() {
		return UtilShimmer.convertSecondsToDateString(mConfigTime);
	}

	public String getConfigTimeExcelCompatible() {
		return UtilShimmer.fromMilToDateExcelCompatible(Long.toString(mConfigTime*1000L), false);
	}

	
	


	// --------------- Get/Set Methods End --------------------------

	public ConfigByteLayout getConfigByteLayout(){
		createInfoMemLayoutObjectIfNeeded();
		return mConfigByteLayout;
	}
	
	 /**
	 * @return the InfoMem byte size. HW and FW version needs to be set first for this to operate correctly.
	 */
	public int getExpectedInfoMemByteLength() {
		createInfoMemLayoutObjectIfNeeded();
		return mConfigByteLayout.mInfoMemSize;
	}
	
	public void createInfoMemLayoutObjectIfNeeded(){
		boolean create = false;
		if(mConfigByteLayout==null){
			create = true;
		}
		else {
			if(mConfigByteLayout.isDifferent(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal())){
				create = true;
			}
		}
		
		if(create){
			createConfigBytesLayout();
		}
	}
	
	protected void parseDockType(){
		if(mDockID.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.BASICDOCK.getLabel())){
			mDockType = DEVICE_TYPE.BASICDOCK;
			//Below to ensure Shimmer shows as unknown in BasicDock rather then pending
			setHaveAttemptedToReadConfig(true);
		}
		else if(mDockID.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.BASE15.getLabel())){
			mDockType = DEVICE_TYPE.BASE15;
		}
		else if(mDockID.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.BASE6.getLabel())){
			mDockType = DEVICE_TYPE.BASE6;
		}
		else if(mDockID.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.SHIMMER3R.getLabel())){
			mDockType = DEVICE_TYPE.SHIMMER3R;
		} 
		else {
			mDockType = DEVICE_TYPE.UNKOWN;
		}
	}
	
	public ObjectCluster buildMsg(byte[] dataPacketFormat, byte[] packetByteArray, COMMUNICATION_TYPE commType, boolean isTimeSyncEnabled, double pcTimestampMs){
		interpretDataPacketFormat(dataPacketFormat, commType);
		return buildMsg(packetByteArray, commType, isTimeSyncEnabled, pcTimestampMs);
	}
	
	/** The packet format can be changed by calling interpretpacketformat
	 * @param newPacket
	 * @param commType
	 * @return
	 */
	public ObjectCluster buildMsg(byte[] newPacket, COMMUNICATION_TYPE commType, boolean isTimeSyncEnabled, double pcTimestampMs){
		boolean debug = false;
		
		if(debug)
			consolePrintLn("Packet: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(newPacket));
		
		ObjectCluster ojc = new ObjectCluster(mShimmerUserAssignedName, getMacId());
		ojc.mRawData = newPacket;
		ojc.createArrayData(getNumberOfEnabledChannels(commType));

		incrementPacketsReceivedCounters();

		if(debug)
			System.out.println("\nNew Parser loop. Packet length:\t" + newPacket.length);
		
		TreeMap<Integer, SensorDetails> parserMapPerComm = mParserMap.get(commType);
		if(parserMapPerComm!=null){
			int index=0;
			for (SensorDetails sensor:parserMapPerComm.values()){
				int length = sensor.getExpectedPacketByteArray(commType);
				byte[] sensorByteArray = new byte[length];
				//TODO process API sensors, not just bytes from Shimmer packet 
				if (length!=0){ //if length 0 means there are no channels to be processed
					if((index+sensorByteArray.length)<=newPacket.length){
						System.arraycopy(newPacket, index, sensorByteArray, 0, sensorByteArray.length);
					}
					else{
						consolePrintLn(mShimmerUserAssignedName + " ERROR PARSING " + sensor.mSensorDetailsRef.mGuiFriendlyLabel);
					}
				}
				sensor.processData(sensorByteArray, commType, ojc, isTimeSyncEnabled, pcTimestampMs);

				if(debug)
					System.out.println(sensor.mSensorDetailsRef.mGuiFriendlyLabel + "\texpectedPacketArraySize:" + length + "\tcurrentIndex:" + index);
				index += length;
			}
		}
		else{
			consolePrintErrLn("ERROR!!!! Parser map null");
		}
		
		//After sensor data has been processed, now process any filters or Algorithms 
		ojc = processData(ojc);
		
		return ojc;
	}
	
	/** Perform any processing required on the data. E.g. time sync, filtering and algorithms
	 * @param ojc
	 * @return
	 */
	protected ObjectCluster processData(ObjectCluster ojc) {
		if(mDataProcessing!=null){
			ojc = mDataProcessing.processData(ojc);
		}
		
		//now process the enabled algorithms
		ojc = processAlgorithms(ojc);
		return ojc;
	}

	public ObjectCluster processAlgorithms(ObjectCluster ojc) {
		if (mAlgorithmProcessingSequence!=null) {
			for (String algoKey:mAlgorithmProcessingSequence) {
				AbstractAlgorithm aA = getMapOfAlgorithmModules().get(algoKey);
				if (aA.isEnabled()) {
					try {
						AlgorithmResultObject algorithmResultObject = aA.processDataRealTime(ojc);
						if(algorithmResultObject!=null){
							ojc = (ObjectCluster) algorithmResultObject.mResult;
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} else {
			for (AbstractAlgorithm aA:getMapOfAlgorithmModules().values()) {
				//			consolePrintErrLn.println(aA.mAlgorithmName + "\tisEnabled:\t" + aA.isEnabled());
				if (aA.isEnabled()) {
					try {
						AlgorithmResultObject algorithmResultObject = aA.processDataRealTime(ojc);
						if(algorithmResultObject!=null){
							ojc = (ObjectCluster) algorithmResultObject.mResult;
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		return ojc;
	}
	
	//TODO get below working if even needed
	/** Based on the SensorMap approach rather then legacy inquiry command */
	public void interpretDataPacketFormat(){
//		String [] signalNameArray=new String[MAX_NUMBER_OF_SIGNALS];
//		String [] signalDataTypeArray=new String[MAX_NUMBER_OF_SIGNALS];
//		
//		int packetSize=0;//mTimeStampPacketByteSize; // Time stamp
//
//		int index = 0;
//		for(SensorDetails sED:mSensorMap.values()) {
//			if(sED.isEnabled() && !sED.isDerivedChannel()) {
//				for(ChannelDetails channelDetails:sED.mListOfChannels){
////				for(String channelDetailsName:sED.mListOfChannels){
////					ChannelDetails channelDetails = mChannelMap.get(channelDetailsName);
//					if(channelDetails.mDefaultNumBytes>0){
//						signalNameArray[index] = channelDetails.mObjectClusterName;
//						signalDataTypeArray[index] = channelDetails.mDefaultChannelDataType;
//						packetSize += channelDetails.mDefaultNumBytes;
//						
//						System.out.println(channelDetails.mObjectClusterName + "\t" + channelDetails.mDefaultChannelDataType + "\t" + channelDetails.mDefaultNumBytes);
//						index++;
//					}
//				}
//			}
//		}
//		
//		mSignalNameArray=signalNameArray;
//		mSignalDataTypeArray=signalDataTypeArray;
//		mPacketSize=packetSize;
	}

	// TODO this is a hack. I'm just picking off the first item from the
	// mListOfAvailableCommunicationTypes as all comms routes currently have the
	// same sampling rate.
	public int getExpectedDataPacketSize(){
		if(mListOfAvailableCommunicationTypes.size()==0){
			return 0;
		}
		generateParserMap();
		return getExpectedDataPacketSize(mListOfAvailableCommunicationTypes.get(0));
	}

	public int getExpectedDataPacketSize(COMMUNICATION_TYPE commsType){
		int dataPacketSize = 0;
		TreeMap<Integer, SensorDetails> parserMapPerCommType = mParserMap.get(commsType);
		if(parserMapPerCommType!=null){
			Iterator<SensorDetails> iterator = parserMapPerCommType.values().iterator();
			while(iterator.hasNext()){
				SensorDetails sensorDetails = iterator.next();
				int expectedPktSize = sensorDetails.getExpectedDataPacketSize();
				if(expectedPktSize>0){
//					System.out.println("Expected Packet size for:\t" + sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel + "\t:\t" + expectedPktSize);
					dataPacketSize += expectedPktSize;
				}
			}
		}
		return dataPacketSize;
	}
	
	public byte[] generateUartConfigMessage(UartComponentPropertyDetails cPD){
		//TODO: process common ShimmerDevice configs
		return null;
	}
	
	public void parseUartConfigResponse(UartComponentPropertyDetails cPD, byte[] response) {
		//TODO: process common ShimmerDevice configs
	}


	public int getNumberOfEnabledChannels(COMMUNICATION_TYPE commType){
		int total = 0;
		Iterator<SensorDetails> iterator = mSensorMap.values().iterator();
		while(iterator.hasNext()){
			SensorDetails sensorEnabledDetails = iterator.next();
			if(sensorEnabledDetails.isEnabled(commType)){
				total += sensorEnabledDetails.getNumberOfChannels();
			}
		}
		return total;
	}
	
	/**
	 * @return the Parsed MAC address from any source available
	 */
	public String getMacIdParsed() {
		String macToUse = getMacId();
		if(macToUse.length()>=12) {
//			return macToUse.substring(8, 12);
			macToUse = macToUse.replace(":", "");
			return macToUse.substring(macToUse.length()-4, macToUse.length());
		}
		return "";
	}
	
	/**
	 * @return the MAC address
	 */
	public String getMacId() {
		return mMacIdFromUart;
	}
	
	/** Sets the Shimmer Trial name but filters out invalid characters and maintains a maximum character length of 12. 
	 * @param trialName the trialName to set
	 */
	public void setTrialNameAndCheck(String trialName) {
		trialName = trialName.replaceAll(INVALID_TRIAL_NAME_CHAR, "");

		if(trialName.isEmpty()){
			trialName = DEFAULT_EXPERIMENT_NAME;
		}
		
		//Limit the name to 12 Char
		if(trialName.length()>12){
			trialName = trialName.substring(0, 11);
		}
		
		setTrialName(trialName);
	}

	/** Sets the Shimmer Trial name but bypasses all safety checks. Preferred use is setTrialNameAndCheck().
	 * @param trialName
	 * @see setTrialNameAndCheck
	 */
	public void setTrialName(String trialName) {
		this.mTrialName = trialName;
	}

	/**
	 * @return the mTrialName
	 */
	public String getTrialName() {
		return mTrialName;
	}
	
	public boolean isTrialNameInvalid(){
		return isTrialOrShimmerNameInvalid(getTrialName());
	}

	public boolean isShimmerNameInvalid(){
		return isTrialOrShimmerNameInvalid(getShimmerUserAssignedName()) 
				|| getShimmerUserAssignedName().equals(DEFAULT_SHIMMER_NAME_WITH_ERROR);
	}

	public boolean isShimmerNameValid() {
		return !isShimmerNameInvalid();
	}
	
	public static boolean isTrialOrShimmerNameInvalid(String name){
		if(name.isEmpty()){
			return true;
		}
		
		Pattern p = Pattern.compile(INVALID_TRIAL_NAME_CHAR);
		Matcher m = p.matcher(name);
		return m.find();
	}
	
	/**
	 * @param mConfigTime the trialConfigTime to set
	 */
	public void setConfigTime(long trialConfigTime) {
		mConfigTime = trialConfigTime;
	}
	
	public void setTrialConfig(String trialName, long trialConfigTime) {
		setTrialName(trialName);
		mConfigTime = trialConfigTime;
	}

	public void setFirstDockRead() {
		mShimmerSDCardDetails.setFirstDockRead();
		setConfigurationReadSuccess(false);
		mReadHwFwSuccess = false;
		mReadDaughterIDSuccess = false;
		writeRealWorldClockFromPcTimeSuccess = false;
		mReadBtVerSuccess = false;
		mReadBtModeSuccess = false;
	}
	
	// ----------------- Overrides from ShimmerDevice end -------------

	public LinkedHashMap<SENSORS, AbstractSensor> getMapOfSensorsClasses() {
		return mMapOfSensorClasses;
	}
	
	public boolean doesSensorKeyExist(int sensorKey) {
		return (mSensorMap.containsKey(sensorKey));
	}

	public boolean isSensorEnabled(int sensorId){
		if(mSensorMap!=null) {
			SensorDetails sensorDetails = getSensorDetails(sensorId);
			if(sensorDetails!=null){
				return sensorDetails.isEnabled();
			}
		}		
		return false;
	}

	public boolean isSensorEnabled(COMMUNICATION_TYPE commType, int sensorKey) {
		SensorDetails sensorDetails = getSensorDetails(sensorKey);
		if(sensorDetails!=null){
			return sensorDetails.isEnabled(commType);
		}
	    return false;
	}

	public String getSensorLabel(int sensorId) {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			String guiFriendlyLabel = abstractSensor.getSensorGuiFriendlyLabel(sensorId);
			if(guiFriendlyLabel!=null){
				return guiFriendlyLabel;
			}
		}
		return null;
	}

	public List<ShimmerVerObject> getListOfCompatibleVersionInfoForSensor(int sensorId) {
		SensorDetails sensorDetails = getSensorDetails(sensorId);
		if(sensorDetails!=null){
			return sensorDetails.mSensorDetailsRef.mListOfCompatibleVersionInfo;
		}
		return null;
	}
    
	/** Returns all sensor map keys in use */
	public Set<Integer> getSensorIdsSet() {
		TreeSet<Integer> setOfSensorIds = new TreeSet<Integer>();
		setOfSensorIds.addAll(mSensorMap.keySet());
		return setOfSensorIds;
	}
	
	/** Sets all default Shimmer settings in ShimmerDevice.
	 */
	public void setDefaultShimmerConfiguration() {
		// TODO Auto-generated method stub
	}
	
	public void configBytesParse(byte[] configBytes) {
		configBytesParse(configBytes, COMMUNICATION_TYPE.BLUETOOTH);
	}
	
	public byte[] configBytesGenerate(boolean generateForWritingToShimmer) {
		return configBytesGenerate(generateForWritingToShimmer, COMMUNICATION_TYPE.BLUETOOTH);
	}

	
	public Object setConfigValueUsingConfigLabel(String configLabel, Object valueToSet){
		return setConfigValueUsingConfigLabel("", configLabel, valueToSet);
	}
	
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet){
		return setConfigValueUsingConfigLabel(Integer.toString(sensorId), configLabel, valueToSet);
	}

	public Object setConfigValueUsingConfigLabel(String identifier, String configLabel, Object valueToSet){
		
//		System.err.println("GROUPNAME:\t" + (identifier.isEmpty()? "EMPTY":identifier) + "\tCONFIGLABEL:\t" + configLabel);
		
		Object returnValue = null;

		returnValue = setSensorClassSetting(identifier, configLabel, valueToSet);
		if(returnValue!=null){
			return returnValue;
		}
		
		//TODO remove below when ready and use "getAlgorithmConfigValueUsingConfigLabel"
		setAlgorithmSettings(identifier, configLabel, valueToSet);

		switch(configLabel){
//Booleans
			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN):
	        	setInternalExpPower((boolean)valueToSet);
	        	break;
//Integers
			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER):
				setInternalExpPower((int)valueToSet);
            	break;
//Strings
			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME):
        		setShimmerUserAssignedName((String)valueToSet);
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME):
        		setTrialNameAndCheck((String)valueToSet);
			break;
			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE):
				//			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_AND_SENSORS_SAMPLING_RATE):
				// don't let sampling rate be empty
				Double enteredSamplingRate = 1.0;
			if (valueToSet instanceof String){
				if(((String)valueToSet).isEmpty()) {
					enteredSamplingRate = 1.0;
				}            	
				else {
					enteredSamplingRate = Double.parseDouble((String)valueToSet);
				}
			} else if (valueToSet instanceof Double){
				enteredSamplingRate = (Double) valueToSet;
			}

			//	          	if(configLabel.equals(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_AND_SENSORS_SAMPLING_RATE)){
			setShimmerAndSensorsSamplingRate(enteredSamplingRate);
			//	          	}
			//	          	else if(configLabel.equals(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE)){
			//	          		setSamplingRateShimmer(enteredSamplingRate);
			//	          	}

			returnValue = Double.toString(getSamplingRateShimmer());
			break;
			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_ALL):
				setMapOfSensorCalibrationAll((TreeMap<Integer, TreeMap<Integer, CalibDetails>>) valueToSet);
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_PER_SENSOR):
				setSensorCalibrationPerSensor(Integer.parseInt(identifier), (TreeMap<Integer, CalibDetails>) valueToSet);
				//TODO decide whether to include the below
//				returnValue = valueToSet;
				break;
	        default:
	        	break;
		}
		
		
		return returnValue;		
	}
	
	public Object getConfigValueUsingConfigLabel(String configLabel){
		return getConfigValueUsingConfigLabel("", configLabel);
	}

	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel){
		return getConfigValueUsingConfigLabel(Integer.toString(sensorId), configLabel);
	}

	public Object getConfigValueUsingConfigLabel(String identifier, String configLabel){
		Object returnValue = null;
		
		returnValue = getSensorClassSetting(identifier, configLabel);
		if(returnValue!=null){
			return returnValue;
		}
		
		//TODO remove below when ready and use "getAlgorithmConfigValueUsingConfigLabel"
		returnValue = getAlgorithmSettings(identifier, configLabel);
		if(returnValue!=null){
			return returnValue;
		}

		switch(configLabel){
//Booleans
			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN):
				returnValue = isInternalExpPower();
	        	break;
//Integers
			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER):
				returnValue = getInternalExpPower();
            	break;
//Strings
			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE):
//			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_AND_SENSORS_SAMPLING_RATE):
		        Double readSamplingRate = getSamplingRateShimmer();
				Double actualSamplingRate = roundSamplingRateToSupportedValue(readSamplingRate, getSamplingClockFreq());
//	   					    	consolePrintLn("GET SAMPLING RATE: " + componentName);
		    	returnValue = actualSamplingRate.toString();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME):
				returnValue = getShimmerUserAssignedName();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME):
				returnValue = getTrialName();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME):
	        	returnValue = getConfigTimeParsed();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_PER_SENSOR):
				Integer sensorId = Configuration.Shimmer3.SENSOR_ID.RESERVED_ANY_SENSOR;
				try{
					sensorId = Integer.parseInt(identifier);
				} catch (NumberFormatException nFE){
					//Do nothing
				}

				returnValue = getMapOfSensorCalibrationPerSensor(sensorId);
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_ALL):
				returnValue = getMapOfSensorCalibrationAll();
				break;
	        default:
	        	break;
		}
		
		return returnValue;		
	}
	
	public String getConfigGuiValueUsingConfigLabel(Object sensorId, String configLabel){
		String guiValue = "";

		Object configValue = null;
		if(sensorId instanceof Integer){
			configValue = getConfigValueUsingConfigLabel((Integer)sensorId, configLabel);
		} else if(sensorId instanceof String){
			configValue = getConfigValueUsingConfigLabel((String)sensorId, configLabel);
		}
		
		if(configValue!=null){
			if(configValue instanceof String){
				guiValue = (String) configValue;
			} else if(configValue instanceof Boolean){
				guiValue = configValue.toString();
			} else if(configValue instanceof Integer){
				int configInt = (int) configValue;
				Map<String, ConfigOptionDetailsSensor> mapOfConfigOptions = getConfigOptionsMap();
				if(mapOfConfigOptions!=null && mapOfConfigOptions.containsKey(configLabel) && mapOfConfigOptions.get(configLabel)!=null){
					ConfigOptionDetails configOption = getConfigOptionsMap().get(configLabel);
					
					guiValue = configOption.getConfigStringFromConfigValue(configInt);
					
				} else {
					guiValue = Integer.toString(configInt);
				}
				
			}
	}

		return guiValue;
	}

	
	private Object setSensorClassSetting(String identifier, String configLabel, Object valueToSet) {
		Object returnValue = null;
		//TODO check sensor classes return a null if the setting is successfully found
		Integer sensorId = Configuration.Shimmer3.SENSOR_ID.RESERVED_ANY_SENSOR;
		try{
			sensorId = Integer.parseInt(identifier);
		} catch(NumberFormatException eFE) {
			//DO nothing
		}
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
			returnValue = abstractSensor.setConfigValueUsingConfigLabel(sensorId, configLabel, valueToSet);
			if(returnValue!=null){
				return returnValue;
			}
		}
		return returnValue;
	}
	
	private Object getSensorClassSetting(String identifier, String configLabel) {
		Object returnValue = null;
		Integer sensorId = Configuration.Shimmer3.SENSOR_ID.RESERVED_ANY_SENSOR;
		try{
			sensorId = Integer.parseInt(identifier);
		} catch(NumberFormatException eFE) {
			//DO nothing
		}
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
			returnValue = abstractSensor.getConfigValueUsingConfigLabel(sensorId, configLabel);
			if(returnValue!=null){
				return returnValue;
			}
		}
		return returnValue;
	}

	public void setAlgorithmSettings(String configLabel, Object valueToSet){
		setAlgorithmSettings(null, configLabel, valueToSet);
	}

	public void setAlgorithmSettings(String groupName, String configLabel, Object valueToSet){
		List<AbstractAlgorithm> listOfAlgorithms = null;
		
		if((groupName!=null && !groupName.isEmpty())){
			listOfAlgorithms = getListOfAlgorithmModulesPerGroup(groupName);		
		}
		else{
			listOfAlgorithms = getListOfAlgorithmModules();
		}
		
		// Treat algorithms differently because we normally want to set the same
		// config across multiple algorithm modules so return only after all have been set
		if(listOfAlgorithms!=null && !listOfAlgorithms.isEmpty()){
			for(AbstractAlgorithm abstractAlgorithm:listOfAlgorithms){	
				abstractAlgorithm.setSettings(configLabel, valueToSet);
			}
		}
	}

	public Object getAlgorithmSettings(String groupName, String configLabel){
		List<AbstractAlgorithm> listOfAlgorithms = null;
		if(groupName.isEmpty()){
//			listOfAlgorithms = getListOfEnabledAlgorithmModules();
			listOfAlgorithms = getListOfAlgorithmModules();
		}
		else {
//			listOfAlgorithms = getListOfEnabledAlgorithmModulesPerGroup(groupName);
			listOfAlgorithms = getListOfAlgorithmModulesPerGroup(groupName);
		}
		
		Object returnValue = null;
		if(listOfAlgorithms!=null && !listOfAlgorithms.isEmpty()){
			for(AbstractAlgorithm abstractAlgorithm:listOfAlgorithms){
				returnValue = abstractAlgorithm.getSettings(configLabel);
				if(returnValue!=null){
					return returnValue;
				}
			}
		}
		return returnValue;		
	}
	
//	public HashMap<String, Object> getEnabledAlgorithmSettingsPerGroup(String groupName) {
//		List<AbstractAlgorithm> listOfAlgorithms = getListOfEnabledAlgorithmModulesPerGroup(groupName);
//		HashMap<String, Object> mapOfAlgorithmSettings = new HashMap<String, Object>();
//		for(AbstractAlgorithm abstractAlgorithm:listOfAlgorithms){
//			mapOfAlgorithmSettings.putAll(abstractAlgorithm.getAlgorithmSettings());
//		}
//		return mapOfAlgorithmSettings;
//	}
//
//	public HashMap<String, Object> getEnabledAlgorithmSettings() {
//		List<AbstractAlgorithm> listOfAlgorithms = getListOfEnabledAlgorithmModules();
//		HashMap<String, Object> mapOfAlgorithmSettings = new HashMap<String, Object>();
//		for(AbstractAlgorithm abstractAlgorithm:listOfAlgorithms){
//			mapOfAlgorithmSettings.putAll(abstractAlgorithm.getAlgorithmSettings());
//		}
//		return mapOfAlgorithmSettings;
//	}
	
	public boolean isSupportedRtcConfigViaUart() {
		return mShimmerVerObject.isSupportedRtcConfigViaUart();
	}
	
	public boolean isSupportedConfigViaUart() {
		return mShimmerVerObject.isSupportedConfigViaUart();
	}

	public boolean isSupportedSdCardAccess() {
		return mShimmerVerObject.isSupportedSdCardAccess();
	}

	public boolean isSupportedBluetooth() {
		return mShimmerVerObject.isSupportedBluetooth();
	}

	public boolean isSupportedCalibDump() {
		return mShimmerVerObject.isSupportedCalibDump();
	}
	
	public boolean isShimmerGen2(){
		return mShimmerVerObject.isShimmerGen2();
	}

	public boolean isShimmerGen3(){
		return mShimmerVerObject.isShimmerGen3();
	}

	public boolean isShimmerGen3R(){
		return mShimmerVerObject.isShimmerGen3R();
	}

	public boolean isShimmerGen3or3R(){
		return mShimmerVerObject.isShimmerGen3() || mShimmerVerObject.isShimmerGen3R();
	}

	public boolean isShimmerGen4(){
		return mShimmerVerObject.isShimmerGen4();
	}

	public boolean isShimmerGenGq(){
		return mShimmerVerObject.isShimmerGenGq();
	}

	public boolean isShimmerVideoDevice(){
		return mShimmerVerObject.isShimmerVideoDevice();
	}
	
	public boolean isSupportedSrProgViaDock() {
		if(mShimmerVerObject.compareVersions(HW_ID.SHIMMER_3, FW_ID.BTSTREAM, 0, 7, 13)
				|| mShimmerVerObject.compareVersions(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 0, 8, 1)
				|| isShimmerGen3R()){
			return true;
		}
		return false;
	}
	
	public boolean isSupportedSdLogSync() {
		if(getFirmwareIdentifier()==ShimmerVerDetails.FW_ID.SDLOG
				|| (UtilShimmer.compareVersions(getShimmerVerObject(),Configuration.Shimmer3.CompatibilityInfoForMaps.svoShimmer3RLogAndStream))
				|| (UtilShimmer.compareVersions(getShimmerVerObject(),Configuration.Shimmer3.CompatibilityInfoForMaps.svoShimmer3LogAndStreamWithSDLogSyncSupport))
				|| getFirmwareIdentifier()==ShimmerVerDetails.FW_ID.STROKARE){
			return true;
		}
		return false;
	}
	
	public boolean isHWAndFWSupportedBtBleControl() {
		if((isShimmerGen3() && getFirmwareIdentifier()==ShimmerVerDetails.FW_ID.LOGANDSTREAM
				&& mShimmerVerObject.compareVersions(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 4))
			|| (isShimmerGen3R() && getFirmwareIdentifier()==ShimmerVerDetails.FW_ID.LOGANDSTREAM
					&& mShimmerVerObject.compareVersions(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 40) 		)){
			return true;
		}
		return false;
	}

	public boolean isHWAndFWSupportedUsbControl() {
		if(isShimmerGen3R() && getFirmwareIdentifier()==ShimmerVerDetails.FW_ID.LOGANDSTREAM
					&& mShimmerVerObject.compareVersions(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 55)){
			return true;
		}
		return false;
	}

	public boolean isLegacySdLog(){
		if (getFirmwareIdentifier()==FW_ID.SDLOG && getFirmwareVersionMajor()==0 && getFirmwareVersionMinor()==5){
			return true;
		}
		return false;
	}

	public boolean isSupportedDerivedSensors(){
		return isSupportedDerivedSensors(mShimmerVerObject);
	}

	public static boolean isSupportedDerivedSensors(ShimmerVerObject svo){
		if((isVerCompatibleWith(svo, HW_ID.SHIMMER_3, FW_ID.BTSTREAM, 0, 7, 0))
			||(isVerCompatibleWith(svo, HW_ID.SHIMMER_3, FW_ID.SDLOG, 0, 8, 69))
			||(isVerCompatibleWith(svo, HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 0, 3, 17))
			||(svo.isShimmerGen3R())
			||(svo.isShimmerGenGq())
			||(svo.isShimmerGen4())){
//			||(isVerCompatibleWith(svo, HW_ID.SHIMMER_4_SDK, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION))){
			return true;
		}
		return false;
	}
	
	public boolean isSupportedNoImuSensors() {
		return isSupportedNoImuSensors(getShimmerVerObject(), getExpansionBoardDetails());
	}
	
	public static boolean isSupportedNoImuSensors(ShimmerVerObject svo, ExpansionBoardDetails ebd) {
		if(svo==null || ebd==null){
			return false;
		}
		
		int expBrdId = ebd.getExpansionBoardId();
		int expBrdRev = ebd.getExpansionBoardRev();
		int expBrdRevSpecial = ebd.getExpansionBoardRevSpecial();
		
		if(svo.getHardwareVersion()==HW_ID.SHIMMER_3 &&	(
				(expBrdId==HW_ID_SR_CODES.SHIMMER_ECG_MD && expBrdRev==3 && expBrdRevSpecial==1)			// == SR59-3-1
				)){
			return true;
		}
		else {
			return false;
		}
	}

	public boolean isSupportedRn4678ErrorDetection() {
		if((isShimmerGen3() && getFirmwareIdentifier()==ShimmerVerDetails.FW_ID.LOGANDSTREAM
				&& mShimmerVerObject.compareVersions(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 17))){
			return true;
		}
		return false;
	}

	
	/**
	 * Check each entry in the passed in list to see if the current Shimmer
	 * version information in this instance of ShimmerDevice is compatible with
	 * any of them. The hardware ID, firmware ID and expansion board ID need to
	 * be equal whereas the combination of firmware major:minor:internal needs
	 * to be greater or equal
	 * 
	 * @param listOfCompatibleVersionInfo
	 * @return
	 */
	public boolean isVerCompatibleWithAnyOf(List<ShimmerVerObject> listOfCompatibleVersionInfo) {

		if(listOfCompatibleVersionInfo==null || listOfCompatibleVersionInfo.isEmpty()) {
			return true;
		}

		for(ShimmerVerObject compatibleVersionInfo:listOfCompatibleVersionInfo){
			int expBrdIdToCompare = compatibleVersionInfo.getShimmerExpansionBoardId(); 
			if(expBrdIdToCompare!=ShimmerVerDetails.ANY_VERSION) {
				if(expBrdIdToCompare!=getExpansionBoardId()) {
					continue;
				}
				
				int expBrdRevToCompare = compatibleVersionInfo.getShimmerExpansionBoardRev(); 
				if(expBrdRevToCompare!=ShimmerVerDetails.ANY_VERSION) {
					if(expBrdRevToCompare>getExpansionBoardRev()) {
						continue;
					}
				}
			} else {
				//TODO add support for New IMU Shimmer3 with any attached Expansion board (i.e. SRx-x-171)
			}
			
			if(isThisVerCompatibleWith( 
					compatibleVersionInfo.mHardwareVersion,
					compatibleVersionInfo.mFirmwareIdentifier, 
					compatibleVersionInfo.mFirmwareVersionMajor, 
					compatibleVersionInfo.mFirmwareVersionMinor, 
					compatibleVersionInfo.mFirmwareVersionInternal)){
				return true;
			}

		}
		return false;
	}
	
	public static boolean isVerCompatibleWith(ShimmerVerObject svo, int hardwareVersion, int firmwareIdentifier, int firmwareVersionMajor, int firmwareVersionMinor, int firmwareVersionInternal){
		return svo.compareVersions(hardwareVersion, firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
	}
	
	public boolean isThisVerCompatibleWith(int firmwareIdentifier, int firmwareVersionMajor, int firmwareVersionMinor, int firmwareVersionInternal){
		return UtilShimmer.compareVersions(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(),
				firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
	}

	public boolean isThisVerCompatibleWith(int hardwareVersion, int firmwareIdentifier, int firmwareVersionMajor, int firmwareVersionMinor, int firmwareVersionInternal){
		return UtilShimmer.compareVersions(getHardwareVersion(), getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(),
				hardwareVersion, firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
	}
	

	public void consolePrintExeptionLn(String message, StackTraceElement[] stackTrace) {
		if(mVerboseMode) {
			consolePrintErrLn(message + "\n" + UtilShimmer.convertStackTraceToString(stackTrace));
		}
	}

	public void consolePrintErrLn(String message) {
		if(mVerboseMode) {
			System.err.println(assemblePrintString(message));
		}
	}

	public void consolePrintLn(String message) {
		if(mVerboseMode) {
			System.out.println(assemblePrintString(message));
		}		
	}
	
	public void consolePrint(String message) {
		if(mVerboseMode) {
			System.out.print(message);
		}		
	}
	
	private String assemblePrintString(String message){
		Calendar rightNow = Calendar.getInstance();
		String rightNowString = "[" + String.format("%02d",rightNow.get(Calendar.HOUR_OF_DAY)) 
				+ ":" + String.format("%02d",rightNow.get(Calendar.MINUTE)) 
				+ ":" + String.format("%02d",rightNow.get(Calendar.SECOND)) 
				+ ":" + String.format("%03d",rightNow.get(Calendar.MILLISECOND)) + "]";
		return (rightNowString 
				+ " " + getClass().getSimpleName() 
				+ " (Mac:" + getMacIdParsed() 
				+ " HashCode:" + Integer.toHexString(this.hashCode()) + ")" 
				+ ": " + message);
	}

	public void setVerboseMode(boolean verboseMode) {
		mVerboseMode = verboseMode;
	}

	/**Automatically control internal expansion board power based on sensor map
	 */
	protected void checkIfInternalExpBrdPowerIsNeeded() {
		for(SensorDetails sensorEnabledDetails:mSensorMap.values()) {
			if(sensorEnabledDetails.isInternalExpBrdPowerRequired()){
				mInternalExpPower = 1;
				return;
			}
			else{
				//TODO move to AbstractSensors
				if(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1)
					||isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12)
					||isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13)
					||isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14)){
					
				}
				else {
					mInternalExpPower = 0;
				}
			}
		}
	}
	
	/**
	 * @param state the mInternalExpPower state to set
	 */
	public void setInternalExpPower(int state) {
		this.mInternalExpPower = state;
	}
	
	/**
	 * @return the mInternalExpPower
	 */
	public boolean isInternalExpPower() {
		return (mInternalExpPower > 0)? true:false;
	}
	
	/**
	 * @param state the mInternalExpPower state to set
	 */
	public void setInternalExpPower(boolean state) {
		setInternalExpPower(state? 1:0);
	}

	public int getInternalExpPower(){
		return mInternalExpPower;
	}


	/**
	 * @return the mSensorMap
	 */
	public LinkedHashMap<Integer, SensorDetails> getSensorMap() {
		return mSensorMap;
	}

	public void setSensorIdsEnabled(Integer[] sensorIds) {
		for(Integer sensorId:mSensorMap.keySet()){
			setSensorEnabledState(sensorId, false);
		}
		
		for(Integer sensorId:sensorIds){
			setSensorEnabledState(sensorId, true);
		}
	}
	
	/**
	 * Used to changed the enabled state of a sensor in the sensormap. This is
	 * only used in Consensys for dynamic configuration of a Shimmer. This
	 * method deals with everything associated with enabling a sensor such as:
	 * 1) dealing with conflicting sensors
	 * 2) dealing with other required sensors for the chosen sensor
	 * 3) determining whether expansion board power is required
	 * 4) setting default settings for disabled sensors 
	 * 5) etc.
	 * 
	 * @param sensorId the sensormap key of the sensor to be enabled/disabled
	 * @param state the sensor state to set 
	 * @return a boolean indicating if the sensors state was successfully changed
	 */
	public boolean setSensorEnabledState(int sensorId, boolean state) {
		if(mSensorMap!=null) {
			sensorId = handleSpecCasesBeforeSetSensorState(sensorId,state);
			
			SensorDetails sensorDetails = getSensorDetails(sensorId);
			//System.err.println("sensorDetails.mSensorDetailsRef: " +sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel);
			if(sensorDetails!=null){
				//Automatically handle required channels for each sensor
				List<Integer> listOfRequiredKeys = sensorDetails.mSensorDetailsRef.mListOfSensorIdsRequired;
				if(listOfRequiredKeys != null && listOfRequiredKeys.size()>0) {
					for(Integer i:listOfRequiredKeys) {
						getSensorDetails(i).setIsEnabled(state);
					}
				}
				
				//Set sensor state
				sensorDetails.setIsEnabled(state);

				setSensorEnabledStateCommon(sensorId, sensorDetails.isEnabled());

				boolean result = sensorDetails.isEnabled();
				boolean successfullySet = result==state? true:false; 
				if(!successfullySet){
					consolePrintLn("WARNING!!! Failed to setSensorEnabledState for sensor:\t" + sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel);
					UtilShimmer.consolePrintCurrentStackTrace();
				}
				return successfullySet;
			} else {
				consolePrintLn("WARNING!!! SensorID not found:" + sensorId);
				UtilShimmer.consolePrintCurrentStackTrace();
			}
			return false;
		}
		else {
			consolePrintLn("setSensorEnabledState:\t SensorMap=null");
			return false;
		}
	}
	
	public boolean setSensorEnabledState(int sensorId, boolean state, COMMUNICATION_TYPE commType) {
		if(mSensorMap!=null) {
			sensorId = handleSpecCasesBeforeSetSensorState(sensorId,state);
			
			SensorDetails sensorDetails = getSensorDetails(sensorId);
			//System.err.println("sensorDetails.mSensorDetailsRef: " +sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel);
			if(sensorDetails!=null){
				//Testing
//				if(sensorDetails.hashCode()!=getSensorDetailsFromMapOfSensorClasses(sensorId).hashCode())
//					System.err.println("SENSOR DETAILS HASHCODES NOT EQUAL");

				//Automatically handle required channels for each sensor
				List<Integer> listOfRequiredKeys = sensorDetails.mSensorDetailsRef.mListOfSensorIdsRequired;
				if(listOfRequiredKeys != null && listOfRequiredKeys.size()>0) {
					for(Integer i:listOfRequiredKeys) {
						getSensorDetails(i).setIsEnabled(commType, state);
					}
				}
				
				//Set sensor state
				sensorDetails.setIsEnabled(commType, state);

				setSensorEnabledStateCommon(sensorId, sensorDetails.isEnabled(commType));

				boolean result = sensorDetails.isEnabled(commType);
				boolean successfullySet = result==state? true:false; 
				if(!successfullySet){
					consolePrintErrLn("Failed to setSensorEnabledState for sensor:\t" + sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel);
				}
				return successfullySet;
			}
			return false;
		}
		else {
			consolePrintLn("setSensorEnabledState:\t SensorMap=null");
			return false;
		}
	}

	
	private void setSensorEnabledStateCommon(int sensorId, boolean state) {
		sensorMapConflictCheckandCorrect(sensorId);
		setDefaultConfigForSensor(sensorId, state);
		
		// Automatically control internal expansion board power
		checkIfInternalExpBrdPowerIsNeeded();

//		printSensorParserAndAlgoMaps();
		refreshEnabledSensorsFromSensorMap();
//		printSensorParserAndAlgoMaps();
		
		generateParserMap();
		//refresh algorithms
		algorithmRequiredSensorCheck();

//		//Debugging
//		printSensorParserAndAlgoMaps();
	}

	/**
	 * This method just changes the isEnabled boolean state in the specific
	 * SensorDetails inside the SensorMap. It doesn't do any of the usual checks
	 * before or after enabling a sensor (such as checking for
	 * conflicts/required sensors/expansion board power etc.)
	 * 
	 * @param sensorId
	 * @param state
	 */
	protected void setSensorEnabledStateBasic(int sensorId, boolean state) {
		SensorDetails sensorDetails = mSensorMap.get(sensorId);
		if (sensorDetails!=null){
			sensorDetails.setIsEnabled(state);
		}
	}
	
	public int handleSpecCasesBeforeSetSensorState(int sensorId, boolean state) {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			int newSensorId = abstractSensor.handleSpecCasesBeforeSetSensorState(sensorId, state);
			if(newSensorId!=sensorId){
				return newSensorId;
			}
		}
		return sensorId;
	}
	
	public boolean isTimestampEnabled(){
		SensorDetails sensorDetails = getSensorDetails(Configuration.Shimmer3.SENSOR_ID.SHIMMER_TIMESTAMP);
		if(sensorDetails!=null){
			return sensorDetails.isEnabled();
		}
		return false;
	}
	
	/**
	 * @param originalSensorId This takes in a single sensor map key to check for conflicts and correct
	 * @return enabledSensors This returns the new set of enabled sensors, where any sensors which conflicts with sensorToCheck is disabled on the bitmap, so sensorToCheck can be accomodated (e.g. for Shimmer2 using ECG will disable EMG,GSR,..basically any daughter board)
	 * @return boolean 
	 *  
	 */
	protected void sensorMapConflictCheckandCorrect(int originalSensorId){
		SensorDetails sdOriginal = getSensorDetails(originalSensorId); 
		if(sdOriginal != null) {
			if(sdOriginal.mSensorDetailsRef.mListOfSensorIdsConflicting != null) {
				for(Integer sensorIdConflicting:sdOriginal.mSensorDetailsRef.mListOfSensorIdsConflicting) {
					SensorDetails sdConflicting = getSensorDetails(sensorIdConflicting); 
					if(sdConflicting != null) {
						sdConflicting.setIsEnabled(false);
						if(sdConflicting.isDerivedChannel()) {
							mDerivedSensors &= ~sdConflicting.mDerivedSensorBitmapID;
						}
						setDefaultConfigForSensor(sensorIdConflicting, sdConflicting.isEnabled());
					}
				}
			}
		}
		
		sensorMapCheckandCorrectSensorDependencies();
		sensorMapCheckandCorrectHwDependencies();
	}
	

	protected void sensorMapCheckandCorrectSensorDependencies() {
		//Cycle through any required sensors and update sensorMap channel enable values
		for(Integer sensorId:mSensorMap.keySet()) {
			SensorDetails sensorDetails = getSensorDetails(sensorId); 
			if(sensorDetails.mSensorDetailsRef.mListOfSensorIdsRequired != null) {
				for(Integer requiredSensorKey:sensorDetails.mSensorDetailsRef.mListOfSensorIdsRequired) {
					if(!getSensorDetails(requiredSensorKey).isEnabled()) {
						sensorDetails.setIsEnabled(false);
						if(sensorDetails.isDerivedChannel()) {
							mDerivedSensors &= ~sensorDetails.mDerivedSensorBitmapID;
						}
						setDefaultConfigForSensor(sensorId, sensorDetails.isEnabled());
						break;
					}
				}
			}
		}
	}
	
	protected void sensorMapCheckandCorrectHwDependencies() {
		for(Integer sensorId:mSensorMap.keySet()) {
			SensorDetails sensorDetails = getSensorDetails(sensorId); 
			if(sensorDetails.mSensorDetailsRef.mListOfCompatibleVersionInfo != null) {
				if(!isVerCompatibleWithAnyOf(sensorDetails.mSensorDetailsRef.mListOfCompatibleVersionInfo)) {
					sensorDetails.setIsEnabled(false);
					if(sensorDetails.isDerivedChannel()) {
						mDerivedSensors &= ~sensorDetails.mDerivedSensorBitmapID;
					}
					setDefaultConfigForSensor(sensorId, sensorDetails.isEnabled());
				}
			}
		}
	}
	
	protected void setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
			if(abstractSensor.setDefaultConfigForSensor(sensorId, isSensorEnabled)){
				//Sensor found, break
				break;
			}
		}
	}
	
	/**
	 * This method has been deprecated, and we recommend users to use either {@link com.shimmerresearch.driver.ShimmerDevice#setSensorEnabledState(int sensorId, boolean state)} 
	 * <br> or {@link com.shimmerresearch.driver.ShimmerDevice#setSensorIdsEnabled(Integer[] sensorIds)}.
	 * <p>
	 * The enabled sensors that are set in the ShimmerDevice class can then be written to the physical device by the following methods:<br>
	 * A) Clone device - Create a virtual representation of a Shimmer device by calling deepClone(). Update the sensor states and/or other desired settings on the clone device. 
	 * Call {@link AssembleShimmerConfig} to generate a Shimmer config for the clone. Then call configureShimmer(clone) from ShimmerBluetoothManager to write the clone settings to the physical device.
	 * <p> B) Call {@link #writeConfigBytes()} after changing the sensor states. 
	 * @param mEnabledSensors
	 */
	@Deprecated
	public void setEnabledSensors(long mEnabledSensors) {
		this.mEnabledSensors = mEnabledSensors;
	}
	
	public long getEnabledSensors() {
		return mEnabledSensors;
	}

	public void setDerivedSensors(long mDerivedSensors) {
		this.mDerivedSensors = mDerivedSensors;
	}
	
	public long getDerivedSensors() {
		return mDerivedSensors;
	}

	public void setEnabledAndDerivedSensorsAndUpdateMaps(long enabledSensors, long derivedSensors) {
		setEnabledAndDerivedSensorsAndUpdateMaps(enabledSensors, derivedSensors, null);
	}
	
	public void setEnabledAndDerivedSensorsAndUpdateMaps(long enabledSensors, long derivedSensors, COMMUNICATION_TYPE commsType) {
		setEnabledSensors(enabledSensors);
		setDerivedSensors(derivedSensors);
		sensorMapUpdateFromEnabledSensorsVars(commsType);
		algorithmMapUpdateFromEnabledSensorsVars();
		
//		setShimmerAndSensorsSamplingRate(getSamplingRateShimmer());
		setSamplingRateSensors(getSamplingRateShimmer());
		setSamplingRateAlgorithms(getSamplingRateShimmer());
		
		generateParserMap();
	}
	
	protected void handleSpecialCasesAfterSensorMapCreate() {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.handleSpecialCasesAfterSensorMapCreate();
		}
	}
	

	public void refreshEnabledSensorsFromSensorMap(){
		if(mSensorMap!=null) {
			
//			//Debugging
//			printSensorParserAndAlgoMaps();
			
			if (!isShimmerGen2()) {
//			if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_4_SDK) {
				long enabledSensors = 0;
				sensorMapCheckandCorrectHwDependencies();
				List<SensorDetails> listOfEnabledSensors  = getListOfEnabledSensors();
				for (SensorDetails sED:listOfEnabledSensors) {
					enabledSensors |= sED.mSensorDetailsRef.mSensorBitmapIDSDLogHeader;
				}
				setEnabledSensors(enabledSensors);
				
				updateDerivedSensors();
				
				handleSpecCasesUpdateEnabledSensors();
			}
		}
	}
	
	/** For use when debugging */
	public void printSensorParserAndAlgoMaps(){
		//For debugging
		consolePrintLn("");
		consolePrintLn("Enabled Sensors\t" + UtilShimmer.longToHexStringWithSpacesFormatted(mEnabledSensors, 5));
		consolePrintLn("Derived Sensors\t" + UtilShimmer.longToHexStringWithSpacesFormatted(mDerivedSensors, 8));
		
		consolePrintLn("SENSOR MAP");
		for(SensorDetails sensorDetails:mSensorMap.values()){
			consolePrintLn("\tSENSOR\t" + sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel + "\tIsEnabled:\t" + sensorDetails.isEnabled());
			if(sensorDetails.mListOfChannels.size()==0){
				consolePrintLn("\t\t"  + "Channels Missing!");
			} else {
				for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
					consolePrintLn("\t\tChannel:\t Channel:" + channelDetails.getChannelObjectClusterName() + "\tDbName:" + channelDetails.getDatabaseChannelHandle());
				}
			}
		}
		consolePrintLn("");
		
		consolePrintLn("PARSER MAP" + "\tSize=" + mParserMap.keySet().size());
		for(COMMUNICATION_TYPE commType:mParserMap.keySet()){
			consolePrintLn("PARSER MAP\tCOMM TYPE:\t" + commType);
			for(SensorDetails sensorDetails:mParserMap.get(commType).values()){
				consolePrintLn("\tSENSOR\t" + sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel);
				if(sensorDetails.mListOfChannels.size()==0){
					consolePrintLn("\t\t"  + "Channels Missing!");
				} else {
					for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
						consolePrintLn("\t\tNumBytes:" + channelDetails.mDefaultNumBytes + "\tChannel:" + channelDetails.getChannelObjectClusterName() + "\tDbName:" + channelDetails.getDatabaseChannelHandle());
					}
				}
			}
			consolePrintLn("");
		}
		consolePrintLn("");
		
		consolePrintLn("ALGO MAP");
		List<AbstractAlgorithm> mapOfEnabledAlgoModules = getListOfAlgorithmModules();
		for(AbstractAlgorithm abstractAlgorithm:mapOfEnabledAlgoModules){
			consolePrintLn("\tALGO\t" + abstractAlgorithm.getAlgorithmName() + "\tIsEnabled:\t" + abstractAlgorithm.isEnabled());
			List<ChannelDetails> listOfChannelDetails = abstractAlgorithm.getChannelDetails();
			for(ChannelDetails channelDetails:listOfChannelDetails){
				consolePrintLn("\t\tChannel:\t" + channelDetails.getChannelObjectClusterName() + "\tDbName:" + channelDetails.getDatabaseChannelHandle());
			}
		}
		consolePrintLn("");
		
		LinkedHashMap<COMMUNICATION_TYPE, List<ChannelDetails>> mapOfOjcChannels = generateObjectClusterIndexes();
		consolePrintLn("\tObjectClusterIndexes:");
		for(COMMUNICATION_TYPE commType:mapOfOjcChannels.keySet()) {
			consolePrintLn("\tComm Type: " + commType);
			List<ChannelDetails> listOfOjcChannels = mapOfOjcChannels.get(commType);
			for(int i=0;i<listOfOjcChannels.size();i++) {
				consolePrintLn("\t\t" + i + "\t" + listOfOjcChannels.get(i).mObjectClusterName);
			}
		}
		
		consolePrintLn("");
	}
	
	public LinkedHashMap<COMMUNICATION_TYPE, List<ChannelDetails>> generateObjectClusterIndexes() {
		LinkedHashMap<COMMUNICATION_TYPE, List<ChannelDetails>> mapOfOjcChannels = new LinkedHashMap<COMMUNICATION_TYPE, List<ChannelDetails>>();
		
		for(COMMUNICATION_TYPE commType:mParserMap.keySet()) {
			List<ChannelDetails> listOfOjcChannels = new ArrayList<ChannelDetails>();
			
			TreeMap<Integer, SensorDetails> mapPerCommType = mParserMap.get(commType);
			for(SensorDetails sensorDetails:mapPerCommType.values()){
				for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
					listOfOjcChannels.add(channelDetails);
				}
			}
			
			// TODO need an enabled algo per commType map
			List<AbstractAlgorithm> mapOfEnabledAlgoModules = getListOfEnabledAlgorithmModules();
			for(AbstractAlgorithm abstractAlgorithm:mapOfEnabledAlgoModules){
				List<ChannelDetails> listOfChannelDetails = abstractAlgorithm.getChannelDetails();
				for(ChannelDetails channelDetails:listOfChannelDetails){
					listOfOjcChannels.add(channelDetails);
				}
			}
			mapOfOjcChannels.put(commType, listOfOjcChannels);
		}
		
		return mapOfOjcChannels;
	}
	
	/** added special cases (e.g., for SensorEXG) */
	public void handleSpecCasesUpdateEnabledSensors() {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.handleSpecCasesUpdateEnabledSensors();
		}
	}
	
	
	public List<SensorDetails> getListOfEnabledSensors(){
		List<SensorDetails> listOfEnabledSensors = new ArrayList<SensorDetails>();
		for (SensorDetails sED:mSensorMap.values()) {
			if (sED.isEnabled()) {
				listOfEnabledSensors.add(sED);
			}
		}
		return listOfEnabledSensors;
	}
	
	public void algorithmMapUpdateFromEnabledSensorsVars() {
//		consolePrintErrLn("algorithmMapUpdateFromEnabledSensorsVars\tmDerivedSensors = " + UtilShimmer.longToHexStringWithSpacesFormatted(mDerivedSensors, 8));
		for(AbstractAlgorithm aA:getMapOfAlgorithmModules().values()){
			if(mEnableDerivedSensors){
				aA.algorithmMapUpdateFromEnabledSensorsVars(mDerivedSensors);
			} else {
				aA.algorithmMapUpdateFromEnabledSensorsVars(0);
			}
		}
		initializeAlgorithms();
	}
	
//	public void sensorMapUpdateFromEnabledSensorsVars(COMMUNICATION_TYPE commType) {
//		for(AbstractSensor sensor:mMapOfSensorClasses.values()){
//			sensor.updateStateFromEnabledSensorsVars(commType, mEnabledSensors, mDerivedSensors);
//		}
//	}
	
	public void sensorMapUpdateFromEnabledSensorsVars() {
		sensorMapUpdateFromEnabledSensorsVars(null);
	}

	/**
	 * Used to convert from the enabledSensors long variable read from the
	 * Shimmer to the set enabled status of the relative entries in the Sensor
	 * Map. Used in Consensys for dynamic GUI generation to configure a Shimmer.
	 * 
	 */
	public void sensorMapUpdateFromEnabledSensorsVars(COMMUNICATION_TYPE commType) {
		//TODO should this be done afterwards (as well?)?
		handleSpecCasesBeforeSensorMapUpdateGeneral();

		if(mSensorMap==null){
			sensorAndConfigMapsCreate();
		}
		
		if(mSensorMap!=null) {
			mapLoop:
			for(Integer sensorId:mSensorMap.keySet()) {
				if(handleSpecCasesBeforeSensorMapUpdatePerSensor(sensorId)){
					continue mapLoop;
				}
					
				// Process remaining channels
				SensorDetails sensorDetails = getSensorDetails(sensorId);
				//TODO test out commType approach
//				sensorDetails.updateFromEnabledSensorsVars(commType, mEnabledSensors, mDerivedSensors);
				sensorDetails.updateFromEnabledSensorsVars(mEnabledSensors, mDerivedSensors);
			}
				
			// Now that all main sensor channels have been parsed, deal with
			// sensor channels that have special conditions. E.g. deciding
			// what type of signal the ExG is configured for or what derived
			// channel is enabled like whether PPG is on ADC12 or ADC13
			handleSpecCasesAfterSensorMapUpdateFromEnabledSensors();
		}
		
//		//Debugging
//		printSensorParserAndAlgoMaps();
	}

	public void handleSpecCasesBeforeSensorMapUpdateGeneral() {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.handleSpecCasesBeforeSensorMapUpdateGeneral(this);
		}
	}

	public boolean handleSpecCasesBeforeSensorMapUpdatePerSensor(Integer sensorId) {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			if(abstractSensor.handleSpecCasesBeforeSensorMapUpdatePerSensor(this, sensorId)){
				return true;
			}
		}
		return false;
	}

	public void handleSpecCasesAfterSensorMapUpdateFromEnabledSensors() {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.handleSpecCasesAfterSensorMapUpdateFromEnabledSensors();
		}
	}



	public List<Integer> sensorMapConflictCheck(Integer key){
		List<Integer> listOfChannelConflicts = new ArrayList<Integer>();
		
		//TODO: handle Shimmer2/r exceptions which involve get5VReg(), getPMux() and writePMux()
		
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_4_SDK || getHardwareVersion() == HW_ID.SHIMMER_3R){
			if(getSensorDetails(key).mSensorDetailsRef.mListOfSensorIdsConflicting != null) {
				for(Integer sensorId:getSensorDetails(key).mSensorDetailsRef.mListOfSensorIdsConflicting) {
					if(isSensorEnabled(sensorId)) {
						listOfChannelConflicts.add(sensorId);
					}
				}
			}
		}
		
		if(listOfChannelConflicts.isEmpty()) {
			return null;
		}
		else {
			return listOfChannelConflicts;
		}
	}
	
	/**
	 * Method to set force defaults for disabled sensors. Need to ensure
	 * consistency across all configured Shimmers. Without the below, if a
	 * Shimmer is read from and then configured without changing any of the
	 * configuration, the configuration will not be checked. Another application
	 * could have saved incorrect configuration to the Shimmer.
	 * 
	 */
	public void checkShimmerConfigBeforeConfiguring() {
		//1) call checkShimmerConfigBeforeConfiguring in each AbstractSensor
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.checkShimmerConfigBeforeConfiguring();
		}
		
		//2) 
		checkIfInternalExpBrdPowerIsNeeded();
	}

	
	/**
	 * @return a refreshed version of the current mShimmerInfoMemBytes
	 */
	public byte[] refreshShimmerInfoMemBytes() {
//		System.out.println("SlotDetails:" + this.mUniqueIdentifier + " " + mShimmerInfoMemBytes[3]);
		return configBytesGenerate(false);
	}
	
	public void checkAndCorrectShimmerName(String shimmerName) {
		// Set name if nothing was read from InfoMem
		if(!shimmerName.isEmpty()) {
			mShimmerUserAssignedName = new String(shimmerName);
		}
		else {
			mShimmerUserAssignedName = DEFAULT_SHIMMER_NAME + "_" + getMacIdFromUartParsed();
		}
	}


	//TODO fill out in abstract sensor classes based on ShimmerObject equivalent
	public void checkConfigOptionValues(String stringKey){
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
		
			 if(abstractSensor.checkConfigOptionValues(stringKey)){
				break;
			}  
		
		}
	}

	/**
	 * Checks a specific sensor class to see if it is using it's default
	 * calibration parameters. Used, for example, in SensorLSM, SensorMPU and
	 * SensorKionix.
	 * 
	 * @param sensorId
	 * @return boolean indicating that the sensor is using default calibration
	 *         parameters.
	 */
	public boolean isSensorUsingDefaultCal(int sensorId) {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			SensorDetails sensorDetails = abstractSensor.getSensorDetails(sensorId);
			if(sensorDetails!=null){
				return abstractSensor.isSensorUsingDefaultCal(sensorId);
			}
		}
		return false;
	}
	
	//TODO tidy up implementation of below, overwritten and handled differently in Shimmer4, ShimmerPC, NoninOnyxII
	public boolean setBluetoothRadioState(BT_STATE state){
		boolean changed=true;
		if (mBluetoothRadioState.toString().equals(state.toString())){
			changed=false;
		}
		BT_STATE stateStored = mBluetoothRadioState;
		mBluetoothRadioState = state;
		consolePrintLn("State change: Was:" + stateStored.toString() + "\tIs now:" + mBluetoothRadioState);
		if(!mUpdateOnlyWhenStateChanges){
			return true;
		}
		return changed;
	}
	
	
	public BT_STATE getBluetoothRadioState() {
		return mBluetoothRadioState;
	}
	
	public String getBluetoothRadioStateString() {
		return mBluetoothRadioState.toString();
	}

	/** Generic method that can be overwritten in instances of ShimmerDevice
	 * @throws ShimmerException
	 */
	public void connect() throws ShimmerException{
		
	}

	/** Generic method that can be overwritten in instances of ShimmerDevice
	 * @throws ShimmerException
	 */
	public void disconnect() throws ShimmerException {
		//TODO
//		stopRecording();
		stopStreaming();
	}

	public boolean ignoreAndDisable(Integer sensorId) {
		//Check if a derived channel is enabled, if it is ignore disable and skip 
//		innerloop:
		SensorDetails sensorDetails = getSensorDetails(sensorId);
		if(sensorDetails!=null){
			for(Integer conflictKey:sensorDetails.mSensorDetailsRef.mListOfSensorIdsConflicting) {
				SensorDetails conflictingSensor = getSensorDetails(conflictKey);
				if(conflictingSensor!=null){
					if(conflictingSensor.isDerivedChannel()) {
						if((mDerivedSensors&conflictingSensor.mDerivedSensorBitmapID)>0) {
							sensorDetails.setIsEnabled(false);
							return true;
//							skipKey = true;
//							break innerloop;
						}
					}
				}
			}
			
		}
		return false;
	}
	

	// ------------- Algorithm Code Start -----------------------
	public void updateDerivedSensors(){
		setDerivedSensors(0);

		updateDerivedSensorsFromSensorMap();
		updateDerivedSensorsFromAlgorithmMap();
	}

	private void updateDerivedSensorsFromSensorMap(){
		List<SensorDetails> listOfEnabledSensors  = getListOfEnabledSensors();
		for (SensorDetails sED:listOfEnabledSensors) {
			if (sED.isDerivedChannel()) {
				mDerivedSensors |= sED.mDerivedSensorBitmapID;
			}
		}
	}

	private void updateDerivedSensorsFromAlgorithmMap(){
		List<AbstractAlgorithm> listOfEnabledAlgorithms = getListOfEnabledAlgorithmModules();
		for(AbstractAlgorithm aA:listOfEnabledAlgorithms){
			mDerivedSensors |= aA.getDerivedSensorBitmapID();
		}
	}
	
	public List<AlgorithmDetails> getListOfEnabledAlgorithmDetails(){
		List<AlgorithmDetails> listOfEnabledAlgorithms = new ArrayList<AlgorithmDetails>();
		for(AbstractAlgorithm aa:getListOfEnabledAlgorithmModules()){
			listOfEnabledAlgorithms.add(aa.mAlgorithmDetails);
		}
		return listOfEnabledAlgorithms;
	}
	
	public List<AbstractAlgorithm> getListOfEnabledAlgorithmModules(){
		List<AbstractAlgorithm> listOfEnabledAlgorithms = new ArrayList<AbstractAlgorithm>();
		for(AbstractAlgorithm aa:mMapOfAlgorithmModules.values()){
			if(aa.isEnabled()){
				listOfEnabledAlgorithms.add(aa);
			}
		}
		return listOfEnabledAlgorithms;
	}

	public List<AbstractAlgorithm> getListOfAlgorithmModules(){
		return new ArrayList<AbstractAlgorithm>(mMapOfAlgorithmModules.values());
	}

	public void setIsAlgorithmEnabledAndSyncGroup(String algorithmName, String groupName, boolean state){
		AbstractAlgorithm abstractAlgorithmToChange = getMapOfAlgorithmModules().get(algorithmName);
		if(abstractAlgorithmToChange!=null){
			// Sync all settings for the group and pass back the configuration
			// (just a precaution as they should all have the same settings even
			// if they are enabled/disabled)
			HashMap<String, Object> mapOfAlgoSettings = syncAlgoGroupConfig(groupName, state);			
			
			setIsAlgorithmEnabled(algorithmName, state);
			
			//Load all settings for the group only if the algorithm has been enabled
			// (just a precaution as they should all have the same settings even
			// if they are enabled/disabled)
			if(state && mapOfAlgoSettings!=null){
				abstractAlgorithmToChange.setAlgorithmSettings(mapOfAlgoSettings);
			}
			
			//Set default settings if the algorithm group has been disabled or freshly enabled
			List<AbstractAlgorithm> listOfEnabledAlgosForGroup = getListOfEnabledAlgorithmModulesPerGroup(groupName);
			if((listOfEnabledAlgosForGroup.size()==0) 				//no algos in group enabled, so set defaults for off
				||(state && listOfEnabledAlgosForGroup.size()==1)){	// this is the first algo to be enabled in this group, set the default for on for the group
				setDefaultSettingsForAlgorithmGroup(groupName);
			}
			
			//Sync again
			syncAlgoGroupConfig(groupName, state);
		}
	}

	public void setIsAlgorithmEnabled(String algorithmName, boolean state){
		AbstractAlgorithm abstractAlgorithm = mMapOfAlgorithmModules.get(algorithmName);

		if(abstractAlgorithm!=null && abstractAlgorithm.mAlgorithmDetails!=null){
			if(state){ 
				//switch on the required sensors
				for (Integer sensorId:abstractAlgorithm.mAlgorithmDetails.mListOfRequiredSensors) {
					if (abstractAlgorithm.mAlgorithmDetails.mSensorCheckMethod==SENSOR_CHECK_METHOD.ANY) {
						
						int tempSensorId = handleSpecCasesBeforeSetSensorState(sensorId,state);
						SensorDetails sensorDetails = getSensorDetails(tempSensorId);
						if(sensorDetails!=null){
							setSensorEnabledState(sensorId, true);
						}
					}
					else if (abstractAlgorithm.mAlgorithmDetails.mSensorCheckMethod==SENSOR_CHECK_METHOD.ALL) {
						boolean success = setSensorEnabledState(sensorId, true);
						if(!success){
							consolePrintErrLn("Failed to enable required sensor ID " + sensorId + " for algorithm:\t" + abstractAlgorithm.getAlgorithmName());
							return;
						}
					}
				}
			}
			abstractAlgorithm.setIsEnabled(state);
		}
		else{
			consolePrintErrLn(algorithmName + " doesn't exist in this device");
		}
		updateDerivedSensors();
		initializeAlgorithms();
	}
	
	public void disableAllAlgorithms(){
		for(AbstractAlgorithm abstractAlgorithmToChange: getMapOfAlgorithmModules().values()){
			setIsAlgorithmEnabled(abstractAlgorithmToChange.getAlgorithmName(), false);
		}
	}
	
	public boolean isECGAlgoEnabled(AbstractAlgorithm abstractAlgorithm){
		SensorDetails ecgSensorDetails = getSensorDetails(Configuration.Shimmer3.SENSOR_ID.HOST_ECG);
		SensorDetails respSensorDetails = getSensorDetails(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION);
		
		if (ecgSensorDetails!=null && ecgSensorDetails.isEnabled() && abstractAlgorithm.mAlgorithmName.contains("ECGtoHR")) {
			return true;
		}
		if (respSensorDetails!=null && ecgSensorDetails.isEnabled() && abstractAlgorithm.mAlgorithmName.contains("ECGtoHR")) {
			return true;
		}
		
		return false;
	}
		
	/** check to ensure sensors that algorithm requires haven't been switched off */
	public void algorithmRequiredSensorCheck() {
		for(SensorGroupingDetails sGD:mMapOfAlgorithmGrouping.values()){
			// looping through algorithms to see which ones are enabled
			for (AlgorithmDetails algorithmDetails:sGD.mListOfAlgorithmDetails) {
				AbstractAlgorithm abstractAlgorithm = mMapOfAlgorithmModules.get(algorithmDetails.mAlgorithmName);
				if (abstractAlgorithm!=null && abstractAlgorithm.isEnabled()) { // run check to see if accompanying sensors
					innerLoop:
					for (Integer sensor:abstractAlgorithm.mAlgorithmDetails.mListOfRequiredSensors) {
						if (isECGAlgoEnabled(abstractAlgorithm)) {
							break innerLoop;
						}
						SensorDetails sensorDetails = getSensorDetails(sensor);
						if (sensorDetails!=null && !sensorDetails.isEnabled()) {
							setIsAlgorithmEnabledAndSyncGroup(abstractAlgorithm.mAlgorithmName, sGD.mGroupName, false);
							break innerLoop;
						}
					}
				}
			}
		}
		initializeAlgorithms();
	}
	
	
	protected List<String[]> getListofEnabledAlgorithmsSignalsandFormats(){
		List<String[]> listAlgoSignalProperties = new ArrayList<String[]>();
		List<AlgorithmDetails> listOfEnabledAlgorithms = getListOfEnabledAlgorithmDetails();
		for(AlgorithmDetails aD:listOfEnabledAlgorithms){
			String[] signalStringArray = aD.getSignalStringArray();
			//TODO is below needed?
			signalStringArray[0] = mShimmerUserAssignedName;
			listAlgoSignalProperties.add(signalStringArray);
		}
		return listAlgoSignalProperties;
	}
	
	/** returns list of compatible algorithms based on Shimmer hardware */
	public Map<String, AbstractAlgorithm> getSupportedAlgorithmChannels(){
		Map<String, AbstractAlgorithm> mSupportedAlgorithmChannelsMap = new LinkedHashMap<String, AbstractAlgorithm>();
		for (AbstractAlgorithm aA:mMapOfAlgorithmModules.values()) {
			for (Integer sensorId:aA.mAlgorithmDetails.mListOfRequiredSensors) {
				if (mSensorMap.containsKey(sensorId)) {
					mSupportedAlgorithmChannelsMap.put(aA.getAlgorithmName(), aA);
				}
			}
		}		
		return mSupportedAlgorithmChannelsMap;
	}
	
	/** This pass back a map of the Algorithm groups so that a GUI can generate configuration options
	 * @return
	 */
	public TreeMap<Integer, SensorGroupingDetails> getMapOfAlgorithmGrouping() {
		TreeMap<Integer, SensorGroupingDetails> algorithmGroupingMap = new TreeMap<Integer, SensorGroupingDetails>(); 
    	for(AbstractAlgorithm abstractAlgorithm:mMapOfAlgorithmModules.values()) {
    		algorithmGroupingMap.putAll(abstractAlgorithm.mMapOfAlgorithmGrouping);
    	}
    	mMapOfAlgorithmGrouping = algorithmGroupingMap;
		return mMapOfAlgorithmGrouping;
	}

	public TreeMap<Integer, SensorGroupingDetails> getMapOfAlgorithmGroupingEnabled() {
		TreeMap<Integer, SensorGroupingDetails> algorithmGroupingMap = new TreeMap<Integer, SensorGroupingDetails>(); 
    	for(AbstractAlgorithm abstractAlgorithm:getListOfEnabledAlgorithmModules()) {
    		algorithmGroupingMap.putAll(abstractAlgorithm.mMapOfAlgorithmGrouping);
    	}
    	mMapOfAlgorithmGrouping = algorithmGroupingMap;
		return mMapOfAlgorithmGrouping;
	}

//	private boolean checkIfToEnableAlgo(AlgorithmDetails algorithmDetails){
//		for(Integer sensorId:algorithmDetails.mListOfRequiredSensors){
//			boolean isSensorEnabled = isSensorEnabled(sensorId); 
//			if(algorithmDetails.mSensorCheckMethod==SENSOR_CHECK_METHOD.ANY){
//				if(isSensorEnabled){
//					//One of the required sensors is enabled -> create algorithm
//					return true;
//				}
//			}
//			else if(algorithmDetails.mSensorCheckMethod==SENSOR_CHECK_METHOD.ALL){
//				if(!isSensorEnabled){
//					//One of the required sensors is not enabled -> continue to next algorithm
//					return false;
//				}
//			}
//		}
//		return true;
//	}
	
	protected void generateMapOfAlgorithmConfigOptions(){
		mConfigOptionsMapAlgorithms = new HashMap<String, ConfigOptionDetails>();
		for(AbstractAlgorithm abstractAlgorithm:mMapOfAlgorithmModules.values()){
			HashMap<String, ConfigOptionDetails> configOptionsMapPerAlgorithm = abstractAlgorithm.getConfigOptionsMap();
			
			if(configOptionsMapPerAlgorithm!=null && configOptionsMapPerAlgorithm.keySet().size()>0){
				// taking out duplicates for orientation algorithm config options 
				for(String s: configOptionsMapPerAlgorithm.keySet()){
					if(mConfigOptionsMapAlgorithms.containsKey(s)){
						//do nothing 
					}
					else{
						mConfigOptionsMapAlgorithms.put(s,configOptionsMapPerAlgorithm.get(s));
					}				
				}
			}
		}
	}
	
	protected void generateMapOfAlgorithmGroupingMap() {
		mMapOfAlgorithmGrouping = new TreeMap<Integer, SensorGroupingDetails>();
		for(AbstractAlgorithm abstractAlgorithm:mMapOfAlgorithmModules.values()){
			TreeMap<Integer, SensorGroupingDetails> algorithmGroupingMap = abstractAlgorithm.mMapOfAlgorithmGrouping;
			if(algorithmGroupingMap!=null && algorithmGroupingMap.keySet().size()>0){
				mMapOfAlgorithmGrouping.putAll(algorithmGroupingMap);
			}
		}
	}

	/**
	 * Overwritten in ShimmerPCMSS and ShimmerSDLog as they have access to
	 * closed source algorithms. Is used to set the mDataProcessing variable
	 */
	public void setupDataProcessing() {
		// TODO Auto-generated method stub
	}


	/**
	 * Load general algorithm modules here. Method can be overwritten in order
	 * to load licenced Shimmer algorithms - as done in ShimmerPCMSS
	 */
	protected void generateMapOfAlgorithmModules(){
//		consolePrintErrLn("generateMapOfAlgorithmModules");

		mMapOfAlgorithmModules = new HashMap<String, AbstractAlgorithm>();
		
		loadAlgorithms(OPEN_SOURCE_ALGORITHMS, null);
		
		//TODO temporarily locating updateMapOfAlgorithmModules() in DataProcessing
		if(mDataProcessing!=null){
			mDataProcessing.updateMapOfAlgorithmModules(this);
		}

		// TODO load algorithm modules automatically from any included algorithm
		// jars depending on license?
	}

	public void loadAlgorithms(List<AlgorithmLoaderInterface> listOfAlgorithms, COMMUNICATION_TYPE commType) {
		for(AlgorithmLoaderInterface algorithmLoader:listOfAlgorithms){
			algorithmLoader.initialiseSupportedAlgorithms(this, commType);
		}
	}

	public void setAlgoProcessingSequence(ArrayList<String> algoOrder) {
		mAlgorithmProcessingSequence = algoOrder;
	}
	
	public Map<String,AbstractAlgorithm> getMapOfAlgorithmModules(){
		return mMapOfAlgorithmModules;
	}

	public AbstractAlgorithm getAlgorithmModule(String algorithmName){
		return mMapOfAlgorithmModules.get(algorithmName);
	}

	public void addAlgorithmModule(AbstractAlgorithm abstractAlgorithm) {
		mMapOfAlgorithmModules.put(abstractAlgorithm.mAlgorithmName, abstractAlgorithm);
	}

	protected void initializeAlgorithms() {
		for (AbstractAlgorithm aa:mMapOfAlgorithmModules.values()){
			try {
				if(aa.isEnabled()){
//				if(aa.isEnabled() && !aa.isInitialized()){
					aa.setShimmerSamplingRate(getSamplingRateShimmer());
					aa.initialize();
				}
				else if(!aa.isEnabled()){
					//orientationChannelSync(aa.mAlgorithmName, aa.isEnabled());
//					aa.reset();
					//TODO stop the algorithm
				}
			} catch (Exception e1) {
				consolePrintException("Error initialising algorithm module\t" + aa.getAlgorithmName(), e1.getStackTrace());
			}
		}
	}

	@Deprecated
	public boolean doesAlgorithmAlreadyExist(AbstractAlgorithm obj){
		for (AbstractAlgorithm aA:mMapOfAlgorithmModules.values())
		{
			if (aA.getAlgorithmName().equals(obj.getAlgorithmName())){
				return true;
			}
		}
		return false;
	}
	
	@Deprecated
	public void removeAlgorithm(AbstractAlgorithm aobj){
		mMapOfAlgorithmModules.remove(aobj);
	}
	
	@Deprecated
	public void removeAlgorithm(String algoName){
		int index=0;
		int keepIndex=-1;
		for (AbstractAlgorithm aA:mMapOfAlgorithmModules.values()){
			if (aA.getAlgorithmName().equals(algoName)){
				keepIndex = index;
			}
			index++;
		}
		
		if (keepIndex>=0){
			mMapOfAlgorithmModules.remove(keepIndex);
		}
	}
	
	public void loadAlgorithmVariablesFromAnotherDevice(ShimmerDevice shimmerDevice) {
		Map<String, AbstractAlgorithm> mapOfSourceAlgorithmModules = shimmerDevice.getMapOfAlgorithmModules();
		Iterator<Entry<String, AbstractAlgorithm>> iterator = mapOfSourceAlgorithmModules.entrySet().iterator();
		while(iterator.hasNext()){
			Entry<String, AbstractAlgorithm> algorithmModuleEntry = iterator.next();
			
			AbstractAlgorithm abstractAlgorithm = getAlgorithmModule(algorithmModuleEntry.getKey());
			if(abstractAlgorithm!=null){
				abstractAlgorithm.loadAlgorithmVariables(algorithmModuleEntry.getValue());
			}
			
		}
	}
	
	public HashMap<String, Object> syncAlgoGroupConfig(String groupName, boolean enabled){
		HashMap<String, Object> mapOfAlgoSettings = null;
		
		List<AbstractAlgorithm> listOfEnabledAlgoModulesPerGroup = getListOfEnabledAlgorithmModulesPerGroup(groupName);
		//check if another algorithm in the group has been enabled 
		if(listOfEnabledAlgoModulesPerGroup!=null && listOfEnabledAlgoModulesPerGroup.size()>0){
			//Take the first enabled algorithm settings and configure all the other algorithms to be the same
			AbstractAlgorithm firstEnabledAlgo = listOfEnabledAlgoModulesPerGroup.get(0);
			
			mapOfAlgoSettings = firstEnabledAlgo.getAlgorithmSettings();

			List<AbstractAlgorithm> listOfAlgoModulesPerGroup = getListOfAlgorithmModulesPerGroup(groupName);
			Iterator<AbstractAlgorithm> iterator = listOfAlgoModulesPerGroup.iterator();
			while(iterator.hasNext()){
				AbstractAlgorithm abstractAlgorithm = iterator.next();
				abstractAlgorithm.setAlgorithmSettings(mapOfAlgoSettings);
			}
		}
		else{
			//Set defaults for off?
			List<AbstractAlgorithm> listOfAlgoModulesPerGroup = getListOfAlgorithmModulesPerGroup(groupName);
		}
		
		return mapOfAlgoSettings;
	}
	
	// ------------- Algorithm Code end -----------------------

	
	/**
	 * Computes the closest compatible sampling rate for the Shimmer based on
	 * the passed in 'rate' variable. Also computes the next highest available
	 * sampling rate for the Shimmer's sensors (dependent on pre-set low-power
	 * modes).
	 * 
	 * @param rateHz
	 */
	public void setShimmerAndSensorsSamplingRate(double rateHz){
		setShimmerAndSensorsSamplingRate(null, rateHz);
	}

	public void setShimmerAndSensorsSamplingRate(COMMUNICATION_TYPE communicationType, double rateHz){
		double correctedRate = correctSamplingRate(rateHz); 
		if(communicationType==null){
			setSamplingRateShimmer(correctedRate);
		} else {
			setSamplingRateShimmer(communicationType, correctedRate);
		}

		setSamplingRateSensors(correctedRate);
		setSamplingRateAlgorithms(correctedRate);
	}

	public void setSamplingRateShimmer(double rateHz){
		//For debugging sampling rate issues
//		if(rateHz!=256){
//			System.out.println("Shimmer:" + getMacId() + "\tSetting Shimmer sampling rate to " + rateHz);
//			UtilShimmer.consolePrintCurrentStackTrace();
//		}
		
		Iterator<COMMUNICATION_TYPE> iterator = mMapOfSamplingRatesShimmer.keySet().iterator();
		while(iterator.hasNext()){
			setSamplingRateShimmer(iterator.next(), rateHz);
		}
	}

	public void setSamplingRateShimmer(COMMUNICATION_TYPE communicationType, double rateHz){
//		UtilShimmer.consolePrintCurrentStackTrace();
		if(mListOfAvailableCommunicationTypes.contains(communicationType)){
			mMapOfSamplingRatesShimmer.put(communicationType, rateHz);
		}
	}
	
	/** Returns the max set sampling rate for the available communication types
	 * @return
	 */
	public double getSamplingRateShimmer() {
		double maxSetRate = 0.0;
		Iterator<COMMUNICATION_TYPE> iterator = mMapOfSamplingRatesShimmer.keySet().iterator();
		while(iterator.hasNext()){
			COMMUNICATION_TYPE commType = iterator.next();
			double samplingRate = getSamplingRateShimmer(commType);
			maxSetRate = Math.max(maxSetRate, samplingRate);
		}
		return maxSetRate;
	}

	public double getSamplingRateShimmer(COMMUNICATION_TYPE commsType){
		if(commsType!=null && mMapOfSamplingRatesShimmer!=null && mMapOfSamplingRatesShimmer.containsKey(commsType)){
			double samplingRate = mMapOfSamplingRatesShimmer.get(commsType);
			if(!Double.isNaN(samplingRate)){
				return samplingRate;
			}
		}
		
		//else, return the max value available
		return getSamplingRateShimmer();
	}
	
	public byte[] getSamplingRateBytesShimmer() {
		return convertSamplingRateFreqToBytes(getSamplingRateShimmer(), getSamplingClockFreq());
	}

	/** This is valid for Shimmers that use a 32.768kHz crystal as the basis for their sampling rate
	 * @param rateHz
	 * @return
	 */
	protected double correctSamplingRate(double rateHz) {
		double maxSamplingRateHz = calcMaxSamplingRate();
		double maxShimmerSamplingRateTicks = getSamplingClockFreq();
		
    	// don't let sampling rate < 0 OR > maxRate
    	if(rateHz < 1) {
    		rateHz = 1.0;
    	}
    	else if (rateHz > maxSamplingRateHz) {
    		rateHz = maxSamplingRateHz;
    	}
    	
    	// RM: get Shimmer compatible sampling rate (use ceil or floor depending on which is appropriate to the user entered sampling rate)
    	Double actualSamplingRate;
    	if((Math.ceil(maxShimmerSamplingRateTicks/rateHz) - maxShimmerSamplingRateTicks/rateHz) < 0.05){
           	actualSamplingRate = maxShimmerSamplingRateTicks/Math.ceil(maxShimmerSamplingRateTicks/rateHz);
    	}
    	else{
        	actualSamplingRate = maxShimmerSamplingRateTicks/Math.floor(maxShimmerSamplingRateTicks/rateHz);
    	}
    	
    	 // round sampling rate to two decimal places
    	actualSamplingRate = (double)Math.round(actualSamplingRate * 100) / 100;
		return actualSamplingRate;
	}
	
	protected static double roundSamplingRateToSupportedValue(double originalSamplingRate, double samplingClockFreq) {
		// get Shimmer compatible sampling rate
    	Double roundedSamplingRate = samplingClockFreq/Math.floor(samplingClockFreq/originalSamplingRate); 
    	// round sampling rate to two decimal places
    	roundedSamplingRate = (double)Math.round(roundedSamplingRate * 100) / 100;
    	return roundedSamplingRate;
	}

	protected static double convertSamplingRateBytesToFreq(byte samplingRateLSB, byte samplingRateMSB, double samplingClockFreq) {
//		//ShimmerObject -> configBytesParse
//		setSamplingRateShimmer((samplingClockFreq/(double)((int)(configBytes[infoMemLayoutCast.idxShimmerSamplingRate] & infoMemLayoutCast.maskShimmerSamplingRate) 
//		+ ((int)(configBytes[infoMemLayoutCast.idxShimmerSamplingRate+1] & infoMemLayoutCast.maskShimmerSamplingRate) << 8))));

//		//ShimmerBluetooth -> processResponseCommand -> SAMPLING_RATE_RESPONSE
//		setSamplingRateShimmer(samplingClockFreq/(double)((int)(bufferSR[0] & 0xFF) + ((int)(bufferSR[1] & 0xFF) << 8)));
		
//		//ShimmerBluetooth -> processAckFromSetCommand -> SET_SAMPLING_RATE_COMMAND
//		tempdouble = samplingClockFreq/(double)((int)(instruction[1] & 0xFF) + ((int)(instruction[2] & 0xFF) << 8));

		//ShimmerObject -> interpretInqResponse
//		setSamplingRateShimmer((samplingClockFreq/(double)((int)(bufferInquiry[0] & 0xFF) + ((int)(bufferInquiry[1] & 0xFF) << 8))));

		double samplingRate = samplingClockFreq / (double)((int)(samplingRateLSB & 0xFF) + ((int)(samplingRateMSB & 0xFF) << 8));
		return samplingRate;
	}
	
	protected static byte[] convertSamplingRateFreqToBytes(double samplingRateFreq, double samplingClockFreq){
		byte[] buf = new byte[2];
		
//		//ShimmerObject -> configBytesGenerate
//		double samplingRateD = getSamplingRateShimmer();
////		int samplingRate = (int)(samplingClockFreq / samplingRateD);
//		int samplingRate = (int) Math.round(samplingClockFreq / samplingRateD);
//		mConfigBytes[infoMemLayout.idxShimmerSamplingRate] = (byte) (samplingRate & infoMemLayout.maskShimmerSamplingRate); 
//		mConfigBytes[infoMemLayout.idxShimmerSamplingRate+1] = (byte) ((samplingRate >> 8) & infoMemLayout.maskShimmerSamplingRate); 

//		//ShimmerBluetooth -> writeShimmerAndSensorsSamplingRate
//		// RM: get Shimmer compatible sampling rate (use ceil or floor depending on which is appropriate to the user entered sampling rate)
//		int samplingByteValue;
//    	if((Math.ceil(samplingClockFreq/getSamplingRateShimmer()) - samplingClockFreq/getSamplingRateShimmer()) < 0.05){
//    		samplingByteValue = (int)Math.ceil(samplingClockFreq/getSamplingRateShimmer());
//    	}
//    	else{
//    		samplingByteValue = (int)Math.floor(samplingClockFreq/getSamplingRateShimmer());
//    	}	
		
		int samplingRate = (int) Math.round(samplingClockFreq / samplingRateFreq);
		buf[0] = (byte) (samplingRate & 0xFF); 
		buf[1] = (byte) ((samplingRate >> 8) & 0xFF); 

		return buf;
	}


	/**
	 * Returns the maximum allowed sampling rate for the Shimmer. This is can be
	 * overridden for a particular version of hardware inside it's respective
	 * extended class (e.g., as done in ShimmerObject)
	 * 
	 * @return
	 */
	protected double calcMaxSamplingRate() {
		double maxGUISamplingRate = 2048.0;
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			double sensorMaxRate = abstractSensor.calcMaxSamplingRate();
			maxGUISamplingRate = Math.min(maxGUISamplingRate, sensorMaxRate);
		}
		return maxGUISamplingRate;
	}

	/**
	 * Sets the sampling rate settings for each of the sensors based on a single
	 * passed in variable (normally the configured Shimmer sampling rate)
	 * 
	 * @param samplingRateShimmer
	 */
	protected void setSamplingRateSensors(double samplingRateShimmer) {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.setSamplingRateFromShimmer(samplingRateShimmer);
		}
	}

	private void setSamplingRateAlgorithms(double samplingRateShimmer) {
		Iterator<AbstractAlgorithm> iterator = mMapOfAlgorithmModules.values().iterator();
		while(iterator.hasNext()){
			AbstractAlgorithm abstractAlgorithm = iterator.next();
//			if(abstractAlgorithm.isEnabled()){
				abstractAlgorithm.setSettings(AbstractAlgorithm.GuiLabelConfigCommon.SHIMMER_SAMPLING_RATE, samplingRateShimmer);
//			}
		}
	}

	public boolean isAlgorithmEnabled(String algorithmName) {
		AbstractAlgorithm abstractAlgorithm = mMapOfAlgorithmModules.get(algorithmName);
		if(abstractAlgorithm!=null && abstractAlgorithm.isEnabled()){
			return true;
		}
		return false;
	}
	
	public boolean isAnyAlgortihmChannelEnabled(List<AlgorithmDetails> listOfAlgorithmDetails){
		for(AlgorithmDetails algortihmDetails:listOfAlgorithmDetails){
			if(isAlgorithmEnabled(algortihmDetails.mAlgorithmName)){
				return true;
			}
		}
		return false;
	}


	public List<AbstractAlgorithm> getListOfEnabledAlgorithmModulesPerGroup(String groupName) {
		for(SensorGroupingDetails sGD:mMapOfAlgorithmGrouping.values()){
			if(sGD.mGroupName.equals(groupName)){
				return getListOfEnabledAlgorithmModulesPerGroup(sGD);
			}
		}
		return null;
	}
	
	public List<AbstractAlgorithm> getListOfEnabledAlgorithmModulesPerGroup(SensorGroupingDetails sGD) {
		List<AbstractAlgorithm> listOfEnabledAlgorthimsPerGroup = new ArrayList<AbstractAlgorithm>();
		if(sGD!=null){
			Map<String, AbstractAlgorithm> mapOfSupportAlgorithms = getSupportedAlgorithmChannels();
			for(AlgorithmDetails aD:sGD.mListOfAlgorithmDetails){
				AbstractAlgorithm aA = mapOfSupportAlgorithms.get(aD.mAlgorithmName);
				if(aA!=null){
					if(aA.isEnabled()){
						listOfEnabledAlgorthimsPerGroup.add(aA);
					}
				}
			}			
		}
		return listOfEnabledAlgorthimsPerGroup;
	}

	public List<AbstractAlgorithm> getListOfAlgorithmModulesPerGroup(String groupName) {
		for(SensorGroupingDetails sGD:mMapOfAlgorithmGrouping.values()){
			if(sGD.mGroupName.equals(groupName)){
				return getListOfAlgorithmModulesPerGroup(sGD);
			}
		}
		return null;
	}
	
	public List<AbstractAlgorithm> getListOfAlgorithmModulesPerGroup(String[] listOfGroupNames){
		
		List<AbstractAlgorithm> listOfAbstractAlgorithms = new ArrayList<AbstractAlgorithm>();
		
		for(String groupName: listOfGroupNames){
			for(SensorGroupingDetails sGD:mMapOfAlgorithmGrouping.values()){
				if(sGD.mGroupName.equals(groupName)){
					listOfAbstractAlgorithms.addAll(getListOfAlgorithmModulesPerGroup(sGD));
				}
			}
		}
		return listOfAbstractAlgorithms;
	}

	public List<AbstractAlgorithm> getListOfAlgorithmModulesPerGroup(SensorGroupingDetails sGD) {
		List<AbstractAlgorithm> listOfEnabledAlgorthimsPerGroup = new ArrayList<AbstractAlgorithm>();
		if(sGD!=null){
			Map<String, AbstractAlgorithm> mapOfSupportAlgorithms = getSupportedAlgorithmChannels();
			for(AlgorithmDetails aD:sGD.mListOfAlgorithmDetails){
				AbstractAlgorithm aA = mapOfSupportAlgorithms.get(aD.mAlgorithmName);
				if(aA!=null){
					listOfEnabledAlgorthimsPerGroup.add(aA);
				}
			}			
		}
		return listOfEnabledAlgorthimsPerGroup;
	}
	
	public void resetAlgorithmBuffers() {
		List<AbstractAlgorithm> listOfEnabledAlgorithmModules = getListOfEnabledAlgorithmModules();
		Iterator<AbstractAlgorithm> iterator = listOfEnabledAlgorithmModules.iterator();
		while(iterator.hasNext()) {
			AbstractAlgorithm abstractAlgorithm = iterator.next();
			try {
				abstractAlgorithm.resetAlgorithmBuffers();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	//---------- Storing to Database related - start --------------------
	
	public LinkedHashMap<String, ChannelDetails> getMapOfEnabledSensorChannelsForStoringToDB(COMMUNICATION_TYPE commType, CHANNEL_TYPE channelType, boolean isKeyOJCName) {
		LinkedHashMap<String, ChannelDetails> mapOfEnabledChannelsForStoringToDb = new LinkedHashMap<String, ChannelDetails>();
		Iterator<SensorDetails> iterator = mSensorMap.values().iterator();
		while(iterator.hasNext()){
			SensorDetails sensorDetails = iterator.next();
			
			boolean isEnabled = false;
			if(commType==null){
				isEnabled = sensorDetails.isEnabled();
			}
			else{
				isEnabled = sensorDetails.isEnabled(commType);
			}
			
			if(isEnabled && !sensorDetails.mSensorDetailsRef.mIsDummySensor){
				for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
					if(channelType!=null && !channelDetails.mListOfChannelTypes.contains(channelType)){
						continue;
					}
					
					if(channelDetails.isStoreToDatabase()){
						String key = (isKeyOJCName ? channelDetails.mObjectClusterName : channelDetails.getDatabaseChannelHandle());
						mapOfEnabledChannelsForStoringToDb.put(key, channelDetails);	
					}
				}
			}
		}
		return mapOfEnabledChannelsForStoringToDb;
	}
	
	//TODO get algorithm isEnabled per commType
	public LinkedHashMap<String, ChannelDetails> getMapOfEnabledAlgortihmChannelsToStoreInDB(COMMUNICATION_TYPE commType, CHANNEL_TYPE channelType, boolean isKeyOJCName) {
		LinkedHashMap<String, ChannelDetails> mapOfEnabledChannelsForStoringToDb = new LinkedHashMap<String, ChannelDetails>();

		Iterator<AbstractAlgorithm> iteratorAlgorithms = getListOfEnabledAlgorithmModules().iterator();
		while(iteratorAlgorithms.hasNext()){
			AbstractAlgorithm algorithm = iteratorAlgorithms.next();
			
			if(!algorithm.mListOfCommunicationTypesSupported.contains(commType)){
				continue;
			}
			
			List<ChannelDetails> listOfDetails = algorithm.getChannelDetails();
			if(listOfDetails!=null){
				for(ChannelDetails channelDetails:listOfDetails){
					if(channelType!=null && !channelDetails.mListOfChannelTypes.contains(channelType)){
						continue;
					}
					
					if(channelDetails.mStoreToDatabase){
						String key = (isKeyOJCName ? channelDetails.mObjectClusterName : channelDetails.getDatabaseChannelHandle());
						mapOfEnabledChannelsForStoringToDb.put(key, channelDetails);
					}
				}
			}
		}

		return mapOfEnabledChannelsForStoringToDb;
	}

	public LinkedHashMap<String, ChannelDetails> getMapOfAllChannelsForStoringToDB(COMMUNICATION_TYPE commType, CHANNEL_TYPE channelType, boolean isKeyOJCName, boolean showDisabledChannels) {
		//TODO get showDisabledChannels working
		
		LinkedHashMap<String, ChannelDetails> mapOfChannelsForStoringToDb = getMapOfEnabledSensorChannelsForStoringToDB(commType, channelType, isKeyOJCName);
		LinkedHashMap<String, ChannelDetails> mapOfAlgoChannelsForStoringToDb = getMapOfEnabledAlgortihmChannelsToStoreInDB(commType, channelType, isKeyOJCName);
		mapOfChannelsForStoringToDb.putAll(mapOfAlgoChannelsForStoringToDb);

		//TODO temp hack. Need to move these channels to their own sensors so that they can be disabled per comm type
		mapOfChannelsForStoringToDb = filterOutUnwantedChannels(mapOfChannelsForStoringToDb, commType, isKeyOJCName);
		
		return mapOfChannelsForStoringToDb;
	}

	//TODO temp hack. Need to move these channels to their own sensors so that they can be disabled per comm type
	private LinkedHashMap<String, ChannelDetails> filterOutUnwantedChannels(LinkedHashMap<String, ChannelDetails> mapOfChannelsForStoringToDb, COMMUNICATION_TYPE commType, boolean isKeyOJCName) {
		String channelToRemove = "";
		if(commType==COMMUNICATION_TYPE.SD){
			channelToRemove = isKeyOJCName? SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP:AbstractSensor.DatabaseChannelHandlesCommon.TIMESTAMP_SYSTEM;
		} else if(commType==COMMUNICATION_TYPE.BLUETOOTH){
			channelToRemove = isKeyOJCName? SensorShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK:SensorShimmerClock.DatabaseChannelHandles.REAL_TIME_CLOCK;
		}
		if(!channelToRemove.isEmpty()){
			mapOfChannelsForStoringToDb.remove(channelToRemove);
		}
		return mapOfChannelsForStoringToDb;
	}

	public LinkedHashMap<String, ChannelDetails> getMapOfChannelsDetailsFromDbHandles(List<String> listOfDbChannelHandles) {
		boolean showDisabledChannels = true;
		boolean showUnknownChannels = true;
		
		// 1) get enabled sensor and algorithms channels to start
		LinkedHashMap<String, ChannelDetails> channelDetailsMap = getMapOfAllChannelsForStoringToDB(null, CHANNEL_TYPE.CAL, false, showDisabledChannels);
		
		// 2) Some algorithms are applied afterwards and aren't registered as
		// being enabled so adding them here. Similarly some channels like
		// Event_Markers are added post data-capture.
		for(SensorDetails sensorDetails:mSensorMap.values()){
			for(ChannelDetails channelDetails:sensorDetails.getListOfChannels()){
				if(!channelDetailsMap.containsKey(channelDetails.getDatabaseChannelHandle())){
					channelDetailsMap.put(channelDetails.getDatabaseChannelHandle(), channelDetails);
				}
			}
		}
		for(AbstractAlgorithm algorithm:mMapOfAlgorithmModules.values()){
			for(ChannelDetails channelDetails:algorithm.getChannelDetails()){
				if(!channelDetailsMap.containsKey(channelDetails.getDatabaseChannelHandle())){
					channelDetailsMap.put(channelDetails.getDatabaseChannelHandle(), channelDetails);
				}
			}
		}
		

		LinkedHashMap<String, ChannelDetails> mapOfChannelsFound = new LinkedHashMap<String, ChannelDetails>();
		for(String dbChannelHandle:listOfDbChannelHandles){
			ChannelDetails channelDetails = channelDetailsMap.get(dbChannelHandle);

			// 3) if still not found, create one anyway
			if(channelDetails==null){
				//not an enabled channel (could be post processed - algorithm or event marker)
				if(showUnknownChannels){
					channelDetails = new ChannelDetails();
					channelDetails.mGuiName = dbChannelHandle;
					channelDetails.mObjectClusterName = dbChannelHandle;
					String dbColumnName = dbChannelHandle.replace("-", "_");
					channelDetails.setDatabaseChannelHandle(dbColumnName);
				}
			}
			
			if(channelDetails!=null){
				//TODO temp hack to remove a DUMMY channel from ECG (for old DBs only?)
				if(channelDetails.mObjectClusterName.contains("_DUMMY_")){
					continue;
				}

				mapOfChannelsFound.put(channelDetails.getDatabaseChannelHandle(), channelDetails);
			}
		}
		
		return mapOfChannelsFound;
	}

	
	public LinkedHashMap<String, Object> generateConfigMap(COMMUNICATION_TYPE commType){
		LinkedHashMap<String, Object> mapOfConfig = new LinkedHashMap<String, Object>();
		
		//General Shimmer configuration
		double samplingRateToStore = commType==null? getSamplingRateShimmer():getSamplingRateShimmer(commType);
		mapOfConfig.put(DatabaseConfigHandle.SAMPLE_RATE, samplingRateToStore);
		mapOfConfig.put(DatabaseConfigHandle.ENABLE_SENSORS, getEnabledSensors());
		mapOfConfig.put(DatabaseConfigHandle.DERIVED_SENSORS, getDerivedSensors());
		
		mapOfConfig.put(DatabaseConfigHandle.SHIMMER_VERSION, getHardwareVersion());
		mapOfConfig.put(DatabaseConfigHandle.FW_VERSION, getFirmwareIdentifier());
		mapOfConfig.put(DatabaseConfigHandle.FW_VERSION_MAJOR, getFirmwareVersionMajor());
		mapOfConfig.put(DatabaseConfigHandle.FW_VERSION_MINOR, getFirmwareVersionMinor());
		mapOfConfig.put(DatabaseConfigHandle.FW_VERSION_INTERNAL, getFirmwareVersionInternal());

		mapOfConfig.put(DatabaseConfigHandle.EXP_BOARD_ID, getExpansionBoardId());
		mapOfConfig.put(DatabaseConfigHandle.EXP_BOARD_REV, getExpansionBoardRev());
		mapOfConfig.put(DatabaseConfigHandle.EXP_BOARD_REV_SPEC, getExpansionBoardRevSpecial());

		mapOfConfig.put(DatabaseConfigHandle.EXP_PWR, getInternalExpPower());
		mapOfConfig.put(DatabaseConfigHandle.CONFIG_TIME, getConfigTime());

		//Sensor configuration
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			LinkedHashMap<String, Object> configMapPerSensor = abstractSensor.generateConfigMap();
			if(configMapPerSensor!=null){
				mapOfConfig.putAll(configMapPerSensor);
			}
		}
		
		//Algorithm configuration
		Iterator<AbstractAlgorithm> iteratorAlgorithms = mMapOfAlgorithmModules.values().iterator();
		while(iteratorAlgorithms.hasNext()){
			AbstractAlgorithm abstractAlgorithm = iteratorAlgorithms.next();
//			if(abstractAlgorithm.isEnabled()){
				LinkedHashMap<String, Object> configMapPerAlgorithm = abstractAlgorithm.generateConfigMap();
				if(configMapPerAlgorithm!=null){
					mapOfConfig.putAll(configMapPerAlgorithm);
				}
//			}
		}
		//Old approach to getting config for DB from Algorithms
//		HashMap<String, Object> algorithmsConfig = getEnabledAlgorithmSettings();
//		mapOfConfig.putAll(algorithmsConfig);
		
		//Useful for debugging
//		printMapOfConfig();
		
		return mapOfConfig;
	}
	
	public void parseConfigMap(ShimmerVerObject svo, LinkedHashMap<String, Object> mapOfConfigPerShimmer, COMMUNICATION_TYPE commType) {
		
		if(svo!=null){
			setShimmerVersionObject(svo);
		}
		
		//General Shimmer configuration
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.EXP_BOARD_ID)
				&& mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.EXP_BOARD_REV)
				&& mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.EXP_BOARD_REV_SPEC)){
			ExpansionBoardDetails eBD = new ExpansionBoardDetails(
					((Double)mapOfConfigPerShimmer.get(DatabaseConfigHandle.EXP_BOARD_ID)).intValue(), 
					((Double)mapOfConfigPerShimmer.get(DatabaseConfigHandle.EXP_BOARD_REV)).intValue(), 
					((Double)mapOfConfigPerShimmer.get(DatabaseConfigHandle.EXP_BOARD_REV_SPEC)).intValue());
			setExpansionBoardDetails(eBD);
		}
		
		sensorAndConfigMapsCreate();

		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.ENABLE_SENSORS)
				&&mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.DERIVED_SENSORS)){
			long enabledSensors = ((Double)mapOfConfigPerShimmer.get(DatabaseConfigHandle.ENABLE_SENSORS)).longValue(); 
			long derivedSensors = ((Double)mapOfConfigPerShimmer.get(DatabaseConfigHandle.DERIVED_SENSORS)).longValue(); 
			setEnabledAndDerivedSensorsAndUpdateMaps(enabledSensors, derivedSensors);
		}

		//For debugging
//		printSensorParserAndAlgoMaps();
		
		
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.SAMPLE_RATE)){
			double samplingRate = (Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.SAMPLE_RATE);
			if(commType==null){
				setSamplingRateShimmer(samplingRate);
			} else {
				setSamplingRateShimmer(commType, samplingRate);
			}
		}
		
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.EXP_PWR)){
			setInternalExpPower(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.EXP_PWR)).intValue());
		}
		
		//Configuration Time
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.CONFIG_TIME)){
			setConfigTime(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.CONFIG_TIME)).longValue());
		}
		
		//Sensor configuration
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.parseConfigMap(mapOfConfigPerShimmer);
		}
		
		//Algorithm configuration
		Iterator<AbstractAlgorithm> iteratorAlgorithms = mMapOfAlgorithmModules.values().iterator();
		while(iteratorAlgorithms.hasNext()){
			AbstractAlgorithm abstractAlgorithm = iteratorAlgorithms.next();
			abstractAlgorithm.parseConfigMapFromDb(mapOfConfigPerShimmer);
		}
		//Old approach to getting config for DB from Algorithms
//		Iterator<AbstractAlgorithm> iteratorAlgo = getListOfAlgorithmModules().iterator();
//		while(iteratorAlgo.hasNext()){
//			AbstractAlgorithm abstractAlgorithm = iteratorAlgo.next();
//			abstractAlgorithm.setAlgorithmSettings(mapOfConfigPerShimmer);
//		}
		
		//Useful for debugging
//		printMapOfConfigForDb();
	}

	public void printMapOfConfig() {
		for(COMMUNICATION_TYPE commType:mListOfAvailableCommunicationTypes){
			HashMap<String, Object> mapOfConfigForDb = generateConfigMap(commType);
			printMapOfConfig(mapOfConfigForDb);
		}
	}

	public static void printMapOfConfig(HashMap<String, Object> mapOfConfigForDb) {
		System.out.println("Printing map of Config for DB, size = " + mapOfConfigForDb.keySet().size());
		for(String configLbl:mapOfConfigForDb.keySet()){
			String stringToPrint = configLbl + " = ";
			Object val = mapOfConfigForDb.get(configLbl);
			
			if(val instanceof String){
				stringToPrint += (String)val; 
			}
			else if(val instanceof Boolean){
				stringToPrint += Boolean.toString((boolean) val); 
			}
			else if(val instanceof Double){
				stringToPrint += Double.toString((double) val); 
			}
			else if(val instanceof Integer){
				stringToPrint += Integer.toString((int) val); 
			}
			else if(val instanceof Long){
				stringToPrint += Long.toString((long) val); 
			}
			System.out.println(stringToPrint);
		}
	}
	
	//---------- Storing to Database related - end --------------------


	public LinkedHashMap<String, ChannelDetails> getMapOfEnabledChannelsForStreaming() {
		return getMapOfEnabledChannelsForStreaming(null);
	}

	public LinkedHashMap<String, ChannelDetails> getMapOfEnabledChannelsForStreaming(COMMUNICATION_TYPE commType) {
		LinkedHashMap<String, ChannelDetails> mapOfChannels = new LinkedHashMap<String, ChannelDetails>();
		Iterator<SensorDetails> iteratorSensors = mSensorMap.values().iterator();
		while(iteratorSensors.hasNext()){
			SensorDetails sensorDetails = iteratorSensors.next();
			
			boolean isEnabled = false;
			if(commType == null){
				isEnabled = sensorDetails.isEnabled();
			}
			else{
				isEnabled = sensorDetails.isEnabled(commType);
			}
			
			if(isEnabled){
				for(ChannelDetails channelDetails : sensorDetails.getListOfChannels()){
					if(channelDetails.isShowWhileStreaming()){
						mapOfChannels.put(channelDetails.mObjectClusterName, channelDetails);
					}
				}
			}
		}
		
		Iterator<AbstractAlgorithm> iteratorAlgorithms = getMapOfAlgorithmModules().values().iterator();
		while(iteratorAlgorithms.hasNext()){
			AbstractAlgorithm abstractAlgorithm = iteratorAlgorithms.next();
			if(abstractAlgorithm.isEnabled()){
				for(ChannelDetails channelDetails : abstractAlgorithm.getChannelDetails()){
					if(channelDetails.isShowWhileStreaming()){
						mapOfChannels.put(channelDetails.mObjectClusterName, channelDetails);
					}
				}
			}
		}
		
		if(mapOfChannels.size()==0){
			consolePrintLn(getMacIdFromUartParsed() + "\tNO SENSORS ENABLED");
		}
		
		return mapOfChannels;
	}
	
	public String[] getListofEnabledChannelSignals(){
		List<String> listofSignals = new ArrayList<String>(getMapOfEnabledChannelsForStreaming().keySet());
//		return listofSignals;
		
		String[] enabledSignals = listofSignals.toArray(new String[listofSignals.size()]);
		return enabledSignals;
	}
	
	public List<String[]> getListofEnabledChannelSignalsandFormats(){
		List<String[]> listofEnabledSensorSignalsandFormats = new ArrayList<String[]>();
		Iterator<ChannelDetails> iterator = getMapOfEnabledChannelsForStreaming().values().iterator();
		while(iterator.hasNext()){
			ChannelDetails channelDetails = iterator.next();
			listofEnabledSensorSignalsandFormats.addAll(channelDetails.getListOfChannelSignalsAndFormats());
		}
		return listofEnabledSensorSignalsandFormats;
	}

	public double getMinAllowedSamplingRate() {
		return getMinAllowedEnabledAlgorithmSamplingRate();
	}

	public double getMinAllowedEnabledAlgorithmSamplingRate(){
		double minAllowedAlgoSamplingRate = 0.0;
		Iterator<AbstractAlgorithm> iterator = getListOfEnabledAlgorithmModules().iterator();
		while(iterator.hasNext()){
			AbstractAlgorithm abstractAlgorithm = iterator.next();
			minAllowedAlgoSamplingRate = Math.max(abstractAlgorithm.getMinSamplingRateForAlgorithm(), minAllowedAlgoSamplingRate);
		}
		return minAllowedAlgoSamplingRate;
	}

	public void setDefaultSettingsForAlgorithmGroup(String groupName) {
		List<AbstractAlgorithm> listOfAlgosForGroup = getListOfAlgorithmModulesPerGroup(groupName);
		for(AbstractAlgorithm abstractAlgorithm:listOfAlgosForGroup){
			abstractAlgorithm.setDefaultSetting();
		}
	}

	public SensorDetails getSensorDetails(Integer sensorId) {
		if(mSensorMap!=null && mSensorMap.containsKey(sensorId)){
			return mSensorMap.get(sensorId);
		}
		return null;
	}

	public void addSensorClass(AbstractSensor abstractSensor){
		addSensorClass(abstractSensor.mSensorType, abstractSensor);
	}

	public void addSensorClass(AbstractSensor.SENSORS sensorClassKey, AbstractSensor abstractSensor){
		mMapOfSensorClasses.put(sensorClassKey, abstractSensor);
		//TODO not sure if this is needed
//		mSensorMap.putAll(abstractSensor.mSensorMap);
	}

	public AbstractSensor getSensorClass(AbstractSensor.SENSORS sensorClassKey){
		if(mMapOfSensorClasses!=null && mMapOfSensorClasses.containsKey(sensorClassKey)){
			return mMapOfSensorClasses.get(sensorClassKey);
		}
		return null;
	}

	@Deprecated
	public AbstractSensor getSensorClass(long sensorClassKeyInt){
		if(mMapOfSensorClasses!=null){
			for(AbstractSensor.SENSORS sensorClassKey:mMapOfSensorClasses.keySet()){
				if(sensorClassKey.ordinal()==sensorClassKeyInt){
					return getSensorClass(sensorClassKey);
				}
			}
		}
		return null;
	}

	//Testing
//	public SensorDetails getSensorDetailsFromMapOfSensorClasses(Integer sensorId) {
//		for(AbstractSensor aS:mMapOfSensorClasses.values()){
//			SensorDetails sD = aS.getSensorDetails(sensorId);
//			if(sD!=null)
//				return sD;
//		}
//		return null;
//	}
	
	public void threadSleep(long millis){
		try {
			Thread.sleep(millis);	
		} catch (InterruptedException e) {
			consolePrintLn("threadSleep ERROR");
			e.printStackTrace();
		}
	}

	//*************** Sensor Calibration Related Start ************************* 
	
	public boolean isCalibrationValid() {
		// debugging
		TreeMap<Integer, TreeMap<Integer, CalibDetails>> calibrationMap = getMapOfSensorCalibrationAll();
		Boolean validCal = true;
		Integer rangeCast = null;

		if (calibrationMap != null) {
			Iterator<Integer> iterator = calibrationMap.keySet().iterator();
			while(iterator.hasNext()){
				Integer sensorId = iterator.next();
				TreeMap<Integer, CalibDetails> mapOfKinematicDetails = getMapOfSensorCalibrationAll().get(sensorId);

				Object returnedRange = getConfigValueUsingConfigLabel(sensorId,AbstractSensor.GuiLabelConfigCommon.RANGE);
				if (returnedRange != null) {
					if (returnedRange instanceof Integer) {
						rangeCast = (Integer) returnedRange;
					}

					CalibDetails calibDetails = mapOfKinematicDetails.get(rangeCast);
					if(calibDetails!=null && calibDetails instanceof CalibDetailsKinematic){
						CalibDetailsKinematic kinematicDetails = (CalibDetailsKinematic)calibDetails;
						if (kinematicDetails.isCurrentValuesSet()) {
							if (!kinematicDetails.isAllCalibrationValid()) {
								validCal = false;
							}
						}
					}
				}
			}
		}
		return validCal;
	}

	public TreeMap<Integer, TreeMap<Integer, CalibDetailsKinematic>> getMapOfSensorCalibrationAllKinematic(){
		//Get all calibration for all sensors
		TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfAllSensorCalibration = getMapOfSensorCalibrationAll();
		
		// Filter out just Kinematic calibration parameters
		TreeMap<Integer, TreeMap<Integer, CalibDetailsKinematic>> mapOfAllSensorCalibrationKinematic = new TreeMap<Integer, TreeMap<Integer, CalibDetailsKinematic>>();
		Iterator<Integer> iteratorSensor = mapOfAllSensorCalibration.keySet().iterator();
		while(iteratorSensor.hasNext()){
			Integer sensorId = iteratorSensor.next();
			TreeMap<Integer, CalibDetailsKinematic> mapOfSensorCalibrationKinematic = new TreeMap<Integer, CalibDetailsKinematic>();
			TreeMap<Integer, CalibDetails> mapOfCalibPerRange = mapOfAllSensorCalibration.get(sensorId);
			Iterator<Integer> iteratorRange = mapOfCalibPerRange.keySet().iterator();
			while(iteratorRange.hasNext()){
				Integer range = iteratorRange.next();
				CalibDetails calibDetails = mapOfCalibPerRange.get(range);
				if(calibDetails instanceof CalibDetailsKinematic){
					mapOfSensorCalibrationKinematic.put(range, (CalibDetailsKinematic)calibDetails);
				}
			}
			
			if(!mapOfSensorCalibrationKinematic.isEmpty()){
				mapOfAllSensorCalibrationKinematic.put(sensorId, mapOfSensorCalibrationKinematic);
			}

		}
		return mapOfAllSensorCalibrationKinematic;
	}
	
	public TreeMap<Integer, TreeMap<Integer, CalibDetails>> getMapOfSensorCalibrationAll(){
		TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfSensorCalibration = new TreeMap<Integer, TreeMap<Integer, CalibDetails>>();
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			Object returnVal = abstractSensor.getConfigValueUsingConfigLabel(AbstractSensor.GuiLabelConfigCommon.CALIBRATION_ALL);
			if(returnVal!=null){
				TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfCalibDetails = (TreeMap<Integer, TreeMap<Integer, CalibDetails>>) returnVal;
				mapOfSensorCalibration.putAll(mapOfCalibDetails);
			}
		}
		return mapOfSensorCalibration;
	}

	public void resetAllCalibParametersToDefault() {
		TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfCalibAll = getMapOfSensorCalibrationAll();
		if(mapOfCalibAll != null){
			// for each sensor
			for(TreeMap<Integer, CalibDetails> mapOfCalibPerSensor:mapOfCalibAll.values()){
				if(mapOfCalibPerSensor != null){
					// for each range
					for(CalibDetails calibDetails:mapOfCalibPerSensor.values()){
						if(calibDetails != null){
							// set to default
							calibDetails.resetToDefaultParameters();
						}
					}
				}
			}
		}
	}
	
	public void setMapOfSensorCalibrationAll(TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfKinematicSensorCalibration){
		Iterator<Integer> iterator = mapOfKinematicSensorCalibration.keySet().iterator();
		while(iterator.hasNext()){
			Integer sensorId = iterator.next();
			AbstractSensor abstractSensor = getSensorClass(sensorId);
			if(abstractSensor!=null){
				abstractSensor.setConfigValueUsingConfigLabel(AbstractSensor.GuiLabelConfigCommon.CALIBRATION_PER_SENSOR, mapOfKinematicSensorCalibration.get(sensorId));
			}
		}
	}
	
	protected void setSensorCalibrationPerSensor(Integer sensorId, TreeMap<Integer, CalibDetails> mapOfSensorCalibration) {
		AbstractSensor abstractSensor = getSensorClass(sensorId);
		if(abstractSensor!=null){
			abstractSensor.setCalibrationMapPerSensor(sensorId, mapOfSensorCalibration);
		}
	}

	public TreeMap<Integer, CalibDetails> getMapOfSensorCalibrationPerSensor(Integer sensorId){
		AbstractSensor abstractSensor = getSensorClass(sensorId);
		if(abstractSensor!=null){
			return abstractSensor.getCalibrationMapForSensor(sensorId);
		}
		return null;
	}
	
	public TreeMap<Integer, CalibDetails> getMapOfSensorCalibrationPerSensor(SENSORS sensorClassKey, int sensorId) {
		AbstractSensor abstractSensor = getSensorClass(sensorClassKey);
		if(abstractSensor!=null){
			return abstractSensor.getCalibrationMapForSensor(sensorId);
		}
		return null;
	}

	public byte[] calibByteDumpGenerate(){
		byte[] calibBytesAll = new byte[]{};
		
//		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
//		while(iterator.hasNext()){
//			AbstractSensor abstractSensor = iterator.next();
//			byte[] calibBytesPerSensor = abstractSensor.generateAllCalibByteArray();
//			if(calibBytesPerSensor!=null){
//				byte[] newCalibBytesAll = ArrayUtils.addAll(calibBytesAll, calibBytesPerSensor);
//				calibBytesAll = newCalibBytesAll;
//			}
//		}
		
		TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfAllCalib = getMapOfSensorCalibrationAll();
		for(Integer sensorId:mapOfAllCalib.keySet()){
			TreeMap<Integer, CalibDetails> calibMapPerSensor = mapOfAllCalib.get(sensorId);
			for(CalibDetails calibDetailsPerRange:calibMapPerSensor.values()){
				
				byte[] calibBytesPerSensor = calibDetailsPerRange.generateCalibDump();
				if(calibBytesPerSensor!=null){
					byte[] calibSensorKeyBytes = new byte[2];
					calibSensorKeyBytes[0] = (byte)((sensorId>>0)&0xFF);
					calibSensorKeyBytes[1] = (byte)((sensorId>>8)&0xFF);
					calibBytesPerSensor = ArrayUtils.addAll(calibSensorKeyBytes, calibBytesPerSensor);
					
					byte[] newCalibBytesAll = ArrayUtils.addAll(calibBytesAll, calibBytesPerSensor);
					calibBytesAll = newCalibBytesAll;
				}
				
				//Debugging
//				if(calibDetailsPerRange.mRangeValue == 7){
//					System.err.println("MAG RANGE 7");
//				}
				
				//Debugging
//				if(calibDetailsPerRange instanceof CalibDetailsKinematic){
//					System.out.println(((CalibDetailsKinematic)calibDetailsPerRange).generateDebugString());
//				}
			}
		}

		byte[] svoBytes = mShimmerVerObject.generateVersionByteArrayNew();
		byte[] concatBytes = ArrayUtils.addAll(svoBytes,calibBytesAll);
		
		byte[] packetLength = new byte[2];
		packetLength[0] = (byte) (concatBytes.length&0xFF);
		packetLength[1] = (byte) ((concatBytes.length>>8)&0xFF);
//		consolePrintLn("Packet byte/t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(packetLength));
//		consolePrintLn("calibByteAll/t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(calibBytesAll));
		
		concatBytes = ArrayUtils.addAll(packetLength,concatBytes);

//		consolePrintLn("Concat byte/t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(concatBytes));
		
		return concatBytes;
	}

	/**
	 * Parsed the calibration byte array dump that is saved on the SD card or
	 * else read over bluetooth/dock from a specific calibration dump read
	 * command
	 * 
	 * @param calibBytesAll
	 */
	public void calibByteDumpParse(byte[] calibBytesAll, CALIB_READ_SOURCE calibReadSource){

		mCalibBytesDescriptions = new HashMap<Integer, String>();
		mCalibBytes = calibBytesAll;
		
		if(UtilShimmer.isAllZeros(calibBytesAll)){
			return;
		}
		
		if(calibBytesAll.length>2){
			//1) Packet lENGTH -> don't need here
			mCalibBytesDescriptions.put(0, "PacketLength_LSB");
			mCalibBytesDescriptions.put(1, "PacketLength_MSB");
			byte[] packetLength = Arrays.copyOfRange(calibBytesAll, 0, 2);
			
			byte[] svoBytes = Arrays.copyOfRange(calibBytesAll, 2, 10);
			ShimmerVerObject svo = new ShimmerVerObject(svoBytes);

			mCalibBytesDescriptions.put(2, "HwID_LSB (" + svo.getHardwareVersionParsed() + ")");
			mCalibBytesDescriptions.put(3, "HwID_MSB");
			mCalibBytesDescriptions.put(4, "FwID_LSB (" + svo.mFirmwareIdentifierParsed + ")");
			mCalibBytesDescriptions.put(5, "FwID_MSB");
			mCalibBytesDescriptions.put(6, "FWVerMjr_LSB");
			mCalibBytesDescriptions.put(7, "FWVerMjr_MSB");
			mCalibBytesDescriptions.put(8, "FWVerMinor");
			mCalibBytesDescriptions.put(9, "FWVerInternal");

			int currentOffset = 10;
			byte[] remainingBytes = Arrays.copyOfRange(calibBytesAll, 10, calibBytesAll.length);;
			while(remainingBytes.length>12){
				//2) parse sensorId (2 bytes LSB)
				byte[] sensorIdBytes = Arrays.copyOfRange(remainingBytes, 0, 2);
				int sensorId = ((sensorIdBytes[1]<<8) | sensorIdBytes[0])&0xFFFF;
				
				String sensorName = "";
				SensorDetails sensorDetails = getSensorDetails(sensorId);
				if(sensorDetails!=null && sensorDetails.mSensorDetailsRef!=null){
					sensorName = sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel;
				}
				mCalibBytesDescriptions.put(currentOffset, "SensorID_LSB (" + sensorName + ")");
				mCalibBytesDescriptions.put(currentOffset+1, "SensorID_MSB");
				
				//3) parse range (1 byte)
				mCalibBytesDescriptions.put(currentOffset+2, "SensorRange");
				byte[] rangeIdBytes = Arrays.copyOfRange(remainingBytes, 2, 3);
				int rangeValue = (rangeIdBytes[0]&0xFF);
	
				//4) parse calib byte length (1 byte)
				mCalibBytesDescriptions.put(currentOffset+3, "CalibByteLength");
	//			byte[] calibLength = Arrays.copyOfRange(remainingBytes, 3, 4);
				int calibLength = (remainingBytes[3]&0xFF);
	//			int calibLength = parseCalibrationLength(sensorId);
				
				//5) parse timestamp (8 bytes MSB/LSB?)
				mCalibBytesDescriptions.put(currentOffset+4, "CalibTimeTicks_LSB");
				mCalibBytesDescriptions.put(currentOffset+11, "CalibTimeTicks_MSB");
				byte[] calibTimeBytesTicks = Arrays.copyOfRange(remainingBytes, 4, 12);
				
//				//Debugging
//				consolePrintLn("");
//				consolePrintLn("Sensor id Bytes - \t" + sensorId + "\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(sensorIdBytes));
//				consolePrintLn("Range id Bytes - \t" + rangeValue + "\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(rangeIdBytes));
//				consolePrintLn("Calib Bytes Length - \t" + calibLength);
//				consolePrintLn("Time Stamp id Bytes - \t" + UtilShimmer.convertShimmerRtcDataBytesToMilliSecondsLSB(calibTimeBytesTicks) + "\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(calibTimeBytesTicks));
				
				int endIndex = 12+calibLength;
				//6) parse calibration bytes (X bytes)
				if(remainingBytes.length>=endIndex){
					mCalibBytesDescriptions.put(currentOffset+12, "Calib_Start");
					mCalibBytesDescriptions.put(currentOffset+endIndex-1, "Calib_End");
					byte[] calibBytes = Arrays.copyOfRange(remainingBytes, 12, endIndex);
					
//					//Debugging
//					consolePrintLn("Calibration id Bytes - \t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(calibBytes));

					calibByteDumpParsePerSensor(sensorId, rangeValue, calibTimeBytesTicks, calibBytes, calibReadSource);
					remainingBytes = Arrays.copyOfRange(remainingBytes, endIndex, remainingBytes.length);
//					consolePrintLn("Remaining Bytes - " + remainingBytes);
					
					currentOffset += endIndex;
				}
				else {
					break;
				}
	
			}
		}
	}
	
	protected void calibByteDumpParsePerSensor(int sensorId, int rangeValue, byte[] calibTimeBytesTicks, byte[] calibBytes, CALIB_READ_SOURCE calibReadSource) {
//		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
//		while(iterator.hasNext()){
//			AbstractSensor abstractSensor = iterator.next();
//			if(abstractSensor.parseAllCalibByteArray(sensorId, rangeValue, calibTime, calibBytes)){
////				consolePrintLn("SUCCESSFULLY PARSED");
//				break;
//			}
//		}
		
		TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfAllCalib = getMapOfSensorCalibrationAll();
		TreeMap<Integer, CalibDetails> mapOfCalibPerSensor = mapOfAllCalib.get(sensorId);
		if(mapOfCalibPerSensor!=null){
			CalibDetails calibDetailsPerRange = mapOfCalibPerSensor.get(rangeValue);
			if(calibDetailsPerRange!=null){
				calibDetailsPerRange.parseCalibDump(calibTimeBytesTicks, calibBytes, calibReadSource);
				
				//Debugging
//				if(calibDetailsPerRange instanceof CalibDetailsKinematic){
//					System.out.println(((CalibDetailsKinematic)calibDetailsPerRange).generateDebugString());
//				}
			}
		}
	}
	//*************** Sensor Calibration Related end ************************* 

	
	//*************** Radio Connection start ************************* 
	/**
	 * @param commsProtocolRadio the mCommsProtocolRadio to set
	 */
	public void setCommsProtocolRadio(CommsProtocolRadio commsProtocolRadio) {
		consolePrintErrLn("Setting CommsProtocolRadio");
		this.mCommsProtocolRadio = commsProtocolRadio;
	}

	/**
	 * @return the mCommsProtocolRadio
	 */
	public CommsProtocolRadio getCommsProtocolRadio() {
		return mCommsProtocolRadio;
	}
	
	public void clearCommsProtocolRadio() throws ShimmerException {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.disconnect();
		}
		mCommsProtocolRadio = null;
	}

	//TODO copied from ShimmerBluetooth
	public void operationPrepare() {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.operationPrepare();
		}
	}

	//TODO copied from ShimmerBluetooth
	public void operationStart(BT_STATE configuring) {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.operationStart(configuring);
		}
	}

	public void startStreaming() throws ShimmerException {
		resetPacketLossVariables();
		generateParserMap();
//		resetAlgorithmBuffers();
		initializeAlgorithms();
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.startStreaming();
		}
	}

	public void stopStreaming() throws ShimmerException {
		resetPacketLossVariables();
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.stopStreaming();
		}
	}
	
	public void startSDLogging() {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.startSDLogging();
		}
	}

	public void stopSDLogging() {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.stopSDLogging();
		}
	}
	
	public void startDataLogAndStreaming() {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.startDataLogAndStreaming();
		}
	}
	
	public void stopStreamingAndLogging() {
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.stopLoggingAndStreaming();
		}
	}
	
	public void updateExpectedDataPacketSize() {
		int expectedDataPacketSize = getExpectedDataPacketSize(COMMUNICATION_TYPE.BLUETOOTH);
//		int expectedDataPacketSize = getExpectedDataPacketSize(COMMUNICATION_TYPE.ALL);
		if(mCommsProtocolRadio!=null){
			mCommsProtocolRadio.setPacketSize(expectedDataPacketSize);
		}
	}

	
	/**This method will not only return the Com Port but also update it from the CommsProtocolRadio if it is in use.  
	 * @return
	 */
	public String getComPort() {
		if(mCommsProtocolRadio!=null && mCommsProtocolRadio.mRadioHal!=null){
			setComPort(((AbstractSerialPortHal) mCommsProtocolRadio.mRadioHal).getConnectionHandle()); 
		}
		return mComPort;
	}
	
	/**This method will only return the comport name
	 * @return
	 */
	public String getComPortName() {
		return mComPort;
	}
	
	public boolean isReadyToConnect() {
		if (mCommsProtocolRadio==null 
				||mCommsProtocolRadio.mRadioHal==null
				||!mCommsProtocolRadio.mRadioHal.isConnected()){
			return true;
		}
		return false;
	}
	
	//*************** Radio Connection end ************************* 

	
	public String getBtConnectionHandle() {
		String comPort = getComPort();
		if(comPort!=null && !comPort.isEmpty()){
			return comPort;
		}
		else{
			return getMacId();
		}
	}
	
	/** Only supported in ShimmerPCMSS currently*/
	public void setComPort(String comPort){
		mComPort = comPort;
		updateThreadName();
	}
	
	public void setUniqueId(String uniqueId){
		mUniqueID = uniqueId;
	}

	public String getUniqueId(){
		return mUniqueID;
	}

	public void calculatePacketReceptionRateCurrent(int intervalMs) {
		double numPacketsShouldHaveReceived = (((double)intervalMs)/1000) * getSamplingRateShimmer();
		double packetsReceivedSinceLastRequest = getPacketReceivedCountCurrent();
		setPacketReceptionRateCurrent((packetsReceivedSinceLastRequest/numPacketsShouldHaveReceived)*100.0);
		
		resetPacketReceptionCurrentCounters();
	}
	
	//-------------- Data processing related start --------------------------
	/**
	 * 
	 * Register a callback to be invoked after buildmsg has executed (A new
	 * packet has been successfully received -> raw bytes interpreted into Raw
	 * and Calibrated Sensor data)
	 * 
	 * @param d
	 *            The callback that will be invoked
	 */
	public void setDataProcessing(DataProcessingInterface dataProcessing) {
		mDataProcessing = dataProcessing;
	}

	public DataProcessingInterface getDataProcessing() {
		return mDataProcessing;
	}

	protected void initaliseDataProcessing() {
		//provides a callback for users to initialize their algorithms when start streaming is called
		if(mDataProcessing!=null){
			mDataProcessing.initializeProcessData((int)getSamplingRateShimmer());
		} 	
	}

	//-------------- Data processing related end --------------------------
	

	public void updateThreadName() {
		String macId = getMacIdParsed();
		String newThreadName = this.getClass().getSimpleName(); 
		if(!macId.isEmpty()){
			newThreadName += "-" + macId;
		}
		setThreadName(newThreadName);
	}
	
	protected void consolePrintException(String message, StackTraceElement[] stackTrace) {
		consolePrintLn("Exception!");
		System.out.println("Message: " + message);
		
		Exception e = new Exception();
		e.setStackTrace(stackTrace);
		//create new StringWriter object
		StringWriter sWriter = new StringWriter();
		//create PrintWriter for StringWriter
		PrintWriter pWriter = new PrintWriter(sWriter);
		//now print the stacktrace to PrintWriter we just created
		e.printStackTrace(pWriter);
		//use toString method to get stacktrace to String from StringWriter object
		String strStackTrace = sWriter.toString();
		System.out.println(strStackTrace);
	}

	public void disableAllSensors() {
		for(int sensorId:mSensorMap.keySet()){
			setSensorEnabledState(sensorId, false);
		}
	}
	
	public void setRadio(AbstractSerialPortHal commsProtocolRadio) {
		// TODO Auto-generated method stub
	}

//	public static void addToMapOfErrorCodes(TreeMap<Integer, String> mapOfErrorCodes) {
//		mMapOfErrorCodes.putAll(mapOfErrorCodes);
//	}
	
	public void addDeviceException(ShimmerException dE) {
		dE.mUniqueID = mUniqueID;
		mListOfDeviceExceptions.add(dE);
		consolePrintLn(dE.getErrStringFormatted());
	}

	public void sensorAndConfigMapsCreateCommon() {
		generateSensorAndParserMaps();
		
		generateMapOfAlgorithmModules();
		generateMapOfAlgorithmConfigOptions();
		generateMapOfAlgorithmGroupingMap();
		
		handleSpecialCasesAfterSensorMapCreate();
	}

	public void setAlternativeName(String alternativeName) {
		mAlternativeName = alternativeName;
	}

	public String getAlternativeName() {
		return mAlternativeName;
	}

	public boolean isFixedShimmerConfigModeSet() {
		return (mFixedShimmerConfigMode==FIXED_SHIMMER_CONFIG_MODE.NONE? false:true);
	}

	public FIXED_SHIMMER_CONFIG_MODE getFixedShimmerConfigMode() {
		return mFixedShimmerConfigMode;
	}

	public void setFixedShimmerConfig(FIXED_SHIMMER_CONFIG_MODE fixedShimmerConfig) {
		mFixedShimmerConfigMode = fixedShimmerConfig;
	}

	public void addFixedShimmerConfig(String configKey, Object configValue) {
		if(mFixedShimmerConfigMap==null){
			mFixedShimmerConfigMap = new LinkedHashMap<String, Object>();
		}
		mFixedShimmerConfigMap.put(configKey, configValue);
	}
	
	protected boolean setFixedConfigWhenConnecting() {
		return FixedShimmerConfigs.setFixedConfigWhenConnecting(this, mFixedShimmerConfigMode, mFixedShimmerConfigMap);
	}

	public void setAutoStartStreaming(boolean state){
		mAutoStartStreaming = state;
	}
	
	public boolean isAutoStartStreaming(){
		return mAutoStartStreaming;
	}

	public void setEnableProcessMarker(boolean enable){
		mEnableProcessMarkers=enable;
	}

	/**
	 * This is clock frequency that controls when the device records a new
	 * sample (this could be a 32.768kHz crystal, or, in the case of a Shimmer3,
	 * it could be the TCXO)
	 */
	public double getSamplingClockFreq() {
		return 32768.0;
	}
	
	/**
	 * This is clock frequency that is used as the basis for the timestamp per
	 * packet of data (normally a 32.768kHz crystal for Shimmer2r3)
	 */
	public static double getRtcClockFreq() {
		return 32768.0;
	}
	
	public boolean isEnabledAlgorithmModulesDuringPlayback(){
		return mIsEnabledAlgorithmModulesDuringPlayback;
	}
	
	public void setEnabledAlgorithmModulesDuringPlayback(boolean enableAlgorithmModulesDuringPlayback){
		mIsEnabledAlgorithmModulesDuringPlayback = enableAlgorithmModulesDuringPlayback;
	}

	public void configureFromClone(ShimmerDevice shimmerDeviceClone) throws ShimmerException {
		//Not current used in this class but can be overwritten in ShimmerDevice instances
	}

	public void setIsPlaybackDevice(boolean isPlaybackDevice) {
		mIsPlaybackDevice = isPlaybackDevice;
	}

	public boolean isPlaybackDevice() {
		return mIsPlaybackDevice;
	}
	
	public void setSensorConfig(List<ISensorConfig> listOfSensorConfig) {
		setSensorConfig(listOfSensorConfig.toArray(new ISensorConfig[listOfSensorConfig.size()]));
	}

	public void setSensorConfig(ISensorConfig[] arrayOfSensorConfig) {
		for(ISensorConfig sensorConfig : arrayOfSensorConfig) {
			setSensorConfig(sensorConfig);
		}
	}

	public void setSensorConfig(ISensorConfig sensorConfig) {
		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			abstractSensor.setSensorConfig(sensorConfig);
		}
	}

	public List<ISensorConfig> getSensorConfig() {
		List<ISensorConfig> listOfSensorConfig = new ArrayList<>();

		Iterator<AbstractSensor> iterator = mMapOfSensorClasses.values().iterator();
		while(iterator.hasNext()){
			AbstractSensor abstractSensor = iterator.next();
			listOfSensorConfig.addAll(abstractSensor.getSensorConfig());
		}
		
		return listOfSensorConfig;
	}

	public String getRadioModel() {
		return "";
	}

	public boolean isDockCommsSuccessful() {
		return mIsDockCommsSuccessful;
	}
	
	public void setDockCommsSuccessful(boolean isDockCommsSuccessful) {
		mIsDockCommsSuccessful = isDockCommsSuccessful;
	}

}