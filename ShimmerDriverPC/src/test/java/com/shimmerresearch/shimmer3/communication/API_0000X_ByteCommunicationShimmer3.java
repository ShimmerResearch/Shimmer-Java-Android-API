package com.shimmerresearch.shimmer3.communication;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;

import bolts.TaskCompletionSource;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // Test methods will be run in alphabetical order
public class API_0000X_ByteCommunicationShimmer3 extends BasicProcessWithCallBack{
	ShimmerPC mDevice;
	TaskCompletionSource<Boolean> mStreamingTask;
	ByteCommunicationSimulatorS3 mByteCommunicationSimulatorS3;

	@Before
    public void setUp() {
		mByteCommunicationSimulatorS3 = new ByteCommunicationSimulatorS3("COM99");
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(mByteCommunicationSimulatorS3);
		setWaitForData(mDevice);
    }

	@After
	public void tearDown() {
		mDevice.disconnectNoException();
	}
    
    @Test
    public void test001_testConnectandDisconnect() {
    	mByteCommunicationSimulatorS3.setIsNewBMPSupported(false);
    	ByteCommunicationTestUtils.connectAndWaitUntilInitialised(mDevice);
			
    	if (!mDevice.isConnected()) {
    		assert(false);
    	}
    	if (!mDevice.getFirmwareVersionParsed().equals("LogAndStream v0.16.0")) {
    		assert(false);
    	}
    	if (!(mDevice.getHardwareVersion()==HW_ID.SHIMMER_3)) {
    		assert(false);
    	}
    	// A Shimmer3 keeps the link CRC when sensing stops, so connecting turns
    	// on the driver default (DEV-976)
    	assertEquals(BT_CRC_MODE.ONE_BYTE_CRC, mDevice.getCurrentBtCommsCrcMode());
    	
    	System.out.println(mDevice.getHardwareVersionParsed());
    	if (!mDevice.getHardwareVersionParsed().equals("Shimmer3")) {
    		assert(false);
    	}
    	
    	if(!mByteCommunicationSimulatorS3.isGetBmp280CalibrationCoefficientsCommand) {
    		assert(false);
    	}
    	
		if(!mDevice.mSensorBMPX80.mSensorType.equals(SENSORS.BMP280)){
    		assert(false);
		}
		
		
    }
    
    @Test
    public void test002_testConnectandDisconnect_NewBMPSupported() {
    	mByteCommunicationSimulatorS3.setIsNewBMPSupported(true);
    	ByteCommunicationTestUtils.connectAndWaitUntilInitialised(mDevice);
			
    	if (!mDevice.isConnected()) {
    		assert(false);
    	}
    	if (!mDevice.getFirmwareVersionParsed().equals("LogAndStream v0.16.9")) {
    		assert(false);
    	}
    	if (!(mDevice.getHardwareVersion()==HW_ID.SHIMMER_3)) {
    		assert(false);
    	}
    	
    	System.out.println(mDevice.getHardwareVersionParsed());
    	if (!mDevice.getHardwareVersionParsed().equals("Shimmer3")) {
    		assert(false);
    	}
    	
    	if (mDevice.isSupportedSdLogSync()) {
    		assert(false);
    	}
    	
    	if(!mByteCommunicationSimulatorS3.isGetPressureCalibrationCoefficientsCommand) {
    		assert(false);
    	}
    	
		if(!mDevice.mSensorBMPX80.mSensorType.equals(SENSORS.BMP280)){
    		assert(false);
		}
      
    	ArrayList<SensorDetails> listofsensorDetails = (ArrayList<SensorDetails>) mDevice.getListOfEnabledSensors();
    	
    	for(SensorDetails sd: listofsensorDetails) {
    		if (sd.isEnabled()) {
    			for (ChannelDetails cd: sd.getListOfChannels()) {
    				System.out.print(cd.mGuiName + " ; ");
    			}
    			System.out.println();
    		}
    	}
    	
    	LinkedHashMap<SENSORS, AbstractSensor> mapOfSensors = mDevice.getMapOfSensorsClasses();
    	for (AbstractSensor sensor:mapOfSensors.values()) {
    		if (sensor.getNumberOfEnabledChannels(COMMUNICATION_TYPE.BLUETOOTH)>0) {
    			System.out.println(sensor.getClass().getName() + " ; " + sensor.getSensorName());
    		}
    	}
    }

    
    ArrayList<ObjectCluster> mListOJC;
    
	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		// TODO Auto-generated method stub
		  int ind = shimmerMSG.mIdentifier;

		  Object object = (Object) shimmerMSG.mB;

		if (ind == ShimmerPC.MSG_IDENTIFIER_STATE_CHANGE) {
			if (ind == ShimmerPC.MSG_IDENTIFIER_STATE_CHANGE) {
				CallbackObject callbackObject = (CallbackObject)object;
				
				if (callbackObject.mState == BT_STATE.CONNECTED) {
					if (mDevice.isInitialised()) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					}
					
				}
		}
	} else if (ind == ShimmerPC.MSG_IDENTIFIER_DATA_PACKET) {
		System.out.println("Shimmer MSG_IDENTIFIER_DATA_PACKET");
		ObjectCluster objc = (ObjectCluster) shimmerMSG.mB;
		mListOJC.add(objc);
	} 

}
   
}
