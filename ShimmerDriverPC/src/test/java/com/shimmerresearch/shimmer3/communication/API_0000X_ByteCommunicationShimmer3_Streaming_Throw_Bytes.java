package com.shimmerresearch.shimmer3.communication;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

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
public class API_0000X_ByteCommunicationShimmer3_Streaming_Throw_Bytes extends BasicProcessWithCallBack{
	ShimmerPC mDevice;
	TaskCompletionSource<Boolean> mStreamingTask;
	ByteCommunicationSimulatorS3 mByteCommunicationSimulatorS3;

	@Before
    public void setUp() {
		mByteCommunicationSimulatorS3 = new ByteCommunicationSimulatorS3_streaming_with_improved_throw_bytes("COM99");
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(mByteCommunicationSimulatorS3);
		setWaitForData(mDevice);
    }

	@After
	public void tearDown() {
		mDevice.disconnectNoException();
	}
    
    ArrayList<ObjectCluster> mListOJC;
    @Test
    public void test001_testStreaming() {
    	mListOJC = new ArrayList<ObjectCluster>();
    	mByteCommunicationSimulatorS3.setIsNewBMPSupported(false);
    	ByteCommunicationTestUtils.connectAndWaitUntilInitialised(mDevice);
    	mStreamingTask = new TaskCompletionSource<Boolean>();

		try {
			mDevice.startStreaming();
		} catch (ShimmerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			mStreamingTask.getTask().waitForCompletion(2, TimeUnit.SECONDS);
			System.out.println("Number of ObjectClusters received: " + mListOJC.size());
			if(mListOJC.size()!=1) {//only one is expected because only the first packet is followed by garbage bytes which causes the first packet to be thrown away
	    		assert(false);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
    }
    
    
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
