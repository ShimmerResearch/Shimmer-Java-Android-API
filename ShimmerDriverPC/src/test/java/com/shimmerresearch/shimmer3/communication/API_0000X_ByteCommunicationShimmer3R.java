package com.shimmerresearch.shimmer3.communication;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.calibration.CalibDetails;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic;
import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.sensors.adxl371.SensorADXL371;
import com.shimmerresearch.sensors.bmpX80.CalibDetailsBmp390;
import com.shimmerresearch.sensors.lis2dw12.SensorLIS2DW12;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS3MDL;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS2MDL;
import com.shimmerresearch.sensors.lsm303.SensorLSM303;
import com.shimmerresearch.sensors.lsm6dsv.SensorLSM6DSV;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;

import bolts.TaskCompletionSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // Test methods will be run in alphabetical order
public class API_0000X_ByteCommunicationShimmer3R extends BasicProcessWithCallBack{
	ShimmerPC mDevice;
	TaskCompletionSource<Boolean> mWaitTask;
	ByteCommunicationSimulatorS3R mByteCommunicationSimulatorS3R;
	@Before
    public void setUp() {
		mByteCommunicationSimulatorS3R = new ByteCommunicationSimulatorS3R("COM99");
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(mByteCommunicationSimulatorS3R);
		setWaitForData(mDevice);
    }
    
    @Test
    public void test001_testConnectandDisconnect() {
    	mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);//Just to give time to connect to finish
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
    	if (!mDevice.isConnected()) {
    		assert(false);
    	}
    	if (!mDevice.getFirmwareVersionParsed().equals("LogAndStream v0.0.1")) {
    		assert(false);
    	}
    	if (!(mDevice.getHardwareVersion()==HW_ID.SHIMMER_3R)) {
    		assert(false);
    	}
    	// LogAndStream v0.0.1 is before v1.00.011, so this Shimmer3R would drop
    	// a link CRC whenever sensing stops: connecting must not turn one on (DEV-976)
    	assertEquals(BT_CRC_MODE.OFF, mDevice.getCurrentBtCommsCrcMode());
    	
    	System.out.println(mDevice.getHardwareVersionParsed());
    	if (!mDevice.getHardwareVersionParsed().equals(ShimmerVerDetails.mMapOfShimmerRevisions.get(HW_ID.SHIMMER_3R))) {
    		assert(false);
    	}
    	if(!mByteCommunicationSimulatorS3R.isGetPressureCalibrationCoefficientsCommand) {
    		assert(false);
    	}
    	if(!mDevice.mSensorBMPX80.mSensorType.equals(SENSORS.BMP390)){
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


	@Test
	public void test002_ConnectandTestBMP390() {
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
    	if (!mDevice.isConnected()) {
    		assert(false);
    	}
    	
    	//BMP390 data parsing
    	try{
    		mDevice.mSensorBMPX80.parseCalParamByteArray(mByteCommunicationSimulatorS3R.getPressureResoTest(),CALIB_READ_SOURCE.INFOMEM);
    		long[] dataPacket = UtilParseData.parseData(mByteCommunicationSimulatorS3R.getTestDataPacket(), mByteCommunicationSimulatorS3R.getTestDataType());	//uncalib
    		double UT = (double)dataPacket[7];
			double UP = (double)dataPacket[8];
			
			double[] bmp390caldata = mDevice.mSensorBMPX80.calibratePressureSensorData(UP, UT);
			double pressure = bmp390caldata[0];
			double temperature = bmp390caldata[1];
			
			if(Math.round(pressure * 10000d) / 10000d != 100912.8176) {	//4d.p. accuracy
	    		assert(false);
			}
			
			if(Math.round(temperature * 10000d) / 10000d != 23.2659) {		//4d.p. accuracy
	    		assert(false);
			}
    	} catch(IndexOutOfBoundsException e){
    		throw(e);
    	}
	}
	
	@Test
	public void test003_ConnectandTestCalibParamRead() {
		
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if (!mDevice.isConnected()) {
        		assert(false);
        	}	
    		
    		
			byte[] deviceCalBytes = mDevice.calibByteDumpGenerate();
			System.out.println("deviceCalBytes : " +  UtilShimmer.bytesToHexStringWithSpacesFormatted(deviceCalBytes));
			mDevice.calibByteDumpParse(deviceCalBytes, CALIB_READ_SOURCE.FILE_DUMP);
			
			Object returnValue = mDevice.getConfigValueUsingConfigLabel(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_ALL);
			assertNotNull(returnValue);
	
			TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfKinematicSensorCalibrationAll = (TreeMap<Integer, TreeMap<Integer, CalibDetails>>)returnValue;
			for(Integer sensorId:mapOfKinematicSensorCalibrationAll.keySet()){
				TreeMap<Integer, CalibDetails> route1CalParamMapPerSensor = mapOfKinematicSensorCalibrationAll.get(sensorId);
				
				SensorDetails sensorDetails = mDevice.getSensorDetails(sensorId);
				assertNotNull(sensorDetails);
				
				System.out.println(sensorDetails.mSensorDetailsRef.mGuiFriendlyLabel);
				TreeMap<Integer, CalibDetails> mapOfKinematicCalibPerRange = mapOfKinematicSensorCalibrationAll.get(sensorId);
				for(CalibDetails calibDetails:mapOfKinematicCalibPerRange.values()){
					if(calibDetails instanceof CalibDetailsKinematic){
						System.out.println(((CalibDetailsKinematic)calibDetails).getDebugString());
					}
				}
				System.out.println();
				
				Object returnValuePerSensor = mDevice.getConfigValueUsingConfigLabel(sensorId, AbstractSensor.GuiLabelConfigCommon.CALIBRATION_PER_SENSOR);
				assertNotNull(returnValuePerSensor);
				TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfKinematicSensorCalibration = (TreeMap<Integer, TreeMap<Integer, CalibDetails>>)returnValue;
				TreeMap<Integer, CalibDetails> route2CalParamMapPerSensor = mapOfKinematicSensorCalibration.get(sensorId);
				assertNotNull(route2CalParamMapPerSensor);
	
				for(Integer rangeValue:route1CalParamMapPerSensor.keySet()){
					CalibDetails route1CalibDetails = route1CalParamMapPerSensor.get(rangeValue); 
					CalibDetails route2CalibDetails = route2CalParamMapPerSensor.get(rangeValue); 
					compareTwoCalibDetails(route1CalibDetails, route2CalibDetails);
				}
			}
	}
	
	@Test
	public void test004_ConnectandTestDefaultLNAccelAndGyroCalibParam() {
		
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if (!mDevice.isConnected()) {
        		assert(false);
        	}	
    		//SHIMMER_LSM6DSV Accel LN and Gyro
			mDevice.setDefaultCalibrationShimmer3StandardImus();			
			
			double[][] lnAccelOffset = mDevice.getOffsetVectorMatrixAccel();
    		assertTrue(Arrays.deepEquals(lnAccelOffset, SensorLSM6DSV.OffsetVectorLowNoiseAccelShimmer3r));
    		double[][] lnAccelAlignment = mDevice.getAlignmentMatrixAccel();
    		assertTrue(Arrays.deepEquals(lnAccelAlignment, SensorLSM6DSV.AlignmentMatrixLowNoiseAccelShimmer3r)); 		
			double[][] lnAccelSensitivity0 = mDevice.getSensitivityMatrixAccel();
    		assertTrue(Arrays.deepEquals(lnAccelSensitivity0, SensorLSM6DSV.SensitivityMatrixLowNoiseAccel2gShimmer3r));    		
    		mDevice.writeGyroRange(0);
    		
    		double[][] gyroOffset = mDevice.getOffsetVectorMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroOffset, SensorLSM6DSV.OffsetVectorGyroShimmer3r));
    		double[][] gyroAlignment = mDevice.getAlignmentMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroAlignment, SensorLSM6DSV.AlignmentMatrixGyroShimmer3r));
    		
    		double[][] gyroSensitivity0 = mDevice.getSensitivityMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroSensitivity0, SensorLSM6DSV.SensitivityMatrixGyro125dpsShimmer3r));
    		mDevice.writeGyroRange(1);
    		double[][] gyroSensitivity1 = mDevice.getSensitivityMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroSensitivity1, SensorLSM6DSV.SensitivityMatrixGyro250dpsShimmer3r));
    		mDevice.writeGyroRange(2);
    		double[][] gyroSensitivity2 = mDevice.getSensitivityMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroSensitivity2, SensorLSM6DSV.SensitivityMatrixGyro500dpsShimmer3r));
    		mDevice.writeGyroRange(3);
    		double[][] gyroSensitivity3 = mDevice.getSensitivityMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroSensitivity3, SensorLSM6DSV.SensitivityMatrixGyro1000dpsShimmer3r));
    		mDevice.writeGyroRange(4);
    		double[][] gyroSensitivity4 = mDevice.getSensitivityMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroSensitivity4, SensorLSM6DSV.SensitivityMatrixGyro2000dpsShimmer3r));
    		mDevice.writeGyroRange(5);
    		double[][] gyroSensitivity5 = mDevice.getSensitivityMatrixGyro();
    		assertTrue(Arrays.deepEquals(gyroSensitivity5, SensorLSM6DSV.SensitivityMatrixGyro4000dpsShimmer3r));

	}
	
	@Test
	public void test005_ConnectandTestDefaultWRAccelCalibParam() {
		
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if (!mDevice.isConnected()) {
        		assert(false);
        	}	
    		//SHIMMER_LIS2DW12_ACCEL_WR
    		if(!mDevice.isUsingDefaultWRAccelParam()) {
    			mDevice.setDefaultCalibrationShimmer3StandardImus();
    		}
    		
			double[][] wrAccelOffset = mDevice.getOffsetVectorMatrixWRAccel();
    		assertTrue(Arrays.deepEquals(wrAccelOffset, SensorLIS2DW12.DefaultOffsetVectorWideRangeAccelShimmer3R));
    		double[][] wrAccelAlignment = mDevice.getAlignmentMatrixWRAccel();
    		assertTrue(Arrays.deepEquals(wrAccelAlignment, SensorLIS2DW12.DefaultAlignmentMatrixWideRangeAccelShimmer3R));
    		
    		mDevice.writeAccelRange(0);
    		double[][] accelSensitivity0 = mDevice.getSensitivityMatrixWRAccel();
    		assertTrue(Arrays.deepEquals(accelSensitivity0, SensorLIS2DW12.DefaultSensitivityMatrixWideRangeAccel2gShimmer3R));
    		mDevice.writeAccelRange(1);    		
    		double[][] accelSensitivity1 = mDevice.getSensitivityMatrixWRAccel();
    		assertTrue(Arrays.deepEquals(accelSensitivity1, SensorLIS2DW12.DefaultSensitivityMatrixWideRangeAccel4gShimmer3R));
    		mDevice.writeAccelRange(2);    		
    		double[][] accelSensitivity2 = mDevice.getSensitivityMatrixWRAccel();
    		assertTrue(Arrays.deepEquals(accelSensitivity2, SensorLIS2DW12.DefaultSensitivityMatrixWideRangeAccel8gShimmer3R));		
    		mDevice.writeAccelRange(3);    		
    		double[][] accelSensitivity3 = mDevice.getSensitivityMatrixWRAccel();
    		assertTrue(Arrays.deepEquals(accelSensitivity3, SensorLIS2DW12.DefaultSensitivityMatrixWideRangeAccel16gShimmer3R));
    		}
	
	@Test
	public void test006_ConnectandTestDefaultMagCalibParam() {
		
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if (!mDevice.isConnected()) {
        		assert(false);
        	}	
    		//SHIMMER_LIS2MDL_MAG
    		
    		mDevice.setDefaultCalibrationShimmer3StandardImus();
			
			double[][] magOffset = mDevice.getOffsetVectorMatrixMag();
    		assertTrue(Arrays.deepEquals(magOffset, SensorLIS2MDL.DefaultOffsetVectorMagShimmer3r));
    		double[][] magAlignment = mDevice.getAlignmentMatrixMag();
    		assertTrue(Arrays.deepEquals(magAlignment, SensorLIS2MDL.DefaultAlignmentMatrixMagShimmer3r)); 
    		double[][] magSensitivity = mDevice.getSensitivityMatrixMag();
    		assertTrue(Arrays.deepEquals(magSensitivity, SensorLIS2MDL.DefaultSensitivityMatrixMagShimmer3r));
    		
    		
	}
	@Test
	public void test007_ConnectandTestDefaultHighGAccelCalibParam() {
		
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if (!mDevice.isConnected()) {
        		assert(false);
        	}	
    		//SHIMMER_ADXL371_ACCEL HIGHG
    		
    		mDevice.setDefaultCalibrationShimmer3StandardImus();
			
			double[][] highGAccelOffset = mDevice.getOffsetVectorMatrixHighGAccel();
    		assertTrue(Arrays.deepEquals(highGAccelOffset, SensorADXL371.DefaultOffsetVectorHighGAccelShimmer3R));
    		double[][] highGAccelAlignment = mDevice.getAlignmentMatrixHighGAccel();
    		assertTrue(Arrays.deepEquals(highGAccelAlignment, SensorADXL371.DefaultAlignmentMatrixHighGAccelShimmer3R));  		
    		double[][] highGAccelSensitivity = mDevice.getSensitivityMatrixHighGAccel();
    		assertTrue(Arrays.deepEquals(highGAccelSensitivity, SensorADXL371.DefaultSensitivityMatrixHighGAccelShimmer3R));
	}
	
	@Test
	public void test008_ConnectandTestDefaultAltMagCalibParam() {
		
		mWaitTask = new TaskCompletionSource<Boolean>();
    	mDevice.connect("","");
    	
    		mWaitTask = new TaskCompletionSource<>();
    		try {
				mWaitTask.getTask().waitForCompletion(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		if (!mDevice.isConnected()) {
        		assert(false);
        	}	
    		
    		//SHIMMER_LIS3MDL_MAG
			mDevice.setDefaultCalibrationShimmer3StandardImus();
			
			double[][] altMagOffset = mDevice.getOffsetVectorMatrixAltMag();
    		assertTrue(Arrays.deepEquals(altMagOffset, SensorLIS3MDL.DefaultOffsetVectorAltMagShimmer3r));
    		double[][] altMagAlignment = mDevice.getAlignmentMatrixAltMag();
    		assertTrue(Arrays.deepEquals(altMagAlignment, SensorLIS3MDL.DefaultAlignmentMatrixAltMagShimmer3r));
    		
    		mDevice.setAltMagRange(0);
    		double[][] altMagSensitivity0 = mDevice.getSensitivityMatrixAltMag();
    		assertTrue(Arrays.deepEquals(altMagSensitivity0, SensorLIS3MDL.DefaultSensitivityMatrixAltMag4GaShimmer3r));
    		mDevice.setAltMagRange(1);
    		double[][] altMagSensitivity1 = mDevice.getSensitivityMatrixAltMag();
    		assertTrue(Arrays.deepEquals(altMagSensitivity1, SensorLIS3MDL.DefaultSensitivityMatrixAltMag8GaShimmer3r));
    		mDevice.setAltMagRange(2);
    		double[][] altMagSensitivity2 = mDevice.getSensitivityMatrixAltMag();
    		assertTrue(Arrays.deepEquals(altMagSensitivity2, SensorLIS3MDL.DefaultSensitivityMatrixAltMag12GaShimmer3r));
    		mDevice.setAltMagRange(3);
    		double[][] altMagSensitivity3 = mDevice.getSensitivityMatrixAltMag();
    		assertTrue(Arrays.deepEquals(altMagSensitivity3, SensorLIS3MDL.DefaultSensitivityMatrixAltMag16GaShimmer3r));
	}
	
	public static void compareTwoCalibDetails(CalibDetails calibDetails1, CalibDetails calibDetails2) {
		assertTrue(calibDetails1 instanceof CalibDetailsKinematic && calibDetails2 instanceof CalibDetailsKinematic);
		
		CalibDetailsKinematic calibDetails1Cast = (CalibDetailsKinematic)calibDetails1;
		CalibDetailsKinematic calibDetails2Cast = (CalibDetailsKinematic)calibDetails2;
		
		System.out.println("First. Is set?\t" + calibDetails1Cast.isCurrentValuesSet());
		System.out.println(calibDetails1Cast.getDebugString());
		System.out.println("Second. Is set?\t" + calibDetails2Cast.isCurrentValuesSet());
		System.out.println(calibDetails2Cast.getDebugString());
		
		assertTrue(Arrays.deepEquals(calibDetails1Cast.getDefaultAlignmentMatrix(), calibDetails2Cast.getDefaultAlignmentMatrix()));
		assertTrue(Arrays.deepEquals(calibDetails1Cast.getValidAlignmentMatrix(), calibDetails2Cast.getValidAlignmentMatrix()));
		assertTrue(Arrays.deepEquals(calibDetails1Cast.getDefaultSensitivityMatrix(), calibDetails2Cast.getDefaultSensitivityMatrix()));
		assertTrue(Arrays.deepEquals(calibDetails1Cast.getValidSensitivityMatrix(), calibDetails2Cast.getValidSensitivityMatrix()));
		assertTrue(Arrays.deepEquals(calibDetails1Cast.getDefaultOffsetVector(), calibDetails2Cast.getDefaultOffsetVector()));
 		assertTrue(Arrays.deepEquals(calibDetails1Cast.getValidOffsetVector(), calibDetails2Cast.getValidOffsetVector()));
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
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
		}
		
		}
	}
   
}
