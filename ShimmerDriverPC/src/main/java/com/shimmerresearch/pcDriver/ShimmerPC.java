/*Rev1.7
 * 
 * 
 * 
 * Copyright (c) 2010, Shimmer Research, Ltd.
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:

 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Shimmer Research, Ltd. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Jong Chern Lim
 * @date   November, 2013
 * 
 * Changes since 1.6
 * - cancel timers for log and stream upon disconnect
 * 
 * Changes since 1.5
 * - updates to constructors
 * 
 * Changes since 1.4.3
 * - remove responsetimer to ShimmerBluetooth
 *  
 * Changes since 1.4.2
 * - included call to isreadyforstreaming
 * - new object for callback method (msg_identifier 1 and 2 only)
 * - only runs connect() if mSerialPort==null
 * - added packet reception rate callback
 * 
 * Changes since 1.4
 * - updated states, and comments
 * 

 */

package com.shimmerresearch.pcDriver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.BluetoothProgressReportPerDevice;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.comms.serialPortInterface.AbstractSerialPortHal;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.sensors.SensorEXG;
import com.shimmerresearch.sensors.SensorGSR;
import com.shimmerresearch.sensors.lsm303.SensorLSM303;
import com.shimmerresearch.sensors.mpu9x50.SensorMPU9X50;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.shimmer3.communication.ByteCommunicationJSSC;
import com.shimmerresearch.shimmerConfig.FixedShimmerConfigs.FIXED_SHIMMER_CONFIG_MODE;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDeviceCallbackAdapter;
import com.shimmerresearch.driver.ShimmerMsg;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

public class ShimmerPC extends ShimmerBluetooth implements Serializable{
	
	private boolean mTesting = false;
	/** * */
	private static final long serialVersionUID = -5927054314345918072L;
	
	// Used by the constructor when the user intends to write new settings to the Shimmer device after connection
	//protected transient SerialPort mSerialPort=null;
	protected transient ByteCommunication mByteCommunication;
	ObjectCluster objectClusterTemp = null;
	
//	double mLastSavedCalibratedTimeStamp = -1;
	public BluetoothProgressReportPerDevice progressReportPerDevice;
	
	//TODO switch to using rather then using JSSC directly in this class 
//	private SerialPortCommJssc SerialPortCommJssc = new SerialPortCommJssc(comPort, uniqueId, baudToUse);
	
	protected transient ShimmerDeviceCallbackAdapter mDeviceCallbackAdapter = new ShimmerDeviceCallbackAdapter(this);
	
	public static boolean CONSOLE_PRINT_TX_RX_BYTES = false;

	//--------------- Constructors start ----------------------------

	public ShimmerPC() {
		super();
	}

	public void setTestRadio(ByteCommunication radio) {
		mTesting = true;
		mByteCommunication = radio;
	}
	
	/**
	 * Constructor. Prepares a new Bluetooth session. Upon Connection the configuration of the device is read back and used. No device setup is done. To setup device see other Constructors. 
	 * This constructor was created as a simple constructor for use with MATLAB.
	 * @param comPort The COM port of the Shimmer Device
	 */
	public ShimmerPC(String comPort) {
		this();
		setComPort(comPort);
    	setSamplingRateShimmer(128);
	}

	/**
	 * Constructor. Prepares a new Bluetooth session. Upon Connection the configuration of the device is read back and used. No device setup is done. To setup device see other Constructors.
	 * @param myName  To allow the user to set a unique identifier for each Shimmer device
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	@Deprecated
	public ShimmerPC(String myName, Boolean continousSync) {
		this();
		setShimmerUserAssignedName(myName);
		setContinuousSync(continousSync);
		
    	setSamplingRateShimmer(128);
	}
	
	/**
	 * Constructor. Prepares a new Bluetooth session. Upon Connection the configuration of the device is read back and used. No device setup is done. To setup device see other Constructors.
	 * @param comPort The COM port of the Shimmer Device
	 * @param myBluetoothAddress
	 * @param myName  To allow the user to set a unique identifier for each Shimmer device
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	public ShimmerPC(String comPort, String myBluetoothAddress, String myName, Boolean continousSync) {
		this(myName, continousSync);
		setComPort(comPort);
		mMyBluetoothAddress=myBluetoothAddress;
	}

	/**Shimmer 3 Constructor
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 * @param enableLowPowerAccel Enables low power Accel on the wide range accelerometer
	 * @param enableLowPowerGyro Enables low power Gyro
	 * @param enableLowPowerMag Enables low power Mag
	 * @param gyroRange Sets the Gyro Range of the accelerometer
	 * @param magRange Sets the Mag Range
	 * @param exg1 Sets the register of EXG chip 1
	 * @param exg2 Setes the register of EXG chip 2
	 */
	@Deprecated //no longer allowed to enable low power 
	public ShimmerPC(String userAssignedName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync, boolean enableLowPowerAccel, boolean enableLowPowerGyro, boolean enableLowPowerMag, int gyroRange, int magRange,byte[] exg1,byte[] exg2, int orientation) {
		super(userAssignedName, samplingRate, null, accelRange, gsrRange, gyroRange, magRange);
		setContinuousSync(continousSync);

		//TODO Old approach - start (migrate to new approach)
//		setAccelRange(accelRange);
//		setGSRRange(gsrRange);
//		mSetEnabledSensors=setEnabledSensors;
//		setLowPowerMag(enableLowPowerMag);
//		setLowPowerAccelWR(enableLowPowerAccel);
//		setLowPowerGyro(enableLowPowerGyro);
//		setGyroRange(gyroRange);
//		setLSM303MagRange(magRange);
//		mSetupEXG = true;
//		setEXG1RegisterArray(exg1);
//		setEXG2RegisterArray(exg2);
		
//		setSetupDeviceWhileConnecting(true);
//    	setSamplingRateShimmer(samplingRate);
		setupOrientation(orientation, samplingRate);
		//TODO Old approach - end
		
		//TODO New approach - start
//		setFixedShimmerConfig(FIXED_SHIMMER_CONFIG_MODE.USER);
//		addFixedShimmerConfig(Shimmer3.GuiLabelConfig.SHIMMER_AND_SENSORS_SAMPLING_RATE, samplingRate);
		addFixedShimmerConfig(Shimmer3.GuiLabelConfig.ENABLED_SENSORS, setEnabledSensors);

		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_MAG_LPM, enableLowPowerMag);
		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_LPM, enableLowPowerAccel);
//		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RANGE, accelRange);
//		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_MAG_RANGE, magRange);
		
		addFixedShimmerConfig(SensorMPU9X50.GuiLabelConfig.MPU9X50_GYRO_LPM, enableLowPowerGyro);
//		addFixedShimmerConfig(SensorMPU9X50.GuiLabelConfig.MPU9X50_GYRO_RANGE, gyroRange);
//
//		addFixedShimmerConfig(SensorGSR.GuiLabelConfig.GSR_RANGE, gsrRange);
		addFixedShimmerConfig(SensorEXG.GuiLabelConfig.EXG_BYTES, Arrays.asList(exg1, exg2));
		
		//TODO New approach - end
	}
	
	/**Shimmer 3 Constructor
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param gyroRange Sets the Gyro Range of the accelerometer
	 * @param magRange Sets the Mag Range
	 */
	public ShimmerPC(String userAssignedName, double samplingRate, int accelRange, int gsrRange, Integer[] sensorIdsToEnable, int gyroRange, int magRange, int orientation, int pressureResolution) {
		super(userAssignedName, samplingRate, sensorIdsToEnable, accelRange, gsrRange, gyroRange, magRange, pressureResolution);
		setupOrientation(orientation, samplingRate);
	}
	
	/**
	*  Shimmer2, Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately.
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 * @param magGain Set mag gain
	 */
	@Deprecated //because continousSync does nothing
	public ShimmerPC(String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync, int magGain, int orientation) {
		super(myName,samplingRate, setEnabledSensors, accelRange, gsrRange, magGain);
		//		this(myName, continousSync);
		//super(myName, samplingRate, null, accelRange, gsrRange, magGain);
		//setContinuousSync(continousSync);

		//TODO Old approach - start (migrate to new approach)
//		setAccelRange(accelRange);
//		setLSM303MagRange(magGain);
//		setGSRRange(gsrRange);
//		mSetEnabledSensors=setEnabledSensors;

//		setSetupDeviceWhileConnecting(true);
//    	setSamplingRateShimmer(samplingRate);
		setupOrientation(orientation, samplingRate);
		//TODO Old approach - end

		//TODO New approach - start
//		setFixedShimmerConfig(FIXED_SHIMMER_CONFIG_MODE.USER);
//		addFixedShimmerConfig(Shimmer3.GuiLabelConfig.SHIMMER_AND_SENSORS_SAMPLING_RATE, samplingRate);
		//addFixedShimmerConfig(Shimmer3.GuiLabelConfig.ENABLED_SENSORS, setEnabledSensors);
//		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RANGE, accelRange);
//		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_MAG_RANGE, magGain);
//		addFixedShimmerConfig(SensorGSR.GuiLabelConfig.GSR_RANGE, gsrRange);
		//TODO New approach - end
	}
	
	/**
	*  Shimmer2, Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately. Note enabled sensors are only for legacy Shimmer2, no sensor map is supported
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	* @param magGain Set mag gain
	 */
	public ShimmerPC(String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, int magGain, int orientation) {
		super(myName,samplingRate, setEnabledSensors, accelRange, gsrRange, magGain);
		setupOrientation(orientation, samplingRate);
	}
	
	// Javadoc comment follows
    /**
     * @deprecated
     * The Shimmer constructor should only have one Shimmer2R constructor
     */
    @Deprecated
	public ShimmerPC( String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync) {
    	this(myName, continousSync);
    	
		//TODO Old approach - start (migrate to new approach)
//    	setAccelRange(accelRange);
//		setGSRRange(gsrRange);
//		mSetEnabledSensors=setEnabledSensors;
		
//		setSetupDeviceWhileConnecting(true);
//    	setSamplingRateShimmer(samplingRate);
		//TODO Old approach - end

		//TODO New approach - start
		setFixedShimmerConfig(FIXED_SHIMMER_CONFIG_MODE.USER);
		addFixedShimmerConfig(Shimmer3.GuiLabelConfig.SHIMMER_AND_SENSORS_SAMPLING_RATE, samplingRate);
		addFixedShimmerConfig(Shimmer3.GuiLabelConfig.ENABLED_SENSORS, setEnabledSensors);
		addFixedShimmerConfig(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RANGE, accelRange);
		addFixedShimmerConfig(SensorGSR.GuiLabelConfig.GSR_RANGE, gsrRange);
		//TODO New approach - end
	}
    
    
	/** Replaces ShimmerDocked
	 * @param dockId
	 * @param slotNumber
	 */
	public ShimmerPC(String dockId, int slotNumber, COMMUNICATION_TYPE communicationType){
		setDockInfo(dockId, slotNumber);
		addCommunicationRoute(communicationType);
    	setSamplingRateShimmer(128);
	}
	
	/** Replaces ShimmerDocked
	 * @param dockId
	 * @param slotNumber
	 */
	public ShimmerPC(String dockId, int slotNumber, String macId, COMMUNICATION_TYPE communicationType){
		this(dockId, slotNumber, communicationType);
		setMacIdFromUart(macId);
	}

	//--------------- Constructors End ----------------------------

	/**
	 * Connect to device specified by address
	 * @param address  The comport of the device e.g. COM32, note device will have to be paired first
	 * @param empty  This is for forward compatibility, in the event a choice of library is offered, any string value can be entered now ~ does nothing
	 */
	@Override
	public synchronized void connect(final String address, String unusedVariable) {
		
		Thread thread = new Thread(){
			public void run(){
				setBluetoothRadioState(BT_STATE.CONNECTING);
				
//				mMyBluetoothAddress = address;
				startTimerConnectingTimeout();
				
				setIamAlive(false);
				getListofInstructions().clear();
				
				if (mByteCommunication==null || mTesting){
					setComPort(address);
					if (!mTesting) {
						mByteCommunication = new ByteCommunicationJSSC(address);
					} else { // do nothingit should already be set
						
					}

					try {
						/*
						consolePrintLn("Connecting to Shimmer on " + address);
						consolePrintLn("Port open: " + mSerialPort.openPort());
						consolePrintLn("Params set: " + mSerialPort.setParams(115200, 8, 1, 0));
						consolePrintLn("Port Status : " + Boolean.toString(mSerialPort.isOpened()));
						*/
						consolePrintLn("Connecting to Shimmer on " + address);
						mByteCommunication.openPort();
						consolePrintLn("Port Status : " + Boolean.toString(mByteCommunication.isOpened()));
						if (mIOThread != null) { 
							mIOThread = null;
							mPThread = null;
						}
						
						if (mByteCommunication.isOpened() && mBluetoothRadioState!=BT_STATE.DISCONNECTED){
//						if (mSerialPort.isOpened() && mState!=BT_STATE.NONE && mState!=BT_STATE.DISCONNECTED){
//							setState(BT_STATE.CONNECTED);
							setIsConnected(true);

							mIOThread = new IOThread();
							mIOThread.setName(getClass().getSimpleName()+"-"+mMyBluetoothAddress+"-"+getComPort());
							mIOThread.start();
							
							if(mUseProcessingThread){
								mPThread = new ProcessingThread();
								mPThread.start();
							}
							initialize();
						} else {
							disconnectNoException();
						}
					}
					catch (SerialPortException ex){
						consolePrintException(ex.getMessage(), ex.getStackTrace());
						
//						connectionLost();
//						closeConnection();
						disconnectNoException();
						setBluetoothRadioState(BT_STATE.CONNECTION_FAILED);
					}
				} 
				else {
					//TODO need to handle if serial po
				}
				
			}

	    };
	    
	    thread.setName("ShimmerPC-"+getMacId()+"-"+mShimmerUserAssignedName);
	    
	    if (!isConnected()){
	    	thread.start();
	    }
	    
//	    if (getState()==STATE.NONE
//	    		|| getState()==STATE.CONNECTION_LOST
//	    		|| getState()==STATE.DISCONNECTED){
//	    	thread.start();
//	    }
	}
	
	@Override
	public boolean bytesAvailableToBeRead() {
		try {
			if(mByteCommunication != null){
				if (mByteCommunication.getInputBufferBytesCount()!=0){
					return true;
				}
			}
		} catch (SerialPortException | NullPointerException ex) {
			consolePrintException(ex.getMessage(), ex.getStackTrace());

			connectionLost();
//			e.printStackTrace();
		}
		return false;
	}
	
	public int availableBytes(){
		try {
			if(mByteCommunication != null){
				return mByteCommunication.getInputBufferBytesCount();
			}
			else{
				return 0;
			}
		} catch (SerialPortException | NullPointerException ex) {
			consolePrintException(ex.getMessage(), ex.getStackTrace());
			connectionLost();
			return 0;
		}
	}

	@Override
	public void writeBytes(byte[] data) {
		try {
			if(mByteCommunication != null){
				if(CONSOLE_PRINT_TX_RX_BYTES) {
					consolePrintLn("TX: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(data));
				}
				mByteCommunication.writeBytes(data);
			}
		} catch (SerialPortException | NullPointerException ex) {
			consolePrintLn("Tried to writeBytes but port is closed");
			consolePrintException(ex.getMessage(), ex.getStackTrace());
			connectionLost();
		}
	}

	@Override
	protected byte[] readBytes(int numberOfBytes) {
		if(numberOfBytes<=0){
			consolePrintLn("Tried to readBytes but numberOfBytes is a negative number");
			return null;
		}
		
		try {
			if(mByteCommunication != null){
				if (mByteCommunication.isOpened()){
					byte[] data = mByteCommunication.readBytes(numberOfBytes, AbstractSerialPortHal.SERIAL_PORT_TIMEOUT_2000);
					if(CONSOLE_PRINT_TX_RX_BYTES) {
						consolePrintLn("RX: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(data));
					}
					return(data);
				} else {
					consolePrintLn("Tried to readBytes but port is closed");
				}
			}
		} catch (SerialPortException | NullPointerException e) {
			connectionLost();
			consolePrintLn("Tried to readBytes but serial port error");
			consolePrintException(e.getMessage(), e.getStackTrace());
//			e.printStackTrace();
		} catch (SerialPortTimeoutException e) {
			consolePrintLn("Tried to readBytes but serial port timed out");
			consolePrintException(e.getMessage(), e.getStackTrace());
//			e.printStackTrace();

			// TODO if in the middle of connecting or configuring, trigger a
			// connectionLost()? BT_STATE.CONFIGURING not currently used by
			// ShimmerBluetooth
			if(mBluetoothRadioState==BT_STATE.CONNECTING
					|| mBluetoothRadioState==BT_STATE.CONFIGURING){
				connectionLost();
			}
		}
		return null;
	}

	@Override
	protected byte readByte() {
		byte[] b = readBytes(1);
		return b[0];
	}

	@Override
	protected void inquiryDone() {
		mDeviceCallbackAdapter.inquiryDone();
		isReadyForStreaming();
		
		//TODO 2018-02-26 MN: Remove below when ready 
//		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
//		sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
//		isReadyForStreaming();
	}

	@Override
	protected void isReadyForStreaming() {
		mDeviceCallbackAdapter.isReadyForStreaming();
		restartTimersIfNull();
		
		//TODO 2018-02-26 MN: Remove below when ready 
//		// Send msg fully initialized, send notification message,  
//		// Do something here
//        mIsInitialised = true;
////        prepareAllAfterConfigRead();
//
//        if (mSendProgressReport){
//        	finishOperation(BT_STATE.CONNECTING);
//        }
//        
//		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_FULLY_INITIALIZED, getMacId(), getComPort());
//		sendCallBackMsg(MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
//		
//		restartTimersIfNull();
//		setBluetoothRadioState(BT_STATE.CONNECTED);
	}

	@Override
	public void calculatePacketReceptionRateCurrent(int intervalMs) {
		super.calculatePacketReceptionRateCurrent(intervalMs);
		mDeviceCallbackAdapter.sendCallbackPacketReceptionRateCurrent();
	}
	
	@Override
	protected void dataHandler(ObjectCluster ojc) {
		mDeviceCallbackAdapter.dataHandler(ojc);
	}

	public byte[] returnRawData(){
		if (objectClusterTemp!=null){
			byte[] data= objectClusterTemp.mRawData;
			//objectClusterTemp = null;
			return data;
		
		}
		else 
			return null;
	}
	
	@Override
	@Deprecated //Use disconnect() instead
	public void stop() {
		disconnectNoException();
	}

	@Override
	public void disconnect() throws ShimmerException {
//		super.disconnect();
		stopAllTimers();
		closeConnection();
		setBluetoothRadioState(BT_STATE.DISCONNECTED);
	}

	public void disconnectNoException()  {
		try {
			disconnect();
		} catch (ShimmerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	protected void connectionLost() {
		disconnectNoException();
//		closeConnection();
//		consolePrintLn("Connection Lost");
		setBluetoothRadioState(BT_STATE.CONNECTION_LOST);
	}
	
	private void closeConnection(){
		try {
			if (mIOThread != null) {
				mIOThread.stop = true;
				
				// Closing serial port before before thread is finished stopping throws an error so waiting here.
				// Bounded join instead of an empty-body busy-wait, so this can't block indefinitely.
				try {
					mIOThread.join(2000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}

				mIOThread = null;

				if(mUseProcessingThread){
					mPThread.stop = true;
					try {
						mPThread.join(2000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					mPThread = null;
				}
			}
			mIsStreaming = false;
			mIsInitialised = false;

			setBluetoothRadioState(BT_STATE.DISCONNECTED);
			if (mByteCommunication != null) {
				if (mByteCommunication.isOpened()) {
					mByteCommunication.purgePort(1);
					mByteCommunication.purgePort(2);
					mByteCommunication.closePort();
				}
			}
			mByteCommunication = null;
		} catch (Exception ex) {
			consolePrintException(ex.getMessage(), ex.getStackTrace());
			setBluetoothRadioState(BT_STATE.DISCONNECTED);
		}			
	}
	
	public void setSerialPort(SerialPort sp){
		if (mByteCommunication == null) {
			mByteCommunication = new ByteCommunicationJSSC(sp);
		}
		if(mByteCommunication instanceof ByteCommunicationJSSC) {
			((ByteCommunicationJSSC)mByteCommunication).setSerialPort(sp);
			getSamplingRateShimmer();
		
			if (mByteCommunication.isOpened()){
				setBluetoothRadioState(BT_STATE.CONNECTING);
			}
			if (mByteCommunication.isOpened() && mBluetoothRadioState!=BT_STATE.DISCONNECTED){
				//			if (mSerialPort.isOpened() && mState!=BT_STATE.NONE && mState!=BT_STATE.DISCONNECTED){
				//				setState(BT_STATE.CONNECTED);
				setIsConnected(true);
	
				mIOThread = new IOThread();
				mIOThread.start();
				if(mUseProcessingThread){
					mPThread = new ProcessingThread();
					mPThread.start();
				}
				initialize();
			}
		}
	}

	@Override
	protected void sendStatusMSGtoUI(String msg) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void sendStatusMsgPacketLossDetected() {
		// TODO Auto-generated method stub
		
	}
	
	public String message;
	@Override
	protected void printLogDataForDebugging(String msg) {
		consolePrintLn(msg);
//		System.out.println(msg);
	}

	@Override
	public boolean setBluetoothRadioState(BT_STATE state) {
		boolean isChanged = super.setBluetoothRadioState(state);
		mDeviceCallbackAdapter.setBluetoothRadioState(state, isChanged);
		return isChanged;

		//TODO 2018-02-26 MN: Remove below when ready 
//		boolean changed = super.setBluetoothRadioState(state);
//
//		if(mBluetoothRadioState==BT_STATE.CONNECTED){
//			mIsInitialised = true;
//			mIsStreaming = false;
//		}
//		else if(mBluetoothRadioState==BT_STATE.STREAMING){
//			mIsStreaming = true;
//		}		
//		else if((mBluetoothRadioState==BT_STATE.DISCONNECTED)
//				||(mBluetoothRadioState==BT_STATE.CONNECTION_LOST)
//				||(mBluetoothRadioState==BT_STATE.CONNECTION_FAILED)){
//			setIsConnected(false);
//			mIsStreaming = false;
//			mIsInitialised = false;
//		}
//		
//		consolePrintLn("State change: " + mBluetoothRadioState.toString());
//
//		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
//		if (changed){
//			sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
//		}
//		return changed;
	}
	
	@Override
	public void startOperation(BT_STATE currentOperation){
		this.startOperation(currentOperation, 1);
		consolePrintLn(currentOperation + " START");
	}
	

	@Override
	public void startOperation(BT_STATE currentOperation, int totalNumOfCmds){
		mDeviceCallbackAdapter.startOperation(currentOperation, totalNumOfCmds);
		
		//TODO 2018-02-26 MN: Remove below when ready 
//		consolePrintLn(currentOperation + " START");
//
//		progressReportPerDevice = new BluetoothProgressReportPerDevice(this, currentOperation, totalNumOfCmds);
//		progressReportPerDevice.mOperationState = BluetoothProgressReportPerDevice.OperationState.INPROGRESS;
//		
//		CallbackObject callBackObject = new CallbackObject(mBluetoothRadioState, getMacId(), getComPort(), progressReportPerDevice);
//		sendCallBackMsg(MSG_IDENTIFIER_PROGRESS_REPORT_PER_DEVICE, callBackObject);
	}
	
	@Override
	public void finishOperation(BT_STATE state){
		mDeviceCallbackAdapter.finishOperation(state);

		//TODO 2018-02-26 MN: Remove below when ready 
//		if(progressReportPerDevice!=null){
//			consolePrintLn("CURRENT OPERATION " + progressReportPerDevice.mCurrentOperationBtState + "\tFINISHED:" + state);
//			
//			if(progressReportPerDevice.mCurrentOperationBtState == state){
//
//				progressReportPerDevice.finishOperation();
//				progressReportPerDevice.mOperationState = BluetoothProgressReportPerDevice.OperationState.SUCCESS;
//				//JC: moved operationFinished to is ready for streaming, seems to be called before the inquiry response is received
//				super.operationFinished();
//				CallbackObject callBackObject = new CallbackObject(mBluetoothRadioState, getMacId(), getComPort(), progressReportPerDevice);
//				sendCallBackMsg(MSG_IDENTIFIER_PROGRESS_REPORT_PER_DEVICE, callBackObject);
//				
//				//Removed to try and stop progress going to 0% after finishing
////				progressReportPerDevice = new ProgressReportPerDevice(this, BT_STATE.NONE, 1);
////				callBackObject = new CallbackObject(mState, getMacId(), mUniqueID, progressReportPerDevice);
////				sendCallBackMsg(MSG_IDENTIFIER_PROGRESS_REPORT_PER_DEVICE, callBackObject);
//			}
//		}
//		else {
//			consolePrintLn("CURRENT OPERATION - UNKNOWN, null progressReportPerDevice" + "\tFINISHED:" + state);
//		}
	}
	
	@Override
	protected void hasStopStreaming() {
		mDeviceCallbackAdapter.hasStopStreaming();
		
		//TODO 2018-02-26 MN: Remove below when ready 
//		// Send a notification msg to the UI through a callback (use a msg identifier notification message)
//				// Do something here
//		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STOP_STREAMING, getMacId(), getComPort());
//		sendCallBackMsg(MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
//		startTimerReadStatus();
//		setBluetoothRadioState(BT_STATE.CONNECTED);
	}
	
	@Override
	protected void isNowStreaming() {
		mDeviceCallbackAdapter.isNowStreaming();

		//TODO 2018-02-26 MN: Remove below when ready 
//		// Send a notification msg to the UI through a callback (use a msg identifier notification message)
//		// Do something here
//		
//		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_START_STREAMING, getMacId(), getComPort());
//		sendCallBackMsg(MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
//		
//		if (isSDLogging()){
//			setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
//		} else {
//			setBluetoothRadioState(BT_STATE.STREAMING);
//		}
	}


	@Override
	protected void eventLogAndStreamStatusChanged(byte currentCommand) {
		
//		if(currentCommand==START_LOGGING_ONLY_COMMAND){
//			TODO this causing a problem Shimmer Bluetooth disconnects
//			setState(BT_STATE.SDLOGGING);
//		}
		if(currentCommand==STOP_LOGGING_ONLY_COMMAND){
			//TODO need to query the Bluetooth connection here!
			if(mIsStreaming){
				setBluetoothRadioState(BT_STATE.STREAMING);
			}
			else if(isConnected()){
				setBluetoothRadioState(BT_STATE.CONNECTED);
			}
			else{
				setBluetoothRadioState(BT_STATE.DISCONNECTED);
			}
		}
		else{
			if(mIsStreaming && isSDLogging()){
				setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
			}
			else if(mIsStreaming){
				setBluetoothRadioState(BT_STATE.STREAMING);
			}
			else if(isSDLogging()){
				setBluetoothRadioState(BT_STATE.SDLOGGING);
			}
			else{
//				if(!isStreaming() && !isSDLogging() && isConnected()){
				if(!mIsStreaming && !isSDLogging() && isConnected() && mBluetoothRadioState!=BT_STATE.CONNECTED){
					setBluetoothRadioState(BT_STATE.CONNECTED);	
				}
//				if(getBTState() == BT_STATE.INITIALISED){
//					
//				}
//				else if(getBTState() != BT_STATE.CONNECTED){
//					setState(BT_STATE.CONNECTED);
//				}
				
				CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
				sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
			}
		}
		
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		// NOT USED IN THIS CLASS
	}
	
	@Override
	protected void sendProgressReport(BluetoothProgressReportPerCmd pRPC) {
		mDeviceCallbackAdapter.sendProgressReport(pRPC);
		
		//TODO 2018-02-26 MN: Remove below when ready 
//		if(progressReportPerDevice!=null){
//			progressReportPerDevice.updateProgress(pRPC);
//			
//			CallbackObject callBackObject = new CallbackObject(mBluetoothRadioState, getMacId(), getComPort(), progressReportPerDevice);
//			sendCallBackMsg(MSG_IDENTIFIER_PROGRESS_REPORT_PER_DEVICE, callBackObject);
//			
////			consolePrintLn("ProgressCounter" + progressReportPerDevice.mProgressCounter + "\tProgressEndValue " + progressReportPerDevice.mProgressEndValue);
//			
//			if(progressReportPerDevice.mProgressCounter==progressReportPerDevice.mProgressEndValue){
//				finishOperation(progressReportPerDevice.mCurrentOperationBtState);
//			}
//		}
	}

	@Override
	public ShimmerPC deepClone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (ShimmerPC) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	protected void interpretDataPacketFormat(Object object, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void batteryStatusChanged() {
		mDeviceCallbackAdapter.batteryStatusChanged();
		
//		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
//		sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
	}

	@Override
	protected void dockedStateChange() {
		mDeviceCallbackAdapter.dockedStateChange();
		
//		CallbackObject callBackObject = new CallbackObject(MSG_IDENTIFIER_SHIMMER_DOCKED_STATE_CHANGE, getMacId(), getComPort());
//		sendCallBackMsg(MSG_IDENTIFIER_SHIMMER_DOCKED_STATE_CHANGE, callBackObject);
	}

	@Override
	public void createConfigBytesLayout() {
		if (mShimmerVerObject.mHardwareVersion==HW_ID.UNKNOWN) {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), HW_ID.SHIMMER_3);
		} else {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), mShimmerVerObject.mHardwareVersion);
		}
	}
	
	public boolean isChannelEnabled(int sensorKey) {
		return super.isSensorEnabled(COMMUNICATION_TYPE.BLUETOOTH, sensorKey);
//		SensorEnabledDetails sensor = mSensorEnabledMap.get(sensorKey);
//	    if(sensor!=null){
//		    return sensor.isEnabled();
//	    }
//	    return false;
	}

	//Need to override here because ShimmerDevice class uses a different map
	@Override
	public String getSensorLabel(int sensorKey) {
		//TODO 2017-08-03 MN: super does this but in a different way, don't know is either is better
		super.getSensorLabel(sensorKey);
		SensorDetails sensor = mSensorMap.get(sensorKey);
	    if(sensor!=null){
		    return sensor.mSensorDetailsRef.mGuiFriendlyLabel;
	    }
		return null;
	}

}
