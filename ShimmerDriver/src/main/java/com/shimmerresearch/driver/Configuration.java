/*Rev 2.6
 * 
 * 
 *  Copyright (c) 2010, Shimmer Research, Ltd.
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
 * @author Jong Chern Lim, Ruaidhri Molloy, Mark Nolan
 * @date   May, 2014
 * 
 * The purpose of this code is to maintain the configurations of BTSTREAM
 * 
 * 
 * Changes since 2.5 (RM first revision)
 * - Addition of Strain Gauge for Shimmer3
 * 
 * Changes since 2.2
 * - Changed list of compatible sensors to public
 * 
 */

package com.shimmerresearch.driver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.shimmerresearch.algorithms.orientation.OrientationModule6DOF;
import com.shimmerresearch.algorithms.orientation.OrientationModule9DOF;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.SensorADC;
import com.shimmerresearch.sensors.SensorBattVoltage;
import com.shimmerresearch.sensors.SensorBridgeAmp;
import com.shimmerresearch.sensors.SensorECGToHRFw;
import com.shimmerresearch.sensors.SensorEXG;
import com.shimmerresearch.sensors.SensorGSR;
import com.shimmerresearch.sensors.SensorPPG;
import com.shimmerresearch.sensors.SensorSTC3100;
import com.shimmerresearch.sensors.SensorSystemTimeStamp;
import com.shimmerresearch.sensors.SensorShimmerClock;
import com.shimmerresearch.sensors.ShimmerStreamingProperties;
import com.shimmerresearch.sensors.bmpX80.SensorBMP180;
import com.shimmerresearch.sensors.bmpX80.SensorBMP280;
import com.shimmerresearch.sensors.bmpX80.SensorBMP390;
import com.shimmerresearch.sensors.bmpX80.SensorBMP581;
import com.shimmerresearch.sensors.kionix.SensorKionixAccel;
import com.shimmerresearch.sensors.kionix.SensorKionixKXRB52042;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS3MDL;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS2MDL;
import com.shimmerresearch.sensors.lsm303.SensorLSM303;
import com.shimmerresearch.sensors.lsm303.SensorLSM303DLHC;
import com.shimmerresearch.sensors.mpu9x50.SensorMPU9150;
import com.shimmerresearch.sensors.mpu9x50.SensorMPU9X50;
import com.shimmerresearch.sensors.adxl371.SensorADXL371;
import com.shimmerresearch.sensors.lsm6dsv.SensorLSM6DSV;
import com.shimmerresearch.sensors.lis2dw12.SensorLIS2DW12;
import com.shimmerresearch.verisense.sensors.SensorLSM6DS3;
//import com.shimmerresearch.verisense.sensors.SensorLIS2DW12;
import com.shimmerresearch.verisense.sensors.SensorBattVoltageVerisense;
import com.shimmerresearch.verisense.sensors.SensorGSRVerisense;
import com.shimmerresearch.verisense.sensors.SensorMAX86916;

/**
 * The purpose of this code is to maintain the configurations constants for a
 * Shimmer Device
 * 
 * @author Jong Chern Lim,
 * @author Ruaidhri Molloy
 * @author Mark Nolan
 * @author Alex Saez
 *
 */
public class Configuration {
	
	public static final double ACCELERATION_DUE_TO_GRAVITY = 9.81;
	
	public static class CHANNEL_UNITS{
		//Sensors units
		public static final String NO_UNITS = "no_units";
		
		public static final String MILLISECONDS = "ms";
		public static final String SECONDS = "s";
		public static final String MINUTES = "min";
		public static final String MICROSECONDS = "us";

		public static final String CLOCK_UNIT = "Ticks";
		public static final String FREQUENCY = "Hz";

		//TODO: should be .SSS rather then .000? .000 might be required for viewing files in Microsoft Office
		public static final String DATE_FORMAT = "yyyy/mm/dd hh:mm:ss.000";
		
		public static final String METER = "m";
		public static final String METER_PER_SECOND = "m/s";
		public static final String METER_PER_SECOND_SQUARE = "m/(s^2)";
		public static final String METER_SQUARE = "m^2";
		public static final String MILLIMETER = "mm";
		public static final String MILLIMETER_PER_SECOND = "mm/s";
		public static final String MILLIMETER_PER_SECOND_SQUARE = "mm/(s^2)";
		public static final String MILLIMETER_SQUARE = "mm^2";
		public static final String MILLIMETER_SQUARE_PER_SECOND = "mm^2/s";
		
		public static final String DEGREES_PER_SECOND = "deg/s";
		public static final String LOCAL_FLUX = "local_flux";
		public static final String KOHMS = "kOhms";
		public static final String KOHMS_PER_SECOND = "kOhms/s";
		public static final String KOHMS_SECONDS = "kOhm.s";
		public static final String MILLIVOLTS = "mV";
		public static final String MILLIAMPS= "mA";
		public static final String NANOAMPS= "nA";
		public static final String MILLIAMP_HOUR = "mAh";
		public static final String BEATS_PER_MINUTE = "BPM";
		public static final String KPASCAL = "kPa";
		public static final String DEGREES_CELSIUS_SHORT = "\u00B0C";
		public static final String DEGREES_CELSIUS = "Degrees Celsius";
		public static final String DEGREES = "Degrees";
		public static final String U_TESLA = "uT";
		public static final String U_SIEMENS = "uS";
		public static final String GRAVITY = "g";
		public static final String RPM = "rpm";
		public static final String POWER = "dB";
		public static final String SCORE = "Score";

		public static final String ACCEL_CAL_UNIT = METER_PER_SECOND_SQUARE;
		public static final String GYRO_CAL_UNIT = DEGREES_PER_SECOND;
		public static final String MAG_CAL_UNIT = LOCAL_FLUX;

//		public static final String ACCEL_DEFAULT_CAL_UNIT = METER_PER_SECOND_SQUARE+"*";
//		public static final String GYRO_DEFAULT_CAL_UNIT = DEGREES_PER_SECOND+"*";
//		public static final String MAG_DEFAULT_CAL_UNIT = LOCAL_FLUX+"*";

		public static final String LOCAL = "local"; //used for axis-angle and madgewick quaternions    //XXX-RS-LSM-SensorClass?
		public static final String PERCENT = "%";
		
		public static final String PIXEL = "px";
		public static final String ASCII_CODE = "ASCII";
	}	
	
	public enum COMMUNICATION_TYPE{
		ALL,
		DOCK,
		BLUETOOTH,
		IEEE802154,
		SD, // Bin file
		HID,
		USB,
		CLASS, //this is to read the value of the class for clones etc. e.g. if you do a getsettings(accelRange,CLASS) and getsettings(accelRange,Bluetooth), the results are different. One returns the value in the object while the other should generate a read command to be sent to the shimmer device
	}
	
	public enum COMMUNICATION_ACTION{
		READ,
		WRITE
	}

	public static class Shimmer2{
		public class Channel{
			public static final int XAccel      = 0x00;
			public static final int YAccel      = 0x01;
			public static final int ZAccel      = 0x02;
			public static final int XGyro       = 0x03;
			public static final int YGyro       = 0x04;
			public static final int ZGyro       = 0x05;
			public static final int XMag        = 0x06;
			public static final int YMag        = 0x07;
			public static final int ZMag        = 0x08;
			public static final int EcgRaLl     = 0x09;
			public static final int EcgLaLl     = 0x0A;
			public static final int GsrRaw      = 0x0B;
			public static final int GsrRes      = 0x0C;
			public static final int Emg         = 0x0D;
			public static final int AnExA0      = 0x0E;
			public static final int AnExA7      = 0x0F;
			public static final int BridgeAmpHigh  = 0x10;
			public static final int BridgeAmpLow   = 0x11;
			public static final int HeartRate   = 0x12;
		}
		public class SensorBitmap{
			public static final int SENSOR_ACCEL				   = 0x80;
			public static final int SENSOR_GYRO				   	   = 0x40;
			public static final int SENSOR_MAG					   = 0x20;
			public static final int SENSOR_ECG					   = 0x10;
			public static final int SENSOR_EMG					   = 0x08;
			public static final int SENSOR_GSR					   = 0x04;
			public static final int SENSOR_EXP_BOARD_A7		       = 0x02;
			public static final int SENSOR_EXP_BOARD_A0		       = 0x01;
			public static final int SENSOR_BRIDGE_AMP			   = 0x8000;
			public static final int SENSOR_HEART				   = 0x4000;
		}
		
		//DATABASE NAMES
		//GUI AND EXPORT CHANNELS
		public static class ObjectClusterSensorName{
			public static String TIMESTAMP = "Timestamp";
			public static String REAL_TIME_CLOCK = "RealTime";
			public static String ACCEL_X = "Accel_X";
			public static String ACCEL_Y = "Accel_Y";
			public static String ACCEL_Z = "Accel_Z";
			public static String BATTERY = "Battery";
			public static String REG = "Reg";
			public static String EXT_EXP_A7 = "Ext_Exp_A7";
			public static String EXT_EXP_A6 = "Ext_Exp_A6";
			public static String EXT_EXP_A15 = "Ext_Exp_A15";
			public static String INT_EXP_A12 = "Int_Exp_A12";
			public static String INT_EXP_A13 = "Int_Exp_A13";
			public static String INT_EXP_A14 = "Int_Exp_A14";
			public static String BRIDGE_AMP_HIGH = "Bridge_Amp_High";
			public static String BRIDGE_AMP_LOW = "Bridge_Amp_Low";
			public static String GSR = "GSR";
			//public static String GSR_RAW = "GSR Raw";
			public static String GSR_RES = "GSR Res";
			public static String INT_EXP_A1 = "Int_Exp_A1";
			public static String EXP_BOARD_A0 = "Exp_Board_A0";
			public static String EXP_BOARD_A7 = "Exp_Board_A7";
			public static String GYRO_X = "Gyro_X";
			public static String GYRO_Y = "Gyro_Y";
			public static String GYRO_Z = "Gyro_Z";
			public static String MAG_X = "Mag_X";
			public static String MAG_Y = "Mag_Y";
			public static String MAG_Z = "Mag_Z";
			public static String EMG = "EMG";
			public static String ECG_RA_LL = "ECG_RA-LL";
			public static String ECG_LA_LL = "ECG_LA-LL";
			public static String ECG_TO_HR = "ECGtoHR";
			public static String QUAT_MADGE_6DOF_W = "Quat_Madge_6DOF_W";
			public static String QUAT_MADGE_6DOF_X = "Quat_Madge_6DOF_X";
			public static String QUAT_MADGE_6DOF_Y = "Quat_Madge_6DOF_Y";
			public static String QUAT_MADGE_6DOF_Z = "Quat_Madge_6DOF_Z";
			public static String QUAT_MADGE_9DOF_W = "Quat_Madge_9DOF_W";
			public static String QUAT_MADGE_9DOF_X = "Quat_Madge_9DOF_X";
			public static String QUAT_MADGE_9DOF_Y = "Quat_Madge_9DOF_Y";
			public static String QUAT_MADGE_9DOF_Z = "Quat_Madge_9DOF_Z";
			public static String EULER_6DOF_YAW = "Euler_6DOF_Yaw";
			public static String EULER_6DOF_PITCH = "Euler_6DOF_PITCH";
			public static String EULER_6DOF_ROLL = "Euler_6DOF_Roll";
			public static String EULER_9DOF_YAW = "Euler_9DOF_YAW";
			public static String EULER_9DOF_PITCH = "Euler_9DOF_PITCH";
			public static String EULER_9DOF_ROLL = "Euler_9DOF_Roll";
			public static String HEART_RATE = "Heart_Rate"; //for the heart rate strap now no longer sold
			public static String AXIS_ANGLE_6DOF_A = "Axis_Angle_A";
			public static String AXIS_ANGLE_6DOF_X = "Axis_Angle_X";
			public static String AXIS_ANGLE_6DOF_Y = "Axis_Angle_Y";
			public static String AXIS_ANGLE_6DOF_Z = "Axis_Angle_Z";
			public static String AXIS_ANGLE_9DOF_A = "Axis_Angle_A";
			public static String AXIS_ANGLE_9DOF_X = "Axis_Angle_X";
			public static String AXIS_ANGLE_9DOF_Y = "Axis_Angle_Y";
			public static String AXIS_ANGLE_9DOF_Z = "Axis_Angle_Z";
			public static String VOLT_REG = "VSenseReg";
		}
		
		public static final String[] ListofCompatibleSensors={"Accelerometer","Gyroscope","Magnetometer","Battery Voltage","ECG","EMG","GSR","Exp Board","Bridge Amplifier","Heart Rate"};
		public static final String[] ListofAccelRange={"+/- 1.5g","+/- 6g"};
		public static final String[] ListofMagRange={"+/- 0.8Ga","+/- 1.3Ga","+/- 1.9Ga","+/- 2.5Ga","+/- 4.0Ga","+/- 4.7Ga","+/- 5.6Ga","+/- 8.1Ga"};
		public static final String[] ListofGSRRange={"10kOhm to 56kOhm","56kOhm to 220kOhm","220kOhm to 680kOhm","680kOhm to 4.7MOhm","Auto Range"};

		public class SENSOR_ID{
			public static final int ACCEL = 0;
			public static final int GYRO = 1;
			public static final int MAG = 2;
			public static final int EMG = 3;
			public static final int ECG = 4;
			public static final int GSR = 5;
			public static final int EXP_BOARD_A7 = 6;
			public static final int EXP_BOARD_A0 = 7;
			public static final int EXP_BOARD = 8;
			public static final int BRIDGE_AMP = 9;
			public static final int HEART = 10;
			public static final int BATT = 11;
			public static final int EXT_ADC_A15 = 12;
			public static final int INT_ADC_A1 = 13;
			public static final int INT_ADC_A12 = 14;
			public static final int INT_ADC_A13 = 15;
			public static final int INT_ADC_A14 = 16;
		}
		
	    public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
	    static {
	        Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer,SensorDetailsRef>();
		
	        aMap.put(Configuration.Shimmer2.SENSOR_ID.ACCEL, new SensorDetailsRef(0x80, 0, "Accelerometer"));
	        aMap.put(Configuration.Shimmer2.SENSOR_ID.GYRO, new SensorDetailsRef(0x40, 0, "Gyroscope"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.MAG, new SensorDetailsRef(0x20, 0, "Magnetometer"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.EMG, new SensorDetailsRef(0x08, 0, "EMG"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.ECG, new SensorDetailsRef(0x10, 0, "ECG"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.GSR, new SensorDetailsRef(0x04, 0, "GSR"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A7, new SensorDetailsRef(0x02, 0, "Exp Board A7"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A0, new SensorDetailsRef(0x01, 0, "Exp Board A0"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.EXP_BOARD, new SensorDetailsRef(0x02|0x01, 0, "Exp Board"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP, new SensorDetailsRef(0x8000, 0, "Bridge Amplifier"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.HEART, new SensorDetailsRef(0x4000, 0, "Heart Rate"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.BATT, new SensorDetailsRef(0x2000, 0, "Battery Voltage"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.EXT_ADC_A15, new SensorDetailsRef(0x0800, 0, "External ADC A15"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.INT_ADC_A1, new SensorDetailsRef(0x0400, 0, "Internal ADC A1"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.INT_ADC_A12, new SensorDetailsRef(0x0200, 0, "Internal ADC A12"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.INT_ADC_A13, new SensorDetailsRef(0x0100, 0, "Internal ADC A13"));
			aMap.put(Configuration.Shimmer2.SENSOR_ID.INT_ADC_A14, new SensorDetailsRef(0x800000, 0, "Internal ADC A14"));
			
			// Conflicting Channels
			aMap.get(Configuration.Shimmer2.SENSOR_ID.GYRO).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.ECG,
				Configuration.Shimmer2.SENSOR_ID.EMG,
				Configuration.Shimmer2.SENSOR_ID.GSR,
				Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.MAG).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.ECG,
				Configuration.Shimmer2.SENSOR_ID.EMG,
				Configuration.Shimmer2.SENSOR_ID.GSR,
				Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.EMG).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.GSR,
				Configuration.Shimmer2.SENSOR_ID.ECG,
				Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP,
				Configuration.Shimmer2.SENSOR_ID.GYRO,
				Configuration.Shimmer2.SENSOR_ID.MAG);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.ECG).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.GSR,
				Configuration.Shimmer2.SENSOR_ID.EMG,
				Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP,
				Configuration.Shimmer2.SENSOR_ID.GYRO,
				Configuration.Shimmer2.SENSOR_ID.MAG);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.GSR).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.ECG,
				Configuration.Shimmer2.SENSOR_ID.EMG,
				Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP,
				Configuration.Shimmer2.SENSOR_ID.GYRO,
				Configuration.Shimmer2.SENSOR_ID.MAG);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A7).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.HEART,
				Configuration.Shimmer2.SENSOR_ID.BATT);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A0).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.HEART,
				Configuration.Shimmer2.SENSOR_ID.BATT);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.BRIDGE_AMP).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.ECG,
				Configuration.Shimmer2.SENSOR_ID.EMG,
				Configuration.Shimmer2.SENSOR_ID.GSR,
				Configuration.Shimmer2.SENSOR_ID.GYRO,
				Configuration.Shimmer2.SENSOR_ID.MAG);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.HEART).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A0,
				Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A7);
			aMap.get(Configuration.Shimmer2.SENSOR_ID.BATT).mListOfSensorIdsConflicting = Arrays.asList(
				Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A0,
				Configuration.Shimmer2.SENSOR_ID.EXP_BOARD_A7);
			mSensorMapRef = Collections.unmodifiableMap(aMap);
	    }
	}

	@Deprecated
	public static void setTooLegacyObjectClusterSensorNames(){

		Shimmer3.ObjectClusterSensorName.TIMESTAMP = "Timestamp";
		Shimmer3.ObjectClusterSensorName.REAL_TIME_CLOCK = "RealTime";
		Shimmer3.ObjectClusterSensorName.ACCEL_LN_X = "Low Noise Accelerometer X";
		Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y = "Low Noise Accelerometer Y";
		Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z = "Low Noise Accelerometer Z";
		Shimmer3.ObjectClusterSensorName.BATTERY = "VSenseBatt";
		Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A7 = "ExpBoard A7";
		Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A6 = "ExpBoard A6";
		Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A15 = "External ADC A15";
		Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A12 = "Internal ADC A12";
		Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A13 = "Internal ADC A13";
		Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A14 = "Internal ADC A14";
		Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_HIGH = "Bridge Amplifier High";
		Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_LOW = "Bridge Amplifier Low";
		Shimmer3.ObjectClusterSensorName.GSR_RESISTANCE = "GSR";
		Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A1 = "Internal ADC A1";
		Shimmer3.ObjectClusterSensorName.RESISTANCE_AMP = "Resistance Amp";
		Shimmer3.ObjectClusterSensorName.GYRO_X = "Gyroscope X";
		Shimmer3.ObjectClusterSensorName.GYRO_Y = "Gyroscope Y";
		Shimmer3.ObjectClusterSensorName.GYRO_Z = "Gyroscope Z";
		Shimmer3.ObjectClusterSensorName.ACCEL_WR_X = "Wide Range Accelerometer X";
		Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y = "Wide Range Accelerometer Y";
		Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z= "Wide Range Accelerometer Z";
		Shimmer3.ObjectClusterSensorName.MAG_X = "Magnetometer X";
		Shimmer3.ObjectClusterSensorName.MAG_Y = "Magnetometer Y";
		Shimmer3.ObjectClusterSensorName.MAG_Z = "Magnetometer Z";
		Shimmer3.ObjectClusterSensorName.ACCEL_MPU_X = "Accel_MPU_X";
		Shimmer3.ObjectClusterSensorName.ACCEL_MPU_Y = "Accel_MPU_Y";
		Shimmer3.ObjectClusterSensorName.ACCEL_MPU_Z = "Accel_MPU_Z";
		Shimmer3.ObjectClusterSensorName.MAG_MPU_X = "Mag_MPU_X";
		Shimmer3.ObjectClusterSensorName.MAG_MPU_Y = "Mag_MPU_Y";
		Shimmer3.ObjectClusterSensorName.MAG_MPU_Z = "Mag_MPU_Z";
		Shimmer3.ObjectClusterSensorName.TEMPERATURE_BMP180 = "Temperature";
		Shimmer3.ObjectClusterSensorName.PRESSURE_BMP180 = "Pressure";
		Shimmer3.ObjectClusterSensorName.EMG_CH1_24BIT = "EMG CH1";
		Shimmer3.ObjectClusterSensorName.EMG_CH2_24BIT = "EMG CH2";
		Shimmer3.ObjectClusterSensorName.EMG_CH1_16BIT = "EMG CH1";
		Shimmer3.ObjectClusterSensorName.EMG_CH2_16BIT = "EMG CH2";
		Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT = "ECG LL-RA";
		Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT = "ECG LA-RA";
		Shimmer3.ObjectClusterSensorName.ECG_LL_LA_24BIT = "ECG LL-LA";
		Shimmer3.ObjectClusterSensorName.ECG_LL_LA_16BIT = "ECG LL-LA";
		Shimmer3.ObjectClusterSensorName.ECG_LL_RA_16BIT = "ECG LL-RA";
		Shimmer3.ObjectClusterSensorName.ECG_LA_RA_16BIT = "ECG LA-RA";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_24BIT = "EXG1 CH1";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_24BIT = "EXG1 CH1";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_24BIT = "EXG1 CH2";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_24BIT = "EXG1 CH2";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_16BIT = "EXG1 CH1 16BIT";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_16BIT = "EXG1 CH1 16BIT";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_16BIT = "EXG1 CH2 16BIT";
		Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_16BIT = "EXG1 CH2 16BIT";
		Shimmer3.ObjectClusterSensorName.EXG1_STATUS = "EXG1 Status";
		Shimmer3.ObjectClusterSensorName.ECG_RESP_24BIT = "ECG RESP";
		Shimmer3.ObjectClusterSensorName.ECG_VX_RL_24BIT = "ECG Vx-RL";
		Shimmer3.ObjectClusterSensorName.ECG_RESP_16BIT = "ECG RESP";
		Shimmer3.ObjectClusterSensorName.ECG_VX_RL_16BIT = "ECG Vx-RL";
		Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT = "ExG1 CH1";
		Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT = "ExG1 CH2";
		Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT = "ExG1 CH1 16Bit";
		Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT = "ExG1 CH2 16Bit";
		Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT = "ExG2 CH1";
		Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT = "ExG2 CH2";
		Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT = "ExG2 CH1 16Bit";
		Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT = "ExG2 CH2 16Bit";
		Shimmer3.ObjectClusterSensorName.EXG2_STATUS = "EXG2 Status";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_W = "Quat_MPL_6DOF_W";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_X = "Quat_MPL_6DOF_X";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Y = "Quat_MPL_6DOF_Y";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Z = "Quat_MPL_6DOF_Z";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_9DOF_W = "Quat_MPL_9DOF_W";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_9DOF_X = "Quat_MPL_9DOF_X";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_9DOF_Y = "Quat_MPL_9DOF_Y";
		Shimmer3.ObjectClusterSensorName.QUAT_MPL_9DOF_Z = "Quat_MPL_9DOF_Z";
		Shimmer3.ObjectClusterSensorName.EULER_MPL_6DOF_X = "Euler_MPL_6DOF_X";
		Shimmer3.ObjectClusterSensorName.EULER_MPL_6DOF_Y = "Euler_MPL_6DOF_Y";
		Shimmer3.ObjectClusterSensorName.EULER_MPL_6DOF_Z = "Euler_MPL_6DOF_Z";
		Shimmer3.ObjectClusterSensorName.EULER_MPL_9DOF_X = "Euler_MPL_9DOF_X";
		Shimmer3.ObjectClusterSensorName.EULER_MPL_9DOF_Y = "Euler_MPL_9DOF_Y";
		Shimmer3.ObjectClusterSensorName.EULER_MPL_9DOF_Z = "Euler_MPL_9DOF_Z";
		Shimmer3.ObjectClusterSensorName.MPL_HEADING = "MPL_heading";
		Shimmer3.ObjectClusterSensorName.MPL_TEMPERATURE = "MPL_Temperature";
		Shimmer3.ObjectClusterSensorName.MPL_PEDOM_CNT = "MPL_Pedom_cnt";
		Shimmer3.ObjectClusterSensorName.MPL_PEDOM_TIME = "MPL_Pedom_Time";
		Shimmer3.ObjectClusterSensorName.TAPDIRANDTAPCNT = "TapDirAndTapCnt";
		Shimmer3.ObjectClusterSensorName.MOTIONANDORIENT = "MotionAndOrient";
		Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_X = "Gyro_MPU_MPL_X";
		Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Y = "Gyro_MPU_MPL_Y";
		Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Z = "Gyro_MPU_MPL_Z";
		Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_X = "Accel_MPU_MPL_X";
		Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Y = "Accel_MPU_MPL_Y";
		Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Z = "Accel_MPU_MPL_Z";
		Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_X = "Mag_MPU_MPL_X";
		Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Y = "Mag_MPU_MPL_Y";
		Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Z = "Mag_MPU_MPL_Z";
		Shimmer3.ObjectClusterSensorName.QUAT_DMP_6DOF_W = "Quat_DMP_6DOF_W";
		Shimmer3.ObjectClusterSensorName.QUAT_DMP_6DOF_X = "Quat_DMP_6DOF_X";
		Shimmer3.ObjectClusterSensorName.QUAT_DMP_6DOF_Y = "Quat_DMP_6DOF_Y";
		Shimmer3.ObjectClusterSensorName.QUAT_DMP_6DOF_Z = "Quat_DMP_6DOF_Z";
		Shimmer3.ObjectClusterSensorName.ECG_TO_HR_FW = "ECGtoHR";
		Shimmer3.ObjectClusterSensorName.PPG_TO_HR = "PPGtoHR";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_6DOF_W = "Quaternion 0";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_6DOF_X = "Quaternion 1";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_6DOF_Y = "Quaternion 2";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_6DOF_Z = "Quaternion 3";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_W = "Quaternion 0";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_X = "Quaternion 1";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y = "Quaternion 2";
		Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z = "Quaternion 3";
		Shimmer3.ObjectClusterSensorName.EULER_6DOF_YAW = "Euler_6DOF_Yaw";
		Shimmer3.ObjectClusterSensorName.EULER_6DOF_PITCH = "Euler_6DOF_Pitch";
		Shimmer3.ObjectClusterSensorName.EULER_6DOF_ROLL = "Euler_6DOF_Roll";
		Shimmer3.ObjectClusterSensorName.EULER_9DOF_YAW = "Euler_9DOF_Yaw";
		Shimmer3.ObjectClusterSensorName.EULER_9DOF_PITCH = "Euler_9DOF_Pitch";
		Shimmer3.ObjectClusterSensorName.EULER_9DOF_ROLL = "Euler_9DOF_Roll";
		Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_A = "Axis Angle A";
		Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_X = "Axis Angle X";
		Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Y = "Axis Angle Y";
		Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Z = "Axis Angle Z";
		SensorPPG.ObjectClusterSensorName.PPG_A12 = "PPG_A12";
		SensorPPG.ObjectClusterSensorName.PPG_A13 = "PPG_A13";
		SensorPPG.ObjectClusterSensorName.PPG1_A12 = "PPG1_A12";
		SensorPPG.ObjectClusterSensorName.PPG1_A13 = "PPG1_A13";
		SensorPPG.ObjectClusterSensorName.PPG2_A1 = "PPG2_A1";
		SensorPPG.ObjectClusterSensorName.PPG2_A14 = "PPG2_A14";
//		Shimmer3.ObjectClusterSensorName.REAL_TIME_CLOCK_SYNC = "RealTime_Sync";
//		Shimmer3.ObjectClusterSensorName.TIMESTAMP_SYNC = "Timestamp_Sync";

		Shimmer2.ObjectClusterSensorName.TIMESTAMP = "Timestamp";
		Shimmer2.ObjectClusterSensorName.REAL_TIME_CLOCK = "RealTime";
		Shimmer2.ObjectClusterSensorName.ACCEL_X = "Accelerometer X";
		Shimmer2.ObjectClusterSensorName.ACCEL_Y = "Accelerometer Y";
		Shimmer2.ObjectClusterSensorName.ACCEL_Z = "Accelerometer Z";
		Shimmer2.ObjectClusterSensorName.BATTERY = "VSenseBatt";
		Shimmer2.ObjectClusterSensorName.VOLT_REG = "VSenseReg";
		Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH = "Bridge Amplifier High";
		Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW = "Bridge Amplifier Low";
		Shimmer2.ObjectClusterSensorName.GSR = "GSR";
		//Shimmer2.ObjectClusterSensorName.GSR_RAW = "GSR Raw";
		Shimmer2.ObjectClusterSensorName.GSR_RES = "GSR Res";
		Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0 = "ExpBoard A0";
		Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7 = "ExpBoard A7";
		Shimmer2.ObjectClusterSensorName.GYRO_X = "Gyroscope X";
		Shimmer2.ObjectClusterSensorName.GYRO_Y = "Gyroscope Y";
		Shimmer2.ObjectClusterSensorName.GYRO_Z = "Gyroscope Z";
		Shimmer2.ObjectClusterSensorName.MAG_X = "Magnetometer X";
		Shimmer2.ObjectClusterSensorName.MAG_Y = "Magnetometer Y";
		Shimmer2.ObjectClusterSensorName.MAG_Z = "Magnetometer Z";
		Shimmer2.ObjectClusterSensorName.EMG = "EMG";
		Shimmer2.ObjectClusterSensorName.ECG_RA_LL = "ECG RA-LL";
		Shimmer2.ObjectClusterSensorName.ECG_LA_LL = "ECG LA-LL";
		Shimmer2.ObjectClusterSensorName.ECG_TO_HR = "ECGtoHR";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_6DOF_W = "Quaternion 0";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_6DOF_X = "Quaternion 1";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_6DOF_Y = "Quaternion 2";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_6DOF_Z = "Quaternion 3";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_W = "Quaternion 0";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_X = "Quaternion 1";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y = "Quaternion 2";
		Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z = "Quaternion 3";
		Shimmer2.ObjectClusterSensorName.EULER_6DOF_YAW = "Euler_6DOF_YAW";
		Shimmer2.ObjectClusterSensorName.EULER_6DOF_PITCH = "Euler_6DOF_PITCH";
		Shimmer2.ObjectClusterSensorName.EULER_6DOF_ROLL = "Euler_6DOF_Roll";
		Shimmer2.ObjectClusterSensorName.EULER_9DOF_YAW = "Euler_9DOF_YAW";
		Shimmer2.ObjectClusterSensorName.EULER_9DOF_PITCH = "Euler_9DOF_PITCH";
		Shimmer2.ObjectClusterSensorName.EULER_9DOF_ROLL = "Euler_9DOF_Roll";
		Shimmer2.ObjectClusterSensorName.HEART_RATE = "Heart_Rate"; //for the heart rate strap now no longer sold
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_6DOF_A = "Axis Angle A 6DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_6DOF_X = "Axis Angle X 6DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_6DOF_Y = "Axis Angle Y 6DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_6DOF_Z = "Axis Angle Z 6DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_9DOF_A = "Axis Angle A 9DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_9DOF_X = "Axis Angle X 9DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Y = "Axis Angle Y 9DOF";
		Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Z = "Axis Angle Z 9DOF";

	;

	}
	

	
	public static class Shimmer3{
		public class Channel{
			public static final int XAAccel     			 = 0x00;//XXX-RS-AA-SensorClass?
			public static final int YAAccel    				 = 0x01;//XXX-RS-AA-SensorClass?
			public static final int ZAAccel     			 = 0x02;//XXX-RS-AA-SensorClass?
			public static final int VBatt       			 = 0x03;
			public static final int XDAccel     			 = 0x04;//XXX-RS-LSM-SensorClass?
			public static final int YDAccel     			 = 0x05;//XXX-RS-LSM-SensorClass?
			public static final int ZDAccel     			 = 0x06;//XXX-RS-LSM-SensorClass?
			public static final int XMag        			 = 0x07;//XXX-RS-LSM-SensorClass?
			public static final int YMag        			 = 0x08;//XXX-RS-LSM-SensorClass?
			public static final int ZMag        			 = 0x09;//XXX-RS-LSM-SensorClass?
			public static final int XGyro       			 = 0x0A;
			public static final int YGyro       			 = 0x0B;
			public static final int ZGyro       			 = 0x0C;
			public static final int ExtAdc7					 = 0x0D;
			public static final int ExtAdc6					 = 0x0E;
			public static final int ExtAdc15 				 = 0x0F;
			public static final int IntAdc1					 = 0x10;
			public static final int IntAdc12 				 = 0x11;
			public static final int IntAdc13 				 = 0x12;
			public static final int IntAdc14 				 = 0x13;
			public static final int ExtAdc9					 = 0x0D;
			public static final int ExtAdc11			     = 0x0E;
			public static final int ExtAdc12 				 = 0x0F;
			public static final int IntAdc17			     = 0x10;
			public static final int IntAdc10 				 = 0x11;
			public static final int IntAdc15 				 = 0x12;
			public static final int IntAdc16 				 = 0x13;
			public static final int XAlterAccel      		 = 0x14; //Alternative Accelerometer
			public static final int YAlterAccel     		 = 0x15;
			public static final int ZAlterAccel     		 = 0x16;
			public static final int XAlterMag        		 = 0x17; //Alternative Magnetometer
			public static final int YAlterMag        		 = 0x18;
			public static final int ZAlterMag        		 = 0x19;
			public static final int Temperature 			 = 0x1A;
			public static final int Pressure 				 = 0x1B;
			public static final int GsrRaw 					 = 0x1C;
			public static final int EXG_ADS1292R_1_STATUS 	 = 0x1D;
			public static final int EXG_ADS1292R_1_CH1_24BIT = 0x1E;
			public static final int EXG_ADS1292R_1_CH2_24BIT = 0x1F;
			public static final int EXG_ADS1292R_2_STATUS 	 = 0x20;
			public static final int EXG_ADS1292R_2_CH1_24BIT = 0x21;
			public static final int EXG_ADS1292R_2_CH2_24BIT = 0x22;
			public static final int EXG_ADS1292R_1_CH1_16BIT = 0x23;
			public static final int EXG_ADS1292R_1_CH2_16BIT = 0x24;
			public static final int EXG_ADS1292R_2_CH1_16BIT = 0x25;
			public static final int EXG_ADS1292R_2_CH2_16BIT = 0x26;
			public static final int BridgeAmpHigh  			 = 0x27;
			public static final int BridgeAmpLow   			 = 0x28;
		}

		public class SensorBitmap{
			//Sensor Bitmap for Shimmer 3
			public static final int SENSOR_A_ACCEL			= 0x80;//XXX-RS-AA-SensorClass?
			public static final int SENSOR_GYRO			   	= 0x40;
			public static final int SENSOR_MAG				= 0x20; //XXX-RS-LSM-SensorClass?
			public static final int SENSOR_EXG1_24BIT		= 0x10;
			public static final int SENSOR_EXG2_24BIT		= 0x08;
			public static final int SENSOR_GSR				= 0x04;
			public static final int SENSOR_EXT_A7			= 0x02;
			public static final int SENSOR_EXT_A6			= 0x01;
			public static final int SENSOR_VBATT			= 0x2000;
			public static final int SENSOR_D_ACCEL			= 0x1000; //XXX-RS-LSM-SensorClass?
			public static final int SENSOR_EXT_A15			= 0x0800;
			public static final int SENSOR_INT_A1			= 0x0400;
			public static final int SENSOR_INT_A12			= 0x0200;
			public static final int SENSOR_INT_A13			= 0x0100;
			public static final int SENSOR_INT_A14			= 0x800000;
			public static final int SENSOR_BMP180			= 0x40000;
			public static final int SENSOR_EXG1_16BIT		= 0x100000;
			public static final int SENSOR_EXG2_16BIT		= 0x080000;
			public static final int SENSOR_BRIDGE_AMP		= 0x8000;
			public static final int SENSOR_ALT_ACCEL		= 0x400000; 
			public static final int SENSOR_ALT_MAG		  	= 0X200000; 
		}

		public static final String[] ListofBluetoothBaudRates = {"115200","1200","2400","4800","9600","19200","38400","57600","230400","460800","921600"};
		public static final Integer[] ListofBluetoothBaudRatesConfigValues = {0,1,2,3,4,5,6,7,8,9,10};
		
		//TODO Remove the need for this, standalone strings are too hardcoded and should be based on sensor/channel maps etc.
		@Deprecated 
		public static final String[] ListofCompatibleSensors = {
			"Low Noise Accelerometer",
			"Wide Range Accelerometer",
			"Gyroscope",
			"Magnetometer",
			"Battery Voltage",
			"External ADC A7",
			"External ADC A6",
			"External ADC A15",
			"Internal ADC A1",
			"Internal ADC A12",
			"Internal ADC A13",
			"Internal ADC A14",
			"Pressure",
			"GSR",
			"EXG1",
			"EXG2",
			"EXG1 16Bit",
			"EXG2 16Bit", 
			"Bridge Amplifier"}; 
		
		
		/**
		 * Some of these sensors are listed in order of how they appear in the
		 * data packets. The firmware also uses the IMU IDs as Sensor IDs in the
		 * IMU/BMP calibration bytes. 
		 */
		public static class SENSOR_ID{
			public static final int RESERVED_ANY_SENSOR = -1;
			
			public static final int HOST_SHIMMER_STREAMING_PROPERTIES = -100;
			//TODO below should be merged with HOST_REAL_TIME_CLOCK?
			public static final int HOST_SYSTEM_TIMESTAMP = -101;
			
			//Sensors channels originating from the Shimmer
			
			//Analog channels begin
			public static final int SHIMMER_TIMESTAMP = -200;//1;
			/** Shimmer3 Low-noise analog accelerometer */
			public static final int SHIMMER_ANALOG_ACCEL = 2;
			public static final int SHIMMER_VBATT = 3;
			
			public static final int SHIMMER_EXT_EXP_ADC_A7 = 4;
			public static final int SHIMMER_EXT_EXP_ADC_A6 = 5;
			public static final int SHIMMER_EXT_EXP_ADC_A15 = 6;
			
			public static final int SHIMMER_INT_EXP_ADC_A12 = 7;
			public static final int HOST_PPG_A12 = 8; //GSR Board
			public static final int HOST_PPG1_A12 = 9; //Proto3 Deluxe Board
			public static final int SHIMMER_INT_EXP_ADC_A13 = 10;
			public static final int HOST_PPG_A13 = 11; //GSR Board
			public static final int HOST_PPG1_A13 = 12; //Proto3 Deluxe Board
			public static final int SHIMMER_INT_EXP_ADC_A14 = 13;
			public static final int HOST_PPG2_A14 = 14; //Proto3 Deluxe Board
			
			//TODO which ADCs should these be beside?
			public static final int SHIMMER_BRIDGE_AMP = 15;
			public static final int SHIMMER_RESISTANCE_AMP = 16;
			
			public static final int SHIMMER_INT_EXP_ADC_A1 = 17;
			public static final int HOST_PPG2_A1 = 18; //Proto3 Deluxe Board
			public static final int SHIMMER_GSR = 19;// Based on ADC1

			//Digital channels begin
			public static final int SHIMMER_MPU9X50_GYRO = 30;
			/** Shimmer3 Wide-range digital accelerometer */
			public static final int SHIMMER_LSM303_ACCEL = 31;
			public static final int SHIMMER_LSM303_MAG = 32;
			/** Shimmer3 Alternative accelerometer */
			public static final int SHIMMER_MPU9X50_ACCEL = 33;
			/** Shimmer3 Alternative magnetometer */
			public static final int SHIMMER_MPU9X50_MAG = 34;
			public static final int SHIMMER_MPU9X50_TEMP = 35;
			public static final int SHIMMER_BMPX80_PRESSURE = 36;	//BMP180 and BMP280
      
			/** Shimmer3r Wide-Range Accelerometer **/
			public static final int SHIMMER_LSM6DSV_ACCEL_LN = 37;
			public static final int SHIMMER_LSM6DSV_GYRO = 38;			
			public static final int SHIMMER_LIS2DW12_ACCEL_WR = 39;
			public static final int SHIMMER_ADXL371_ACCEL_HIGHG = 40;
			public static final int SHIMMER_LIS2MDL_MAG = 42;
			public static final int SHIMMER_LIS3MDL_MAG_ALT = 41;
			public static final int SHIMMER_BMP390_PRESSURE = 43;
			public static final int SHIMMER_BMP581_PRESSURE = 44;
			
//			public static final int SHIMMER_EXG1_24BIT = 3;
//			public static final int SHIMMER_EXG2_24BIT = 4;
			//public static final int SHIMMER_HR = 9;
//			public static final int SHIMMER_EXG1_16BIT = 19;
//			public static final int SHIMMER_EXG2_16BIT = 21;
			
			//public static final int SHIMMER_BMP180_TEMPERATURE = 23; // not yet implemented
			//public static final int SHIMMER_MSP430_TEMPERATURE = 24; // not yet implemented
			//public static final int SHIMMER_LSM303DLHC_TEMPERATURE = 26; // not yet implemented
			//public static final int SHIMMER_MPU9150_MPL_TEMPERATURE = 1<<17; // same as SENSOR_SHIMMER3_MPU9150_TEMP 			

			public static final int SHIMMER_MPU9X50_MPL_QUAT_6DOF = 50;
			public static final int SHIMMER_MPU9X50_MPL_QUAT_9DOF = 51;
			public static final int SHIMMER_MPU9X50_MPL_EULER_6DOF = 52;
			public static final int SHIMMER_MPU9X50_MPL_EULER_9DOF = 53;
			public static final int SHIMMER_MPU9X50_MPL_HEADING = 54;
			public static final int SHIMMER_MPU9X50_MPL_PEDOMETER = 55;
			public static final int SHIMMER_MPU9X50_MPL_TAP = 56;
			public static final int SHIMMER_MPU9X50_MPL_MOTION_ORIENT = 57;
			public static final int SHIMMER_MPU9X50_MPL_GYRO = 58;
			public static final int SHIMMER_MPU9X50_MPL_ACCEL = 59;
			public static final int SHIMMER_MPU9X50_MPL_MAG = 60;
			public static final int SHIMMER_MPU9X50_MPL_QUAT_6DOF_RAW = 61;
			
			// STC3100 Channels
			public static final int SHIMMER_STC3100 = 62;
//			public static final int SHIMMER_STC3100_VOLTAGE = 62;
//			public static final int SHIMMER_STC3100_CURRENT = 63;
//			public static final int SHIMMER_STC3100_TEMP = 64;
//			public static final int SHIMMER_STC3100_CHARGE = 65;
//			public static final int SHIMMER_STC3100_BATTERY_PERCENTAGE = 66;
//			public static final int SHIMMER_STC3100_TIME_REMAINING = 67;
			

			//Sensors channels modified or created on the host side
			// Combination Channels
			public static final int HOST_ECG = 100;
			public static final int HOST_EMG = 101;
			public static final int HOST_EXG_TEST = 102;
			public static final int HOST_EXG_CUSTOM = 116;
			public static final int HOST_EXG_THREE_UNIPOLAR = 106;
			
			// Derived Channels
			public static final int HOST_EXG_RESPIRATION = 103;
			public static final int HOST_SKIN_TEMPERATURE_PROBE = 104;
	
			public static final int HOST_PPG_DUMMY = 105;
			public static final int HOST_PPG1_DUMMY = 110;
			public static final int HOST_PPG2_DUMMY = 113;
			
			public static final int HOST_TIMESTAMP_SYNC = 151;
			public static final int HOST_REAL_TIME_CLOCK = 152;
			public static final int HOST_REAL_TIME_CLOCK_SYNC = 153;
			
			public static final int SHIMMER_ECG_TO_HR_FW = 150;
			
			// Third party devices @ 1000+
			public static final int THIRD_PARTY_NONIN = 1000;
			public static final int HOST_KEYBOARD_LISTENER = 1001;
			public static final int HOST_MOUSE_LISTENER = 1002;
			public static final int HOST_WEBCAM = 1003;
			public static final int HOST_CPU_USAGE = 1004;
		}

		/**
		 * The bit mask must not change for each algorithm as this is what is
		 * stored in the Shimmer's infomem and the SD file configuration header.
		 * 
		 * 3 bytes for derived channels were introduced in LogAndStream v0.3.15 and SDLog v0.8.69.
		 * 5 bytes in GQ firmware.
		 * 8 bytes in LogAndStream v0.7.1 and SDLog v0.13.1 onwards
		 */
		public class DerivedSensorsBitMask {
			// -------------- Derived Channels Byte 0 -------------------
			public final static int RES_AMP 					= 1 << 0; // (0*8 + 0);
			public final static int SKIN_TEMP 					= 1 << 1; // (0*8 + 1);
			
			// TODO: Now implemented in SensorPPG -> decide where best these
			// bits belong, Configuration.Shimmer3 or sensor/algorithm classes
			public final static int PPG_12_13 					= 1 << 2; // (0*8 + 2);
			public final static int PPG1_12_13 					= 1 << 3; // (0*8 + 3);
			public final static int PPG2_1_14 					= 1 << 4; // (0*8 + 4);
			public final static int PPG_TO_HR_12_13 			= 1 << 5; // (0*8 + 5);
			public final static int PPG_TO_HR1_12_13 			= 1 << 6; // (0*8 + 6);
			public final static int PPG_TO_HR2_1_14 			= 1 << 7; // (0*8 + 7);
			
			// -------------- Derived Channels Byte 1 -------------------
			public final static int ACTIVITY_MODULE 			= 1 << 8; // (1*8 + 0);
			
			public final static int GSR_METRICS_GENERAL			= 1 << 9; // (1*8 + 1);

			public final static int ECG2HR_HRV_FREQ_DOMAIN 		= 1 << 10; // (1*8 + 2);
			public final static int ECG2HR_HRV_TIME_DOMAIN 		= 1 << 11; // (1*8 + 3);
			public final static int ECG2HR_CHIP2_CH2 			= 1 << 12; // (1*8 + 4);
			public final static int ECG2HR_CHIP2_CH1 			= 1 << 13; // (1*8 + 5);
			public final static int ECG2HR_CHIP1_CH2 			= 1 << 14; // (1*8 + 6);
			public final static int ECG2HR_CHIP1_CH1 			= 1 << 15; // (1*8 + 7);

			// -------------- Derived Channels Byte 2 -------------------
			public final static int ORIENTATION_9DOF_WR_QUAT 	= 1 << 16; // (2*8 + 0);
			public final static int ORIENTATION_9DOF_WR_EULER 	= 1 << 17; // (2*8 + 1);
			public final static int ORIENTATION_6DOF_WR_QUAT 	= 1 << 18; // (2*8 + 2);
			public final static int ORIENTATION_6DOF_WR_EULER 	= 1 << 19; // (2*8 + 3);
			public final static int ORIENTATION_9DOF_LN_QUAT 	= 1 << 20; // (2*8 + 4);
			public final static int ORIENTATION_9DOF_LN_EULER 	= 1 << 21; // (2*8 + 5);
			public final static int ORIENTATION_6DOF_LN_QUAT 	= 1 << 22; // (2*8 + 6);
			public final static int ORIENTATION_6DOF_LN_EULER 	= 1 << 23; // (2*8 + 7);

			// -------------- Derived Channels Byte 3 -------------------
			public final static int EMG_PROCESSING_CHAN2 		= 1 << 24; // (3*8 + 0);
			public final static int EMG_PROCESSING_CHAN1 		= 1 << 25; // (3*8 + 1);
			
			public final static int GSR_BASELINE 				= 1 << 26; // (3*8 + 2);
			public final static int GSR_METRICS_TREND_PEAK		= 1 << 27; // (3*8 + 3);
			
			public final static int GAIT_MODULE 				= 1 << 28; // (3*8 + 4);

			public final static int GYRO_ON_THE_FLY_CAL			= 1 << 29; // (3*8 + 5);
//			public final static int UNUSED 						= 1 << 30; // (3*8 + 6);
//			public final static int UNUSED 						= 1 << 31; // (3*8 + 7);

			// -------------- Derived Channels Byte 4 -------------------
			// Currently Unused
			// -------------- Derived Channels Byte 5 -------------------
			// Currently Unused
			// -------------- Derived Channels Byte 6 -------------------
			// Currently Unused
			// -------------- Derived Channels Byte 7 -------------------
			// Currently Unused
		}

		// Config Options Map
		public class GuiLabelConfig{
			public static final String SHIMMER_USER_ASSIGNED_NAME = "Shimmer Name";
			public static final String TRIAL_NAME = "Trial Name";
			/** Algorithm models and sensor classes also rely on this*/
			public static final String SHIMMER_SAMPLING_RATE = "Sampling Rate";
			public static final String SHIMMER_AND_SENSORS_SAMPLING_RATE = "Shimmer and Sensors Sampling Rate";
			public static final String BUFFER_SIZE = "Buffer Size";
			public static final String CONFIG_TIME = "Config Time";
			public static final String EXPERIMENT_NUMBER_OF_SHIMMERS = "Number Of Shimmers";
			public static final String SHIMMER_MAC_FROM_INFOMEM = "InfoMem MAC";
			public static final String EXPERIMENT_ID = "Experiment ID";
			public static final String EXPERIMENT_DURATION_ESTIMATED = "Estimated Duration";
			public static final String EXPERIMENT_DURATION_MAXIMUM = "Maximum Duration";
			public static final String BROADCAST_INTERVAL = "Broadcast Interval";
			public static final String BLUETOOTH_BAUD_RATE = "Bluetooth Baud Rate";

			public static final String ENABLED_SENSORS = "Enabled Sensors Int";
			public static final String ENABLED_SENSORS_IDS = "Enabled SensorsIds";

			public static final String SD_BT_STREAM_WHEN_RECORDING = "<html>SD Log and/or<br> Bluetooth Stream</html>";
			public static final String SD_STREAM_WHEN_RECORDING = "<html>SD Log Recording<br>Only</html>";
			public static final String SD_SYNC_STREAM_WHEN_RECORDING = "<html>SD Log with<br> Inter-device Sync</html>";
			public static final String USER_BUTTON_START = "User Button";
			public static final String UNDOCK_START = "Undock/Dock";
			public static final String SINGLE_TOUCH_START = "Single Touch Start";
			public static final String EXPERIMENT_MASTER_SHIMMER = "Master Shimmer";
			public static final String EXPERIMENT_SYNC_WHEN_LOGGING = "Sync When Logging";

			public static final String TCXO = "TCX0";
			public static final String INT_EXP_BRD_POWER_BOOLEAN = "Internal Expansion Board Power";
			public static final String INT_EXP_BRD_POWER_INTEGER = "Int Exp Power";
			public static final String ENABLE_ERROR_LEDS_RTC = "RTC Error LEDs";
			public static final String ENABLE_ERROR_LEDS_SD = "SD Error LEDs";
			public static final String LOW_POWER_AUTOSTOP = "Low-power Autostop";

			public static final String KINEMATIC_LPM = "Kinematic Sensors Low-Power Mode";//XXX-RS-LSM-SensorClass? What about HighResolutionMode?!
			public static final String CALIBRATION_ALL = AbstractSensor.GuiLabelConfigCommon.CALIBRATION_ALL;
			public static final String CALIBRATION_PER_SENSOR = AbstractSensor.GuiLabelConfigCommon.CALIBRATION_PER_SENSOR;
		}

		/** GUI Sensor Tiles
		 *	Order of Enum is the order in which they will be generated in the GUI
		 */
		public enum LABEL_SENSOR_TILE{
			STREAMING_PROPERTIES(SensorShimmerClock.LABEL_SENSOR_TILE.STREAMING_PROPERTIES),
			LOW_NOISE_ACCEL(SensorKionixAccel.LABEL_SENSOR_TILE.LOW_NOISE_ACCEL),
			LOW_NOISE_ACCEL_3R(SensorLSM6DSV.LABEL_SENSOR_TILE.LOW_NOISE_ACCEL),
			WIDE_RANGE_ACCEL(SensorLSM303.LABEL_SENSOR_TILE.WIDE_RANGE_ACCEL),
			HIGH_G_ACCEL(SensorADXL371.LABEL_SENSOR_TILE.HIGH_G_ACCEL),
			WIDE_RANGE_ACCEL_3R(SensorLIS2DW12.LABEL_SENSOR_TILE.WIDE_RANGE_ACCEL),
			GYRO(SensorMPU9X50.LABEL_SENSOR_TILE.GYRO),
			GYRO_3R(SensorLSM6DSV.LABEL_SENSOR_TILE.GYRO),
			MAG(SensorLSM303.LABEL_SENSOR_TILE.MAG),
			MAG_3R(SensorLIS2MDL.LABEL_SENSOR_TILE.MAG),
			ALT_MAG_3R(SensorLIS3MDL.LABEL_SENSOR_TILE.ALT_MAG),
			PRESSURE_TEMPERATURE_BMP180(SensorBMP180.LABEL_SENSOR_TILE.PRESSURE_TEMPERATURE),
			PRESSURE_TEMPERATURE_BMP280(SensorBMP280.LABEL_SENSOR_TILE.PRESSURE_TEMPERATURE),
			PRESSURE_TEMPERATURE_BMP390(SensorBMP390.LABEL_SENSOR_TILE.PRESSURE_TEMPERATURE),
			PRESSURE_TEMPERATURE_BMP581(SensorBMP581.LABEL_SENSOR_TILE.PRESSURE_TEMPERATURE),
			BATTERY_MONITORING(SensorBattVoltage.LABEL_SENSOR_TILE.BATTERY_MONITORING),
			EXTERNAL_EXPANSION_ADC(SensorADC.LABEL_SENSOR_TILE.EXTERNAL_EXPANSION_ADC),
			INTERNAL_EXPANSION_ADC(SensorADC.LABEL_SENSOR_TILE.INTERNAL_EXPANSION_ADC),
			GSR(SensorGSR.LABEL_SENSOR_TILE.GSR),
			EXG("ECG/EMG"),
			PROTO3_MINI("Proto Mini"),
			PROTO3_DELUXE("Proto Deluxe"),
			PROTO3_DELUXE_SUPP(SensorPPG.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP),
			BRIDGE_AMPLIFIER(SensorBridgeAmp.LABEL_SENSOR_TILE.BRIDGE_AMPLIFIER),
			BRIDGE_AMPLIFIER_SUPP(SensorBridgeAmp.LABEL_SENSOR_TILE.BRIDGE_AMPLIFIER_SUPP),
			ADXL377_ACCEL_200G(Configuration.Shimmer3.GuiLabelSensors.ADXL377_ACCEL_200G),
			MPU_ACCEL_GYRO_MAG(SensorMPU9X50.LABEL_SENSOR_TILE.MPU_ACCEL_GYRO_MAG),
			MPU(SensorMPU9X50.LABEL_SENSOR_TILE.MPU),
			MPU_OTHER(SensorMPU9X50.LABEL_SENSOR_TILE.MPU_OTHER),
			GPS("GPS"),
			STC3100_MONITORING (SensorSTC3100.LABEL_SENSOR_TILE.STC3100_MONITORING),
			SWEATCH_ADC ("Sweatch ADC");
			
			private String tileText = "";
			LABEL_SENSOR_TILE(String text){
				this.tileText = text;
			}
			
			public String getTileText(){
				return tileText;
			}
		}
		
		//GUI SENSORS
		//TODO Change over to sensor classes
		public class GuiLabelSensors{
//			public static final String ACCEL_LN = SensorKionixKXRB52042.GuiLabelSensors.ACCEL_LN;
//			public static final String ACCEL_LN = SensorLSM6DSV.GuiLabelSensors.ACCEL_LN;
			
//			public static final String BATTERY = SensorBattVoltage.GuiLabelSensors.BATTERY;
			
//			public static final String EXT_EXP_A7 = SensorADC.GuiLabelSensors.EXT_EXP_A7;
//			public static final String EXT_EXP_A6 = SensorADC.GuiLabelSensors.EXT_EXP_A6;
//			public static final String EXT_EXP_A15 = SensorADC.GuiLabelSensors.EXT_EXP_A15;
//			public static final String INT_EXP_A12 = SensorADC.GuiLabelSensors.INT_EXP_A12;
//			public static final String INT_EXP_A13 = SensorADC.GuiLabelSensors.INT_EXP_A13;
//			public static final String INT_EXP_A14 = SensorADC.GuiLabelSensors.INT_EXP_A14;
//			public static final String INT_EXP_A1 = SensorADC.GuiLabelSensors.INT_EXP_A1;
//			
			public static final String BRIDGE_AMPLIFIER = SensorBridgeAmp.GuiLabelSensors.BRIDGE_AMPLIFIER;
			public static final String RESISTANCE_AMP = SensorBridgeAmp.GuiLabelSensors.RESISTANCE_AMP;
			public static final String SKIN_TEMP_PROBE = SensorBridgeAmp.GuiLabelSensors.SKIN_TEMP_PROBE;
			public static final String BRAMP_HIGHGAIN = SensorBridgeAmp.GuiLabelSensors.BRAMP_HIGHGAIN;
			public static final String BRAMP_LOWGAIN = SensorBridgeAmp.GuiLabelSensors.BRAMP_LOWGAIN;

//			public static final String GSR = SensorGSR.GuiLabelSensors.GSR;
			
//			public static final String ACCEL_WR = SensorLSM303.GuiLabelSensors.ACCEL_WR;
			public static final String MAG = SensorLSM303DLHC.GuiLabelSensors.MAG;
			public static final String ALT_MAG_3R = SensorLIS3MDL.GuiLabelSensors.MAG_ALT;

//			public static final String PRESS_TEMP_BMP180 = 	SensorBMP180.GuiLabelSensors.PRESS_TEMP_BMP180;

//			public static final String GYRO = SensorMPU9X50.GuiLabelSensors.GYRO;
//			public static final String ACCEL_MPU = SensorMPU9X50.GuiLabelSensors.ACCEL_MPU;
//			public static final String MAG_MPU = SensorMPU9X50.GuiLabelSensors.MAG_MPU;

			public static final String GYRO_MPU_MPL = SensorMPU9X50.GuiLabelSensors.GYRO_MPU_MPL;
			public static final String ACCEL_MPU_MPL = SensorMPU9X50.GuiLabelSensors.ACCEL_MPU_MPL;
			public static final String MAG_MPU_MPL = SensorMPU9X50.GuiLabelSensors.MAG_MPU_MPL;
			
			public static final String QUAT_MPL_6DOF = SensorMPU9X50.GuiLabelSensors.QUAT_MPL_6DOF;
			public static final String QUAT_MPL_9DOF = SensorMPU9X50.GuiLabelSensors.QUAT_MPL_9DOF;
			public static final String EULER_MPL_6DOF = SensorMPU9X50.GuiLabelSensors.EULER_MPL_6DOF;
			public static final String EULER_MPL_9DOF = SensorMPU9X50.GuiLabelSensors.EULER_MPL_9DOF;
			public static final String QUAT_DMP_6DOF = SensorMPU9X50.GuiLabelSensors.QUAT_DMP_6DOF;

			public static final String MPL_HEADING = SensorMPU9X50.GuiLabelSensors.MPL_HEADING;
			public static final String MPL_TEMPERATURE = SensorMPU9X50.GuiLabelSensors.MPL_TEMPERATURE;
			public static final String MPL_PEDOM_CNT = SensorMPU9X50.GuiLabelSensors.MPL_PEDOM_CNT; // not currently supported
			public static final String MPL_PEDOM_TIME = SensorMPU9X50.GuiLabelSensors.MPL_PEDOM_TIME; // not currently supported
			public static final String MPL_TAPDIRANDTAPCNT = SensorMPU9X50.GuiLabelSensors.MPL_TAPDIRANDTAPCNT; //"TapDirAndTapCnt"; // not currently supported
			public static final String MPL_MOTIONANDORIENT = SensorMPU9X50.GuiLabelSensors.MPL_MOTIONANDORIENT; //"MotionAndOrient"; // not currently supported
			
			public static final String ECG_TO_HR = "ECG To HR";
			public static final String PPG_TO_HR = "PPG To HR";
			
			//TODO: refer to OrienationModule instead
			public static final String ORIENTATION_3D_6DOF = "3D Orientation (6DOF)"; 
			public static final String ORIENTATION_3D_9DOF = "3D Orientation (9DOF)"; 
			public static final String EULER_ANGLES_6DOF = "Euler Angles (6DOF)"; 
			public static final String EULER_ANGLES_9DOF = "Euler Angles (9DOF)";   // needed in both MPU and Algorithm???

			public static final String ADXL377_ACCEL_200G = "200g Accel";
			
			public static final String ADXL371_ACCEL_HIGHG = "High-G Accel";

//			public static final String PPG_DUMMY = SensorPPG.GuiLabelSensors.PPG_DUMMY;
//			public static final String PPG_A12 = SensorPPG.GuiLabelSensors.PPG_A12;
//			public static final String PPG_A13 = SensorPPG.GuiLabelSensors.PPG_A13;
//			public static final String PPG1_DUMMY = SensorPPG.GuiLabelSensors.PPG1_DUMMY;
//			public static final String PPG1_A12 = SensorPPG.GuiLabelSensors.PPG1_A12;
//			public static final String PPG1_A13 = SensorPPG.GuiLabelSensors.PPG1_A13;
//			public static final String PPG2_DUMMY = SensorPPG.GuiLabelSensors.PPG2_DUMMY;
//			public static final String PPG2_A1 = SensorPPG.GuiLabelSensors.PPG2_A1;
//			public static final String PPG2_A14 = SensorPPG.GuiLabelSensors.PPG2_A14;
			
			public static final String EMG = SensorEXG.GuiLabelSensors.EMG;
			public static final String ECG = SensorEXG.GuiLabelSensors.ECG;
			public static final String EXG_TEST = SensorEXG.GuiLabelSensors.EXG_TEST;
			public static final String EXG_RESPIRATION = SensorEXG.GuiLabelSensors.EXG_RESPIRATION;
			public static final String EXG1_24BIT = SensorEXG.GuiLabelSensors.EXG1_24BIT;
			public static final String EXG2_24BIT = SensorEXG.GuiLabelSensors.EXG2_24BIT;
			public static final String EXG1_16BIT = SensorEXG.GuiLabelSensors.EXG1_16BIT;
			public static final String EXG2_16BIT = SensorEXG.GuiLabelSensors.EXG2_16BIT;
			public static final String EXG_CUSTOM = SensorEXG.GuiLabelSensors.EXG_CUSTOM;
		}
		
		// GUI Algorithm Grouping
		public enum GuiLabelAlgorithmGrouping{
			TIME_SYNC("Time Sync"),
			GYRO_ON_THE_FLY_CAL("Gyro on-the-fly Calibration"),
			ORIENTATION_9DOF("9DOF"),  
			ORIENTATION_6DOF("6DOF"),  
			ECG_TO_HR("ECG-to-HR (with IBI)"),
			PPG_TO_HR("PPG-to-HR (with IBI)"),
			PPG_TO_HR_ADAPTIVE("PPG-to-HR Adaptive"),
			HRV_ECG("HRV"),
			EMG("EMG Processing"),
			ACTIVITY("Activity"),
			GAIT("Gait"),
			GSR_METRICS("GSR Metrics"),
			GSR_BASELINE("GSR Baseline");

			private String tileText = "";
			
			GuiLabelAlgorithmGrouping(String text){
				this.tileText = text;
			}
			
			public String getTileText(){
				return tileText;
			}
		}

		//DATABASE NAMES
		public static class DatabaseChannelHandles{
			/** Calibrated Data Table = Raw Data Table + some processed signals **/
			public static final String FILTERED = "_Filtered"; // to create the name of the filtered signals
			
			//TODO: refer to ECG/PPG Algorithm Modules instead
			public static final String ECG_TO_HR = "ECGToHR";
			public static final String PPG_TO_HR = "PPGToHR";
		}
		
		//GUI AND EXPORT CHANNELS
		public static class ObjectClusterSensorName{
//			public static String EULER_9DOF_Y;
//			public static String EULER_9DOF_Z;
//			public static final String SHIMMER = "Shimmer";
			public static final String PACKET_RECEPTION_RATE_CURRENT = ShimmerStreamingProperties.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT;//"Packet_Reception_Rate_Current";
			public static final String PACKET_RECEPTION_RATE_OVERALL = ShimmerStreamingProperties.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL;//"Packet_Reception_Rate_Trial";
			
			public static String TIMESTAMP = 			SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP;//"Timestamp";
			public static String REAL_TIME_CLOCK = 		SensorShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK;//"RealTime";
			public static String SHIMMER_CLOCK = 		SensorShimmerClock.ObjectClusterSensorName.SHIMMER_CLOCK;//"RealTime";
//			public static String REAL_TIME_CLOCK_SYNC = TimeSyncModule.ObjectClusterSensorName.REAL_TIME_CLOCK_SYNC;//"RealTime_Sync";
//			public static String TIMESTAMP_SYNC = 		TimeSyncModule.ObjectClusterSensorName.TIMESTAMP_SYNC;//"Timestamp_Sync";
			public static String SYSTEM_TIMESTAMP = 	SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP;//"System_Timestamp";
			public static String SYSTEM_TIMESTAMP_PLOT = SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP_PLOT;//"System_Timestamp_plot";

			public static String TIMESTAMP_OFFSET = SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET;//"Offset";

			public static String ACCEL_LN_X = SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_X;
			public static String ACCEL_LN_Y = SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_Y;
			public static String ACCEL_LN_Z = SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_Z;
			
			public static String BATTERY = SensorBattVoltage.ObjectClusterSensorName.BATTERY;
			public static final String BATT_PERCENTAGE = SensorBattVoltage.ObjectClusterSensorName.BATT_PERCENTAGE;
			
			public static String EXT_EXP_ADC_A7 = SensorADC.ObjectClusterSensorName.EXT_EXP_ADC_A7;
			public static String EXT_EXP_ADC_A6 = SensorADC.ObjectClusterSensorName.EXT_EXP_ADC_A6;
			public static String EXT_EXP_ADC_A15 = SensorADC.ObjectClusterSensorName.EXT_EXP_ADC_A15;
			public static String INT_EXP_ADC_A1 = SensorADC.ObjectClusterSensorName.INT_EXP_ADC_A1;
			public static String INT_EXP_ADC_A12 = SensorADC.ObjectClusterSensorName.INT_EXP_ADC_A12;
			public static String INT_EXP_ADC_A13 = SensorADC.ObjectClusterSensorName.INT_EXP_ADC_A13;
			public static String INT_EXP_ADC_A14 = SensorADC.ObjectClusterSensorName.INT_EXP_ADC_A14;
			
			//Generalised ADC channel name and number
			public static String EXT_ADC_0 = SensorADC.ObjectClusterSensorName.EXT_ADC_0;
			public static String EXT_ADC_1 = SensorADC.ObjectClusterSensorName.EXT_ADC_1;
			public static String EXT_ADC_2 = SensorADC.ObjectClusterSensorName.EXT_ADC_2;
			public static String INT_ADC_3 = SensorADC.ObjectClusterSensorName.INT_ADC_3;
			public static String INT_ADC_0 = SensorADC.ObjectClusterSensorName.INT_ADC_0;
			public static String INT_ADC_1 = SensorADC.ObjectClusterSensorName.INT_ADC_1;
			public static String INT_ADC_2 = SensorADC.ObjectClusterSensorName.INT_ADC_2;
			
			public static  String ACCEL_HIGHG_X = SensorADXL371.ObjectClusterSensorName.ACCEL_HIGHG_X;
			public static  String ACCEL_HIGHG_Y = SensorADXL371.ObjectClusterSensorName.ACCEL_HIGHG_Y;
			public static  String ACCEL_HIGHG_Z= SensorADXL371.ObjectClusterSensorName.ACCEL_HIGHG_Z;
			public static  String ALT_MAG_X = SensorLIS3MDL.ObjectClusterSensorName.MAG_ALT_X;
			public static  String ALT_MAG_Y = SensorLIS3MDL.ObjectClusterSensorName.MAG_ALT_Y;
			public static  String ALT_MAG_Z= SensorLIS3MDL.ObjectClusterSensorName.MAG_ALT_Z;
			
			public static String BRIDGE_AMP_HIGH = SensorBridgeAmp.ObjectClusterSensorName.BRIDGE_AMP_HIGH;
			public static String BRIDGE_AMP_LOW = SensorBridgeAmp.ObjectClusterSensorName.BRIDGE_AMP_LOW;
			public static String RESISTANCE_AMP = SensorBridgeAmp.ObjectClusterSensorName.RESISTANCE_AMP;
			public static final String SKIN_TEMPERATURE_PROBE = SensorBridgeAmp.ObjectClusterSensorName.SKIN_TEMPERATURE_PROBE;
			public static final String FREQUENCY = ShimmerGq802154.ObjectClusterSensorName.FREQUENCY;

			public static String GSR_RESISTANCE = SensorGSR.ObjectClusterSensorName.GSR_RESISTANCE;
			public static String GSR_CONDUCTANCE = SensorGSR.ObjectClusterSensorName.GSR_CONDUCTANCE;

			public static String ACCEL_WR_X = SensorLSM303.ObjectClusterSensorName.ACCEL_WR_X;
			public static String ACCEL_WR_Y = SensorLSM303.ObjectClusterSensorName.ACCEL_WR_Y;
			public static String ACCEL_WR_Z= SensorLSM303.ObjectClusterSensorName.ACCEL_WR_Z;
			public static String MAG_X = SensorLSM303.ObjectClusterSensorName.MAG_X;
			public static String MAG_Y = SensorLSM303.ObjectClusterSensorName.MAG_Y;
			public static String MAG_Z = SensorLSM303.ObjectClusterSensorName.MAG_Z;
			
			public static String TEMPERATURE_BMP180 = SensorBMP180.ObjectClusterSensorName.TEMPERATURE_BMP180;
			public static String PRESSURE_BMP180 = SensorBMP180.ObjectClusterSensorName.PRESSURE_BMP180;
			
			public static String ECG_GQ = SensorEXG.ObjectClusterSensorName.ECG_GQ;
//			public static String ECG_TO_HR_FW_GQ = SensorECGToHRFw.DatabaseChannelHandles.ECG_TO_HR_FW;//"ECGToHR_FW";
//			public static String ECG_TO_HR_SW_GQ = "ECGToHR_SW";
			public static String EXG1_STATUS = SensorEXG.ObjectClusterSensorName.EXG1_STATUS;
			public static String EXG2_STATUS = SensorEXG.ObjectClusterSensorName.EXG2_STATUS;
			public static String EXG1_CH1_24BIT = SensorEXG.ObjectClusterSensorName.EXG1_CH1_24BIT;
			public static String EXG1_CH2_24BIT = SensorEXG.ObjectClusterSensorName.EXG1_CH2_24BIT;
			public static String EXG1_CH1_16BIT = SensorEXG.ObjectClusterSensorName.EXG1_CH1_16BIT;
			public static String EXG1_CH2_16BIT = SensorEXG.ObjectClusterSensorName.EXG1_CH2_16BIT;
			public static String EXG2_CH1_24BIT = SensorEXG.ObjectClusterSensorName.EXG2_CH1_24BIT;
			public static String EXG2_CH2_24BIT = SensorEXG.ObjectClusterSensorName.EXG2_CH2_24BIT;
			public static String EXG2_CH1_16BIT = SensorEXG.ObjectClusterSensorName.EXG2_CH1_16BIT;
			public static String EXG2_CH2_16BIT = SensorEXG.ObjectClusterSensorName.EXG2_CH2_16BIT;
			public static String EMG_CH1_24BIT = SensorEXG.ObjectClusterSensorName.EMG_CH1_24BIT;
			public static String EMG_CH2_24BIT = SensorEXG.ObjectClusterSensorName.EMG_CH2_24BIT;
			public static String EMG_CH1_16BIT = SensorEXG.ObjectClusterSensorName.EMG_CH1_16BIT;
			public static String EMG_CH2_16BIT = SensorEXG.ObjectClusterSensorName.EMG_CH2_16BIT;
			public static String ECG_LA_RA_24BIT = SensorEXG.ObjectClusterSensorName.ECG_LA_RA_24BIT;
			public static String ECG_LA_RL_24BIT = SensorEXG.ObjectClusterSensorName.ECG_LA_RL_24BIT;
			public static String ECG_LL_RA_24BIT = SensorEXG.ObjectClusterSensorName.ECG_LL_RA_24BIT;
			public static String ECG_LL_LA_24BIT = SensorEXG.ObjectClusterSensorName.ECG_LL_LA_24BIT; //derived
			public static String ECG_RESP_24BIT = SensorEXG.ObjectClusterSensorName.ECG_RESP_24BIT;
			public static String ECG_VX_RL_24BIT = SensorEXG.ObjectClusterSensorName.ECG_VX_RL_24BIT;
			public static String ECG_LA_RA_16BIT = SensorEXG.ObjectClusterSensorName.ECG_LA_RA_16BIT;
			public static String ECG_LA_RL_16BIT = SensorEXG.ObjectClusterSensorName.ECG_LA_RL_16BIT;
			public static String ECG_LL_RA_16BIT = SensorEXG.ObjectClusterSensorName.ECG_LL_RA_16BIT;
			public static String ECG_LL_LA_16BIT = SensorEXG.ObjectClusterSensorName.ECG_LL_LA_16BIT; //derived
			public static String ECG_RESP_16BIT = SensorEXG.ObjectClusterSensorName.ECG_RESP_16BIT;
			public static String ECG_VX_RL_16BIT = SensorEXG.ObjectClusterSensorName.ECG_VX_RL_16BIT;
			public static String EXG_TEST_CHIP1_CH1_24BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_24BIT;
			public static String EXG_TEST_CHIP1_CH2_24BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_24BIT;
			public static String EXG_TEST_CHIP2_CH1_24BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_24BIT;
			public static String EXG_TEST_CHIP2_CH2_24BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_24BIT;
			public static String EXG_TEST_CHIP1_CH1_16BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_16BIT;
			public static String EXG_TEST_CHIP1_CH2_16BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_16BIT;
			public static String EXG_TEST_CHIP2_CH1_16BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_16BIT;
			public static String EXG_TEST_CHIP2_CH2_16BIT = SensorEXG.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_16BIT;
			
			public static String GYRO_X = SensorMPU9X50.ObjectClusterSensorName.GYRO_X;
			public static String GYRO_Y = SensorMPU9X50.ObjectClusterSensorName.GYRO_Y;
			public static String GYRO_Z = SensorMPU9X50.ObjectClusterSensorName.GYRO_Z;
			public static String ACCEL_MPU_X = SensorMPU9X50.ObjectClusterSensorName.ACCEL_MPU_X;
			public static String ACCEL_MPU_Y = SensorMPU9X50.ObjectClusterSensorName.ACCEL_MPU_Y;
			public static String ACCEL_MPU_Z = SensorMPU9X50.ObjectClusterSensorName.ACCEL_MPU_Z;
			public static String MAG_MPU_X = SensorMPU9X50.ObjectClusterSensorName.MAG_MPU_X;
			public static String MAG_MPU_Y = SensorMPU9X50.ObjectClusterSensorName.MAG_MPU_Y;
			public static String MAG_MPU_Z = SensorMPU9X50.ObjectClusterSensorName.MAG_MPU_Z;
			
			public static String QUAT_MPL_6DOF_W = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_6DOF_W;
			public static String QUAT_MPL_6DOF_X = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_6DOF_X;
			public static String QUAT_MPL_6DOF_Y = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_6DOF_Y;
			public static String QUAT_MPL_6DOF_Z = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_6DOF_Z;
			public static String QUAT_MPL_9DOF_W = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_9DOF_W;
			public static String QUAT_MPL_9DOF_X = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_9DOF_X;
			public static String QUAT_MPL_9DOF_Y = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_9DOF_Y;
			public static String QUAT_MPL_9DOF_Z = SensorMPU9X50.ObjectClusterSensorName.QUAT_MPL_9DOF_Z;
			public static String EULER_MPL_6DOF_X = SensorMPU9X50.ObjectClusterSensorName.EULER_MPL_6DOF_X;
			public static String EULER_MPL_6DOF_Y = SensorMPU9X50.ObjectClusterSensorName.EULER_MPL_6DOF_Y;
			public static String EULER_MPL_6DOF_Z = SensorMPU9X50.ObjectClusterSensorName.EULER_MPL_6DOF_Z;
			public static String EULER_MPL_9DOF_X = SensorMPU9X50.ObjectClusterSensorName.EULER_MPL_9DOF_X;
			public static String EULER_MPL_9DOF_Y = SensorMPU9X50.ObjectClusterSensorName.EULER_MPL_9DOF_Y;
			public static String EULER_MPL_9DOF_Z = SensorMPU9X50.ObjectClusterSensorName.EULER_MPL_9DOF_Z;
			
			public static String MPL_HEADING = SensorMPU9X50.ObjectClusterSensorName.MPL_HEADING;
			public static String MPL_TEMPERATURE = SensorMPU9X50.ObjectClusterSensorName.MPL_TEMPERATURE;
			public static String MPL_PEDOM_CNT = SensorMPU9X50.ObjectClusterSensorName.MPL_PEDOM_CNT;
			public static String MPL_PEDOM_TIME = SensorMPU9X50.ObjectClusterSensorName.MPL_PEDOM_TIME;
			public static String TAPDIRANDTAPCNT = SensorMPU9X50.ObjectClusterSensorName.TAPDIRANDTAPCNT;
			public static String MOTIONANDORIENT = SensorMPU9X50.ObjectClusterSensorName.MOTIONANDORIENT;
			
			public static String GYRO_MPU_MPL_X = SensorMPU9X50.ObjectClusterSensorName.GYRO_MPU_MPL_X;
			public static String GYRO_MPU_MPL_Y = SensorMPU9X50.ObjectClusterSensorName.GYRO_MPU_MPL_Y;
			public static String GYRO_MPU_MPL_Z = SensorMPU9X50.ObjectClusterSensorName.GYRO_MPU_MPL_Z;
			public static String ACCEL_MPU_MPL_X = SensorMPU9X50.ObjectClusterSensorName.ACCEL_MPU_MPL_X;
			public static String ACCEL_MPU_MPL_Y = SensorMPU9X50.ObjectClusterSensorName.ACCEL_MPU_MPL_Y;
			public static String ACCEL_MPU_MPL_Z = SensorMPU9X50.ObjectClusterSensorName.ACCEL_MPU_MPL_Z;
			public static String MAG_MPU_MPL_X = SensorMPU9X50.ObjectClusterSensorName.MAG_MPU_MPL_X;
			public static String MAG_MPU_MPL_Y = SensorMPU9X50.ObjectClusterSensorName.MAG_MPU_MPL_Y;
			public static String MAG_MPU_MPL_Z = SensorMPU9X50.ObjectClusterSensorName.MAG_MPU_MPL_Z;
			public static String QUAT_DMP_6DOF_W = SensorMPU9X50.ObjectClusterSensorName.QUAT_DMP_6DOF_W;
			public static String QUAT_DMP_6DOF_X = SensorMPU9X50.ObjectClusterSensorName.QUAT_DMP_6DOF_X;
			public static String QUAT_DMP_6DOF_Y = SensorMPU9X50.ObjectClusterSensorName.QUAT_DMP_6DOF_Y;
			public static String QUAT_DMP_6DOF_Z = SensorMPU9X50.ObjectClusterSensorName.QUAT_DMP_6DOF_Z;

			public static String EVENT_MARKER = ShimmerStreamingProperties.ObjectClusterSensorName.EVENT_MARKER;//"Event_Marker"; 

//			public static String PPG_A12 = SensorPPG.ObjectClusterSensorName.PPG_A12;
//			public static String PPG_A13 = SensorPPG.ObjectClusterSensorName.PPG_A13;
//			public static String PPG1_A12 = SensorPPG.ObjectClusterSensorName.PPG1_A12;
//			public static String PPG1_A13 = SensorPPG.ObjectClusterSensorName.PPG1_A13;
//			public static String PPG2_A1 = SensorPPG.ObjectClusterSensorName.PPG2_A1;
//			public static String PPG2_A14 = SensorPPG.ObjectClusterSensorName.PPG2_A14;

			//TODO: move to algorithms class (JC).
			//Algorithms
			//TODO separate entries for LN accel vs. WR accel. 
			public static String QUAT_MADGE_6DOF_W = OrientationModule6DOF.ObjectClusterSensorName.QUAT_MADGE_6DOF_W;//"Quat_Madge_6DOF_W"; 
			public static String QUAT_MADGE_6DOF_X = OrientationModule6DOF.ObjectClusterSensorName.QUAT_MADGE_6DOF_X;//"Quat_Madge_6DOF_X"; 
			public static String QUAT_MADGE_6DOF_Y = OrientationModule6DOF.ObjectClusterSensorName.QUAT_MADGE_6DOF_Y;//"Quat_Madge_6DOF_Y"; 
			public static String QUAT_MADGE_6DOF_Z = OrientationModule6DOF.ObjectClusterSensorName.QUAT_MADGE_6DOF_Z;//"Quat_Madge_6DOF_Z"; 
			public static String QUAT_MADGE_9DOF_W = OrientationModule9DOF.ObjectClusterSensorName.QUAT_MADGE_9DOF_W;//"Quat_Madge_9DOF_W"; 
			public static String QUAT_MADGE_9DOF_X = OrientationModule9DOF.ObjectClusterSensorName.QUAT_MADGE_9DOF_X;//"Quat_Madge_9DOF_X"; 
			public static String QUAT_MADGE_9DOF_Y = OrientationModule9DOF.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y;//"Quat_Madge_9DOF_Y"; 
			public static String QUAT_MADGE_9DOF_Z = OrientationModule9DOF.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z;//"Quat_Madge_9DOF_Z";
			
			//axis angle 
			public static String AXIS_ANGLE_6DOF_A = OrientationModule6DOF.ObjectClusterSensorName.AXIS_ANGLE_6DOF_A;//"Axis_Angle_6DOF_A"; 
			public static String AXIS_ANGLE_6DOF_X = OrientationModule6DOF.ObjectClusterSensorName.AXIS_ANGLE_6DOF_X;//"Axis_Angle_6DOF_X"; 
			public static String AXIS_ANGLE_6DOF_Y = OrientationModule6DOF.ObjectClusterSensorName.AXIS_ANGLE_6DOF_Y;//"Axis_Angle_6DOF_Y"; 
			public static String AXIS_ANGLE_6DOF_Z = OrientationModule6DOF.ObjectClusterSensorName.AXIS_ANGLE_6DOF_Z;//"Axis_Angle_6DOF_Z"; 
			public static String AXIS_ANGLE_9DOF_A = OrientationModule9DOF.ObjectClusterSensorName.AXIS_ANGLE_9DOF_A;//"Axis_Angle_9DOF_A"; 
			public static String AXIS_ANGLE_9DOF_X = OrientationModule9DOF.ObjectClusterSensorName.AXIS_ANGLE_9DOF_X;//"Axis_Angle_9DOF_X"; 
			public static String AXIS_ANGLE_9DOF_Y = OrientationModule9DOF.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Y;//"Axis_Angle_9DOF_Y"; 
			public static String AXIS_ANGLE_9DOF_Z = OrientationModule9DOF.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Z;//"Axis_Angle_9DOF_Z"; 
			
			//Euler
			public static String EULER_6DOF_YAW = OrientationModule6DOF.ObjectClusterSensorName.EULER_6DOF_YAW;//"Euler_6DOF_Yaw"; 
			public static String EULER_6DOF_PITCH = OrientationModule6DOF.ObjectClusterSensorName.EULER_6DOF_PITCH;//"Euler_6DOF_Pitch"; 
			public static String EULER_6DOF_ROLL = OrientationModule6DOF.ObjectClusterSensorName.EULER_6DOF_ROLL;//"Euler_6DOF_Roll"; 
			public static String EULER_9DOF_YAW = OrientationModule9DOF.ObjectClusterSensorName.EULER_9DOF_YAW;//"Euler_9DOF_Yaw"; 
			public static String EULER_9DOF_PITCH = OrientationModule9DOF.ObjectClusterSensorName.EULER_9DOF_PITCH;//"Euler_9DOF_Pitch"; 
			public static String EULER_9DOF_ROLL = OrientationModule9DOF.ObjectClusterSensorName.EULER_9DOF_ROLL;//"Euler_9DOF_Roll"; 
			
			//TODO: axis angle 9DOF vs 6DOF??
//			public static String AXIS_ANGLE_A = "Axis_Angle_A"; 
//			public static String AXIS_ANGLE_X = "Axis_Angle_X"; 
//			public static String AXIS_ANGLE_Y = "Axis_Angle_Y"; 
//			public static String AXIS_ANGLE_Z = "Axis_Angle_Z"; 
			
//			// Moved by JC to algorithm module
//			public static String ECG_TO_HR_LA_RA = "ECGtoHR_LA-RA";
//			public static String ECG_TO_HR_LL_RA = "ECGtoHR_LL-RA";
//			public static String ECG_TO_HR_LL_LA = "ECGtoHR_LL_LA";
//			public static String ECG_TO_HR_VX_RL = "ECGtoHR_VX-RL";

			//TODO: remove two old channels names below
			public static String ECG_TO_HR_FW = SensorECGToHRFw.ObjectClusterSensorName.ECG_TO_HR_FW_GQ;//"ECGtoHR";

			public static  String PPG_TO_HR = "PPGtoHR";
			public static  String PPG_TO_HR1 = "PPGtoHR1";
			public static  String PPG_TO_HR2 = "PPGtoHR2";

		}
		
//		//Names used for parsing the GQ configuration header file 
//		public class HeaderFileSensorName{
//			public static final String SHIMMER3 = "shimmer3";
//			public static final String VBATT = "VBATT";
//			public static final String GSR = "GSR";
//			public static final String LSM303DLHC_ACCEL = "LSM303DLHC_ACCEL";
//		}
		
		public static class NEW_IMU_EXP_REV{
			public static final int GSR_UNIFIED = 3;		// >= SR48-3-0
			public static final int EXG_UNIFIED = 3;		// >= SR47-3-0 (SR48-2 was skipped)
			public static final int BRIDGE_AMP = 3;			// >= SR49-3-0 (SR49-2 was skipped)
			public static final int IMU = 6;				// >= SR31-6-0
			public static final int ANY_EXP_BRD_WITH_SPECIAL_REV = 171;	// == SRx-x-171 -> any expansion board attached to a new IMU base board
			public static final int PROTO3_DELUXE = 3;		// Future unified board
			public static final int PROTO3_MINI = 3;		// Future unified board
		}
		
		public static class CompatibilityInfoForMaps{
			
			// These can be used to enable/disble GUI options depending on what HW, FW, Expansion boards versions are present
			private static final ShimmerVerObject svoAnyIntExpBoardAndFw = 		new ShimmerVerObject(ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoAnyIntExpBoardAndSdlog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoAnyIntExpBoardAndBtStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoAnyIntExpBoardAndLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoAnyIntExpBoardAndLogAndStream3R = new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);

			private static final ShimmerVerObject svoNoIntExpBoardSdLog = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.NONE);

			private static final ShimmerVerObject svoSdLog = 				new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoSdLogMpl = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,10,1,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoBtStream = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,ShimmerVerDetails.ANY_VERSION);

			private static final ShimmerVerObject svoNewImuSdLog = 				new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,HW_ID_SR_CODES.SHIMMER3, NEW_IMU_EXP_REV.IMU);
			private static final ShimmerVerObject svoNewImuLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,HW_ID_SR_CODES.SHIMMER3, NEW_IMU_EXP_REV.IMU);
			private static final ShimmerVerObject svoNewImuAnyExpBrdSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, NEW_IMU_EXP_REV.ANY_EXP_BRD_WITH_SPECIAL_REV);
			private static final ShimmerVerObject svoNewImuAnyExpBrdLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, NEW_IMU_EXP_REV.IMU);
			private static final ShimmerVerObject svoShimmer3RImuLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,HW_ID_SR_CODES.SHIMMER3, NEW_IMU_EXP_REV.IMU);
			private static final ShimmerVerObject svoShimmer3RImuAnyExpBrdLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, NEW_IMU_EXP_REV.IMU);
		
			public static final ShimmerVerObject svoShimmer3RLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoShimmer3LogAndStreamWithSDLogSyncSupport = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,16,11,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoShimmer3RLogAndStreamWithSDLogSyncSupport = 		new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			
			private static final ShimmerVerObject svoShimmerGq802154Lr = 	new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_LR,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoShimmerGq802154Nr = 	new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_NR,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoShimmer2rGq = 			new ShimmerVerObject(HW_ID.SHIMMER_2R_GQ,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);

			private static final ShimmerVerObject svoExgSdLog = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_EXG); 
			private static final ShimmerVerObject svoExgUnifiedSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED);
			private static final ShimmerVerObject svoExgUnifiedNewImuSdLog = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, NEW_IMU_EXP_REV.EXG_UNIFIED);
			private static final ShimmerVerObject svoExgBtStream = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_EXG);
			private static final ShimmerVerObject svoExgUnifiedBtStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED);
			private static final ShimmerVerObject svoExgLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_EXG);
			private static final ShimmerVerObject svoExgUnifiedLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED);
			private static final ShimmerVerObject svoExgUnifiedNewImuLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, NEW_IMU_EXP_REV.EXG_UNIFIED);
			private static final ShimmerVerObject svoShimmer3RExgUnifiedLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED, NEW_IMU_EXP_REV.EXG_UNIFIED);

			private static final ShimmerVerObject svoGsrSdLog = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_GSR);
			private static final ShimmerVerObject svoGsrUnifiedSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED);
			private static final ShimmerVerObject svoGsrUnifiedNewImuSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, NEW_IMU_EXP_REV.GSR_UNIFIED);
			private static final ShimmerVerObject svoGsrBtStream = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_GSR);
			private static final ShimmerVerObject svoGsrUnifiedBtStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED);
			private static final ShimmerVerObject svoGsrLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_GSR);
			private static final ShimmerVerObject svoGsrUnifiedLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED);
			private static final ShimmerVerObject svoGsrUnifiedNewImuLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, NEW_IMU_EXP_REV.GSR_UNIFIED);
			private static final ShimmerVerObject svoGsrGqBle = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.GQ_BLE,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,HW_ID_SR_CODES.EXP_BRD_GSR);
			private static final ShimmerVerObject svoGsrUnifiedGqBle = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.GQ_BLE,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED);
			private static final ShimmerVerObject svoShimmer3RGsrUnifiedLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, NEW_IMU_EXP_REV.GSR_UNIFIED);
			private static final ShimmerVerObject svoShimmer3RGsrNewImuLogAndStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,HW_ID_SR_CODES.EXP_BRD_GSR);
			
			private static final ShimmerVerObject svoBrAmpSdLog = 			new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_BR_AMP);
			private static final ShimmerVerObject svoBrAmpUnifiedSdLog = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED);
			private static final ShimmerVerObject svoBrAmpUnifiedNewImuSdLog = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED, NEW_IMU_EXP_REV.BRIDGE_AMP);
			private static final ShimmerVerObject svoBrAmpBtStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_BR_AMP);
			private static final ShimmerVerObject svoBrAmpUnifiedBtStream = new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED);
			private static final ShimmerVerObject svoBrAmpLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_BR_AMP);
			private static final ShimmerVerObject svoBrAmpUnifiedLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED);
			private static final ShimmerVerObject svoBrAmpUnifiedNewImuLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED, NEW_IMU_EXP_REV.BRIDGE_AMP);
			private static final ShimmerVerObject svoShimmer3RBrAmpUnifiedLogAndStream = new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED, NEW_IMU_EXP_REV.BRIDGE_AMP);

			private static final ShimmerVerObject svoProto3MiniSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI);
			private static final ShimmerVerObject svoProto3MiniNewImuSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI, NEW_IMU_EXP_REV.PROTO3_MINI);
			private static final ShimmerVerObject svoProto3MiniBtStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI);
			private static final ShimmerVerObject svoProto3MiniLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI);
			private static final ShimmerVerObject svoProto3MiniNewImuLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI, NEW_IMU_EXP_REV.PROTO3_MINI);

			private static final ShimmerVerObject svoProto3DeluxeSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE);
			private static final ShimmerVerObject svoProto3DeluxeNewImuSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,15,1,HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, NEW_IMU_EXP_REV.PROTO3_DELUXE);
			private static final ShimmerVerObject svoProto3DeluxeBtStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE);
			private static final ShimmerVerObject svoProto3DeluxeLogAndStream =	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE);
			private static final ShimmerVerObject svoProto3DeluxeNewImuLogAndStream =	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,8,1,HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, NEW_IMU_EXP_REV.PROTO3_DELUXE);
			private static final ShimmerVerObject svoShimmer3RProto3DeluxeLogAndStream =new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, NEW_IMU_EXP_REV.PROTO3_DELUXE);

			private static final ShimmerVerObject svoAdxl377Accel200GSdLog = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,8,0,HW_ID_SR_CODES.EXP_BRD_ADXL377_ACCEL_200G);
			private static final ShimmerVerObject svoAdxl377Accel200GBtStream = 		new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.BTSTREAM,0,5,0,HW_ID_SR_CODES.EXP_BRD_ADXL377_ACCEL_200G);
			private static final ShimmerVerObject svoAdxl377Accel200GLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,3,3,HW_ID_SR_CODES.EXP_BRD_ADXL377_ACCEL_200G);
			private static final ShimmerVerObject svoShimmer3RAdxl377Accel200GLogAndStream = 	new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,HW_ID_SR_CODES.EXP_BRD_ADXL377_ACCEL_200G);

			//for using in shimmer test
			public static final ShimmerVerObject svoInShimmerTestLogAndStream =	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,16,7);
			public static final ShimmerVerObject svoInShimmerTestSdLog=	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.SDLOG,0,22,7);
			
			
			//TODO move non-Shimmer3 devices out of here
			public static final ShimmerVerObject svoShimmer4Stock = 			new ShimmerVerObject(HW_ID.SHIMMER_4_SDK,FW_ID.SHIMMER4_SDK_STOCK,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			private static final ShimmerVerObject svoArduino = 				new ShimmerVerObject(HW_ID.ARDUINO,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoSweatch = 				new ShimmerVerObject(HW_ID.SWEATCH,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoShimmerECGmd = 				new ShimmerVerObject(HW_ID.SHIMMER_3,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,HW_ID_SR_CODES.SHIMMER_ECG_MD);
			public static final ShimmerVerObject svoStrokare = 				new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.STROKARE,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);

			//TODO remove any lists below to their relevant Sensor classes.
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgGeneral = Arrays.asList(
					svoExgSdLog, svoExgBtStream, svoExgLogAndStream,  
					svoExgUnifiedSdLog, svoExgUnifiedBtStream, svoExgUnifiedLogAndStream,
					svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq,
					svoShimmerECGmd, svoShimmer4Stock, svoStrokare, svoShimmer3RExgUnifiedLogAndStream);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgEmg = Arrays.asList(
					svoExgSdLog, svoExgBtStream, svoExgLogAndStream,  
					svoExgUnifiedSdLog, svoExgUnifiedBtStream, svoExgUnifiedLogAndStream,
					svoShimmerECGmd, svoShimmer4Stock, svoStrokare, svoShimmer3RExgUnifiedLogAndStream);

			//TODO separate out GQ devices that are related to SensorEXG.sDRefEcgGq
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgEcg = Arrays.asList(
					svoExgSdLog, svoExgBtStream, svoExgLogAndStream,  
					svoExgUnifiedSdLog, svoExgUnifiedBtStream, svoExgUnifiedLogAndStream,
					svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq,
					svoShimmerECGmd, svoShimmer4Stock, svoShimmer3RExgUnifiedLogAndStream);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgEcgGq = Arrays.asList(
					svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgTest = Arrays.asList(
					svoExgSdLog, svoExgBtStream, svoExgLogAndStream,  
					svoExgUnifiedSdLog, svoExgUnifiedBtStream, svoExgUnifiedLogAndStream,
					svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq,
					svoShimmerECGmd, svoShimmer4Stock, svoShimmer3RExgUnifiedLogAndStream);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgThreeUnipolar = Arrays.asList(
					svoExgSdLog, svoExgBtStream, svoExgLogAndStream,  
					svoExgUnifiedSdLog, svoExgUnifiedBtStream, svoExgUnifiedLogAndStream,
					svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq,
					svoShimmerECGmd, svoShimmer4Stock, svoShimmer3RExgUnifiedLogAndStream);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExgRespiration = Arrays.asList(
					svoExgUnifiedSdLog, svoExgUnifiedBtStream, svoExgUnifiedLogAndStream,
					svoShimmer4Stock, svoShimmer3RExgUnifiedLogAndStream);

			private static final List<ShimmerVerObject> listOfCompatibleVersionInfoSdLog = Arrays.asList(svoSdLog);
			
			private static final List<ShimmerVerObject> listOfCompatibleVersionInfoAnyExpBoardAndFw = Arrays.asList(svoAnyIntExpBoardAndFw);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoAnyExpBoardStandardFW = Arrays.asList(
					svoAnyIntExpBoardAndSdlog,svoAnyIntExpBoardAndBtStream,svoAnyIntExpBoardAndLogAndStream,
					svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq, svoAnyIntExpBoardAndLogAndStream3R,
					svoShimmer4Stock, svoArduino, svoSweatch, svoStrokare); 

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoBattVoltage = Arrays.asList(
					svoAnyIntExpBoardAndSdlog,svoAnyIntExpBoardAndBtStream,svoAnyIntExpBoardAndLogAndStream,
					svoAnyIntExpBoardAndLogAndStream3R, svoShimmer4Stock,
					svoSweatch);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoGsr = Arrays.asList(
					svoGsrSdLog, svoGsrBtStream, svoGsrLogAndStream, svoGsrGqBle,
					svoGsrUnifiedSdLog,  svoGsrUnifiedBtStream, svoGsrUnifiedLogAndStream, 
					svoGsrUnifiedGqBle, svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq,
					svoShimmer4Stock, svoShimmer3RGsrUnifiedLogAndStream,svoShimmer3RGsrNewImuLogAndStream);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoBMP180 = Arrays.asList(
					svoAnyIntExpBoardAndSdlog,svoAnyIntExpBoardAndBtStream,svoAnyIntExpBoardAndLogAndStream,
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoBMP280 = Arrays.asList(
					svoNewImuSdLog, svoNewImuLogAndStream, svoShimmer3RLogAndStream, 
					svoNewImuAnyExpBrdSdLog, svoNewImuAnyExpBrdLogAndStream,
					svoGsrUnifiedNewImuSdLog, svoGsrUnifiedNewImuLogAndStream,
					svoExgUnifiedNewImuSdLog, svoExgUnifiedNewImuLogAndStream,
					svoBrAmpUnifiedNewImuSdLog, svoBrAmpUnifiedNewImuLogAndStream,
					svoProto3MiniNewImuSdLog, svoProto3MiniNewImuLogAndStream,
					svoProto3DeluxeNewImuSdLog, svoProto3DeluxeNewImuLogAndStream,
					svoShimmer4Stock);  
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoBMP390 = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,  
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream,
					svoShimmer4Stock);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoBMP581 = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoMPU9250 = Arrays.asList(
					svoNewImuSdLog, svoNewImuLogAndStream, svoShimmer3RLogAndStream,  
					svoNewImuAnyExpBrdSdLog, svoNewImuAnyExpBrdLogAndStream,
					svoGsrUnifiedNewImuSdLog, svoGsrUnifiedNewImuLogAndStream,
					svoExgUnifiedNewImuSdLog, svoExgUnifiedNewImuLogAndStream,
					svoBrAmpUnifiedNewImuSdLog, svoBrAmpUnifiedNewImuLogAndStream,
					svoProto3MiniNewImuSdLog, svoProto3MiniNewImuLogAndStream,
					svoProto3DeluxeNewImuSdLog, svoProto3DeluxeNewImuLogAndStream,
					svoShimmer4Stock);  

			//Shimmer3 WR Accel & Mag
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLSM303AH = Arrays.asList(
					svoNewImuSdLog, svoNewImuLogAndStream, svoShimmer3RLogAndStream,  
					svoNewImuAnyExpBrdSdLog, svoNewImuAnyExpBrdLogAndStream,
					svoGsrUnifiedNewImuSdLog, svoGsrUnifiedNewImuLogAndStream,
					svoExgUnifiedNewImuSdLog, svoExgUnifiedNewImuLogAndStream,
					svoBrAmpUnifiedNewImuSdLog, svoBrAmpUnifiedNewImuLogAndStream,
					svoProto3MiniNewImuSdLog, svoProto3MiniNewImuLogAndStream,
					svoProto3DeluxeNewImuSdLog, svoProto3DeluxeNewImuLogAndStream);
			
			//Shimmer3r Mag
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLIS2MDL = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,  
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream);
			
			//Shimmer3r WR Mag
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLIS3MDL = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,  
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream);
			
			//Shimmer3r High-G Accel
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoADXL371 = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,  
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream);
			
			//TODO need to update this list and remove "any"
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoKionixKXRB52042 = Arrays.asList(
					svoAnyIntExpBoardAndSdlog, svoAnyIntExpBoardAndBtStream, svoAnyIntExpBoardAndLogAndStream,
					svoShimmer4Stock);

			//Shimmer3 LN Accel
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoKionixKXTC92050 = Arrays.asList(
					svoNewImuSdLog, svoNewImuLogAndStream, svoShimmer3RLogAndStream,  
					svoNewImuAnyExpBrdSdLog, svoNewImuAnyExpBrdLogAndStream,
					svoGsrUnifiedNewImuSdLog, svoGsrUnifiedNewImuLogAndStream,
					svoExgUnifiedNewImuSdLog, svoExgUnifiedNewImuLogAndStream,
					svoBrAmpUnifiedNewImuSdLog, svoBrAmpUnifiedNewImuLogAndStream,
					svoProto3MiniNewImuSdLog, svoProto3MiniNewImuLogAndStream,
					svoProto3DeluxeNewImuSdLog, svoProto3DeluxeNewImuLogAndStream);
			
			//Shimmer3r LN Accel & Gyro
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLSM6DSV = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,  
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream);
			
			//Shimmer3r WR Accel
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLIS2DW12 = Arrays.asList(
					svoShimmer3RImuLogAndStream, svoShimmer3RLogAndStream,  
					svoShimmer3RImuAnyExpBrdLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RExgUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoBrAmp = Arrays.asList(
					svoBrAmpSdLog, svoBrAmpBtStream, svoBrAmpLogAndStream,  
					svoBrAmpUnifiedSdLog,  svoBrAmpUnifiedBtStream, svoBrAmpUnifiedLogAndStream,
					svoShimmer3RBrAmpUnifiedLogAndStream,
					svoShimmer4Stock);

			private static final List<ShimmerVerObject> listOfCompatibleVersionInfoProto3Mini = Arrays.asList(
					svoProto3MiniSdLog, svoProto3MiniBtStream, svoProto3MiniLogAndStream);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoProto3Deluxe = Arrays.asList(
					svoProto3DeluxeSdLog, svoProto3DeluxeBtStream, svoProto3DeluxeLogAndStream,
					svoShimmer3RProto3DeluxeLogAndStream,
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA1 = Arrays.asList(
					svoProto3MiniSdLog, svoProto3MiniBtStream, svoProto3MiniLogAndStream, 
					svoProto3DeluxeSdLog, svoProto3DeluxeBtStream, svoProto3DeluxeLogAndStream, 
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA12 = Arrays.asList(
					svoProto3MiniSdLog, svoProto3MiniBtStream, svoProto3MiniLogAndStream, 
					svoProto3DeluxeSdLog, svoProto3DeluxeBtStream, svoProto3DeluxeLogAndStream, 
					svoGsrSdLog, svoGsrBtStream, svoGsrLogAndStream, 
					svoGsrUnifiedSdLog, svoGsrUnifiedBtStream, svoGsrUnifiedLogAndStream,
					svoAdxl377Accel200GSdLog, svoAdxl377Accel200GBtStream, svoAdxl377Accel200GLogAndStream,
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA13 = Arrays.asList(
					svoProto3MiniSdLog, svoProto3MiniBtStream, svoProto3MiniLogAndStream, 
					svoProto3DeluxeSdLog, svoProto3DeluxeBtStream, svoProto3DeluxeLogAndStream, 
					svoGsrSdLog, svoGsrBtStream, svoGsrLogAndStream, 
					svoGsrUnifiedSdLog, svoGsrUnifiedBtStream, svoGsrUnifiedLogAndStream, 
					svoAdxl377Accel200GSdLog, svoAdxl377Accel200GBtStream, svoAdxl377Accel200GLogAndStream, 
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA14 = Arrays.asList(
					svoProto3MiniSdLog, svoProto3MiniBtStream, svoProto3MiniLogAndStream, 
					svoProto3DeluxeSdLog, svoProto3DeluxeBtStream, svoProto3DeluxeLogAndStream, 
					svoAdxl377Accel200GSdLog, svoAdxl377Accel200GBtStream, svoAdxl377Accel200GLogAndStream, 
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA10 = Arrays.asList(
					svoShimmer3RProto3DeluxeLogAndStream, 
					svoShimmer3RGsrNewImuLogAndStream, 
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RAdxl377Accel200GLogAndStream,
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA15 = Arrays.asList(
					svoShimmer3RProto3DeluxeLogAndStream, 
					svoShimmer3RGsrNewImuLogAndStream, 
					svoShimmer3RGsrUnifiedLogAndStream,
					svoShimmer3RAdxl377Accel200GLogAndStream,
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA16 = Arrays.asList(
					svoShimmer3RProto3DeluxeLogAndStream, 
					svoShimmer3RAdxl377Accel200GLogAndStream, 
					svoShimmer4Stock);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntExpA17 = Arrays.asList(
					svoShimmer3RProto3DeluxeLogAndStream, 
					svoShimmer3RAdxl377Accel200GLogAndStream, 
					svoShimmer4Stock);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoIntAdcsGeneral = Arrays.asList(
					svoGsrSdLog, svoGsrBtStream, svoGsrLogAndStream, 
					svoGsrUnifiedSdLog, svoGsrUnifiedBtStream, svoGsrUnifiedLogAndStream,
					svoShimmer3RGsrUnifiedLogAndStream, svoShimmer3RGsrNewImuLogAndStream, svoShimmer4Stock);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExtAdcs = Arrays.asList(
					svoAnyIntExpBoardAndSdlog, svoAnyIntExpBoardAndBtStream, svoAnyIntExpBoardAndLogAndStream,
					svoAnyIntExpBoardAndLogAndStream3R, svoShimmer4Stock);
			
//			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoExtAdcs = Arrays.asList(
//					svoNewImuSdLog, svoNewImuLogAndStream, 
//					svoNewImuAnyExpBrdSdLog, svoNewImuAnyExpBrdLogAndStream,
//					svoGsrSdLog, svoGsrBtStream, svoGsrLogAndStream, 
//					svoGsrUnifiedNewImuSdLog, svoGsrUnifiedNewImuLogAndStream,
//					svoExgUnifiedNewImuSdLog, svoExgUnifiedNewImuLogAndStream,
//					svoBrAmpUnifiedNewImuSdLog, svoBrAmpUnifiedNewImuLogAndStream,
//					svoProto3MiniNewImuSdLog, svoProto3MiniNewImuLogAndStream,
//					svoProto3DeluxeNewImuSdLog, svoProto3DeluxeNewImuLogAndStream,
//					svoGsrUnifiedGqBle, svoShimmerGq802154Lr, svoShimmerGq802154Nr, svoShimmer2rGq,
//					svoShimmer4Stock);

			private static final List<ShimmerVerObject> listOfCompatibleVersionInfoStreaming = Arrays.asList(
					svoBtStream, svoLogAndStream,
					svoShimmer4Stock);

			private static final List<ShimmerVerObject> listOfCompatibleVersionInfoLogging = Arrays.asList(
					svoSdLog, svoLogAndStream,
					svoShimmer4Stock, svoStrokare);
			
			private static final List<ShimmerVerObject> listOfCompatibleVersionInfoAdxl377Accel200G = Arrays.asList(
					svoAdxl377Accel200GSdLog,svoAdxl377Accel200GBtStream,svoAdxl377Accel200GLogAndStream);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoMPLSensors = Arrays.asList(svoSdLogMpl);//,baseShimmer4); //TODO Shimmer4 temp here
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoShimmer4 = Arrays.asList(svoShimmer4Stock);
			
		}


	    public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
	    static {
	        Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();

//			aMap.put(Configuration.Shimmer3.SENSOR_ID.HOST_SYSTEM_TIMESTAMP, ShimmerClock.sensorSystemTimeStampRef);
//	        aMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_TIMESTAMP, ShimmerClock.sensorShimmerClock);
//	        aMap.put(Configuration.Shimmer3.SENSOR_ID.HOST_SHIMMER_STREAMING_PROPERTIES, ShimmerClock.sensorShimmerPacketReception);
	        aMap.putAll(SensorShimmerClock.mSensorMapRef);

			aMap.putAll(SensorGSR.mSensorMapRef);
			aMap.putAll(SensorADC.mSensorMapRef);
			aMap.putAll(SensorBattVoltage.mSensorMapRef);
//			aMap.putAll(SensorBMP180.mSensorMapRef);
			aMap.putAll(SensorEXG.mSensorMapRef);
			// Derived Channels - Bridge Amp Board
			aMap.putAll(SensorBridgeAmp.mSensorMapRef);
			// Derived Channels - GSR Board/Proto3 Board
			aMap.putAll(SensorPPG.mSensorMapRef);

			mSensorMapRef = Collections.unmodifiableMap(aMap);
	    }
	    
	    public static final Map<Integer, SensorDetailsRef> mSensorMapRef3r;
	    static {
	        Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();

//			aMap.put(Configuration.Shimmer3.SENSOR_ID.HOST_SYSTEM_TIMESTAMP, ShimmerClock.sensorSystemTimeStampRef);
//	        aMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_TIMESTAMP, ShimmerClock.sensorShimmerClock);
//	        aMap.put(Configuration.Shimmer3.SENSOR_ID.HOST_SHIMMER_STREAMING_PROPERTIES, ShimmerClock.sensorShimmerPacketReception);
	        aMap.putAll(SensorShimmerClock.mSensorMapRef);

			aMap.putAll(SensorGSR.mSensorMapRef);
			aMap.putAll(SensorADC.mSensorMapRef3r);
			aMap.putAll(SensorBattVoltage.mSensorMapRef);
//			aMap.putAll(SensorBMP180.mSensorMapRef);
			aMap.putAll(SensorEXG.mSensorMapRef);
			// Derived Channels - Bridge Amp Board
			aMap.putAll(SensorBridgeAmp.mSensorMapRef);
			// Derived Channels - GSR Board/Proto3 Board
			aMap.putAll(SensorPPG.mSensorMapRef3r);

			mSensorMapRef3r = Collections.unmodifiableMap(aMap);
	    }
	    
	    
	    public static final Map<String, ChannelDetails> mChannelMapRef;
	    static {
	        Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
	        
			aMap.put(SensorBattVoltage.ObjectClusterSensorName.BATT_PERCENTAGE, SensorBattVoltage.channelBattPercentage);
	        
			aMap.put(ShimmerStreamingProperties.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT, SensorShimmerClock.channelReceptionRateCurrent);
			aMap.put(ShimmerStreamingProperties.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL, SensorShimmerClock.channelReceptionRateTrial);
			
			aMap.put(Configuration.Shimmer3.ObjectClusterSensorName.EVENT_MARKER, SensorShimmerClock.channelEventMarker);
			
			// All Information required for parsing each of the channels
			//TODO incorporate 3 byte timestamp change for newer firmware
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP, SensorShimmerClock.channelShimmerClock2byte);
			
			aMap.put(SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP, SensorShimmerClock.channelSystemTimestamp);
			aMap.put(SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP_PLOT, SensorShimmerClock.channelSystemTimestampPlot);
			
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK, SensorShimmerClock.channelRealTimeClock);
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.SHIMMER_CLOCK, SensorShimmerClock.channelShimmerClock3LSB);
//			aMap.put(ShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK, ShimmerClock.channelRealTimeClockSync);
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET, SensorShimmerClock.channelShimmerClockOffset);
//			aMap.putAll(ShimmerClock.mChannelMapRef);
			

			aMap.putAll(SensorBattVoltage.mChannelMapRef);
			aMap.putAll(SensorADC.mChannelMapRef);
			aMap.putAll(SensorBridgeAmp.mChannelMapRef);
			aMap.putAll(SensorGSR.mChannelMapRef);
			aMap.putAll(SensorEXG.mChannelMapRef);
			aMap.putAll(SensorPPG.mChannelMapRef);

			// Algorithm Channels
			aMap.put(Configuration.Shimmer3.ObjectClusterSensorName.ECG_TO_HR_FW, SensorECGToHRFw.channelEcgToHrFw);
			
			//TODO remove below
			aMap.put(Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,
					new ChannelDetails(
							Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,
							Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,
							DatabaseChannelHandles.PPG_TO_HR,
							CHANNEL_UNITS.BEATS_PER_MINUTE,
							Arrays.asList(CHANNEL_TYPE.CAL)));

			mChannelMapRef = Collections.unmodifiableMap(aMap);
	    }
	    
	    public static final Map<String, ChannelDetails> mChannelMapRef3r;
	    static {
	        Map<String, ChannelDetails> aMap = new LinkedHashMap<String, ChannelDetails>();
	        
			aMap.put(SensorBattVoltage.ObjectClusterSensorName.BATT_PERCENTAGE, SensorBattVoltage.channelBattPercentage);
	        
			aMap.put(ShimmerStreamingProperties.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT, SensorShimmerClock.channelReceptionRateCurrent);
			aMap.put(ShimmerStreamingProperties.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL, SensorShimmerClock.channelReceptionRateTrial);
			
			aMap.put(Configuration.Shimmer3.ObjectClusterSensorName.EVENT_MARKER, SensorShimmerClock.channelEventMarker);
			
			// All Information required for parsing each of the channels
			//TODO incorporate 3 byte timestamp change for newer firmware
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP, SensorShimmerClock.channelShimmerClock2byte);
			
			aMap.put(SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP, SensorShimmerClock.channelSystemTimestamp);
			aMap.put(SensorSystemTimeStamp.ObjectClusterSensorName.SYSTEM_TIMESTAMP_PLOT, SensorShimmerClock.channelSystemTimestampPlot);
			
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK, SensorShimmerClock.channelRealTimeClock);
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.SHIMMER_CLOCK, SensorShimmerClock.channelShimmerClock3LSB);
//			aMap.put(ShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK, ShimmerClock.channelRealTimeClockSync);
			aMap.put(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET, SensorShimmerClock.channelShimmerClockOffset);
//			aMap.putAll(ShimmerClock.mChannelMapRef);
			

			aMap.putAll(SensorBattVoltage.mChannelMapRef);
			aMap.putAll(SensorADC.mChannelMapRef3r);
			aMap.putAll(SensorBridgeAmp.mChannelMapRef);
			aMap.putAll(SensorGSR.mChannelMapRef);
			aMap.putAll(SensorEXG.mChannelMapRef);
			aMap.putAll(SensorPPG.mChannelMapRef3r);

			// Algorithm Channels
			aMap.put(Configuration.Shimmer3.ObjectClusterSensorName.ECG_TO_HR_FW, SensorECGToHRFw.channelEcgToHrFw);
			
			//TODO remove below
			aMap.put(Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,
					new ChannelDetails(
							Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,
							Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,
							DatabaseChannelHandles.PPG_TO_HR,
							CHANNEL_UNITS.BEATS_PER_MINUTE,
							Arrays.asList(CHANNEL_TYPE.CAL)));

			mChannelMapRef3r = Collections.unmodifiableMap(aMap);
	    }


	    public static final Map<Integer, SensorGroupingDetails> mSensorGroupingMapRef;
	    static {
	        Map<Integer, SensorGroupingDetails> aMap = new TreeMap<Integer, SensorGroupingDetails>();
		
			//Sensor Grouping for Configuration Panel 'tile' generation. 
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.STREAMING_PROPERTIES.ordinal(), SensorShimmerClock.sensorGroupStreamingProperties);

			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BATTERY_MONITORING.ordinal(), SensorBattVoltage.sensorGroupBattVoltage);
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.EXTERNAL_EXPANSION_ADC.ordinal(), SensorADC.sensorGroupExternalExpansionADCs);
			
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.GSR.ordinal(), new SensorGroupingDetails(
					SensorGSR.LABEL_SENSOR_TILE.GSR,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR,
								Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY),
	//							Configuration.Shimmer3.SENSOR_ID.PPG_A12,
	//							Configuration.Shimmer3.SENSOR_ID.PPG_A13)
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.EXG.ordinal(), new SensorGroupingDetails(
					SensorEXG.LABEL_SENSOR_TILE.EXG,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.HOST_ECG,
								Configuration.Shimmer3.SENSOR_ID.HOST_EMG,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoExgGeneral));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_MINI.ordinal(), new SensorGroupingDetails(
					SensorADC.LABEL_SENSOR_TILE.PROTO3_MINI,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoProto3Mini));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE.ordinal(), new SensorGroupingDetails(
					SensorADC.LABEL_SENSOR_TILE.PROTO3_DELUXE,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoProto3Deluxe));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.ordinal(), new SensorGroupingDetails(
					Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.getTileText(),
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY,
								Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoProto3Deluxe));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BRIDGE_AMPLIFIER.ordinal(), SensorBridgeAmp.sensorGroupBrAmp);
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BRIDGE_AMPLIFIER_SUPP.ordinal(), SensorBridgeAmp.sensorGroupBrAmpTemperature);
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.ADXL377_ACCEL_200G.ordinal(), new SensorGroupingDetails(
					Configuration.Shimmer3.LABEL_SENSOR_TILE.ADXL377_ACCEL_200G.getTileText(),
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12, //X-axis
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13, //Y-axis
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14), //Z-axis
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoAdxl377Accel200G)); //unused but accessible
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.INTERNAL_EXPANSION_ADC.ordinal(), SensorADC.sensorGroupInternalExpansionADCs);
			
			//Not implemented: GUI_LABEL_CHANNEL_GROUPING_GPS
			
			
			// For loop to automatically inherit associated channel configuration options from mSensorMap in the aMap
			for (SensorGroupingDetails sensorGroup:aMap.values()) {
//				 Ok to clear here because variable is initiated in the class
//				sensorGroup.mListOfConfigOptionKeysAssociated.clear();
//				List<ShimmerVerObject> listOfCompatibleVersionInfo = new ArrayList<ShimmerVerObject>();
				for (Integer sensor:sensorGroup.mListOfSensorIdsAssociated) {
					SensorDetailsRef sensorDetails = Configuration.Shimmer3.mSensorMapRef.get(sensor);
					if(sensorDetails!=null){
						List<String> associatedConfigOptions = sensorDetails.mListOfConfigOptionKeysAssociated;
						if (associatedConfigOptions != null) {
							for (String configOption : associatedConfigOptions) {
								// do not add duplicates
								if (!(sensorGroup.mListOfConfigOptionKeysAssociated.contains(configOption))) {
									sensorGroup.mListOfConfigOptionKeysAssociated.add(configOption);
								}
							}
						}
//						if(sensorDetails.mListOfCompatibleVersionInfo!=null){
//							listOfCompatibleVersionInfo.addAll(sensorDetails.mListOfCompatibleVersionInfo);
//						}
					}
				}
//				sensorGroup.mListOfCompatibleVersionInfo = listOfCompatibleVersionInfo;
			}
			
//			aMap.get(Configuration.Shimmer3.LABEL_SENSOR_TILE.GSR.ordinal()).mListOfConfigOptionKeysAssociated.add(Configuration.Shimmer3.GuiLabelConfig.PPG_ADC_SELECTION);
			aMap.get(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.ordinal()).mListOfConfigOptionKeysAssociated.add(SensorPPG.GuiLabelConfig.PPG1_ADC_SELECTION);
			aMap.get(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.ordinal()).mListOfConfigOptionKeysAssociated.add(SensorPPG.GuiLabelConfig.PPG2_ADC_SELECTION);
			
	        mSensorGroupingMapRef = Collections.unmodifiableMap(aMap);
	    }
	    
	    public static final Map<Integer, SensorGroupingDetails> mSensorGroupingMapRef3r;
	    static {
	        Map<Integer, SensorGroupingDetails> aMap = new TreeMap<Integer, SensorGroupingDetails>();
		
			//Sensor Grouping for Configuration Panel 'tile' generation. 
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.STREAMING_PROPERTIES.ordinal(), SensorShimmerClock.sensorGroupStreamingProperties);

			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BATTERY_MONITORING.ordinal(), SensorBattVoltage.sensorGroupBattVoltage);
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.EXTERNAL_EXPANSION_ADC.ordinal(), SensorADC.sensorGroupExternalExpansionADCs);
			
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.GSR.ordinal(), new SensorGroupingDetails(
					SensorGSR.LABEL_SENSOR_TILE.GSR,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR,
								Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY),
	//							Configuration.Shimmer3.SENSOR_ID.PPG_A12,
	//							Configuration.Shimmer3.SENSOR_ID.PPG_A13)
			CompatibilityInfoForMaps.listOfCompatibleVersionInfoGsr));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.EXG.ordinal(), new SensorGroupingDetails(
					SensorEXG.LABEL_SENSOR_TILE.EXG,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.HOST_ECG,
								Configuration.Shimmer3.SENSOR_ID.HOST_EMG,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION,
								Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoExgGeneral));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_MINI.ordinal(), new SensorGroupingDetails(
					SensorADC.LABEL_SENSOR_TILE.PROTO3_MINI,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoProto3Mini));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE.ordinal(), new SensorGroupingDetails(
					SensorADC.LABEL_SENSOR_TILE.PROTO3_DELUXE,
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13,
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoProto3Deluxe));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.ordinal(), new SensorGroupingDetails(
					Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.getTileText(),
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY,
								Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY),
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoProto3Deluxe));
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BRIDGE_AMPLIFIER.ordinal(), SensorBridgeAmp.sensorGroupBrAmp);
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.BRIDGE_AMPLIFIER_SUPP.ordinal(), SensorBridgeAmp.sensorGroupBrAmpTemperature);
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.ADXL377_ACCEL_200G.ordinal(), new SensorGroupingDetails(
					Configuration.Shimmer3.LABEL_SENSOR_TILE.ADXL377_ACCEL_200G.getTileText(),
					Arrays.asList(Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12, //X-axis
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13, //Y-axis
								Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14), //Z-axis
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoAdxl377Accel200G)); //unused but accessible
			aMap.put(Configuration.Shimmer3.LABEL_SENSOR_TILE.INTERNAL_EXPANSION_ADC.ordinal(), SensorADC.sensorGroupInternalExpansionADCs);
			
			//Not implemented: GUI_LABEL_CHANNEL_GROUPING_GPS
			
			
			// For loop to automatically inherit associated channel configuration options from mSensorMap in the aMap
			for (SensorGroupingDetails sensorGroup:aMap.values()) {
//				 Ok to clear here because variable is initiated in the class
//				sensorGroup.mListOfConfigOptionKeysAssociated.clear();
//				List<ShimmerVerObject> listOfCompatibleVersionInfo = new ArrayList<ShimmerVerObject>();
				for (Integer sensor:sensorGroup.mListOfSensorIdsAssociated) {
					SensorDetailsRef sensorDetails = Configuration.Shimmer3.mSensorMapRef3r.get(sensor);
					if(sensorDetails!=null){
						List<String> associatedConfigOptions = sensorDetails.mListOfConfigOptionKeysAssociated;
						if (associatedConfigOptions != null) {
							for (String configOption : associatedConfigOptions) {
								// do not add duplicates
								if (!(sensorGroup.mListOfConfigOptionKeysAssociated.contains(configOption))) {
									sensorGroup.mListOfConfigOptionKeysAssociated.add(configOption);
								}
							}
						}
//						if(sensorDetails.mListOfCompatibleVersionInfo!=null){
//							listOfCompatibleVersionInfo.addAll(sensorDetails.mListOfCompatibleVersionInfo);
//						}
					}
				}
//				sensorGroup.mListOfCompatibleVersionInfo = listOfCompatibleVersionInfo;
			}
			
//			aMap.get(Configuration.Shimmer3.LABEL_SENSOR_TILE.GSR.ordinal()).mListOfConfigOptionKeysAssociated.add(Configuration.Shimmer3.GuiLabelConfig.PPG_ADC_SELECTION);
			aMap.get(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.ordinal()).mListOfConfigOptionKeysAssociated.add(SensorPPG.GuiLabelConfig.PPG1_ADC_SELECTION);
			aMap.get(Configuration.Shimmer3.LABEL_SENSOR_TILE.PROTO3_DELUXE_SUPP.ordinal()).mListOfConfigOptionKeysAssociated.add(SensorPPG.GuiLabelConfig.PPG2_ADC_SELECTION);
			
	        mSensorGroupingMapRef3r = Collections.unmodifiableMap(aMap);
	    }

	    
	    public static final ConfigOptionDetailsSensor configOptionLowPowerAutoStop = new ConfigOptionDetailsSensor(
				Configuration.Shimmer3.GuiLabelConfig.LOW_POWER_AUTOSTOP,
				ShimmerDevice.DatabaseConfigHandle.LOW_POWER_AUTOSTOP,
				ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
				Arrays.asList(new ShimmerVerObject(FW_ID.LOGANDSTREAM, 0, 9, 6),
						new ShimmerVerObject(FW_ID.SDLOG, 0, 17, 3)));
	    
	    public static final Map<String, ConfigOptionDetailsSensor> mConfigOptionsMapRef;
	    static {
	        Map<String, ConfigOptionDetailsSensor> aMap = new LinkedHashMap<String, ConfigOptionDetailsSensor>();
	        
			// Assemble the channel configuration options map
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME,
					ShimmerDevice.DatabaseConfigHandle.SHIMMER_NAME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE,
					ShimmerDevice.DatabaseConfigHandle.SAMPLE_RATE,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.BUFFER_SIZE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.BUFFER_SIZE,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME,
					ShimmerDevice.DatabaseConfigHandle.CONFIG_TIME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME,
					ShimmerDevice.DatabaseConfigHandle.TRIAL_NAME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID,
					ShimmerDevice.DatabaseConfigHandle.TRIAL_ID,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoLogging));

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_NUMBER_OF_SHIMMERS, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_NUMBER_OF_SHIMMERS,
					ShimmerDevice.DatabaseConfigHandle.N_SHIMMER,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_ESTIMATED, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_ESTIMATED,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_MAXIMUM, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_MAXIMUM,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.BROADCAST_INTERVAL, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.BROADCAST_INTERVAL,
					ShimmerDevice.DatabaseConfigHandle.BROADCAST_INTERVAL,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					new ArrayList(){}));
			
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.BLUETOOTH_BAUD_RATE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.BLUETOOTH_BAUD_RATE,
					ShimmerDevice.DatabaseConfigHandle.BAUD_RATE,
					Configuration.Shimmer3.ListofBluetoothBaudRates, 
					Configuration.Shimmer3.ListofBluetoothBaudRatesConfigValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoStreaming));
			
			aMap.put(SensorGSR.GuiLabelConfig.GSR_RANGE, SensorGSR.configOptionGsrRange);

			aMap.put(SensorEXG.GuiLabelConfig.EXG_GAIN, SensorEXG.configOptionExgGain);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESOLUTION, SensorEXG.configOptionExgResolution);

			//Advanced ExG		
			aMap.put(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE, SensorEXG.configOptionExgRefElectrode);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_BYTES, SensorEXG.configOptionExgBytes);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RATE, SensorEXG.configOptionExgRate);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_DETECTION, SensorEXG.configOptionExgLeadOffDetection);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_CURRENT, SensorEXG.configOptionExgLeadOffCurrent);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_COMPARATOR, SensorEXG.configOptionExgLeadOffComparator);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESOLUTION, SensorEXG.configOptionExgResolution);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_FREQ, SensorEXG.configOptionExgRespirationDetectFreq);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE, SensorEXG.configOptionExgRespirationDetectPhase);

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START,
					ShimmerDevice.DatabaseConfigHandle.USER_BUTTON,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoLogging));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START,
					ShimmerDevice.DatabaseConfigHandle.SINGLE_TOUCH_START,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_MASTER_SHIMMER, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_MASTER_SHIMMER,
					ShimmerDevice.DatabaseConfigHandle.MASTER_CONFIG,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_SYNC_WHEN_LOGGING, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_SYNC_WHEN_LOGGING,
					ShimmerDevice.DatabaseConfigHandle.SYNC_CONFIG,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_LPM, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_LPM,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoAnyExpBoardStandardFW));
			
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX));

			aMap.put(SensorPPG.GuiLabelConfig.PPG_ADC_SELECTION, SensorPPG.configOptionPpgAdcSelection);
			aMap.put(SensorPPG.GuiLabelConfig.PPG1_ADC_SELECTION, SensorPPG.configOptionPpg1AdcSelection);
			aMap.put(SensorPPG.GuiLabelConfig.PPG2_ADC_SELECTION, SensorPPG.configOptionPpg2AdcSelection);
	        
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_RTC, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_RTC,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					Arrays.asList(new ShimmerVerObject(FW_ID.LOGANDSTREAM, 0, 7, 12),
							new ShimmerVerObject(FW_ID.SDLOG, 0, 11, 3))));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_SD, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_SD,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					Arrays.asList(new ShimmerVerObject(FW_ID.LOGANDSTREAM, 0, 7, 12))));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.TCXO, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.TCXO,
					ShimmerDevice.DatabaseConfigHandle.TXCO,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoAnyExpBoardAndFw));

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.LOW_POWER_AUTOSTOP, configOptionLowPowerAutoStop);

	        mConfigOptionsMapRef = Collections.unmodifiableMap(aMap);
	    }
	    
	    public static final Map<String, ConfigOptionDetailsSensor> mConfigOptionsMapRef3r;
	    static {
	        Map<String, ConfigOptionDetailsSensor> aMap = new LinkedHashMap<String, ConfigOptionDetailsSensor>();
	        
			// Assemble the channel configuration options map
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME,
					ShimmerDevice.DatabaseConfigHandle.SHIMMER_NAME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE,
					ShimmerDevice.DatabaseConfigHandle.SAMPLE_RATE,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.BUFFER_SIZE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.BUFFER_SIZE,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME,
					ShimmerDevice.DatabaseConfigHandle.CONFIG_TIME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME,
					ShimmerDevice.DatabaseConfigHandle.TRIAL_NAME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID,
					ShimmerDevice.DatabaseConfigHandle.TRIAL_ID,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoLogging));

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_NUMBER_OF_SHIMMERS, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_NUMBER_OF_SHIMMERS,
					ShimmerDevice.DatabaseConfigHandle.N_SHIMMER,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_ESTIMATED, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_ESTIMATED,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_MAXIMUM, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_MAXIMUM,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.BROADCAST_INTERVAL, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.BROADCAST_INTERVAL,
					ShimmerDevice.DatabaseConfigHandle.BROADCAST_INTERVAL,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					new ArrayList(){}));
			
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.BLUETOOTH_BAUD_RATE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.BLUETOOTH_BAUD_RATE,
					ShimmerDevice.DatabaseConfigHandle.BAUD_RATE,
					Configuration.Shimmer3.ListofBluetoothBaudRates, 
					Configuration.Shimmer3.ListofBluetoothBaudRatesConfigValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoStreaming));
			
			aMap.put(SensorGSR.GuiLabelConfig.GSR_RANGE, SensorGSR.configOptionGsrRange);

			aMap.put(SensorEXG.GuiLabelConfig.EXG_GAIN, SensorEXG.configOptionExgGain);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESOLUTION, SensorEXG.configOptionExgResolution);

			//Advanced ExG		
			aMap.put(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE, SensorEXG.configOptionExgRefElectrode);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_BYTES, SensorEXG.configOptionExgBytes);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RATE, SensorEXG.configOptionExgRate);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_DETECTION, SensorEXG.configOptionExgLeadOffDetection);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_CURRENT, SensorEXG.configOptionExgLeadOffCurrent);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_COMPARATOR, SensorEXG.configOptionExgLeadOffComparator);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESOLUTION, SensorEXG.configOptionExgResolution);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_FREQ, SensorEXG.configOptionExgRespirationDetectFreq);
			aMap.put(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE, SensorEXG.configOptionExgRespirationDetectPhase);

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START,
					ShimmerDevice.DatabaseConfigHandle.USER_BUTTON,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoLogging));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START,
					ShimmerDevice.DatabaseConfigHandle.SINGLE_TOUCH_START,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_MASTER_SHIMMER, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_MASTER_SHIMMER,
					ShimmerDevice.DatabaseConfigHandle.MASTER_CONFIG,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_SYNC_WHEN_LOGGING, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_SYNC_WHEN_LOGGING,
					ShimmerDevice.DatabaseConfigHandle.SYNC_CONFIG,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoSdLog));

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_LPM, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_LPM,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoAnyExpBoardStandardFW));
			
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX));

			aMap.put(SensorPPG.GuiLabelConfig.PPG_ADC_SELECTION, SensorPPG.configOptionPpgAdcSelection3r);
			aMap.put(SensorPPG.GuiLabelConfig.PPG1_ADC_SELECTION, SensorPPG.configOptionPpg1AdcSelection3r);
			aMap.put(SensorPPG.GuiLabelConfig.PPG2_ADC_SELECTION, SensorPPG.configOptionPpg2AdcSelection3r);
	        
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_RTC, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_RTC,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					Arrays.asList(new ShimmerVerObject(FW_ID.LOGANDSTREAM, 0, 7, 12),
							new ShimmerVerObject(FW_ID.SDLOG, 0, 11, 3))));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_SD, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_SD,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					Arrays.asList(new ShimmerVerObject(FW_ID.LOGANDSTREAM, 0, 7, 12))));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.TCXO, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.TCXO,
					ShimmerDevice.DatabaseConfigHandle.TXCO,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					CompatibilityInfoForMaps.listOfCompatibleVersionInfoAnyExpBoardAndFw));

			aMap.put(Configuration.Shimmer3.GuiLabelConfig.LOW_POWER_AUTOSTOP, configOptionLowPowerAutoStop);

	        mConfigOptionsMapRef3r = Collections.unmodifiableMap(aMap);
	    }
	}
	
	public static class ShimmerGq802154{
//		public class SENSOR_ID{
//			public static final int GSR = 1;
//			public static final int ECGtoHR = 2;
//		}
		
		//GUI AND EXPORT CHANNELS
		public static class ObjectClusterSensorName{
			//Related to the Spectrum Analyser
			public static final String POWER_CALC_85 = "SpanManager-85";//"Results-85";
			public static final String POWER_CALC_15 = "SpanManager-15";//"Results-15";
			public static final String FREQUENCY = "Frequency";
			public static final String POWER = "Power";
			
			public static final String RADIO_RECEPTION = "Radio_Reception";
		}
	}

	public static class ShimmerGqBle{
		public class SENSOR_ID{
			public static final int MPU9150_ACCEL = 17; // SensorMPU9X50.SENSOR_ID.MPU9150_ACCEL;
			public static final int MPU9150_GYRO =  1; // SensorMPU9X50.SENSOR_ID.MPU9150_GYRO;
			public static final int MPU9150_MAG =  18; // SensorMPU9X50.SENSOR_ID.MPU9150_MAG;
			public static final int VBATT = 10;
			public static final int LSM303DLHC_ACCEL = 11;  //XXX-RS-LSM-SensorClass?
			public static final int PPG = 107;
			public static final int GSR = 5;
			public static final int BEACON = 108;
		}

		// GUI Sensor Tiles
		public enum LABEL_SENSOR_TILE{
			GYRO(Configuration.ShimmerGqBle.GuiLabelSensors.GYRO),
			MAG(Configuration.ShimmerGqBle.GuiLabelSensors.MAG), //XXX-RS-LSM-SensorClass?
			BATTERY_MONITORING(Configuration.ShimmerGqBle.GuiLabelSensors.BATTERY),
			WIDE_RANGE_ACCEL(Configuration.ShimmerGqBle.GuiLabelSensors.ACCEL_WR),  //XXX-RS-LSM-SensorClass?
			GSR("GSR+"),
			BEACON(Configuration.ShimmerGqBle.GuiLabelSensors.BEACON);
			
			private String tileText = "";
			
			LABEL_SENSOR_TILE(String text){
				this.tileText = text;
			}
			
			public String getTileText(){
				return tileText;
			}
		}
		
		//GUI SENSORS
		public class GuiLabelSensors{
			public static final String BATTERY = SensorBattVoltage.GuiLabelSensors.BATTERY;
			public static final String GSR = SensorGSR.GuiLabelSensors.GSR;
			public static final String GYRO = SensorMPU9X50.GuiLabelSensors.GYRO;
			public static final String ACCEL_WR = SensorLSM303DLHC.GuiLabelSensors.ACCEL_WR;
			public static final String MAG = SensorLSM303DLHC.GuiLabelSensors.MAG;
			public static final String ACCEL_MPU = SensorMPU9X50.GuiLabelSensors.ACCEL_MPU;
			public static final String MAG_MPU = SensorMPU9X50.GuiLabelSensors.MAG_MPU;
			public static final String PPG_TO_HR = "PPG To HR";
			public static final String PPG = SensorPPG.GuiLabelSensors.PPG;
			public static final String BEACON = "Beacon";
		}
		
		public class GuiLabelConfig{
			public static final String SAMPLING_RATE_DIVIDER_VBATT = SensorBattVoltage.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT; //"VBATT Divider";
			public static final String SAMPLING_RATE_DIVIDER_GSR = SensorGSR.GuiLabelConfig.SAMPLING_RATE_DIVIDER_GSR; //"GSR Divider";
			public static final String SAMPLING_RATE_DIVIDER_PPG = SensorPPG.GuiLabelConfig.SAMPLING_RATE_DIVIDER_PPG; //"PPG Divider";
			public static final String SAMPLING_RATE_DIVIDER_LSM303DLHC_ACCEL = "LSM303DLHC Divider"; //XXX-RS-LSM-SensorClass?
			public static final String LSM303DLHC_ACCEL_RATE = SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RATE;
			public static final String LSM303DLHC_ACCEL_RANGE = SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RANGE;
			public static final String GSR_RANGE = SensorGSR.GuiLabelConfig.GSR_RANGE; // "GSR Range";
			public static final String LSM303DLHC_ACCEL_LPM = SensorLSM303.GuiLabelConfig.LSM303_ACCEL_LPM;
			public static final String SAMPLING_RATE_DIVIDER_BEACON = "Beacon Divider";
			
			public static final String SHIMMER_USER_ASSIGNED_NAME = "Shimmer Name";
			public static final String EXPERIMENT_NAME = "Experiment Name";
			public static final String SHIMMER_SAMPLING_RATE = "Sampling Rate";
			public static final String CONFIG_TIME = "Config Time";
			public static final String SHIMMER_MAC_FROM_INFOMEM = "InfoMem MAC";
		}
		
		public static final String[] ListofSamplingRateDividers={"0.75Hz","1.5Hz","3Hz","7.5Hz","15Hz","30Hz","75Hz","220Hz"};
		public static final Integer[] ListofSamplingRateDividersValues={0,1,2,3,4,5,6,7};
		
		
		public static class CompatibilityInfoForMaps{
			// These can be used to enable/disble GUI options depending on what HW, FW, Expansion boards versions are present
			private static final ShimmerVerObject svoAnyIntExpBoardAndFw = new ShimmerVerObject(HW_ID.SHIMMER_GQ_BLE,FW_ID.GQ_BLE,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoGq = Arrays.asList(svoAnyIntExpBoardAndFw);
		}
		
		
		// These can be used to enable/disble GUI options depending on what HW, FW, Expansion boards versions are present
	    public static final Map<Integer, SensorDetailsRef> mSensorMapRef;
	    static {
	        Map<Integer, SensorDetailsRef> aMap = new LinkedHashMap<Integer, SensorDetailsRef>();

//	        aMap.put(Configuration.ShimmerGQ.SENSOR_ID.SHIMMER_STREAMING_PROPERTIES, new SensorDetails(0, 0, "Device Properties"));

			aMap.put(Configuration.ShimmerGqBle.SENSOR_ID.VBATT, new SensorDetailsRef(0, 0, Configuration.ShimmerGqBle.GuiLabelSensors.BATTERY));
			aMap.put(Configuration.ShimmerGqBle.SENSOR_ID.LSM303DLHC_ACCEL, new SensorDetailsRef(0, 0, Configuration.ShimmerGqBle.GuiLabelSensors.ACCEL_WR)); //XXX-RS-LSM-SensorClass?
			aMap.put(Configuration.ShimmerGqBle.SENSOR_ID.GSR, new SensorDetailsRef(0, 0, Configuration.ShimmerGqBle.GuiLabelSensors.GSR));
			aMap.put(Configuration.ShimmerGqBle.SENSOR_ID.BEACON, new SensorDetailsRef(0, 0, Configuration.ShimmerGqBle.GuiLabelSensors.BEACON));
			aMap.put(Configuration.ShimmerGqBle.SENSOR_ID.PPG, new SensorDetailsRef(0, 0, Configuration.ShimmerGqBle.GuiLabelSensors.PPG));
			
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.VBATT).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.LSM303DLHC_ACCEL).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;  //XXX-RS-LSM-SensorClass?
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.GSR).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.BEACON).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.PPG).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;
			
			// Associated config options for each channel (currently used for the ChannelTileMap)
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.VBATT).mListOfConfigOptionKeysAssociated = Arrays.asList(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT);
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.LSM303DLHC_ACCEL).mListOfConfigOptionKeysAssociated = Arrays.asList(  //XXX-RS-LSM-SensorClass?
					Configuration.ShimmerGqBle.GuiLabelConfig.LSM303DLHC_ACCEL_RANGE,
					Configuration.ShimmerGqBle.GuiLabelConfig.LSM303DLHC_ACCEL_RATE,
					Configuration.ShimmerGqBle.GuiLabelConfig.LSM303DLHC_ACCEL_LPM,
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_LSM303DLHC_ACCEL);
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.GSR).mListOfConfigOptionKeysAssociated = Arrays.asList(
					Configuration.ShimmerGqBle.GuiLabelConfig.GSR_RANGE,
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_GSR);
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.BEACON).mListOfConfigOptionKeysAssociated = Arrays.asList(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_BEACON);
			aMap.get(Configuration.ShimmerGqBle.SENSOR_ID.PPG).mListOfConfigOptionKeysAssociated = Arrays.asList(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_PPG);
			mSensorMapRef = Collections.unmodifiableMap(aMap);
	    }

		
	    public static final Map<Integer, SensorGroupingDetails> mSensorGroupingMapRef;
	    static {
	        Map<Integer, SensorGroupingDetails> aMap = new TreeMap<Integer, SensorGroupingDetails>();

			//Sensor Grouping for Configuration Panel 'tile' generation. 
	        aMap.put(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.BATTERY_MONITORING.ordinal(), new SensorGroupingDetails(
	        		Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.BATTERY_MONITORING.getTileText(),
	        		Arrays.asList(Configuration.ShimmerGqBle.SENSOR_ID.VBATT)));
	        aMap.put(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.WIDE_RANGE_ACCEL.ordinal(), new SensorGroupingDetails(   //XXX-RS-LSM-SensorClass?
	        		Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.WIDE_RANGE_ACCEL.getTileText(),
	        		Arrays.asList(Configuration.ShimmerGqBle.SENSOR_ID.LSM303DLHC_ACCEL)));
	        aMap.put(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.GSR.ordinal(), new SensorGroupingDetails(
	        		Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.GSR.getTileText(),
					Arrays.asList(Configuration.ShimmerGqBle.SENSOR_ID.GSR,
								Configuration.ShimmerGqBle.SENSOR_ID.PPG)));
	        aMap.put(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.BEACON.ordinal(), new SensorGroupingDetails(
	        		Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.BEACON.getTileText(), 
	        		Arrays.asList(Configuration.ShimmerGqBle.SENSOR_ID.BEACON)));
		
			// ChannelTiles that have compatibility considerations (used to auto generate tiles in GUI)
			aMap.get(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.BATTERY_MONITORING).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;;
			aMap.get(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.WIDE_RANGE_ACCEL).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;;   //XXX-RS-LSM-SensorClass?
			aMap.get(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.GSR).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;; 
			aMap.get(Configuration.ShimmerGqBle.LABEL_SENSOR_TILE.BEACON).mListOfCompatibleVersionInfo = Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq;;
			
			// For loop to automatically inherit associated channel configuration options from ChannelMap in the ChannelTileMap
			for (SensorGroupingDetails channelGroup:aMap.values()) {
				// Ok to clear here because variable is initiated in the class
				channelGroup.mListOfConfigOptionKeysAssociated.clear();
				for (Integer channel : channelGroup.mListOfSensorIdsAssociated) {
					if(mSensorMapRef.containsKey(channel)){
						List<String> associatedConfigOptions = mSensorMapRef.get(channel).mListOfConfigOptionKeysAssociated;
						if (associatedConfigOptions != null) {
							for (String configOption : associatedConfigOptions) {
								// do not add duplicates
								if (!(channelGroup.mListOfConfigOptionKeysAssociated.contains(configOption))) {
									channelGroup.mListOfConfigOptionKeysAssociated.add(configOption);
								}
							}
						}
					}
				}
			}
			
	        mSensorGroupingMapRef = Collections.unmodifiableMap(aMap);
	    }

		// Assemble the channel configuration options map
	    public static final Map<String, ConfigOptionDetailsSensor> mConfigOptionsMapRef;
	    static {
	        Map<String, ConfigOptionDetailsSensor> aMap = new LinkedHashMap<String, ConfigOptionDetailsSensor>();

			aMap = new HashMap<String,ConfigOptionDetailsSensor>();
			
	//		aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME, 
	//				new ConfigOptionDetailsSensor(ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
	//						listOfCompatibleVersionInfoGq));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE,
					ShimmerDevice.DatabaseConfigHandle.SAMPLE_RATE,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
	//		aMap.put(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM, 
	//				new ConfigOptionDetailsSensor(ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME,
					ShimmerDevice.DatabaseConfigHandle.CONFIG_TIME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME,
					ShimmerDevice.DatabaseConfigHandle.TRIAL_NAME,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
					Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq));
	//		aMap.put(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID, 
	//				new ConfigOptionDetailsSensor(ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.TEXTFIELD,
	//						Configuration.ShimmerGQ.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq));
			
			aMap.put(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RANGE, SensorLSM303DLHC.configOptionAccelRange);
			aMap.put(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RATE, SensorLSM303DLHC.configOptionAccelRate);
	
			aMap.put(SensorGSR.GuiLabelConfig.GSR_RANGE, SensorGSR.configOptionGsrRange);  

			aMap.put(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_GSR, new ConfigOptionDetailsSensor(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_GSR,
					null,
					Configuration.ShimmerGqBle.ListofSamplingRateDividers, 
					Configuration.ShimmerGqBle.ListofSamplingRateDividersValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX));
			//XXX-RS-LSM-SensorClass?
			aMap.put(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_LSM303DLHC_ACCEL, new ConfigOptionDetailsSensor(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_LSM303DLHC_ACCEL,
					null,
					Configuration.ShimmerGqBle.ListofSamplingRateDividers, 
					Configuration.ShimmerGqBle.ListofSamplingRateDividersValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX));
			aMap.put(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_PPG, new ConfigOptionDetailsSensor(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_PPG,
					null,
					Configuration.ShimmerGqBle.ListofSamplingRateDividers, 
					Configuration.ShimmerGqBle.ListofSamplingRateDividersValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX));
			aMap.put(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT, new ConfigOptionDetailsSensor(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT,
					null,
					Configuration.ShimmerGqBle.ListofSamplingRateDividers, 
					Configuration.ShimmerGqBle.ListofSamplingRateDividersValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX));
			aMap.put(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_BEACON, new ConfigOptionDetailsSensor(
					Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_BEACON,
					null,
					Configuration.ShimmerGqBle.ListofSamplingRateDividers, 
					Configuration.ShimmerGqBle.ListofSamplingRateDividersValues, 
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX));
	
		    aMap.put(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER, new ConfigOptionDetailsSensor(
		    		Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER,
					ShimmerDevice.DatabaseConfigHandle.EXP_PWR,
		    		Configuration.ShimmerDeviceCommon.ListOfOnOff, 
	    	        Configuration.ShimmerDeviceCommon.ListOfOnOffConfigValues,
	    	        ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX));
			
			//General Config
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START,
					ShimmerDevice.DatabaseConfigHandle.USER_BUTTON,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq));
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START,
					ShimmerDevice.DatabaseConfigHandle.SINGLE_TOUCH_START,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX,
					Configuration.ShimmerGqBle.CompatibilityInfoForMaps.listOfCompatibleVersionInfoGq));
	
			aMap.put(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_LPM, SensorLSM303DLHC.configOptionAccelLpm);
			
			aMap.put(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN, new ConfigOptionDetailsSensor(
					Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN,
					null,
					ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX));

			mConfigOptionsMapRef = Collections.unmodifiableMap(aMap);
	    }
	}
	
	
	public static class Shimmer4{
		public class GuiLabelConfig{
			public static final String INT_EXP_BRD_POWER_BOOLEAN = Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN;
			public static final String INT_EXP_BRD_POWER_INTEGER = Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER;
		}
		
		public static final ConfigOptionDetailsSensor configOptionIntExpBrdPowerInteger = new ConfigOptionDetailsSensor(
	    		Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER,
				ShimmerDevice.DatabaseConfigHandle.EXP_PWR,
				Configuration.ShimmerDeviceCommon.ListOfOnOff, 
				Configuration.ShimmerDeviceCommon.ListOfOnOffConfigValues, 
				ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.COMBOBOX);

		public static final ConfigOptionDetailsSensor configOptionIntExpBrdPowerBoolean = new ConfigOptionDetailsSensor(
				Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN,
				null,
				ConfigOptionDetailsSensor.GUI_COMPONENT_TYPE.CHECKBOX);
		
		
		// Assemble the channel configuration options map
	    public static final Map<String, ConfigOptionDetailsSensor> mConfigOptionsMapRef;
	    static {
	        Map<String, ConfigOptionDetailsSensor> aMap = new LinkedHashMap<String, ConfigOptionDetailsSensor>();

			aMap = new HashMap<String,ConfigOptionDetailsSensor>();

		    aMap.put(GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER, configOptionIntExpBrdPowerInteger);
		    aMap.put(GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN, configOptionIntExpBrdPowerBoolean);
			mConfigOptionsMapRef = Collections.unmodifiableMap(aMap);
	    }
	}

	
	public static final class Arduino {
		
		public static enum LABEL_SENSOR_TILE{
			ANALOG_IN("Analog In"),//SensorArduino.LABEL_SENSOR_TILE.ANALOG_IN),
			DIGITAL_IN("Digital In");//SensorArduino.LABEL_SENSOR_TILE.DIGITAL_IN);
			
			private String tileText = "";
			LABEL_SENSOR_TILE(String text){
				this.tileText = text;
			}
			
			public String getTileText(){
				return tileText;
			}
		}
	}

	public static final class ShimmerDeviceCommon {

		public static final String[] ListOfOnOff = {"On","Off"};
		public static final Integer[] ListOfOnOffConfigValues = {0x01,0x00};
	}

	public static final class Sweatch {
		public class SENSOR_ID {
			public static final int SWEATCH_ADC = 1005;
		}
	}

	public static class Webcam{
		public static class CompatibilityInfoForMaps{
			public static final ShimmerVerObject svoWebcamGeneric = 		new ShimmerVerObject(HW_ID.WEBCAM_GENERIC,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
//			private static final ShimmerVerObject svoWebcamLogitechC920 = 	new ShimmerVerObject(HW_ID.WEBCAM_LOGITECH_HD_C920,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
//			private static final ShimmerVerObject svoWebcamLogitechC930e = 	new ShimmerVerObject(HW_ID.WEBCAM_LOGITECH_HD_C930E,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
//			protected static final ShimmerVerObject svoWebcamGeneric = new ShimmerVerObject(HW_ID.WEBCAM_GENERIC, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoWebcamLogitechHdC920 = 	new ShimmerVerObject(HW_ID.WEBCAM_LOGITECH_HD_C920, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoWebcamLogitechHdC930E = new ShimmerVerObject(HW_ID.WEBCAM_LOGITECH_HD_C930E, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoWebcamDigiOptixSmartGlasses = new ShimmerVerObject(HW_ID.WEBCAM_DIGIOPTIX_SMART_GLASSES, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoWebcam = Arrays.asList(
					svoWebcamGeneric, svoWebcamLogitechHdC920, svoWebcamLogitechHdC930E);

		}
	}
	
	public static final class Verisense {
		
		public class SensorBitmap {
			// LSB byte 0
			public static final int LIS2DW12_ACCEL			= 1 << (7 + (8*0));
			public static final int LSM6DS3_ACCEL			= 1 << (6 + (8*0));
			public static final int LSM6DS3_GYRO			= 1 << (5 + (8*0));
			
			// Byte 1
			public static final int GSR	 					= 1 << (7 + (8*1));
			public static final int MAX86916_PPG_GREEN		= 1 << (6 + (8*1));
			public static final int MAX86XXX_PPG_RED		= 1 << (5 + (8*1));
			public static final int MAX86XXX_PPG_IR			= 1 << (4 + (8*1));
			public static final int MAX86150_ECG			= 1 << (3 + (8*1));
			public static final int MAX86916_PPG_BLUE		= 1 << (2 + (8*1));
			public static final int VBATT					= 1 << (1 + (8*1));
		}
		
		public class DerivedSensorsBitMask {
			public final static int NON_WEAR_DETECTION_LIS2DW12	= (1 << 0);
			public final static int NON_WEAR_DETECTION_LSM6DS3	= (1 << 1);
			public final static int PPG_TO_HR_RED_LED		= (1 << 2);
			public final static int PPG_TO_HR_IR_LED		= (1 << 3);
			public final static int PPG_TO_HR_GREEN_LED		= (1 << 4);
			public final static int PPG_TO_HR_BLUE_LED		= (1 << 5);
			public final static int PPG_TO_SPO2				= (1 << 6);
			public final static int GYRO_ON_THE_FLY_CAL		= (1 << 7);
			public final static int ORIENTATION_6DOF_QUAT 	= (1 << 8);
			public final static int ORIENTATION_6DOF_EULER 	= (1 << 9);
		}

		public class SENSOR_ID {
			public static final int VERISENSE_TIMESTAMP		= 2000;
			//TODO not sure if there is a need for an offset here -> it was just copied from Sweatch implementation above
			public static final int LIS2DW12_ACCEL 			= 2005;
			public static final int LSM6DS3_GYRO 			= 2006;
			public static final int LSM6DS3_ACCEL 			= 2007;
			public static final int MAX86XXX_PPG_RED 		= 2008;
			public static final int MAX86XXX_PPG_IR 		= 2009;
			public static final int MAX86150_ECG 			= 2010;
			public static final int MAX86916_PPG_GREEN 		= 2011;
			public static final int MAX86916_PPG_BLUE 		= 2012;
			public static final int VBATT			 		= 2013;
			public static final int GSR				 		= 2014;
		}
		
		public enum LABEL_SENSOR_TILE{
			ACCEL(SensorLIS2DW12.LABEL_SENSOR_TILE.ACCEL),
			GSR(SensorGSRVerisense.LABEL_SENSOR_TILE.GSR),
			PPG(SensorMAX86916.LABEL_SENSOR_TILE.PPG),
			VBATT(SensorBattVoltageVerisense.LABEL_SENSOR_TILE.BATTERY_MONITORING),
			ACCEL2_GYRO(SensorLSM6DS3.LABEL_SENSOR_TILE.ACCEL2_GYRO);
			
			private String tileText = "";
			LABEL_SENSOR_TILE(String text){
				this.tileText = text;
			}
			
			public String getTileText(){
				return tileText;
			}
		}
		
		public static class CompatibilityInfoForMaps{
			public static final ShimmerVerObject svoVerisenseDevBrd = 	new ShimmerVerObject(HW_ID.VERISENSE_DEV_BRD,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoVerisenseImu = 		new ShimmerVerObject(HW_ID.VERISENSE_IMU,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoVerisenseGsrPlus = 		new ShimmerVerObject(HW_ID.VERISENSE_GSR_PLUS,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			public static final ShimmerVerObject svoVerisensePpg0 =		new ShimmerVerObject(HW_ID.VERISENSE_PPG,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,HW_ID.VERISENSE_PPG,0);
			public static final ShimmerVerObject svoVerisensePpg1 =		new ShimmerVerObject(HW_ID.VERISENSE_PPG,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,HW_ID.VERISENSE_PPG,1);
			public static final ShimmerVerObject svoVerisensePulsePlus = new ShimmerVerObject(HW_ID.VERISENSE_PULSE_PLUS,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLIS2DW12 = Arrays.asList(
					svoVerisenseDevBrd, svoVerisenseImu, svoVerisensePpg0, svoVerisensePpg1, svoVerisenseGsrPlus, svoVerisensePulsePlus);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoLSM6DS3 = Arrays.asList(
					svoVerisenseDevBrd, svoVerisenseImu, svoVerisensePpg0, svoVerisensePpg1, svoVerisenseGsrPlus);

			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoMAX86150 = Arrays.asList(
					svoVerisensePpg0);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoMAX86916 = Arrays.asList(
					svoVerisenseDevBrd, svoVerisensePpg1, svoVerisensePulsePlus);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoVbatt = Arrays.asList(
					svoVerisenseDevBrd, svoVerisenseImu, svoVerisensePpg0, svoVerisensePpg1, svoVerisenseGsrPlus, svoVerisensePulsePlus);
			
			public static final List<ShimmerVerObject> listOfCompatibleVersionInfoGsr = Arrays.asList(
					svoVerisenseGsrPlus, svoVerisensePulsePlus);
		}

	}
	
}


