package com.shimmerresearch.sensors;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import com.shimmerresearch.bluetooth.BtCommandDetails;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3.CompatibilityInfoForMaps;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;


/**
 * @author Ronan McCormack
 * @author Mark Nolan
 *
 */
public class SensorBattVoltage extends AbstractSensor{
	
	/** *  */
	private static final long serialVersionUID = -2835431738933986391L;
	
	//--------- Sensor specific variables start --------------
//	protected DescriptiveStatistics mVSenseBattMA= new DescriptiveStatistics(1024);
	
	/** GQ BLE */
	protected int mSamplingDividerVBatt = 0;

	private ShimmerBattStatusDetails mShimmerBattStatusDetails = new ShimmerBattStatusDetails();
	
	public class GuiLabelConfig{
		public static final String SAMPLING_RATE_DIVIDER_VBATT = "VBATT Divider";
		}
	
	public class GuiLabelSensors{
		public static final String BATTERY = "Battery Voltage";
	}
	
	public class LABEL_SENSOR_TILE{
		public static final String BATTERY_MONITORING = GuiLabelSensors.BATTERY;
	}
	
	public static class DatabaseChannelHandles{
		public static final String BATTERY = "F5437a_Int_A2_Battery";
	}

	public static class ObjectClusterSensorName{
		public static final String BATTERY = "Battery";
		public static final String BATT_PERCENTAGE = "Batt_Percentage";
	}
	
	// The battery voltage is divided by half before entering the Microcontroller's ADC.  The value is not quite equal to 2 due to the components used in the circuit.
	public static final double BATTERY_VOLTAGE_DIVIDER_RATIO = 1.988;

	// Second-generation Verisense hardware (SR61-5/6, SR68-9/10) battery-sense divider.
	// Matches the firmware's conversion in hal_asm_battery.c (measureBatteryVoltage).
	public static final double BATTERY_VOLTAGE_DIVIDER_RATIO_GEN2 = 2.469;
	
	//--------- Sensor specific variables end --------------
	
	//--------- Bluetooth commands start ------------------
	
	public static final byte VBATT_RESPONSE                         = (byte) 0x94;
	public static final byte GET_VBATT_COMMAND                      = (byte) 0x95;
	
    public static final Map<Byte, BtCommandDetails> mBtGetCommandMap;
    static {
        Map<Byte, BtCommandDetails> aMap = new LinkedHashMap<Byte, BtCommandDetails>();
        aMap.put(GET_VBATT_COMMAND, new BtCommandDetails(GET_VBATT_COMMAND, "GET_VBATT_COMMAND", VBATT_RESPONSE));
        mBtGetCommandMap = Collections.unmodifiableMap(aMap);
    }
	
//    public static final Map<Byte, BtCommandDetails> mBtSetCommandMap;
//    static {
//       mBtSetCommandMap = Collections.unmodifiableMap(aMap);
//    }
//	
    
    
    
  //--------- Configuration options start -------------- 
  //--------- Configuration options end --------------
	
	
  	//--------- Sensor info start --------------
  	public static final SensorDetailsRef sensorBattVoltageRef = new SensorDetailsRef(
  			0x20<<8, 
  			0x20<<8, 
  			GuiLabelSensors.BATTERY,
  			null,
  			null,
  			Arrays.asList(ObjectClusterSensorName.BATTERY,
  					ObjectClusterSensorName.BATT_PERCENTAGE));
    
  	public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
    static {
        Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();
        aMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_VBATT, SensorBattVoltage.sensorBattVoltageRef);  
		mSensorMapRef = Collections.unmodifiableMap(aMap);
    }
  	
    public static final SensorGroupingDetails sensorGroupBattVoltage = new SensorGroupingDetails(
			LABEL_SENSOR_TILE.BATTERY_MONITORING,
			Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_VBATT),
//			CompatibilityInfoForMaps.listOfCompatibleVersionInfoAnyExpBoardStandardFW);
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoBattVoltage);
  	
	//--------- Sensor info end --------------
	
	
	//--------- Channel info start --------------
  	
  	 public static final ChannelDetails channelBattPercentage = new ChannelDetails(
 			ObjectClusterSensorName.BATT_PERCENTAGE,
 			"Battery Percent",
 			ObjectClusterSensorName.BATT_PERCENTAGE,
 			CHANNEL_UNITS.PERCENT,
 			Arrays.asList(CHANNEL_TYPE.CAL),
 			true,
 			false);
  	
  	 public static final ChannelDetails channelBattVolt = new ChannelDetails(
			ObjectClusterSensorName.BATTERY,
			ObjectClusterSensorName.BATTERY,
			DatabaseChannelHandles.BATTERY,
			CHANNEL_DATA_TYPE.UINT12, 2, CHANNEL_DATA_ENDIAN.LSB,
			CHANNEL_UNITS.MILLIVOLTS,
			Arrays.asList(CHANNEL_TYPE.CAL, CHANNEL_TYPE.UNCAL),
			true,
			true);
  	 
  	 
  	public static final Map<String, ChannelDetails> mChannelMapRef;
    static {
        Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
        aMap.put(ObjectClusterSensorName.BATT_PERCENTAGE, channelBattPercentage);
        aMap.put(ObjectClusterSensorName.BATTERY, channelBattVolt);

        mChannelMapRef = Collections.unmodifiableMap(aMap);
    }
  	
  	// -----------Channel info end ------
    
    //--------- Constructors for this class start --------------  	
	public SensorBattVoltage(ShimmerDevice shimmerDevice) {
		super(SENSORS.Battery, shimmerDevice);
		mShimmerBattStatusDetails = shimmerDevice.mShimmerBattStatusDetails;
		initialise();
	}

	public SensorBattVoltage(ShimmerVerObject svo) {
		super(SENSORS.Battery, svo);
		initialise();
	}
	 //--------- Constructors for this class end --------------
	
	
	//--------- Abstract methods implemented start --------------
	
	@Override
	public void generateSensorMap() {
		super.createLocalSensorMapWithCustomParser(mSensorMapRef, mChannelMapRef);
	}

	@Override
	public void generateConfigOptionsMap() {
		//Not in this class
	}

	@Override
	public void generateSensorGroupMapping() {
		mSensorGroupingMap = new LinkedHashMap<Integer, SensorGroupingDetails>();
		if(mShimmerVerObject.isShimmerGen3() || mShimmerVerObject.isShimmerGen4() || mShimmerVerObject.isSweatchDevice()){
			mSensorGroupingMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BATTERY_MONITORING.ordinal(), sensorGroupBattVoltage);
		}
		super.updateSensorGroupingMap();
	}

	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		// Not in this class
	}

	@Override
	public ObjectCluster processDataCustom(SensorDetails sensorDetails, byte[] rawData, COMMUNICATION_TYPE commType, ObjectCluster objectCluster, boolean isTimeSyncEnabled, double pcTimestampMs) {
		
		sensorDetails.processDataCommon(rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestampMs);

//		if(mEnableCalibration){
			for(ChannelDetails channelDetails:sensorDetails.mListOfChannels){
				if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.BATTERY)){
//					objectCluster = SensorADC.processMspAdcChannel(sensorDetails, rawData, commType, objectCluster, isTimeSyncEnabled, pcTimestamp);
				
					double unCalData = ((FormatCluster)ObjectCluster.returnFormatCluster(objectCluster.getCollectionOfFormatClusters(channelDetails.mObjectClusterName), channelDetails.mChannelFormatDerivedFromShimmerDataPacket.toString())).mData;
					double offset = 0; double vRefP = 3; double gain = 1; 
					double calData = SensorADC.calibrateU12AdcValueToMillivolts(unCalData, offset, vRefP, gain);
					calData *= BATTERY_VOLTAGE_DIVIDER_RATIO;
					objectCluster.addCalData(channelDetails, calData, objectCluster.getIndexKeeper()-1);
					
					getShimmerBattStatusDetails().calculateBattPercentage(calData/1000);
				}
				else if (channelDetails.mObjectClusterName.equals(ObjectClusterSensorName.BATT_PERCENTAGE)){
					double estimatedChargePercentage = (double)getShimmerBattStatusDetails().getEstimatedChargePercentage();
					if(!Double.isNaN(estimatedChargePercentage) && !Double.isInfinite(estimatedChargePercentage)){
						objectCluster.addCalData(channelDetails, estimatedChargePercentage);
						objectCluster.incrementIndexKeeper();
					}
				}
			}
//		}

//		System.err.println(
//				getShimmerBattStatusDetails().getBattVoltage()
//				+ "\t" + getShimmerBattStatusDetails().getEstimatedChargePercentage() 
//				+ "\t" + getShimmerBattStatusDetails().getEstimatedChargePercentageParsed());

		return objectCluster;
	}

	@Override
	public void configBytesGenerate(ShimmerDevice shimmerDevice, byte[] mInfoMemBytes, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void configBytesParse(ShimmerDevice shimmerDevice, byte[] mInfoMemBytes, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object setConfigValueUsingConfigLabel(Integer sensorId, String configLabel, Object valueToSet) {
		Object returnValue = null;
//		if (componentName.equals(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT)){
//		    setSamplingDividerVBatt((int)valueToSet);
//		}
		return returnValue;
	}

	@Override
	public Object getConfigValueUsingConfigLabel(Integer sensorId, String configLabel) {
		Object returnValue = null;
//		if(componentName.equals(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT)){
//			returnValue = getSamplingDividerVBatt();
//		}	
		return returnValue;
	}

	@Override
	public void setSensorSamplingRate(double samplingRateHz) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean setDefaultConfigForSensor(int sensorId, boolean isSensorEnabled) {
		// //Not in this class
		return false;
	}

	@Override
	public boolean checkConfigOptionValues(String stringKey) {
		//Not in this class
		return false;
	}

	@Override
	public Object getSettings(String componentName, COMMUNICATION_TYPE commType) {
		// Not in this class
		return null;
	}

	@Override
	public ActionSetting setSettings(String componentName, Object valueToSet, COMMUNICATION_TYPE commType) {
		// Not in this class
		return null;
	}

	@Override
	public LinkedHashMap<String, Object> generateConfigMap() {
		//Not in this class
		return null;
	}
	
	@Override
	public void parseConfigMap(LinkedHashMap<String, Object> mapOfConfigPerShimmer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean processResponse(int responseCommand, Object parsedResponse, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		return false;
	}
	
	
	/** ShimmerGQBle
	 * @param mSamplingDividerVBatt the mSamplingDividerVBatt to set
	 */
	public void setSamplingDividerVBatt(int mSamplingDividerVBatt) {
		this.mSamplingDividerVBatt = mSamplingDividerVBatt;
	}
	
	
	/**
	 * @return the mSamplingDividerVBatt
	 */
	public int getSamplingDividerVBatt() {
		return mSamplingDividerVBatt;
	}

	protected ShimmerBattStatusDetails getShimmerBattStatusDetails(){
		if(mShimmerDevice!=null){
			return mShimmerDevice.mShimmerBattStatusDetails;
		}
		else{
			return mShimmerBattStatusDetails;
		}
	}

}
