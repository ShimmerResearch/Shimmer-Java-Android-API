package com.shimmerresearch.managers.bluetoothManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.shimmerresearch.bluetooth.BluetoothProgressReportAll;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerRadioInitializer;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.serialPortInterface.AbstractSerialPortHal;
import com.shimmerresearch.comms.serialPortInterface.ByteLevelDataCommListener;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3;
import com.shimmerresearch.driver.shimmer4sdk.Shimmer4sdk;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerShell;
import com.shimmerresearch.driverUtilities.BluetoothDeviceDetails;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.DEVICE_TYPE;
import com.shimmerresearch.exceptions.ConnectionExceptionListener;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.sensors.lsm303.SensorLSM303DLHC;
import com.shimmerresearch.shimmerConfig.FixedShimmerConfigs.FIXED_SHIMMER_CONFIG_MODE;
import com.shimmerresearch.thirdpartyDevices.noninOnyxII.NoninOnyxIIDevice;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.communication.VerisenseProtocolByteCommunication;

public abstract class ShimmerBluetoothManager{

	protected boolean mVerbose = true;
	protected boolean directConnectUnknownShimmer=false;
	
	/** Key is the COM port in PC and BT address for Android*/
	public static ConcurrentHashMap<String, ShimmerDevice> mMapOfBtConnectedShimmers = new ConcurrentHashMap<String, ShimmerDevice>(7);
	
	public TreeMap<String, BluetoothDeviceDetails> mMapOfParsedBtComPorts = new TreeMap<String, BluetoothDeviceDetails>();
	public TreeMap<String, BluetoothDeviceDetails> mMapOfParsedBLEDevices = new TreeMap<String, BluetoothDeviceDetails>();
	public TreeMap<String, BluetoothDeviceDetails> mMapOfParsedBtComPortsDeepCopy  = new TreeMap<String, BluetoothDeviceDetails>();
	public TreeMap<String, BluetoothDeviceDetails> mMapOfParsedBLEDevicesDeepCopy  = new TreeMap<String, BluetoothDeviceDetails>();
	
	public HashMap<String, ConnectThread> mapOfConnectionThreads = new HashMap<String, ConnectThread>(); 
	
	private ConnectionExceptionListener connectionExceptionListener;
	
	protected static final boolean USE_INFOMEM_CONFIG_METHOD = true;
	public static final long SLEEP_BETWEEN_GROUP_ACTIONS_MS = 50;
	public static final String COMPORT_PREFIX = "COM";
//	public static final String COMPORT_PREFIX_MAC = "/dev/"; 	//Commented-out as MacOS is relying on BT device name rather than COM PORT prefix
	public static final String COMPORT_PREFIX_MAC = "Shimmer";

	/**
	 * Whether a connection handle is a serial port's: a COM port on Windows, or
	 * on macOS the device's Bluetooth name, Shimmer... or a NeuroSmart unit's
	 * Neurosmart-XXXX-BT/BLE (DEV-1389). Any other handle is a BLE device's
	 * address.
	 */
	public static boolean isSerialPortHandle(String connectionHandle) {
		return connectionHandle.contains(COMPORT_PREFIX) || connectionHandle.contains(COMPORT_PREFIX_MAC)
				|| HwDriverShimmerDeviceDetails.SH_SEARCH.BT.isNeurosmartName(connectionHandle);
	}

	protected int mSyncTrainingIntervalInSeconds = 15;
	protected int msDelayBetweenSetCommands = 0;
	protected BluetoothProgressReportAll mProgressReportAll;
	
	//Timers start
	transient protected Timer mTimerTimerCalcReceptionRate;
	private int mCalcReceptionRateMs = 1000; 
	//Timers end

    protected abstract void loadBtShimmers(Object... params);
	public abstract void addCallBack(BasicProcessWithCallBack basicProcess);
	public abstract void printMessage(String message);
	
	public abstract ShimmerDevice getShimmerGlobalMap(String bluetoothAddress);
	public abstract void putShimmerGlobalMap(String bluetoothAddress, ShimmerDevice shimmerDevice);
    protected abstract AbstractSerialPortHal createNewSerialPortComm(String comPort, String bluetoothAddress);
    protected abstract void connectExistingShimmer(Object... params) throws ShimmerException;
    
    /** This method is used to create a new Shimmer3 instance without setting the radio */ 
    protected abstract ShimmerDevice createNewShimmer3(String comPort, String bluetoothAddress);
	/** This method is used to create a new Shimmer3 instance after a serial port
	 * connection has already been established (e.g. after a connection has been
	 * made to a Shimmer and we have determined what hardware version it is)*/
    protected abstract ShimmerDevice createNewShimmer3(ShimmerRadioInitializer bldc, String bluetoothAddress);
    
    /** This method is used to create a new Shimmer4 instance without setting the radio */ 
	protected abstract Shimmer4sdk createNewShimmer4(String comPort, String bluetoothAddress);
	/** This method is used to create a new Shimmer4 instance after a serial port
	 * connection has already been established (e.g. after a connection has been
	 * made to a Shimmer and we have determined what hardware version it is)*/
    protected abstract Shimmer4sdk createNewShimmer4(ShimmerRadioInitializer radioInitializer, String bluetoothAddress);

	// TODO Might be better to be able to set mFixedShimmerConfig and
	// mAutoStartStreaming on a per Shimmer basis through variables passed into the
	// connect() method rather then globals for all devices
    /** Used to load a set configuration to the devices based on different applications */
    protected FIXED_SHIMMER_CONFIG_MODE mFixedShimmerConfigGlobal = FIXED_SHIMMER_CONFIG_MODE.NONE;
	/** If true, all devices will auto-stream once a connection is established */
	protected boolean mAutoStartStreaming = false;		

	private static final List<Integer> HW_IDS_THAT_SUPPORT_CONFIG_VIA_BT = Arrays.asList(
			HW_ID.SHIMMER_3, HW_ID.SHIMMER_3R, HW_ID.SWEATCH, HW_ID.SHIMMER_4_SDK, 
			HW_ID.VERISENSE_IMU, HW_ID.VERISENSE_DEV_BRD, HW_ID.VERISENSE_GSR_PLUS, HW_ID.VERISENSE_PPG, HW_ID.VERISENSE_PULSE_PLUS);
	
	public ShimmerBluetoothManager() {
		startTimerCalcReceptionRate();
	}

	public void connectShimmerThroughCommPort(String comPort){
		connectShimmer(comPort, null, null);
	}
	
	/**This is called to connect to a shimmer device
	 * 
	 * @param connectionHandle can either be COM port or MAC address e.g. COM3, D0:2B:46:3D:A2:BB
	 * @param shimmerVerObject
	 * @param expansionBoardDetails
	 */
	public void connectShimmer(String connectionHandle, ShimmerVerObject shimmerVerObject, ExpansionBoardDetails expansionBoardDetails) {
		BluetoothDeviceDetails bluetoothDetails = getBluetoothDeviceDetails(connectionHandle);

		//No need to start the thread if the device isn't available
		if(bluetoothDetails!=null){
			ConnectThread connectThread = new ConnectThread(connectionHandle, shimmerVerObject, expansionBoardDetails);
			mapOfConnectionThreads.put(connectThread.connectionHandle, connectThread);
			connectThread.start();
		} else {
			sendFeedbackOnConnectStartException(connectionHandle);
		}
	}
	
	public void connectShimmerThroughBTAddress(String bluetoothAddress){
		ConnectThread connectThread = new ConnectThread(bluetoothAddress);
		mapOfConnectionThreads.put(connectThread.connectionHandle, connectThread);
		connectThread.start();
	}
	
	public void connectShimmerThroughBTAddress(BluetoothDeviceDetails deviceDetails){
		ConnectThread connectThread = new ConnectThread(deviceDetails);
		connectThread.start();
	}
	
	/**
	 * @param connectionHandle comport or mac address, note that this does not remove the device from the map
	 */
	public void disconnectShimmer(String connectionHandle){
		printMessage("Attempting to disconnect from connection handle = " + connectionHandle);
		ShimmerDevice shimmerDevice = getShimmerDeviceBtConnected(connectionHandle); 
		if (shimmerDevice!=null){
			printMessage("Disconnecting from " + shimmerDevice.getClass().getSimpleName() + " with connection handle = " + connectionHandle);
			disconnectShimmer(shimmerDevice);
		}
	}

	public void disconnectAllDevices(){
		Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
		while (iterator.hasNext()){
			ShimmerDevice shimmerDevice = iterator.next(); 
			disconnectShimmer(shimmerDevice);
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
		mMapOfBtConnectedShimmers.clear();
	}

	public void disconnectShimmer(ShimmerDevice shimmerDevice){
		try {
			shimmerDevice.disconnect();
		} catch (ShimmerException e) {
			printMessage(e.getErrStringFormatted());
		}
	}
	
	public void startStreaming(String bluetoothAddress) throws ShimmerException {
		ShimmerDevice shimmerDevice = getShimmerDeviceBtConnectedFromMac(bluetoothAddress);
		if(shimmerDevice!=null){
			startStreaming(shimmerDevice);
		}
	}

	public void startStreamingAllDevices() {
		Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
		while (iterator.hasNext()) {
			ShimmerDevice shimmerDevice = iterator.next();
			try {
				startStreaming(shimmerDevice);
			} catch (ShimmerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
	}

	public void startStreaming(ShimmerDevice shimmerDevice) throws ShimmerException{
		if (shimmerDevice.isConnected()){
			shimmerDevice.startStreaming();
		}
	}

	public void stopStreamingAllDevices() {
		Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
		while (iterator.hasNext()) {
			ShimmerDevice shimmerDevice = iterator.next();
			try {
				stopStreaming(shimmerDevice);
			} catch (ShimmerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
	}
	
	public void stopStreaming(String bluetoothAddress) throws ShimmerException {
		ShimmerDevice shimmerDevice = getShimmerDeviceBtConnectedFromMac(bluetoothAddress);
		if(shimmerDevice!=null){
			stopStreaming(shimmerDevice);
		}
	}

	public void stopStreaming(ShimmerDevice shimmerDevice) throws ShimmerException {
		if (shimmerDevice.isConnected() && shimmerDevice.isStreaming()) {
			shimmerDevice.stopStreaming();
		}
	}

	public void startSDLogging(ShimmerDevice shimmerDevice){
		if(shimmerDevice.isConnected()){
			shimmerDevice.startSDLogging();
		}
	}
	
	public void startSDLogging(List<ShimmerDevice> listOfShimmers) {
		for(ShimmerDevice shimmerDevice:listOfShimmers){
			startSDLogging(shimmerDevice);
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
	}
	
	public void stopSDLogging(ShimmerDevice shimmerDevice) {
		//Remove check?
//		if (shimmerDevice.isConnected() && shimmerDevice.isSDLogging()) {
			shimmerDevice.stopSDLogging();
//		}
	}
	
	public void stopSDLogging(List<ShimmerDevice> listOfShimmers) {
		for(ShimmerDevice shimmerDevice:listOfShimmers){
			stopSDLogging(shimmerDevice);
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
	}


	public void startStreamingAndLogging(ShimmerDevice shimmerDevice){
		if(shimmerDevice.isConnected()){
			shimmerDevice.startDataLogAndStreaming();
		}
	}

	public void stopStreamingAndLogging(ShimmerDevice shimmerDevice) {
		if (shimmerDevice.isConnected() && (shimmerDevice.isSDLogging() || shimmerDevice.isStreaming())) {
			shimmerDevice.stopStreamingAndLogging();
		}
	}
	
	public void toggleLed(String connectionHandle) {
		ShimmerDevice selectedShimmer = getShimmerDeviceBtConnected(connectionHandle);
		if(selectedShimmer!=null){
			toggleLed(selectedShimmer);
		}
	}

	public void toggleLed(List<String> listOfShimmersToToggle) {
		for(String connectionHandle:listOfShimmersToToggle){
			toggleLed(connectionHandle);
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
	}

	public void toggleLed(ShimmerDevice selectedShimmer) {
		if (selectedShimmer!=null && selectedShimmer.isConnected()) {
			if (selectedShimmer instanceof ShimmerBluetooth) {
				((ShimmerBluetooth) selectedShimmer).toggleLed();
			} 
			else if (selectedShimmer instanceof Shimmer4sdk) {
				((Shimmer4sdk) selectedShimmer).toggleLed();
			}
		}
	}
	

	public void setVerbose(boolean verbose){
		mVerbose = verbose;
	}
	
	/** Use setFixedConfigWhenConnecting instead
	 * 
	 * @param device
	 */
	@Deprecated
	public void setCadenceBTConfig(ShimmerDevice device){
//		ShimmerDevice shimmerDevice = getShimmerDeviceBtConnected(device.getComPort());
		ShimmerDevice shimmerDevice = getShimmerDeviceBtConnected(device.getBtConnectionHandle());

		if(shimmerDevice!=null){
			//TODO remove the first half of this if statement, second half is more generic and will work for Shimmer3/4
//			if (shimmerDevice.getHardwareVersion() == ShimmerVerDetails.HW_ID.SHIMMER_3){
//				if (shimmerDevice instanceof ShimmerBluetooth){
//					ShimmerBluetooth shimmerBluetooth = (ShimmerBluetooth)shimmerDevice;
//					shimmerBluetooth.checkShimmerConfigBeforeConfiguring();
//
//					shimmerBluetooth.operationPrepare();
//					shimmerBluetooth.setSendProgressReport(true);
//				
//					//setting accel range +-4g
//					shimmerBluetooth.writeAccelRange(1);
//					//setting sampling rate 51.2 hardcoded
//					shimmerBluetooth.writeShimmerAndSensorsSamplingRate(51.20);// s3 = 4
//					//write sensors 
//					shimmerBluetooth.writeEnabledSensors((long)4096); // setting wide range accel as only sensor
//					shimmerBluetooth.operationStart(BT_STATE.CONFIGURING);
//				}
//			}
//			else {
				shimmerDevice.operationPrepare();

				shimmerDevice.setDefaultShimmerConfiguration();
				shimmerDevice.disableAllAlgorithms();
				shimmerDevice.disableAllSensors();

				//write sensors
				if(shimmerDevice.getSensorIdsSet().contains(Shimmer3.SENSOR_ID.SHIMMER_LSM303_ACCEL)){
					//- setting wide range accel as only sensor
					shimmerDevice.setSensorEnabledState(Shimmer3.SENSOR_ID.SHIMMER_LSM303_ACCEL, true); 

					//setting accel range +/- 4g
					shimmerDevice.setConfigValueUsingConfigLabel(
							Shimmer3.SENSOR_ID.SHIMMER_LSM303_ACCEL, 
							SensorLSM303DLHC.GuiLabelConfig.LSM303_ACCEL_RANGE,
							1);
				}

				//setting sampling rate 51.2 hardcoded
				shimmerDevice.setShimmerAndSensorsSamplingRate(51.20);
				
				if(shimmerDevice instanceof ShimmerBluetooth){
					ShimmerBluetooth shimmerBluetooth = (ShimmerBluetooth)shimmerDevice;
					shimmerBluetooth.writeConfigBytes(shimmerDevice.getShimmerConfigBytes());
					shimmerBluetooth.writeEnabledSensors(shimmerDevice.getEnabledSensors());
				}
				
				shimmerDevice.operationStart(BT_STATE.CONFIGURING);
//			}
		}
	}
	
	public void configureShimmer(ShimmerDevice shimmerClone, boolean writeCalibrationDump){
		if (shimmerClone instanceof ShimmerBluetooth) {
			((ShimmerBluetooth) shimmerClone).setWriteCalibrationDumpWhenConfiguringForClone(writeCalibrationDump);
		}
		configureShimmers(Arrays.asList(shimmerClone));
	}
	
	public void configureShimmer(ShimmerDevice shimmerClone){
		configureShimmers(Arrays.asList(shimmerClone));
	}

	public void configureShimmers(List<ShimmerDevice> listOfShimmerClones){

		mProgressReportAll = new BluetoothProgressReportAll(BT_STATE.CONFIGURING, listOfShimmerClones);
		
		for(ShimmerDevice cloneShimmer:listOfShimmerClones){
			//TODO include below?
//			cloneShimmerCast.setBluetoothRadioState(BT_STATE.CONFIGURING);

			ShimmerDevice originalShimmerDevice = getShimmerDeviceBtConnected(cloneShimmer.getMacId());
			int cloneHwId = cloneShimmer.getHardwareVersion();
			if(originalShimmerDevice!=null 
					&& HW_IDS_THAT_SUPPORT_CONFIG_VIA_BT.contains(cloneHwId)
					&& originalShimmerDevice.getClass().isInstance(cloneShimmer)){
				originalShimmerDevice.operationPrepare();
				try {
					originalShimmerDevice.configureFromClone(cloneShimmer);
					originalShimmerDevice.operationStart(BT_STATE.CONFIGURING);
				} catch (ShimmerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				printMessage("Hardware ID not supported currently: " + cloneHwId);
			}
			
			threadSleep(SLEEP_BETWEEN_GROUP_ACTIONS_MS);
		}
	}


	//-------------- EventMarkers methods start -----------------------------
	public void setEventTriggered(long eventCode, int eventType){
		for(ShimmerDevice spc:mMapOfBtConnectedShimmers.values()){
			if (spc.isStreaming() || spc.isConnected()){ 
				spc.setEventTriggered(eventCode, eventType);
			}
		}
	}
	
	public void setEventUntriggered(long eventCode){
		for(ShimmerDevice spc:mMapOfBtConnectedShimmers.values()){
			if (spc.isStreaming() || spc.isConnected()){
				spc.setEventUntrigger(eventCode);
			}
		}
	}
	
	public void resetEventMarkerValuestoDefault(){
		Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
		while (iterator.hasNext()){
			ShimmerDevice shimmerDevice = iterator.next();
			shimmerDevice.resetEventMarkerValuetoDefault();
		}	
	}
	//-------------- EventMarkers methods end -----------------------------
	
	//-------------- Timer methods start -----------------------------

	public void startTimerCalcReceptionRate(){
		if(mTimerTimerCalcReceptionRate==null){ 
			mTimerTimerCalcReceptionRate = new Timer("BluetoothManager_TimerCalcReceptionRate");
			mTimerTimerCalcReceptionRate.schedule(new TimerTaskCalcReceptionRate(), 0, mCalcReceptionRateMs);
		} 
	}
	
	public void stopTimerCalcReceptionRate(){
		if(mTimerTimerCalcReceptionRate!=null){
			mTimerTimerCalcReceptionRate.cancel();
			mTimerTimerCalcReceptionRate.purge();
			mTimerTimerCalcReceptionRate = null;
		}
	}
	
	public class TimerTaskCalcReceptionRate extends TimerTask {
		public void run() {
			Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
			while (iterator.hasNext()) {
				ShimmerDevice shimmerDevice = (ShimmerDevice) iterator.next();
				if(shimmerDevice.isStreaming()){
					shimmerDevice.calculatePacketReceptionRateCurrent(mCalcReceptionRateMs);
				}
			}
		}
	}
	
	//-------------- Timer methods end -----------------------------

	
	
	//---------- GET Methods start -------------------------------
	
	//	public String[] getListofBluetoothComPorts(){
	//	String[] list = new String[2];
	//	list[0] = "COM16";
	//	list[1] = "COM19";
	//	return list;
	//}
	
	public ShimmerDevice getShimmerDeviceFromName(String deviceName){
		Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
		while (iterator.hasNext()) {
			ShimmerDevice stemp=(ShimmerDevice) iterator.next();
			if (stemp.getShimmerUserAssignedName().equals(deviceName)){
				return stemp;
			}
		}
		return null;
	}
	
	protected void putShimmerBtConnectedMap(ShimmerDevice shimmerDevice){
		String connectionHandle = shimmerDevice.getBtConnectionHandle();
		ShimmerDevice existingShimmer = getShimmerDeviceBtConnected(connectionHandle);
		if (existingShimmer==null){
			shimmerDevice.mVerboseMode = this.mVerbose;
			System.err.println("Putting Shimmer in BT connected map with connectionHandle:" + (connectionHandle.isEmpty()? "EMPTY":connectionHandle));
			mMapOfBtConnectedShimmers.put(connectionHandle, shimmerDevice);
		}
	}

	public ShimmerDevice getShimmerDeviceBtConnected(String connectionHandle){
		ShimmerDevice shimmerDevice = mMapOfBtConnectedShimmers.get(connectionHandle);
		if(shimmerDevice==null){
//			System.err.println("No Shimmer in BT connected map for connectionHandle:" + (connectionHandle.isEmpty()? "EMPTY":connectionHandle));
			shimmerDevice = getShimmerDeviceBtConnectedFromMac(connectionHandle);
		}
		return shimmerDevice;
	}

	public boolean checkIfAlgoEnabledonAnyConnectedDevice(String algoName) {
		for (String comPort:mMapOfBtConnectedShimmers.keySet()) {
			ShimmerDevice device = getShimmerDeviceBtConnected(comPort);
			boolean algoenabled = device.isAlgorithmEnabled(algoName);
			if(algoenabled) {
				return true;
			}
		}
		return false;		
	}
	
	public ShimmerDevice getShimmerDeviceBtConnectedFromMac(String macAddress){
		Iterator<ShimmerDevice> iterator = mMapOfBtConnectedShimmers.values().iterator();
		while(iterator.hasNext()){
			ShimmerDevice shimmerDevice = iterator.next();
			if(shimmerDevice.getMacId().toUpperCase().equals(macAddress.toUpperCase())){
				return shimmerDevice;
			}
		}
		return null;
	}

	public void removeShimmerDeviceBtConnected(String connectionHandle){
		mMapOfBtConnectedShimmers.remove(connectionHandle);
	}

	protected BluetoothDeviceDetails getBluetoothDeviceDetails(String connectionHandle){
		if (!isSerialPortHandle(connectionHandle)) {
			return getBLEDeviceDetails(connectionHandle);
		}
    	return mMapOfParsedBtComPorts.get(connectionHandle);
    }
	
	protected BluetoothDeviceDetails getBLEDeviceDetails(String connectionHandle){
    	return mMapOfParsedBLEDevices.get(connectionHandle);
    }

	public ConcurrentHashMap<String, ShimmerDevice> getMapOfBtConnectedShimmers() {
		return mMapOfBtConnectedShimmers;
	}

	/** Currently not used by any PC software.  
	 * @param comPort of the ShimmerDevice
	 * @return the parsed state of the ShimmerDEvice if found
	 */
	public String getStateParsed(String comPort){
		ShimmerDevice shimmerDevice = mMapOfBtConnectedShimmers.get(comPort); 
		if(shimmerDevice!=null){
			return shimmerDevice.getBluetoothRadioStateString();
		}
		return "Not Initialised";
	}
	
	/**
	 * Returns the state of the device, returns Connected, Disconnected,
	 * Streaming, Connecting, returns null if Shimmer device is not found or is
	 * in an unknown state (replaced with ShimmerBluetooth.getStateParsed() )
	 * 
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public String getDeviceState(String comPort){
		ShimmerDevice shimmerDevice = mMapOfBtConnectedShimmers.get(comPort);
		if(shimmerDevice!=null){
			if(shimmerDevice.isConnected()
					&& !shimmerDevice.isStreaming()){
				return "Connected";
			} 
//			else if(((ShimmerBluetooth)shimmerDevice).getBTState()==BT_STATE.CONNECTING){
//				return "Connecting";
//			} 
//			else if(((ShimmerBluetooth)shimmerDevice).getBTState()==BT_STATE.NONE){
//				return "Disconnected";
//			} 
			else if(shimmerDevice.isConnected()
					&& shimmerDevice.isStreaming()) {
				return "Streaming";
			} 
			else {
				return null;
				
			}
		} else {
			return null;
		}
	}
	
	/**
	 * Returns the state of the device, returns Connected, Disconnected,
	 * Streaming, Connecting, returns null if Shimmer device is not found or is
	 * in an unknown state
	 * 
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public int getDeviceStateInt(String comPort){
		
//		System.out.println("keyset size:" + mMultiShimmer.keySet().size());
		ShimmerDevice shimmerDevice = getShimmerDeviceBtConnected(comPort);
		if(shimmerDevice!=null) {
//			if(((ShimmerBluetooth)shimmerDevice).isConnected()
//					&& !((ShimmerBluetooth)shimmerDevice).isStreaming()){
//				return BT_STATE.CONNECTED.ordinal();
//			} 
//			else if(((ShimmerBluetooth)shimmerDevice).getBTState()==BT_STATE.CONNECTING){
//				return BT_STATE.CONNECTING.ordinal();
//			} 
//			else if(((ShimmerBluetooth)shimmerDevice).getBTState()==BT_STATE.NONE){
//				return BT_STATE.NONE.ordinal();
//			} 
			if(shimmerDevice.isConnected()
					&& shimmerDevice.isStreaming()) {
				return BT_STATE.STREAMING.ordinal();
			}
			else {
//				return ShimmerBluetooth.STATE_UNINTIALISED;
//				return BT_STATE.NONE.ordinal();
				return BT_STATE.DISCONNECTED.ordinal();
			}
		} 
		else {
//			return ShimmerBluetooth.STATE_UNINTIALISED;
//			return BT_STATE.NONE.ordinal();
			return BT_STATE.DISCONNECTED.ordinal();
		}
	}
	
	
	/** Returns a list of connected Shimmer Devices. Not used by main Consensys application
	 * @return a list of connected Shimmer Devices
	 */
	public List<String> getListOfConnectedDevicesUserAssignedName(){
		List<String> list = new ArrayList<String>();
		for(ShimmerDevice shimmerDevice:mMapOfBtConnectedShimmers.values()){
			if (shimmerDevice.isConnected()){
				list.add(shimmerDevice.getShimmerUserAssignedName());
			}
		}
		return list;
	}
	
	/**
	 * @return
	 */
	public List<String> getListOfConnectedDevicesComPort(){
		List<String> list = new ArrayList<String>();
		for(ShimmerDevice shimmerDevice:mMapOfBtConnectedShimmers.values()){
			if (shimmerDevice.isConnected()){
				list.add(shimmerDevice.getComPort());
			}
		}
		return list;
	}
	
	public List<ShimmerDevice> getListOfConnectedDevices(){
		List<ShimmerDevice> list = new ArrayList<ShimmerDevice>();
		for(ShimmerDevice shimmerDevice:mMapOfBtConnectedShimmers.values()){
			if (shimmerDevice.isConnected()){
				list.add(shimmerDevice);
			}
		}
		return list;
	}
	
	//TODO add support for Shimmer4
	public List<String []> getListOfSignalsFromAllDevices(){
		List<String []> list = new ArrayList<String []>();
		for(ShimmerDevice shimmerDevice:mMapOfBtConnectedShimmers.values()){
			if(shimmerDevice instanceof ShimmerBluetooth){
				ShimmerBluetooth spc = (ShimmerBluetooth) shimmerDevice;
				list.addAll(spc.getListofEnabledChannelSignalsandFormats());
			}
			else if(shimmerDevice instanceof Shimmer4sdk){
				
			}
		}
		return list;
	}
	
	//TODO add support for Shimmer4
	public List<String []> getListOfSignalsFromAllConnectedDevices(){
		List<String []> list = new ArrayList<String []>();
		for(ShimmerDevice shimmerDevice:mMapOfBtConnectedShimmers.values()){
			if(shimmerDevice instanceof ShimmerBluetooth){
				ShimmerBluetooth spc = (ShimmerBluetooth) shimmerDevice;
				if(spc.isConnected()){
					list.addAll(spc.getListofEnabledChannelSignalsandFormats());
				}
			}
			else if(shimmerDevice instanceof Shimmer4sdk){
				
			}
		}
		return list;
	}
	
	/**
	 * @param friendlyName
	 * @return comport if found, and null if not
	 */
	public String getComPortFromFriendlyName(String friendlyName) {
		for (BluetoothDeviceDetails bdd:mMapOfParsedBtComPorts.values()) {
			if (bdd.mFriendlyName.equals(friendlyName)) {
				return bdd.mComPort;
			}
		}
		return null;
	}
	
	//TODO add support for Shimmer4
	public List<String[]> getListOfSignalsFromDevices(String address){
		List<String []> list = new ArrayList<String []>();
		ShimmerDevice shimmerDevice = mMapOfBtConnectedShimmers.get(address);
		if(shimmerDevice!=null){
			if(shimmerDevice instanceof ShimmerBluetooth){
				ShimmerBluetooth spc = (ShimmerBluetooth) shimmerDevice;
				list.addAll(spc.getListofEnabledChannelSignalsandFormats());
			}
			else if(shimmerDevice instanceof Shimmer4sdk){
				
			}
		}
		return list;
	}
	
	//---------- GET Methods end -------------------------------
	
	public void stopConnectionThread(String connectionHandle) {
		ConnectThread connectThread = mapOfConnectionThreads.get(connectionHandle);
		if(connectThread!=null) {
			connectThread.disconnect();
		}
	}

	/**
	 * @author User
	 *
	 */
	protected class ConnectThread extends Thread{
		
		String comPort;
		String bluetoothAddress;
		String bluetoothModuleDeviceName; //This is not the Shimmer user assigned name
		ShimmerVerObject shimmerVerObject;
		ExpansionBoardDetails expansionBoardDetails;
		DEVICE_TYPE deviceTypeDetected = DEVICE_TYPE.UNKOWN;
		boolean connectThroughComPort;
		/** Com port for PC/MAC, Bluetooth address for Android*/
		String connectionHandle;
		BluetoothDeviceDetails mDeviceDetails;
		ShimmerRadioInitializer shimmerRadioInitializer = null;
		
		/**For use via Consensys for PC/MAC/Linux
		 * @param comPort
		 * @param shimmerVerObject
		 * @param expansionBoardDetails
		 */
		public ConnectThread(String comPort, ShimmerVerObject shimmerVerObject, ExpansionBoardDetails expansionBoardDetails){
			this.comPort = comPort;
			this.connectionHandle = comPort;
			this.shimmerVerObject = shimmerVerObject;
			this.expansionBoardDetails = expansionBoardDetails;
			this.connectThroughComPort = true;
			this.setName(getClass().getSimpleName()+"_"+connectionHandle);
		}
		
		/**For use via Android
		 * @param macAddress
		 */
		public ConnectThread(String macAddress) {
			this.bluetoothAddress = macAddress;
			this.connectionHandle = macAddress;
			this.connectThroughComPort = false;
			this.setName(getClass().getSimpleName()+"_"+connectionHandle);
		}
		public ConnectThread(BluetoothDeviceDetails devDetails) {
			directConnectUnknownShimmer=false;
			this.bluetoothAddress = devDetails.mShimmerMacId;
			this.connectionHandle = devDetails.mShimmerMacId;
			this.connectThroughComPort = false;
			this.mDeviceDetails = devDetails;
			deviceTypeDetected = devDetails.mDeviceTypeDetected;
			mDeviceDetails = devDetails;
			this.setName(getClass().getSimpleName()+"_"+connectionHandle);
		}
		
		public void disconnect() {
			if(shimmerRadioInitializer!=null) {
				try {
					shimmerRadioInitializer.getSerialCommPort().disconnect();
				} catch (ShimmerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			disconnectShimmer(bluetoothAddress);
		}

		@Override
		public void run(){
			if(directConnectUnknownShimmer){
				try {
					connectUnknownShimmer();
				} catch (ShimmerException e) {
					// TODO Auto-generated catch block
					System.err.println(e.getErrStringFormatted());
					return;
				}
			}else{
				try {
					BluetoothDeviceDetails bluetoothDetails = getBluetoothDeviceDetails(connectionHandle);
					
					if (mDeviceDetails!=null) {
						bluetoothDetails=mDeviceDetails;
					}
					if(bluetoothDetails==null){
						printMessage("NULL BluetoothDeviceDetails for ConnectionHandle: " + connectionHandle + ", returning...");
						sendFeedbackOnConnectStartException(connectionHandle);
						return;
					}

					setBluetoothDeviceDetails(bluetoothDetails);

					sendFeedbackOnConnectionStart(connectionHandle);

					//Handle third party Bluetooth devices here
					if(deviceTypeDetected==DEVICE_TYPE.NONIN_ONYX_II){
						connectNoninOnyxII(comPort, bluetoothAddress);
					}
					if(deviceTypeDetected==DEVICE_TYPE.VERISENSE){
						connectVerisenseDevice(bluetoothDetails);
					}
					else if(deviceTypeDetected==DEVICE_TYPE.LUMAFIT){
						//TODO
					}
					else if(deviceTypeDetected==DEVICE_TYPE.ARDUINO){
						//					connectArduino(comPort);
					}
					//Handle Shimmer devices here
					else{
						// Check if Shimmer already exists in the global map. If not,
						// create a new Shimmer based on whether we can determine the
						// Shimmer hardware version from the Bluetooth module device
						// name (e.g., Shimmer3_3A44, Shimmer4_2784). Early versions of
						// Shimmer shipped with the Bluetooth module name set to
						// "RN42_xxxx" and in these cases we need to connect to the
						// device first to find it's hardware version
						ShimmerDevice shimmerDevice = getShimmerGlobalMap(bluetoothAddress);
						if (shimmerDevice==null || shimmerDevice instanceof ShimmerShell){
							shimmerDevice = createShimmerIfKnown();
						}

						// ShimmerDevice should be created at this point if we were able
						// to determine the hardware version from the bluetooth module
						// name. If yes, then connect and initialise.
						if (shimmerDevice!=null && !(shimmerDevice instanceof ShimmerShell)){
							printMessage("Connecting to " + shimmerDevice.getClass().getSimpleName() + " with connection handle = " + (connectThroughComPort? comPort:bluetoothAddress));
							if(connectThroughComPort){
								if (!comPort.contains(COMPORT_PREFIX)) {
									//Besides Windows, this is used on MacOS to connect Shimmer over BLE, as MacOS does not use BT Classic
									connectShimmer3BleGrpc(bluetoothDetails);
								}else {
									connectExistingShimmer(shimmerDevice, comPort, bluetoothAddress);
								}
							}
							else{
								connectExistingShimmer(shimmerDevice, bluetoothAddress);
							}
						}
						//If Shimmer is still unknown, connect and find out what type it is
						else{
							connectUnknownShimmer();
						}
					}
				} catch (ShimmerException e) {
					sendFeedbackOnConnectionException(e);
					printMessage(e.getErrStringFormatted());
					return;
				}
			}
		}
		
		private ShimmerDevice createShimmerIfKnown() {
			ShimmerDevice shimmerDevice = null;
			deviceTypeDetected = useMockHWID(deviceTypeDetected);
			if(deviceTypeDetected==DEVICE_TYPE.SHIMMER3
					|| deviceTypeDetected==DEVICE_TYPE.SHIMMER3_OUTPUT
					|| deviceTypeDetected==DEVICE_TYPE.SHIMMER_ECG_MD){
				shimmerDevice = createNewShimmer3(comPort, bluetoothAddress);
			}
			else if(deviceTypeDetected==DEVICE_TYPE.SHIMMER4){
				shimmerDevice = createNewShimmer4(comPort, bluetoothAddress);
			}
			else if(deviceTypeDetected==DEVICE_TYPE.SWEATCH){
				shimmerDevice = createNewSweatchDevice(comPort, bluetoothAddress);
			}
			
			if(shimmerDevice!=null){
				if(shimmerVerObject!=null){
					shimmerDevice.setShimmerVersionObjectAndCreateSensorMap(shimmerVerObject);
				}
				if(expansionBoardDetails!=null){
					shimmerDevice.setExpansionBoardDetailsAndCreateSensorMap(expansionBoardDetails);
				}
			}

			return shimmerDevice;
		}

		private BluetoothDeviceDetails setBluetoothDeviceDetails(BluetoothDeviceDetails portDetails){
			if(portDetails!=null){
				bluetoothAddress = portDetails.mShimmerMacId;
				bluetoothModuleDeviceName = portDetails.mFriendlyName;
				deviceTypeDetected = portDetails.mDeviceTypeDetected;
			}
			else{
				bluetoothAddress = UtilShimmer.MAC_ADDRESS_ZEROS;
				bluetoothModuleDeviceName = "Unknown";
				deviceTypeDetected = DEVICE_TYPE.UNKOWN;
			}
			
			return portDetails;
		}

		
		protected void connectUnknownShimmer() throws ShimmerException {
			printMessage("Connecting to new Shimmer with connection handle = " + (connectThroughComPort? comPort:bluetoothAddress));
			
			//radio address will be the com port in case of the PC and the BT address in case of Android
			shimmerRadioInitializer = new ShimmerRadioInitializer();
			final AbstractSerialPortHal serialPortComm = createNewSerialPortComm(comPort, bluetoothAddress);
			shimmerRadioInitializer.setSerialCommPort(serialPortComm);
			serialPortComm.addByteLevelDataCommListener(new ByteLevelDataCommListener(){
				@Override
				public void eventConnected() {
					resolveUnknownShimmer(shimmerRadioInitializer);
				}

				@Override
				public void eventDisconnected() {
					// TODO Auto-generated method stub
					sendFeedbackOnConnectStartException(serialPortComm.getConnectionHandle());
				}
			});

			serialPortComm.connect();
		}
		
		/**
		 * @param shimmerRadioInitializer
		 * @return
		 */
		protected ShimmerDevice resolveUnknownShimmer(ShimmerRadioInitializer shimmerRadioInitializer){

			ShimmerDevice shimmerDeviceNew = null;

			try {
				ShimmerVerObject sVO = shimmerRadioInitializer.readShimmerVerObject();
				if (sVO.isShimmerGen2() || sVO.isShimmerGen3()){
					shimmerDeviceNew = createNewShimmer3(shimmerRadioInitializer, bluetoothAddress);
				} 
				else if(sVO.isShimmerGen3R()) {
					shimmerDeviceNew = createNewShimmer3(shimmerRadioInitializer, bluetoothAddress);
				}
				else if(sVO.isShimmerGen4()){
					shimmerDeviceNew = createNewShimmer4(shimmerRadioInitializer, bluetoothAddress);
				}
				else if(sVO.isSweatchDevice()){
					shimmerDeviceNew = createNewSweatchDevice(shimmerRadioInitializer, bluetoothAddress);
				}


	
				if(shimmerDeviceNew!=null){
					shimmerDeviceNew.setComPort(comPort);
					shimmerDeviceNew.setMacIdFromUart(bluetoothAddress);
					shimmerDeviceNew.setShimmerVersionObjectAndCreateSensorMap(sVO);

	
					//Temporarily added, not needed at the moment so commenting out
	//				ExpansionBoardDetails expBrdDetails = shimmerRadioInitializer.readExpansionBoardID();
	//				if(expBrdDetails!=null){
	//					shimmerDeviceNew.setExpansionBoardDetails(expBrdDetails);
	//				}

	
					initializeNewShimmerCommon(shimmerDeviceNew);



				}
				else {
					shimmerRadioInitializer.getSerialCommPort().disconnect();



				}
			} catch(ShimmerException e) {
				e.printStackTrace();
				try {
					shimmerRadioInitializer.getSerialCommPort().disconnect();
				} catch (Exception ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}
			}
			
			return shimmerDeviceNew;
		}

	}

	protected void connectVerisenseDevice(BluetoothDeviceDetails bdd) {

	}
	
	protected void connectShimmer3BleGrpc(BluetoothDeviceDetails bdd) {

	}
	
	protected void connectNoninOnyxII(String comPort, String bluetoothAddress) throws ShimmerException {
		NoninOnyxIIDevice noninDevice = new NoninOnyxIIDevice(comPort, bluetoothAddress);
		connectThirdPartyDevice(noninDevice, comPort, bluetoothAddress);
	}

//	protected void connectArduino(String comPort) throws DeviceException {
//		ArduinoDevice arduino = new ArduinoDevice();
//		connectThirdPartyDevice(arduino, comPort, comPort);
//	}

	protected void connectThirdPartyDevice(ShimmerDevice shimmerDevice, String comPort, String bluetoothAddress) throws ShimmerException {
		shimmerDevice.setRadio(createNewSerialPortComm(comPort, bluetoothAddress));
		
		initializeNewShimmerCommon(shimmerDevice);

		shimmerDevice.connect();
	}
	
	protected ShimmerDevice createNewSweatchDevice(String comPort, String bluetoothAddress) {
		//Overridden in ShimmerBluetoothManagerPC
		return null;
	}

	protected ShimmerDevice createNewSweatchDevice(ShimmerRadioInitializer shimmerRadioInitializer, String bluetoothAddress) {
		//Overridden in ShimmerBluetoothManagerPC
		return null;
	}

	protected void initializeNewShimmerCommon(ShimmerDevice shimmerDevice) {
		shimmerDevice.addCommunicationRoute(COMMUNICATION_TYPE.BLUETOOTH);
		
		//TODO add to BT connected map after connected callback has been received?
		putShimmerBtConnectedMap(shimmerDevice);
		putShimmerGlobalMap(shimmerDevice.getMacId(), shimmerDevice);
		
		addCallBack(shimmerDevice);
		shimmerDevice.updateThreadName();
		
		// If a fixed Shimmer config hasn't already been set by the driver for a
		// particular device, set it to be the global setting in this class
		if(!shimmerDevice.isFixedShimmerConfigModeSet()) {
			shimmerDevice.setFixedShimmerConfig(mFixedShimmerConfigGlobal);
		}
		//Just to be safe
		if (mFixedShimmerConfigGlobal==FIXED_SHIMMER_CONFIG_MODE.NEUHOME || mFixedShimmerConfigGlobal==FIXED_SHIMMER_CONFIG_MODE.NEUHOMEGSRONLY) {
			if (shimmerDevice instanceof ShimmerBluetooth) {
				((ShimmerBluetooth)shimmerDevice).setSetupDeviceWhileConnecting(true);
			}
		}
		shimmerDevice.setAutoStartStreaming(mAutoStartStreaming);
	}

	
	/**
	 * @param fixedConfig the mFixedConfig to set
	 */
	public void setFixedConfig(FIXED_SHIMMER_CONFIG_MODE fixedConfig) {
		this.mFixedShimmerConfigGlobal = fixedConfig;
	}
	
	public void setAutoStartStreaming(boolean state){
		mAutoStartStreaming = state;
	}

	public void setConnectionExceptionListener(ConnectionExceptionListener listener){
		this.connectionExceptionListener = listener;
	}
	
	private void sendFeedbackOnConnectStartException(String connectionHandle) {
		if(connectionExceptionListener != null){
			connectionExceptionListener.onConnectStartException(connectionHandle);
		}
	}

	private void sendFeedbackOnConnectionStart(String connectionHandle) {
		if(connectionExceptionListener != null){
			connectionExceptionListener.onConnectionStart(connectionHandle);
		}
	}

	private void sendFeedbackOnConnectionException(ShimmerException e) {
		if(connectionExceptionListener != null){
			connectionExceptionListener.onConnectionException(e);
		}
	}


	private void threadSleep(long millis){
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public DEVICE_TYPE useMockHWID(DEVICE_TYPE deviceTypeDetected){
		return deviceTypeDetected;
	}
	
}
