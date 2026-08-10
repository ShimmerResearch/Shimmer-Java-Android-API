package com.shimmerresearch.driver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.shimmerresearch.algorithms.AbstractAlgorithm;
import com.shimmerresearch.algorithms.gyroOnTheFlyCal.GyroOnTheFlyCalModule;
import com.shimmerresearch.algorithms.gyroOnTheFlyCal.OnTheFlyGyroOffsetCal;
import com.shimmerresearch.comms.wiredProtocol.UartComponentPropertyDetails;
import com.shimmerresearch.comms.wiredProtocol.UartPacketDetails.UART_COMPONENT_AND_PROPERTY;
import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer2;
import com.shimmerresearch.driver.Configuration.Shimmer3;
import com.shimmerresearch.driver.Configuration.Shimmer3.DerivedSensorsBitMask;
import com.shimmerresearch.driver.calibration.CalibDetails;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic;
import com.shimmerresearch.driver.calibration.UtilCalibration;
import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;
import com.shimmerresearch.driver.shimmer2r3.BluetoothModuleVersionDetails;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driver.shimmerGq.ConfigByteLayoutShimmerGq802154;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetails;
import com.shimmerresearch.driverUtilities.ConfigOptionDetailsSensor;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.SensorDetailsRef;
import com.shimmerresearch.driverUtilities.SensorGroupingDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerSDCardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.exgConfig.ExGConfigBytesDetails;
import com.shimmerresearch.exgConfig.ExGConfigBytesDetails.EXG_SETTINGS;
import com.shimmerresearch.exgConfig.ExGConfigOption;
import com.shimmerresearch.exgConfig.ExGConfigBytesDetails.EXG_SETTING_OPTIONS;
import com.shimmerresearch.exgConfig.ExGConfigOptionDetails.EXG_CHIP_INDEX;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.SensorADC;
import com.shimmerresearch.sensors.SensorBridgeAmp;
import com.shimmerresearch.sensors.SensorECGToHRFw;
import com.shimmerresearch.sensors.SensorEXG;
import com.shimmerresearch.sensors.SensorGSR;
import com.shimmerresearch.sensors.SensorPPG;
import com.shimmerresearch.sensors.SensorShimmerClock;
import com.shimmerresearch.sensors.adxl371.SensorADXL371;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.sensors.SensorADC.MICROCONTROLLER_ADC_PROPERTIES;
import com.shimmerresearch.sensors.bmpX80.SensorBMP180;
import com.shimmerresearch.sensors.bmpX80.SensorBMP280;
import com.shimmerresearch.sensors.bmpX80.SensorBMP390;
import com.shimmerresearch.sensors.bmpX80.SensorBMPX80;
import com.shimmerresearch.sensors.kionix.SensorKionixAccel;
import com.shimmerresearch.sensors.kionix.SensorKionixKXRB52042;
import com.shimmerresearch.sensors.kionix.SensorKionixKXTC92050;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS3MDL;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS2MDL;
import com.shimmerresearch.sensors.lsm6dsv.SensorLSM6DSV;
import com.shimmerresearch.sensors.lis2dw12.SensorLIS2DW12;
import com.shimmerresearch.sensors.lsm303.SensorLSM303;
import com.shimmerresearch.sensors.lsm303.SensorLSM303AH;
import com.shimmerresearch.sensors.lsm303.SensorLSM303DLHC;
import com.shimmerresearch.sensors.mpu9x50.SensorMPU9150;
import com.shimmerresearch.sensors.mpu9x50.SensorMPU9250;
import com.shimmerresearch.sensors.mpu9x50.SensorMPU9X50;
import com.shimmerresearch.sensors.shimmer2.SensorMMA736x;
import com.shimmerresearch.sensors.shimmer2.SensorShimmer2Gyro;
import com.shimmerresearch.sensors.shimmer2.SensorShimmer2Mag;
import com.shimmerresearch.algorithms.orientation.GradDes3DOrientation;
import com.shimmerresearch.algorithms.orientation.GradDes3DOrientation9DoF;
import com.shimmerresearch.algorithms.orientation.Orientation3DObject;

/**
 * Rev_1.9
* Copyright (c) 2010 - 2014, Shimmer Research, Ltd.
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
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. <br>
*
* @author Jong Chern Lim, Ruaidhri Molloy, Alejandro Saez, Mark Nolan  <br>
* 
* Changes since 1.8 <br>
* - updated to work with SDLOG
* - change method formatdatapacketreverse to protected
* - added i32r to parsed data
* - mSignalNameArray changed to protected
* - fix twos complement for long numbers (32bit>=)
* - fix 6dof calculation ~ long 
* - fix intiali time stamp ~ long
* 
* 
*  <br>Changes since 1.7 <br>
* - remove multiplying factor (2.8) from the gain in the calculation of the Bridge Amplifier calibrated data
* 
* @date   September, 2014  <br>
* 
* Changes since 1.6 <br>
* - Added functionality for plotmanager see MSSAPI , addExtraSignalProperty, removeExtraSignalProperty ,getListofEnabledSensorSignalsandFormats()
* - various exg advance updates
* 
* @date   July, 2014 <br>
* 
*  <br>Changes since 1.5 <br>
* - Bridge Amplifier gauge support for Shimmer3
* - Bug fix for strain gauge calibration for Shimmer2r
* - Enable 3D orientation for wide range accel, orientation algorithm defaults to low noise even if wide range is enabled
* - Fixed quaternion naming typo
* - Commented out initialisation mSensorBitmaptoName
* - add method getPressureRawCoefficients
*  
* @date   October, 2013 <br>
* 
*  <br>Changes since 1.4 <br>
* - fix getListofEnabledSensors, which was not returning accel shimmer2r
* - fix null pointer graddes algo when using Shimmer2
* - converted to abstract class , and added checkbattery abstract method for the Shimmer2r
* - updated gsr calibrate command parameters for Shimmer3
* - removed mShimmerSamplingRate decimal formatter, decimal formatter should be done on the UI
* - fixed a GSR Shimmer2 problem when using autorange
* - added VSense Batt and VSense Reg and Timestamp to getListOfEnabledSensorSignals
* - added getSamplingRate()
* - added get methods for calibration parameters accel,gyro,mag,accel2
* - updated getoffsetaccel
* - added exg configurations
* - added i24r for exg
* - add get exg configurations
* - renamed i16* to i16r for consistency
* - added EXG_CHIP1 = 0 and EXG_CHIP2=1
* - updated Mag Default Range
* 
*  <br>v0.1.0 consensys
*/
public abstract class ShimmerObject extends ShimmerDevice implements Serializable {

	/** * */
	private static final long serialVersionUID = -1364568867018921219L;
	
	public static final class DatabaseConfigHandleShimmerObject{
		public static final String SYNC_WHEN_LOGGING = 	"Sync_When_Logging";
		public static final String TRIAL_DURATION_ESTIMATED = "Trial_Dur_Est";
		public static final String TRIAL_DURATION_MAXIMUM = "Trial_Dur_Max";
	}

	//Sensor Bitmap for ID ; for the purpose of forward compatibility the sensor bitmap and the ID and the sensor bitmap for the Shimmer firmware has been kept separate, 
	public static final int SENSOR_ACCEL				   = 0x80; 
	public static final int SENSOR_DACCEL				   = 0x1000;
	public static final int SENSOR_GYRO				   	   = 0x40;
	public static final int SENSOR_MAG					   = 0x20; 
	public static final int SENSOR_ECG					   = 0x10;
	public static final int SENSOR_EMG					   = 0x08;
	public static final int SENSOR_EXG1_24BIT			   = 0x10; //only applicable for Shimmer3
	public static final int SENSOR_EXG2_24BIT			   = 0x08; //only applicable for Shimmer3
	public static final int SHIMMER3_SENSOR_ECG			   = SENSOR_EXG1_24BIT + SENSOR_EXG2_24BIT;
	public static final int SHIMMER3_SENSOR_EMG			   = SENSOR_EXG1_24BIT;
	public static final int SENSOR_GSR					   = 0x04;
	public static final int SENSOR_EXP_BOARD_A7		       = 0x02; //only Applicable for Shimmer2
	public static final int SENSOR_EXP_BOARD_A0		       = 0x01; //only Applicable for Shimmer2
	public static final int SENSOR_EXP_BOARD		       = SENSOR_EXP_BOARD_A7+SENSOR_EXP_BOARD_A0;
	public static final int SENSOR_BRIDGE_AMP			   = 0x8000;
	public static final int SENSOR_HEART				   = 0x4000;
	public static final int SENSOR_BATT	  			       = 0x2000; //THIS IS A DUMMY VALUE
	public static final int SENSOR_EXT_ADC_A7              = 0x02; //only Applicable for Shimmer3
	public static final int SENSOR_EXT_ADC_A6              = 0x01; //only Applicable for Shimmer3
	public static final int SENSOR_EXT_ADC_A15             = 0x0800;
	public static final int SENSOR_INT_ADC_A1              = 0x0400;
	public static final int SENSOR_INT_ADC_A12             = 0x0200;
	public static final int SENSOR_INT_ADC_A13             = 0x0100;
	public static final int SENSOR_INT_ADC_A14             = 0x800000;
	public static final int SENSOR_ALL_ADC_SHIMMER3        = SENSOR_INT_ADC_A14 | SENSOR_INT_ADC_A13 | SENSOR_INT_ADC_A12 | SENSOR_INT_ADC_A1 | SENSOR_EXT_ADC_A7 | SENSOR_EXT_ADC_A6 | SENSOR_EXT_ADC_A15; 
	public static final int SENSOR_BMPX80              	   = 0x40000;
	public static final int SENSOR_EXG1_16BIT			   = 0x100000; //only applicable for Shimmer3
	public static final int SENSOR_EXG2_16BIT			   = 0x080000; //only applicable for Shimmer3
	public static final int SENSOR_ALT_ACCEL			   = 0x400000; //only applicable for Shimmer3r
	public static final int SENSOR_ALT_MAG		  		   = 0X200000; //only applicable for Shimmer3r
	public BiMap<String, String> mSensorBitmaptoName;  
	
	public class SDLogHeader {
		
		public final static int ACCEL_LN 		= 1<<7;
		public final static int GYRO 			= 1<<6;
		public final static int MAG 			= 1<<5;
		public final static int EXG1_24BIT 		= 1<<4;
		public final static int EXG2_24BIT 		= 1<<3;
		public final static int GSR 			= 1<<2;
		public final static int EXT_EXP_A7 		= 1<<1;
		public final static int EXT_EXP_A6 		= 1<<0;
		public final static int BRIDGE_AMP 		= 1<<15;
		public final static int ECG_TO_HR_FW	= 1<<14;
		public final static int BATTERY 		= 1<<13;
		public final static int ACCEL_WR 		= 1<<12;
		public final static int EXT_EXP_A15 	= 1<<11;
		public final static int INT_EXP_A1 		= 1<<10;
		public final static int INT_EXP_A12 	= 1<<9;
		public final static int INT_EXP_A13 	= 1<<8;
		public final static int INT_EXP_A14 	= 1<<23;
		public final static int ACCEL_MPU 		= 1<<22;
		public final static int MAG_MPU 		= 1<<21;
		public final static int EXG1_16BIT 		= 1<<20;
		public final static int EXG2_16BIT 		= 1<<19;
		public final static int BMPX80 			= 1<<18;
		public final static int MPL_TEMPERATURE = 1<<17;
		// 1<<16
		public final static long MPL_QUAT_6DOF 	= (long)1<<31; // needs to be cast to a long otherwise would overflow
		public final static int MPL_QUAT_9DOF 	= 1<<30;
		public final static int MPL_EULER_6DOF 	= 1<<29;
		public final static int MPL_EULER_9DOF 	= 1<<28;
		public final static int MPL_HEADING 	= 1<<27;
		public final static int MPL_PEDOMETER 	= 1<<26;
		public final static int MPL_TAP 		= 1<<25;
		public final static int MPL_MOTION_ORIENT = 1<<24;
		public final static long GYRO_MPU_MPL 	= (long)1<<39;
		public final static long ACCEL_MPU_MPL 	= (long)1<<38;
		public final static long MAG_MPU_MPL 	= (long)1<<37;
		public final static long SD_SENSOR_MPL_QUAT_6DOF_RAW = (long)1<<36;
		// 1<<35
		// 1<<34
		// 1<<33
		// 1<<32
	}
	
	public class BTStream {
		
		public final static int ACCEL_LN = 0x80; 
		public final static int GYRO = 0x40;
		public final static int MAG = 0x20;
		public final static int EXG1_24BIT = 0x10;
		public final static int EXG2_24BIT = 0x08;
		public final static int GSR = 0x04;
		public final static int EXT_EXP_A7 = 0x02;
		public final static int EXT_EXP_A6 = 0x01;
		public final static int BRIDGE_AMP = 0x8000;
		// 1<<9 NONE 
		public final static int BATTERY = 0x2000;
		public final static int ACCEL_WR = 0x1000;
		public final static int EXT_EXP_A15 = 0x0800;
		public final static int INT_EXP_A1 = 0x0400;
		public final static int INT_EXP_A12 = 0x0200;
		public final static int INT_EXP_A13 = 0x0100;
		public final static int INT_EXP_A14 = 0x800000;
		public final static int ACCEL_MPU = 0x400000;
		public final static int MAG_MPU = 0x200000;
		public final static int EXG1_16BIT = 0x100000;
		public final static int EXG2_16BIT = 0x080000;
		public final static int BMP180 = 0x40000;
		public final static int MPL_TEMPERATURE = 1<<22;
		public final static int ACCEL_ALT = 0x400000; 
		public final static int MAG_ALT = 0X200000; 
		// 1<<23
		//public final static int MPL_QUAT_6DOF = 1<<24;
		//public final static int MPL_QUAT_9DOF = 1<<25;
		//public final static int MPL_EULER_6DOF = 1<<26;
		//public final static int MPL_EULER_9DOF = 1<<27;
		//public final static int MPL_HEADING = 1<<28;
		//public final static int MPL_PEDOMETER = 1<<29;
		//public final static int MPL_TAP = 1<<30;
		//public final static int MPL_MOTION_ORIENT = 1<<31;
		//public final static long GYRO_MPU_MPL = 1<<32;
		//public final static long ACCEL_MPU_MPL = 1<<33;
		//public final static long MAG_MPU_MPL = 1<<34;
		//public final static long SD_SENSOR_MPL_QUAT_6DOF_RAW = 1<<35;
	}
	
	
	protected Map<String, ChannelDetails> mChannelMap = new LinkedHashMap<String, ChannelDetails>(); 
	
	//Constants describing the packet type
	public static final byte DATA_PACKET                      		= (byte) 0x00;
	public static final byte INQUIRY_COMMAND                  		= (byte) 0x01;
	public static final byte INQUIRY_RESPONSE                 		= (byte) 0x02;
	public static final byte GET_SAMPLING_RATE_COMMAND 	   			= (byte) 0x03;
	public static final byte SAMPLING_RATE_RESPONSE           		= (byte) 0x04;
	public static final byte SET_SAMPLING_RATE_COMMAND        		= (byte) 0x05;
	public static final byte TOGGLE_LED_COMMAND              		= (byte) 0x06;
	public static final byte START_STREAMING_COMMAND          		= (byte) 0x07;
	public static final byte SET_SENSORS_COMMAND              		= (byte) 0x08;
	public static final byte SET_ACCEL_SENSITIVITY_COMMAND    		= (byte) 0x09;
	public static final byte ACCEL_SENSITIVITY_RESPONSE       		= (byte) 0x0A;
	public static final byte GET_ACCEL_SENSITIVITY_COMMAND    		= (byte) 0x0B;
	public static final byte SET_5V_REGULATOR_COMMAND         		= (byte) 0x0C; // only Shimmer 2
	public static final byte SET_PMUX_COMMAND                 		= (byte) 0x0D; // only Shimmer 2
	public static final byte SET_CONFIG_BYTE0_COMMAND   	   		= (byte) 0x0E;
	public static final byte CONFIG_BYTE0_RESPONSE      	   		= (byte) 0x0F;
	public static final byte GET_CONFIG_BYTE0_COMMAND   	   		= (byte) 0x10;
	public static final byte STOP_STREAMING_COMMAND           		= (byte) 0x20;
	
	public static final byte SET_ACCEL_CALIBRATION_COMMAND			= (byte) 0x11;
	public static final byte ACCEL_CALIBRATION_RESPONSE       		= (byte) 0x12;
	
	public static final byte SET_LSM303DLHC_ACCEL_CALIBRATION_COMMAND = (byte) 0x1A;
	public static final byte LSM303DLHC_ACCEL_CALIBRATION_RESPONSE 	= (byte) 0x1B;
	public static final byte GET_LSM303DLHC_ACCEL_CALIBRATION_COMMAND = (byte) 0x1C;
	
	public static final byte GET_ACCEL_CALIBRATION_COMMAND    		= (byte) 0x13;
	
	public static final byte SET_GYRO_CALIBRATION_COMMAND 	  		= (byte) 0x14;
	public static final byte GYRO_CALIBRATION_RESPONSE        		= (byte) 0x15;
	public static final byte GET_GYRO_CALIBRATION_COMMAND     		= (byte) 0x16;
	public static final byte SET_MAG_CALIBRATION_COMMAND      		= (byte) 0x17;
	public static final byte MAG_CALIBRATION_RESPONSE         		= (byte) 0x18;
	public static final byte GET_MAG_CALIBRATION_COMMAND      		= (byte) 0x19;
	public static final byte SET_GSR_RANGE_COMMAND			   		= (byte) 0x21;
	public static final byte GSR_RANGE_RESPONSE			   			= (byte) 0x22;
	public static final byte GET_GSR_RANGE_COMMAND			   		= (byte) 0x23;
	public static final byte GET_SHIMMER_VERSION_COMMAND      		= (byte) 0x24;
	public static final byte GET_SHIMMER_VERSION_COMMAND_NEW      	= (byte) 0x3F; //this is to avoid the $ char which is used by rn42
	public static final byte GET_SHIMMER_VERSION_RESPONSE     		= (byte) 0x25;
	public static final byte SET_EMG_CALIBRATION_COMMAND      		= (byte) 0x26;
	public static final byte EMG_CALIBRATION_RESPONSE         		= (byte) 0x27;
	public static final byte GET_EMG_CALIBRATION_COMMAND      		= (byte) 0x28;
	public static final byte SET_ECG_CALIBRATION_COMMAND      		= (byte) 0x29;
	public static final byte ECG_CALIBRATION_RESPONSE         		= (byte) 0x2A;
	public static final byte GET_ECG_CALIBRATION_COMMAND      		= (byte) 0x2B;
	public static final byte GET_ALL_CALIBRATION_COMMAND      		= (byte) 0x2C;
	public static final byte ALL_CALIBRATION_RESPONSE         		= (byte) 0x2D; 
	public static final byte GET_FW_VERSION_COMMAND          		= (byte) 0x2E;
	public static final byte FW_VERSION_RESPONSE             	 	= (byte) 0x2F;
	public static final byte SET_BLINK_LED                    		= (byte) 0x30;
	public static final byte BLINK_LED_RESPONSE               		= (byte) 0x31;
	public static final byte GET_BLINK_LED                    		= (byte) 0x32;
	public static final byte SET_GYRO_TEMP_VREF_COMMAND       		= (byte) 0x33;
	public static final byte SET_BUFFER_SIZE_COMMAND          		= (byte) 0x34;
	public static final byte BUFFER_SIZE_RESPONSE             		= (byte) 0x35;
	public static final byte GET_BUFFER_SIZE_COMMAND          		= (byte) 0x36;
	public static final byte SET_MAG_GAIN_COMMAND             		= (byte) 0x37;
	public static final byte MAG_GAIN_RESPONSE                		= (byte) 0x38;
	public static final byte GET_MAG_GAIN_COMMAND             		= (byte) 0x39;
	public static final byte SET_MAG_SAMPLING_RATE_COMMAND    		= (byte) 0x3A;
	public static final byte MAG_SAMPLING_RATE_RESPONSE       		= (byte) 0x3B;
	public static final byte GET_MAG_SAMPLING_RATE_COMMAND    		= (byte) 0x3C;
	public static final byte SET_ACCEL_SAMPLING_RATE_COMMAND  		= (byte) 0x40;
	public static final byte ACCEL_SAMPLING_RATE_RESPONSE  			= (byte) 0x41;
	public static final byte GET_ACCEL_SAMPLING_RATE_COMMAND  		= (byte) 0x42;
	public static final byte SET_LSM303DLHC_ACCEL_LPMODE_COMMAND 	= (byte) 0x43;
	public static final byte LSM303DLHC_ACCEL_LPMODE_RESPONSE		= (byte) 0x44;
	public static final byte GET_LSM303DLHC_ACCEL_LPMODE_COMMAND 	= (byte) 0x45;
	public static final byte SET_LSM303DLHC_ACCEL_HRMODE_COMMAND	= (byte) 0x46;
	public static final byte LSM303DLHC_ACCEL_HRMODE_RESPONSE		= (byte) 0x47;
	public static final byte GET_LSM303DLHC_ACCEL_HRMODE_COMMAND 	= (byte) 0x48;
	public static final byte SET_MPU9150_GYRO_RANGE_COMMAND 		= (byte) 0x49;
	public static final byte MPU9150_GYRO_RANGE_RESPONSE 			= (byte) 0x4A;
	public static final byte GET_MPU9150_GYRO_RANGE_COMMAND 		= (byte) 0x4B;;
	public static final byte SET_MPU9150_SAMPLING_RATE_COMMAND 		= (byte) 0x4C;
	public static final byte MPU9150_SAMPLING_RATE_RESPONSE 		= (byte) 0x4D;
	public static final byte GET_MPU9150_SAMPLING_RATE_COMMAND 		= (byte) 0x4E;
	public static final byte SET_BMP180_PRES_RESOLUTION_COMMAND 	= (byte) 0x52;
	public static final byte BMP180_PRES_RESOLUTION_RESPONSE 		= (byte) 0x53;
	public static final byte GET_BMP180_PRES_RESOLUTION_COMMAND 	= (byte) 0x54;
	public static final byte SET_BMP180_PRES_CALIBRATION_COMMAND	= (byte) 0x55;
	public static final byte BMP180_PRES_CALIBRATION_RESPONSE 		= (byte) 0x56;
	public static final byte GET_BMP180_PRES_CALIBRATION_COMMAND 	= (byte) 0x57;
	public static final byte BMP180_CALIBRATION_COEFFICIENTS_RESPONSE = (byte) 0x58;
	public static final byte GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND = (byte) 0x59;
	public static final byte RESET_TO_DEFAULT_CONFIGURATION_COMMAND = (byte) 0x5A;
	public static final byte RESET_CALIBRATION_VALUE_COMMAND 		= (byte) 0x5B;
	public static final byte MPU9150_MAG_SENS_ADJ_VALS_RESPONSE 	= (byte) 0x5C;
	public static final byte GET_MPU9150_MAG_SENS_ADJ_VALS_COMMAND 	= (byte) 0x5D;
	public static final byte SET_INTERNAL_EXP_POWER_ENABLE_COMMAND 	= (byte) 0x5E;
	public static final byte INTERNAL_EXP_POWER_ENABLE_RESPONSE 	= (byte) 0x5F;
	public static final byte GET_INTERNAL_EXP_POWER_ENABLE_COMMAND 	= (byte) 0x60;
	public static final byte SET_EXG_REGS_COMMAND 					= (byte) 0x61;
	public static final byte EXG_REGS_RESPONSE 						= (byte) 0x62;
	public static final byte GET_EXG_REGS_COMMAND 					= (byte) 0x63;
	public static final byte DAUGHTER_CARD_ID_RESPONSE 				= (byte) 0x65;
	public static final byte GET_DAUGHTER_CARD_ID_COMMAND			= (byte) 0x66;
	public static final byte SET_BAUD_RATE_COMMAND 					= (byte) 0x6A;
	public static final byte BAUD_RATE_RESPONSE 					= (byte) 0x6B;
	public static final byte GET_BAUD_RATE_COMMAND 					= (byte) 0x6C;
	public static final byte SET_DERIVED_CHANNEL_BYTES				= (byte) 0x6D; 
	public static final byte DERIVED_CHANNEL_BYTES_RESPONSE			= (byte) 0x6E; 
	public static final byte GET_DERIVED_CHANNEL_BYTES				= (byte) 0x6F; 
	public static final byte START_SDBT_COMMAND 					= (byte) 0x70;
	public static final byte STATUS_RESPONSE	 					= (byte) 0x71;
	public static final byte GET_STATUS_COMMAND 					= (byte) 0x72;
	public static final byte SET_TRIAL_CONFIG_COMMAND				= (byte) 0x73; 
	public static final byte TRIAL_CONFIG_RESPONSE					= (byte) 0x74; 
	public static final byte GET_TRIAL_CONFIG_COMMAND				= (byte) 0x75; 
	public static final byte SET_CENTER_COMMAND						= (byte) 0x76; 
	public static final byte CENTER_RESPONSE						= (byte) 0x77; 
	public static final byte GET_CENTER_COMMAND						= (byte) 0x78; 
	public static final byte SET_SHIMMERNAME_COMMAND				= (byte) 0x79; //Shimmer Name
	public static final byte SHIMMERNAME_RESPONSE					= (byte) 0x7a; 
	public static final byte GET_SHIMMERNAME_COMMAND				= (byte) 0x7b; 
	public static final byte SET_EXPID_COMMAND						= (byte) 0x7c; //Experiment Name
	public static final byte EXPID_RESPONSE							= (byte) 0x7d; 
	public static final byte GET_EXPID_COMMAND						= (byte) 0x7e; 
	public static final byte SET_MYID_COMMAND						= (byte) 0x7F; //Shimmer ID in trial
	public static final byte MYID_RESPONSE							= (byte) 0x80; 
	public static final byte GET_MYID_COMMAND						= (byte) 0x81; 
	public static final byte SET_NSHIMMER_COMMAND					= (byte) 0x82; 
	public static final byte NSHIMMER_RESPONSE						= (byte) 0x83; 
	public static final byte GET_NSHIMMER_COMMAND					= (byte) 0x84; 
	public static final byte SET_CONFIGTIME_COMMAND					= (byte) 0x85; 
	public static final byte CONFIGTIME_RESPONSE					= (byte) 0x86; 
	public static final byte GET_CONFIGTIME_COMMAND					= (byte) 0x87; 
	public static final byte DIR_RESPONSE		 					= (byte) 0x88;
	public static final byte GET_DIR_COMMAND 						= (byte) 0x89;
	public static final byte INSTREAM_CMD_RESPONSE 					= (byte) 0x8A;
	
	public static final byte SET_INFOMEM_COMMAND   					= (byte) 0x8C;
	public static final byte INFOMEM_RESPONSE      					= (byte) 0x8D;
	public static final byte GET_INFOMEM_COMMAND   					= (byte) 0x8E;
	
	public static final byte SET_CRC_COMMAND						= (byte) 0x8B; 
	public static final byte SET_RWC_COMMAND                        = (byte) 0x8F;
	public static final byte RWC_RESPONSE                           = (byte) 0x90;
	public static final byte GET_RWC_COMMAND                        = (byte) 0x91;
	
	public static final byte ROUTINE_COMMUNICATION					= (byte) 0xE0;
	public static final byte ACK_COMMAND_PROCESSED            		= (byte) 0xFF;
	
	public static final byte START_LOGGING_ONLY_COMMAND				= (byte) 0x92;
	public static final byte STOP_LOGGING_ONLY_COMMAND				= (byte) 0x93;
	public static final byte VBATT_RESPONSE                         = (byte) 0x94;
	public static final byte GET_VBATT_COMMAND                      = (byte) 0x95;
	public static final byte TEST_CONNECTION_COMMAND            	= (byte) 0x96;
	public static final byte STOP_SDBT_COMMAND 						= (byte) 0x97;
	
	//TODO 2017-06-19 replace with LiteProtocol values once the protobuf code has been updated to the newer values shown below
	public static final byte GET_BMP280_CALIBRATION_COEFFICIENTS_COMMAND = (byte) 0xA0;//(byte) InstructionsGet.GET_BMP280_CALIBRATION_COEFFICIENTS_COMMAND_VALUE;
	public static final byte BMP280_CALIBRATION_COEFFICIENTS_RESPONSE = (byte) 0x9F;//(byte) InstructionsResponse.BMP280_CALIBRATION_COEFFICIENTS_RESPONSE_VALUE;

	public static final byte GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND  = (byte) 0xA7;//BMP390
	public static final byte PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE    = (byte) 0xA6;//BMP390
	
	public static final byte SET_PRESSURE_OVERSAMPLING_RATIO_COMMAND 	= (byte) 0x52;
	public static final byte PRESSURE_OVERSAMPLING_RATIO_RESPONSE 		= (byte) 0x53;
	public static final byte GET_PRESSURE_OVERSAMPLING_RATIO_COMMAND 	= (byte) 0x54;
	
	//new BT + SD command to set/rsp/get/update_dump_file all calibration parameters using the new byte array structure
	public static final byte SET_CALIB_DUMP_COMMAND					= (byte) 0x98;
	public static final byte RSP_CALIB_DUMP_COMMAND					= (byte) 0x99;
	public static final byte GET_CALIB_DUMP_COMMAND					= (byte) 0x9A;
	public static final byte UPD_CALIB_DUMP_COMMAND					= (byte) 0x9B;	
	//new BT + SD command to Write config file after all of InfoMem is written.
	public static final byte UPD_SDLOG_CFG_COMMAND					= (byte) 0x9C;

	public static final byte GET_BT_FW_VERSION_STR_COMMAND			= (byte) 0xA1;
	public static final byte BT_FW_VERSION_STR_RESPONSE				= (byte) 0xA2;
	public static final byte SET_TEST								= (byte) 0xA8;
	
	public static final byte SET_FEATURE							= (byte) 0xB7;
	
	public static final int MAX_NUMBER_OF_SIGNALS = 77;//50; //used to be 11 but now 13 because of the SR30 + 8 for 3d orientation
	public static final int MAX_INQUIRY_PACKET_SIZE = 47;

	public enum TEST_MODE {
	    MAIN_TEST((byte)0, "Main Test"),
	    LED_TEST((byte)1, "LED Test"),
		IC_TEST((byte)2, "IC Test");

	    private final byte byteInstruction;
	    private final String description;

	    // Constructor to initialize the int and String values
	    TEST_MODE(byte byteInstruction, String description) {
	        this.byteInstruction = byteInstruction;
	        this.description = description;
	    }

	    // Getter to retrieve the int value
	    public byte getByteInstruction() {
	        return byteInstruction;
	    }

	    // Getter to retrieve the String value
	    public String getDescription() {
	        return description;
	    }

	    // Optional: Override toString() to include both values in the string representation
	    @Override
	    public String toString() {
	        return description + " (" + String.format("0x%02X", byteInstruction) + ")";
	    }


	}
	public static final Map<String,TEST_MODE> mMapOfBluetoothDeviceTest;
	static {
		Map<String,TEST_MODE> aMap = new LinkedHashMap<String,TEST_MODE>();
        
        aMap.put(TEST_MODE.MAIN_TEST.getDescription(),TEST_MODE.MAIN_TEST);
        aMap.put(TEST_MODE.LED_TEST.getDescription(),TEST_MODE.LED_TEST);
        aMap.put(TEST_MODE.IC_TEST.getDescription(),TEST_MODE.IC_TEST);
        mMapOfBluetoothDeviceTest = Collections.unmodifiableMap(aMap);
    }
	
	
	protected int mBluetoothBaudRate=9; //460800

	protected int mPacketSize=0; // Default 2 bytes for time stamp and 6 bytes for accelerometer 
	protected long mConfigByte0;	
	public int mNChannels=0;	                                                // Default number of sensor channels set to three because of the on board accelerometer 
	protected int mBufferSize;                   							

	protected String[] mSignalNameArray=new String[MAX_NUMBER_OF_SIGNALS];							// 19 is the maximum number of signal thus far
	protected String[] mSignalDataTypeArray=new String[MAX_NUMBER_OF_SIGNALS];						// 19 is the maximum number of signal thus far
	
	protected int mButtonStart = 0;
	protected int mDisableBluetooth = 0;
	protected int mMasterShimmer = 0;
	protected int mSingleTouch = 0;
	protected int mTCXO = 0;
	protected int mSyncWhenLogging = 0;
	protected int mSyncBroadcastInterval = 0;
	protected String mCenter = "";
	
	private boolean isOverrideShowErrorLedsRtc = false;
	private int mShowErrorLedsRtc = 0;
	private boolean isOverrideShowErrorLedsSd = false;
	private int mShowErrorLedsSd = 0;
	private boolean mLowBattAutoStop = false;

	protected int mTrialId = 0;
	protected int mTrialNumberOfShimmers = 0;
	protected int mTrialDurationEstimatedInSecs = 0;
	protected int mTrialDurationMaximumInSecs = 0;
	
	/** Used in BT communication */
	protected String mMyBluetoothAddress="";
//	/** Used in UART command through the base/dock*/
//	protected String mMacIdFromUart = "";
	/** Read from the InfoMem from UART command through the base/dock*/
	protected String mMacIdFromInfoMem = "";

	protected BluetoothModuleVersionDetails bluetoothModuleVersionDetails = new BluetoothModuleVersionDetails();
	
//	protected String mShimmerUserAssignedName=""; // This stores the user assigned name
	
	protected List<String> syncNodesList = new ArrayList<String>();
// ----------- Now implemented in SensorPPG -------------------------		
	protected int mPpgAdcSelectionGsrBoard = 0;
	protected int mPpg1AdcSelectionProto3DeluxeBoard = 0;
	protected int mPpg2AdcSelectionProto3DeluxeBoard = 0;
// ------------------------------------------------------------------	

	protected int mCurrentLEDStatus=0;

	private double mLastKnownHeartRate=0;
	protected DescriptiveStatistics mVSenseBattMA = new DescriptiveStatistics(1024); //YYY -BattVolt-SensorClass
	Quat4d mQ = new Quat4d();	
	transient GradDes3DOrientation mOrientationAlgo;	
	private boolean mIsOrientationEnabled = false;	
	protected double mDefaultOnTheFlyGyroCalibThreshold = 1.2;	

	protected boolean mEnableCalibration = true;	
	protected boolean mConfigFileCreationFlag = true;
	protected boolean mShimmerUsingConfigFromInfoMem = false;
	protected boolean mUseInfoMemConfigMethod = true;
	
	protected boolean mUseArraysDataStructureInObjectCluster = false;

	protected byte[] mInquiryResponseBytes;	
	
	//This features are only used in LogAndStream FW 
	protected String mDirectoryName;
	@Deprecated
	private List<String[]> mExtraSignalProperties = null;
	
	/** GQ BLE */
	protected int mGqPacketNumHeaderBytes = 0;
	/** GQ BLE */
	protected int mSamplingDividerVBatt = 0;
	/** GQ BLE */
	protected int mSamplingDividerGsr = 0;
// ----------- Now implemented in SensorPPG -------------------------		
	/** GQ BLE */
	protected int mSamplingDividerPpg = 0;
// ------------------------------------------------------------------	
	/** GQ BLE */
	protected int mSamplingDividerLsm303dlhcAccel = 0;
	/** GQ BLE */
	protected int mSamplingDividerBeacon = 0;


	protected abstract void checkBattery();
	
	//-------- Timestamp related start --------
	protected double mLastReceivedTimeStampTicksUnwrapped=0;
	protected double mCurrentTimeStampCycle=0;
	protected long mInitialTimeStampTicksSd = 0;
	@Deprecated //not needed any more
	protected double mLastReceivedCalibratedTimeStamp=-1; 
	
	protected boolean mStreamingStartTimeSaved = false;	
	protected double mStreamingStartTimeMilliSecs;
	
	protected int mTimeStampPacketByteSize = 2;
	protected int mTimeStampTicksMaxValue = 65536;// 16777216 or 65536
	
	protected long mRTCDifferenceInTicks = 0; //this is in ticks
	public int mRTCSetByBT = 1; // RTC source, = 1 because it comes from the BT

	protected boolean mFirstTime = true;
	
	/** This variable was originally implemented because the
	 * initial time in the SD header (i.e., the time when the header
	 * was created) wasn't equal to the first timestamp in the
	 * subsequent data packets (i.e., the lower 3 bytes of the
	 * initial timestamp). This problem has since been addressed in
	 * firmware whereby the header is updated with the timestamp
	 * from the first packet. Therefore this variable is redundant. */
	protected double mFirstTsOffsetFromInitialTsTicks = 0;
	public int OFFSET_LENGTH = 9;
	//-------- Timestamp related end --------

	// Shimmer2/2r - Analog accel
	protected SensorMMA736x mSensorMMA736x = new SensorMMA736x(this);
	// Shimmer2 Mag
	protected SensorShimmer2Mag mSensorShimmer2Mag = new SensorShimmer2Mag(this);
	// Shimmer2 - Gyro
	protected SensorShimmer2Gyro mSensorShimmer2Gyro = new SensorShimmer2Gyro(this);

	
	// Shimmer3 - Analog accel
	private SensorKionixAccel mSensorKionixAccel = new SensorKionixKXRB52042(this);
	// Shimmer3 Wide-range accel and mag
	private SensorLSM303 mSensorLSM303 = new SensorLSM303DLHC(this);
	// Shimmer3 - Gyro
	protected SensorMPU9X50 mSensorMpu9x50 = new SensorMPU9150(this);
	// Shimmer3 - Pressure/Temperature 
	public SensorBMPX80 mSensorBMPX80 = new SensorBMP180(this);
  
	// Shimmer3r - Mag
	private SensorLIS2MDL mSensorLIS2MDL = new SensorLIS2MDL(this);
	// Shimmer3r - Alt Mag
	private SensorLIS3MDL mSensorLIS3MDL = new SensorLIS3MDL(this);	
	// Shimmer3R - WR Accel
	private SensorLIS2DW12 mSensorLIS2DW12 = new SensorLIS2DW12(this);
	// Shimmer3r - Accel LN & Gyro
	private SensorLSM6DSV mSensorLSM6DSV = new SensorLSM6DSV(this);
	
	// Shimmer3r - High-g accel
	protected SensorADXL371 mSensorADXL371 = new SensorADXL371(this);
	
	// ----------  ECG/EMG start ---------------
	protected double OffsetECGRALL=2060;
	protected double GainECGRALL=175;
	protected double OffsetECGLALL=2060;
	protected double GainECGLALL=175;
	protected double OffsetEMG=2060;
	protected double GainEMG=750;

	/** 0 = 16 bit, 1 = 24 bit */
	protected int mExGResolution = 1;
	protected boolean mIsExg1_24bitEnabled = false;
	protected boolean mIsExg2_24bitEnabled = false;
	protected boolean mIsExg1_16bitEnabled = false;
	protected boolean mIsExg2_16bitEnabled = false;

	//Shimmer2r and not Shimmer3?
	protected byte[] mEMGCalRawParams  = new byte[13];
	protected byte[] mECGCalRawParams = new byte[13];
	
	protected boolean mDefaultCalibrationParametersECG = true;
	protected boolean mDefaultCalibrationParametersEMG = true;

	//EXG
	protected ExGConfigBytesDetails mExGConfigBytesDetails = new ExGConfigBytesDetails(); 

	protected byte[] mEXG1RegisterArray = new byte[10];
	protected byte[] mEXG2RegisterArray = new byte[10];
	
	//Gain is set at 6 (default) in eq. below until updated later in the connection/configuration sequence
	protected double exg1Ch1CalFactor24Bit = (((2.42*1000)/6)/(Math.pow(2,23)-1));
	protected double exg1Ch2CalFactor24Bit = (((2.42*1000)/6)/(Math.pow(2,23)-1));
	protected double exg2Ch1CalFactor24Bit = (((2.42*1000)/6)/(Math.pow(2,23)-1));
	protected double exg2Ch2CalFactor24Bit = (((2.42*1000)/6)/(Math.pow(2,23)-1));

	protected double exg1Ch1CalFactor16Bit = (((2.42*1000)/6)/(Math.pow(2,15)-1));
	protected double exg1Ch2CalFactor16Bit = (((2.42*1000)/6)/(Math.pow(2,15)-1));
	protected double exg2Ch1CalFactor16Bit = (((2.42*1000)/6)/(Math.pow(2,15)-1));
	protected double exg2Ch2CalFactor16Bit = (((2.42*1000)/6)/(Math.pow(2,15)-1));

	@Deprecated
	private int mEXG1RateSetting; //setting not value
	@Deprecated
	private int mEXG1CH1GainSetting; // this is the setting not to be confused with the actual value
	@Deprecated
	private int mEXG1CH1GainValue; // this is the value
	@Deprecated
	private int mEXG1CH2GainSetting; // this is the setting not to be confused with the actual value
	@Deprecated
	private int mEXG1CH2GainValue; // this is the value
	@Deprecated
	private int mEXG2RateSetting; //setting not value
	@Deprecated
	private int mEXG2CH1GainSetting; // this is the setting not to be confused with the actual value
	@Deprecated
	private int mEXG2CH1GainValue; // this is the value
	@Deprecated
	private int mEXG2CH2PowerDown;//Not used in ShimmerBluetooth
	@Deprecated
	private int mEXG2CH2GainSetting; // this is the setting not to be confused with the actual value
	@Deprecated
	private int mEXG2CH2GainValue; // this is the value
	
	//EXG ADVANCED
	@Deprecated
	private int mEXGReferenceElectrode=-1;
	@Deprecated
	private int mLeadOffDetectionMode;
	@Deprecated
	private int mEXG1LeadOffCurrentMode;
	@Deprecated
	private int mEXG2LeadOffCurrentMode;
	@Deprecated
	private int mEXG1Comparators;
	@Deprecated
	private int mEXG2Comparators;
	@Deprecated
	private int mEXGRLDSense;
	@Deprecated
	private int mEXG1LeadOffSenseSelection;
	@Deprecated
	private int mEXG2LeadOffSenseSelection;
	@Deprecated
	private int mEXGLeadOffDetectionCurrent;
	@Deprecated
	private int mEXGLeadOffComparatorTreshold;
	@Deprecated
	private int mEXG2RespirationDetectState;//Not used in ShimmerBluetooth
	@Deprecated
	private int mEXG2RespirationDetectFreq;//Not used in ShimmerBluetooth
	@Deprecated
	private int mEXG2RespirationDetectPhase;//Not used in ShimmerBluetooth
	// ----------  ECG/EMG end ---------------
	
	// ---------- GSR start ------------------
	protected int mGSRRange=4;		// This stores the current GSR range being used.
	
	protected int mPastGSRRange=4; 				// this is to fix a bug with SDLog v0.9
	protected int mPastGSRUncalibratedValue=4; 	// this is to fix a bug with SDLog v0.9
	protected boolean mPastGSRFirstTime=true; 	// this is to fix a bug with SDLog v0.9

	// ---------- GSR end ------------------
    
	public ObjectCluster setLSLTimeIfAvailable(ObjectCluster ojc) {
		return ojc;
	}

	/** This method will be deprecated for future Shimmer hardware revisions. The last hardware this will be used for is Shimmer3. 
	 *  It should work with all FW associated with Shimmer3 and Shimmer2 devices.
	 *  
	 *  Future hardware which WON'T be using this will start with ShimmerGQ HW. 
	 * 
	 * @param newPacket
	 * @param fwType
	 * @param isTimeSyncEnabled
	 * @param pcTimestampMs this is only used by shimmerbluetooth, set to -1 if not using
	 * @return
	 */
	@Override
	public ObjectCluster buildMsg(byte[] newPacket, COMMUNICATION_TYPE fwType, boolean isTimeSyncEnabled, double pcTimestampMs) {
		
		ObjectCluster objectCluster = new ObjectCluster();
		setLSLTimeIfAvailable(objectCluster);
		if(mUseArraysDataStructureInObjectCluster) {
			objectCluster.mEnableArraysDataStructure = true;	//This will disable the Multimap as well
		}
		
		objectCluster.setShimmerName(mShimmerUserAssignedName);
		objectCluster.setMacAddress(mMyBluetoothAddress);
		objectCluster.setComPort(getComPortName());
		objectCluster.mRawData = newPacket;
						
		if(fwType!=COMMUNICATION_TYPE.BLUETOOTH && fwType!=COMMUNICATION_TYPE.SD){
//			throw new Exception("The Firmware is not compatible");
			consolePrintErrLn("The Firmware is not compatible");
		}
		
		int numCalibratedData = mNChannels;
		int numUncalibratedData = mNChannels;
		int numUncalibratedDataUnits = mNChannels;
		int numCalibratedDataUnits = mNChannels; 
		int numSensorNames = mNChannels;
		int numAdditionalChannels = 0;
		
		int indexAlgo = 0;
		
		if (fwType == COMMUNICATION_TYPE.BLUETOOTH){
//			objectCluster.mSystemTimeStampBytes=ByteBuffer.allocate(8).putLong(pcTimestamp).array();
			objectCluster.setSystemTimeStamp(pcTimestampMs);
			//plus 1 because of: timestamp
			numAdditionalChannels += 1;
			
			//plus 4 because of: batt percent, PRR Current, PRR Trial, system timestamp
			numAdditionalChannels += 4;
			
			//Event Markers
			numAdditionalChannels += 1;
		} 
		else {
			if (fwType == COMMUNICATION_TYPE.SD && getHardwareVersion()==HW_ID.SHIMMER_3R) {
				//plus 1 because of: timestamp, because we are no longer relying on interpretdatapacketformat within ShimmerSDLog
				numAdditionalChannels += 1;
			}
			
			if (!isRtcDifferenceSet()){
				//sd log time stamp already included in mnChannels
				if(isEXGUsingDefaultECGConfiguration() || isEXGUsingDefaultRespirationConfiguration()){
					//HACK for CON-644 -> Do not merge to Dev/Master
					numAdditionalChannels += 1;
				}
			} 
			else {
				//for RTC
				numAdditionalChannels += 1;
			}
		}
		
//		if (fwType == COMMUNICATION_TYPE.BLUETOOTH){ // Here so as not to mess with ShimmerSDLog
			//for ECG LL-LA Channel added&calculated in API
			if(isEXGUsingDefaultECGConfiguration() || isEXGUsingDefaultRespirationConfiguration()){
				numAdditionalChannels += 1;
			}
//		}
		
		// adding channels from enabled algorithms details
		//
		for(AbstractAlgorithm aA: getListOfEnabledAlgorithmModules()){
			numAdditionalChannels += aA.getNumberOfEnabledChannels();
		}
		
		if(is3DOrientationEnabled()) {
			//JY: Add the number of 9DOF channels to sensorNames so that sensorNamesAlgo has space to be copied in later.
			//This is because setting mIsOrientationEnabled does not add to the listOfEnabledAlgorithmModules 
			numAdditionalChannels += 8;
		}
		
		//numAdditionalChannels += 15;
		
		double [] calibratedData = new double[numCalibratedData+numAdditionalChannels];
		double [] uncalibratedData = new double[numUncalibratedData+numAdditionalChannels];
		String [] uncalibratedDataUnits = new String[numUncalibratedDataUnits+numAdditionalChannels];
		String [] calibratedDataUnits = new String[numCalibratedDataUnits+numAdditionalChannels];
		String [] sensorNames = new String[numSensorNames+numAdditionalChannels];
		
		double [] calDataAlgo = null;
		String [] calDataUnitsAlgo = null;
		String [] sensorNamesAlgo = null;
		
		if(is3DOrientationEnabled()) {
			//Add 8 channels for 9DOF Quats & Eulers
			calDataAlgo = new double[8];
			calDataUnitsAlgo = new String[8];
			sensorNamesAlgo = new String[8];
		}
		
		objectCluster.sensorDataArray = new SensorDataArray(numCalibratedData+numAdditionalChannels);
		
		
		System.arraycopy(mSignalNameArray, 0, sensorNames, 0, sensorNames.length);
		// the above is throwing an error
		//sensorNames = Arrays.copyOf(mSignalNameArray, mSignalNameArray.length);
		
		//PARSE DATA
		long[] newPacketInt = null;
		
		try{
			newPacketInt = UtilParseData.parseData(newPacket, mSignalDataTypeArray);
		} catch(IndexOutOfBoundsException e){
			String debugString = "SignalDataTypeArray:";
			for(String s:mSignalDataTypeArray){
				debugString += "\t" + s;
			}
			System.out.println(debugString);
			System.out.println("newPacket:\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(newPacket));
			throw(e);
		}
		
		double[] tempData=new double[3];
		Vector3d accelerometer = new Vector3d(); 
		Vector3d magnetometer = new Vector3d(); 
		Vector3d gyroscope = new Vector3d();
		
		if (getHardwareVersion()==HW_ID.SHIMMER_SR30 || getHardwareVersion()==HW_ID.SHIMMER_3  || getHardwareVersion()==HW_ID.SHIMMER_3R  
				|| getHardwareVersion()==HW_ID.SHIMMER_GQ_802154_LR || getHardwareVersion()==HW_ID.SHIMMER_GQ_802154_NR || getHardwareVersion()==HW_ID.SHIMMER_2R_GQ){
			
			parseTimestampShimmer3(fwType, objectCluster, uncalibratedData, uncalibratedDataUnits, calibratedData, calibratedDataUnits, sensorNames, newPacketInt);
			
			//OFFSET
			if(isTimeSyncEnabled && (fwType == COMMUNICATION_TYPE.SD)){
				int iOffset=getSignalIndex(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET); //find index
				double offsetValue = Double.NaN;
				if (OFFSET_LENGTH==9){
					if(newPacketInt[iOffset] == 1152921504606846975L){
						offsetValue=Double.NaN;
					} else {
						offsetValue=(double)newPacketInt[iOffset];
					}	
				}
				else{
					if(newPacketInt[iOffset] == 4294967295L){ //this is 4 bytes
						offsetValue=Double.NaN;
					} else {
						offsetValue=(double)newPacketInt[iOffset];
					}
				}

				objectCluster.addDataToMap(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,offsetValue);
				objectCluster.addDataToMap(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,Double.NaN);
				uncalibratedData[iOffset] = offsetValue;
				calibratedData[iOffset] = Double.NaN;
				uncalibratedDataUnits[iOffset] = CHANNEL_UNITS.NO_UNITS;
				calibratedDataUnits[iOffset] = CHANNEL_UNITS.NO_UNITS;
			} 

			//Used anywhere?
			objectCluster = callAdditionalServices(objectCluster);

			//first get raw and calibrated data, this is data derived from the Shimmer device and involves no involvement from the API
			
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.ACCEL_LN) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.ACCEL_LN) > 0)){
				int iAccelX=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_LN_X); //find index
				int iAccelY=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y); //find index
				int iAccelZ=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z); //find index
				tempData[0]=(double)newPacketInt[iAccelX];
				tempData[1]=(double)newPacketInt[iAccelY];
				tempData[2]=(double)newPacketInt[iAccelZ];

				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_X,CHANNEL_TYPE.UNCAL.toString().toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelZ]);
				uncalibratedData[iAccelX]=(double)newPacketInt[iAccelX];
				uncalibratedData[iAccelY]=(double)newPacketInt[iAccelY];
				uncalibratedData[iAccelZ]=(double)newPacketInt[iAccelZ];
				uncalibratedDataUnits[iAccelX]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelY]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelZ]=CHANNEL_UNITS.NO_UNITS;

				
				if (mEnableCalibration){
					double[] accelCalibratedData;
//					accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixAnalogAccel, mSensitivityMatrixAnalogAccel, mOffsetVectorAnalogAccel);
					accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsAccelLn());
					calibratedData[iAccelX]=accelCalibratedData[0];
					calibratedData[iAccelY]=accelCalibratedData[1];
					calibratedData[iAccelZ]=accelCalibratedData[2];
					
					if (isShimmerGen3R()) {
			            objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_X, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.ACCEL_CAL_UNIT, accelCalibratedData[0], mSensorLSM6DSV.mIsUsingDefaultLNAccelParam);
			            objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.ACCEL_CAL_UNIT, accelCalibratedData[1], mSensorLSM6DSV.mIsUsingDefaultLNAccelParam);
			            objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.ACCEL_CAL_UNIT, accelCalibratedData[2], mSensorLSM6DSV.mIsUsingDefaultLNAccelParam);
					}
					else if (isShimmerGen3()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,accelCalibratedData[0],mSensorKionixAccel.mIsUsingDefaultLNAccelParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,accelCalibratedData[1],mSensorKionixAccel.mIsUsingDefaultLNAccelParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,accelCalibratedData[2],mSensorKionixAccel.mIsUsingDefaultLNAccelParam);
					}
					
					calibratedDataUnits[iAccelX] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
					calibratedDataUnits[iAccelY] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
					calibratedDataUnits[iAccelZ] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
					accelerometer.x=accelCalibratedData[0];
					accelerometer.y=accelCalibratedData[1];
					accelerometer.z=accelCalibratedData[2];

//					if (mDefaultCalibrationParametersAccel == true) {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_LN_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT,accelCalibratedData[0]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT,accelCalibratedData[1]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT,accelCalibratedData[2]));
//						calibratedDataUnits[iAccelX]=CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iAccelY]=CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iAccelZ]=CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT;
//						accelerometer.x=accelCalibratedData[0];
//						accelerometer.y=accelCalibratedData[1];
//						accelerometer.z=accelCalibratedData[2];
//
//					} else {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_LN_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,accelCalibratedData[0]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,accelCalibratedData[1]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,accelCalibratedData[2]));
//						calibratedDataUnits[iAccelX] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
//						calibratedDataUnits[iAccelY] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
//						calibratedDataUnits[iAccelZ] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
//						accelerometer.x=accelCalibratedData[0];
//						accelerometer.y=accelCalibratedData[1];
//						accelerometer.z=accelCalibratedData[2];
//
//					}
				}
			}
			
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.ACCEL_WR) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.ACCEL_WR) > 0)
					){
				int iAccelX=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_WR_X); //find index
				int iAccelY=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y); //find index
				int iAccelZ=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z); //find index
				//check range

				tempData[0]=(double)newPacketInt[iAccelX];
				tempData[1]=(double)newPacketInt[iAccelY];
				tempData[2]=(double)newPacketInt[iAccelZ];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelZ]);
				uncalibratedData[iAccelX]=(double)newPacketInt[iAccelX];
				uncalibratedData[iAccelY]=(double)newPacketInt[iAccelY];
				uncalibratedData[iAccelZ]=(double)newPacketInt[iAccelZ];
				uncalibratedDataUnits[iAccelX]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelY]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelZ]=CHANNEL_UNITS.NO_UNITS;

				if (mEnableCalibration){
					double[] accelCalibratedData;
//					accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixWRAccel, mSensitivityMatrixWRAccel, mOffsetVectorWRAccel);
					accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsAccelWr());
					calibratedData[iAccelX]=accelCalibratedData[0];
					calibratedData[iAccelY]=accelCalibratedData[1];
					calibratedData[iAccelZ]=accelCalibratedData[2];
					
					if (isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelX],mSensorLIS2DW12.isUsingDefaultWRAccelParam());
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelY],mSensorLIS2DW12.isUsingDefaultWRAccelParam());
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelZ],mSensorLIS2DW12.isUsingDefaultWRAccelParam());
					}
					else if (isShimmerGen3()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelX],mSensorLSM303.isUsingDefaultWRAccelParam());
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelY],mSensorLSM303.isUsingDefaultWRAccelParam());
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelZ],mSensorLSM303.isUsingDefaultWRAccelParam());
					}

					calibratedDataUnits[iAccelX]=CHANNEL_UNITS.ACCEL_CAL_UNIT;
					calibratedDataUnits[iAccelY]=CHANNEL_UNITS.ACCEL_CAL_UNIT;
					calibratedDataUnits[iAccelZ]=CHANNEL_UNITS.ACCEL_CAL_UNIT;
					if (((mEnabledSensors & SENSOR_ACCEL) == 0)){
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];
					}

//					if (isUsingDefaultWRAccelParam()) {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_WR_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT,calibratedData[iAccelX]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT,calibratedData[iAccelY]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT,calibratedData[iAccelZ]));
//						calibratedDataUnits[iAccelX]=CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iAccelY]=CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iAccelZ]=CHANNEL_UNITS.ACCEL_DEFAULT_CAL_UNIT;
//						if (((mEnabledSensors & SENSOR_ACCEL) == 0)){
//							accelerometer.x=accelCalibratedData[0]; //this is used to calculate quaternions // skip if Low noise is enabled
//							accelerometer.y=accelCalibratedData[1];
//							accelerometer.z=accelCalibratedData[2];
//						}
//					} else {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_WR_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelX]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelY]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.ACCEL_CAL_UNIT,calibratedData[iAccelZ]));
//						calibratedDataUnits[iAccelX]=CHANNEL_UNITS.ACCEL_CAL_UNIT;
//						calibratedDataUnits[iAccelY]=CHANNEL_UNITS.ACCEL_CAL_UNIT;
//						calibratedDataUnits[iAccelZ]=CHANNEL_UNITS.ACCEL_CAL_UNIT;
//						if (((mEnabledSensors & SENSOR_ACCEL) == 0)){
//							accelerometer.x=accelCalibratedData[0];
//							accelerometer.y=accelCalibratedData[1];
//							accelerometer.z=accelCalibratedData[2];
//						}
//
//					}	
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.ACCEL_ALT) > 0) 
				|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.ACCEL_MPU) > 0)) {
				int iAccelX=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_X); //find index
				int iAccelY=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Y); //find index
				int iAccelZ=getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Z); //find index
				tempData[0]=(double)newPacketInt[iAccelX];
				tempData[1]=(double)newPacketInt[iAccelY];
				tempData[2]=(double)newPacketInt[iAccelZ];

				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_X,CHANNEL_TYPE.UNCAL.toString().toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelZ]);
				uncalibratedData[iAccelX]=(double)newPacketInt[iAccelX];
				uncalibratedData[iAccelY]=(double)newPacketInt[iAccelY];
				uncalibratedData[iAccelZ]=(double)newPacketInt[iAccelZ];
				uncalibratedDataUnits[iAccelX]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelY]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelZ]=CHANNEL_UNITS.NO_UNITS;

				
				if (mEnableCalibration){
					double[] altAccelCalibratedData;
//					accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixAnalogAccel, mSensitivityMatrixAnalogAccel, mOffsetVectorAnalogAccel);
					altAccelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsAccelAlt());
					calibratedData[iAccelX]=altAccelCalibratedData[0];
					calibratedData[iAccelY]=altAccelCalibratedData[1];
					calibratedData[iAccelZ]=altAccelCalibratedData[2];
					
					if (isShimmerGen3R()) {
			            objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_X, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.ACCEL_CAL_UNIT, altAccelCalibratedData[0], mSensorADXL371.mIsUsingDefaultHighGAccelParam);
			            objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Y, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.ACCEL_CAL_UNIT, altAccelCalibratedData[1], mSensorADXL371.mIsUsingDefaultHighGAccelParam);
			            objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Z, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.ACCEL_CAL_UNIT, altAccelCalibratedData[2], mSensorADXL371.mIsUsingDefaultHighGAccelParam);
					}
					
					calibratedDataUnits[iAccelX] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
					calibratedDataUnits[iAccelY] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
					calibratedDataUnits[iAccelZ] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
					accelerometer.x=altAccelCalibratedData[0];
					accelerometer.y=altAccelCalibratedData[1];
					accelerometer.z=altAccelCalibratedData[2];
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.MAG_ALT) > 0) 
				|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MAG_MPU) > 0)) {
				int iMagX=getSignalIndex(Shimmer3.ObjectClusterSensorName.ALT_MAG_X);
				int iMagY=getSignalIndex(Shimmer3.ObjectClusterSensorName.ALT_MAG_Y);
				int iMagZ=getSignalIndex(Shimmer3.ObjectClusterSensorName.ALT_MAG_Z);
				tempData[0]=(double)newPacketInt[iMagX];
				tempData[1]=(double)newPacketInt[iMagY];
				tempData[2]=(double)newPacketInt[iMagZ];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ALT_MAG_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ALT_MAG_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ALT_MAG_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagZ]);
				uncalibratedData[iMagX]=(double)newPacketInt[iMagX];
				uncalibratedData[iMagY]=(double)newPacketInt[iMagY];
				uncalibratedData[iMagZ]=(double)newPacketInt[iMagZ];
				uncalibratedDataUnits[iMagX]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iMagY]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iMagZ]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					double[] altMagCalibratedData;
//					magCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixMagnetometer, mSensitivityMatrixMagnetometer, mOffsetVectorMagnetometer);
					altMagCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsMagAlt());
					calibratedData[iMagX]=altMagCalibratedData[0];
					calibratedData[iMagY]=altMagCalibratedData[1];
					calibratedData[iMagZ]=altMagCalibratedData[2];
					
					if(isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ALT_MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,altMagCalibratedData[0],mSensorLIS3MDL.mIsUsingDefaultAltMagParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ALT_MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,altMagCalibratedData[1],mSensorLIS3MDL.mIsUsingDefaultAltMagParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ALT_MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,altMagCalibratedData[2],mSensorLIS3MDL.mIsUsingDefaultAltMagParam);
					}
					magnetometer.x=altMagCalibratedData[0];
					magnetometer.y=altMagCalibratedData[1];
					magnetometer.z=altMagCalibratedData[2];
					calibratedDataUnits[iMagX]=CHANNEL_UNITS.MAG_CAL_UNIT;
					calibratedDataUnits[iMagY]=CHANNEL_UNITS.MAG_CAL_UNIT;
					calibratedDataUnits[iMagZ]=CHANNEL_UNITS.MAG_CAL_UNIT;
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.GYRO) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.GYRO) > 0)
					) {
				int iGyroX=getSignalIndex(Shimmer3.ObjectClusterSensorName.GYRO_X);
				int iGyroY=getSignalIndex(Shimmer3.ObjectClusterSensorName.GYRO_Y);
				int iGyroZ=getSignalIndex(Shimmer3.ObjectClusterSensorName.GYRO_Z);
				tempData[0]=(double)newPacketInt[iGyroX];
				tempData[1]=(double)newPacketInt[iGyroY];
				tempData[2]=(double)newPacketInt[iGyroZ];


				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGyroX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGyroY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGyroZ]);
				uncalibratedData[iGyroX]=(double)newPacketInt[iGyroX];
				uncalibratedData[iGyroY]=(double)newPacketInt[iGyroY];
				uncalibratedData[iGyroZ]=(double)newPacketInt[iGyroZ];
				uncalibratedDataUnits[iGyroX]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iGyroY]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iGyroZ]=CHANNEL_UNITS.NO_UNITS;
				
				
				if (mEnableCalibration){
//					double[] gyroCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixGyroscope, mSensitivityMatrixGyroscope, mOffsetVectorGyroscope);
					double[] gyroCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsGyro());
					calibratedData[iGyroX]=gyroCalibratedData[0];
					calibratedData[iGyroY]=gyroCalibratedData[1];
					calibratedData[iGyroZ]=gyroCalibratedData[2];
					
					if (isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[0], mSensorLSM6DSV.mIsUsingDefaultGyroParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[1], mSensorLSM6DSV.mIsUsingDefaultGyroParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[2], mSensorLSM6DSV.mIsUsingDefaultGyroParam);
					} 
					else if (isShimmerGen3()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[0], mSensorMpu9x50.mIsUsingDefaultGyroParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[1], mSensorMpu9x50.mIsUsingDefaultGyroParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[2], mSensorMpu9x50.mIsUsingDefaultGyroParam);
					}
					
					gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
					gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
					gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
					calibratedDataUnits[iGyroX]=CHANNEL_UNITS.GYRO_CAL_UNIT;
					calibratedDataUnits[iGyroY]=CHANNEL_UNITS.GYRO_CAL_UNIT;
					calibratedDataUnits[iGyroZ]=CHANNEL_UNITS.GYRO_CAL_UNIT;

//					if (mDefaultCalibrationParametersGyro == true) {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_DEFAULT_CAL_UNIT,gyroCalibratedData[0]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_DEFAULT_CAL_UNIT,gyroCalibratedData[1]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_DEFAULT_CAL_UNIT,gyroCalibratedData[2]));
//						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
//						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
//						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
//						calibratedDataUnits[iGyroX]=CHANNEL_UNITS.GYRO_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iGyroY]=CHANNEL_UNITS.GYRO_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iGyroZ]=CHANNEL_UNITS.GYRO_DEFAULT_CAL_UNIT;
//					} else {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[0]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[1]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,gyroCalibratedData[2]));
//						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
//						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
//						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
//						calibratedDataUnits[iGyroX]=CHANNEL_UNITS.GYRO_CAL_UNIT;
//						calibratedDataUnits[iGyroY]=CHANNEL_UNITS.GYRO_CAL_UNIT;
//						calibratedDataUnits[iGyroZ]=CHANNEL_UNITS.GYRO_CAL_UNIT;
//					} 
					
					if(isShimmerGen2() && isGyroOnTheFlyCalEnabled()){
						getOnTheFlyCalGyro().updateGyroOnTheFlyGyroOVCal(getCurrentCalibDetailsGyro(), gyroCalibratedData,
								(double) newPacketInt[iGyroX], (double) newPacketInt[iGyroY], (double) newPacketInt[iGyroZ]);
					}
				}

			}
			
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.MAG) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MAG) > 0)
					) {
				int iMagX=getSignalIndex(Shimmer3.ObjectClusterSensorName.MAG_X);
				int iMagY=getSignalIndex(Shimmer3.ObjectClusterSensorName.MAG_Y);
				int iMagZ=getSignalIndex(Shimmer3.ObjectClusterSensorName.MAG_Z);
				tempData[0]=(double)newPacketInt[iMagX];
				tempData[1]=(double)newPacketInt[iMagY];
				tempData[2]=(double)newPacketInt[iMagZ];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagZ]);
				uncalibratedData[iMagX]=(double)newPacketInt[iMagX];
				uncalibratedData[iMagY]=(double)newPacketInt[iMagY];
				uncalibratedData[iMagZ]=(double)newPacketInt[iMagZ];
				uncalibratedDataUnits[iMagX]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iMagY]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iMagZ]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					double[] magCalibratedData;
//					magCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixMagnetometer, mSensitivityMatrixMagnetometer, mOffsetVectorMagnetometer);
					magCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsMag());
					calibratedData[iMagX]=magCalibratedData[0];
					calibratedData[iMagY]=magCalibratedData[1];
					calibratedData[iMagZ]=magCalibratedData[2];
					
					if(isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[0],mSensorLIS2MDL.mIsUsingDefaultMagParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[1],mSensorLIS2MDL.mIsUsingDefaultMagParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[2],mSensorLIS2MDL.mIsUsingDefaultMagParam);
					}
					else {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[0],mSensorLSM303.mIsUsingDefaultMagParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[1],mSensorLSM303.mIsUsingDefaultMagParam);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[2],mSensorLSM303.mIsUsingDefaultMagParam);
					}
					magnetometer.x=magCalibratedData[0];
					magnetometer.y=magCalibratedData[1];
					magnetometer.z=magCalibratedData[2];
					calibratedDataUnits[iMagX]=CHANNEL_UNITS.MAG_CAL_UNIT;
					calibratedDataUnits[iMagY]=CHANNEL_UNITS.MAG_CAL_UNIT;
					calibratedDataUnits[iMagZ]=CHANNEL_UNITS.MAG_CAL_UNIT;

//					if (mDefaultCalibrationParametersMag == true) {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_DEFAULT_CAL_UNIT,magCalibratedData[0]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_DEFAULT_CAL_UNIT,magCalibratedData[1]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_DEFAULT_CAL_UNIT,magCalibratedData[2]));
//						magnetometer.x=magCalibratedData[0];
//						magnetometer.y=magCalibratedData[1];
//						magnetometer.z=magCalibratedData[2];
//						calibratedDataUnits[iMagX]=CHANNEL_UNITS.MAG_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iMagY]=CHANNEL_UNITS.MAG_DEFAULT_CAL_UNIT;
//						calibratedDataUnits[iMagZ]=CHANNEL_UNITS.MAG_DEFAULT_CAL_UNIT;
//					} else {
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[0]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[1]));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,magCalibratedData[2]));
//						magnetometer.x=magCalibratedData[0];
//						magnetometer.y=magCalibratedData[1];
//						magnetometer.z=magCalibratedData[2];
//						calibratedDataUnits[iMagX]=CHANNEL_UNITS.MAG_CAL_UNIT;
//						calibratedDataUnits[iMagY]=CHANNEL_UNITS.MAG_CAL_UNIT;
//						calibratedDataUnits[iMagZ]=CHANNEL_UNITS.MAG_CAL_UNIT;
//					}
				}
			}

			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.BATTERY) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.BATTERY) > 0)
					) {
				int iBatt = getSignalIndex(Shimmer3.ObjectClusterSensorName.BATTERY);
				tempData[0] = (double)newPacketInt[iBatt];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iBatt]);
				uncalibratedData[iBatt]=(double)newPacketInt[iBatt];
				uncalibratedDataUnits[iBatt]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					calibratedData[iBatt]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1)*2;
					calibratedDataUnits[iBatt] = CHANNEL_UNITS.MILLIVOLTS;
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iBatt]);
					mVSenseBattMA.addValue(calibratedData[iBatt]);
					checkBattery();
					
					mShimmerBattStatusDetails.calculateBattPercentage(calibratedData[iBatt]/1000);
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXT_EXP_A7) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXT_EXP_A7) > 0)
					) {
				int iA7;
				if (mShimmerVerObject.isShimmerGen3R()) {
					iA7 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXT_ADC_0);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_ADC_0,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA7]);
				}
				else {
					iA7 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A7);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A7,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA7]);
				}
				tempData[0] = (double)newPacketInt[iA7];

				uncalibratedData[iA7]=(double)newPacketInt[iA7];
				uncalibratedDataUnits[iA7]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					calibratedData[iA7]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
					if (mShimmerVerObject.isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_ADC_0,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA7]);
					}
					else {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A7,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA7]);
					}
					
					calibratedDataUnits[iA7] = CHANNEL_UNITS.MILLIVOLTS;

				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXT_EXP_A6) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXT_EXP_A6) > 0)
					) {
				int iA6;
				if (mShimmerVerObject.isShimmerGen3R()) {
					iA6 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXT_ADC_1);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_ADC_1,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA6]);
				}
				else {
					iA6 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A6);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A6,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA6]);
				}
				tempData[0] = (double)newPacketInt[iA6];

				uncalibratedData[iA6]=(double)newPacketInt[iA6];
				uncalibratedDataUnits[iA6]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					calibratedData[iA6]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
					if (mShimmerVerObject.isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_ADC_1,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA6]);
					}
					else {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A6,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA6]);
					}
					
					calibratedDataUnits[iA6] = CHANNEL_UNITS.MILLIVOLTS;

				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXT_EXP_A15) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXT_EXP_A15) > 0)
					) {
				int iA15;
				if (mShimmerVerObject.isShimmerGen3R()) {
					iA15 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXT_ADC_2);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_ADC_2,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA15]);
				}
				else {
					iA15 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A15);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A15,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA15]);
				}
				tempData[0] = (double)newPacketInt[iA15];

				uncalibratedData[iA15]=(double)newPacketInt[iA15];
				uncalibratedDataUnits[iA15]=CHANNEL_UNITS.NO_UNITS;

				if (mEnableCalibration){
					calibratedData[iA15]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
					if (mShimmerVerObject.isShimmerGen3R()) {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_ADC_2,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA15]);
					}
					else {
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A15,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA15]);
					}
					
					calibratedDataUnits[iA15] = CHANNEL_UNITS.MILLIVOLTS;

				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.INT_EXP_A1) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.INT_EXP_A1) > 0)
					) {
					int iA1;
					String sensorName;
					if (mShimmerVerObject.isShimmerGen3R()) {
						iA1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_ADC_3);
						sensorName = Shimmer3.ObjectClusterSensorName.INT_ADC_3;
					}
					else {
						iA1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A1);
						sensorName = Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A1;
					}
					tempData[0] = (double)newPacketInt[iA1];
					
					//to Support derived sensor renaming
					if (isSupportedDerivedSensors()){
						//change name based on derived sensor value
						if ((mDerivedSensors & DerivedSensorsBitMask.PPG2_1_14)>0){
							if(isShimmerGen3R()) {
								sensorName = SensorPPG.ObjectClusterSensorName.PPG2_A3;
							} else {
								sensorName = SensorPPG.ObjectClusterSensorName.PPG2_A1;
							}
						}else if ((mDerivedSensors & DerivedSensorsBitMask.RES_AMP)>0){
							sensorName = Shimmer3.ObjectClusterSensorName.RESISTANCE_AMP;
						}else if((mDerivedSensors & DerivedSensorsBitMask.SKIN_TEMP)>0){
							sensorName = Shimmer3.ObjectClusterSensorName.SKIN_TEMPERATURE_PROBE;
						}
						
					}
					sensorNames[iA1]=sensorName;
					objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA1]);
					uncalibratedData[iA1]=(double)newPacketInt[iA1];
					uncalibratedDataUnits[iA1]=CHANNEL_UNITS.NO_UNITS;
					if (mEnableCalibration){
						if((mDerivedSensors & DerivedSensorsBitMask.SKIN_TEMP)>0){
							calibratedData[iA1]=SensorBridgeAmp.calibratePhillipsSkinTemperatureData(tempData[0]);
							calibratedDataUnits[iA1] = CHANNEL_UNITS.DEGREES_CELSIUS;
							objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_CELSIUS,calibratedData[iA1]);
						}
						else{
							calibratedData[iA1]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
							calibratedDataUnits[iA1] = CHANNEL_UNITS.MILLIVOLTS;
							objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA1]);
						}
					}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.INT_EXP_A12) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.INT_EXP_A12) > 0)
					) {
				int iA12;
				String sensorName;
				if (mShimmerVerObject.isShimmerGen3R()) {
					iA12 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_ADC_0);
					sensorName = Shimmer3.ObjectClusterSensorName.INT_ADC_0;
				}
				else {
					iA12 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A12);
					sensorName = Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A12;
				}
				tempData[0] = (double)newPacketInt[iA12];
				//to Support derived sensor renaming
				if (isSupportedDerivedSensors()){
					//change name based on derived sensor value
					if ((mDerivedSensors & DerivedSensorsBitMask.PPG_12_13)>0){
						if (isShimmerGen3R()) {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG_A0;
						} else {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG_A12;
						}
					} else if ((mDerivedSensors & DerivedSensorsBitMask.PPG1_12_13)>0){
						if (isShimmerGen3R()) {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG1_A0;
						} else {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG1_A12;
						}
					}

				}
				
				sensorNames[iA12]=sensorName;
				objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA12]);
				uncalibratedData[iA12]=(double)newPacketInt[iA12];
				uncalibratedDataUnits[iA12]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					calibratedData[iA12]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
					objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA12]);
					calibratedDataUnits[iA12] = CHANNEL_UNITS.MILLIVOLTS;

				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.INT_EXP_A13) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.INT_EXP_A13) > 0)
					) {
				int iA13;
				String sensorName;
				if (mShimmerVerObject.isShimmerGen3R()) {
					iA13 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_ADC_1);
					sensorName = Shimmer3.ObjectClusterSensorName.INT_ADC_1;
				}
				else {
					iA13 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A13);
					sensorName = Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A13;
				}
				tempData[0] = (double)newPacketInt[iA13];
				//to Support derived sensor renaming
				if (isSupportedDerivedSensors()){
					//change name based on derived sensor value
					if ((mDerivedSensors & DerivedSensorsBitMask.PPG_12_13)>0){
						if(isShimmerGen3R()) {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG_A1;
						} else {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG_A13;
						}
					} else if ((mDerivedSensors & DerivedSensorsBitMask.PPG1_12_13)>0){
						if(isShimmerGen3R()) {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG1_A1;
						} else {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG1_A13;
						}
					}

				}
				
				sensorNames[iA13]=sensorName;
				objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA13]);
				uncalibratedData[iA13]=(double)newPacketInt[iA13];
				uncalibratedDataUnits[iA13]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					calibratedData[iA13]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
					objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA13]);
					calibratedDataUnits[iA13] = CHANNEL_UNITS.MILLIVOLTS;
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.INT_EXP_A14) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.INT_EXP_A14) > 0)
					) {
				int iA14;
				String sensorName;
				if (mShimmerVerObject.isShimmerGen3R()) {
					iA14 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_ADC_2);
					sensorName = Shimmer3.ObjectClusterSensorName.INT_ADC_2;
				}
				else {
					iA14 = getSignalIndex(Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A14);
					sensorName = Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A14;
				}
				tempData[0] = (double)newPacketInt[iA14];
				//to Support derived sensor renaming
				if (isSupportedDerivedSensors()){
					//change name based on derived sensor value
					if ((mDerivedSensors & DerivedSensorsBitMask.PPG2_1_14)>0){
						if(isShimmerGen3R()) {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG2_A2;
						}
						else {
							sensorName = SensorPPG.ObjectClusterSensorName.PPG2_A14;
						}
					}
				}
				sensorNames[iA14]=sensorName;
				objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA14]);
				uncalibratedData[iA14]=(double)newPacketInt[iA14];
				uncalibratedDataUnits[iA14]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					calibratedData[iA14]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
					objectCluster.addDataToMap(sensorName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA14]);
					calibratedDataUnits[iA14] = CHANNEL_UNITS.MILLIVOLTS;
				}
			}
			//((fwIdentifier == FW_IDEN_BTSTREAM) && (mEnabledSensors & BTStream.ACCEL_WR) > 0) 
			//|| ((fwIdentifier == FW_IDEN_SD) && (mEnabledSensors & SDLogHeader.ACCEL_WR) > 0)
			if(((fwType == COMMUNICATION_TYPE.BLUETOOTH)&&((mEnabledSensors & BTStream.ACCEL_LN) > 0 || (mEnabledSensors & BTStream.ACCEL_WR) > 0) && ((mEnabledSensors & BTStream.GYRO) > 0) && ((mEnabledSensors & BTStream.MAG) > 0) && is3DOrientationEnabled() )
					||((fwType == COMMUNICATION_TYPE.SD)&&((mEnabledSensors & SDLogHeader.ACCEL_LN) > 0 || (mEnabledSensors & SDLogHeader.ACCEL_WR) > 0) && ((mEnabledSensors & SDLogHeader.GYRO) > 0) && ((mEnabledSensors & SDLogHeader.MAG) > 0) && is3DOrientationEnabled() )){

				if (mEnableCalibration){
					if (mOrientationAlgo==null){
						mOrientationAlgo = new GradDes3DOrientation((double)1/getSamplingRateShimmer());
//						mOrientationAlgo = new GradDes3DOrientation(0.4, (double)1/getSamplingRateShimmer(), 1, 0, 0,0);
					}
					//New 2016-05-31
					Orientation3DObject q = mOrientationAlgo.update(accelerometer.x,accelerometer.y,accelerometer.z, gyroscope.x,gyroscope.y,gyroscope.z, magnetometer.x,magnetometer.y,magnetometer.z);
					//Old 2016-05-31
//					Quaternion q = mOrientationAlgo.update(accelerometer.x,accelerometer.y,accelerometer.z, gyroscope.x,gyroscope.y,gyroscope.z, magnetometer.x,magnetometer.y,magnetometer.z);
					double theta, Rx, Ry, Rz, rho;
//					rho = Math.acos(q.q1);
//					theta = rho * 2;
//					Rx = q.q2 / Math.sin(rho);
//					Ry = q.q3 / Math.sin(rho);
//					Rz = q.q4 / Math.sin(rho);
					
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_A,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getTheta());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getAngleX());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getAngleY());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getAngleZ());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_W,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionW());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionX());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionY());
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionZ());
					
					sensorNamesAlgo[indexAlgo] = Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_A;
					sensorNamesAlgo[indexAlgo+1] = Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_X;
					sensorNamesAlgo[indexAlgo+2] = Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Y;
					sensorNamesAlgo[indexAlgo+3] = Shimmer3.ObjectClusterSensorName.AXIS_ANGLE_9DOF_Z;
					sensorNamesAlgo[indexAlgo+4] = Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_W;
					sensorNamesAlgo[indexAlgo+5] = Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_X;
					sensorNamesAlgo[indexAlgo+6] = Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y;
					sensorNamesAlgo[indexAlgo+7] = Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z;
					
					calDataUnitsAlgo[indexAlgo] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+1] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+2] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+3] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+4] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+5] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+6] = CHANNEL_UNITS.LOCAL;
					calDataUnitsAlgo[indexAlgo+7] = CHANNEL_UNITS.LOCAL;
					
					calDataAlgo[indexAlgo] = q.getTheta();
					calDataAlgo[indexAlgo+1] = q.getAngleX();
					calDataAlgo[indexAlgo+2] = q.getAngleY();
					calDataAlgo[indexAlgo+3] = q.getAngleZ();
					calDataAlgo[indexAlgo+4] = q.getQuaternionW();
					calDataAlgo[indexAlgo+5] = q.getQuaternionX();
					calDataAlgo[indexAlgo+6] = q.getQuaternionY();
					calDataAlgo[indexAlgo+7] = q.getQuaternionZ();
					
					indexAlgo += 7;
				}
			}

			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXG1_24BIT) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXG1_24BIT) > 0)){
				int iexg1ch1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT);
				int iexg1ch2 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT);
				int iexg1sta = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_STATUS);
				double exg1ch1 = (double)newPacketInt[iexg1ch1];
				double exg1ch2 = (double)newPacketInt[iexg1ch2];
				double exg1sta = (double)newPacketInt[iexg1sta];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_STATUS,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1sta);
				uncalibratedData[iexg1sta]=(double)newPacketInt[iexg1sta];
				uncalibratedData[iexg1ch1]=(double)newPacketInt[iexg1ch1];
				uncalibratedData[iexg1ch2]=(double)newPacketInt[iexg1ch2];
				uncalibratedDataUnits[iexg1sta]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg1ch1]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg1ch2]=CHANNEL_UNITS.NO_UNITS;
				
				//TODO objectcluster will not be populated at all if mEnableCalibration is not enabled
				if (mEnableCalibration){
					//System.err.println("getExg1CH1GainValue(): " +getExg1CH1GainValue());
					double calexg1ch1 = exg1ch1 *(((2.42*1000)/getExg1CH1GainValue())/(Math.pow(2,23)-1));
					double calexg1ch2 = exg1ch2 *(((2.42*1000)/getExg1CH2GainValue())/(Math.pow(2,23)-1));
					calibratedData[iexg1ch1]=calexg1ch1;
					calibratedData[iexg1ch2]=calexg1ch2;
					calibratedData[iexg1sta]=(double)newPacketInt[iexg1sta];
					calibratedDataUnits[iexg1sta]=CHANNEL_UNITS.NO_UNITS;
					calibratedDataUnits[iexg1ch1]=CHANNEL_UNITS.MILLIVOLTS;
					calibratedDataUnits[iexg1ch2]=CHANNEL_UNITS.MILLIVOLTS;
					
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_STATUS,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1sta);
					
					if (isEXGUsingDefaultECGConfiguration()||isEXGUsingDefaultRespirationConfiguration()||isEXGUsingDefaultECGGqConfiguration()){
//						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT;
//						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT;
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
//						if(getFirmwareIdentifier()==FW_ID.GQ_802154){
//							objectCluster.addData(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
//							objectCluster.addData(Shimmer3.ObjectClusterSensorName.ECG_LA_RL_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
//						} else {
//							objectCluster.addData(Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
//							objectCluster.addData(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
//						}

//						//Even thought the below code is more accurate then the above, DatabaseHandler.parseFromGUIChannelsToDBColumn() relies on the sensorNames[] entries being the same as Shimmer3 for GQ, it doesn't matter what's in the objectcluster.
						if(isShimmerGenGq()){
//							sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT;
//							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
//							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
							sensorNames[iexg1ch1]=SensorEXG.ObjectClusterSensorName.ECG_GQ;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_GQ,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_GQ,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
							
							sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.ECG_LA_RL_24BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RL_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RL_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
						} else {
							sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
							
							sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
						}
					} 
					else if (isEXGUsingDefaultEMGConfiguration()){
						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.EMG_CH1_24BIT;
						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.EMG_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
					} 
					else if (isEXGUsingDefaultTestSignalConfiguration()){
						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_24BIT;
						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
					} 
					else {
						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT;
						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
					}
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXG2_24BIT) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXG2_24BIT) > 0)){
				int iexg2ch1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT);
				int iexg2ch2 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT);
				int iexg2sta = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG2_STATUS);
				double exg2ch1 = (double)newPacketInt[iexg2ch1];
				double exg2ch2 = (double)newPacketInt[iexg2ch2];
				double exg2sta = (double)newPacketInt[iexg2sta];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_STATUS,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2sta);
				uncalibratedData[iexg2sta]=(double)newPacketInt[iexg2sta];
				uncalibratedData[iexg2ch1]=(double)newPacketInt[iexg2ch1];
				uncalibratedData[iexg2ch2]=(double)newPacketInt[iexg2ch2];
				uncalibratedDataUnits[iexg2sta]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg2ch1]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg2ch2]=CHANNEL_UNITS.NO_UNITS;
				
				//TODO objectcluster will not be populated at all if mEnableCalibration is not enabled
				if (mEnableCalibration){
					double calexg2ch1 = exg2ch1 *(((2.42*1000)/getExg2CH1GainValue())/(Math.pow(2,23)-1));
					double calexg2ch2 = exg2ch2 *(((2.42*1000)/getExg2CH2GainValue())/(Math.pow(2,23)-1));
					calibratedData[iexg2ch1]=calexg2ch1;
					calibratedData[iexg2ch2]=calexg2ch2;
					calibratedData[iexg2sta]=(double)newPacketInt[iexg2sta];
					calibratedDataUnits[iexg2sta]=CHANNEL_UNITS.NO_UNITS;
					calibratedDataUnits[iexg2ch1]=CHANNEL_UNITS.MILLIVOLTS;
					calibratedDataUnits[iexg2ch2]=CHANNEL_UNITS.MILLIVOLTS;
					
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_STATUS,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2sta);
					
					if (isEXGUsingDefaultECGConfiguration()||isEXGUsingDefaultRespirationConfiguration()){
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.ECG_VX_RL_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_VX_RL_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_VX_RL_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);
//						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT;
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1));
						if (isEXGUsingDefaultRespirationConfiguration()){
							sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.ECG_RESP_24BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_RESP_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_RESP_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1);
						}
					}
					else if (isEXGUsingDefaultEMGConfiguration()){
						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT;
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,0);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,0);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,0);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,0);
					} 
					else if (isEXGUsingDefaultTestSignalConfiguration()){
						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_24BIT;
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);
					} 
					else if (isEXGUsingDefaultThreeUnipolarConfiguration()){
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);
					} 
					else {
						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT;
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1); 
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);	
					}
				}
			}
			
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXG1_16BIT) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXG1_16BIT) > 0)
					){
				int iexg1ch1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT);
				int iexg1ch2 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT);
				int iexg1sta = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_STATUS);
				double exg1ch1 = (double)newPacketInt[iexg1ch1];
				double exg1ch2 = (double)newPacketInt[iexg1ch2];
				double exg1sta = (double)newPacketInt[iexg1sta];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_STATUS,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1sta);
				uncalibratedData[iexg1sta]=(double)newPacketInt[iexg1sta];
				uncalibratedData[iexg1ch1]=(double)newPacketInt[iexg1ch1];
				uncalibratedData[iexg1ch2]=(double)newPacketInt[iexg1ch2];
				uncalibratedDataUnits[iexg1sta]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg1ch1]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg1ch2]=CHANNEL_UNITS.NO_UNITS;
				
				//TODO objectcluster will not be populated at all if mEnableCalibration is not enabled
				if (mEnableCalibration){
					double calexg1ch1 = exg1ch1 *(((2.42*1000)/(getExg1CH1GainValue()*2))/(Math.pow(2,15)-1));
					double calexg1ch2 = exg1ch2 *(((2.42*1000)/(getExg1CH2GainValue()*2))/(Math.pow(2,15)-1));
					calibratedData[iexg1ch1]=calexg1ch1;
					calibratedData[iexg1ch2]=calexg1ch2;
					calibratedData[iexg1sta]=(double)newPacketInt[iexg1sta];
					calibratedDataUnits[iexg1sta]=CHANNEL_UNITS.NO_UNITS;
					calibratedDataUnits[iexg1ch1]=CHANNEL_UNITS.MILLIVOLTS;
					calibratedDataUnits[iexg1ch2]=CHANNEL_UNITS.MILLIVOLTS;
					
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_STATUS,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1sta);
					
					if (isEXGUsingDefaultECGConfiguration()||isEXGUsingDefaultRespirationConfiguration()){
						
						if(isShimmerGenGq()){
//							sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT;
//							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
//							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
							sensorNames[iexg1ch1]=SensorEXG.ObjectClusterSensorName.ECG_GQ;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_GQ,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_GQ,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
							
							sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.ECG_LA_RL_16BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RL_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RL_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
						}
						else {
							sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.ECG_LL_RA_16BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_RA_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_RA_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
							
							sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.ECG_LA_RA_16BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LA_RA_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
						}
					} 
					else if (isEXGUsingDefaultEMGConfiguration()){
						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.EMG_CH1_16BIT;
						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.EMG_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EMG_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
					} 
					else if (isEXGUsingDefaultTestSignalConfiguration()){
						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_16BIT;
						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP1_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
					} 
					else {
						sensorNames[iexg1ch1]=Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT;
						sensorNames[iexg1ch2]=Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg1ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg1ch2);
					}
				}
			}
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXG2_16BIT) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXG2_16BIT) > 0)
					){
				int iexg2ch1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT);
				int iexg2ch2 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT);
				int iexg2sta = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG2_STATUS);
				double exg2ch1 = (double)newPacketInt[iexg2ch1];
				double exg2ch2 = (double)newPacketInt[iexg2ch2];
				double exg2sta = (double)newPacketInt[iexg2sta];
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_STATUS,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2sta);
				uncalibratedData[iexg2sta]=(double)newPacketInt[iexg2sta];
				uncalibratedData[iexg2ch1]=(double)newPacketInt[iexg2ch1];
				uncalibratedData[iexg2ch2]=(double)newPacketInt[iexg2ch2];
				uncalibratedDataUnits[iexg2sta]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg2ch1]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iexg2ch2]=CHANNEL_UNITS.NO_UNITS;
				
				//TODO objectcluster will not be populated at all if mEnableCalibration is not enabled
				if (mEnableCalibration){
					double calexg2ch1 = ((exg2ch1)) *(((2.42*1000)/(getExg2CH1GainValue()*2))/(Math.pow(2,15)-1));
					double calexg2ch2 = ((exg2ch2)) *(((2.42*1000)/(getExg2CH2GainValue()*2))/(Math.pow(2,15)-1));
					calibratedData[iexg2ch1]=calexg2ch1;
					calibratedData[iexg2ch2]=calexg2ch2;
					calibratedData[iexg2sta]=(double)newPacketInt[iexg2sta];
					calibratedDataUnits[iexg2sta]=CHANNEL_UNITS.NO_UNITS;
					calibratedDataUnits[iexg2ch1]=CHANNEL_UNITS.MILLIVOLTS;
					calibratedDataUnits[iexg2ch2]=CHANNEL_UNITS.MILLIVOLTS;
					
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_STATUS,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2sta);

					
					if (isEXGUsingDefaultECGConfiguration()||isEXGUsingDefaultRespirationConfiguration()){
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.ECG_VX_RL_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_VX_RL_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_VX_RL_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);
//						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT;
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1));
//						objectCluster.addData(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1));
						if (isEXGUsingDefaultRespirationConfiguration()){
							sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.ECG_RESP_16BIT;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_RESP_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1);
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_RESP_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1);
						}
					}
					else if (isEXGUsingDefaultEMGConfiguration()){
						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT;
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,0);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,0);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,0);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,0);
					} 
					else if (isEXGUsingDefaultTestSignalConfiguration()){
						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_16BIT;
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG_TEST_CHIP2_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);
					} 
					else if (isEXGUsingDefaultThreeUnipolarConfiguration()){
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);
					} 
					else {
						sensorNames[iexg2ch1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT;
						sensorNames[iexg2ch2]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT;
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,exg2ch2);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch1);
						objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calexg2ch2);	
					}
				}
			}

			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.BMP180) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.BMPX80) > 0)){

				String signalNameBmpX80Temperature = SensorBMP180.ObjectClusterSensorName.TEMPERATURE_BMP180;
				String signalNameBmpX80Pressure = SensorBMP180.ObjectClusterSensorName.PRESSURE_BMP180;
				if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
					signalNameBmpX80Temperature = SensorBMP390.ObjectClusterSensorName.TEMPERATURE_BMP390;
					signalNameBmpX80Pressure = SensorBMP390.ObjectClusterSensorName.PRESSURE_BMP390;
				} else if(isSupportedBmp280()){
					signalNameBmpX80Temperature = SensorBMP280.ObjectClusterSensorName.TEMPERATURE_BMP280;
					signalNameBmpX80Pressure = SensorBMP280.ObjectClusterSensorName.PRESSURE_BMP280;
				}

				int iUT = getSignalIndex(signalNameBmpX80Temperature);
				int iUP = getSignalIndex(signalNameBmpX80Pressure);
				double UT = (double)newPacketInt[iUT];
				double UP = (double)newPacketInt[iUP];
				
				objectCluster.addDataToMap(signalNameBmpX80Pressure,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,UP);
				objectCluster.addDataToMap(signalNameBmpX80Temperature,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,UT);
				uncalibratedData[iUT]=(double)newPacketInt[iUT];
				uncalibratedData[iUP]=(double)newPacketInt[iUP];
				uncalibratedDataUnits[iUT]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iUP]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					double[] bmp180caldata = mSensorBMPX80.calibratePressureSensorData(UP,UT);
					objectCluster.addDataToMap(signalNameBmpX80Pressure,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.KPASCAL,bmp180caldata[0]/1000);
					objectCluster.addDataToMap(signalNameBmpX80Temperature,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_CELSIUS,bmp180caldata[1]);
					calibratedData[iUT]=bmp180caldata[1];
					calibratedData[iUP]=bmp180caldata[0]/1000;
					calibratedDataUnits[iUT]=CHANNEL_UNITS.DEGREES_CELSIUS;
					calibratedDataUnits[iUP]=CHANNEL_UNITS.KPASCAL;
				}
			}

			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.BRIDGE_AMP) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.BRIDGE_AMP) > 0)
					) {
				int iBAHigh = getSignalIndex(Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_HIGH);
				int iBALow = getSignalIndex(Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_LOW);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_HIGH,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iBAHigh]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_LOW,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iBALow]);
				uncalibratedData[iBAHigh]=(double)newPacketInt[iBAHigh];
				uncalibratedData[iBALow]=(double)newPacketInt[iBALow];
				uncalibratedDataUnits[iBAHigh]=CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iBALow]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iBAHigh];
					tempData[1] = (double)newPacketInt[iBALow];
					calibratedData[iBAHigh]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],60,3,551);
					calibratedData[iBALow]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[1],1950,3,183.7);
					calibratedDataUnits[iBAHigh]=CHANNEL_UNITS.MILLIVOLTS;
					calibratedDataUnits[iBALow]=CHANNEL_UNITS.MILLIVOLTS;
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_HIGH,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iBAHigh]);
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_LOW,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iBALow]);	
				}
			}
			
			if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.GSR) > 0) 
					|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.GSR) > 0)
					) {
				String mainGsrSignalName = SensorGSR.ObjectClusterSensorName.GSR_RESISTANCE;
				if(isShimmerGenGq()){
					mainGsrSignalName = SensorGSR.ObjectClusterSensorName.GSR_GQ;
				}
				
				int iGSR = getSignalIndex(mainGsrSignalName);
//				int newGSRRange = -1; // initialized to -1 so it will only come into play if mGSRRange = 4  

				tempData[0] = (double)newPacketInt[iGSR];
				int gsrAdcValueUnCal = ((int)tempData[0] & 4095); 
				
				int currentGSRRange = getGSRRange();
				 // this is to fix a bug with SDLog v0.9
				if (getFirmwareIdentifier()==FW_ID.SDLOG && getFirmwareVersionMajor()==0 && getFirmwareVersionMinor()==9){

					/*
					 * 	for i = 2:length(range)
    				 *		if range(i-1)~=range(i)
        			 *			if abs(gsrValue(i-1)-gsrValue(i))< 300
            		 *				range(i) = range(i-1);
        			 *			end
    				 *		end
					 *	end
					 */
					
					if (currentGSRRange==4){
						currentGSRRange=(49152 & (int)tempData[0])>>14;
						if (mPastGSRFirstTime){
							mPastGSRRange = currentGSRRange;
							mPastGSRFirstTime = false;
						}
						if (currentGSRRange != mPastGSRRange){
							if (Math.abs(mPastGSRUncalibratedValue-gsrAdcValueUnCal)<300){
								currentGSRRange = mPastGSRRange;
							} 
							else {
								mPastGSRRange = currentGSRRange;
							}
							mPastGSRUncalibratedValue = gsrAdcValueUnCal;
						}
					}
				} 
				else {
					//Note that from FW (SDLog?) v1.0 onwards the MSB of the GSR data contains the range
					if (currentGSRRange==4){
						//Mask upper 2 bits of the 16-bit packet and then bit shift down
						currentGSRRange=(49152 & (int)tempData[0])>>14; 
					}
				}


				if(mChannelMap.get(SensorGSR.ObjectClusterSensorName.GSR_RANGE)!=null){
					objectCluster.addCalDataToMap(SensorGSR.channelGsrRange,currentGSRRange);
					objectCluster.addUncalDataToMap(SensorGSR.channelGsrRange,currentGSRRange);
				}
				if(SensorGSR.sensorGsrRef.mListOfChannelsRef.contains(SensorGSR.channelGsrAdc.mObjectClusterName)){
					objectCluster.addUncalDataToMap(SensorGSR.channelGsrAdc, gsrAdcValueUnCal);
					objectCluster.addCalDataToMap(SensorGSR.channelGsrAdc, SensorADC.calibrateMspAdcChannelToMillivolts(gsrAdcValueUnCal));
				}
				
				objectCluster.addDataToMap(mainGsrSignalName,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,gsrAdcValueUnCal);
				uncalibratedData[iGSR] = gsrAdcValueUnCal;
				uncalibratedDataUnits[iGSR]=CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					double gsrResistanceKOhms = 0.0;
					double gsrConductanceUSiemens = 0.0;
					if(currentGSRRange==3 && gsrAdcValueUnCal<SensorGSR.GSR_UNCAL_LIMIT_RANGE3) {
						gsrAdcValueUnCal = SensorGSR.GSR_UNCAL_LIMIT_RANGE3;
					}
					gsrResistanceKOhms = SensorGSR.calibrateGsrDataToKOhmsUsingAmplifierEq(gsrAdcValueUnCal, currentGSRRange, MICROCONTROLLER_ADC_PROPERTIES.SHIMMER2R3_3V0, SensorGSR.SHIMMER3_GSR_REF_RESISTORS_KOHMS);
					gsrResistanceKOhms = SensorGSR.nudgeGsrResistance(gsrResistanceKOhms, getGSRRange(), SensorGSR.SHIMMER3_GSR_RESISTANCE_MIN_MAX_KOHMS);
					gsrConductanceUSiemens = (1.0/gsrResistanceKOhms)*1000;
					
					//If ShimmerGQ we only want to have one GSR channel and it's units should be 'uS'
					if(isShimmerGenGq()){
						calibratedData[iGSR] = gsrConductanceUSiemens;
						calibratedDataUnits[iGSR]=CHANNEL_UNITS.U_SIEMENS;
						objectCluster.addDataToMap(mainGsrSignalName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.U_SIEMENS,gsrConductanceUSiemens);
					}
					else {
						calibratedData[iGSR] = gsrResistanceKOhms;
						calibratedDataUnits[iGSR]=CHANNEL_UNITS.KOHMS;
						objectCluster.addDataToMap(mainGsrSignalName,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.KOHMS,gsrResistanceKOhms);
						
						if(mChannelMap.get(SensorGSR.ObjectClusterSensorName.GSR_CONDUCTANCE)!=null){
							objectCluster.addUncalDataToMap(SensorGSR.channelGsrMicroSiemens,gsrAdcValueUnCal);
							objectCluster.addCalDataToMap(SensorGSR.channelGsrMicroSiemens,gsrConductanceUSiemens);

//							System.out.println("mGSRRange:" + mGSRRange + "\tnewGSRRange" + newGSRRange + "\tp1:" + p1 + "\tp2" + p2);
//							System.out.println("p1:" + p1 + "\tp2" + p2 + "\tADC:" + gsrAdcValueUnCal + "\tRes:" + calibratedData[iGSR] + "\tSie:" + SensorGSR.calibrateGsrDataToSiemens(gsrAdcValueUnCal,p1,p2));
						}
					}
				}

			}

			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.GYRO_MPU_MPL) > 0)){
				int iGyroX = getSignalIndex(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_X);
				int iGyroY = getSignalIndex(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Y);
				int iGyroZ = getSignalIndex(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Z);
				calibratedData[iGyroX] = (double)newPacketInt[iGyroX]/Math.pow(2, 16);
				calibratedData[iGyroY] = (double)newPacketInt[iGyroY]/Math.pow(2, 16);
				calibratedData[iGyroZ] = (double)newPacketInt[iGyroZ]/Math.pow(2, 16);
				calibratedDataUnits[iGyroX] = CHANNEL_UNITS.GYRO_CAL_UNIT;
				calibratedDataUnits[iGyroY] = CHANNEL_UNITS.GYRO_CAL_UNIT;
				calibratedDataUnits[iGyroZ] = CHANNEL_UNITS.GYRO_CAL_UNIT;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,calibratedData[iGyroX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,calibratedData[iGyroY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GYRO_CAL_UNIT,calibratedData[iGyroZ]);
				uncalibratedData[iGyroX] = (double)newPacketInt[iGyroX];
				uncalibratedData[iGyroY] = (double)newPacketInt[iGyroY];
				uncalibratedData[iGyroZ] = (double)newPacketInt[iGyroZ];
				uncalibratedDataUnits[iGyroX] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iGyroY] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iGyroZ] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iGyroX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iGyroY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.GYRO_MPU_MPL_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iGyroZ]);
			}

			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.ACCEL_MPU_MPL) > 0)){
				int iAccelX = getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_X);
				int iAccelY = getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Y);
				int iAccelZ = getSignalIndex(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Z);
//				calibratedData[iAccelX] = ((double)newPacketInt[iAccelX]/Math.pow(2, 16))*ACCELERATION_DUE_TO_GRAVITY;
//				calibratedData[iAccelY] = ((double)newPacketInt[iAccelY]/Math.pow(2, 16))*ACCELERATION_DUE_TO_GRAVITY;
//				calibratedData[iAccelZ] = ((double)newPacketInt[iAccelZ]/Math.pow(2, 16))*ACCELERATION_DUE_TO_GRAVITY;
//				calibratedDataUnits[iAccelX] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
//				calibratedDataUnits[iAccelY] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
//				calibratedDataUnits[iAccelZ] = CHANNEL_UNITS.ACCEL_CAL_UNIT;
				calibratedData[iAccelX] = (double)newPacketInt[iAccelX]/Math.pow(2, 16);
				calibratedData[iAccelY] = (double)newPacketInt[iAccelY]/Math.pow(2, 16);
				calibratedData[iAccelZ] = (double)newPacketInt[iAccelZ]/Math.pow(2, 16);
				calibratedDataUnits[iAccelX] = CHANNEL_UNITS.GRAVITY;
				calibratedDataUnits[iAccelY] = CHANNEL_UNITS.GRAVITY;
				calibratedDataUnits[iAccelZ] = CHANNEL_UNITS.GRAVITY;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GRAVITY,calibratedData[iAccelX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GRAVITY,calibratedData[iAccelY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.GRAVITY,calibratedData[iAccelZ]);
				uncalibratedData[iAccelX] = (double)newPacketInt[iAccelX];
				uncalibratedData[iAccelY] = (double)newPacketInt[iAccelX];
				uncalibratedData[iAccelZ] = (double)newPacketInt[iAccelX];
				uncalibratedDataUnits[iAccelX] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelY] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iAccelZ] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iAccelX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iAccelY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ACCEL_MPU_MPL_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iAccelZ]);
			}

			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MAG_MPU_MPL) > 0)){
				int iMagX = getSignalIndex(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_X);
				int iMagY = getSignalIndex(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Y);
				int iMagZ = getSignalIndex(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Z);
				calibratedData[iMagX] = (double)newPacketInt[iMagX]/Math.pow(2, 16);
				calibratedData[iMagY] = (double)newPacketInt[iMagY]/Math.pow(2, 16);
				calibratedData[iMagZ] = (double)newPacketInt[iMagZ]/Math.pow(2, 16);
				calibratedDataUnits[iMagX] = CHANNEL_UNITS.MAG_CAL_UNIT;
				calibratedDataUnits[iMagY] = CHANNEL_UNITS.MAG_CAL_UNIT;
				calibratedDataUnits[iMagZ] = CHANNEL_UNITS.MAG_CAL_UNIT;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,calibratedData[iMagX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,calibratedData[iMagY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MAG_CAL_UNIT,calibratedData[iMagZ]);
				uncalibratedData[iMagX] = (double)newPacketInt[iMagX];
				uncalibratedData[iMagY] = (double)newPacketInt[iMagY];
				uncalibratedData[iMagZ] = (double)newPacketInt[iMagZ];
				uncalibratedDataUnits[iMagX] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iMagY] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iMagZ] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iMagX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iMagY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MAG_MPU_MPL_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iMagZ]);
			}

			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MPL_QUAT_6DOF) > 0)){
				int iQW = getSignalIndex(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_W);
				int iQX = getSignalIndex(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_X);
				int iQY = getSignalIndex(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Y);
				int iQZ = getSignalIndex(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Z);
				calibratedData[iQW] = (double)newPacketInt[iQW]/Math.pow(2, 30);
				calibratedData[iQX] = (double)newPacketInt[iQX]/Math.pow(2, 30);
				calibratedData[iQY] = (double)newPacketInt[iQY]/Math.pow(2, 30);
				calibratedData[iQZ] = (double)newPacketInt[iQZ]/Math.pow(2, 30);
				calibratedDataUnits[iQW] = CHANNEL_UNITS.NO_UNITS;
				calibratedDataUnits[iQX] = CHANNEL_UNITS.NO_UNITS;
				calibratedDataUnits[iQY] = CHANNEL_UNITS.NO_UNITS;
				calibratedDataUnits[iQZ] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_W,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iQW]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iQX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iQY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iQZ]);
				uncalibratedData[iQW] = (double)newPacketInt[iQW];
				uncalibratedData[iQX] = (double)newPacketInt[iQX];
				uncalibratedData[iQY] = (double)newPacketInt[iQY];
				uncalibratedData[iQZ] = (double)newPacketInt[iQZ];
				uncalibratedDataUnits[iQW] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iQX] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iQY] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iQZ] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_W,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iQW]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iQX]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iQY]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.QUAT_MPL_6DOF_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iQZ]);
			}
			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MPL_TEMPERATURE) > 0)){
				int iT = getSignalIndex(Shimmer3.ObjectClusterSensorName.MPL_TEMPERATURE);
				calibratedData[iT] = (double)newPacketInt[iT]/Math.pow(2, 16);
				calibratedDataUnits[iT] = CHANNEL_UNITS.DEGREES_CELSIUS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_TEMPERATURE,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_CELSIUS,calibratedData[iT]);
				uncalibratedData[iT] = (double)newPacketInt[iT];
				uncalibratedDataUnits[iT] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_TEMPERATURE,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iT]);
			}
			
			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MPL_PEDOMETER) > 0)){
				int iPedoCnt = getSignalIndex(Shimmer3.ObjectClusterSensorName.MPL_PEDOM_CNT);
				int iPedoTime = getSignalIndex(Shimmer3.ObjectClusterSensorName.MPL_PEDOM_TIME);
				calibratedData[iPedoCnt] = (double)newPacketInt[iPedoCnt];
				calibratedData[iPedoTime] = (double)newPacketInt[iPedoTime];
				calibratedDataUnits[iPedoCnt] = CHANNEL_UNITS.NO_UNITS;
				calibratedDataUnits[iPedoTime] = CHANNEL_UNITS.MILLISECONDS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_PEDOM_CNT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iPedoCnt]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_PEDOM_TIME,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLISECONDS,calibratedData[iPedoTime]);
				uncalibratedData[iPedoCnt] = (double)newPacketInt[iPedoCnt];
				uncalibratedData[iPedoTime] = (double)newPacketInt[iPedoTime];
				uncalibratedDataUnits[iPedoCnt] = CHANNEL_UNITS.NO_UNITS;
				uncalibratedDataUnits[iPedoTime] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_PEDOM_CNT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iPedoCnt]);
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_PEDOM_TIME,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iPedoTime]);
			}
			
			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MPL_HEADING) > 0)){
				int iH = getSignalIndex(Shimmer3.ObjectClusterSensorName.MPL_HEADING);
				calibratedData[iH] = (double)newPacketInt[iH]/Math.pow(2, 16);
				calibratedDataUnits[iH] = CHANNEL_UNITS.DEGREES;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_HEADING,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES,calibratedData[iH]);
				uncalibratedData[iH] = (double)newPacketInt[iH];
				uncalibratedDataUnits[iH] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MPL_HEADING,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iH]);
			}

			//TODO: separate out tap dir and cnt to two channels 
			//Bits 7-5 - Direction,	Bits 4-0 - Count
			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MPL_TAP) > 0)){
				int iTap = getSignalIndex(Shimmer3.ObjectClusterSensorName.TAPDIRANDTAPCNT);
//				calibratedData[iTapCnt] = (double)(newPacketInt[iPedoCnt]&0x1F);
//				calibratedData[iTapDir] = (double)((newPacketInt[iPedoTime]>>5)&0x07);
				calibratedData[iTap] = (double)newPacketInt[iTap];
				calibratedDataUnits[iTap] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.TAPDIRANDTAPCNT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iTap]);
				uncalibratedData[iTap] = (double)newPacketInt[iTap];
				uncalibratedDataUnits[iTap] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.TAPDIRANDTAPCNT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iTap]);
			}
			
			//TODO: separate out motion and orientation to two channels
			//Bit 7 - Motion/No motion,	Bits 5-4 - Display Orientation,	Bits 3-1 - Orientation,	Bit 0 - Flip indicator
			if (((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.MPL_MOTION_ORIENT) > 0)){
				int iMotOrient = getSignalIndex(Shimmer3.ObjectClusterSensorName.MOTIONANDORIENT);
				calibratedData[iMotOrient] = (double)newPacketInt[iMotOrient];
				calibratedDataUnits[iMotOrient] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MOTIONANDORIENT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.NO_UNITS,calibratedData[iMotOrient]);
				uncalibratedData[iMotOrient] = (double)newPacketInt[iMotOrient];
				uncalibratedDataUnits[iMotOrient] = CHANNEL_UNITS.NO_UNITS;
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.MOTIONANDORIENT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[iMotOrient]);
			}
			
			if ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.ECG_TO_HR_FW) > 0){
				int sigIndex = getSignalIndex(Shimmer3.ObjectClusterSensorName.ECG_TO_HR_FW);
				
				double ecgToHrFW = (double)newPacketInt[sigIndex];
				//Substitute 255 (i.e., invalid HR from FW) in SD data parsing for a -1 (which the SW normally gives as invalid HR)
				if(ecgToHrFW==255) {
					ecgToHrFW = SensorECGToHRFw.INVALID_HR_SUBSTITUTE;
				}
				
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_TO_HR_FW,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.BEATS_PER_MINUTE,ecgToHrFW);
//				uncalibratedData[sigIndex]=(double)newPacketInt[sigIndex];
//				uncalibratedDataUnits[sigIndex]=CHANNEL_UNITS.BEATS_PER_MINUTE;
				calibratedData[sigIndex]=(double)newPacketInt[sigIndex];
				calibratedDataUnits[sigIndex]=CHANNEL_UNITS.BEATS_PER_MINUTE;
			}

			//Additional Channels Offset
			int additionalChannelsOffset = calibratedData.length-numAdditionalChannels; 
			if(fwType == COMMUNICATION_TYPE.BLUETOOTH) {
				additionalChannelsOffset += 1;	//BT need +1 because timestamp added as additional channel
			}
			if (getHardwareVersion() == HW_ID.SHIMMER_3 || getHardwareVersion() == HW_ID.SHIMMER_3R){
				if(isEXGUsingDefaultECGConfiguration() || isEXGUsingDefaultRespirationConfiguration()){
					if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXG1_16BIT) > 0) 
							|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXG1_16BIT) > 0)){
						
						int iexg1ch1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT);
						int iexg1ch2 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT);

						if(iexg1ch1!=-1 && iexg1ch2!=-1){
							sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.ECG_LL_LA_16BIT;
							
							uncalibratedData[additionalChannelsOffset]= uncalibratedData[iexg1ch1] - uncalibratedData[iexg1ch2];
							uncalibratedDataUnits[additionalChannelsOffset]=CHANNEL_UNITS.NO_UNITS;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_LA_16BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[additionalChannelsOffset]);
							
							if(mEnableCalibration){
								calibratedData[additionalChannelsOffset]= calibratedData[iexg1ch1] - calibratedData[iexg1ch2];
								calibratedDataUnits[additionalChannelsOffset]=CHANNEL_UNITS.MILLIVOLTS;
								objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_LA_16BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[additionalChannelsOffset]);
							}
							additionalChannelsOffset += 1;
						}
					}
					else if (((fwType == COMMUNICATION_TYPE.BLUETOOTH) && (mEnabledSensors & BTStream.EXG1_24BIT) > 0) 
							|| ((fwType == COMMUNICATION_TYPE.SD) && (mEnabledSensors & SDLogHeader.EXG1_24BIT) > 0)){
						
						int iexg1ch1 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT);
						int iexg1ch2 = getSignalIndex(Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT);

						if(iexg1ch1!=-1 && iexg1ch2!=-1){
							sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.ECG_LL_LA_24BIT;
							
							uncalibratedData[additionalChannelsOffset]= uncalibratedData[iexg1ch1] - uncalibratedData[iexg1ch2];
							uncalibratedDataUnits[additionalChannelsOffset]=CHANNEL_UNITS.NO_UNITS;
							objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_LA_24BIT,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,uncalibratedData[additionalChannelsOffset]);
							
							if(mEnableCalibration){
								calibratedData[additionalChannelsOffset]= calibratedData[iexg1ch1] - calibratedData[iexg1ch2];
								calibratedDataUnits[additionalChannelsOffset]=CHANNEL_UNITS.MILLIVOLTS;
								objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.ECG_LL_LA_24BIT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[additionalChannelsOffset]);
							}
							additionalChannelsOffset += 1;
						}
					}
				}
			}
			
			if(fwType == COMMUNICATION_TYPE.BLUETOOTH){
				double estimatedChargePercentage = (double)mShimmerBattStatusDetails.getEstimatedChargePercentage();
				if(!Double.isNaN(estimatedChargePercentage) && !Double.isInfinite(estimatedChargePercentage)){
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.BATT_PERCENTAGE, CHANNEL_TYPE.CAL.toString(), CHANNEL_UNITS.PERCENT, estimatedChargePercentage);
					calibratedData[additionalChannelsOffset] = estimatedChargePercentage;
					calibratedDataUnits[additionalChannelsOffset] = CHANNEL_UNITS.PERCENT;
					uncalibratedData[additionalChannelsOffset] = Double.NaN;
					uncalibratedDataUnits[additionalChannelsOffset] = "";
					sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.BATT_PERCENTAGE;
				}
				additionalChannelsOffset+=1;

				double packetReceptionRateCurrent = (double)getPacketReceptionRateCurrent();
				if(!Double.isNaN(packetReceptionRateCurrent) && !Double.isInfinite(packetReceptionRateCurrent)){
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.PERCENT, packetReceptionRateCurrent);
					calibratedData[additionalChannelsOffset] = packetReceptionRateCurrent;
					calibratedDataUnits[additionalChannelsOffset] = CHANNEL_UNITS.PERCENT;
					uncalibratedData[additionalChannelsOffset] = Double.NaN;
					uncalibratedDataUnits[additionalChannelsOffset] = "";
					sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT;
				}
				additionalChannelsOffset+=1;

				double packetReceptionRateOverall = (double)getPacketReceptionRateOverall();
				if(!Double.isNaN(packetReceptionRateOverall) && !Double.isInfinite(packetReceptionRateOverall)){
					objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.PERCENT, packetReceptionRateOverall);
					calibratedData[additionalChannelsOffset] = packetReceptionRateOverall;
					calibratedDataUnits[additionalChannelsOffset] = CHANNEL_UNITS.PERCENT;
					uncalibratedData[additionalChannelsOffset] = Double.NaN;
					uncalibratedDataUnits[additionalChannelsOffset] = "";
					sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL;
				}
				additionalChannelsOffset+=1;
				
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLISECONDS,(double)pcTimestampMs);
				calibratedData[additionalChannelsOffset] = (double)pcTimestampMs;
				calibratedDataUnits[additionalChannelsOffset] = CHANNEL_UNITS.MILLISECONDS;
				uncalibratedData[additionalChannelsOffset] = Double.NaN;
				uncalibratedDataUnits[additionalChannelsOffset] = "";
				sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP;
				additionalChannelsOffset+=1;
				
				processEventMarkerCh(objectCluster);
				
//				calibratedData[additionalChannelsOffset] = (double)mPacketReceptionRate;
//				calibratedDataUnits[additionalChannelsOffset] = CHANNEL_UNITS.PERCENT;
//				uncalibratedData[additionalChannelsOffset] = Double.NaN;
//				uncalibratedDataUnits[additionalChannelsOffset] = "";
//				sensorNames[additionalChannelsOffset] = Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_TRIAL;
//				additionalChannelsOffset+=1;
			}
			
			if(!objectCluster.mEnableArraysDataStructure) {
				objectCluster.mCalData = calibratedData;
				objectCluster.mUncalData = uncalibratedData;
				objectCluster.mUnitCal = calibratedDataUnits;
				objectCluster.mUnitUncal = uncalibratedDataUnits;
				objectCluster.mSensorNames = sensorNames;
				objectCluster.setIndexKeeper(additionalChannelsOffset);
			} else {
				if(((fwType == COMMUNICATION_TYPE.BLUETOOTH)&&((mEnabledSensors & BTStream.ACCEL_LN) > 0 || (mEnabledSensors & BTStream.ACCEL_WR) > 0) && ((mEnabledSensors & BTStream.GYRO) > 0) && ((mEnabledSensors & BTStream.MAG) > 0) && is3DOrientationEnabled() )
						||((fwType == COMMUNICATION_TYPE.SD)&&((mEnabledSensors & SDLogHeader.ACCEL_LN) > 0 || (mEnabledSensors & SDLogHeader.ACCEL_WR) > 0) && ((mEnabledSensors & SDLogHeader.GYRO) > 0) && ((mEnabledSensors & SDLogHeader.MAG) > 0) && is3DOrientationEnabled() )){
					//First, find the last index of the sensor names, ignoring the other null values after
					int indexSensorNames = -1;
					for(int i=0; i<sensorNames.length; i++) {
						if(sensorNames[i] == null 
							&& sensorNames[i+1] == null
							&& sensorNames[i+2] == null
							&& sensorNames[i+3] == null
							&& sensorNames[i+4] == null
							&& sensorNames[i+5] == null
							&& sensorNames[i+6] == null
							&& sensorNames[i+7] == null) { //JC:This is temporary until we can come up with a better efficient solution, there are 8 channels for orientation which needs to be added, this null checks is just to ensure nothing is overwritten. only orientation algo is using this and if other algorithms are used this should be updated
							indexSensorNames = i;
							break;
						}
					}
					
					//Append the 9DOF data to the end of the array if 3D Orientation is enabled
					System.arraycopy(sensorNamesAlgo, 0, sensorNames, indexSensorNames, (indexAlgo+1));
					System.arraycopy(calDataUnitsAlgo, 0, calibratedDataUnits, indexSensorNames, (indexAlgo+1));
					System.arraycopy(calDataAlgo, 0, calibratedData, indexSensorNames, (indexAlgo+1));
					additionalChannelsOffset += (indexAlgo+1);
				}
				
				objectCluster.sensorDataArray.mCalData=calibratedData;
				objectCluster.sensorDataArray.mUncalData=uncalibratedData;
				objectCluster.sensorDataArray.mCalUnits = calibratedDataUnits;
				objectCluster.sensorDataArray.mUncalUnits = uncalibratedDataUnits;
				objectCluster.sensorDataArray.mSensorNames = sensorNames;
				objectCluster.setIndexKeeper(additionalChannelsOffset);
			}
			
//			printSensorNames(objectCluster.mSensorNames);
//			printSensorNames(objectCluster.getChannelNamesByInsertionOrder());
			
//			 processAlgorithmData(objectCluster);	

		} 
		else if (getHardwareVersion()==HW_ID.SHIMMER_2 || getHardwareVersion()==HW_ID.SHIMMER_2R){
			 //start of Shimmer2

			int iTimeStamp = getSignalIndex(Configuration.Shimmer2.ObjectClusterSensorName.TIMESTAMP); //find index
			double timestampUnwrappedTicks = unwrapTimeStamp((double)newPacketInt[iTimeStamp]);
			double timestampUnwrappedMilliSecs = timestampUnwrappedTicks/getRtcClockFreq()*1000;   // to convert into mS
			
			incrementPacketsReceivedCounters();
			calculateTrialPacketLoss(timestampUnwrappedMilliSecs);

			//TIMESTAMP
			if (fwType == COMMUNICATION_TYPE.BLUETOOTH){
				objectCluster.addDataToMap(Configuration.Shimmer2.ObjectClusterSensorName.TIMESTAMP,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iTimeStamp]);
				uncalibratedData[iTimeStamp] = (double)newPacketInt[iTimeStamp];
				uncalibratedDataUnits[iTimeStamp] = CHANNEL_UNITS.NO_UNITS;
				if (mEnableCalibration){
					objectCluster.addDataToMap(Configuration.Shimmer2.ObjectClusterSensorName.TIMESTAMP,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLISECONDS,timestampUnwrappedMilliSecs);
					calibratedData[iTimeStamp] = timestampUnwrappedMilliSecs;
					calibratedDataUnits[iTimeStamp] = CHANNEL_UNITS.MILLISECONDS;
				}
			}
			
			if ((mEnabledSensors & SENSOR_ACCEL) > 0){
				int iAccelX=getSignalIndex(Shimmer2.ObjectClusterSensorName.ACCEL_X); //find index
				int iAccelY=getSignalIndex(Shimmer2.ObjectClusterSensorName.ACCEL_Y); //find index
				int iAccelZ=getSignalIndex(Shimmer2.ObjectClusterSensorName.ACCEL_Z); //find index
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelX]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelY]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iAccelZ]);
				if (mEnableCalibration){
					tempData[0]=(double)newPacketInt[iAccelX];
					tempData[1]=(double)newPacketInt[iAccelY];
					tempData[2]=(double)newPacketInt[iAccelZ];
//					double[] accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixAnalogAccel, mSensitivityMatrixAnalogAccel, mOffsetVectorAnalogAccel);
					double[] accelCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsAccelLn());
					calibratedData[iAccelX]=accelCalibratedData[0];
					calibratedData[iAccelY]=accelCalibratedData[1];
					calibratedData[iAccelZ]=accelCalibratedData[2];
					if (mSensorMMA736x.mIsUsingDefaultLNAccelParam) {
//					if (mDefaultCalibrationParametersAccel == true) {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.METER_PER_SECOND_SQUARE+"*",accelCalibratedData[0]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.METER_PER_SECOND_SQUARE+"*",accelCalibratedData[1]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.METER_PER_SECOND_SQUARE+"*",accelCalibratedData[2]);
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];
					} else {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.METER_PER_SECOND_SQUARE,accelCalibratedData[0]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.METER_PER_SECOND_SQUARE,accelCalibratedData[1]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ACCEL_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.METER_PER_SECOND_SQUARE,accelCalibratedData[2]);
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];
					}
				}

			}

			if ((mEnabledSensors & SENSOR_GYRO) > 0) {
				int iGyroX=getSignalIndex(Shimmer2.ObjectClusterSensorName.GYRO_X);
				int iGyroY=getSignalIndex(Shimmer2.ObjectClusterSensorName.GYRO_Y);
				int iGyroZ=getSignalIndex(Shimmer2.ObjectClusterSensorName.GYRO_Z);					
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGyroX]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGyroY]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGyroZ]);
				if (mEnableCalibration){
					tempData[0]=(double)newPacketInt[iGyroX];
					tempData[1]=(double)newPacketInt[iGyroY];
					tempData[2]=(double)newPacketInt[iGyroZ];
//					double[] gyroCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixGyroscope, mSensitivityMatrixGyroscope, mOffsetVectorGyroscope);
					double[] gyroCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsGyro());
					calibratedData[iGyroX]=gyroCalibratedData[0];
					calibratedData[iGyroY]=gyroCalibratedData[1];
					calibratedData[iGyroZ]=gyroCalibratedData[2];
//					if (mDefaultCalibrationParametersGyro == true) {
					if(mSensorShimmer2Gyro.mIsUsingDefaultGyroParam) {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_PER_SECOND+"*",gyroCalibratedData[0]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_PER_SECOND+"*",gyroCalibratedData[1]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_PER_SECOND+"*",gyroCalibratedData[2]);
						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
					} else {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_PER_SECOND,gyroCalibratedData[0]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_PER_SECOND,gyroCalibratedData[1]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.DEGREES_PER_SECOND,gyroCalibratedData[2]);
						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;

						if(isShimmerGen2() && isGyroOnTheFlyCalEnabled()){
							getOnTheFlyCalGyro().updateGyroOnTheFlyGyroOVCal(getCurrentCalibDetailsGyro(), gyroCalibratedData, (double) newPacketInt[iGyroX], (double) newPacketInt[iGyroY], (double) newPacketInt[iGyroZ]);
						}
					} 
				}
			}
			if ((mEnabledSensors & SENSOR_MAG) > 0) {
				int iMagX=getSignalIndex(Shimmer2.ObjectClusterSensorName.MAG_X);
				int iMagY=getSignalIndex(Shimmer2.ObjectClusterSensorName.MAG_Y);
				int iMagZ=getSignalIndex(Shimmer2.ObjectClusterSensorName.MAG_Z);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagX]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagY]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iMagZ]);
				if (mEnableCalibration){
					tempData[0]=(double)newPacketInt[iMagX];
					tempData[1]=(double)newPacketInt[iMagY];
					tempData[2]=(double)newPacketInt[iMagZ];
//					double[] magCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, mAlignmentMatrixMagnetometer, mSensitivityMatrixMagnetometer, mOffsetVectorMagnetometer);
					double[] magCalibratedData=UtilCalibration.calibrateInertialSensorData(tempData, getCurrentCalibDetailsMag());
					calibratedData[iMagX]=magCalibratedData[0];
					calibratedData[iMagY]=magCalibratedData[1];
					calibratedData[iMagZ]=magCalibratedData[2];
//					if (mDefaultCalibrationParametersMag == true) {
					if (mSensorShimmer2Mag.mIsUsingDefaultMagParam) {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL_FLUX+"*",magCalibratedData[0]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL_FLUX+"*",magCalibratedData[1]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL_FLUX+"*",magCalibratedData[2]);
						magnetometer.x=magCalibratedData[0];
						magnetometer.y=magCalibratedData[1];
						magnetometer.z=magCalibratedData[2];
					} else {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL_FLUX,magCalibratedData[0]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL_FLUX,magCalibratedData[1]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL_FLUX,magCalibratedData[2]);
						magnetometer.x=magCalibratedData[0];
						magnetometer.y=magCalibratedData[1];
						magnetometer.z=magCalibratedData[2];
					}
				}
			}


			if ((mEnabledSensors & SENSOR_ACCEL) > 0 && (mEnabledSensors & SENSOR_GYRO) > 0 && (mEnabledSensors & SENSOR_MAG) > 0 && is3DOrientationEnabled() ){
				if (mEnableCalibration){
					if (mOrientationAlgo==null){
//						mOrientationAlgo = new GradDes3DOrientation9DoF(0.4, (double)1/getSamplingRateShimmer(), 1, 0, 0,0);
						mOrientationAlgo = new GradDes3DOrientation((double)1/getSamplingRateShimmer());
					}
//					Orientation3DObject q = mOrientationAlgo.update(accelerometer.x,accelerometer.y,accelerometer.z, gyroscope.x,gyroscope.y,gyroscope.z, magnetometer.x,magnetometer.y,magnetometer.z);
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_A,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getTheta());
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getAngleX());
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.AXIS_ANGLE_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getAngleY());
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_W,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionW());
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionX());
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionY());
//					objectCluster.addData(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL,q.getQuaternionZ());
				}
			}

			if ((mEnabledSensors & SENSOR_GSR) > 0) {
				int iGSR = getSignalIndex(Shimmer2.ObjectClusterSensorName.GSR);
				tempData[0] = (double)newPacketInt[iGSR];
				int newGSRRange = -1; // initialized to -1 so it will only come into play if mGSRRange = 4  
				int currentGSRRange = getGSRRange();
				
				double p1=0,p2=0;//,p3=0,p4=0,p5=0;
				if (currentGSRRange==4){
					newGSRRange=(49152 & (int)tempData[0])>>14; 
				}
				if (currentGSRRange==0 || newGSRRange==0) { //Note that from FW 1.0 onwards the MSB of the GSR data contains the range
					// the polynomial function used for calibration has been deprecated, it is replaced with a linear function
					
					p1 = 0.0373;
					p2 = -24.9915;
				} else if (currentGSRRange==1 || newGSRRange==1) {
					p1 = 0.0054;
					p2 = -3.5194;
				} else if (currentGSRRange==2 || newGSRRange==2) {
					p1 = 0.0015;
					p2 = -1.0163;
				} else if (currentGSRRange==3  || newGSRRange==3) {
					p1 = 4.5580e-04;
					p2 = -0.3014;
				}
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GSR,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iGSR]);
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iGSR];
					calibratedData[iGSR] = SensorGSR.calibrateGsrDataToResistance(tempData[0],p1,p2);
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.GSR,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.KOHMS,calibratedData[iGSR]);
				}
			}
			if ((mEnabledSensors & SENSOR_ECG) > 0) {
				int iECGRALL = getSignalIndex(Shimmer2.ObjectClusterSensorName.ECG_RA_LL);
				int iECGLALL = getSignalIndex(Shimmer2.ObjectClusterSensorName.ECG_LA_LL);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ECG_RA_LL,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iECGRALL]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ECG_LA_LL,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iECGLALL]);
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iECGRALL];
					tempData[1] = (double)newPacketInt[iECGLALL];
					calibratedData[iECGRALL]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],OffsetECGRALL,3,GainECGRALL);
					calibratedData[iECGLALL]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[1],OffsetECGLALL,3,GainECGLALL);
					if (mDefaultCalibrationParametersECG == true) {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ECG_RA_LL,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS+"*",calibratedData[iECGRALL]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ECG_LA_LL,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS+"*",calibratedData[iECGLALL]);
					} else {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ECG_RA_LL,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iECGRALL]);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.ECG_LA_LL,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iECGLALL]);
					}
				}
			}
			if ((mEnabledSensors & SENSOR_EMG) > 0) {
				int iEMG = getSignalIndex(Shimmer2.ObjectClusterSensorName.EMG);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EMG,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iEMG]);
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iEMG];
					calibratedData[iEMG]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],OffsetEMG,3,GainEMG);

					if (mDefaultCalibrationParametersEMG == true){
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EMG,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS+"*",calibratedData[iEMG]);
					} else {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EMG,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iEMG]);
					}
				}
			}
			if ((mEnabledSensors & SENSOR_BRIDGE_AMP) > 0) {
				int iBAHigh = getSignalIndex(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH);
				int iBALow = getSignalIndex(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iBAHigh]);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iBALow]);
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iBAHigh];
					tempData[1] = (double)newPacketInt[iBALow];
					calibratedData[iBAHigh]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],60,3,551);
					calibratedData[iBALow]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[1],1950,3,183.7);

					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iBAHigh]);
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iBALow]);	
				}
			}
			if ((mEnabledSensors & SENSOR_HEART) > 0) {
				int iHeartRate = getSignalIndex(Shimmer2.ObjectClusterSensorName.HEART_RATE);
				tempData[0] = (double)newPacketInt[iHeartRate];
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.HEART_RATE,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,tempData[0]);
				if (mEnableCalibration){
					calibratedData[iHeartRate]=tempData[0];
					if (getFirmwareVersionMajor()==0 && getFirmwareVersionMinor()==1){

					} else {
						if (tempData[0]==0){
							calibratedData[iHeartRate]=	mLastKnownHeartRate;
						} else {
							calibratedData[iHeartRate]=(int)(1024/tempData[0]*60);
							mLastKnownHeartRate=calibratedData[iHeartRate];
						}
					}
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.HEART_RATE,CHANNEL_TYPE.CAL.toString(),"BPM",calibratedData[iHeartRate]);	
				}
			}
			if ((mEnabledSensors& SENSOR_EXP_BOARD_A0) > 0) {
				int iA0 = getSignalIndex(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0);
				tempData[0] = (double)newPacketInt[iA0];
				if (getPMux()==0){
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA0]);
					if (mEnableCalibration){
						calibratedData[iA0]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);	
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA0]);
					}
				} else {
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.VOLT_REG,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA0]);
					if (mEnableCalibration){
						calibratedData[iA0]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1)*1.988;
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.VOLT_REG,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA0]);
					}

				}
			}					
			if ((mEnabledSensors & SENSOR_EXP_BOARD_A7) > 0) {
				int iA7 = getSignalIndex(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7);
				tempData[0] = (double)newPacketInt[iA7];
				if (getPMux()==0){
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA7]);
					if (mEnableCalibration){
						calibratedData[iA7]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1);
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA7]);
					} else {
						objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA7]);
						if (mEnableCalibration){
							calibratedData[iA7]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1)*2;
							objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA7]);
						}

					}
				} 
			}
			if  ((mEnabledSensors & SENSOR_BATT) > 0) {
				int iA0 = getSignalIndex(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.VOLT_REG,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA0]);

				int iA7 = getSignalIndex(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7);
				objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS,(double)newPacketInt[iA7]);


				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iA0];
					calibratedData[iA0]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1)*1.988;
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.VOLT_REG,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA0]);

					tempData[0] = (double)newPacketInt[iA7];
					calibratedData[iA7]=SensorADC.calibrateU12AdcValueToMillivolts(tempData[0],0,3,1)*2;
					objectCluster.addDataToMap(Shimmer2.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS,calibratedData[iA7]);	
					mVSenseBattMA.addValue(calibratedData[iA7]);
					checkBattery();
				}
			}
		}
		else{
//			throw new Exception("The Hardware version is not compatible");
			consolePrintErrLn("The Hardware version is not compatible");
		}
		
		objectCluster = processData(objectCluster);

		return objectCluster;
	}
	

	protected void parseTimestampShimmer3(COMMUNICATION_TYPE fwType, ObjectCluster objectCluster, double[] uncalibratedData, String[] uncalibratedDataUnits, double[] calibratedData, String[] calibratedDataUnits, String[] sensorNames, long[] newPacketInt) {
		int iTimeStamp=getSignalIndex(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP); //find index
		double shimmerTimestampTicks = (double)newPacketInt[iTimeStamp];
		objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.SHIMMER_CLOCK,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.CLOCK_UNIT,shimmerTimestampTicks);
		objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.SHIMMER_CLOCK,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.CLOCK_UNIT,shimmerTimestampTicks);
		if(mFirstTime && fwType==COMMUNICATION_TYPE.SD){
			//this is to make sure the Raw starts from zero for SD data. See comment for mFirstTsOffsetFromInitialTsTicks. 
			mFirstTsOffsetFromInitialTsTicks = shimmerTimestampTicks;
			
			//This is to circumvent a bug with old StroKare firmware. Resolved in firmware v1.0.1 
			if(getFirmwareIdentifier()==FW_ID.STROKARE
					&& !isThisVerCompatibleWith(FW_ID.STROKARE, 1, 0, 1)){
				long initialTsTicksOriginal = getInitialTimeStampTicksSd();
				long initialTsTicksNew = (long) ((initialTsTicksOriginal&0xFFFF000000L)+shimmerTimestampTicks);
				setInitialTimeStampTicksSd(initialTsTicksNew);
			}
			
			mFirstTime = false;
		}

		double timestampUnwrappedTicks = unwrapTimeStamp(shimmerTimestampTicks);
		double timestampUnwrappedMilliSecs = timestampUnwrappedTicks/getRtcClockFreq()*1000;   // to convert into mS
		
		incrementPacketsReceivedCounters();
		calculateTrialPacketLoss(timestampUnwrappedMilliSecs);
		
		//TIMESTAMP
		double timestampUnwrappedWithOffsetTicks = 0;
		if (fwType==COMMUNICATION_TYPE.SD){
			// RTC timestamp uncal. (shimmer timestamp + RTC offset from header); unit = ticks
			timestampUnwrappedWithOffsetTicks = timestampUnwrappedTicks + getInitialTimeStampTicksSd();

			if (isLegacySdLog()){
				uncalibratedData[iTimeStamp] = shimmerTimestampTicks;
			} else {
				//See Javadocs tooltip for description of mFirstTsOffsetFromInitialTsTicks
				timestampUnwrappedWithOffsetTicks -= mFirstTsOffsetFromInitialTsTicks; //deduct this so it will start from 0
				uncalibratedData[iTimeStamp] = timestampUnwrappedWithOffsetTicks;
			}
			
			if (mEnableCalibration){
				double timestampUnwrappedWithOffsetMilliSecs = timestampUnwrappedWithOffsetTicks/getRtcClockFreq()*1000;   // to convert into mS
				calibratedData[iTimeStamp] = timestampUnwrappedWithOffsetMilliSecs;
			}
		} 
		else if (fwType==COMMUNICATION_TYPE.BLUETOOTH){
			uncalibratedData[iTimeStamp] = shimmerTimestampTicks;
			if (mEnableCalibration){
				calibratedData[iTimeStamp] = timestampUnwrappedMilliSecs;
			}
		}
		
		uncalibratedDataUnits[iTimeStamp] = CHANNEL_UNITS.CLOCK_UNIT;
		objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.TIMESTAMP,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.CLOCK_UNIT,uncalibratedData[iTimeStamp]);
		if (mEnableCalibration){
			calibratedDataUnits[iTimeStamp] = CHANNEL_UNITS.MILLISECONDS;
			objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.TIMESTAMP,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLISECONDS,calibratedData[iTimeStamp]);
			objectCluster.setTimeStampMilliSecs(calibratedData[iTimeStamp]);
		}

		
		//RAW RTC
		if (fwType==COMMUNICATION_TYPE.SD && isRtcDifferenceSet()) {
			double rtcTimestampTicks = timestampUnwrappedWithOffsetTicks + getRTCDifferenceInTicks();
			
//			System.out.println("\tRtcDiff: " + UtilShimmer.convertMilliSecondsToDateString((mRTCDifferenceInTicks+mInitialTsTicks)/32768*1000, true));
//			System.out.println("\tRtcDiffActual: " + UtilShimmer.convertMilliSecondsToDateString((rtcTimestampTicks)/32768*1000, true));

			objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.REAL_TIME_CLOCK,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.CLOCK_UNIT,rtcTimestampTicks);
			uncalibratedData[sensorNames.length-1] = rtcTimestampTicks;
			uncalibratedDataUnits[sensorNames.length-1] = CHANNEL_UNITS.CLOCK_UNIT;
			sensorNames[sensorNames.length-1]= Shimmer3.ObjectClusterSensorName.REAL_TIME_CLOCK;
			if (mEnableCalibration){
				// TODO below is suitable for both SD and BT but, for SD data,
				// it would be more efficient to just convert rtcTimestampTicks
				// to milliseconds via the following line
				double rtcTimestampMilliSecs = 0;
				if (fwType==COMMUNICATION_TYPE.SD){
					rtcTimestampMilliSecs = rtcTimestampTicks/getSamplingClockFreq()*1000.0;
				} else {
					//TODO remove below, no need for it
					rtcTimestampMilliSecs = timestampUnwrappedMilliSecs;
					if(getInitialTimeStampTicksSd()!=0){
						rtcTimestampMilliSecs += ((double)getInitialTimeStampTicksSd()/getSamplingClockFreq()*1000.0);
					}
					if(isRtcDifferenceSet()){
						rtcTimestampMilliSecs += ((double)getRTCDifferenceInTicks()/getSamplingClockFreq()*1000.0);
					}
					if(mFirstTsOffsetFromInitialTsTicks!=0){
						rtcTimestampMilliSecs -= (mFirstTsOffsetFromInitialTsTicks/getSamplingClockFreq()*1000.0);
					}
				}
				objectCluster.addDataToMap(Shimmer3.ObjectClusterSensorName.REAL_TIME_CLOCK,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLISECONDS,rtcTimestampMilliSecs);
				calibratedData[sensorNames.length-1] = rtcTimestampMilliSecs;
				calibratedDataUnits[sensorNames.length-1] = CHANNEL_UNITS.MILLISECONDS;
			}
		}
	}

	private void printSensorNames(List<String> list) {
		System.out.println("ObjectClusterSensorNames");
		for(String channelName:list){
			System.out.println("\t"+channelName);
		}
		System.out.println("");
	}


	private void printSensorNames(String[] channelNames) {
		System.out.println("ObjectClusterChannelNames");
		for(String channelName:channelNames){
			System.out.println("\t"+channelName);
		}
		System.out.println("");
	}

	protected int getSignalIndex(String signalName) {
		int iSignal=-1; //better to fail //used to be -1, putting to zero ensure it works eventhough it might be wrong SR30
		for (int i=0;i<mSignalNameArray.length;i++) {
			if (signalName==mSignalNameArray[i]) {
//				iSignal=i;
				return i;
			}
		}

		return iSignal;
	}

	/** Only for Bluetooth
	 * @param numChannels
	 * @param signalId
	 */
	public void interpretDataPacketFormat(int numChannels, byte[] signalId){
		String [] signalNameArray=new String[MAX_NUMBER_OF_SIGNALS];
		String [] signalDataTypeArray=new String[MAX_NUMBER_OF_SIGNALS];
		int packetSize=mTimeStampPacketByteSize; // Time stamp
		
		int iTS=0;
		
		if (getHardwareVersion()==HW_ID.SHIMMER_3R && this.getClass().getSimpleName().equals("ShimmerSDLog") ) {
			mNChannels = numChannels;
			if (isSyncWhenLogging() 
					&& (getFirmwareIdentifier()==FW_ID.SDLOG || getFirmwareIdentifier()==FW_ID.GQ_802154 
					||(UtilShimmer.compareVersions(getShimmerVerObject(),Configuration.Shimmer3.CompatibilityInfoForMaps.svoShimmer3LogAndStreamWithSDLogSyncSupport))
					||(UtilShimmer.compareVersions(getShimmerVerObject(),Configuration.Shimmer3.CompatibilityInfoForMaps.svoShimmer3RLogAndStreamWithSDLogSyncSupport)))){
				signalNameArray[iTS]=SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET;
				if (OFFSET_LENGTH==5){
					signalDataTypeArray[iTS]="u32signed";
					mNChannels += 1;
					packetSize+=5;
				} else if (OFFSET_LENGTH==9){
					signalDataTypeArray[iTS]="u72";
					mNChannels += 1;
					packetSize+=9;
				}
				iTS++;
			} 
		}
		
		
		if (getHardwareVersion()==HW_ID.SHIMMER_SR30 || getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion() == HW_ID.SHIMMER_3R){
			signalNameArray[iTS]=Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP;
		}
		else{
			signalNameArray[iTS]=Configuration.Shimmer2.ObjectClusterSensorName.TIMESTAMP;
		}
		

		if (mTimeStampPacketByteSize==2){
			signalDataTypeArray[iTS]="u16";
		} else if (mTimeStampPacketByteSize==3) {
			signalDataTypeArray[iTS]="u24";
		}
		
		int enabledSensors= 0x00;
		for (int i=iTS;i<numChannels+iTS;i++) {
			if ((byte)signalId[i-iTS]==(byte)0x00){
				if (getHardwareVersion()==HW_ID.SHIMMER_SR30 || getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_LN_X;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.ACCEL_X;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_ACCEL;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x01){
				if (getHardwareVersion()==HW_ID.SHIMMER_SR30 || getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2; 
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.ACCEL_Y;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_ACCEL;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x02){
				if (getHardwareVersion()==HW_ID.SHIMMER_SR30 || getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.ACCEL_Z;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_ACCEL;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x03){

				if (getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.GYRO_X;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO;
				} 
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.BATTERY; //should be the battery but this will do for now
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_VBATT;	
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.GYRO_X;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_GYRO;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x04){

				if (getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.GYRO_Y;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO;
				} 
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_WR_X;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.GYRO_Y;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_GYRO;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x05){
				if (getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.GYRO_Z;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO;
				}
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL;
				}
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.GYRO_Z;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_GYRO;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x06){
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.BATTERY; //should be the battery but this will do for now
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_VBATT;	
				} 
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.MAG_X;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_MAG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x07){
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_WR_X;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL;
				} 
				else if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.MAG_X;
					if(isSupportedNewImuSensors()){
						signalDataTypeArray[i+1] = "i16";			
					} else {
						signalDataTypeArray[i+1] = "i16r";			
					}
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_MAG;
				} 
				else {
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.MAG_Y;
					enabledSensors |= SENSOR_MAG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x08)
			{	
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL;
				} 
				else if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.MAG_Y;
					if(isSupportedNewImuSensors()){
						signalDataTypeArray[i+1] = "i16";			
					} else {
						signalDataTypeArray[i+1] = "i16r";			
					}
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_MAG;
				} 
				else {
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.MAG_Z;
					enabledSensors |= SENSOR_MAG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x09){
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL;
				} 
				else if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.MAG_Z;
					if(isSupportedNewImuSensors()){
						signalDataTypeArray[i+1] = "i16";			
					} else {
						signalDataTypeArray[i+1] = "i16r";			
					}
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_MAG;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.ECG_RA_LL;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_ECG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x0A){
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.MAG_X;
					signalDataTypeArray[i+1] = "i16";			
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_MAG;
				}
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R) {
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.GYRO_X;
					if (getHardwareVersion()==HW_ID.SHIMMER_3) {
						signalDataTypeArray[i+1] = "i16r";
					}else if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalDataTypeArray[i+1] = "i16";
					}
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO;
				}
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.ECG_LA_LL;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_ECG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x0B){
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.MAG_Y;
					signalDataTypeArray[i+1] = "i16";			
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_MAG;
				}
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R) {
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.GYRO_Y;
					if (getHardwareVersion()==HW_ID.SHIMMER_3) {
						signalDataTypeArray[i+1] = "i16r";
					}else if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalDataTypeArray[i+1] = "i16";
					}
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO;
				}
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.GSR;
					signalDataTypeArray[i+1] = "u16";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_GSR;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x0C){
				if(getHardwareVersion()==HW_ID.SHIMMER_SR30){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.MAG_Z;
					signalDataTypeArray[i+1] = "i16";			
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_MAG;
				}
				else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R) {
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.GYRO_Z;
					if (getHardwareVersion()==HW_ID.SHIMMER_3) {
						signalDataTypeArray[i+1] = "i16r";
					}else if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalDataTypeArray[i+1] = "i16";
					}
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO;
				}
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.GSR_RES;
					signalDataTypeArray[i+1] = "u16";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_GSR;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x0D){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXT_ADC_0;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A7;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXT_ADC_A7;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.EMG;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EMG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x0E){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXT_ADC_1;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A6;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXT_ADC_A6;
				} 
				else{
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXP_BOARD_A0;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x0F){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXT_ADC_2;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXT_EXP_ADC_A15;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXT_ADC_A15;
				} else{
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXP_BOARD_A7;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x10){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_ADC_3;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A1;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_INT_ADC_A1;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_BRIDGE_AMP;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x11){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_ADC_0;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A12;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_INT_ADC_A12;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_BRIDGE_AMP;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x12){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_ADC_1;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A13;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_INT_ADC_A13;
				} 
				else {
					signalNameArray[i+1]=Shimmer2.ObjectClusterSensorName.HEART_RATE;
					if (getFirmwareVersionMajor()==0 && getFirmwareVersionMinor()==1){
						signalDataTypeArray[i+1] = "u8";
						packetSize=packetSize+1;
					} else {
						signalDataTypeArray[i+1] = "u16"; 
						packetSize=packetSize+2;
					}
					enabledSensors |= SENSOR_HEART;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x13){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_ADC_2;
						signalDataTypeArray[i+1] = "u14";
					}
					else {
						signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A14;
						signalDataTypeArray[i+1] = "u12";
					}

					packetSize=packetSize+2;
					enabledSensors |= SENSOR_INT_ADC_A14;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x14){
				if (getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_X;
					signalDataTypeArray[i+1] = "i12*>";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_ALT_ACCEL;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x15){
				if (getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Y;
					signalDataTypeArray[i+1] = "i12*>";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_ALT_ACCEL;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x16){
				if (getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_Z;
					signalDataTypeArray[i+1] = "i12*>";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_ALT_ACCEL;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x17){
				if (getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ALT_MAG_X;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_ALT_MAG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x18){
				if (getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ALT_MAG_Y;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_ALT_MAG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x19){
				if (getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.ALT_MAG_Z;
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors |= Configuration.Shimmer3.SensorBitmap.SENSOR_ALT_MAG;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x1A){
				if (getHardwareVersion()==HW_ID.SHIMMER_3){
					String signalName = SensorBMP180.ObjectClusterSensorName.TEMPERATURE_BMP180;
					if(isSupportedBmp280()){
						signalName=SensorBMP280.ObjectClusterSensorName.TEMPERATURE_BMP280;
					}
					signalNameArray[i+1]=signalName;
					signalDataTypeArray[i+1] = "u16r";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_BMPX80;
				}else if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
					String signalName = SensorBMP390.ObjectClusterSensorName.TEMPERATURE_BMP390;
					signalNameArray[i+1]=signalName;
					signalDataTypeArray[i+1] = "u24";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_BMPX80;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x1B){
				if (getHardwareVersion()==HW_ID.SHIMMER_3){ 
					String signalName = SensorBMP180.ObjectClusterSensorName.PRESSURE_BMP180;
					if(isSupportedBmp280()){
						signalName=SensorBMP280.ObjectClusterSensorName.PRESSURE_BMP280;
					}
					signalNameArray[i+1]=signalName;
					signalDataTypeArray[i+1] = "u24r";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_BMPX80;
				}else if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
					String signalName=SensorBMP390.ObjectClusterSensorName.PRESSURE_BMP390;
					signalNameArray[i+1]=signalName;
					signalDataTypeArray[i+1] = "u24";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_BMPX80;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x1C){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=SensorGSR.ObjectClusterSensorName.GSR_RESISTANCE;
					signalDataTypeArray[i+1] = "u16";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_GSR;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x1D){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG1_STATUS;
					signalDataTypeArray[i+1] = "u8";
					packetSize=packetSize+1;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x1E){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG1_CH1_24BIT;
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_EXG1_24BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x1F){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG1_CH2_24BIT;
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_EXG1_24BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x20){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG2_STATUS;
					signalDataTypeArray[i+1] = "u8";
					packetSize=packetSize+1;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x21){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_24BIT;
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_EXG2_24BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x22){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_24BIT;
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors |= SENSOR_EXG2_24BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x23){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG1_CH1_16BIT;
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXG1_16BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x24){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG1_CH2_16BIT;
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXG1_16BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x25){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG2_CH1_16BIT;
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXG2_16BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x26){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.EXG2_CH2_16BIT;
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_EXG2_16BIT;
				}
			}
			else if ((byte)signalId[i-iTS]==(byte)0x27){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_HIGH;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_BRIDGE_AMP;
				} 
			}
			else if ((byte)signalId[i-iTS]==(byte)0x28){
				if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					signalNameArray[i+1]=Shimmer3.ObjectClusterSensorName.BRIDGE_AMP_LOW;
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors |= SENSOR_BRIDGE_AMP;
				} 
			}
			else{
				signalNameArray[i+1]=Byte.toString(signalId[i-iTS]);
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
			}
		}
		
//		int offset = nC + 2;
//		signalNameArray[offset]=Shimmer3.ObjectClusterSensorName.BATT_PERCENTAGE;
//		signalNameArray[offset+1]=Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT;
//		signalNameArray[offset+2]=Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_TRIAL;
//		signalNameArray[offset+3]=Shimmer3.ObjectClusterSensorName.ECG_LL_LA_16BIT;
		
		mSignalNameArray=signalNameArray;
		mSignalDataTypeArray=signalDataTypeArray;
		setPacketSize(packetSize);
	}

	/**
	 * Should only be used when Shimmer is Connected and Initialized
	 */
	@Deprecated
	public static BiMap<String,String> generateBiMapSensorIDtoSensorName(int shimmerVersion){
		BiMap<String, String> sensorBitmaptoName =null;  
		if (shimmerVersion != HW_ID.SHIMMER_2R){
			final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
			tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
			tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
			tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
			tempSensorBMtoName.put(Integer.toString(SENSOR_BMPX80), "Pressure");
			tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Low Noise Accelerometer");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_DACCEL), "Wide Range Accelerometer");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG1_24BIT), "EXG1");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG2_24BIT), "EXG2");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG1_16BIT), "EXG1 16Bit");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG2_16BIT), "EXG2 16Bit");
			sensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
		} else {
			final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
			tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Accelerometer");
			tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EMG), "EMG");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_ECG), "ECG");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
			tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
			tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
			sensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
		}
		return sensorBitmaptoName;
	}

	/**
	 * Should only be used when Shimmer is Connected and Initialized
	 */
	@Deprecated
	public void generateBiMapSensorIDtoSensorName(){
		if (getHardwareVersion() != -1){
			if (getHardwareVersion() != HW_ID.SHIMMER_2R){
				final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
				tempSensorBMtoName.put(Integer.toString(SENSOR_BMPX80), "Pressure");
				tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
				tempSensorBMtoName.put(Integer.toString(SHIMMER3_SENSOR_ECG), "ECG");  
				tempSensorBMtoName.put(Integer.toString(SHIMMER3_SENSOR_EMG), "EMG");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
				tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
				tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
				tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Low Noise Accelerometer");
				tempSensorBMtoName.put(Integer.toString(SENSOR_DACCEL), "Wide Range Accelerometer");
				mSensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
			} else {
				final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
				tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Accelerometer");
				tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");
				tempSensorBMtoName.put(Integer.toString(SENSOR_ECG), "ECG");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EMG), "EMG");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
				tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
				tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
				mSensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
			}
		}
	}


	/* (non-Javadoc)
	 * @see com.shimmerresearch.driver.ShimmerDevice#getListofEnabledSensorSignals()
	 */
	@Override
	public String[] getListofEnabledChannelSignals(){
		List<String> listofSignals = new ArrayList<String>();
		if (getHardwareVersion()==HW_ID.SHIMMER_2 || getHardwareVersion()==HW_ID.SHIMMER_2R){
			listofSignals.add("Timestamp");
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				listofSignals.add(Shimmer2.ObjectClusterSensorName.ACCEL_X);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.ACCEL_Y);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.ACCEL_Z);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.GYRO_X);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.GYRO_Y);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.GYRO_Z);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.MAG_X);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.MAG_Y);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.MAG_Z);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.GSR);
				//listofSignals.add(Shimmer2.ObjectClusterSensorName.GSR_RES);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.ECG_RA_LL);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.ECG_LA_LL);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.EMG);
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW);
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.HEART_RATE);
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) && getPMux() == 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0);
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0 && getPMux() == 0)) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7);
			}
			if (((mEnabledSensors & 0xFFFF) & SENSOR_BATT) > 0) {
				listofSignals.add(Shimmer2.ObjectClusterSensorName.BATTERY);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.REG);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && is3DOrientationEnabled()){
				listofSignals.add(Shimmer2.ObjectClusterSensorName.EULER_9DOF_YAW);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.EULER_9DOF_PITCH);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.EULER_9DOF_ROLL);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && is3DOrientationEnabled()){
				listofSignals.add(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_W);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_X);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y);
				listofSignals.add(Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z);
			}

		} else {
			//Changed to the approach below based on the SensorMap implemented in the ShimmerDevice.getListofEnabledSensorSignals()
			return super.getListofEnabledChannelSignals();
		}
		
		String[] enabledSignals = listofSignals.toArray(new String[listofSignals.size()]);
		return enabledSignals;
	}	

	/** Shimmer2r only
	 * @param bufferCalibrationParameters
	 * @param packetType
	 */
	protected void retrieveBiophysicalCalibrationParametersFromPacket(byte[] bufferCalibrationParameters, int packetType){
		if (packetType == ECG_CALIBRATION_RESPONSE){
			if (bufferCalibrationParameters[0]==-1 && bufferCalibrationParameters[1] == -1 && bufferCalibrationParameters[2] == -1 && bufferCalibrationParameters[3]==-1){
				mDefaultCalibrationParametersECG = true;
			} else {
				mDefaultCalibrationParametersECG = false;
				OffsetECGLALL=(double)((bufferCalibrationParameters[0]&0xFF)<<8)+(bufferCalibrationParameters[1]&0xFF);
				GainECGLALL=(double)((bufferCalibrationParameters[2]&0xFF)<<8)+(bufferCalibrationParameters[3]&0xFF);
				OffsetECGRALL=(double)((bufferCalibrationParameters[4]&0xFF)<<8)+(bufferCalibrationParameters[5]&0xFF);
				GainECGRALL=(double)((bufferCalibrationParameters[6]&0xFF)<<8)+(bufferCalibrationParameters[7]&0xFF);
			}	
		}

		if (packetType == EMG_CALIBRATION_RESPONSE){

			if (bufferCalibrationParameters[0]==-1 && bufferCalibrationParameters[1] == -1 && bufferCalibrationParameters[2] == -1 && bufferCalibrationParameters[3]==-1){
				mDefaultCalibrationParametersEMG = true;
			} else {
				mDefaultCalibrationParametersEMG = false;
				OffsetEMG=(double)((bufferCalibrationParameters[0]&0xFF)<<8)+(bufferCalibrationParameters[1]&0xFF);
				GainEMG=(double)((bufferCalibrationParameters[2]&0xFF)<<8)+(bufferCalibrationParameters[3]&0xFF);
			}
		}
	}

	/**
	 * Unwraps the timestamp based on the current recording (i.e., per file for
	 * SD recordings not taking into account the initial file start time)
	 * 
	 * @param timeStampTicks
	 * @return
	 */
	protected double unwrapTimeStamp(double timeStampTicks){
		//first convert to continuous time stamp
		double timestampUnwrappedTicks = calculateTimeStampUnwrapped(timeStampTicks);
		
		//Check if there was a roll-over
		if (getLastReceivedTimeStampTicksUnwrapped()>timestampUnwrappedTicks){ 
			mCurrentTimeStampCycle += 1;
			//Recalculate timestamp
			timestampUnwrappedTicks = calculateTimeStampUnwrapped(timeStampTicks);
		}

		setLastReceivedTimeStampTicksUnwrapped(timestampUnwrappedTicks);

		return timestampUnwrappedTicks;
	}

	private double calculateTimeStampUnwrapped(double timeStampTicks) {
		return timeStampTicks+(mTimeStampTicksMaxValue*mCurrentTimeStampCycle);
	}


//	@Override
	private void calculateTrialPacketLoss(double timestampUnwrappedMilliSecs) {
		//TODO currently this check is ok here as this method is called for each packet but this is excessive. 
		//Store in order to trigger packet loss calculations while streaming in real-time
		if (!mStreamingStartTimeSaved){
			mStreamingStartTimeSaved=true;
			mStreamingStartTimeMilliSecs = timestampUnwrappedMilliSecs;   // to convert into mS
		}

		if(mStreamingStartTimeMilliSecs>0){
			double timeDifference = timestampUnwrappedMilliSecs - mStreamingStartTimeMilliSecs;
			
			//The expected time difference (in milliseconds) based on the device's sampling rate
			double expectedTimeDifference = (1/getSamplingRateShimmer())*1000;

			long packetExpectedCount = (long) (timeDifference/expectedTimeDifference);
			setPacketExpectedCountOverall(packetExpectedCount);
			
			long packetReceivedCount = getPacketReceivedCountOverall();

			//For legacy support
			long packetLossCountPerTrial = packetExpectedCount + packetReceivedCount;
			setPacketLossCountPerTrial(packetLossCountPerTrial);

			double packetReceptionRateTrial = ((double)packetReceivedCount/(double)packetExpectedCount)*100; 
			setPacketReceptionRateOverall(packetReceptionRateTrial);
		}
	}

	public void resetCalibratedTimeStamp(){
		setLastReceivedTimeStampTicksUnwrapped(0);
		mLastReceivedCalibratedTimeStamp = -1;
		
		mStreamingStartTimeSaved = false;
		mStreamingStartTimeMilliSecs = -1;
		
		setCurrentTimeStampCycle(0);
	}


	//protected abstract void sendStatusMsgPacketLossDetected();
	protected void sendStatusMsgPacketLossDetected() {
		
	}

	public int getPMux(){
		if ((mConfigByte0 & (byte)64)!=0) {
			//then set ConfigByte0 at bit position 7
			return 1;
		} else{
			return 0;
		}
	}

	protected ObjectCluster callAdditionalServices(ObjectCluster objectCluster) {
		return objectCluster;
	}

	//TODO unlink from ShimmerFile and move to ShimmerBluetooth
	/**Only for Bluetooth
	 * @param bufferInquiry
	 */
	protected void interpretInqResponse(byte[] bufferInquiry){
		try {
		if (getHardwareVersion()==HW_ID.SHIMMER_2 || getHardwareVersion()==HW_ID.SHIMMER_2R){
			setPacketSize(mTimeStampPacketByteSize +bufferInquiry[3]*2); 
			setSamplingRateShimmer((double)1024/bufferInquiry[0]);
			if (getMagRate()==3 && getSamplingRateShimmer()>10){
				setLowPowerMag(true);
			}
			setDigitalAccelRange(bufferInquiry[1]);
			mConfigByte0 = bufferInquiry[2] & 0xFF; //convert the byte to unsigned integer
			mNChannels = bufferInquiry[3];
			mBufferSize = bufferInquiry[4];
			byte[] signalIdArray = new byte[mNChannels];
			System.arraycopy(bufferInquiry, 5, signalIdArray, 0, mNChannels);
			updateEnabledSensorsFromChannels(signalIdArray);
			interpretDataPacketFormat(mNChannels,signalIdArray);
			mInquiryResponseBytes = new byte[5+mNChannels];
			System.arraycopy(bufferInquiry, 0, mInquiryResponseBytes , 0, mInquiryResponseBytes.length);
		} 
		else if (getHardwareVersion()==HW_ID.SHIMMER_3) {
			if(bufferInquiry.length>=8){
				mPacketSize = mTimeStampPacketByteSize+bufferInquiry[6]*2; 
				double samplingRate = convertSamplingRateBytesToFreq(bufferInquiry[0], bufferInquiry[1], getSamplingClockFreq());
				setSamplingRateShimmer(samplingRate);
//				setSamplingRateShimmer((getSamplingClockFreq()/(double)((int)(bufferInquiry[0] & 0xFF) + ((int)(bufferInquiry[1] & 0xFF) << 8))));
				mNChannels = bufferInquiry[6];
				mBufferSize = bufferInquiry[7];
				mConfigByte0 = ((long)(bufferInquiry[2] & 0xFF) +((long)(bufferInquiry[3] & 0xFF) << 8)+((long)(bufferInquiry[4] & 0xFF) << 16) +((long)(bufferInquiry[5] & 0xFF) << 24));
				setDigitalAccelRange(((int)(mConfigByte0 & 0xC))>>2);
				setGyroRange(((int)(mConfigByte0 & 196608))>>16);
				setLSM303MagRange(((int)(mConfigByte0 & 14680064))>>21);
				setLSM303DigitalAccelRate(((int)(mConfigByte0 & 0xF0))>>4);
				setMPU9150GyroAccelRate(((int)(mConfigByte0 & 65280))>>8);
				setMagRate(((int)(mConfigByte0 & 1835008))>>18); 
				setPressureResolution((((int)(mConfigByte0 >>28)) & 3));
				setGSRRange((((int)(mConfigByte0 >>25)) & 7));
				setInternalExpPower(((int)(mConfigByte0 >>24)) & 1);
				mInquiryResponseBytes = new byte[8+mNChannels];
				System.arraycopy(bufferInquiry, 0, mInquiryResponseBytes , 0, mInquiryResponseBytes.length);
				if ((getLSM303DigitalAccelRate()==2 && getSamplingRateShimmer()>10)){
					setLowPowerAccelWR(true);
				}
				checkLowPowerGyro();
				checkLowPowerMag();
				
				if(bufferInquiry.length>=(8+mNChannels)){
					byte[] signalIdArray = new byte[mNChannels];
					System.arraycopy(bufferInquiry, 8, signalIdArray, 0, mNChannels);
					updateEnabledSensorsFromChannels(signalIdArray);
					if(mUseInfoMemConfigMethod && getFirmwareVersionCode()>=6){
					} else {
						setEnabledAndDerivedSensorsAndUpdateMaps(mEnabledSensors, mDerivedSensors);
					}
					interpretDataPacketFormat(mNChannels,signalIdArray);
					checkExgResolutionFromEnabledSensorsVar();
				}
			}
		} 
		else if (getHardwareVersion()==HW_ID.SHIMMER_3R) {
			if(bufferInquiry.length>=11){
				mPacketSize = mTimeStampPacketByteSize+bufferInquiry[9]*2;  
				double samplingRate = convertSamplingRateBytesToFreq(bufferInquiry[0], bufferInquiry[1], getSamplingClockFreq());
				setSamplingRateShimmer(samplingRate);
//				setSamplingRateShimmer((getSamplingClockFreq()/(double)((int)(bufferInquiry[0] & 0xFF) + ((int)(bufferInquiry[1] & 0xFF) << 8))));
				mNChannels = bufferInquiry[9];
				mBufferSize = bufferInquiry[10];
				mConfigByte0 = ((long)(bufferInquiry[2] & 0xFF) +((long)(bufferInquiry[3] & 0xFF) << 8)+((long)(bufferInquiry[4] & 0xFF) << 16) +((long)(bufferInquiry[5] & 0xFF) << 24)
						+((long)(bufferInquiry[6] & 0xFF) << 32)+((long)(bufferInquiry[7] & 0xFF) << 40)+((long)(bufferInquiry[8] & 0xFF) << 48) );
				setDigitalAccelRange(((int)(mConfigByte0 & 0xC))>>2);
//				setGyroRange(((int)(mConfigByte0 & 196608))>>16);
				int gyroRange = (int)(((mConfigByte0 & 196608) >> 16) & 3);
				int msbGyroRange = (int)((mConfigByte0 >> 34) & 1);
				gyroRange = gyroRange + (msbGyroRange << 2);
				setGyroRange(gyroRange);
				//setLSM303MagRange(((int)(mConfigByte0 & 14680064))>>21);
				setAltMagRange(((int)(mConfigByte0 & 14680064))>>21);
				setLIS2DW12DigitalAccelRate(((int)(mConfigByte0 & 0xF0))>>4);
				
				setLowPowerAccelWR((getLIS2DW12DigitalAccelRate()==1)? true:false);
				setLSM6DSVGyroAccelRate(((int)(mConfigByte0 & 65280))>>8);
				checkLowPowerGyro();
				
				int magSamplingRate = (int)((mConfigByte0 >> 18) & 0x07);
				int altMagSamplingRate = (int)((mConfigByte0 >> 40) & 0x3F); //8+8+8+8+8
				//int MSB_MAG_RATE = (int)((mConfigByte0 >> 43) & 0x07); //8+8+8+8+8+3
				//magSamplingRate = magSamplingRate + (MSB_MAG_RATE << 3);
				setMagRate(magSamplingRate); 
				setAltMagRate(altMagSamplingRate); 

				checkLowPowerMag();
				
//				setPressureResolution((((int)(mConfigByte0 >>28)) & 3));
				setGSRRange((((int)(mConfigByte0 >>25)) & 7));
				setInternalExpPower(((int)(mConfigByte0 >>24)) & 1);
				
				int pressureResolution = (((int)(mConfigByte0 >>28)) & 3);
				int msbPressureResolution = (((int)(mConfigByte0 >>32)) & 1);
				pressureResolution = pressureResolution + (msbPressureResolution << 2);
				setPressureResolution(pressureResolution);
				
				mInquiryResponseBytes = new byte[11+mNChannels];
				System.arraycopy(bufferInquiry, 0, mInquiryResponseBytes , 0, mInquiryResponseBytes.length);
			
				if(bufferInquiry.length>=(11+mNChannels)){
					byte[] signalIdArray = new byte[mNChannels];
					System.arraycopy(bufferInquiry, 11, signalIdArray, 0, mNChannels);
					updateEnabledSensorsFromChannels(signalIdArray);
					if(mUseInfoMemConfigMethod && getFirmwareVersionCode()>=6){
					} else {
						setEnabledAndDerivedSensorsAndUpdateMaps(mEnabledSensors, mDerivedSensors);
					}
					interpretDataPacketFormat(mNChannels,signalIdArray);
					checkExgResolutionFromEnabledSensorsVar();
				}
			}
		}
		else if (getHardwareVersion()==HW_ID.SHIMMER_SR30) {
			mPacketSize = mTimeStampPacketByteSize+bufferInquiry[2]*2; 
			setSamplingRateShimmer((double)1024/bufferInquiry[0]);
			setDigitalAccelRange(bufferInquiry[1]);
			mNChannels = bufferInquiry[2];
			mBufferSize = bufferInquiry[3];
			byte[] signalIdArray = new byte[mNChannels];
			System.arraycopy(bufferInquiry, 4, signalIdArray, 0, mNChannels); // this is 4 because there is no config byte
			interpretDataPacketFormat(mNChannels,signalIdArray);
		}
		} catch (Exception ex) {
			ex.printStackTrace();
			try {
				disconnect();
			} catch (ShimmerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	/** No need for this any more with correct usage of EnabledSensors, DerivedSensors and the SensorMap */
	@Deprecated 
	protected void updateEnabledSensorsFromChannels(byte[] channels){
		// set the sensors value
		// crude way of getting this value, but allows for more customised firmware
		// to still work with this application
		// e.g. if any axis of the accelerometer is being transmitted, then it will
		// recognise that the accelerometer is being sampled
		int enabledSensors = 0;
		for (int i=0;i<channels.length;i++)
		{
			if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
				
				if (channels[i]==Configuration.Shimmer3.Channel.XAAccel || channels[i]==Configuration.Shimmer3.Channel.YAAccel || channels[i]==Configuration.Shimmer3.Channel.ZAAccel){
					enabledSensors = enabledSensors | SENSOR_ACCEL;
				}
				
				if (channels[i]==Configuration.Shimmer3.Channel.XDAccel || channels[i]==Configuration.Shimmer3.Channel.YDAccel||channels[i]==Configuration.Shimmer3.Channel.ZDAccel){
					enabledSensors = enabledSensors | SENSOR_DACCEL;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.XGyro || channels[i]==Configuration.Shimmer3.Channel.YGyro||channels[i]==Configuration.Shimmer3.Channel.ZGyro){
					enabledSensors = enabledSensors | SENSOR_GYRO;
				}
				
				if (channels[i]==Configuration.Shimmer3.Channel.XMag || channels[i]==Configuration.Shimmer3.Channel.YMag||channels[i]==Configuration.Shimmer3.Channel.ZMag){
					enabledSensors = enabledSensors | SENSOR_MAG;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.VBatt){
					enabledSensors = enabledSensors | SENSOR_BATT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc7){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A7;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc6){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A6;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc15){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A15;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc1){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A1;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc12){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A12;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc13){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A13;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc14){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A14;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc9){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A7;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc11){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A6;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc12){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A15;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc17){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A1;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc10){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A12;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc15){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A13;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc16){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A14;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.Pressure){
					enabledSensors = enabledSensors | SENSOR_BMPX80;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.Temperature){
					enabledSensors = enabledSensors | SENSOR_BMPX80;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.GsrRaw){
					enabledSensors = enabledSensors | SENSOR_GSR;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_STATUS){
					//enabledSensors = enabledSensors | SENSOR_EXG1_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH1_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH2_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH1_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_16BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH2_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_16BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_STATUS){
					//enabledSensors = enabledSensors | SENSOR_EXG2_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH1_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH2_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH1_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_16BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH2_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_16BIT;
				}
				if ((channels[i] == Configuration.Shimmer3.Channel.BridgeAmpHigh) || (channels[i] == Configuration.Shimmer3.Channel.BridgeAmpLow)){
					enabledSensors = enabledSensors | SENSOR_BRIDGE_AMP;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.XAlterAccel || channels[i]==Configuration.Shimmer3.Channel.YAlterAccel || channels[i]==Configuration.Shimmer3.Channel.ZAlterAccel){
					enabledSensors = enabledSensors | SENSOR_ALT_ACCEL;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.XAlterMag || channels[i]==Configuration.Shimmer3.Channel.YAlterMag || channels[i]==Configuration.Shimmer3.Channel.ZAlterMag){
					enabledSensors = enabledSensors | SENSOR_ALT_MAG;
				}

			}
			else if(getHardwareVersion()==HW_ID.SHIMMER_2R){
				if (channels[i]==Configuration.Shimmer2.Channel.XAccel || channels[i]==Configuration.Shimmer2.Channel.YAccel||channels[i]==Configuration.Shimmer2.Channel.ZAccel){
					enabledSensors = enabledSensors | SENSOR_ACCEL;
				}
				if (channels[i]==Configuration.Shimmer2.Channel.XGyro || channels[i]==Configuration.Shimmer2.Channel.YGyro ||channels[i]==Configuration.Shimmer2.Channel.ZGyro){
					enabledSensors = enabledSensors | SENSOR_GYRO;
				}
				if (channels[i]==Configuration.Shimmer2.Channel.XMag || channels[i]==Configuration.Shimmer2.Channel.XMag ||channels[i]==Configuration.Shimmer2.Channel.XMag){
					enabledSensors = enabledSensors | SENSOR_MAG;
				}        	
				if ((channels[i] == Configuration.Shimmer2.Channel.EcgLaLl) || (channels[i] == Configuration.Shimmer2.Channel.EcgRaLl)){
					enabledSensors = enabledSensors | SENSOR_ECG;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.Emg){
					enabledSensors = enabledSensors | SENSOR_EMG;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA0 && getPMux()==0){
					enabledSensors = enabledSensors | SENSOR_EXP_BOARD_A0;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA7 && getPMux()==0){
					enabledSensors = enabledSensors | SENSOR_EXP_BOARD_A7;
				}
				else if ((channels[i] == Configuration.Shimmer2.Channel.BridgeAmpHigh) || (channels[i] == Configuration.Shimmer2.Channel.BridgeAmpLow)){
					enabledSensors = enabledSensors | SENSOR_BRIDGE_AMP;
				}
				else if ((channels[i] == Configuration.Shimmer2.Channel.GsrRaw) || (channels[i] == Configuration.Shimmer2.Channel.GsrRes)){
					enabledSensors = enabledSensors | SENSOR_GSR;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.HeartRate){
					enabledSensors = enabledSensors | SENSOR_HEART;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA0 && getPMux()==1){
					enabledSensors = enabledSensors | SENSOR_BATT;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA7 && getPMux()==1){
					enabledSensors = enabledSensors | SENSOR_BATT;
				}
			} 
		}
		mEnabledSensors=enabledSensors;	
	}
	
	public String getBluetoothAddress(){
		return  mMyBluetoothAddress;
	}

	/** replaced with getShimmerUserAssignedName()
	 * @return
	 */
	@Deprecated 
	public String getDeviceName(){
		return getShimmerUserAssignedName();
	}

	/** replaced with setShimmerUserAssignedName()
	 * @return
	 */
	@Deprecated 
	public void setDeviceName(String deviceName) {
		setShimmerUserAssignedName(deviceName);
//		mShimmerUserAssignedName = deviceName;
	}
	
	public byte[] getRawInquiryResponse(){
		return mInquiryResponseBytes;
	}

	public byte[] getRawCalibrationParameters(){

		byte[] rawcal=new byte[1];
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R)
		{
			//Accel + Digi Accel + Gyro + Mag + Pressure
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
			try {
				outputStream.write(5); // write the number of different calibration parameters
				
				outputStream.write( getAccelCalRawParams().length);
				outputStream.write( getAccelCalRawParams());
				
				outputStream.write( getDigiAccelCalRawParams().length);
				outputStream.write( getDigiAccelCalRawParams() );
				outputStream.write( getGyroCalRawParams().length );
				outputStream.write( getGyroCalRawParams() );
				outputStream.write( getMagCalRawParams().length );
				outputStream.write( getMagCalRawParams() );
				outputStream.write( getPressureRawCoefficients().length);
				outputStream.write( getPressureRawCoefficients() );
				rawcal = outputStream.toByteArray( );
			} catch (IOException e) {
				e.printStackTrace();
			}			

		} else if (getHardwareVersion()==HW_ID.SHIMMER_2 ||getHardwareVersion()==HW_ID.SHIMMER_2R)
		{
			//Accel + Digi Accel + Gyro + Mag + Pressure
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
			try 
			{
				outputStream.write(5); // write the number of different calibration parameters
				outputStream.write( getAccelCalRawParams().length);
				outputStream.write( getAccelCalRawParams());
				outputStream.write( getGyroCalRawParams().length );
				outputStream.write( getGyroCalRawParams() );
				outputStream.write( getMagCalRawParams().length );
				outputStream.write( getMagCalRawParams() );
				outputStream.write( mECGCalRawParams.length );
				outputStream.write( mECGCalRawParams );
				outputStream.write( mEMGCalRawParams.length );
				outputStream.write( mEMGCalRawParams );
				rawcal = outputStream.toByteArray( );
			} catch (IOException e) {
				e.printStackTrace();
			}		

		} else {
			rawcal[0]=0;
		}
		return rawcal;

	}


	/** Very old method, not up to date and should be based on SensorMap instead. Could also be conflicting with ShimmerDevice.getListOfEnabledSensors() */ 
	@Deprecated 
	public List<String> getListofEnabledSensors(){
		List<String> listofSensors = new ArrayList<String>();
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
			
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				listofSensors.add("Low Noise Accelerometer");
			}
			
			if ((mEnabledSensors & SENSOR_DACCEL) > 0){
				listofSensors.add("Wide Range Accelerometer");
			}
			
		} else {
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				listofSensors.add("Accelerometer");
			}
		}
		
		if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
			listofSensors.add("Gyroscope");
		}
		if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
			listofSensors.add("Magnetometer");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
			listofSensors.add("GSR");
		}
		if (getHardwareVersion()==HW_ID.SHIMMER_2 || getHardwareVersion()==HW_ID.SHIMMER_2R){
			if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
				listofSensors.add("ECG");
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
				listofSensors.add("EMG");
			}
		}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				listofSensors.add("Bridge Amplifier");
			}
		
		if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
			listofSensors.add("Heart Rate");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0 && (mEnabledSensors & SENSOR_BATT) == 0 && getHardwareVersion() != HW_ID.SHIMMER_3) {
			listofSensors.add("ExpBoard A0");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0  && (mEnabledSensors & SENSOR_BATT) == 0 && getHardwareVersion() != HW_ID.SHIMMER_3) {
			listofSensors.add("ExpBoard A7");
		}
		if ((mEnabledSensors & SENSOR_BATT) > 0) {
			listofSensors.add("Battery Voltage");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXT_ADC_A7) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("External ADC A7");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXT_ADC_A6) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("External ADC A6");
		}
		if (((mEnabledSensors & 0xFFFF) & SENSOR_EXT_ADC_A15) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("External ADC A15");
		}
		if (((mEnabledSensors & 0xFFFF) & SENSOR_INT_ADC_A1) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("Internal ADC A1");
		}
		if (((mEnabledSensors & 0xFFFF) & SENSOR_INT_ADC_A12) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("Internal ADC A12");
		}
		if (((mEnabledSensors & 0xFFFFFF) & SENSOR_INT_ADC_A13) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("Internal ADC A13");
		}
		if (((mEnabledSensors & 0xFFFFFF) & SENSOR_INT_ADC_A14) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("Internal ADC A14");
		}
		if ((mEnabledSensors & SENSOR_BMPX80) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("Pressure");
		}
		if ((mEnabledSensors & SENSOR_EXG1_24BIT) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("EXG1");
		}
		if ((mEnabledSensors & SENSOR_EXG2_24BIT) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("EXG2");
		}
		if ((mEnabledSensors & SENSOR_EXG1_16BIT) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("EXG1 16Bit");
		}
		if ((mEnabledSensors & SENSOR_EXG2_16BIT) > 0  && getHardwareVersion() == HW_ID.SHIMMER_3) {
			listofSensors.add("EXG2 16Bit");
		}

		return listofSensors;
	}
	
	/** Returns a list of string[] of the four properties. 1) Shimmer Name - 2) Property/Signal Name - 3) Format Name - 4) Unit Name
	 * @return list string array of properties
	 */
	@Override
	public List<String[]> getListofEnabledChannelSignalsandFormats(){
		List<String[]> listofSignals = new ArrayList<String[]>();
		 
		if (getHardwareVersion()==HW_ID.SHIMMER_2 || getHardwareVersion()==HW_ID.SHIMMER_2R){
			String[] channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.TIMESTAMP,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLISECONDS};
			listofSignals.add(channel);
			channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.TIMESTAMP,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
			listofSignals.add(channel);
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				String unit = CHANNEL_UNITS.METER_PER_SECOND_SQUARE;
				if (mSensorMMA736x.mIsUsingDefaultLNAccelParam) {
//				if (mDefaultCalibrationParametersAccel == true) {
					unit += "*";
				}
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ACCEL_X,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ACCEL_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ACCEL_Y,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ACCEL_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ACCEL_Z,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ACCEL_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				String unit = CHANNEL_UNITS.DEGREES_PER_SECOND;
//				if (mDefaultCalibrationParametersGyro == true) {
				if(mSensorShimmer2Gyro.mIsUsingDefaultGyroParam) {
					unit += "*";
				} 
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GYRO_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GYRO_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GYRO_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				String unit = CHANNEL_UNITS.LOCAL_FLUX;
//				if (mDefaultCalibrationParametersMag == true) {
				if(mSensorShimmer2Mag.mIsUsingDefaultMagParam) {
					unit += "*";
				} 
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.MAG_X,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.MAG_Y,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.MAG_Z,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GSR,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.KOHMS};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.GSR,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				//channel = new String[]{mMyName,Shimmer2.ObjectClusterSensorName.GSR_RES,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				//listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
				
				String unit = CHANNEL_UNITS.MILLIVOLTS;
				if (mDefaultCalibrationParametersECG == true) {
					unit += "*";
				}
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ECG_RA_LL,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ECG_RA_LL,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ECG_LA_LL,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.ECG_LA_LL,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
				String unit = CHANNEL_UNITS.MILLIVOLTS;
				if (mDefaultCalibrationParametersECG == true) {
					unit += "*";
				}
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EMG,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EMG,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				String unit = CHANNEL_UNITS.MILLIVOLTS;
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_HIGH,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.BRIDGE_AMP_LOW,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
				String unit = CHANNEL_UNITS.BEATS_PER_MINUTE;
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.HEART_RATE,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.HEART_RATE,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) && getPMux() == 0) {
				String unit = CHANNEL_UNITS.MILLIVOLTS;
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EXP_BOARD_A0,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0 && getPMux() == 0)) {
				String unit = CHANNEL_UNITS.MILLIVOLTS;
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7,CHANNEL_TYPE.CAL.toString(),unit};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EXP_BOARD_A7,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFF) & SENSOR_BATT) > 0) {
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.REG,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.REG,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.MILLIVOLTS};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.BATTERY,CHANNEL_TYPE.UNCAL.toString(),CHANNEL_UNITS.NO_UNITS};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && is3DOrientationEnabled()){
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EULER_9DOF_YAW,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EULER_9DOF_PITCH,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.EULER_9DOF_ROLL,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_W,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_X,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
				channel = new String[]{mShimmerUserAssignedName,Shimmer2.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z,CHANNEL_TYPE.CAL.toString(),CHANNEL_UNITS.LOCAL};
				listofSignals.add(channel);
			}
//			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
//				String unit = "local";
//				channel = new String[]{mMyName,"Quaternion 0",CHANNEL_TYPE.CALIBRATED,unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 1",CHANNEL_TYPE.CALIBRATED,unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 2",CHANNEL_TYPE.CALIBRATED,unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 3",CHANNEL_TYPE.CALIBRATED,unit};
//				listofSignals.add(channel);
//			}

		} else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R) {
			//Below based on SensorMap approach
			return super.getListofEnabledChannelSignalsandFormats();
		}
		
		
		//Currently just used by the REAL_TIME_CLOCK channel
		if (mExtraSignalProperties != null){
			listofSignals.addAll(mExtraSignalProperties);
		}
		
		//Process enabled algorithms
		listofSignals.addAll(getListofEnabledAlgorithmsSignalsandFormats());
		
		return listofSignals;
	}
	
	@Deprecated
	public void addAlgorithm(String key,AbstractAlgorithm aobj){
		if (!doesAlgorithmAlreadyExist(aobj)){
			mMapOfAlgorithmModules.put(key,aobj);
			String[] outputNameArray = aobj.getSignalOutputNameArray();
			String[] outputFormatArray = aobj.getSignalOutputFormatArray();
			String[] outputUnitArray = aobj.getSignalOutputUnitArray();

			for (int i=0;i<outputNameArray.length;i++){
				String[] prop= new String[4];
				prop[0] = mShimmerUserAssignedName;
				prop[1] = outputNameArray[i];
				prop[2] = outputFormatArray[i];
				prop[3] = outputUnitArray[i];
				addExtraSignalProperty(prop);
			}
		}
	}
	
	@Deprecated
	public void addExtraSignalProperty(String [] property){
		if (mExtraSignalProperties==null){
			mExtraSignalProperties = new ArrayList<String[]>();
		}
		mExtraSignalProperties.add(property);
	}
	
	@Deprecated
	public void clearExtraSignalProperties(){
		if (mExtraSignalProperties!=null){
			mExtraSignalProperties.clear();
		}
	}
	
	@Deprecated
	public void removeExtraSignalProperty(String [] property){
		if(mExtraSignalProperties!=null){//JC: fix for consensys 4.3
			for (int i=mExtraSignalProperties.size()-1;i>-1;i--){
				String[]p = mExtraSignalProperties.get(i);
				if (p[0].equals(property[0]) && p[1].equals(property[1]) && p[2].equals(property[2]) && p[3].equals(property[3])){
					mExtraSignalProperties.remove(i);
				}

			}
		}
	}
	
	//AlignmentMatrixMag, SensitivityMatrixMag, OffsetVectorMag

	public int getRTCSetByBT(){
		return mRTCSetByBT;
	}

	public void setRTCSetByBT(int RTCSetByBT){
		mRTCSetByBT = RTCSetByBT;
	}

	/**
	 * The RTC difference is calculated by the Shimmer itself once the current
	 * real time clock is sent to it by software. The RTC difference represents
	 * the difference between the current Shimmer clock time at the point when
	 * the RTC is set and the RTC time. 
	 * 
	 * This is used in Binary SD data files. 
	 * 
	 * @return
	 */
	public long getRTCDifferenceInTicks(){
		return mRTCDifferenceInTicks;
	}

	/**
	 * The RTC difference is calculated by the Shimmer itself once the current
	 * real time clock is sent to it by software. The RTC difference represents
	 * the difference between the current Shimmer clock time at the point when
	 * the RTC is set and the RTC time.
	 * 
	 * This is used in Binary SD data files. 
	 * 
	 * @param rtcOffset
	 */
	public void setRTCDifferenceInTicks(long rtcOffset){
		mRTCDifferenceInTicks = rtcOffset;
	}
	
	/**
	 * The RTC difference is calculated by the Shimmer itself once the current
	 * real time clock is sent to it by software. The RTC difference represents
	 * the difference between the current Shimmer clock time at the point when
	 * the RTC is set and the RTC time.
	 * 
	 * This is used in Binary SD data files. 
	 * 
	 * @return
	 */
	public boolean isRtcDifferenceSet(){
		return (mRTCDifferenceInTicks==0)? false:true;
	}

	/**
	 * The initial timestamp is stored in 8 bytes (legacy=5 bytes) in the header
	 * of each SD data file. This is the Shimmer clock value at the point at
	 * which each file is created.
	 * 
	 * @return
	 */
	public long getInitialTimeStampTicksSd(){
		return mInitialTimeStampTicksSd;
	}
	
	/**
	 * The initial timestamp is stored in 8 bytes (legacy=5 bytes) in the header
	 * of each SD data file. This is the Shimmer clock value at the point at
	 * which each file is created.
	 * 
	 * @param initialTimeStamp
	 */
	public void setInitialTimeStampTicksSd(long initialTimeStamp){
		mInitialTimeStampTicksSd = initialTimeStamp;
	}
	
	/** This sets the time stamp cycle, when unwrapping time stamps
	 * @param timeStampCycle
	 */
	public void setCurrentTimeStampCycle(double timeStampCycle){
		mCurrentTimeStampCycle = timeStampCycle;
	}

	public double getCurrenTimeStampCycle(){
		return mCurrentTimeStampCycle;
	}
	
	/**
	 * @return the mLastReceivedTimeStamp
	 */
	public double getLastReceivedTimeStampTicksUnwrapped(){
		return mLastReceivedTimeStampTicksUnwrapped;
	}
	
	public void setLastReceivedTimeStampTicksUnwrapped(double lastReceivedTimeStampTicksUnwrapped){
		mLastReceivedTimeStampTicksUnwrapped = lastReceivedTimeStampTicksUnwrapped;
	}

	public void updateTimestampByteLength(){
		//Once the version is known update settings accordingly 
		if (getFirmwareVersionCode()>=6){
			mTimeStampPacketByteSize = 3;
//			mTimeStampPacketMaxValueTicks = 16777216;
		} 
		else {//if (getFirmwareVersionCode()<6){
			mTimeStampPacketByteSize = 2;
//			mTimeStampPacketMaxValueTicks = 65536;
		}
		mTimeStampTicksMaxValue = (int) Math.pow(2, 8*mTimeStampPacketByteSize);
	}
	

	/**
	 * Suitable for Shimmer2r only. Shimmer3 has been replaced with the sensors
	 * maps approach
	 */
	@Deprecated
	public String[] getListofSupportedSensors(){
		return getListofSupportedSensors(getHardwareVersion());
	}

	/**
	 * Suitable for Shimmer2r only. Shimmer3 has been replaced with the sensors
	 * maps approach
	 */
	@Deprecated
	public static String[] getListofSupportedSensors(int shimmerVersion){
		String[] sensorNames = null;
		if (shimmerVersion==HW_ID.SHIMMER_2R || shimmerVersion==HW_ID.SHIMMER_2){
			sensorNames = Configuration.Shimmer2.ListofCompatibleSensors;
		} else if  (shimmerVersion==HW_ID.SHIMMER_3){
			sensorNames = Configuration.Shimmer3.ListofCompatibleSensors;
		}
		return sensorNames;
	}

	/* (non-Javadoc)
	 * @see com.shimmerresearch.driver.ShimmerDevice#calcMaxSamplingRate()
	 */
	@Override
	protected double calcMaxSamplingRate() {
		double maxGUISamplingRate = super.calcMaxSamplingRate();
		
		if (getHardwareVersion()==HW_ID.SHIMMER_2 || getHardwareVersion()==HW_ID.SHIMMER_2R) {
			maxGUISamplingRate = Math.min(1024.0, maxGUISamplingRate);
		} else if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_GQ_BLE || getHardwareVersion()==HW_ID.SHIMMER_3R) {
			maxGUISamplingRate = Math.min(2048.0, maxGUISamplingRate);
		}	
		return maxGUISamplingRate;
	}

	/* (non-Javadoc)
	 * @see com.shimmerresearch.driver.ShimmerDevice#setSamplingRateSensors(double)
	 */
	@Override
	protected void setSamplingRateSensors(double samplingRateShimmer) {
		super.setSamplingRateSensors(samplingRateShimmer);
		
		if (mShimmerVerObject.isShimmerGen3()) {
			setExGRateFromFreq(samplingRateShimmer);
		}
	}

	/**
	 * Checks to see if the LSM303DLHC Mag is in low power mode. As determined by
	 * the sensor's sampling rate being set to the lowest possible value and not
	 * related to any specific configuration bytes sent to the Shimmer/MPU9150.
	 * 
	 * @return boolean, true if low-power mode enabled
	 */
	public boolean checkLowPowerMag() {
		if(isShimmerGen2()){
			return mSensorShimmer2Mag.checkLowPowerMag();
		} else if (isShimmerGen3()){
			return mSensorLSM303.checkLowPowerMag();
		} else if (isShimmerGen3R()) {
			return mSensorLIS3MDL.checkLowPowerMag();
		}
		return false;
	}
	
	/**
	 * This enables the calculation of 3D orientation through the use of the
	 * gradient descent algorithm, note that the user will have to ensure that
	 * mEnableCalibration has been set to true (see enableCalibration), and that
	 * the accel, gyro and mag has been enabled
	 * 
	 * @param enable
	 */
	protected void set3DOrientation(boolean enable){
		//TODO enable the sensors if they have not been enabled 
		mIsOrientationEnabled = enable;
	}
	
	public boolean is3DOrientationEnabled(){
		return mIsOrientationEnabled;
	}
	
	protected void setupOrientation(int orientation, double samplingRate) {
		if (orientation == 1){
			set3DOrientation(true);
//			setOnTheFlyGyroCal(true);
			enableOnTheFlyGyroCal(true, (int)samplingRate, 1.2);
		} else {
			set3DOrientation(false);
			setOnTheFlyGyroCal(false);
		}
	}

	/* Need to override here because ShimmerDevice uses a different sensormap
	 * (non-Javadoc)
	 * @see com.shimmerresearch.driver.ShimmerDevice#setDefaultShimmerConfiguration()
	 */
	@Override
	public void setDefaultShimmerConfiguration() {
		if (getHardwareVersion() != -1){
			
			setDefaultShimmerName();
			
			clearExgConfig(); // this currently must be called before sensorAndConfigMapsCreate() as it controls ExG booleans which affect EnabledSensors
			sensorAndConfigMapsCreate();
			
			int hwVersion = getHardwareVersion();
			if (hwVersion == HW_ID.SHIMMER_3){
				if(getExpansionBoardId()==HW_ID_SR_CODES.SHIMMER_ECG_MD){
					setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, true);
				} else {
					setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_ANALOG_ACCEL, true);
					setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_MPU9X50_GYRO, true);
					setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LSM303_MAG, true);
					setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_VBATT, true);
					
					setIsAlgorithmEnabled(GyroOnTheFlyCalModule.GENERAL_ALGORITHM_NAME, true);
				}
			} else if (hwVersion == HW_ID.SHIMMER_3R) {
				setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LSM6DSV_ACCEL_LN, true);
				setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LSM6DSV_GYRO, true);
				setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LIS2MDL_MAG, true);
				setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_VBATT, true);
				
				setIsAlgorithmEnabled(GyroOnTheFlyCalModule.GENERAL_ALGORITHM_NAME, true);
			}

			mTrialName = DEFAULT_EXPERIMENT_NAME;
			
			mTrialNumberOfShimmers = 1;
			mTrialId = 0;
			
			setButtonStart(true);
			setShowErrorLedsRtc(true);
			setShowErrorLedsSd(true);
			
			setBluetoothBaudRate(9); //460800

			setInternalExpPower(false);

			setExGResolution(1);
			setShimmer2rMagRate(0);
			
			setMasterShimmer(false);
			setSingleTouch(false);
			setTCXO(false);
			setLowBattAutoStop(false);
			
			setPacketSize(0);
			mConfigByte0=0;
			mNChannels=0; 
			mBufferSize=0;
			mSyncBroadcastInterval = 0;
			setInitialTimeStampTicksSd(0);
			
			setShimmerAndSensorsSamplingRate(51.2);
			
			setLSM303MagRange(getMagRange());
			setAccelRange(getAccelRange());
			setGyroRange(getGyroRange());
			
			syncNodesList.clear();
		}
	}
	
	private void setDefaultShimmerName(){
		mShimmerUserAssignedName = DEFAULT_SHIMMER_NAME;

		String macParsed = getMacIdParsed();
		if(macParsed.isEmpty()){
			return;
		}
		
		if(mShimmerUserAssignedName.length()>7) { // 12 char max minus "_XXXX"
			mShimmerUserAssignedName = mShimmerUserAssignedName.substring(0, 7);
		}
		mShimmerUserAssignedName += "_" + macParsed; 
		return;
	}
	
	/**
	 * Parse the Shimmer's Information Memory when read through the Shimmer
	 * Dock/Consensys Base. The Information Memory is a region of the Shimmer's
	 * inbuilt RAM where all configuration information is stored.
	 * 
	 * @param configBytes
	 *            the array of InfoMem bytes.
	 */
	@Override
	public void configBytesParse(byte[] configBytes, COMMUNICATION_TYPE commType) {
		String shimmerName = "";

		mInfoMemBytesOriginal = configBytes;
		
		if(!ConfigByteLayout.checkConfigBytesValid(configBytes)){
			// InfoMem not valid
			setDefaultShimmerConfiguration();
			mShimmerUsingConfigFromInfoMem = false;

//			mShimmerInfoMemBytes = infoMemByteArrayGenerate();
//			mShimmerInfoMemBytes = new byte[infoMemContents.length];
			mConfigBytes = configBytes;
		}
		else {
			// configBytes are valid
			ConfigByteLayoutShimmer3 configByteLayoutCast = (ConfigByteLayoutShimmer3) mConfigByteLayout;
			mShimmerUsingConfigFromInfoMem = true;
			mConfigBytes = configBytes;
			createInfoMemLayoutObjectIfNeeded();
			
			// InfoMem D - Start - used by BtStream, SdLog and LogAndStream
			// Sampling Rate
			byte samplingRateMSB = (byte) (configBytes[configByteLayoutCast.idxShimmerSamplingRate+1] & configByteLayoutCast.maskShimmerSamplingRate);
			byte samplingRateLSB = (byte) (configBytes[configByteLayoutCast.idxShimmerSamplingRate] & configByteLayoutCast.maskShimmerSamplingRate);
			double samplingRate = convertSamplingRateBytesToFreq(samplingRateLSB, samplingRateMSB, getSamplingClockFreq());
			setShimmerAndSensorsSamplingRate(samplingRate);

			//Sampling rate is required in order to initialise the algorithms or else console errors are thrown.
			// Parse Enabled and Derived sensor bytes in order to update sensor maps
			parseEnabledDerivedSensorsForMaps(configByteLayoutCast, configBytes);
			
			mBufferSize = (int)(configBytes[configByteLayoutCast.idxBufferSize] & configByteLayoutCast.maskBufferSize);
			
			// Configuration
			setGSRRange((configBytes[configByteLayoutCast.idxConfigSetupByte3] >> configByteLayoutCast.bitShiftGSRRange) & configByteLayoutCast.maskGSRRange);
			mInternalExpPower = (configBytes[configByteLayoutCast.idxConfigSetupByte3] >> configByteLayoutCast.bitShiftEXPPowerEnable) & configByteLayoutCast.maskEXPPowerEnable;
			
			//EXG Configuration
			System.arraycopy(configBytes, configByteLayoutCast.idxEXGADS1292RChip1Config1, mEXG1RegisterArray, 0, 10);
			System.arraycopy(configBytes, configByteLayoutCast.idxEXGADS1292RChip2Config1, mEXG2RegisterArray, 0, 10);
			exgBytesGetConfigFrom(mEXG1RegisterArray, mEXG2RegisterArray);
			
			mBluetoothBaudRate = configBytes[configByteLayoutCast.idxBtCommBaudRate] & configByteLayoutCast.maskBaudRate;
			//TODO: hack below -> fix
//			if(!(mBluetoothBaudRate>=0 && mBluetoothBaudRate<=10)){
//				mBluetoothBaudRate = 0; 
//			}
			
			byte[] bufferCalibrationParameters = new byte[configByteLayoutCast.lengthGeneralCalibrationBytes];
			
			//TODO: decide what to do
			// BMP180 Pressure Calibration Parameters

			// InfoMem D - End

//			//SDLog and LogAndStream
//			if(((getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)||(getFirmwareIdentifier()==FW_ID.SDLOG))&&(mInfoMemBytes.length >=384)) {
				
				// Shimmer Name
				byte[] shimmerNameBuffer = new byte[configByteLayoutCast.lengthShimmerName];
				System.arraycopy(configBytes, configByteLayoutCast.idxSDShimmerName, shimmerNameBuffer, 0 , configByteLayoutCast.lengthShimmerName);
				for(byte b : shimmerNameBuffer) {
					if(!UtilShimmer.isAsciiPrintable((char)b)) {
						break;
					}
					shimmerName += (char)b;
				}
				
				// Experiment Name
				byte[] experimentNameBuffer = new byte[configByteLayoutCast.lengthExperimentName];
				System.arraycopy(configBytes, configByteLayoutCast.idxSDEXPIDName, experimentNameBuffer, 0 , configByteLayoutCast.lengthExperimentName);
				String experimentName = "";
				for(byte b : experimentNameBuffer) {
					if(!UtilShimmer.isAsciiPrintable((char)b)) {
						break;
					}
					experimentName += (char)b;
				}
				mTrialName = new String(experimentName);
	
				//Configuration Time
				int bitShift = (configByteLayoutCast.lengthConfigTimeBytes-1) * 8;
				mConfigTime = 0;
				for(int x=0; x<configByteLayoutCast.lengthConfigTimeBytes; x++ ) {
					mConfigTime += (((long)(configBytes[configByteLayoutCast.idxSDConfigTime0+x] & 0xFF)) << bitShift);
					bitShift -= 8;
				}
//				//TODO can be replaced by more efficient implementation
//				long value = 0;
//				for (int i = 0; i < by.length; i++){
//				   value = (value << 8) + (by[i] & 0xff);
//				}
//				//if ConfigTime is all F's, reset the time to 0 
//				if((mConfigTime&(2^32)) == (2^32)) {
//					mConfigTime = 0;
//				}

				if(isSupportedSdLogSync()) {
					mTrialId = configBytes[configByteLayoutCast.idxSDMyTrialID] & 0xFF;
					mTrialNumberOfShimmers = configBytes[configByteLayoutCast.idxSDNumOfShimmers] & 0xFF;
				}
				
				if(getFirmwareIdentifier()==FW_ID.SDLOG || getFirmwareIdentifier()==FW_ID.LOGANDSTREAM || getFirmwareIdentifier()==FW_ID.STROKARE) {
					mButtonStart = (configBytes[configByteLayoutCast.idxSDExperimentConfig0] >> configByteLayoutCast.bitShiftButtonStart) & configByteLayoutCast.maskButtonStart;
					mDisableBluetooth = (configBytes[configByteLayoutCast.idxSDExperimentConfig0] >> configByteLayoutCast.bitShiftDisableBluetooth) & configByteLayoutCast.maskDisableBluetooth;
					setShowErrorLedsRtc((configBytes[configByteLayoutCast.idxSDExperimentConfig0] >> configByteLayoutCast.bitShiftShowErrorLedsRwc) & configByteLayoutCast.maskShowErrorLedsRwc);
					setShowErrorLedsSd((configBytes[configByteLayoutCast.idxSDExperimentConfig0] >> configByteLayoutCast.bitShiftShowErrorLedsSd) & configByteLayoutCast.maskShowErrorLedsSd);
				}
				
				if(isSupportedSdLogSync()) {
					mSyncWhenLogging = (configBytes[configByteLayoutCast.idxSDExperimentConfig0] >> configByteLayoutCast.bitShiftTimeSyncWhenLogging) & configByteLayoutCast.maskTimeSyncWhenLogging;
					mMasterShimmer = (configBytes[configByteLayoutCast.idxSDExperimentConfig0] >> configByteLayoutCast.bitShiftMasterShimmer) & configByteLayoutCast.maskTimeMasterShimmer;
					mSingleTouch = (configBytes[configByteLayoutCast.idxSDExperimentConfig1] >> configByteLayoutCast.bitShiftSingleTouch) & configByteLayoutCast.maskTimeSingleTouch;
					
					mSyncBroadcastInterval = (int)(configBytes[configByteLayoutCast.idxSDBTInterval] & 0xFF);
					
					// Maximum and Estimated Length in minutes
					setTrialDurationEstimatedInSecs((int)(configBytes[configByteLayoutCast.idxEstimatedExpLengthLsb] & 0xFF) + (((int)(configBytes[configByteLayoutCast.idxEstimatedExpLengthMsb] & 0xFF)) << 8));
					setTrialDurationMaximumInSecs((int)(configBytes[configByteLayoutCast.idxMaxExpLengthLsb] & 0xFF) + (((int)(configBytes[configByteLayoutCast.idxMaxExpLengthMsb] & 0xFF)) << 8));
				}
				
				if(getFirmwareIdentifier()==FW_ID.SDLOG || getFirmwareIdentifier()==FW_ID.LOGANDSTREAM || getFirmwareIdentifier()==FW_ID.STROKARE) {
					mTCXO = (configBytes[configByteLayoutCast.idxSDExperimentConfig1] >> configByteLayoutCast.bitShiftTCX0) & configByteLayoutCast.maskTimeTCX0;
				}

				if(isVerCompatibleWithAnyOf(Configuration.Shimmer3.configOptionLowPowerAutoStop.mListOfCompatibleVersionInfo)) {
					setLowBattAutoStop((configBytes[configByteLayoutCast.idxSDExperimentConfig1] >> configByteLayoutCast.bitShiftLowBattStop) & configByteLayoutCast.maskLowBattStop);
				}
					
				byte[] macIdBytes = new byte[configByteLayoutCast.lengthMacIdBytes];
				System.arraycopy(configBytes, configByteLayoutCast.idxMacAddress, macIdBytes, 0 , configByteLayoutCast.lengthMacIdBytes);
				mMacIdFromInfoMem = UtilShimmer.bytesToHexString(macIdBytes);
				

				if(((configBytes[configByteLayoutCast.idxSDConfigDelayFlag]>>configByteLayoutCast.bitShiftSDCfgFileWriteFlag)&configByteLayoutCast.maskSDCfgFileWriteFlag) == configByteLayoutCast.maskSDCfgFileWriteFlag) {
					mConfigFileCreationFlag = true;
				}
				else {
					mConfigFileCreationFlag = false;
				}

				//Removed below because it was never fully used and has be removed from LogAndStream v0.6.5 onwards
//				if(((infoMemBytes[infoMemLayoutCast.idxSDConfigDelayFlag]>>infoMemLayoutCast.bitShiftSDCalibFileWriteFlag)&infoMemLayoutCast.maskSDCalibFileWriteFlag) == infoMemLayoutCast.maskSDCalibFileWriteFlag) {
//					mCalibFileCreationFlag = true;
//				}
//				else {
//					mCalibFileCreationFlag = false;
//				}

				// InfoMem C - End
					
				if(isSupportedSdLogSync()) {
					// InfoMem B Start -> Slave MAC ID for Multi-Shimmer Syncronisation
					syncNodesList.clear();
					for (int i = 0; i < configByteLayoutCast.maxNumOfExperimentNodes; i++) {
						System.arraycopy(configBytes, configByteLayoutCast.idxNode0 + (i*configByteLayoutCast.lengthMacIdBytes), macIdBytes, 0 , configByteLayoutCast.lengthMacIdBytes);
						if((Arrays.equals(macIdBytes, configByteLayoutCast.invalidMacId))||(Arrays.equals(macIdBytes, configByteLayoutCast.invalidMacId))) {
//						if(Arrays.equals(macIdBytes, new byte[]{-1,-1,-1,-1,-1,-1})) {
							break;
						}
						else {
							syncNodesList.add(UtilShimmer.bytesToHexString(macIdBytes));
						}
					}
					// InfoMem B End
				}
//			}
			
			// Configuration from each Sensor settings
			for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
				abstractSensor.configBytesParse(this, mConfigBytes, commType);
			}
			

//			prepareAllMapsAfterConfigRead();
		}
		
		checkAndCorrectShimmerName(shimmerName);
	}

	private void parseEnabledDerivedSensorsForMaps(ConfigByteLayoutShimmer3 infoMemLayoutCast, byte[] configBytes) {
		// Sensors
		mEnabledSensors = ((long)configBytes[infoMemLayoutCast.idxSensors0] & infoMemLayoutCast.maskSensors) << infoMemLayoutCast.byteShiftSensors0;
		mEnabledSensors += ((long)configBytes[infoMemLayoutCast.idxSensors1] & infoMemLayoutCast.maskSensors) << infoMemLayoutCast.byteShiftSensors1;
		mEnabledSensors += ((long)configBytes[infoMemLayoutCast.idxSensors2] & infoMemLayoutCast.maskSensors) << infoMemLayoutCast.byteShiftSensors2;

		if(mShimmerVerObject.isSupportedMpl()) {
			mEnabledSensors += ((long)configBytes[infoMemLayoutCast.idxSensors3] & 0xFF) << infoMemLayoutCast.bitShiftSensors3;
			mEnabledSensors += ((long)configBytes[infoMemLayoutCast.idxSensors4] & 0xFF) << infoMemLayoutCast.bitShiftSensors4;
		}

		mDerivedSensors = (long)0;
		// Check if compatible and not equal to 0xFF
		if((infoMemLayoutCast.idxDerivedSensors0>0) && (configBytes[infoMemLayoutCast.idxDerivedSensors0]!=(byte)infoMemLayoutCast.maskDerivedChannelsByte)
				&& (infoMemLayoutCast.idxDerivedSensors1>0) && (configBytes[infoMemLayoutCast.idxDerivedSensors1]!=(byte)infoMemLayoutCast.maskDerivedChannelsByte)) { 
			
			mDerivedSensors |= ((long)configBytes[infoMemLayoutCast.idxDerivedSensors0] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors0;
			mDerivedSensors |= ((long)configBytes[infoMemLayoutCast.idxDerivedSensors1] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors1;
			
			// Check if compatible and not equal to 0xFF
			// RM commented out the below check sept 2016 as infoMemBytes[infoMemLayoutCast.idxDerivedSensors2]  can be 0xFF if all 6DoF and 9DoF algorithms are enabled
			//if((infoMemLayoutCast.idxDerivedSensors2>0) && (infoMemBytes[infoMemLayoutCast.idxDerivedSensors2]!=(byte)infoMemLayoutCast.maskDerivedChannelsByte)){ 
			if (infoMemLayoutCast.idxDerivedSensors2 > 0) {
				mDerivedSensors |= ((long) configBytes[infoMemLayoutCast.idxDerivedSensors2] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors2;
			}

			if (mShimmerVerObject.isSupportedEightByteDerivedSensors()) {
				mDerivedSensors |= ((long) configBytes[infoMemLayoutCast.idxDerivedSensors3] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors3;
				mDerivedSensors |= ((long) configBytes[infoMemLayoutCast.idxDerivedSensors4] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors4;
				mDerivedSensors |= ((long) configBytes[infoMemLayoutCast.idxDerivedSensors5] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors5;
				mDerivedSensors |= ((long) configBytes[infoMemLayoutCast.idxDerivedSensors6] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors6;
				mDerivedSensors |= ((long) configBytes[infoMemLayoutCast.idxDerivedSensors7] & infoMemLayoutCast.maskDerivedChannelsByte) << infoMemLayoutCast.byteShiftDerivedSensors7;
			}
		}

		setEnabledAndDerivedSensorsAndUpdateMaps(mEnabledSensors, mDerivedSensors);
	}


	/**
	 * Generate the Shimmer's Information Memory byte array based on the
	 * settings stored in ShimmerObject. These bytes can then be written to the
	 * Shimmer via the Shimmer Dock/Consensys Base. The Information Memory is is
	 * a region of the Shimmer's inbuilt RAM where all configuration information
	 * is stored.
	 * 
	 * @param generateForWritingToShimmer
	 * @return
	 */
	@Override
	public byte[] configBytesGenerate(boolean generateForWritingToShimmer, COMMUNICATION_TYPE commType) {
		ConfigByteLayoutShimmer3 configByteLayoutCast = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), HW_ID.SHIMMER_3);
		
		if (mShimmerVerObject.mHardwareVersion==HW_ID.UNKNOWN) {
			
		} else {
			configByteLayoutCast = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), mShimmerVerObject.mHardwareVersion);
		}
		
		byte[] configByteBackup = mConfigBytes.clone();
		
		// InfoMem defaults to 0xFF on firmware flash
		mConfigBytes = configByteLayoutCast.createConfigByteArrayEmpty(mConfigBytes.length);
//		mInfoMemBytes = infoMemLayout.createEmptyInfoMemByteArray();
		
		if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
		
			// If not being generated from scratch then copy across existing InfoMem contents
			if(!generateForWritingToShimmer) {
				System.arraycopy(configByteBackup, 0, mConfigBytes, 0, (configByteBackup.length > mConfigBytes.length) ? mConfigBytes.length:configByteBackup.length);
			}	
			
			// InfoMem D - Start - used by BtStream, SdLog and LogAndStream
			// Sampling Rate
			byte[] samplingRateBytes = convertSamplingRateFreqToBytes(getSamplingRateShimmer(), getSamplingClockFreq());
			mConfigBytes[configByteLayoutCast.idxShimmerSamplingRate] = samplingRateBytes[0]; 
			mConfigBytes[configByteLayoutCast.idxShimmerSamplingRate+1] = samplingRateBytes[1]; 
	
			//FW not using this feature and BtStream will reject infomem if this isn't set to '1'
			mConfigBytes[configByteLayoutCast.idxBufferSize] = (byte) 1;//(byte) (mBufferSize & mInfoMemLayout.maskBufferSize); 
			
			// Sensors
			//JC: The updateEnabledSensorsFromExgResolution(), seems to be working incorrectly because of the boolean values of mIsExg1_24bitEnabled, so updating this values first 
			checkExgResolutionFromEnabledSensorsVar();
			refreshEnabledSensorsFromSensorMap();
			mConfigBytes[configByteLayoutCast.idxSensors0] = (byte) ((mEnabledSensors >> configByteLayoutCast.byteShiftSensors0) & configByteLayoutCast.maskSensors);
			mConfigBytes[configByteLayoutCast.idxSensors1] = (byte) ((mEnabledSensors >> configByteLayoutCast.byteShiftSensors1) & configByteLayoutCast.maskSensors);
			mConfigBytes[configByteLayoutCast.idxSensors2] = (byte) ((mEnabledSensors >> configByteLayoutCast.byteShiftSensors2) & configByteLayoutCast.maskSensors);
			
			// Configuration
			mConfigBytes[configByteLayoutCast.idxConfigSetupByte0] = (byte) (0x00);
			mConfigBytes[configByteLayoutCast.idxConfigSetupByte1] = (byte) (0x00);
			mConfigBytes[configByteLayoutCast.idxConfigSetupByte2] = (byte) (0x00);
			mConfigBytes[configByteLayoutCast.idxConfigSetupByte3] = (byte) (0x00);
			
			mConfigBytes[configByteLayoutCast.idxConfigSetupByte3] |= (byte) ((getGSRRange() & configByteLayoutCast.maskGSRRange) << configByteLayoutCast.bitShiftGSRRange);
			checkIfInternalExpBrdPowerIsNeeded();
			mConfigBytes[configByteLayoutCast.idxConfigSetupByte3] |= (byte) ((mInternalExpPower & configByteLayoutCast.maskEXPPowerEnable) << configByteLayoutCast.bitShiftEXPPowerEnable);
			
			//EXG Configuration
			exgBytesGetFromConfig(); //update mEXG1Register and mEXG2Register
			System.arraycopy(mEXG1RegisterArray, 0, mConfigBytes, configByteLayoutCast.idxEXGADS1292RChip1Config1, 10);
			System.arraycopy(mEXG2RegisterArray, 0, mConfigBytes, configByteLayoutCast.idxEXGADS1292RChip2Config1, 10);
			
			mConfigBytes[configByteLayoutCast.idxBtCommBaudRate] = (byte) (mBluetoothBaudRate & configByteLayoutCast.maskBaudRate);
	
			//TODO: decide what to do
			// BMP180 Pressure Calibration Parameters
	
			// Derived Sensors
			if((configByteLayoutCast.idxDerivedSensors0>0)&&(configByteLayoutCast.idxDerivedSensors1>0)) { // Check if compatible
				mConfigBytes[configByteLayoutCast.idxDerivedSensors0] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors0) & configByteLayoutCast.maskDerivedChannelsByte);
				mConfigBytes[configByteLayoutCast.idxDerivedSensors1] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors1) & configByteLayoutCast.maskDerivedChannelsByte);
				if(configByteLayoutCast.idxDerivedSensors2>0) { // Check if compatible
					mConfigBytes[configByteLayoutCast.idxDerivedSensors2] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors2) & configByteLayoutCast.maskDerivedChannelsByte);
				}
				
				if (mShimmerVerObject.isSupportedEightByteDerivedSensors()) {
					mConfigBytes[configByteLayoutCast.idxDerivedSensors3] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors3) & configByteLayoutCast.maskDerivedChannelsByte);
					mConfigBytes[configByteLayoutCast.idxDerivedSensors4] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors4) & configByteLayoutCast.maskDerivedChannelsByte);
					mConfigBytes[configByteLayoutCast.idxDerivedSensors5] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors5) & configByteLayoutCast.maskDerivedChannelsByte);
					mConfigBytes[configByteLayoutCast.idxDerivedSensors6] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors6) & configByteLayoutCast.maskDerivedChannelsByte);
					mConfigBytes[configByteLayoutCast.idxDerivedSensors7] = (byte) ((mDerivedSensors >> configByteLayoutCast.byteShiftDerivedSensors7) & configByteLayoutCast.maskDerivedChannelsByte);
				}
			}
			
			// InfoMem D - End
	
			
			//TODO: Add full FW version checking here to support future changes to FW
			//SDLog and LogAndStream
	//		if(((getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)||(getFirmwareIdentifier()==FW_ID.SDLOG))&&(mInfoMemBytes.length >=384)) {
	
				// Shimmer Name
				for (int i = 0; i < configByteLayoutCast.lengthShimmerName; i++) {
					if (i < mShimmerUserAssignedName.length()) {
						mConfigBytes[configByteLayoutCast.idxSDShimmerName + i] = (byte) mShimmerUserAssignedName.charAt(i);
					}
					else {
						mConfigBytes[configByteLayoutCast.idxSDShimmerName + i] = (byte) 0xFF;
					}
				}
				
				// Experiment Name
				for (int i = 0; i < configByteLayoutCast.lengthExperimentName; i++) {
					if (i < mTrialName.length()) {
						mConfigBytes[configByteLayoutCast.idxSDEXPIDName + i] = (byte) mTrialName.charAt(i);
					}
					else {
						mConfigBytes[configByteLayoutCast.idxSDEXPIDName + i] = (byte) 0xFF;
					}
				}
	
				//Configuration Time
				mConfigBytes[configByteLayoutCast.idxSDConfigTime0] = (byte) ((mConfigTime >> configByteLayoutCast.bitShiftSDConfigTime0) & 0xFF);
				mConfigBytes[configByteLayoutCast.idxSDConfigTime1] = (byte) ((mConfigTime >> configByteLayoutCast.bitShiftSDConfigTime1) & 0xFF);
				mConfigBytes[configByteLayoutCast.idxSDConfigTime2] = (byte) ((mConfigTime >> configByteLayoutCast.bitShiftSDConfigTime2) & 0xFF);
				mConfigBytes[configByteLayoutCast.idxSDConfigTime3] = (byte) ((mConfigTime >> configByteLayoutCast.bitShiftSDConfigTime3) & 0xFF);
				
				if(isSupportedSdLogSync()) {
					mConfigBytes[configByteLayoutCast.idxSDMyTrialID] = (byte) (mTrialId & 0xFF);
		
					mConfigBytes[configByteLayoutCast.idxSDNumOfShimmers] = (byte) (mTrialNumberOfShimmers & 0xFF);
				}
				
				if(getFirmwareIdentifier()==FW_ID.SDLOG || getFirmwareIdentifier()==FW_ID.LOGANDSTREAM || getFirmwareIdentifier()==FW_ID.STROKARE) {
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] = (byte) ((mButtonStart & configByteLayoutCast.maskButtonStart) << configByteLayoutCast.bitShiftButtonStart);
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((mDisableBluetooth & configByteLayoutCast.maskDisableBluetooth) << configByteLayoutCast.bitShiftDisableBluetooth);
					if(this.isOverrideShowErrorLedsRtc){
						mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((configByteLayoutCast.maskShowErrorLedsRwc) << configByteLayoutCast.bitShiftShowErrorLedsRwc);
					}
					else {
						mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((mShowErrorLedsRtc & configByteLayoutCast.maskShowErrorLedsRwc) << configByteLayoutCast.bitShiftShowErrorLedsRwc);
					}
					if(this.isOverrideShowErrorLedsSd){
						mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((configByteLayoutCast.maskShowErrorLedsSd) << configByteLayoutCast.bitShiftShowErrorLedsSd);
					}
					else {
						mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((mShowErrorLedsSd & configByteLayoutCast.maskShowErrorLedsSd) << configByteLayoutCast.bitShiftShowErrorLedsSd);
					}
				}
				
				mConfigBytes[configByteLayoutCast.idxSDExperimentConfig1] = 0;
				
				if(isSupportedSdLogSync()) {
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((mSyncWhenLogging & configByteLayoutCast.maskTimeSyncWhenLogging) << configByteLayoutCast.bitShiftTimeSyncWhenLogging);
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig0] |= (byte) ((mMasterShimmer & configByteLayoutCast.maskTimeMasterShimmer) << configByteLayoutCast.bitShiftMasterShimmer);
					
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig1] |= (byte) ((mSingleTouch & configByteLayoutCast.maskTimeSingleTouch) << configByteLayoutCast.bitShiftSingleTouch);
				
					mConfigBytes[configByteLayoutCast.idxSDBTInterval] = (byte) (mSyncBroadcastInterval & 0xFF);
				
					// Maximum and Estimated Length in minutes
					mConfigBytes[configByteLayoutCast.idxEstimatedExpLengthLsb] = (byte) ((getTrialDurationEstimatedInSecs() >> 0) & 0xFF);
					mConfigBytes[configByteLayoutCast.idxEstimatedExpLengthMsb] = (byte) ((getTrialDurationEstimatedInSecs() >> 8) & 0xFF);
					mConfigBytes[configByteLayoutCast.idxMaxExpLengthLsb] = (byte) ((getTrialDurationMaximumInSecs() >> 0) & 0xFF);
					mConfigBytes[configByteLayoutCast.idxMaxExpLengthMsb] = (byte) ((getTrialDurationMaximumInSecs() >> 8) & 0xFF);
				}

				if(getFirmwareIdentifier()==FW_ID.SDLOG || getFirmwareIdentifier()==FW_ID.LOGANDSTREAM || getFirmwareIdentifier()==FW_ID.STROKARE) {
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig1] |= (byte) ((mTCXO & configByteLayoutCast.maskTimeTCX0) << configByteLayoutCast.bitShiftTCX0);
				}
				
				if(isVerCompatibleWithAnyOf(Configuration.Shimmer3.configOptionLowPowerAutoStop.mListOfCompatibleVersionInfo)) {
					mConfigBytes[configByteLayoutCast.idxSDExperimentConfig1] |= (byte) ((isLowBattAutoStop()?1:0 & configByteLayoutCast.maskLowBattStop) << configByteLayoutCast.bitShiftLowBattStop);
				}

				if(getFirmwareIdentifier()==FW_ID.LOGANDSTREAM || getFirmwareIdentifier()==FW_ID.SDLOG || getFirmwareIdentifier()==FW_ID.STROKARE) {
					if(generateForWritingToShimmer) {
						// MAC address - set to all 0xFF (i.e. invalid MAC) so that Firmware will know to check for MAC from Bluetooth transceiver
						// (already set to 0xFF at start of method but just incase)
						System.arraycopy(configByteLayoutCast.invalidMacId, 0, mConfigBytes, configByteLayoutCast.idxMacAddress, configByteLayoutCast.lengthMacIdBytes);
		
						mConfigBytes[configByteLayoutCast.idxSDConfigDelayFlag] = 0;
						// Tells the Shimmer to create a new config file on undock/power cycle
						//TODO RM enabled the two lines below (MN had the below two lines commented out.. but need them to write config successfully over UART)
						byte configFileWriteBit = (byte) (mConfigFileCreationFlag? (configByteLayoutCast.maskSDCfgFileWriteFlag << configByteLayoutCast.bitShiftSDCfgFileWriteFlag):0x00);
						mConfigBytes[configByteLayoutCast.idxSDConfigDelayFlag] |= configFileWriteBit;
	
						mConfigBytes[configByteLayoutCast.idxSDConfigDelayFlag] |= configByteLayoutCast.bitShiftSDCfgFileWriteFlag;
						
						//Removed below because it was never fully used and has be removed from LogAndStream v0.6.5 onwards
//						 // Tells the Shimmer to create a new calibration files on undock/power cycle
//						byte calibFileWriteBit = (byte) (mCalibFileCreationFlag? (infoMemLayout.maskSDCalibFileWriteFlag << infoMemLayout.bitShiftSDCalibFileWriteFlag):0x00);
//						mInfoMemBytes[infoMemLayout.idxSDConfigDelayFlag] |= calibFileWriteBit;
					}
				}
				// InfoMem C - End
					
				if(isSupportedSdLogSync()) {
					// InfoMem B Start -> Slave MAC ID for Multi-Shimmer Syncronisation
					for (int i = 0; i < configByteLayoutCast.maxNumOfExperimentNodes; i++) { // Limit of 21 nodes
						byte[] macIdArray;
						if((syncNodesList.size()>0) && (i<syncNodesList.size()) && (mSyncWhenLogging>0)) {
							macIdArray = UtilShimmer.hexStringToByteArray(syncNodesList.get(i));
						}
						else {
							macIdArray = configByteLayoutCast.invalidMacId;
						}
						System.arraycopy(macIdArray, 0, mConfigBytes, configByteLayoutCast.idxNode0 + (i*configByteLayoutCast.lengthMacIdBytes), configByteLayoutCast.lengthMacIdBytes);
					}
					// InfoMem B End
				}

//			}
			
			// Configuration from each Sensor settings
			for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()){
				abstractSensor.configBytesGenerate(this, mConfigBytes, commType);
			}
		}
		
		//TODO remove the need for this temp hack by adding back in the ability to change individual sensor sampling rates
    	if(getFirmwareIdentifier()==FW_ID.STROKARE){
    		//override rates
    		//getLSM303DigitalAccelRate()
    		mConfigBytes[configByteLayoutCast.idxConfigSetupByte0] &= ~((byte) (configByteLayoutCast.maskLSM303DLHCAccelSamplingRate << configByteLayoutCast.bitShiftLSM303DLHCAccelSamplingRate));
    		mConfigBytes[configByteLayoutCast.idxConfigSetupByte0] |= (byte) ((5 & configByteLayoutCast.maskLSM303DLHCAccelSamplingRate) << configByteLayoutCast.bitShiftLSM303DLHCAccelSamplingRate);
    		//getMPU9X50GyroAccelRate()
    		mConfigBytes[configByteLayoutCast.idxConfigSetupByte1] &= ~((byte) (configByteLayoutCast.maskMPU9150AccelGyroSamplingRate << configByteLayoutCast.bitShiftMPU9150AccelGyroSamplingRate));
    		mConfigBytes[configByteLayoutCast.idxConfigSetupByte1] |= (byte) ((155 & configByteLayoutCast.maskMPU9150AccelGyroSamplingRate) << configByteLayoutCast.bitShiftMPU9150AccelGyroSamplingRate);
    		//getLSM303MagRate()
    		mConfigBytes[configByteLayoutCast.idxConfigSetupByte2] &= ~((byte) (configByteLayoutCast.maskLSM303DLHCMagSamplingRate << configByteLayoutCast.bitShiftLSM303DLHCMagSamplingRate));
    		mConfigBytes[configByteLayoutCast.idxConfigSetupByte2] |= (byte) ((6 & configByteLayoutCast.maskLSM303DLHCMagSamplingRate) << configByteLayoutCast.bitShiftLSM303DLHCMagSamplingRate);
    	}
		
		return mConfigBytes;
	}
	
//	@Deprecated
//	public void setCalibFileCreationFlag(boolean state) {
//		mCalibFileCreationFlag = state;
//	}

	public void setConfigFileCreationFlag(boolean state) {
		mConfigFileCreationFlag = state;
	}
	
	@Override
	public void handleSpecCasesUpdateEnabledSensors() {
		updateEnabledSensorsFromExgResolution();
	}
	
	/**
	 * This method is used as the basis for configuring a Shimmer through the
	 * Consensys application. All sensor and configuration information is stored
	 * in dynamically created and controlled Maps.
	 * 
	 * Should only be used after the Shimmer HW and FW version information is
	 * set
	 */
	@Override
	public void sensorAndConfigMapsCreate() {
		createMapOfSensorClasses();

		super.sensorAndConfigMapsCreateCommon();
	}
	
	private void updateSensorMapChannelsFromChannelMap(LinkedHashMap<Integer, SensorDetails> sensorMap) {
		//Update ChannelDetails in the mSensorMap
		Iterator<SensorDetails> iterator = sensorMap.values().iterator();
		while(iterator.hasNext()){
			SensorDetails sensorDetails = iterator.next();
			for(String channelMapKey:sensorDetails.mSensorDetailsRef.mListOfChannelsRef){
				ChannelDetails channelDetails = mChannelMap.get(channelMapKey);
				if(channelDetails!=null){
					sensorDetails.mListOfChannels.add(channelDetails);
				}
			}
		}
	}

	//TODO decide whether to match Shimmer4 approach by storing classes only in the map or use mSensorBMPX80 
	private void createMapOfSensorClasses() {
		mMapOfSensorClasses = new LinkedHashMap<SENSORS, AbstractSensor>();

		if(isShimmerGen2()){
			mSensorMMA736x = new SensorMMA736x(this);
			addSensorClass(mSensorMMA736x);
			
			mSensorShimmer2Mag = new SensorShimmer2Mag(this);
			addSensorClass(mSensorShimmer2Mag);
			
			mSensorShimmer2Gyro = new SensorShimmer2Gyro(this);
			addSensorClass(mSensorShimmer2Gyro);
		} else if(isShimmerGenGq()){
//			addSensorClass(SENSORS.CLOCK, new ShimmerClock(this));
			addSensorClass(SENSORS.GSR, new SensorGSR(mShimmerVerObject));
			addSensorClass(SENSORS.ECG_TO_HR, new SensorECGToHRFw(mShimmerVerObject));
//			addSensorClass(SENSORS.EXG, new SensorEXG(this));
		} else if(isShimmerGen3()){
			if(isSupportedNoImuSensors()){
				
			}
			else if(isSupportedNewImuSensors()){
				mSensorBMPX80 = new SensorBMP280(this);
				addSensorClass(mSensorBMPX80);
				
				mSensorLSM303 = new SensorLSM303AH(this);
				addSensorClass(mSensorLSM303);

				mSensorKionixAccel = new SensorKionixKXTC92050(this);
				addSensorClass(mSensorKionixAccel);

				mSensorMpu9x50 = new SensorMPU9250(this);
				addSensorClass(mSensorMpu9x50);
				  
			
			}
			
			else{
				mSensorBMPX80 = new SensorBMP180(this);
				addSensorClass(mSensorBMPX80);
				
				mSensorLSM303 = new SensorLSM303DLHC(this);
				addSensorClass(mSensorLSM303);

				mSensorKionixAccel = new SensorKionixKXRB52042(this);
				addSensorClass(mSensorKionixAccel);

				mSensorMpu9x50 = new SensorMPU9150(this);
				addSensorClass(mSensorMpu9x50);
				
			}
			
		} else if(isShimmerGen3R()){
			mSensorBMPX80 = new SensorBMP390(this);
			addSensorClass(mSensorBMPX80);
			
			mSensorLSM6DSV = new SensorLSM6DSV(this);
			addSensorClass(mSensorLSM6DSV);

			if(isShimmer3RwithHighGAccelSupport()){
				mSensorADXL371 = new SensorADXL371(this);
				addSensorClass(mSensorADXL371);
			}
      
			mSensorLIS2DW12 = new SensorLIS2DW12(this);
			addSensorClass(mSensorLIS2DW12);
			
			if(isShimmer3RwithAltMagSupport()){
				mSensorLIS3MDL = new SensorLIS3MDL(this);
				addSensorClass(mSensorLIS3MDL);
			}
			
			mSensorLIS2MDL = new SensorLIS2MDL(this);
			addSensorClass(mSensorLIS2MDL);
		}
	}

	@Override
	protected void handleSpecialCasesAfterSensorMapCreate() {
		super.handleSpecialCasesAfterSensorMapCreate();
		
		//Old approach pre-Shimmer4 code framework with sensor classes
		if (getHardwareVersion()!=-1){
			if (isShimmerGen2()){
				Map<Integer,SensorDetailsRef> sensorMapRef = Configuration.Shimmer2.mSensorMapRef;
				for(Integer key:sensorMapRef.keySet()){
					mSensorMap.put(key, new SensorDetails(false, 0, sensorMapRef.get(key)));
				}
				updateSensorMapChannelsFromChannelMap(mSensorMap);
			} 
			else if (isShimmerGen3() || isShimmerGen3R()//3R SUPPORT TEMP JA-116
					|| isShimmerGenGq()) { // need isShimmerGenGq() here for parsing GQ data through ShimmerSDLog
				if(isShimmerGen3R()) {
					mChannelMap.putAll(Configuration.Shimmer3.mChannelMapRef3r);
				}else {
					mChannelMap.putAll(Configuration.Shimmer3.mChannelMapRef);
				}

				//Hack for GSR parsing in GQ from SD files (ideally GQ SD parsing should be done in ShimmerGQ class)
				if(isShimmerGenGq()){
					mChannelMap.remove(SensorGSR.ObjectClusterSensorName.GSR_RESISTANCE);
					mChannelMap.remove(SensorGSR.ObjectClusterSensorName.GSR_ADC_VALUE);
					mChannelMap.remove(SensorGSR.ObjectClusterSensorName.GSR_CONDUCTANCE);
					mChannelMap.remove(SensorGSR.ObjectClusterSensorName.GSR_RANGE);

					mChannelMap.put(SensorGSR.channelGsrMicroSiemensGq.mObjectClusterName, SensorGSR.channelGsrMicroSiemensGq);
					
					mChannelMap.remove(SensorEXG.ObjectClusterSensorName.ECG_LA_RA_24BIT);
					mChannelMap.putAll(SensorEXG.mChannelMapRefGq);
				}
				
				if(getFirmwareVersionCode()>=6){
					mChannelMap.remove(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP);
					mChannelMap.put(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP, SensorShimmerClock.channelShimmerClock3byte);
				}
				
				if(isShimmerGen3() || isShimmerGen3R()){ //3R SUPPORT TEMP JA-116
					mSensorMap.putAll(createSensorMapShimmer3());
				}
				else if(isShimmerGenGq()){
					LinkedHashMap<Integer, SensorDetails> sensorMap = new LinkedHashMap<Integer, SensorDetails>();
					sensorMap.put(Configuration.Shimmer3.SENSOR_ID.SHIMMER_TIMESTAMP, new SensorDetails(false, 0, SensorShimmerClock.sensorShimmerClock));
					sensorMap.put(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, new SensorDetails(false, 0, SensorEXG.sDRefEcgGq));
					updateSensorMapChannelsFromChannelMap(sensorMap);
					mSensorMap.putAll(sensorMap);
				}

				createSensorGroupMapShimmer3();

				//TODO Hack - will eventually be handled in the Sensor classes as done in createMapOfSensorClasses()
				//External expansion ADC are supported in the Shimmer ECGmd but are not made available
				if(getExpansionBoardId()==HW_ID_SR_CODES.SHIMMER_ECG_MD){
					mSensorGroupingMap.remove(Configuration.Shimmer3.LABEL_SENSOR_TILE.EXTERNAL_EXPANSION_ADC.ordinal());
				}

				createConfigOptionMapShimmer3();
				
			}
			else if (getHardwareVersion() == HW_ID.SHIMMER_GQ_BLE) {
				
				Map<Integer,SensorDetailsRef> sensorMapRef = Configuration.ShimmerGqBle.mSensorMapRef;
				for(Integer key:sensorMapRef.keySet()){
					mSensorMap.put(key, new SensorDetails(false, 0, sensorMapRef.get(key)));
				}

				mSensorGroupingMap.putAll(Configuration.ShimmerGqBle.mSensorGroupingMapRef);
				mConfigOptionsMapSensors.putAll(Configuration.ShimmerGqBle.mConfigOptionsMapRef);
				
				updateSensorMapChannelsFromChannelMap(mSensorMap);
			}
		}

		SensorEXG.updateSensorMapForExgResolution(this, getExGResolution());
	}

	@Override
	public void generateParserMap() {
		SensorEXG.updateSensorMapForExgResolution(this, getExGResolution());
		super.generateParserMap();
	}
	
	private LinkedHashMap<Integer, SensorDetails> createSensorMapShimmer3(){
		LinkedHashMap<Integer, SensorDetails> sensorMap = new LinkedHashMap<Integer, SensorDetails>();
		
//		createInfoMemLayoutObjectIfNeeded();
		ConfigByteLayout infoMemLayout = getConfigByteLayout();
		if(infoMemLayout!=null){
			Map<Integer,SensorDetailsRef> sensorMapRef;
			if(isShimmerGen3R()) {
				sensorMapRef = Configuration.Shimmer3.mSensorMapRef3r;
			}else {
				sensorMapRef = Configuration.Shimmer3.mSensorMapRef;
			}

			for(Integer key:sensorMapRef.keySet()){
				
				//Special cases for derived sensor bitmap ID
				int derivedChannelBitmapID = 0;
				if(infoMemLayout instanceof ConfigByteLayoutShimmer3){
					ConfigByteLayoutShimmer3 infoMemLayoutCast = (ConfigByteLayoutShimmer3) infoMemLayout;
					
					if(key==Configuration.Shimmer3.SENSOR_ID.SHIMMER_RESISTANCE_AMP){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelResAmp;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_SKIN_TEMPERATURE_PROBE){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelSkinTemp;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A12){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelPpg_ADC12ADC13;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A13){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelPpg_ADC12ADC13;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A12){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelPpg1_ADC12ADC13;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A13){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelPpg1_ADC12ADC13;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A1){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelPpg2_ADC1ADC14;
					}
					else if(key==Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A14){
						derivedChannelBitmapID = infoMemLayoutCast.maskDerivedChannelPpg2_ADC1ADC14;
					}
				}
				
				SensorDetails sensorDetails = new SensorDetails(false, derivedChannelBitmapID, sensorMapRef.get(key));
				sensorMap.put(key, sensorDetails);
			}
			
		}
		
		updateSensorMapChannelsFromChannelMap(sensorMap);

		return sensorMap;
	}
	
	private void createSensorGroupMapShimmer3() {
		Map<Integer, SensorGroupingDetails> groupMapRef;
		if(isShimmerGen3R()) {
			groupMapRef = Configuration.Shimmer3.mSensorGroupingMapRef3r;
		}else {
			groupMapRef = Configuration.Shimmer3.mSensorGroupingMapRef;
		}

		loadCompatibleSensorGroupEntries(groupMapRef);
	}

	private void createConfigOptionMapShimmer3() {
		generateConfigOptionsMap();
		Map<String, ConfigOptionDetailsSensor> configOptionsMapRef;
		if(isShimmerGen3R()) {
			configOptionsMapRef = Configuration.Shimmer3.mConfigOptionsMapRef3r;
		}else {
			configOptionsMapRef = Configuration.Shimmer3.mConfigOptionsMapRef;
		}
		loadCompatibleConfigOptionGroupEntries(configOptionsMapRef);
	}

	
	//TODO 2016-05-18 feed below into sensor map classes
	@Override
	public void checkConfigOptionValues(String stringKey) {
		if(mConfigOptionsMapSensors!=null){
			ConfigOptionDetails configOptions = mConfigOptionsMapSensors.get(stringKey);
			if(configOptions!=null){
				if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R ){
					int nonStandardIndex = -1;
					//Taking out as now handled in the Sensor class
//			        if(stringKey.equals(SensorLSM303.GuiLabelConfig.LSM303_ACCEL_RATE)) {
//			        	if(isLSM303DigitalAccelLPM()) {
//							nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.LSM303_ACCEL_RATE.IS_LPM;
//			        	}
//			        	else {
//							nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.LSM303_ACCEL_RATE.NOT_LPM;
//		        			// double check that rate is compatible with LPM (8 not compatible so set to higher rate) 
//			        		setLSM303DigitalAccelRate(getLSM303DigitalAccelRate());
//			        	}
//			        	
//						if(nonStandardIndex!=-1 && configOptions instanceof ConfigOptionDetailsSensor){
//							((ConfigOptionDetailsSensor) configOptions).setIndexOfValuesToUse(nonStandardIndex);
//						}
//
//			        }
			        if(stringKey.equals(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE)) {
			        	checkWhichExgRespPhaseValuesToUse();
			        }
			        else if(stringKey.equals(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE)) {
			        	checkWhichExgRefElectrodeValuesToUse();
			        }
				}
				else if(getHardwareVersion()==HW_ID.SHIMMER_GQ_BLE){
				}
			}
		}
	}
	
	@Override
	public void handleSpecCasesBeforeSensorMapUpdateGeneral() {
		checkExgResolutionFromEnabledSensorsVar();
	}
	
	@Override
	public boolean handleSpecCasesBeforeSensorMapUpdatePerSensor(Integer sensorId) {
		// Skip if ExG channels here -> handle them after for loop.
		if(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_ECG
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EMG
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR) {
			mSensorMap.get(sensorId).setIsEnabled(false);
			return true;
		}
		// Handle derived sensors based on int adc channels (e.g. PPG vs. A12/A13)
		else if(((sensorId==Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A12)
			||(sensorId==Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A13)
			||(sensorId==Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A1)
			||(sensorId==Configuration.Shimmer3.SENSOR_ID.SHIMMER_INT_EXP_ADC_A14))){

			//Check if a derived channel is enabled, if it is ignore disable and skip 
			innerloop:
				if (mSensorMap.get(sensorId).mSensorDetailsRef.mListOfSensorIdsConflicting!=null){
					for(Integer conflictKey:mSensorMap.get(sensorId).mSensorDetailsRef.mListOfSensorIdsConflicting) {
						if(mSensorMap.get(conflictKey)!=null && mSensorMap.get(conflictKey).isDerivedChannel()) {
							if((mDerivedSensors&mSensorMap.get(conflictKey).mDerivedSensorBitmapID) == mSensorMap.get(conflictKey).mDerivedSensorBitmapID) {
								mSensorMap.get(sensorId).setIsEnabled(false);
								return true;
							}
						}
					}
				} else {
					System.out.println("2r:null");
				}
		}
//		else if(sensorId == Configuration.Shimmer3.SENSOR_ID.TIMESTAMP_SYNC 
////				|| sensorId == Configuration.Shimmer3.SENSOR_ID.TIMESTAMP
////				|| sensorId == Configuration.Shimmer3.SENSOR_ID.REAL_TIME_CLOCK
//				|| sensorId == Configuration.Shimmer3.SENSOR_ID.REAL_TIME_CLOCK_SYNC){
//			mSensorMap.get(sensorId).setIsEnabled(false);
//			skipKey = true;
//		}
		else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_SHIMMER_STREAMING_PROPERTIES){
			mSensorMap.get(sensorId).setIsEnabled(true);
			return true;
		}
		return false;
	}
	
	@Override
	public void handleSpecCasesAfterSensorMapUpdateFromEnabledSensors() {
		// Now that all main sensor channels have been parsed, deal with
		// sensor channels that have special conditions. E.g. deciding
		// what type of signal the ExG is configured for or what derived
		// channel is enabled like whether PPG is on ADC12 or ADC13
		
		//Handle ExG sensors
		checkExgResolutionFromEnabledSensorsVar();
		internalCheckExgModeAndUpdateSensorMap();

		// Handle PPG sensors so that it appears in Consensys as a
		// single PPG channel with a selectable ADC based on different
		// hardware versions.
		
		//Used for Shimmer GSR hardware
		if (mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A12)!=null){
			if((mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A12).isEnabled())||(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A13).isEnabled())) {
				mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY).setIsEnabled(true);
				if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A12).isEnabled()) {
					mPpgAdcSelectionGsrBoard = SensorPPG.ListOfPpgAdcSelectionConfigValues[1]; // PPG_A12
				}
				else if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A13).isEnabled()) {
					mPpgAdcSelectionGsrBoard = SensorPPG.ListOfPpgAdcSelectionConfigValues[0]; // PPG_A13
	
				}
			}
			else {
				if (mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY)!=null){
					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY).setIsEnabled(false);
				} else {
					System.out.println("2r:null");
				}
	
			}
		}
		//Used for Shimmer Proto3 Deluxe hardware
		if (mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A12)!=null){
			if((mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A12).isEnabled())||(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A13).isEnabled())) {
				mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY).setIsEnabled(true);
				if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A12).isEnabled()) {
					mPpg1AdcSelectionProto3DeluxeBoard = SensorPPG.ListOfPpg1AdcSelectionConfigValues[1]; // PPG1_A12
				}
				else if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A13).isEnabled()) {
					mPpg1AdcSelectionProto3DeluxeBoard = SensorPPG.ListOfPpg1AdcSelectionConfigValues[0]; // PPG1_A13
				}
			}
			else {
				if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY)!=null){
					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY).setIsEnabled(false);
				} else {
					System.out.println("2r:null");
				}
			}
		}
		//Used for Shimmer Proto3 Deluxe hardware
		if (mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A1)!=null){
			if((mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A1).isEnabled())||(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A14).isEnabled())) {
				mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY).setIsEnabled(true);
				if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A1).isEnabled()) {
					mPpg2AdcSelectionProto3DeluxeBoard = SensorPPG.ListOfPpg2AdcSelectionConfigValues[0]; // PPG2_A1
				}
				else if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A14).isEnabled()) {
					mPpg2AdcSelectionProto3DeluxeBoard = SensorPPG.ListOfPpg2AdcSelectionConfigValues[1]; // PPG2_A14
				}
			}
			else {
				if(mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY)!=null){
					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY).setIsEnabled(false);
				} else {
					System.out.println("2r:null");
				}
			}
		}
		
		enableShimmer3Timestamps();
	}
	
	private void enableShimmer3Timestamps() {
		SensorDetails sensorDetailTimestamp = mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.SHIMMER_TIMESTAMP);
		if(sensorDetailTimestamp!=null){
			sensorDetailTimestamp.setIsEnabled(true);
		}

		SensorDetails sensorDetailSystemTimestamp = mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_SYSTEM_TIMESTAMP);
		if(sensorDetailSystemTimestamp!=null){
			sensorDetailSystemTimestamp.setIsEnabled(true);
		}

		SensorDetails sensorDetailStreamingProp = mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_SHIMMER_STREAMING_PROPERTIES);
		if(sensorDetailStreamingProp!=null){
			sensorDetailStreamingProp.setIsEnabled(true);
		}
		
	}


	//TODO 2016-05-18 feed below into sensor map classes
	
// --------------------Implemented in SensorPPG-----------------------	
	public boolean checkIfSensorEnabled(int sensorId){
		if (getHardwareVersion() == HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R ) {
			//Used for Shimmer GSR hardware
			if(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY){
				if((super.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A12))
						|| (super.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A13)))
					return true;
				else
					return false;
			}
			else if(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY){
				if((super.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A12))
						|| (super.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A13)))
					return true;
				else
					return false;
			}
			else if(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY){
				if((super.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A1))
						|| (super.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A14)))
					return true;
				else
					return false;
			}
		}
		
		return super.isSensorEnabled(sensorId);
	}
// -------------------------------------------------------------------------------------------------------------------	

	@Override
	public int handleSpecCasesBeforeSetSensorState(int sensorId, boolean state) {
		// Special case for Dummy entries in the Sensor Map
		if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY) {
			mSensorMap.get(sensorId).setIsEnabled(state);
			if(SensorPPG.ListOfPpgAdcSelection[mPpgAdcSelectionGsrBoard].contains("A12")) {
				return Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A12;
			}
			else {
				return Configuration.Shimmer3.SENSOR_ID.HOST_PPG_A13;
			}
		}		
		else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY) {
			mSensorMap.get(sensorId).setIsEnabled(state);
			if(SensorPPG.ListOfPpg1AdcSelection[mPpg1AdcSelectionProto3DeluxeBoard].contains("A12")) {
				return Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A12;
			}
			else {
				return Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_A13;
			}
		}		
		else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY) {
			mSensorMap.get(sensorId).setIsEnabled(state);
			if(SensorPPG.ListOfPpg2AdcSelection[mPpg2AdcSelectionProto3DeluxeBoard].contains("A14")) {
				return Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A14;
			}
			else {
				return Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_A1;
			}
		}	
		return sensorId;
	}
//--------------------------------------------------------------------------------
	
	//TODO 2016-05-18 feed below into sensor map classes
	/* (non-Javadoc)
	 * @see com.shimmerresearch.driver.ShimmerDevice#checkShimmerConfigBeforeConfiguring()
	 */
	@Override
	public void checkShimmerConfigBeforeConfiguring() {
		
		if (getHardwareVersion() == HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){

			// If Shimmer name is default, update with MAC ID if available.
			if(mShimmerUserAssignedName.equals(DEFAULT_SHIMMER_NAME)){
				setDefaultShimmerName();
			}
			
			//This will fill a default trial name if the current one is invalid
			setTrialNameAndCheck(getTrialName());

			if(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR)) {
				setDefaultGsrSensorConfig(false);
			}
			
			if(!SensorEXG.checkIsAnyExgChannelEnabled(mSensorMap)){
				clearExgConfig();
			}
//			if((!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_ECG))
//					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST))
//					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_EMG))
//					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM))
//					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION)) ){
////					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT))
////					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT))
////					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT))
////					&&(!isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT))) {
////				if(!checkIfOtherExgChannelEnabled()) {
//				setDefaultECGConfiguration(getSamplingRateShimmer());
////				clearExgConfig();
////				}
//			}

			super.checkShimmerConfigBeforeConfiguring();

			setSamplingRateSensors(getSamplingRateShimmer());
		}
		
	}

	//TODO 2016-05-18 feed below into sensor map classes
// --------------Copied to PPG sensor class----------------------
	
	/**Automatically control internal expansion board power based on sensor map
	 */
	@Override
	protected void checkIfInternalExpBrdPowerIsNeeded(){
		super.checkIfInternalExpBrdPowerIsNeeded();
		
		if(mInternalExpPower==0){
			if (getHardwareVersion() == HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
				for(SensorDetails sensorDetails:mSensorMap.values()) {
					if(sensorDetails.isInternalExpBrdPowerRequired()){
						mInternalExpPower = 1;
						break;
					}
					else {
						// Exception for Int ADC sensors 
						//TODO need to check HW version??
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
		}
	}
	
// --------------------------------------------------------------------------------------	
	
	//TODO 2016-05-18 feed below into sensor map classes
	//TODO set defaults when ").setIsEnabled(false))" is set manually in the code
	@Override
	protected void setDefaultConfigForSensor(int sensorId, boolean state) {
		if(sensorId==Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR) {
			setDefaultGsrSensorConfig(state);
		}

		else if(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_ECG
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EMG
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM
				|| sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR) {
			
			// If ExG sensor enabled, set default settings for that
			// sensor. Otherwise set the default ECG configuration.
			if(state) { 
				if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION) {
					setDefaultRespirationConfiguration(getSamplingRateShimmer());
				}
				else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_ECG) {
//					System.err.println("SET ECG CHANNEL");
					setDefaultECGConfiguration(getSamplingRateShimmer());
				}
				else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EMG) {
					setDefaultEMGConfiguration(getSamplingRateShimmer());
				}
				else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST) {
					setEXGTestSignal(getSamplingRateShimmer());
				}
				else if (sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR) {
					setExgThreeUnipolarInput(getSamplingRateShimmer());
				}
				else if(sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM) {
					setEXGCustom(getSamplingRateShimmer());
				}
			}
			else {
				if(!SensorEXG.checkIsAnyExgChannelEnabled(mSensorMap)) {
//					System.err.println("CLEAR EXG CHANNEL");
					clearExgConfig();
				}
			}
		}

		else {
			super.setDefaultConfigForSensor(sensorId, state);
		}
		
	}
	
	private void setDefaultGsrSensorConfig(boolean state) {
		if(state) {
		}
		else {
			setGSRRange(4);
		}
	}

	/**
	 * @return the mBufferSize
	 */
	public int getBufferSize() {
		return mBufferSize;
	}
	
	/**
	 * @return the mMasterShimmer
	 */
	public int getMasterShimmer() {
		return mMasterShimmer;
	}

	/**
	 * @return the mSingleTouch
	 */
	public int getSingleTouch() {
		return mSingleTouch;
	}

	/**
	 * @return the mTCXO
	 */
	public int getTCXO() {
		return mTCXO;
	}
	
	private void setLowBattAutoStop(int state) {
		setLowBattAutoStop(state>0? true:false);
	}

	private void setLowBattAutoStop(boolean state) {
		mLowBattAutoStop = state;
	}

	private boolean isLowBattAutoStop() {
		return mLowBattAutoStop;
	}

	/**
	 * @return the mTrialNumberOfShimmers
	 */
	public int getTrialNumberOfShimmers() {
		return mTrialNumberOfShimmers;
	}
	
	/**
	 * @return the mTrialDurationEstimated
	 */
	public int getTrialDurationEstimatedInSecs() {
		return mTrialDurationEstimatedInSecs;
	}

	/**
	 * @param experimentDurationEstimatedInSecs the mExperimentDurationEstimated to set.  Min value is 1.
	 */
	public void setExperimentDurationEstimatedInSecs(int experimentDurationEstimatedInSecs) {
    	int maxValue = (int) ((Math.pow(2, 16))-1); 
    	if(experimentDurationEstimatedInSecs>maxValue) {
    		experimentDurationEstimatedInSecs = maxValue;
    	}
    	else if(experimentDurationEstimatedInSecs<=0) {
    		experimentDurationEstimatedInSecs = 1;
    	}
    	setTrialDurationEstimatedInSecs(experimentDurationEstimatedInSecs);
	}

	public void setTrialDurationEstimatedInSecs(int trialDurationEstimatedInSecs) {
		mTrialDurationEstimatedInSecs = trialDurationEstimatedInSecs;
	}

	/**
	 * @return the mTrialDurationMaximum
	 */
	public int getTrialDurationMaximumInSecs() {
		return mTrialDurationMaximumInSecs;
	}

	/**
	 * @param experimentDurationMaximumInSecs the mExperimentDurationMaximum to set. Min value is 0.
	 */
	public void setExperimentDurationMaximumInSecs(int experimentDurationMaximumInSecs) {
    	int maxValue = (int) ((Math.pow(2, 16))-1); 
    	if(experimentDurationMaximumInSecs>maxValue) {
    		experimentDurationMaximumInSecs = maxValue;
    	}
    	else if(experimentDurationMaximumInSecs<0) {
    		experimentDurationMaximumInSecs = 1;
    	}
    	setTrialDurationMaximumInSecs(experimentDurationMaximumInSecs);
	}

	public void setTrialDurationMaximumInSecs(int trialDurationMaximumInSecs) {
		mTrialDurationMaximumInSecs = trialDurationMaximumInSecs;
	}

	/**
	 * @return the mMacIdFromInfoMem
	 */
	public String getMacIdFromInfoMem() {
		return mMacIdFromInfoMem;
	}

	/**
	 * @return the mExperimentId
	 */
	public int getExperimentId() {
		return mTrialId;
	}

	/**
	 * @return the syncNodesList
	 */
	public List<String> getSyncNodesList() {
		return syncNodesList;
	}


	/**
	 * @return the mSyncBroadcastInterval
	 */
	public int getSyncBroadcastInterval() {
		return mSyncBroadcastInterval;
	}


	/**
	 * @return the mSyncWhenLogging
	 */
	public int getSyncWhenLogging() {
		return mSyncWhenLogging;
	}


	/**
	 * @return the mBluetoothBaudRate
	 */
	public int getBluetoothBaudRate() {
		return mBluetoothBaudRate;
	}

	public boolean getInitialized(){
		return mIsInitialised;
	}

	
	/**
	 * Get the 5V Reg. Only supported on Shimmer2/2R.
	 * @return 0 in case the 5V Reg is disableb, 1 in case the 5V Reg is enabled, and -1 in case the device doesn't support this feature
	 */
	public int get5VReg(){
		if(getHardwareVersion()!=HW_ID.SHIMMER_3 && getHardwareVersion()!=HW_ID.SHIMMER_3R){
			if((mConfigByte0 & (byte)128)!=0) {
				//then set ConfigByte0 at bit position 7
				return 1;
			} 
			else {
				return 0;
			}
		}
		else{
			return -1;
		}
	}
	
	public int getCurrentLEDStatus() {
		return mCurrentLEDStatus;
	}

	public int getBaudRate(){
		return mBluetoothBaudRate;
	}
	
	public int getShimmerVersion(){
		return getHardwareVersion();
	}

    //region --------- IS+something FUNCTIONS --------- 
    
	public boolean isUsingDefaultECGParam(){
		return mDefaultCalibrationParametersECG;
	}
	
	public boolean isUsingDefaultEMGParam(){
		return mDefaultCalibrationParametersEMG;
	}
	
    
	/**
	 * @return the mChannelMap
	 */
	public Map<String, ChannelDetails> getChannelMap() {
		return mChannelMap;
	}
	
	public boolean isUsingConfigFromInfoMem() {
		return mShimmerUsingConfigFromInfoMem;
	}

	/**
	 * @return the mMacIdFromBtParsed
	 */
	public String getMacIdFromBtParsed() {
		if(this.mMyBluetoothAddress.length()>=12) {
			return this.mMyBluetoothAddress.substring(8, 12);
		}
		return "0000";
	}
	
	/**
	 * @return the MAC address from any source available
	 */
	@Override
	public String getMacId() {
		if(mMacIdFromUart!=null){
			if(!mMacIdFromUart.isEmpty()){
				return mMacIdFromUart; 
			}
			else {
				if(!mMacIdFromInfoMem.isEmpty()){
					return mMacIdFromInfoMem; 
				}
				else {
					if(!mMacIdFromInfoMem.isEmpty()){
						return mMacIdFromInfoMem; 
					}
					else {
						return mMyBluetoothAddress; 
					}
				}
			}
		} 
		else{
			return mMyBluetoothAddress; 
		}
		
	}
	
// YYY - Implemented in SensorBattVoltage
	/**
	 * @return the mSamplingDividerVBatt
	 */
	public int getSamplingDividerVBatt() {
		return mSamplingDividerVBatt;
	}

	/**
	 * @return the mSamplingDividerGsr
	 */
	public int getSamplingDividerGsr() {
		return mSamplingDividerGsr;
	}

	public void setGSRRange(int i){
		mGSRRange = i;
	}
	
	public int getGSRRange(){
		return mGSRRange;
	}


	/**
	 * @return the mShimmerInfoMemBytes generated from an empty byte array. This is called to generate the InfoMem bytes for writing to the Shimmer.
	 */
	protected byte[] generateConfigBytesForWritingToShimmer() {
//		System.out.println("SlotDetails:" + this.mUniqueIdentifier + " " + mShimmerInfoMemBytes[3]);
		return configBytesGenerate(true);
	}
	
	
//	/**
//	 * @param mConfigTime the mConfigTime to set
//	 */
//	public void setConfigTime(long mConfigTime) {
//		this.mConfigTime = mConfigTime;
//	}

	
	/**
	 * @param mBufferSize the mBufferSize to set
	 */
	protected void setBufferSize(int mBufferSize) {
		this.mBufferSize = mBufferSize;
	}

	/**
	 * @param mExperimentNumberOfShimmers the mExperimentNumberOfShimmers to set
	 */
	public void setExperimentNumberOfShimmers(int mExperimentNumberOfShimmers) {
    	int maxValue = (int) ((Math.pow(2, 8))-1); 
    	if(mExperimentNumberOfShimmers>maxValue) {
    		mExperimentNumberOfShimmers = maxValue;
    	}
    	else if(mExperimentNumberOfShimmers<=0) {
    		mExperimentNumberOfShimmers = 1;
    	}
    	this.mTrialNumberOfShimmers = mExperimentNumberOfShimmers;
	}

//	/**
//	 * @param state the mInternalExpPower state to set
//	 */
//	protected void setInternalExpPower(int state) {
//		this.mInternalExpPower = state;
//	}
//	
//	/**
//	 * @return the mInternalExpPower
//	 */
//	public boolean isInternalExpPower() {
//		if(mInternalExpPower > 0)
//			return true;
//		else
//			return false;
//	}
//	
//	/**
//	 * @param state the mInternalExpPower state to set
//	 */
//	protected void setInternalExpPower(boolean state) {
//		if(state) 
//			mInternalExpPower = 0x01;
//		else 
//			mInternalExpPower = 0x00;
//	}
//	
//	public int getInternalExpPower(){
//		return mInternalExpPower;
//	}

	
	/**
	 * @param state the mMasterShimmer state to set
	 */
	public void setMasterShimmer(boolean state) {
		setMasterShimmer(state? 1:0);
	}

	/**
	 * @param state the mMasterShimmer state to set
	 */
	public void setMasterShimmer(int state) {
		this.mMasterShimmer = state;
	}

	/**
	 * @return the mMasterShimmer
	 */
	public boolean isMasterShimmer() {
		return (this.mMasterShimmer > 0)? true:false;
	}

	/**
	 * @param state the mSingleTouch state to set
	 */
	public void setSingleTouch(boolean state) {
		setSingleTouch(state? 1:0);
	}

	/**
	 * @param state the mSingleTouch state to set
	 */
	public void setSingleTouch(int state) {
		mSingleTouch = state;
	}

	/**
	 * @return the mSingleTouch
	 */
	public boolean isSingleTouch() {
		return (this.mSingleTouch > 0)? true:false;
	}
	
	/**
	 * @param state  the mTCXO state to set
	 */
	public void setTCXO(boolean state) {
		setTCXO(state? 1:0);
	}
	
	public void setTCXO(int state) {
		this.mTCXO = state;
	}

	/**
	 * @return the mTCXO
	 */
	public boolean isTCXO() {
		return (this.mTCXO > 0)? true:false;
	}
	
	/**
	 * @return the mSyncWhenLogging
	 */
	public boolean isSyncWhenLogging() {
		return (this.mSyncWhenLogging > 0)? true:false;
	}

	/**
	 * @param state the mSyncWhenLogging state to set
	 */
	public void setSyncWhenLogging(boolean state) {
		setSyncWhenLogging(state? 1:0);
	}

	public void setSyncWhenLogging(int state) {
		this.mSyncWhenLogging = state;
	}


	public int getButtonStart() {
		return mButtonStart;
	}
	
	public int getDisableBluetooth() {
		return mDisableBluetooth;
	}

	/**
	 * @return the mButtonStart
	 */
	public boolean isButtonStart() {
		return (this.mButtonStart > 0)? true:false;
	}
	
	public boolean isDisableBluetooth() {
		return (this.mDisableBluetooth > 0)? true:false;
	}

	
	/**
	 * @param state the mButtonStart state to set
	 */
	public void setButtonStart(boolean state) {
		setButtonStart(state? 1:0);
	}
	
	public void setDisableBluetooth(boolean state) {
		setDisableBluetooth(state? 1:0);
	}

	/**
	 * @param state the mButtonStart state to set
	 */
	public void setButtonStart(int state) {
		this.mButtonStart = state;
	}
	
	protected void setDisableBluetooth(int state) {
		this.mDisableBluetooth = state;
	}

	public void setShowErrorLedsSd(boolean state) {
		this.mShowErrorLedsSd = (state? 1:0);
	}

	public void setShowErrorLedsSd(int state) {
		this.mShowErrorLedsSd = state;
	}

	public boolean isShowErrorLedsSd() {
		return (this.mShowErrorLedsSd > 0)? true:false;
	}
	
	public void setShowErrorLedsRtc(boolean state) {
		this.mShowErrorLedsRtc = (state? 1:0);
	}

	public void setShowErrorLedsRtc(int state) {
		this.mShowErrorLedsRtc = state;
	}

	public boolean isShowErrorLedsRtc() {
		return (this.mShowErrorLedsRtc > 0)? true:false;
	}
	
	public void setIsOverrideShowErrorLedsRtc(boolean state) {
		this.isOverrideShowErrorLedsRtc = state;
	}

	public void setIsOverrideShowErrorLedsSd(boolean state) {
		this.isOverrideShowErrorLedsSd = state;
	}

	
	/**
	 * @param mExperimentId the mExperimentId to set
	 */
	public void setExperimentId(int mExperimentId) {
    	int maxValue = (int) ((Math.pow(2, 8))-1); 
    	if(mExperimentId>maxValue) {
    		mExperimentId = maxValue;
    	}
    	else if(mExperimentId<0) {
    		mExperimentId = 1;
    	}
		this.mTrialId = mExperimentId;
	}
	
	/**
	 * @param syncNodesList the syncNodesList to set
	 */
	public void setSyncNodesList(List<String> syncNodesList) {
		this.syncNodesList = syncNodesList;
	}

	/**
	 * @param mSyncBroadcastInterval the mSyncBroadcastInterval to set
	 */
	protected void setSyncBroadcastInterval(int mSyncBroadcastInterval) {
    	int maxValue = (int) ((Math.pow(2, 8))-1); 
    	if(mSyncBroadcastInterval>maxValue) {
    		mSyncBroadcastInterval = maxValue;
    	}
    	else if(mSyncBroadcastInterval<=0) {
    		mSyncBroadcastInterval = 1;
    	}
		this.mSyncBroadcastInterval = mSyncBroadcastInterval;
	}
	
	
	/**
	 * @param mBluetoothBaudRate the mBluetoothBaudRate to set
	 */
	public void setBluetoothBaudRate(int mBluetoothBaudRate) {
		this.mBluetoothBaudRate = mBluetoothBaudRate;
	}

	/**
	 * @param myBluetoothAddress the myBluetoothAddress to set
	 */
	protected void setMacIdFromBt(String myBluetoothAddress){
		this.mMyBluetoothAddress = myBluetoothAddress;
	}
	
	
	public void setPacketSize(int packetSize) {
		mPacketSize = packetSize;
	}

	


	//-------------------- Calibration Parameters Start -----------------------------------
	
	/**
	 * Converts the LSM303DLHC Accel calibration variables from Shimmer Object
	 * into a byte array for sending to the Shimmer.
	 * 
	 * @return the bytes array containing the LSM303DLHC Accel calibration
	 */
	public byte[] generateCalParamLSM303DLHCAccel(){
		return getCurrentCalibDetailsAccelWr().generateCalParamByteArray();
	}
	
	/**
	 * Converts the LSM303DLHC Magnetometer calibration variables from Shimmer Object
	 * into a byte array for sending to the Shimmer.
	 * 
	 * @return the bytes array containing the Gyroscope Calibration
	 */
	public byte[] generateCalParamLSM303DLHCMag(){
		return getCurrentCalibDetailsMag().generateCalParamByteArray();
	}
	
	public void parseCalibParamFromPacketAccelLsm(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		getCurrentCalibDetailsAccelWr().parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
	}
	
	public void getCurrentCalibDetailsAccelAlt(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		getCurrentCalibDetailsAccelAlt().parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
	}

	public void getCurrentCalibDetailsMagWr(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		getCurrentCalibDetailsMagAlt().parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
	}
	
	public void parseCalibParamFromPacketMag(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		getCurrentCalibDetailsMag().parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
	}

	public void setDefaultCalibrationShimmer3StandardImus(){
		setDefaultCalibrationShimmer3LowNoiseAccel();
		setDefaultCalibrationShimmer3WideRangeAccel();
		setDefaultCalibrationShimmer3Gyro();
		setDefaultCalibrationShimmer3Mag();
	}

	private void setDefaultCalibrationShimmer3LowNoiseAccel() {
		getCurrentCalibDetailsAccelLn().resetToDefaultParameters();
	}
	
	private void setDefaultCalibrationShimmer3WideRangeAccel() {
		getCurrentCalibDetailsAccelWr().resetToDefaultParameters();
	}

	private void setDefaultCalibrationShimmer3Mag() {
		getCurrentCalibDetailsMag().resetToDefaultParameters();
	}
	
	protected CalibDetailsKinematic getCurrentCalibDetailsAccelWr() {
		if (isShimmerGen3() || isShimmerGenGq()) { //GQ FOR LEGACY SUPPORT
			return mSensorLSM303.getCurrentCalibDetailsAccelWr();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.getCurrentCalibDetailsAccelWr();
		}
		return null;
	}

	protected CalibDetailsKinematic getCurrentCalibDetailsMag() {
		if(isShimmerGen2()){
			return mSensorShimmer2Mag.getCurrentCalibDetailsMag();
		} else if(isShimmerGen3() || isShimmerGenGq()) { //GQ FOR LEGACY SUPPORT
			return mSensorLSM303.getCurrentCalibDetailsMag();
		} else if(isShimmerGen3R()) {
			return mSensorLIS2MDL.getCurrentCalibDetailsMag();
		}
		return null;
	}
	
	protected CalibDetailsKinematic getCurrentCalibDetailsMagAlt() {
		if(isShimmerGen3R()) {
			return mSensorLIS3MDL.getCurrentCalibDetailsMagAlt();
		}
		return null;
	}
	
	protected CalibDetailsKinematic getCurrentCalibDetailsAccelLn() {
		if(isShimmerGen2()){
			return mSensorMMA736x.getCurrentCalibDetailsAccelLn();
		} else if (isShimmerGen3() || isShimmerGenGq()){ //GQ FOR LEGACY SUPPORT
			return mSensorKionixAccel.getCurrentCalibDetailsAccelLn();
		} else if (isShimmerGen3R()){
			return mSensorLSM6DSV.getCurrentCalibDetailsAccelLn();
		}
		return null;
	}
	
	protected CalibDetailsKinematic getCurrentCalibDetailsAccelAlt() {
		if (isShimmerGen3R()){
			return mSensorADXL371.getCurrentCalibDetailsAccelHighG();
		}
		return null;
	}

	@Override
	public void setMapOfSensorCalibrationAll(TreeMap<Integer, TreeMap<Integer, CalibDetails>> mapOfAllSensorCalibration) {
		super.setMapOfSensorCalibrationAll(mapOfAllSensorCalibration);
		
		for(Integer sensorId:mapOfAllSensorCalibration.keySet()){
			TreeMap<Integer, CalibDetails> mapOfSensorCalibration = mapOfAllSensorCalibration.get(sensorId);
			setSensorCalibrationPerSensor(sensorId, mapOfSensorCalibration);
		}
	}

	public void updateCurrentAccelLnCalibInUse(){
		if(isShimmerGen2()){
			mSensorMMA736x.updateCurrentAccelCalibInUse();
		} else if (isShimmerGen3()){
			mSensorKionixAccel.updateCurrentAccelLnCalibInUse();
		} else if (isShimmerGen3R()) {
			mSensorLSM6DSV.updateCurrentAccelLnCalibInUse();
		}
	}

	public void updateCurrentMagCalibInUse(){
		if(isShimmerGen3R()) {
			mSensorLIS2MDL.updateCurrentMagCalibInUse();
		} else {
			mSensorLSM303.updateCurrentMagCalibInUse();
		}
	}
	
	public void updateCurrentAccelWrCalibInUse(){
		if(isShimmerGen3()){
			mSensorLSM303.updateCurrentAccelWrCalibInUse();
		} else if(isShimmerGen3R()) {
			mSensorLIS2DW12.updateCurrentAccelWrCalibInUse();
		}
	}
	
	/**
	 * This is for legacy support related to storing the calibration parameters
	 * at the top of ShimmerObject. This approach is being replaced by instead
	 * storing the calibration parameters inside the sensor classes
	 */
	private void updateCurrentCalibInUse() {
		//TODO push to sensor clases when they are "initialised"?
		updateCurrentAccelLnCalibInUse();
		updateCurrentAccelWrCalibInUse();
		updateCurrentGyroCalibInUse();
		updateCurrentMagCalibInUse();
		updateCurrentPressureCalibInUse();
	}

	//-------------------- Calibration Parameters End -----------------------------------

	//-------------------- Pressure/Temperature Start -----------------------------------

	/**
	 * @param pressureResoRes
	 * @param calibReadSource
	 */
	protected void retrievePressureCalibrationParametersFromPacket(byte[] pressureResoRes, CALIB_READ_SOURCE calibReadSource) {
		mSensorBMPX80.parseCalParamByteArray(pressureResoRes, calibReadSource);

		//TODO below not needed here because is done in method above
		updateCurrentPressureCalibInUse();
	}
	
	public void updateCurrentPressureCalibInUse(){
		mSensorBMPX80.updateCurrentPressureCalibInUse();
	}

	public void setPressureResolution(int i){
		mSensorBMPX80.setPressureResolution(i);
	}
	
	public int getPressureResolution(){
		return mSensorBMPX80.getPressureResolution();
	}

	public void setPressureCalib(
			double AC1, double AC2, double AC3, 
			double AC4, double AC5, double AC6,
			double B1, double B2, 
			double MB, double MC, double MD){
		if(mSensorBMPX80 instanceof SensorBMP180){
			((SensorBMP180) mSensorBMPX80).setPressureCalib(AC1, AC2, AC3, AC4, AC5, AC6, B1, B2, MB, MC, MD);
		}
	}

	public byte[] getPressureRawCoefficients(){
		return mSensorBMPX80.mCalibDetailsBmpX80.getPressureRawCoefficients();
	}
	
	public byte[] getDigiAccelCalRawParams(){
		return getCurrentCalibDetailsAccelWr().generateCalParamByteArray();
	}
	
	public byte[] getAccelCalRawParams(){
		return getCurrentCalibDetailsAccelLn().generateCalParamByteArray();
	}
	
	public byte[] getMagCalRawParams(){
		return getCurrentCalibDetailsMag().generateCalParamByteArray();
	}

	//-------------------- Pressure/Temperature End -----------------------------------

	//-------------------- PPG Start -----------------------------------

	/**
	 * @return the mPpgAdcSelectionGsrBoard
	 */
	public int getPpgAdcSelectionGsrBoard() {
		return mPpgAdcSelectionGsrBoard;
	}

	/**
	 * @return the mPpg1AdcSelectionProto3DeluxeBoard
	 */
	public int getPpg1AdcSelectionProto3DeluxeBoard() {
		return mPpg1AdcSelectionProto3DeluxeBoard;
	}

	/**
	 * @return the mPpg2AdcSelectionProto3DeluxeBoard
	 */
	public int getPpg2AdcSelectionProto3DeluxeBoard() {
		return mPpg2AdcSelectionProto3DeluxeBoard;
	}

	/**
	 * @param ppgAdcSelectionGsrBoard the mPpgAdcSelectionGsrBoard to set
	 */
	protected void setPpgAdcSelectionGsrBoard(int ppgAdcSelectionGsrBoard) {
		this.mPpgAdcSelectionGsrBoard = ppgAdcSelectionGsrBoard;
		int key = Configuration.Shimmer3.SENSOR_ID.HOST_PPG_DUMMY;
		this.setSensorEnabledState(key, mSensorMap.get(key).isEnabled());
	}

	/**
	 * @param ppg1AdcSelectionProto3DeluxeBoard the mPpg1AdcSelectionProto3DeluxeBoard to set
	 */
	protected void setPpg1AdcSelectionProto3DeluxeBoard(int ppg1AdcSelectionProto3DeluxeBoard) {
		this.mPpg1AdcSelectionProto3DeluxeBoard = ppg1AdcSelectionProto3DeluxeBoard;
		int key = Configuration.Shimmer3.SENSOR_ID.HOST_PPG1_DUMMY;
		this.setSensorEnabledState(key, mSensorMap.get(key).isEnabled());
	}

	/**
	 * @param ppg2AdcSelectionProto3DeluxeBoard the mPpg2AdcSelectionProto3DeluxeBoard to set
	 */
	protected void setPpg2AdcSelectionProto3DeluxeBoard(int ppg2AdcSelectionProto3DeluxeBoard) {
		this.mPpg2AdcSelectionProto3DeluxeBoard = ppg2AdcSelectionProto3DeluxeBoard;
		int key = Configuration.Shimmer3.SENSOR_ID.HOST_PPG2_DUMMY;
		this.setSensorEnabledState(key, mSensorMap.get(key).isEnabled());
	}
	
	/** ShimmerGQBle
	 * @param mSamplingDividerPpg the mSamplingDividerPpg to set
	 */
	public void setSamplingDividerPpg(int mSamplingDividerPpg) {
		this.mSamplingDividerPpg = mSamplingDividerPpg;
	}

	/**
	 * @return the mSamplingDividerPpg
	 */
	public int getSamplingDividerPpg() {
		return mSamplingDividerPpg;
	}


	//-------------------- PPG End -----------------------------------

	//-------------------- ExG Start -----------------------------------
	
	/**
	 * Populates the individual ExG related variables in ShimmerObject per ExG
	 * chip based on the ExG configuration byte arrays
	 * 
	 * @param chipIndex
	 *            indicates whether the bytes are specific to chip 1 or chip 2
	 *            on the ExG expansion board.
	 * @param byteArray
	 *            the configuration byte array for an individual chip (10-bytes
	 *            long)
	 */
	public void exgBytesGetConfigFrom(EXG_CHIP_INDEX chipIndex, byte[] byteArray) {
		// to overcome possible backward compatability issues (where
		// bufferAns.length was 11 or 12 using all of the ExG config bytes)
		int index = 1;
		if(byteArray.length == 10) {
			index = 0;
		}
		
		if (chipIndex==EXG_CHIP_INDEX.CHIP1){
			System.arraycopy(byteArray, index, mEXG1RegisterArray, 0, 10);
			// retrieve the gain and rate from the the registers
			mEXG1RateSetting = mEXG1RegisterArray[0] & 7;
			mEXGLeadOffDetectionCurrent = (mEXG1RegisterArray[2] >> 2) & 3;
			mEXGLeadOffComparatorTreshold = (mEXG1RegisterArray[2] >> 5) & 7;
			mEXG1CH1GainSetting = (mEXG1RegisterArray[3] >> 4) & 7;
			mEXG1CH1GainValue = SensorEXG.convertEXGGainSettingToValue(mEXG1CH1GainSetting);
			mEXG1CH2GainSetting = (mEXG1RegisterArray[4] >> 4) & 7;
			mEXG1CH2GainValue = SensorEXG.convertEXGGainSettingToValue(mEXG1CH2GainSetting);
			mEXGReferenceElectrode = mEXG1RegisterArray[5] & 0x0F;
			mEXG1LeadOffCurrentMode = mEXG1RegisterArray[2] & 1;
			mEXG1Comparators = mEXG1RegisterArray[1] & 0x40;								
			mEXGRLDSense = mEXG1RegisterArray[5] & 0x10;
			mEXG1LeadOffSenseSelection = mEXG1RegisterArray[6] & 0x0f; //2P1N1P
			
			mExGConfigBytesDetails.updateFromRegisterArray(EXG_CHIP_INDEX.CHIP1, mEXG1RegisterArray);
		} 
		
		else if (chipIndex==EXG_CHIP_INDEX.CHIP2){
			System.arraycopy(byteArray, index, mEXG2RegisterArray, 0, 10);
			mEXG2RateSetting = mEXG2RegisterArray[0] & 7;
			mEXG2CH1GainSetting = (mEXG2RegisterArray[3] >> 4) & 7;
			mEXG2CH2PowerDown = (mEXG2RegisterArray[3] >> 7) & 1;
			mEXG2CH1GainValue = SensorEXG.convertEXGGainSettingToValue(mEXG2CH1GainSetting);
			mEXG2CH2GainSetting = (mEXG2RegisterArray[4] >> 4) & 7;
			mEXG2CH2GainValue = SensorEXG.convertEXGGainSettingToValue(mEXG2CH2GainSetting);
			mEXG2LeadOffCurrentMode = mEXG2RegisterArray[2] & 1;
			mEXG2Comparators = mEXG2RegisterArray[1] & 0x40;
			mEXG2LeadOffSenseSelection = mEXG2RegisterArray[6] & 0x0f; //2P
			
			mEXG2RespirationDetectState = (mEXG2RegisterArray[8] >> 6) & 0x03;
			mEXG2RespirationDetectPhase = (mEXG2RegisterArray[8] >> 2) & 0x0F;
			mEXG2RespirationDetectFreq = (mEXG2RegisterArray[9] >> 2) & 0x01;
			
			mExGConfigBytesDetails.updateFromRegisterArray(EXG_CHIP_INDEX.CHIP2, mEXG2RegisterArray);
		}
		
	}

	public void exgBytesGetConfigFrom(byte[] EXG1RegisterArray, byte[] EXG2RegisterArray){
		if(EXG1RegisterArray!=null){
			setEXG1RegisterArray(EXG1RegisterArray);
		}
		if(EXG2RegisterArray!=null){
			setEXG2RegisterArray(EXG2RegisterArray);
		}
		internalCheckExgModeAndUpdateSensorMap();
	}
	
	//TODO:2015-06-16 remove the need for this by using map
	/**
	 * Generates the ExG configuration byte arrays based on the individual ExG
	 * related variables stored in ShimmerObject. The resulting arrays are
	 * stored in the global variables mEXG1RegisterArray and mEXG2RegisterArray.
	 * 
	 */
	public void exgBytesGetFromConfig() {
		mEXG1RegisterArray = mExGConfigBytesDetails.getEXG1RegisterArray();
		mEXG2RegisterArray = mExGConfigBytesDetails.getEXG2RegisterArray();
		exgBytesGetConfigFrom(mEXG1RegisterArray, mEXG2RegisterArray);
	}
	
	/**
	 * This can only be used for Shimmer3 devices (EXG) When a enable
	 * configuration is load, the advanced exg configuration is removed, so it
	 * needs to be set again
	 * 
	 */
	 public void setDefaultECGConfiguration(double shimmerSamplingRate) {
		 if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
//			mEXG1RegisterArray = new byte[]{(byte) 2,(byte) 160,(byte) 16,(byte) 64,(byte) 64,(byte) 45,(byte) 0,(byte) 0,(byte) 2,(byte) 3};
//			mEXG2RegisterArray = new byte[]{(byte) 2,(byte) 160,(byte) 16,(byte) 64,(byte) 71,(byte) 0,(byte) 0,(byte) 0,(byte) 2,(byte) 1};

			clearExgConfig();
			setExgChannelBitsPerMode(Configuration.Shimmer3.SENSOR_ID.HOST_ECG);
			
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG1.CONVERSION_MODES.CONTINUOUS);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.REFERENCE_BUFFER.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.VOLTAGE_REFERENCE.VREF_2_42V);

//			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4);
//			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG5.CH2_PGA_GAIN.GAIN_4);
			setExGGainSetting(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4.configValueInt);

			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT);
			
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_BUFFER_POWER.ENABLED);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_NEG_INPUTS_CH2.RLD_CONNECTED_TO_IN2N);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_POS_INPUTS_CH2.RLD_CONNECTED_TO_IN2P);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_POS_INPUTS_CH1.RLD_CONNECTED_TO_IN1P);
			
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG10.RLD_REFERENCE_SIGNAL.HALF_OF_SUPPLY);
			
			setDefaultExgCommon(shimmerSamplingRate);
		 }
	}

	/**
	 * This can only be used for Shimmer3 devices (EXG) When a enable
	 * configuration is load, the advanced exg configuration is removed, so it
	 * needs to be set again
	 */
	 protected void setDefaultEMGConfiguration(double shimmerSamplingRate){
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
//			mEXG1RegisterArray = new byte[]{(byte) 2,(byte) 160,(byte) 16,(byte) 105,(byte) 96,(byte) 32,(byte) 0,(byte) 0,(byte) 2,(byte) 3};
//			mEXG2RegisterArray = new byte[]{(byte) 2,(byte) 160,(byte) 16,(byte) 129,(byte) 129,(byte) 0,(byte) 0,(byte) 0,(byte) 2,(byte) 1};
			
			clearExgConfig();
			setExgChannelBitsPerMode(Configuration.Shimmer3.SENSOR_ID.HOST_EMG);
			
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG1.CONVERSION_MODES.CONTINUOUS);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.REFERENCE_BUFFER.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.VOLTAGE_REFERENCE.VREF_2_42V);
			
//			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_12);
//			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG5.CH2_PGA_GAIN.GAIN_12);
			setExGGainSetting(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_12.configValueInt);

			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.ROUTE_CH3_TO_CH1);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.NORMAL);
			
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG4.CH1_POWER_DOWN.POWER_DOWN);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.SHORTED);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG5.CH2_POWER_DOWN.POWER_DOWN);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.SHORTED);
			
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_BUFFER_POWER.ENABLED);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG10.RLD_REFERENCE_SIGNAL.HALF_OF_SUPPLY);
			
			setDefaultExgCommon(shimmerSamplingRate);
		}
	}

	/**
	 * This can only be used for Shimmer3 devices (EXG). Enables the test signal
	 * (square wave) of both EXG chips, to use, both EXG1 and EXG2 have to be
	 * enabled
	 */
	 protected void setEXGTestSignal(double shimmerSamplingRate){
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
//			mEXG1RegisterArray = new byte[]{(byte) 2,(byte) 163,(byte) 16,(byte) 5,(byte) 5,(byte) 0,(byte) 0,(byte) 0,(byte) 2,(byte) 1};
//			mEXG2RegisterArray = new byte[]{(byte) 2,(byte) 163,(byte) 16,(byte) 5,(byte) 5,(byte) 0,(byte) 0,(byte) 0,(byte) 2,(byte) 1};
			
			clearExgConfig();
			setExgChannelBitsPerMode(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST);
			
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG1.CONVERSION_MODES.CONTINUOUS);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.REFERENCE_BUFFER.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.VOLTAGE_REFERENCE.VREF_2_42V);

			setExGGainSetting(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_1.configValueInt);

			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.TEST_SIGNAL_SELECTION.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.TEST_SIGNAL_FREQUENCY.SQUARE_WAVE_1KHZ);

			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.TEST_SIGNAL);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.TEST_SIGNAL);
			
			setDefaultExgCommon(shimmerSamplingRate);
		}
	}
		
	/**
	 * This can only be used for Shimmer3 devices (EXG) When a enable
	 * configuration is load, the advanced exg configuration is removed, so it
	 * needs to be set again
	 * @param shimmerSamplingRate 
	 * 
	 */
	 protected void setDefaultRespirationConfiguration(double shimmerSamplingRate) {
		 if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
//			mEXG1RegisterArray = new byte[]{(byte) 2,(byte) 160,(byte) 16,(byte) 64,(byte) 64,(byte) 32,(byte) 0,(byte) 0,(byte) 2,(byte) 3};
//			mEXG2RegisterArray = new byte[]{(byte) 2,(byte) 160,(byte) 16,(byte) 64,(byte) 71,(byte) 0,(byte) 0,(byte) 0,(byte) 234,(byte) 1};
			
			clearExgConfig();
			setExgChannelBitsPerMode(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION);

			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG1.CONVERSION_MODES.CONTINUOUS);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.REFERENCE_BUFFER.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.VOLTAGE_REFERENCE.VREF_2_42V);

//			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4);
//			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG5.CH2_PGA_GAIN.GAIN_4);
			setExGGainSetting(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4.configValueInt);

//			setExgPropertySingleChip(CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.INPUT_SELECTION_CH2.RLDIN_CONNECTED_TO_NEG_INPUT); //TODO:2015-06 check!!
			
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_BUFFER_POWER.ENABLED);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG10.RLD_REFERENCE_SIGNAL.HALF_OF_SUPPLY);

			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG9.RESPIRATION_DEMOD_CIRCUITRY.ON);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG9.RESPIRATION_MOD_CIRCUITRY.ON);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG9.RESPIRATION_PHASE_AT_32KHZ.PHASE_112_5);
			
			setDefaultExgCommon(shimmerSamplingRate);
		 }
	}	 

	/**
	 * This can only be used for Shimmer3 devices (EXG). Enables the default
	 * setting for the 'custom' channel.
	 * 
	 * Ben: I believe ExG can do 3 channels single ended maybe 4. For 3, The INN
	 * channels are set to "RLD" and then tie RL to ground (Won't be perfect
	 * because of R4 but should be reasonable). The RLD buffer needs to be
	 * disabled and inputs to the buffer likely set to not connected... For the
	 * 4th, it seems like we could use the RESPMOD input to get RLD - RA (and
	 * then invert in SW?). But this may always be zero if RLD is ground... We
	 * would end up with:
	 * <p>
	 * <li>Chip1 Ch1: LL - RLD 
	 * <li>Chip1 Ch2: LA - RLD 
	 * <li>Chip2 Ch1: Nothing? 
	 * <li>Chip2 Ch2: V1 - RLD
	 * <p>
	 * However there may be an advanced configuration where we use VDD/2 as
	 * input to a channel of Chip1 and then buffer that via RLD and then tie
	 * that buffered 1.5V via RLD to the ground of a sensor and the various
	 * inputs. That config would be best for AC signals (giving a Vdd/2
	 * reference) but limits peak amplitude of the incoming signal.
	 * 
	 */
	 protected void setEXGCustom(double shimmerSamplingRate){
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
//			mEXG1RegisterArray = new byte[]{(byte) 2,(byte) 163,(byte) 16,(byte) 7,(byte) 7,(byte) 0,(byte) 0,(byte) 0,(byte) 2,(byte) 1};
//			mEXG2RegisterArray = new byte[]{(byte) 2,(byte) 163,(byte) 16,(byte) 7,(byte) 7,(byte) 0,(byte) 0,(byte) 0,(byte) 2,(byte) 1};
			
			clearExgConfig();
			setExgChannelBitsPerMode(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM);
			
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG1.CONVERSION_MODES.CONTINUOUS);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.REFERENCE_BUFFER.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.VOLTAGE_REFERENCE.VREF_2_42V);

			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.TEST_SIGNAL_SELECTION.ON);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.TEST_SIGNAL_FREQUENCY.SQUARE_WAVE_1KHZ);

			setExGGainSetting(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4.configValueInt);

//			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT);
			
			setDefaultExgCommon(shimmerSamplingRate);
		}
	}

	public void setExgThreeUnipolarInput(double shimmerSamplingRate) {
		clearExgConfig();
		setExgChannelBitsPerMode(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR);

		setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG1.CONVERSION_MODES.CONTINUOUS);
		setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.REFERENCE_BUFFER.ON);
		setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.VOLTAGE_REFERENCE.VREF_2_42V);

//		setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4);
//		setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG5.CH2_PGA_GAIN.GAIN_4);
		setExGGainSetting(EXG_SETTING_OPTIONS.REG4.CH1_PGA_GAIN.GAIN_4.configValueInt);

		setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT);
		setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT);
		setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.SHORTED);
		setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2,EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT);

		setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG6.RLD_BUFFER_POWER.ENABLED);
		setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG10.RLD_REFERENCE_SIGNAL.HALF_OF_SUPPLY);

		setDefaultExgCommon(shimmerSamplingRate);
	}
	
	private void setDefaultExgCommon(double shimmerSamplingRate) {
		if(ShimmerVerObject.isSupportedExgChipClocksJoined(getShimmerVerObject(), getExpansionBoardDetails())){
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1,EXG_SETTING_OPTIONS.REG2.OSCILLATOR_CLOCK_CONNECTION.ON);
		}
		setExGRateFromFreq(shimmerSamplingRate);
		exgBytesGetConfigFrom(mEXG1RegisterArray, mEXG2RegisterArray);
	}

	protected void clearExgConfig(){
		setExgChannelBitsPerMode(-1);
		mExGConfigBytesDetails.startNewExGConig();

		mEXG1RegisterArray = mExGConfigBytesDetails.getEXG1RegisterArray();
		mEXG2RegisterArray = mExGConfigBytesDetails.getEXG2RegisterArray();
		exgBytesGetConfigFrom(mEXG1RegisterArray, mEXG2RegisterArray);
	}
	
	protected boolean isExgPropertyEnabled(EXG_CHIP_INDEX chipIndex, ExGConfigOption option){
		return mExGConfigBytesDetails.isExgPropertyEnabled(chipIndex, option);
	}
	 
	protected void setExgPropertyBothChips(ExGConfigOption option){
		mExGConfigBytesDetails.setExgPropertyBothChips(option);
		mEXG1RegisterArray = mExGConfigBytesDetails.getEXG1RegisterArray();
		mEXG2RegisterArray = mExGConfigBytesDetails.getEXG2RegisterArray();
		
		exgBytesGetConfigFrom(mEXG1RegisterArray, mEXG2RegisterArray);
	}

	 
	/** Note: Doesn't update the Sensor Map
	 * @param chipIndex
	 * @param option
	 */
	protected void setExgPropertySingleChip(EXG_CHIP_INDEX chipIndex, ExGConfigOption option){
		mExGConfigBytesDetails.setExgPropertySingleChip(chipIndex,option);
		if(chipIndex==EXG_CHIP_INDEX.CHIP1){
			mEXG1RegisterArray = mExGConfigBytesDetails.getEXG1RegisterArray();
		}
		else if(chipIndex==EXG_CHIP_INDEX.CHIP2){
			mEXG2RegisterArray = mExGConfigBytesDetails.getEXG2RegisterArray();
		}
		updateExgVariables(chipIndex);
	}
	
	/** Note: Doesn't update the Sensor Map
	 * @param chipIndex
	 * @param propertyName
	 * @param value
	 */
	public void setExgPropertySingleChipValue(EXG_CHIP_INDEX chipIndex, String propertyName, int value){
		mExGConfigBytesDetails.setExgPropertyValue(chipIndex,propertyName,value);
		if(chipIndex==EXG_CHIP_INDEX.CHIP1){
			mEXG1RegisterArray = mExGConfigBytesDetails.getEXG1RegisterArray();
		}
		else if(chipIndex==EXG_CHIP_INDEX.CHIP2){
			mEXG2RegisterArray = mExGConfigBytesDetails.getEXG2RegisterArray();
		}
		updateExgVariables(chipIndex);
	}
	
	private void updateExgVariables(EXG_CHIP_INDEX chipIndex) {
		if(chipIndex==EXG_CHIP_INDEX.CHIP1){
			exgBytesGetConfigFrom(EXG_CHIP_INDEX.CHIP1, mEXG1RegisterArray);
		}
		else if(chipIndex==EXG_CHIP_INDEX.CHIP2){
			exgBytesGetConfigFrom(EXG_CHIP_INDEX.CHIP2, mEXG2RegisterArray);
		}
	}
	
	public int getExgPropertySingleChip(EXG_CHIP_INDEX chipIndex, String propertyName){
		return mExGConfigBytesDetails.getExgPropertySingleChip(chipIndex, propertyName);
	}
	
	
	public HashMap<String, Integer> getMapOfExGSettingsChip1(){
		return mExGConfigBytesDetails.mMapOfExGSettingsChip1ThisShimmer;
	}

	public HashMap<String, Integer> getMapOfExGSettingsChip2(){
		return mExGConfigBytesDetails.mMapOfExGSettingsChip2ThisShimmer;
	}
	
	protected void checkExgResolutionFromEnabledSensorsVar(){
		ConfigByteLayout infoMemLayout = getConfigByteLayout();
		if(infoMemLayout instanceof ConfigByteLayoutShimmer3){
			ConfigByteLayoutShimmer3 infoMemLayoutCast = (ConfigByteLayoutShimmer3)infoMemLayout;
			mIsExg1_24bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg1_24bitFlag)>0)? true:false;
			mIsExg2_24bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg2_24bitFlag)>0)? true:false;
			mIsExg1_16bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg1_16bitFlag)>0)? true:false;
			mIsExg2_16bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg2_16bitFlag)>0)? true:false;
		}
		//Needs to be here just for SD parsing
		else if(infoMemLayout instanceof ConfigByteLayoutShimmerGq802154){
			ConfigByteLayoutShimmerGq802154 infoMemLayoutCast = (ConfigByteLayoutShimmerGq802154)infoMemLayout;
			mIsExg1_24bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg1_24bitFlag)>0)? true:false;
			mIsExg2_24bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg2_24bitFlag)>0)? true:false;
			mIsExg1_16bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg1_16bitFlag)>0)? true:false;
			mIsExg2_16bitEnabled = ((mEnabledSensors & infoMemLayoutCast.maskExg2_16bitFlag)>0)? true:false;
		}

		if(mIsExg1_16bitEnabled||mIsExg2_16bitEnabled){
			mExGResolution = 0;
		}
		else if(mIsExg1_24bitEnabled||mIsExg2_24bitEnabled){
			mExGResolution = 1;
		}
	}

	private void updateEnabledSensorsFromExgResolution(){
		long enabledSensors = getEnabledSensors();
		ConfigByteLayout infoMemLayout = getConfigByteLayout();
		if(infoMemLayout instanceof ConfigByteLayoutShimmer3){
			ConfigByteLayoutShimmer3 infoMemLayoutCast = (ConfigByteLayoutShimmer3)infoMemLayout;
	
			//JC: should this be here -> checkExgResolutionFromEnabledSensorsVar()
			
			enabledSensors &= ~infoMemLayoutCast.maskExg1_24bitFlag;
			enabledSensors |= (mIsExg1_24bitEnabled? infoMemLayoutCast.maskExg1_24bitFlag:0);
			
			enabledSensors &= ~infoMemLayoutCast.maskExg2_24bitFlag;
			enabledSensors |= (mIsExg2_24bitEnabled? infoMemLayoutCast.maskExg2_24bitFlag:0);
			
			enabledSensors &= ~infoMemLayoutCast.maskExg1_16bitFlag;
			enabledSensors |= (mIsExg1_16bitEnabled? infoMemLayoutCast.maskExg1_16bitFlag:0);
			
			enabledSensors &= ~infoMemLayoutCast.maskExg2_16bitFlag;
			enabledSensors |= (mIsExg2_16bitEnabled? infoMemLayoutCast.maskExg2_16bitFlag:0);
		}
		//Needs to be here just for SD parsing
		else if(infoMemLayout instanceof ConfigByteLayoutShimmerGq802154){
			ConfigByteLayoutShimmerGq802154 infoMemLayoutCast = (ConfigByteLayoutShimmerGq802154)infoMemLayout;
	
			//JC: should this be here -> checkExgResolutionFromEnabledSensorsVar()
			
			enabledSensors &= ~infoMemLayoutCast.maskExg1_24bitFlag;
			enabledSensors |= (mIsExg1_24bitEnabled? infoMemLayoutCast.maskExg1_24bitFlag:0);
			
			enabledSensors &= ~infoMemLayoutCast.maskExg2_24bitFlag;
			enabledSensors |= (mIsExg2_24bitEnabled? infoMemLayoutCast.maskExg2_24bitFlag:0);
			
			enabledSensors &= ~infoMemLayoutCast.maskExg1_16bitFlag;
			enabledSensors |= (mIsExg1_16bitEnabled? infoMemLayoutCast.maskExg1_16bitFlag:0);
			
			enabledSensors &= ~infoMemLayoutCast.maskExg2_16bitFlag;
			enabledSensors |= (mIsExg2_16bitEnabled? infoMemLayoutCast.maskExg2_16bitFlag:0);
		}
		
		setEnabledSensors(enabledSensors);
	}
	
	private void setExgChannelBitsPerMode(int sensorId){
		mIsExg1_24bitEnabled = false;
		mIsExg2_24bitEnabled = false;
		mIsExg1_16bitEnabled = false;
		mIsExg2_16bitEnabled = false;
		
		boolean chip1Enabled = false;
		boolean chip2Enabled = false;
		if(sensorId==-1){
			chip1Enabled = false;
			chip2Enabled = false;
		}
		else if (sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_ECG
				|| sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION
				|| sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM
				|| sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST
				|| sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR) {
			chip1Enabled = true;
			chip2Enabled = true;
		}
		else if (sensorId == Configuration.Shimmer3.SENSOR_ID.HOST_EMG) {
			chip1Enabled = true;
			chip2Enabled = false;
		}
		
		if(mExGResolution==1){
			mIsExg1_24bitEnabled = chip1Enabled;
			mIsExg2_24bitEnabled = chip2Enabled;
		}
		else {
			mIsExg1_16bitEnabled = chip1Enabled;
			mIsExg2_16bitEnabled = chip2Enabled;
		}
	}
	
//	private static boolean checkIfOtherExgChannelEnabled(Map<Integer,SensorDetails> sensorEnabledMap) {
//		for(Integer sensorId:sensorEnabledMap.keySet()) {
//			if(sensorEnabledMap.get(sensorId).isEnabled()) {
//				if((sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_ECG)
//					||(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EMG)
//					||(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST)
//					||(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM)
//					||(sensorId==Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION) ){
////					||(sensorId==Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT)
////					||(sensorId==Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT)
////					||(sensorId==Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT)
////					||(sensorId==Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT)) {
//					return true;
//				}
//			}
//		}
//		return false;
//	}
	
	/**
	 * @return the mEXG1RateSetting
	 */
	public int getEXG1RateSetting() {
		return getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG1_DATA_RATE);
	}

	/**
	 * @return the mEXGReferenceElectrode
	 */
	public int getEXGReferenceElectrode() {
		return mExGConfigBytesDetails.getEXGReferenceElectrode();
	}
	
	/**
	 * @return the mEXGLeadOffDetectionCurrent
	 */
	public int getEXGLeadOffDetectionCurrent() {
		return getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG3_LEAD_OFF_CURRENT);
	}


	/**
	 * @return the mEXGLeadOffComparatorTreshold
	 */
	public int getEXGLeadOffComparatorTreshold() {
		return getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG3_COMPARATOR_THRESHOLD);
	}

	/**
	 * @return the mEXG2RespirationDetectFreq
	 */
	public int getEXG2RespirationDetectFreq() {
		return getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG10_RESPIRATION_CONTROL_FREQUENCY);
	}

	/**
	 * @return the mEXG2RespirationDetectPhase
	 */
	public int getEXG2RespirationDetectPhase() {
		return getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG9_RESPIRATION_PHASE);
	}
	
	/**
	 * @return the mEXG1RegisterArray
	 */
	public byte[] getEXG1RegisterArray() {
		return mEXG1RegisterArray;
	}

	/**
	 * @return the mEXG2RegisterArray
	 */
	public byte[] getEXG2RegisterArray() {
		return mEXG2RegisterArray;
	}
	
	/**
	 * @return the mExGResolution
	 */
	public int getExGResolution() {
		//System.out.println("mExGResolution: " +mExGResolution);
		return mExGResolution;
	}
	
	public int getExGGainSetting(){
	//  System.out.println("SlotDetails: getExGGain - Setting: = " + mEXG1CH1GainSetting + " - Value = " + mEXG1CH1GainValue);
		return getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_PGA_GAIN);
	}
	
	public int getExg1CH1GainValue(){
		return SensorEXG.convertEXGGainSettingToValue(getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_PGA_GAIN));
	}
		 
	public int getExg1CH2GainValue(){
		return SensorEXG.convertEXGGainSettingToValue(getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_PGA_GAIN));
	}
 
	public int getExg2CH1GainValue(){
		return SensorEXG.convertEXGGainSettingToValue(getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG4_CHANNEL_1_PGA_GAIN));
	}
 
	public int getExg2CH2GainValue(){
		return SensorEXG.convertEXGGainSettingToValue(getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG5_CHANNEL_2_PGA_GAIN));
	}
	
	public boolean areExgChannelGainsEqual(List<EXG_CHIP_INDEX> listOfChipsToCheck){
		boolean areEqual = true;
		if(listOfChipsToCheck.contains(EXG_CHIP_INDEX.CHIP1)){
			if(getExg1CH1GainValue() != getExg1CH2GainValue()) {
				areEqual = false;
			}
		}

		if(listOfChipsToCheck.contains(EXG_CHIP_INDEX.CHIP2)){
			if(getExg2CH1GainValue() != getExg2CH2GainValue()) {
				areEqual = false;
			}
		}

		if(listOfChipsToCheck.contains(EXG_CHIP_INDEX.CHIP1) && listOfChipsToCheck.contains(EXG_CHIP_INDEX.CHIP2)){
			if(getExg1CH1GainValue() != getExg2CH1GainValue()) {
				areEqual = false;
			}
		}
		return areEqual;
	}

	protected void setExGGainSetting(EXG_CHIP_INDEX chipID,  int channel, int value){
		if(chipID==EXG_CHIP_INDEX.CHIP1){
			if(channel==1){
				setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG4_CHANNEL_1_PGA_GAIN,value);
				exg1Ch1CalFactor24Bit = (((2.42*1000)/getExg1CH1GainValue())/(Math.pow(2,23)-1));
				//TODO need to confirm, why *2 for 16-bit?
				exg1Ch1CalFactor16Bit = (((2.42*1000)/getExg1CH1GainValue()*2)/(Math.pow(2,15)-1));
			}
			else if(channel==2){
				setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG5_CHANNEL_2_PGA_GAIN,value);
				exg1Ch2CalFactor24Bit = (((2.42*1000)/getExg1CH2GainValue())/(Math.pow(2,23)-1));
				//TODO need to confirm, why *2 for 16-bit?
				exg1Ch2CalFactor16Bit = (((2.42*1000)/getExg1CH2GainValue()*2)/(Math.pow(2,15)-1));
			}
		}
		else if(chipID==EXG_CHIP_INDEX.CHIP2){
			if(channel==1){
				setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2,EXG_SETTINGS.REG4_CHANNEL_1_PGA_GAIN,value);
				exg2Ch1CalFactor24Bit = (((2.42*1000)/getExg2CH1GainValue())/(Math.pow(2,23)-1));
				//TODO need to confirm, why *2 for 16-bit?
				exg2Ch1CalFactor16Bit = (((2.42*1000)/getExg2CH1GainValue()*2)/(Math.pow(2,15)-1));
			}
			else if(channel==2){
				setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2,EXG_SETTINGS.REG5_CHANNEL_2_PGA_GAIN,value);
				exg2Ch2CalFactor24Bit = (((2.42*1000)/getExg2CH2GainValue())/(Math.pow(2,23)-1));
				//TODO need to confirm, why *2 for 16-bit?
				exg2Ch2CalFactor16Bit = (((2.42*1000)/getExg2CH2GainValue()*2)/(Math.pow(2,15)-1));
			}
		}
	}

	protected void setExGGainSetting(int value){
		setExGGainSetting(EXG_CHIP_INDEX.CHIP1, 1, value);
		setExGGainSetting(EXG_CHIP_INDEX.CHIP1, 2, value);
		setExGGainSetting(EXG_CHIP_INDEX.CHIP2, 1, value);
		setExGGainSetting(EXG_CHIP_INDEX.CHIP2, 2, value);
	}
	
	protected void setExGResolution(int i){
		mExGResolution = i;
		
		if(i==0) { // 16-bit
			//this is needed so the BT can write the correct sensor
			/*if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0){
				mEnabledSensors=mEnabledSensors^SENSOR_EXG1_16BIT;
			}
			if ((mEnabledSensors & SENSOR_EXG2_16BIT)>0){
				mEnabledSensors=mEnabledSensors^SENSOR_EXG2_16BIT;
			}
			mEnabledSensors = SENSOR_EXG1_16BIT|SENSOR_EXG2_16BIT;
			*/
			//
			
			if(mIsExg1_24bitEnabled){
				mIsExg1_24bitEnabled = false;
				mIsExg1_16bitEnabled = true;
			}
			if(mIsExg2_24bitEnabled){
				mIsExg2_24bitEnabled = false;
				mIsExg2_16bitEnabled = true;
			}
		}
		else if(i==1) { // 24-bit
			/*if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0){
				mEnabledSensors=mEnabledSensors^SENSOR_EXG1_24BIT;
			}
			if ((mEnabledSensors & SENSOR_EXG2_24BIT)>0){
				mEnabledSensors=mEnabledSensors^SENSOR_EXG2_24BIT;
			}
			mEnabledSensors = SENSOR_EXG1_24BIT|SENSOR_EXG2_24BIT;
			*/
			if(mIsExg1_16bitEnabled){
				mIsExg1_24bitEnabled = true;
				mIsExg1_16bitEnabled = false;
			}
			if(mIsExg2_16bitEnabled){
				mIsExg2_24bitEnabled = true;
				mIsExg2_16bitEnabled = false;
			}
		}
		
		updateEnabledSensorsFromExgResolution();
		
//		if(mSensorMap != null) {
//			if(i==0) { // 16-bit
//				if(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT)) {
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT).setIsEnabled(false);
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT).setIsEnabled(true);
//				}
//				if(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT)) {
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT).setIsEnabled(false);
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT).setIsEnabled(true);
//				}
//			}
//			else if(i==1) { // 24-bit
//				if(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT)) {
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT).setIsEnabled(false);
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT).setIsEnabled(true);
//				}
//				if(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT)) {
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT).setIsEnabled(false);
//					mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT).setIsEnabled(true);
//				}
//			}
//		}
	}
	
	
	/** Note: Doesn't update the Sensor Map
	 * @param mEXG1RegisterArray the mEXG1RegisterArray to set
	 */
	protected void setEXG1RegisterArray(byte[] EXG1RegisterArray) {
		setEXGRegisterArray(EXG_CHIP_INDEX.CHIP1, EXG1RegisterArray);
	}

	/** Note: Doesn't update the Sensor Map
	 * @param mEXG2RegisterArray the mEXG2RegisterArray to set
	 */
	protected void setEXG2RegisterArray(byte[] EXG2RegisterArray) {
		setEXGRegisterArray(EXG_CHIP_INDEX.CHIP2, EXG2RegisterArray);
	}

	protected void setEXGRegisterArray(EXG_CHIP_INDEX chipId, byte[] EXGRegisterArray) {
		if(chipId==EXG_CHIP_INDEX.CHIP1){
			this.mEXG1RegisterArray = EXGRegisterArray;
			exgBytesGetConfigFrom(EXG_CHIP_INDEX.CHIP1, mEXG1RegisterArray);
		}
		else if(chipId==EXG_CHIP_INDEX.CHIP2){
			this.mEXG2RegisterArray = EXGRegisterArray;
			exgBytesGetConfigFrom(EXG_CHIP_INDEX.CHIP2, mEXG2RegisterArray);
		}
	}


	/**
	 *This can only be used for Shimmer3 devices (EXG) 
	 *When a enable configuration is loaded, the advanced ExG configuration is removed, so it needs to be set again
	 */
	 protected void enableDefaultECGConfiguration() {
		 if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
			setDefaultECGConfiguration(getSamplingRateShimmer());
		 }
	}

	/**
	 * This can only be used for Shimmer3 devices (EXG)
	 * When a enable configuration is loaded, the advanced ExG configuration is removed, so it needs to be set again
	 */
	protected void enableDefaultEMGConfiguration(){
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
			setDefaultEMGConfiguration(getSamplingRateShimmer());
		}
	}

	/**
	 * This can only be used for Shimmer3 devices (EXG). Enables the test signal (square wave) of both EXG chips, to use, both EXG1 and EXG2 have to be enabled
	 */
	protected void enableEXGTestSignal(){
		if (getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
			setEXGTestSignal(getSamplingRateShimmer());
		}
	}

	/**
	 * @param valueToSet the valueToSet to set
	 */
	protected void setEXGRateSetting(int valueToSet) {
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG1_DATA_RATE,(int)valueToSet);
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2,EXG_SETTINGS.REG1_DATA_RATE,(int)valueToSet);
	}
	
	/**
	 * @param valueToSet the valueToSet to set
	 */
	protected void setEXGRateSetting(EXG_CHIP_INDEX chipID, int valueToSet) {
		if(chipID==EXG_CHIP_INDEX.CHIP1){
			setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG1_DATA_RATE,(int)valueToSet);
		}
		else if(chipID==EXG_CHIP_INDEX.CHIP2){
			setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2,EXG_SETTINGS.REG1_DATA_RATE,(int)valueToSet);
		}
	}

	/**
	 * @param mEXGReferenceElectrode the mEXGReferenceElectrode to set
	 */
	protected void setEXGReferenceElectrode(int valueToSet) {
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG6_CH2_RLD_NEG_INPUTS,((valueToSet&0x08) == 0x08)? 1:0);
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG6_CH2_RLD_POS_INPUTS,((valueToSet&0x04) == 0x04)? 1:0);
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG6_CH1_RLD_NEG_INPUTS,((valueToSet&0x02) == 0x02)? 1:0);
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1,EXG_SETTINGS.REG6_CH1_RLD_POS_INPUTS,((valueToSet&0x01) == 0x01)? 1:0);
	}

	protected void setEXGLeadOffCurrentMode(int mode){
		if(mode==0){//Off
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG3.LEAD_OFF_FREQUENCY.DC);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.LEAD_OFF_COMPARATORS.OFF);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG6.RLD_LEAD_OFF_SENSE_FUNCTION.OFF);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.OFF);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.OFF);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.OFF);
			if(isEXGUsingDefaultEMGConfiguration()){
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG5.CH2_POWER_DOWN.POWER_DOWN);
			}
		}
		else if(mode==1){//DC Current
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG3.LEAD_OFF_FREQUENCY.DC);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.LEAD_OFF_COMPARATORS.ON);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG6.RLD_LEAD_OFF_SENSE_FUNCTION.ON);

			if(isEXGUsingDefaultThreeUnipolarConfiguration()){
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.OFF);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.ON);
			} else {
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.ON);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.ON);
			}

			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.OFF);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.OFF);

			setExgPropertyBothChips(ExGConfigBytesDetails.EXG_SETTING_OPTIONS.REG3.LEAD_OFF_CURRENT.CURRENT_22NA);
			setExgPropertyBothChips(ExGConfigBytesDetails.EXG_SETTING_OPTIONS.REG3.COMPARATOR_THRESHOLD.POS90NEG10);
			
			//For EMG the second chip needs to be powered up in order to support lead-off detection
			if(isEXGUsingDefaultEMGConfiguration()){
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG5.CH2_POWER_DOWN.NORMAL_OPERATION);
			}
		}
		else if(mode==2){//AC Current
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG3.LEAD_OFF_FREQUENCY.AC);
			setExgPropertyBothChips(EXG_SETTING_OPTIONS.REG2.LEAD_OFF_COMPARATORS.ON);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG6.RLD_LEAD_OFF_SENSE_FUNCTION.ON);
			
			if(isEXGUsingDefaultThreeUnipolarConfiguration()){
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.OFF);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.ON);
			} else {
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.ON);
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.ON);
			}

			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.OFF);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.OFF);
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.OFF);

			//For EMG the second chip needs to be powered up in order to support lead-off detection
			if(isEXGUsingDefaultEMGConfiguration()){
				setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG5.CH2_POWER_DOWN.NORMAL_OPERATION);
			}
		}
	}

	protected int getEXGLeadOffCurrentMode(){
		if(isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG6.RLD_LEAD_OFF_SENSE_FUNCTION.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH2.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH2.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_NEG_INPUTS_CH1.ON)
				||isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG7.LEAD_OFF_DETECT_POS_INPUTS_CH1.ON)
				){
			if(isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG3.LEAD_OFF_FREQUENCY.DC)){
				return 1;//DC Current
			}
			else if(isExgPropertyEnabled(EXG_CHIP_INDEX.CHIP1, EXG_SETTING_OPTIONS.REG3.LEAD_OFF_FREQUENCY.AC)){
				return 2;//AC Current
			}
		}
		return 0;//Off
	}

	/**
	 * @param mEXGLeadOffDetectionCurrent the mEXGLeadOffDetectionCurrent to set
	 */
	protected void setEXGLeadOffDetectionCurrent(int mEXGLeadOffDetectionCurrent) {
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG3_LEAD_OFF_CURRENT, mEXGLeadOffDetectionCurrent);
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG3_LEAD_OFF_CURRENT, mEXGLeadOffDetectionCurrent);
	}


	/**
	 * @param mEXGLeadOffComparatorTreshold the mEXGLeadOffComparatorTreshold to set
	 */
	protected void setEXGLeadOffComparatorTreshold(int mEXGLeadOffComparatorTreshold) {
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG3_COMPARATOR_THRESHOLD, mEXGLeadOffComparatorTreshold);
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG3_COMPARATOR_THRESHOLD, mEXGLeadOffComparatorTreshold);
	}

	/**
	 * @param mEXG2RespirationDetectFreq the mEXG2RespirationDetectFreq to set
	 */
	protected void setEXG2RespirationDetectFreq(int mEXG2RespirationDetectFreq) {
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG10_RESPIRATION_CONTROL_FREQUENCY, mEXG2RespirationDetectFreq);
		checkWhichExgRespPhaseValuesToUse();
		
		if(isExgRespirationDetectFreq32kHz()) {
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG9.RESPIRATION_PHASE_AT_32KHZ.PHASE_112_5);
		}
		else {
			setExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTING_OPTIONS.REG9.RESPIRATION_PHASE_AT_64KHZ.PHASE_157_5);
		}
	}

	/**
	 * @param mEXG2RespirationDetectPhase the mEXG2RespirationDetectPhase to set
	 */
	protected void setEXG2RespirationDetectPhase(int mEXG2RespirationDetectPhase) {
		setExgPropertySingleChipValue(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG9_RESPIRATION_PHASE, mEXG2RespirationDetectPhase);
	}
	
	/** TODO USE SENSOR MAPS and isEXGUsingDefaultECGConfiguration() instead */
	@Deprecated 
	public boolean isEXGUsingDefaultECGConfigurationForSDFW(){
		if (getFirmwareIdentifier() == FW_ID.GQ_802154){
			if(((mEXG1RegisterArray[3] & 0x0F)==0)&&((mEXG1RegisterArray[4] & 0x0F)==0)){
				return true;
			}
		}
		else {
//			if((mIsExg1_16bitEnabled&&mIsExg2_16bitEnabled)||(mIsExg1_24bitEnabled&&mIsExg2_24bitEnabled)){
				if(((mEXG1RegisterArray[3] & 0x0F)==0)&&((mEXG1RegisterArray[4] & 0x0F)==0)&& ((mEXG2RegisterArray[3] & 0x0F)==0)&&((mEXG2RegisterArray[4] & 0x0F)==7)){
					return true;
				}
//			}
		}
		return false;

	}

	public boolean isEXGUsingDefaultECGConfiguration(){
		if (getFirmwareIdentifier() == FW_ID.GQ_802154){
//			if(((mEXG1RegisterArray[3] & 0x0F)==0)&&((mEXG1RegisterArray[4] & 0x0F)==0)){
			if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.NORMAL.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.NORMAL.configValueInt)){
				return true;
			}
		}
		else {
			if((mIsExg1_16bitEnabled && mIsExg2_16bitEnabled) || (mIsExg1_24bitEnabled && mIsExg2_24bitEnabled)){
//				if(((mEXG1RegisterArray[3] & 0x0F)==0) 
//						&& ((mEXG1RegisterArray[4] & 0x0F)==0) 
//						&& ((mEXG2RegisterArray[3] & 0x0F)==0) 
//						&& ((mEXG2RegisterArray[4] & 0x0F)==7)){
				if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.NORMAL.configValueInt)
						&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.NORMAL.configValueInt)
						&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.NORMAL.configValueInt)
						&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT.configValueInt)){
					
					return true;
				}
			}
		}
		return false;
	}

	public boolean isEXGUsingDefaultEMGConfiguration(){
		if((mIsExg1_16bitEnabled && !mIsExg2_16bitEnabled) || (mIsExg1_24bitEnabled && !mIsExg2_24bitEnabled)){
//			if(((mEXG1RegisterArray[3] & 0x0F)==9)
//					&& ((mEXG1RegisterArray[4] & 0x0F)==0)
//					&& ((mEXG2RegisterArray[3] & 0x0F)==1)
//					&& ((mEXG2RegisterArray[4] & 0x0F)==1)){
			if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.ROUTE_CH3_TO_CH1.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.NORMAL.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.SHORTED.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.SHORTED.configValueInt)){
				return true;
			}
		}
		return false;
	}
	
	public boolean isEXGUsingDefaultTestSignalConfiguration(){
		if((mIsExg1_16bitEnabled && mIsExg2_16bitEnabled) || (mIsExg1_24bitEnabled && mIsExg2_24bitEnabled)){
//			if(((mEXG1RegisterArray[3] & 0x0F)==5)
//					&& ((mEXG1RegisterArray[4] & 0x0F)==5)
//					&& ((mEXG2RegisterArray[3] & 0x0F)==5)
//					&& ((mEXG2RegisterArray[4] & 0x0F)==5)){
			if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.TEST_SIGNAL.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.TEST_SIGNAL.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.TEST_SIGNAL.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.TEST_SIGNAL.configValueInt)){
				return true;
			}
		}
		return false;
	}

	public boolean isEXGUsingDefaultRespirationConfiguration(){
		if((mIsExg1_16bitEnabled && mIsExg2_16bitEnabled) || (mIsExg1_24bitEnabled && mIsExg2_24bitEnabled)){
			//		if(isEXGUsingDefaultECGConfiguration()&&((mEXG2RegisterArray[8] & 0xC0)==0xC0)){
//			if((mEXG2RegisterArray[8] & 0xC0)==0xC0){
			if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG9_RESPIRATION_MOD_CIRCUITRY)==EXG_SETTING_OPTIONS.REG9.RESPIRATION_MOD_CIRCUITRY.ON.configValueInt)
				&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG9_RESPIRATION_DEMOD_CIRCUITRY)==EXG_SETTING_OPTIONS.REG9.RESPIRATION_DEMOD_CIRCUITRY.ON.configValueInt)){
				return true;
			}
		}
		return false;
	}
	
	public boolean isEXGUsingDefaultThreeUnipolarConfiguration(){
		if((mIsExg1_16bitEnabled && mIsExg2_16bitEnabled) || (mIsExg1_24bitEnabled && mIsExg2_24bitEnabled)){
			if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.SHORTED.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP2, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.RLDIN_CONNECTED_TO_NEG_INPUT.configValueInt)){
				return true;
			}
		}
		return false;
	}
	
	public boolean isEXGUsingDefaultECGGqConfiguration(){
		if (mShimmerVerObject.getFirmwareIdentifier() == FW_ID.GQ_802154){
			if((getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG4_CHANNEL_1_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG4.CH1_INPUT_SELECTION.NORMAL.configValueInt)
					&& (getExgPropertySingleChip(EXG_CHIP_INDEX.CHIP1, EXG_SETTINGS.REG5_CHANNEL_2_INPUT_SELECTION)==EXG_SETTING_OPTIONS.REG5.CH2_INPUT_SELECTION.NORMAL.configValueInt)){
				return true;
			}
		}
		return false;
	}


	public boolean isEXGUsingCustomSignalConfiguration(){
		if(mIsExg1_16bitEnabled||mIsExg2_16bitEnabled||mIsExg1_24bitEnabled||mIsExg2_24bitEnabled){
			return true;
		}
		return false;
	}
	
	/**
	 * @return true if ExG respiration detection frequency is 32kHz and false if 64kHz
	 */
	public boolean isExgRespirationDetectFreq32kHz() {
		return (getEXG2RespirationDetectFreq()==0)? true:false;
	}
	
	
	/**
	 * Computes next higher available sensor sampling rate setting based on
	 * passed in "freq" variable and dependent on whether low-power mode is set.
	 * 
	 * @param freq
	 * @return int the rate configuration setting for the respective sensor
	 */
	public int setExGRateFromFreq(double freq) {
		
		int valueToSet = 0x00; // 125Hz
		if (freq<=125) {
			valueToSet = 0x00; // 125Hz
		} else if (freq<=250) {
			valueToSet = 0x01; // 250Hz
		} else if (freq<=500) {
			valueToSet = 0x02; // 500Hz
		} else if (freq<=1000) {
			valueToSet = 0x03; // 1000Hz
		} else if (freq<=2000) {
			valueToSet = 0x04; // 2000Hz
		} else if (freq<=4000) {
			valueToSet = 0x05; // 4000Hz
		} else if (freq<=8000) {
			valueToSet = 0x06; // 8000Hz
		} else {
			valueToSet = 0x02; // 500Hz
		}
		setEXGRateSetting(valueToSet);
		return mEXG1RateSetting;
	}
	
	private void internalCheckExgModeAndUpdateSensorMap(){
		if(mSensorMap!=null){
			if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
//				if((mIsExg1_24bitEnabled||mIsExg2_24bitEnabled||mIsExg1_16bitEnabled||mIsExg2_16bitEnabled)){
//				if((isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG1_16BIT))
//						||(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG2_16BIT))
//						||(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG1_24BIT))
//						||(isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.EXG2_24BIT))) {
					if(isEXGUsingDefaultRespirationConfiguration()) { // Do Respiration check first
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, true);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, false);
					}
					else if(isEXGUsingDefaultECGConfiguration()) {
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, true);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, false);
					}
					else if(isEXGUsingDefaultEMGConfiguration()) {
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, true);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, false);
					}
					else if(isEXGUsingDefaultTestSignalConfiguration()){
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, true);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, false);
					}
					else if(isEXGUsingDefaultThreeUnipolarConfiguration()){
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, true);
					}
					else if(isEXGUsingCustomSignalConfiguration()){
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, true);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, false);
						setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, false);
					}
					else {
						if (mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.HOST_ECG)!=null){
							setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, false);
							setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EMG, false);
							setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_TEST, false);
							setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_CUSTOM, false);
							setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_RESPIRATION, false);
							setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_EXG_THREE_UNIPOLAR, false);
						}
					}
//				}
			}
			else if(isShimmerGenGq()){
//				setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG).setIsEnabled(isEXGUsingDefaultECGGqConfiguration());
				setSensorEnabledStateBasic(Configuration.Shimmer3.SENSOR_ID.HOST_ECG, isEXGUsingDefaultECGGqConfiguration());
			}
		}
	}
	
	private void checkWhichExgRespPhaseValuesToUse(){
		String stringKey = SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE;
		if(mConfigOptionsMapSensors!=null){
			ConfigOptionDetails configOptions = mConfigOptionsMapSensors.get(stringKey);
			if(configOptions!=null){
				if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					int nonStandardIndex = -1;
					if(isExgRespirationDetectFreq32kHz()) {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_RESPIRATION_DETECT_PHASE.PHASE_32KHZ;
					}
					else {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_RESPIRATION_DETECT_PHASE.PHASE_64KHZ;
					}
					
					if(nonStandardIndex!=-1 && configOptions instanceof ConfigOptionDetailsSensor){
						((ConfigOptionDetailsSensor) configOptions).setIndexOfValuesToUse(nonStandardIndex);
					}
					
					// change config val if not appropriate
					Integer[] configvalues = configOptions.getConfigValues();
					if(!Arrays.asList(configvalues).contains(getEXG2RespirationDetectPhase())){
						setEXG2RespirationDetectPhase(configvalues[0]);
					}
					
				}
			}
		}
	}
	
	private void checkWhichExgRefElectrodeValuesToUse(){
		String stringKey = SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE;
		if(mConfigOptionsMapSensors!=null){
			ConfigOptionDetails configOptions = mConfigOptionsMapSensors.get(stringKey);
			if(configOptions!=null){
				if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
					int nonStandardIndex = -1;
					if(isEXGUsingDefaultRespirationConfiguration()) { // Do Respiration check first
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_REFERENCE_ELECTRODE.RESP;
					}
					else if(isEXGUsingDefaultECGConfiguration() || isEXGUsingDefaultECGGqConfiguration()) {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_REFERENCE_ELECTRODE.ECG;
					}
					else if(isEXGUsingDefaultEMGConfiguration()) {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_REFERENCE_ELECTRODE.EMG;
					}
					else if(isEXGUsingDefaultTestSignalConfiguration()) {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_REFERENCE_ELECTRODE.TEST;
					}
					else if(isEXGUsingDefaultThreeUnipolarConfiguration()) {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_REFERENCE_ELECTRODE.UNIPOLAR;
					}
					else {
						nonStandardIndex = ConfigOptionDetailsSensor.VALUE_INDEXES.EXG_REFERENCE_ELECTRODE.CUSTOM;
					}
					
					if(nonStandardIndex!=-1 && configOptions instanceof ConfigOptionDetailsSensor){
						((ConfigOptionDetailsSensor) configOptions).setIndexOfValuesToUse(nonStandardIndex);
					}

					// change config val if not appropriate		
					Integer[] configvalues = configOptions.getConfigValues();
//					Integer[] configvalues = configOptions.mConfigValues; // RM: Needed to add this line because getConfigValues() above was returning null
					
					if(!Arrays.asList(configvalues).contains(getEXGReferenceElectrode())){
						consolePrintErrLn("EXG Ref not supported: " + getEXGReferenceElectrode() + "\tChanging to: " + configvalues[0]);
						setEXGReferenceElectrode(configvalues[0]);
					}
				}
			}
		}
	}
	
	/**
	 * Checks if 16 bit ECG configuration is set on the Shimmer device. Do not
	 * use this command right after setting an EXG setting, as due to the
	 * execution model, the old settings might be returned, if this command is
	 * executed before an ack is received.
	 * 
	 * @return true if 16 bit ECG is set
	 */
	@Deprecated
	public boolean isEXGUsingECG16Configuration(){
		boolean using = false;
		if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0 && (mEnabledSensors & SENSOR_EXG2_16BIT)>0){
			if(isEXGUsingDefaultECGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 24 bit ECG configuration is set on the Shimmer device. Do not
	 * use this command right after setting an EXG setting, as due to the
	 * execution model, the old settings might be returned, if this command is
	 * executed before an ack is received.
	 * 
	 * @return true if 24 bit ECG is set
	 */
	@Deprecated
	public boolean isEXGUsingECG24Configuration(){
		boolean using = false;
		if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0 && (mEnabledSensors & SENSOR_EXG2_24BIT)>0){
			if(isEXGUsingDefaultECGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 16 bit EMG configuration is set on the Shimmer device. Do not
	 * use this command right after setting an EXG setting, as due to the
	 * execution model, the old settings might be returned, if this command is
	 * executed before an ack is received.
	 * 
	 * @return true if 16 bit EMG is set
	 */
	@Deprecated
	public boolean isEXGUsingEMG16Configuration(){
		boolean using = false;
		if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0 && (mEnabledSensors & SENSOR_EXG2_16BIT)>0){
			if(isEXGUsingDefaultEMGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 24 bit EMG configuration is set on the Shimmer device. Do not
	 * use this command right after setting an EXG setting, as due to the
	 * execution model, the old settings might be returned, if this command is
	 * executed before an ack is received.
	 * 
	 * @return true if 24 bit EMG is set
	 */
	@Deprecated
	public boolean isEXGUsingEMG24Configuration(){
		boolean using = false;
		if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0 && (mEnabledSensors & SENSOR_EXG2_24BIT)>0){
			if(isEXGUsingDefaultEMGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 16 bit test signal configuration is set on the Shimmer device.
	 * Do not use this command right after setting an EXG setting, as due to the
	 * execution model, the old settings might be returned, if this command is
	 * executed before an ack is received.
	 * 
	 * @return true if 24 bit test signal is set
	 */
	@Deprecated
	public boolean isEXGUsingTestSignal16Configuration(){
		boolean using = false;
		if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0 && (mEnabledSensors & SENSOR_EXG2_16BIT)>0){
			if(isEXGUsingDefaultTestSignalConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 24 bit test signal configuration is set on the Shimmer device.
	 * @return true if 24 bit test signal is set
	 */
	@Deprecated
	public boolean isEXGUsingTestSignal24Configuration(){
		boolean using = false;
		if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0 && (mEnabledSensors & SENSOR_EXG2_24BIT)>0){
			if(isEXGUsingDefaultTestSignalConfiguration()){
				using = true;
			}
		}
		return using;
	}	
	
	@Deprecated
	public String parseReferenceElectrodeTotring(int referenceElectrode){
		String refElectrode = "Unknown";

		if(referenceElectrode==0 && (isEXGUsingDefaultECGConfiguration() || isEXGUsingDefaultEMGConfiguration()))
			refElectrode = "Fixed Potential";
		else if(referenceElectrode==13 && isEXGUsingDefaultECGConfiguration())
			refElectrode = "Inverse Wilson CT";
		else if(referenceElectrode==3 && isEXGUsingDefaultEMGConfiguration())
			refElectrode = "Inverse of Ch1";

		return refElectrode;
	}

	@Deprecated
	public static String parseLeadOffComparatorTresholdToString(int treshold){

		String tresholdString="";
		switch(treshold){
		case 0:
			tresholdString = "Pos:95% - Neg:5%";
			break;
		case 1:
			tresholdString = "Pos:92.5% - Neg:7.5%";
			break;
		case 2:
			tresholdString = "Pos:90% - Neg:10%";
			break;
		case 3:
			tresholdString = "Pos:87.5% - Neg:12.5%";
			break;
		case 4:
			tresholdString = "Pos:85% - Neg:15%";
			break;
		case 5:
			tresholdString = "Pos:80% - Neg:20%";
			break;
		case 6:
			tresholdString = "Pos:75% - Neg:25%";
			break;
		case 7:
			tresholdString = "Pos:70% - Neg:30%";
			break;
		default:
			tresholdString = "Treshold unread";
			break;
		}

		return tresholdString;
	}

	@Deprecated
	public static String parseLeadOffModeToString(int leadOffMode){

		String modeString="";
		switch(leadOffMode){
		case 0:
			modeString +="Off";
			break;
		case 1:
			modeString +="DC Current";
			break;
		case 2:
			modeString +="AC Current";
			break;
		default:
			modeString +="No mode selected";
			break;
		}

		return modeString;
	}

	@Deprecated
	public static String parseLeadOffDetectionCurrentToString(int current){

		String currentString="";
		switch(current){
		case 0:
			currentString +="6 nA";
			break;
		case 1:
			currentString +="22 nA";
			break;
		case 2:
			currentString +="6 uA";
			break;
		default:
			currentString +="22 uA";
			break;
		}

		return currentString;
	}
	
	@Deprecated
	public int getReferenceElectrode(){
		return mEXGReferenceElectrode;
	}
	
	@Deprecated
	public int getLeadOffDetectionMode(){
		return mLeadOffDetectionMode;
	}
	
	@Deprecated
	public int getLeadOffDetectionCurrent(){
		return mEXGLeadOffDetectionCurrent;
	}
	
	@Deprecated
	public int getLeadOffComparatorTreshold(){
		return mEXGLeadOffComparatorTreshold;
	}
	
	@Deprecated
	public int getExGComparatorsChip1(){
		return mEXG1Comparators;
	}
	
	@Deprecated
	public int getExGComparatorsChip2(){
		return mEXG2Comparators;
	}

	//-------------------- ExG End -----------------------------------


	// ----------- KionixKXRB52042 - Analog Accelerometer start -----------------------------------
	public boolean isUsingValidLNAccelParam(){
		if(!UtilShimmer.isAllZeros(getAlignmentMatrixAccel()) && !UtilShimmer.isAllZeros(getSensitivityMatrixAccel())){
			return true;
		}else{
			return false;
		}
	}

	public double getCalibTimeAccel() {
		return getCurrentCalibDetailsAccelLn().getCalibTimeMs();
	}

	public boolean isUsingDefaultLNAccelParam(){
		return getCurrentCalibDetailsAccelLn().isUsingDefaultParameters();
	}
	
	public double[][] getAlignmentMatrixAccel(){
		return getCurrentCalibDetailsAccelLn().getValidAlignmentMatrix();
	}

	public double[][] getSensitivityMatrixAccel(){
		return getCurrentCalibDetailsAccelLn().getValidSensitivityMatrix();
	}

	public double[][] getOffsetVectorMatrixAccel(){
		return getCurrentCalibDetailsAccelLn().getValidOffsetVector();
	}
	
	/**
	 * Converts the Analog Accel calibration variables from Shimmer Object
	 * into a byte array for sending to the Shimmer.
	 * 
	 * @return the bytes array containing the Analog Accel Calibration
	 */
	public byte[] generateCalParamByteArrayAccelLn(){
//		return mCurrentCalibDetailsAccelLn.generateCalParamByteArray();
		return getCurrentCalibDetailsAccelLn().generateCalParamByteArray();
	}

	public void parseCalibParamFromPacketAccelAnalog(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		getCurrentCalibDetailsAccelLn().parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
//		if(USE_KIONIX_SENSOR_CLASS){
//			if(isShimmerGen2()){
//				mSensorMMA736x.parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
//			} else {
//				mSensorKionixAccel.parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
//			}
//		} else {
//			mCurrentCalibDetailsAccelLn.parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
//		}
	}

	// ----------- KionixKXRB52042 - Analog Accelerometer end -----------------------------------

	// ----------- LSM303 start -----------------------------------
	/**
	 * @param mHighResAccelWR the mHighResAccelWR to set
	 */
	public void setHighResAccelWR(boolean enable) {
		mSensorLSM303.setHighResAccelWR(enable);
	}
	
	public void setHighPerModeAccelWR(boolean enable) {
		mSensorLIS2DW12.setHighPerModeAccelWR(enable);
	}

	/**
	 * This enables the low power accel option. When not enabled the sampling
	 * rate of the accel is set to the closest value to the actual sampling rate
	 * that it can achieve. In low power mode it defaults to 10Hz. Also an
	 * additional low power mode is used for the LSM303DLHC. This command will
	 * only supports the following Accel range +4g, +8g , +16g   //TODO Where in the datasheet is this mentioned?
	 * 
	 * @param enable
	 */
	public void setLowPowerAccelWR(boolean enable){
		if (isShimmerGen3()) {
			mSensorLSM303.setLowPowerAccelWR(enable);
		} else if (isShimmerGen3R()) {
			mSensorLIS2DW12.setLowPowerAccelWR(enable);
		}
	}


	/**
	 * This enables the low power mag option. When not enabled the sampling rate
	 * of the mag is set to the closest supported value to the actual sampling
	 * rate that it can achieve. In low power mode it defaults to 10Hz
	 * 
	 * @param enable
	 */
	protected void setLowPowerMag(boolean enable){
		if(isShimmerGen2()){
			mSensorShimmer2Mag.setLowPowerMag(enable);
		} else if(isShimmerGen3()) {
			mSensorLSM303.setLowPowerMag(enable);
		}
		else if(isShimmerGen3R()){
			mSensorLIS3MDL.setLowPowerMag(enable);
		}
	}
	
	protected void setMedPowerMag(boolean enable) {
		mSensorLIS3MDL.setLowPowerMag(enable);
	}
	
	protected void setHighPowerMag(boolean enable) {
		mSensorLIS3MDL.setLowPowerMag(enable);
	}
	
	protected void setUltraHighPowerMag(boolean enable) {
		mSensorLIS3MDL.setLowPowerMag(enable);
	}

	private void setShimmer2rMagRateFromFreq(double samplingRateShimmer) {
		mSensorShimmer2Mag.setShimmer2rMagRateFromFreq(samplingRateShimmer);
	}

	private void setShimmer2rMagRate(int magRate) {
		if(isShimmerGen2()){
			mSensorShimmer2Mag.setShimmer2rMagRate(magRate);
		}
	}

    public boolean isLowPowerMagEnabled(){
		if(isShimmerGen2()){
			return mSensorShimmer2Mag.isLowPowerMagEnabled();
		} else if(isShimmerGen3()){
			return mSensorLSM303.isLowPowerMagEnabled();
		} else if(isShimmerGen3R()) {
			return mSensorLIS3MDL.isLowPowerMagEnabled();
		}
		return false;
	} 
    
    public boolean isMedPowerMagEnabled() {
    	return mSensorLIS3MDL.isMedPowerMagEnabled();
    }
    
    public boolean isHighPowerMagEnabled() {
    	return mSensorLIS3MDL.isMedPowerMagEnabled();
    }
    
    public boolean isUltraHighPowerMagEnabled() {
    	return mSensorLIS3MDL.isMedPowerMagEnabled();
    }

	public void setAccelRange(int i){
		setDigitalAccelRange(i);
	}
	
	public void setDigitalAccelRate(int i) {
		if(isShimmerGen3()) {
			mSensorLSM303.setLSM303DigitalAccelRate(i);
		} else if(isShimmerGen3R()) {
			mSensorLIS2DW12.setLIS2DW12DigitalAccelRate(i);
		}
	}
	
	public void setDigitalAccelRange(int i){
		if(isShimmerGen2()) {
			mSensorMMA736x.setAccelRange(i);
		} else if(isShimmerGen3()) {
			mSensorLSM303.setLSM303AccelRange(i);
		} else if(isShimmerGen3R()) {
			mSensorLIS2DW12.setLIS2DW12AccelRange(i);
		}
	}
	
	/**
	 * @param mLSM303DigitalAccelRate the mLSM303DigitalAccelRate to set
	 */
	public void setLSM303DigitalAccelRate(int mLSM303DigitalAccelRate) {
		if(isShimmerGen3()) {
			mSensorLSM303.setLSM303DigitalAccelRate(mLSM303DigitalAccelRate);
		}
	}
	
	public void setLIS2DW12DigitalAccelRate(int mLIS2DW12DigitalAccelRate) {
		if(isShimmerGen3R()) {
			mSensorLIS2DW12.setLIS2DW12DigitalAccelRate(mLIS2DW12DigitalAccelRate);
		}
	}
	
	public void setAltMagRange(int i){
		mSensorLIS3MDL.setLIS3MDLAltMagRange(i);
	}
	
	public void setAltMagRate(int i){
		mSensorLIS3MDL.setLIS3MDLAltMagRate(i);
	}

	public void setMagRange(int i){
		if(isShimmerGen3()) {
			mSensorLSM303.setLSM303MagRange(i);
		} else if(isShimmerGen3R()) {
			mSensorLIS2MDL.setLISMagRange(i);
		}
	}

	/** Use setMagRange() instead
	 * @param i
	 */
	@Deprecated
	public void setLSM303MagRange(int i){
		if(isShimmerGen3()) {
			mSensorLSM303.setLSM303MagRange(i);
		} else if(isShimmerGen3R()) {
			mSensorLIS2MDL.setLISMagRange(i);
		}
	}

	/**
	 * Computes next higher available sensor sampling rate setting based on
	 * passed in "freq" variable and dependent on whether low-power mode is set.
	 * 
	 * @param freq
	 * @return int the rate configuration setting for the respective sensor
	 */
	private int setLSM303MagRateFromFreq(double freq) {
		if(isShimmerGen3R()) {
			return mSensorLIS2MDL.setLIS2MDLAltMagRateFromFreq(freq);
		} else{
			return mSensorLSM303.setLSM303MagRateFromFreq(freq);
		}
	}

	public void setMagRate(int magRate) {
		if(isShimmerGen2()){
			mSensorShimmer2Mag.setShimmer2rMagRate(magRate);
		} else if (isShimmerGen3()){
			mSensorLSM303.setLSM303MagRate(magRate);
		} else if (isShimmerGen3R()) {
			mSensorLIS2MDL.setLISMagRateInternal(magRate);
		}
	}
	
	public int getWRAccelRate() throws Exception {
		
			if(isShimmerGen2()){
				throw new Exception("WR Accel not configurable for Shimmer2");
			} else if(isShimmerGen3()) {
				return mSensorLSM303.getLSM303DigitalAccelRate();
			} else if(isShimmerGen3R()) {
				return mSensorLIS2DW12.getLIS2DW12DigitalAccelRate();
			}
			return (Integer) null;
	}
	
	public int getGyroRate() throws Exception {
		if(isShimmerGen2()){
			throw new Exception("Gyro Rate is not configurable for Shimmer2");
		} else if (isShimmerGen3()) {
			return mSensorMpu9x50.getMPU9X50GyroAccelRate();
		} else if (isShimmerGen3R()) {
			return mSensorLSM6DSV.getLSM6DSVGyroAccelRate();
		}
		return (Integer) null;
	}

	public int getMagRate() {
		if(isShimmerGen2()){
			return mSensorShimmer2Mag.getMagRate();
		} else if (isShimmerGen3()){
			return mSensorLSM303.getLSM303MagRate();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2MDL.getLIS2MDLMagRate();
		}
		return 0;
	}
	
	/**
	 * Computes next higher available sensor sampling rate setting based on
	 * passed in "freq" variable and dependent on whether low-power mode is set.
	 * 
	 * @param freq
	 * @return int the rate configuration setting for the respective sensor
	 */
	private int setLSM303AccelRateFromFreq(double freq) {
		return mSensorLSM303.setLSM303AccelRateFromFreq(freq);
	}
	
	private int setLIS2DW12AccelRateFromFreq(double freq) {
		return mSensorLIS2DW12.setLIS2DW12AccelRateFromFreq(freq);
	}
	
	private void setDefaultLsm303AccelSensorConfig(boolean state) {
		mSensorLSM303.setDefaultLsm303AccelSensorConfig(state);
	}
	
	private void setDefaultLIS2DW12AccelSensorConfig(boolean state) {
		mSensorLIS2DW12.setDefaultLIS2DW12AccelSensorConfig(state);
	}

	private void setDefaultLsm303MagSensorConfig(boolean state) {
		mSensorLSM303.setDefaultLsm303MagSensorConfig(state);
	}

	/**
	 * @return the mHighResAccelWR
	 */
	public boolean isHighResAccelWR() {
		return mSensorLSM303.isHighResAccelWR();
	}
	
	public boolean isHighPerModeAccelWR() {
		return mSensorLIS2DW12.isHighPerModeAccelWR();
	}

	public void setHighResAccelWR(int i){
		mSensorLSM303.setHighResAccelWR(i);
	}
	
	public void setHighPerModeAccelWR(int i) {
		mSensorLIS2DW12.setHighPerModeAccelWR(i);
	}
	
	/**
	 * @return the mLSM303DigitalAccelHRM
	 */
	public boolean isLSM303DigitalAccelHRM() {
		return mSensorLSM303.isLSM303DigitalAccelHRM();
	}
	
	public boolean isLIS2DW12DigitalAccelHPM() {
		return mSensorLIS2DW12.isLIS2DW12DigitalAccelHPM();
	}
	
	public int getHighResAccelWREnabled(){
		return mSensorLSM303.getHighResAccelWREnabled();
	}
	
	public int getHighPerModeAccelWREnabled() {
		return mSensorLIS2DW12.getHighPerModeAccelWREnabled();
	}

	public double getCalibTimeWRAccel() {
		if (isShimmerGen3()) {
			return mSensorLSM303.getCalibTimeWRAccel();
		}
		else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.getCalibTimeWRAccel();
		}
		return (Double) null;
	}

	public boolean isUsingDefaultWRAccelParam(){
		if (isShimmerGen3()) {
			return mSensorLSM303.isUsingDefaultWRAccelParam();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.isUsingDefaultWRAccelParam();
		}
		return (Boolean) null;
	}

	public boolean isUsingValidWRAccelParam(){
		if (isShimmerGen3()) {
			return mSensorLSM303.isUsingValidWRAccelParam();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.isUsingValidWRAccelParam();
		}
		return (Boolean) null;
	}
	
	public double getCalibTimeMag() {
		if(isShimmerGen3R()) {
			return mSensorLIS2MDL.getCalibTimeMag();
		} else {
			return mSensorLSM303.getCalibTimeMag();
		}
	}
	
	public boolean isUsingDefaultMagParam(){
		if(isShimmerGen3R()) {
			return mSensorLIS2MDL.isUsingDefaultMagParam();
		} else {
			return mSensorLSM303.isUsingDefaultMagParam();
		}
	}
	
	public boolean isUsingValidMagParam(){
		if (isShimmerGen3R()) {
			return mSensorLIS2MDL.isUsingValidMagParam();
		} else {
			return mSensorLSM303.isUsingValidMagParam();
		}
	}
	
	public boolean isUsingDefaultMagAltParam() {
		return mSensorLIS3MDL.isUsingDefaultMagAltParam();
	}
	
	public boolean isUsingValidMagAltParam() {
		return mSensorLIS3MDL.isUsingValidMagAltParam();
	}
	
	/**
	 * @return the mLowPowerAccelWR
	 */
	public boolean isLowPowerAccelWR() {
		if (isShimmerGen3()) {
			return mSensorLSM303.isLowPowerAccelWR();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.isLowPowerAccelWR();
		}
		return (Boolean) null;
	}

	/**
	 * @return the mLSM303DigitalAccelLPM
	 */
	public boolean isLSM303DigitalAccelLPM() {
		return mSensorLSM303.isLSM303DigitalAccelLPM();
	}
	
	public boolean isLIS2DW12DigitalAccelLPM() {
		return mSensorLIS2DW12.isLIS2DW12DigitalAccelLPM();
	}

	public boolean isLowPowerAccelEnabled() {
		if (isShimmerGen3()) {
			return mSensorLSM303.isLowPowerAccelEnabled();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.isLowPowerAccelEnabled();
		}
		return (Boolean) null;
	}

	public void setLowPowerAccelEnabled(int i){
		if (isShimmerGen3()) {
			mSensorLSM303.setLowPowerAccelEnabled(i);
		} else if (isShimmerGen3R()) {
			mSensorLIS2DW12.setLowPowerAccelEnabled(i);
		}
	}

	public int getLowPowerAccelEnabled(){
		if (isShimmerGen3()) {
			return mSensorLSM303.getLowPowerAccelEnabled();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.getLowPowerAccelEnabled();
		}
		return (Integer) null;
	}

	public int getLowPowerMagEnabled() {
		if (isShimmerGen3R()) {
			return mSensorLIS2MDL.getLowPowerMagEnabled();
		} else {
			return mSensorLSM303.getLowPowerMagEnabled();
		}
	}
	
	public int getMedPowerMagEnabled() {
		return mSensorLIS3MDL.getMedPowerMagEnabled();
	}
	
	public int getHighPowerMagEnabled() {
		return mSensorLIS3MDL.getHighPowerMagEnabled();
	}
	
	public int getUltraHighPowerMagEnabled() {
		return mSensorLIS3MDL.getUltraHighPowerMagEnabled();
	}

	/** Shimmer3 -> 0 = +/-2g, 1 = +/-4g, 2 = +/-8g, 3 = +/- 16g */
	public int getAccelRange(){
		if(isShimmerGen2()){
			return mSensorMMA736x.getAccelRange();
		} else if(isShimmerGen3()) {
			return mSensorLSM303.getAccelRange();
		} else if(isShimmerGen3R()) {
			return mSensorLIS2DW12.getAccelRange();
		}
		return (Integer) null;
	}

	public int getMagRange(){
		if(isShimmerGen2()){
			return mSensorShimmer2Mag.getMagRange();
		} else if (isShimmerGen3()){
			return mSensorLSM303.getMagRange();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2MDL.getMagRange();
		}
		return 0;
	}

	/**
	 * @return the mLSM303DigitalAccelRate
	 */
	public int getLSM303DigitalAccelRate() {
		return mSensorLSM303.getLSM303DigitalAccelRate();
	}
	
	public int getLIS2DW12DigitalAccelRate() {
		return mSensorLIS2DW12.getLIS2DW12DigitalAccelRate();
	}

	public double[][] getAlignmentMatrixWRAccel(){
		if (isShimmerGen3()) {
			return mSensorLSM303.getAlignmentMatrixWRAccel();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.getAlignmentMatrixWRAccel();
		}
		return null;
	}

	public double[][] getSensitivityMatrixWRAccel(){
		if (isShimmerGen3()) {
			return mSensorLSM303.getSensitivityMatrixWRAccel();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.getSensitivityMatrixWRAccel();
		}
		return null;
	}

	public double[][] getOffsetVectorMatrixWRAccel(){
		if (isShimmerGen3()) {
			return mSensorLSM303.getOffsetVectorMatrixWRAccel();
		} else if (isShimmerGen3R()) {
			return mSensorLIS2DW12.getOffsetVectorMatrixWRAccel();
		}
		return null;
	}

	public double[][] getAlignmentMatrixMag(){
		if (isShimmerGen3R()) {
			return mSensorLIS2MDL.getAlignmentMatrixMag();
		} else {
			return mSensorLSM303.getAlignmentMatrixMag();
		}
	}

	public double[][] getSensitivityMatrixMag(){
		if (isShimmerGen3R()) {
			return mSensorLIS2MDL.getSensitivityMatrixMag();
		} else {
			return mSensorLSM303.getSensitivityMatrixMag();
		}
	}

	public double[][] getOffsetVectorMatrixMag(){
		if (isShimmerGen3R()) {
			return mSensorLIS2MDL.getOffsetVectorMatrixMag();
		} else {
			return mSensorLSM303.getOffsetVectorMatrixMag();
		}
	}
	
	public double[][] getOffsetVectorMatrixHighGAccel(){
		if (isShimmerGen3R()) {
			return mSensorADXL371.getOffsetVectorMatrixHighGAccel();
		}
		return null; 
	}
	
	public double[][] getAlignmentMatrixHighGAccel(){
		if (isShimmerGen3R()) {
			return mSensorADXL371.getAlignmentMatrixHighGAccel();
		}
		return null; 
	}
	
	public double[][] getSensitivityMatrixHighGAccel(){
		if (isShimmerGen3R()) {
			return mSensorADXL371.getSensitivityMatrixHighGAccel();
		}
		return null; 
	}
	
	public double[][] getOffsetVectorMatrixAltMag(){
		if (isShimmerGen3R()) {
			return mSensorLIS3MDL.getOffsetVectorMatrixMagAlt();
		}
		return null; 
	}
	
	public double[][] getAlignmentMatrixAltMag(){
		if (isShimmerGen3R()) {
			return mSensorLIS3MDL.getAlignmentMatrixMagAlt();
		}
		return null; 
	}
	
	public double[][] getSensitivityMatrixAltMag(){
		if (isShimmerGen3R()) {
			return mSensorLIS3MDL.getSensitivityMatrixMagAlt();
		}
		return null; 
	}

	/** Only GQ BLE
	 * @return the mSamplingDividerLsm303dlhcAccel
	 */
	public int getSamplingDividerLsm303dlhcAccel() {
		return mSamplingDividerLsm303dlhcAccel;
	}

	// ----------- LSM303 end -----------------------------------
	
	// ----------- MPU9X50 options start -------------------------
	public byte[] getGyroCalRawParams(){
		if(isShimmerGen2()){
			return mSensorShimmer2Gyro.mCurrentCalibDetailsGyro.generateCalParamByteArray();
		} else if (isShimmerGen3()){
			return mSensorMpu9x50.mCurrentCalibDetailsGyro.generateCalParamByteArray();
		} else if (isShimmerGen3R()) {
			return mSensorLSM6DSV.mCurrentCalibDetailsGyro.generateCalParamByteArray();
		}
		return null;
	}

	public void updateCurrentGyroCalibInUse(){
		if (isShimmerGen2()){
			mSensorShimmer2Gyro.updateCurrentCalibInUse();
		}
		else if (isShimmerGen3()){
			mSensorMpu9x50.updateCurrentGyroCalibInUse();
		}
		else if (isShimmerGen3R()) {
			mSensorLSM6DSV.updateCurrentGyroCalibInUse();
		}
	}

	/**
	 * @param enable this enables the calibration of the gyroscope while streaming
	 * @param bufferSize sets the buffersize of the window used to determine the new calibration parameters, see implementation for more details
	 * @param threshold sets the threshold of when to use the incoming data to recalibrate gyroscope offset, this is in degrees, and the default value is 1.2
	 */
	public void enableOnTheFlyGyroCal(boolean state, int bufferSize, double threshold){
		if(isShimmerGen2()){
			mSensorShimmer2Gyro.enableOnTheFlyGyroCal(state, bufferSize, threshold);
		} else {
//			mSensorMpu9x50.enableOnTheFlyGyroCal(state, bufferSize, threshold);
			AbstractAlgorithm abstractAlgorithm = getAlgorithmModule(GyroOnTheFlyCalModule.GENERAL_ALGORITHM_NAME);
			if(abstractAlgorithm!=null) {
				setIsAlgorithmEnabled(GyroOnTheFlyCalModule.GENERAL_ALGORITHM_NAME, state);
				GyroOnTheFlyCalModule gyroOnTheFlyCalModule = (GyroOnTheFlyCalModule) abstractAlgorithm;
				gyroOnTheFlyCalModule.enableOnTheFlyGyroCal(state, bufferSize, threshold);
			}
		}
	}

	public void setOnTheFlyGyroCal(boolean state){
		if(isShimmerGen2()){
			mSensorShimmer2Gyro.setOnTheFlyGyroCal(state);
		} else {
//			mSensorMpu9x50.setOnTheFlyGyroCal(state);
			setIsAlgorithmEnabled(GyroOnTheFlyCalModule.GENERAL_ALGORITHM_NAME, state);
		}
	}

    public boolean isGyroOnTheFlyCalEnabled(){
		if(isShimmerGen2()){
			return mSensorShimmer2Gyro.isGyroOnTheFlyCalEnabled();
		} else {
//			return mSensorMpu9x50.isGyroOnTheFlyCalEnabled();
			return isAlgorithmEnabled(GyroOnTheFlyCalModule.GENERAL_ALGORITHM_NAME);
		}
	}
    
    private OnTheFlyGyroOffsetCal getOnTheFlyCalGyro(){
		if(isShimmerGen2()){
			return mSensorShimmer2Gyro.getOnTheFlyCalGyro();
		} else {
//			return mSensorMpu9x50.getOnTheFlyCalGyro();
			return null;
		}
    }

	public CalibDetailsKinematic getCurrentCalibDetailsGyro() {
		if (isShimmerGen2()){
			return mSensorShimmer2Gyro.getCurrentCalibDetailsGyro();
		} else if (isShimmerGen3() || isShimmerGenGq()){ //GQ FOR LEGACY SUPPORT
			return mSensorMpu9x50.getCurrentCalibDetailsGyro();
		} else if (isShimmerGen3R()) {
			return mSensorLSM6DSV.getCurrentCalibDetailsGyro();
		}
		return null;
	}
	
	/**
	 * Converts the MPU9150 Gyroscope calibration variables from Shimmer Object
	 * into a byte array for sending to the Shimmer.
	 * 
	 * @return the bytes array containing the Gyroscope Calibration
	 */
	public byte[] generateCalParamGyroscope(){
		return getCurrentCalibDetailsGyro().generateCalParamByteArray();
	}

	public void parseCalibParamFromPacketGyro(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		getCurrentCalibDetailsGyro().parseCalParamByteArray(bufferCalibrationParameters, calibReadSource);
	}

	private void setDefaultCalibrationShimmer3Gyro() {
		getCurrentCalibDetailsGyro().resetToDefaultParameters();
	}

	
	/**
	 * Computes next higher available sensor sampling rate setting based on
	 * passed in "freq" variable and dependent on whether low-power mode is set.
	 * 
	 * @param freq
	 * @return int the rate configuration setting for the respective sensor
	 */
	public int setMPU9150GyroAccelRateFromFreq(double freq) {
		return mSensorMpu9x50.setMPU9150GyroAccelRateFromFreq(freq);
	}
	
	/**
	 * Computes next higher available sensor sampling rate setting based on
	 * passed in "freq" variable and dependent on whether low-power mode is set.
	 * 
	 * @param freq
	 * @return int the rate configuration setting for the respective sensor
	 */
	private int setMPU9150MagRateFromFreq(double freq) {
		return mSensorMpu9x50.setMPU9150MagRateFromFreq(freq);
	}
	
	/**
	 * Computes next higher available sensor sampling rate setting based on
	 * passed in "freq" variable and dependent on whether low-power mode is set.
	 * 
	 * @param freq
	 * @return int the rate configuration setting for the respective sensor
	 */
	private int setMPU9150MplRateFromFreq(double freq) {
		return mSensorMpu9x50.setMPU9150MplRateFromFreq(freq);
	}

	private void setDefaultMpu9150GyroSensorConfig(boolean state) {
		mSensorMpu9x50.setDefaultMpu9150GyroSensorConfig(state);
	}
	
	//TODO check why is the Gyro sensorId is referenced here
	private void setDefaultMpu9150AccelSensorConfig(boolean state) {
		mSensorMpu9x50.setDefaultMpu9150AccelSensorConfig(state);
	}
	
	private void setDefaultMpu9150MplSensorConfig(boolean isSensorEnabled) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setDefaultMpu9150MplSensorConfig(isSensorEnabled);
		}
	}
	
	private boolean checkIfAMpuGyroOrAccelEnabled(){
		if(mSensorMpu9x50!=null){
			return mSensorMpu9x50.checkIfAMpuGyroOrAccelEnabled();
		}
		return false;
	}	

	private boolean checkIfAnyOtherMplChannelEnabled(int sensorId){
		if(mSensorMpu9x50!=null){
			return mSensorMpu9x50.checkIfAnyOtherMplChannelEnabled(sensorId);
		}
		return false;
	}
			
	public boolean checkIfAnyMplChannelEnabled(){
		if(mSensorMpu9x50!=null){
			return mSensorMpu9x50.checkIfAnyMplChannelEnabled();
		}
		return false;
	}

	/**
	 * @return the mMPU9150GyroAccelRate
	 */
	public int getMPU9X50GyroAccelRate() {
		return mSensorMpu9x50.getMPU9X50GyroAccelRate();
	}

	public void setMPU9150GyroAccelRate(int rate) {
		mSensorMpu9x50.setMPU9X50GyroAccelRate(rate);
	}
	
	public int getLSM6DSVGyroAccelRate() {
		return mSensorLSM6DSV.getLSM6DSVGyroAccelRate();
	}

	public void setLSM6DSVGyroAccelRate(int rate) {
		mSensorLSM6DSV.setLSM6DSVGyroAccelRate(rate);
	}

	/**
	 * @return the mMPU9150MotCalCfg
	 */
	public int getMPU9X50MotCalCfg() {
		return mSensorMpu9x50.getMPU9X50MotCalCfg();
	}
	
	/**
	 * @return the mMPU9150LPF
	 */
	public int getMPU9X50LPF() {
		return mSensorMpu9x50.getMPU9X50LPF();
	}
	
	public int getMPU9X50DMP() {
		return mSensorMpu9x50.getMPU9X50DMP();
	}

	/**
	 * @return the mMPU9150MPLSamplingRate
	 */
	public int getMPU9X50MPLSamplingRate() {
		return mSensorMpu9x50.getMPU9X50MPLSamplingRate();
	}

	/**
	 * @return the mMPU9150MagSamplingRate
	 */
	public int getMPU9X50MagSamplingRate() {
		return mSensorMpu9x50.getMPU9X50MagSamplingRate();
	}
	
	/**
	 * @return the mMPU9150GyroAccelRate in Hz
	 */
	public double getMPU9X50GyroAccelRateInHz() {
		return mSensorMpu9x50.getMPU9X50GyroAccelRateInHz();
	}
	
	/**
	 * @param mMPU9150AccelRange the mMPU9150AccelRange to set
	 */
	public void setMPU9150AccelRange(int i) {
		mSensorMpu9x50.setMPU9X50AccelRange(i);
	}

	/**
	 * @return the mMPU9150AccelRange
	 */
	public int getMPU9X50AccelRange() {
		return mSensorMpu9x50.getMPU9X50AccelRange();
	}
	
	public int getGyroRange(){
		if(isShimmerGen2()){
			return mSensorShimmer2Gyro.getGyroRange();
		} else if (isShimmerGen3()) {
			return mSensorMpu9x50.getGyroRange();
		} else if (isShimmerGen3R()) {
			return mSensorLSM6DSV.getGyroRange();
		}
		return (Integer) null;
	}
	
	public void setGyroRange(int newRange) {
		if(isShimmerGen2()){
			mSensorShimmer2Gyro.setGyroRange(newRange);
		} else if (isShimmerGen3()){
			mSensorMpu9x50.setGyroRange(newRange);
		} else if (isShimmerGen3R()){
			mSensorLSM6DSV.setGyroRange(newRange);
		}
	}

	protected void setMPU9150GyroRange(int i){
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9150GyroRange(i);
		}
	}
	
	protected void setLSM6DSVGyroRange(int i){
		if(mSensorLSM6DSV!=null){
			mSensorLSM6DSV.setLSM6DSVGyroRange(i);
		}
	}

	/**
	 * @param mMPU9150MPLSamplingRate the mMPU9150MPLSamplingRate to set
	 */
	public void setMPU9150MPLSamplingRate(int mMPU9150MPLSamplingRate) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9150MPLSamplingRate(mMPU9150MPLSamplingRate);
		}
	}

	/**
	 * @param mMPU9150MagSamplingRate the mMPU9150MagSamplingRate to set
	 */
	public void setMPU9150MagSamplingRate(int mMPU9150MagSamplingRate) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9X50MagSamplingRate(mMPU9150MagSamplingRate);
		}
	}
	
	// MPL options
	/**
	 * @return the mMPU9150DMP
	 */
	public boolean isMPU9150DMP() {
		return mSensorMpu9x50.isMPU9150DMP();
	}

	/**
	 * @param state the mMPU9150DMP state to set
	 */
	public void setMPU9150DMP(boolean state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9150DMP(state);
		}
	}

	public void setMPU9150DMP(int i) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9150DMP(i);
		}
	}

	public int getMPLEnable() {
		return mSensorMpu9x50.getMPLEnable();
	}

	/**
	 * @return the mMPLEnable
	 */
	public boolean isMPLEnabled() {
		return mSensorMpu9x50.isMPLEnable();
	}
	
	/**
	 * @param state the mMPLEnable state to set
	 */
	public void setMPLEnabled(boolean state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLEnabled(state);
		}
	}

	/**
	 * @param state the mMPLEnable state to set
	 */
	public void setMPLEnabled(int state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLEnabled(state);
		}
	}

	/**
	 * @param state the mMPLSensorFusion state to set
	 */
	public void setMPLSensorFusion(boolean state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLSensorFusion(state);
		}
	}

	public void setMPLSensorFusion(int state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLSensorFusion(state);
		}
	}

	public int getMPLGyroCalTC() {
		return mSensorMpu9x50.getMPLGyroCalTC();
	}

	/**
	 * @return the mMPLGyroCalTC
	 */
	public boolean isMPLGyroCalTC() {
		return mSensorMpu9x50.isMPLGyroCalTC();
	}
	
	/**
	 * @param state the mMPLGyroCalTC state to set
	 */
	public void setMPLGyroCalTC(boolean state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLGyroCalTC(state);
		}
	}

	public void setMPLGyroCalTC(int state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLGyroCalTC(state);
		}
	}

	public int getMPLVectCompCal() {
		return mSensorMpu9x50.getMPLVectCompCal();
	}

	/**
	 * @return the mMPLVectCompCal
	 */
	public boolean isMPLVectCompCal() {
		return mSensorMpu9x50.isMPLVectCompCal();
	}

	/**
	 * @param state the mMPLVectCompCal state to set
	 */
	public void setMPLVectCompCal(boolean state) {
		mSensorMpu9x50.setMPLVectCompCal(state);
	}

	/**
	 * @param state the mMPLVectCompCal state to set
	 */
	public void setMPLVectCompCal(int state) {
		mSensorMpu9x50.setMPLVectCompCal(state);
	}

	public int getMPLMagDistCal() {
		return mSensorMpu9x50.getMPLMagDistCal();
	}

	/**
	 * @return the mMPLMagDistCal
	 */
	public boolean isMPLMagDistCal() {
		return mSensorMpu9x50.isMPLMagDistCal();
	}
	
	/**
	 * @param state the mMPLMagDistCal state to set
	 */
	public void setMPLMagDistCal(boolean state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLMagDistCal(state);
		}
	}

	/**
	 * @param state the mMPLMagDistCal state to set
	 */
	public void setMPLMagDistCal(int state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPLMagDistCal(state);
		}
	}

	public int getMPLSensorFusion() {
		return mSensorMpu9x50.getMPLSensorFusion();
	}

	/**
	 * @return the mMPLSensorFusion
	 */
	public boolean isMPLSensorFusion() {
		return mSensorMpu9x50.isMPLSensorFusion();
	}

	/**
	 * @param mMPU9150MotCalCfg the mMPU9150MotCalCfg to set
	 */
	public void setMPU9150MotCalCfg(boolean state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9150MotCalCfg(state);
		}
	}

	/**
	 * @param mMPU9150MotCalCfg the mMPU9150MotCalCfg to set
	 */
	public void setMPU9150MotCalCfg(int state) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9X150MotCalCfg(state);
		}
	}

	/**
	 * @param mMPU9150LPF the mMPU9150LPF to set
	 */
	public void setMPU9150LPF(int mMPU9150LPF) {
		if(mSensorMpu9x50!=null){
			mSensorMpu9x50.setMPU9X50LPF(mMPU9150LPF);
		}
	}

	public double getCalibTimeGyro() {
		return getCurrentCalibDetailsGyro().getCalibTimeMs();
	}
    
	public double[][] getAlignmentMatrixGyro(){
		return getCurrentCalibDetailsGyro().getValidAlignmentMatrix();
	}

	public double[][] getSensitivityMatrixGyro(){
		return getCurrentCalibDetailsGyro().getValidSensitivityMatrix();
	}

	public double[][] getOffsetVectorMatrixGyro(){
		return getCurrentCalibDetailsGyro().getValidOffsetVector();
	}
	
	public double[][] getOffsetVectorMPLAccel(){
		return mSensorMpu9x50.getOffsetVectorMPLAccel();
	}
	
	public double[][] getSensitivityMatrixMPLAccel(){
		return mSensorMpu9x50.getSensitivityMatrixMPLAccel();
	}
	
	public double[][] getAlignmentMatrixMPLAccel(){
		return mSensorMpu9x50.getAlignmentMatrixMPLAccel();
	}
	
	public double[][] getOffsetVectorMPLMag(){
		return mSensorMpu9x50.getOffsetVectorMPLMag();
	}
	
	public double[][] getSensitivityMatrixMPLMag(){
		return mSensorMpu9x50.getSensitivityMatrixMPLMag();
	}
	
	public double[][] getAlignmentMatrixMPLMag(){
		return mSensorMpu9x50.getAlignmentMatrixMPLMag();
	}
	
	public double[][] getOffsetVectorMPLGyro(){
		return mSensorMpu9x50.getOffsetVectorMPLGyro();
	}
	
	public double[][] getSensitivityMatrixMPLGyro(){
		return mSensorMpu9x50.getSensitivityMatrixMPLGyro();
	}
	
	public double[][] getAlignmentMatrixMPLGyro(){
		return mSensorMpu9x50.getAlignmentMatrixMPLGyro();
	}

	public boolean isLowPowerGyroEnabled() {
		if (isShimmerGen3()) {
			return mSensorMpu9x50.isLowPowerGyroEnabled();
		}
		else if (isShimmerGen3R()) {
			return mSensorLSM6DSV.isLowPowerGyroEnabled();
		}
		return (Boolean) null;
	}
	
	public boolean isUsingDefaultGyroParam(){
		return getCurrentCalibDetailsGyro().isUsingDefaultParameters();
	}
	
	public boolean isUsingValidGyroParam(){
		if(!UtilShimmer.isAllZeros(getAlignmentMatrixGyro()) && !UtilShimmer.isAllZeros(getSensitivityMatrixGyro())){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * This enables the low-power gyro option. When not enabled the sampling
	 * rate of the gyro is set to the closest supported value to the actual
	 * sampling rate that it can achieve. For the Shimmer2, in low power mode it
	 * defaults to 10Hz.
	 * 
	 * @param enable
	 */
	protected void setLowPowerGyro(boolean enable){
		if(mShimmerVerObject.isShimmerGen2()){
			mSensorShimmer2Gyro.setLowPowerGyro(enable);
		}
		else if(mShimmerVerObject.isShimmerGen3()){
			mSensorMpu9x50.setLowPowerGyro(enable);
		}
		else if(mShimmerVerObject.isShimmerGen3R()) {
			mSensorLSM6DSV.setLowPowerGyro(enable);
		}
	}
	
	/**
	 * Checks to see if the MPU9150 gyro is in low power mode. As determined by
	 * the sensor's sampling rate being set to the lowest possible value and not
	 * related to any specific configuration bytes sent to the Shimmer/MPU9150.
	 * 
	 * @return boolean, true if low-power mode enabled
	 */
	public boolean checkLowPowerGyro() {
		if(mShimmerVerObject.isShimmerGen2()){
			return mSensorShimmer2Gyro.checkLowPowerGyro();
		}
		else if(mShimmerVerObject.isShimmerGen3()){
			return mSensorMpu9x50.checkLowPowerGyro();
		}
		else if (mShimmerVerObject.isShimmerGen3R()) {
			return mSensorLSM6DSV.checkLowPowerGyro();
		}
		return false;
	}

	public int getLowPowerGyroEnabled() {
		if(mShimmerVerObject.isShimmerGen2()){
			return mSensorShimmer2Gyro.getLowPowerGyroEnabled();
		}
		else if(mShimmerVerObject.isShimmerGen3()){
			return mSensorMpu9x50.getLowPowerGyroEnabled();
		}
		else if (mShimmerVerObject.isShimmerGen3R()) {
			return mSensorLSM6DSV.getLowPowerGyroEnabled();
		}
		return 0;
	}
	

	
	// ----------- MPU9X50 options end -------------------------
	
	@Override
	public Object getConfigValueUsingConfigLabel(String identifier, String configLabel) {
		Object returnValue = super.getConfigValueUsingConfigLabel(identifier, configLabel);
    	if(returnValue!=null){
    		return returnValue;
    	}
		
        if((configLabel.equals(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE))
        		|| (configLabel.equals(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE))){
        	checkConfigOptionValues(configLabel);
        }
        
		Integer sensorId = Configuration.Shimmer3.SENSOR_ID.RESERVED_ANY_SENSOR;
		try{
			sensorId = Integer.parseInt(identifier);
		} catch (NumberFormatException nFE){
			//Do nothing
		}

		switch(configLabel){
			case(Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START):
				returnValue = isButtonStart();
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.SD_STREAM_WHEN_RECORDING):
				returnValue = isDisableBluetooth();
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START):
				returnValue = isSingleTouch();
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_RTC):
				returnValue = isShowErrorLedsRtc();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_SD):
				returnValue = isShowErrorLedsSd();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_MASTER_SHIMMER):
				returnValue = isMasterShimmer();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_SYNC_WHEN_LOGGING):
				returnValue = isSyncWhenLogging();
	        	break;
			
			case(Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_LPM): 
				if(isLSM303DigitalAccelLPM()&&checkLowPowerGyro()&&checkLowPowerMag()) {
					returnValue = true;
				}
				else {
					returnValue = false;
				}
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.TCXO):
				returnValue = isTCXO();
	        	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN):
//				returnValue = isInternalExpPower();
//	        	break;

			case(Configuration.Shimmer3.GuiLabelConfig.LOW_POWER_AUTOSTOP):
				returnValue = isLowBattAutoStop();
	        	break;

			case(Configuration.Shimmer3.GuiLabelConfig.BLUETOOTH_BAUD_RATE):
				returnValue = getBluetoothBaudRate();
	        	break;
			case(SensorGSR.GuiLabelConfig.GSR_RANGE):
				returnValue = getGSRRange(); //TODO: check with RM re firmware bug??
	        	break;
	        	
			case(SensorEXG.GuiLabelConfig.EXG_RESOLUTION):
				returnValue = getExGResolution();
	    		break;
	        	
			case(SensorEXG.GuiLabelConfig.EXG_GAIN):
				returnValue = getExGGainSetting();
				//consolePrintLn("Get " + configValue);
	        	break;
	        	
			case(SensorEXG.GuiLabelConfig.EXG_RATE):
				returnValue = getEXG1RateSetting();
				//returnValue = getEXG2RateSetting();
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE):
				returnValue = getEXGReferenceElectrode();
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_DETECTION):
				returnValue = getEXGLeadOffCurrentMode();
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_CURRENT):
				returnValue = getEXGLeadOffDetectionCurrent();
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_COMPARATOR):
				returnValue = getEXGLeadOffComparatorTreshold();
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_FREQ):
				returnValue = getEXG2RespirationDetectFreq();
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE):
				returnValue = getEXG2RespirationDetectPhase();
            	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER):
//				returnValue = getInternalExpPower();
//            	break;
			case(SensorPPG.GuiLabelConfig.PPG_ADC_SELECTION):
				returnValue = getPpgAdcSelectionGsrBoard();
	    		break;
			case(SensorPPG.GuiLabelConfig.PPG1_ADC_SELECTION):
				returnValue = getPpg1AdcSelectionProto3DeluxeBoard();
	    		break;
			case(SensorPPG.GuiLabelConfig.PPG2_ADC_SELECTION):
				returnValue = getPpg2AdcSelectionProto3DeluxeBoard();
	    		break;
            	

			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_GSR):
				returnValue = getSamplingDividerGsr();
	    		break;
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_LSM303DLHC_ACCEL): 
				returnValue = getSamplingDividerLsm303dlhcAccel();
	    		break;
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_PPG):
				returnValue = getSamplingDividerPpg();
	    		break;
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT):
				returnValue = getSamplingDividerVBatt();
	    		break;

//    		case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME):
//    			returnValue = getShimmerUserAssignedName();
//    		   	break;
//    		case(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME):
//    			returnValue = getTrialName();
//    	       	break;
			case(Configuration.Shimmer3.GuiLabelConfig.BUFFER_SIZE):
				returnValue = Integer.toString(getBufferSize());
	        	break;
//    		case(Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME):
//    	       	returnValue = getConfigTimeParsed();
//    	       	break;
			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM):
	        	returnValue = getMacIdFromInfoMem();
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID):
	        	returnValue = Integer.toString(getExperimentId());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_NUMBER_OF_SHIMMERS):
	        	returnValue = Integer.toString(getTrialNumberOfShimmers());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_ESTIMATED):
	        	returnValue = Integer.toString(getTrialDurationEstimatedInSecs());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_MAXIMUM):
	        	returnValue = Integer.toString(getTrialDurationMaximumInSecs());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.BROADCAST_INTERVAL):
	        	returnValue = Integer.toString(getSyncBroadcastInterval());
	        	break;
	        	
//			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_PER_SENSOR):
//				returnValue = getMapOfSensorCalibrationPerSensor(sensorId);
//				break;
//			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_ALL):
//				returnValue = getMapOfSensorCalibrationAll();
//	        	break;
////List<Byte[]>
//    		case(SensorEXG.GuiLabelConfig.EXG_BYTES):
//    			List<byte[]> listOFExGBytes = new ArrayList<byte[]>();
//    			listOFExGBytes.add(getEXG1RegisterArray());
//    			listOFExGBytes.add(getEXG2RegisterArray());
//    			returnValue = listOFExGBytes;
//    	       	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_CALIBRATION_ALL):

		}
		
		return returnValue;
	}		
	
	@Override
	public Object setConfigValueUsingConfigLabel(String identifier, String configLabel, Object valueToSet) {

		Object returnValue = null;
		int buf = 0;

		switch(configLabel){
			case(Configuration.Shimmer3.GuiLabelConfig.USER_BUTTON_START):
				setButtonStart((boolean)valueToSet);
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.SD_BT_STREAM_WHEN_RECORDING):
				setDisableBluetooth(!(boolean)valueToSet);
				setSyncWhenLogging(!(boolean)valueToSet);
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.SD_STREAM_WHEN_RECORDING):
				setDisableBluetooth((boolean)valueToSet);
				setSyncWhenLogging(!(boolean)valueToSet);
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.SINGLE_TOUCH_START):
				setSingleTouch((boolean)valueToSet);
				break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_MASTER_SHIMMER):
				setMasterShimmer((boolean)valueToSet);
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_RTC):
				setShowErrorLedsRtc((boolean)valueToSet);
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.ENABLE_ERROR_LEDS_SD):
				setShowErrorLedsSd((boolean)valueToSet);
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_SYNC_WHEN_LOGGING):
				setDisableBluetooth(!(boolean)valueToSet);
				setSyncWhenLogging((boolean)valueToSet);
	        	break;

			case(Configuration.Shimmer3.GuiLabelConfig.KINEMATIC_LPM):
				setLowPowerAccelWR((boolean)valueToSet); 
				setLowPowerGyro((boolean)valueToSet);
				setLowPowerMag((boolean)valueToSet);
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.TCXO):
            	setTCXO((boolean)valueToSet);
	        	break;
	        //TODO remove? already in ShimmerDevice
			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_BOOLEAN):
            	setInternalExpPower((boolean)valueToSet);
	        	break;

			case(Configuration.Shimmer3.GuiLabelConfig.LOW_POWER_AUTOSTOP):
            	setLowBattAutoStop((boolean)valueToSet);
	        	break;

			case(Configuration.Shimmer3.GuiLabelConfig.BLUETOOTH_BAUD_RATE):
				setBluetoothBaudRate((int)valueToSet);
	        	break;
		        	
			case(SensorGSR.GuiLabelConfig.GSR_RANGE):
	    		setGSRRange((int)valueToSet);
	        	break;
	        	
			case(SensorEXG.GuiLabelConfig.EXG_RESOLUTION):
				setExGResolution((int)valueToSet);
	    		break;
	    		
			case(SensorEXG.GuiLabelConfig.EXG_GAIN):
				//consolePrintLn("before set " + getExGGain());
				setExGGainSetting((int)valueToSet);
				//consolePrintLn("after set " + getExGGain());
	        	break;
				
	        //TODO: regenerate EXG register bytes on each change (just in case)
	        	
			case(SensorEXG.GuiLabelConfig.EXG_RATE):
//				setEXG1RateSetting((int)valueToSet);
//				setEXG2RateSetting((int)valueToSet);
				setEXGRateSetting((int)valueToSet);
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE):
				setEXGReferenceElectrode((int)valueToSet);
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_DETECTION):
				setEXGLeadOffCurrentMode((int)valueToSet);
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_CURRENT):
				setEXGLeadOffDetectionCurrent((int)valueToSet);
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_LEAD_OFF_COMPARATOR):
				setEXGLeadOffComparatorTreshold((int)valueToSet);
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_FREQ):
				setEXG2RespirationDetectFreq((int)valueToSet);
            	break;
			case(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE):
				setEXG2RespirationDetectPhase((int)valueToSet);
            	break;	        	
			case(Configuration.Shimmer3.GuiLabelConfig.INT_EXP_BRD_POWER_INTEGER):
				setInternalExpPower((int)valueToSet);
            	break;
			case(SensorPPG.GuiLabelConfig.PPG_ADC_SELECTION):
				setPpgAdcSelectionGsrBoard((int)valueToSet);
	    		break;
			case(SensorPPG.GuiLabelConfig.PPG1_ADC_SELECTION):
				setPpg1AdcSelectionProto3DeluxeBoard((int)valueToSet);
	    		break;
			case(SensorPPG.GuiLabelConfig.PPG2_ADC_SELECTION):
				setPpg2AdcSelectionProto3DeluxeBoard((int)valueToSet);
	    		break;
	    	//GQ
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_GSR):
				setSamplingDividerGsr((int)valueToSet);
	    		break;
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_LSM303DLHC_ACCEL)://GQ BLE related
				setSamplingDividerLsm303dlhcAccel((int)valueToSet);
	    		break;
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_PPG):
				setSamplingDividerPpg((int)valueToSet);
	    		break;
			case(Configuration.ShimmerGqBle.GuiLabelConfig.SAMPLING_RATE_DIVIDER_VBATT):
				setSamplingDividerVBatt((int)valueToSet);
	    		break;
	    		
//			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_ALL):
//				setMapOfSensorCalibrationAll((TreeMap<Integer, TreeMap<Integer, CalibDetails>>) valueToSet);
//				break;
//				
//			case(Configuration.Shimmer3.GuiLabelConfig.CALIBRATION_PER_SENSOR):
//				setSensorCalibrationPerSensor(Integer.parseInt(identifier), (TreeMap<Integer, CalibDetails>) valueToSet);
//				//TODO decide whether to include the below
////				returnValue = valueToSet;
//				break;

//			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_USER_ASSIGNED_NAME):
//        		setShimmerUserAssignedName((String)valueToSet);
//	        	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.TRIAL_NAME):
//				setTrialName((String)valueToSet);
//	        	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE):
//	          	// don't let sampling rate be empty
//	          	Double enteredSamplingRate;
//	          	if(((String)valueToSet).isEmpty()) {
//	          		enteredSamplingRate = 1.0;
//	          	}            	
//	          	else {
//	          		enteredSamplingRate = Double.parseDouble((String)valueToSet);
//	          	}
//	      		setShimmerAndSensorsSamplingRate(enteredSamplingRate);
//	      		
//	      		returnValue = Double.toString(getSamplingRateShimmer());
//	        	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.BUFFER_SIZE):
//	        	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.CONFIG_TIME):
//	        	break;
//			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_MAC_FROM_INFOMEM):
//	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_ID):
            	if(!(((String)valueToSet).isEmpty())) {
                	buf = Integer.parseInt((String)valueToSet);
            	}
        		setExperimentId(buf);
        		
        		returnValue = Integer.toString(getExperimentId());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_NUMBER_OF_SHIMMERS):
            	if(((String)valueToSet).isEmpty()) {
            		buf = 1;
            	}
            	else {
                	buf = Integer.parseInt((String)valueToSet);
            	}
        		setExperimentNumberOfShimmers(buf);
        		
        		returnValue = Integer.toString(getTrialNumberOfShimmers());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_ESTIMATED):
            	if(((String)valueToSet).isEmpty()) {
            		buf = 1;
            	}
            	else {
                	buf = Integer.parseInt((String)valueToSet);
            	}
        		setExperimentDurationEstimatedInSecs(buf);
        		
        		returnValue = Integer.toString(getTrialDurationEstimatedInSecs());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.EXPERIMENT_DURATION_MAXIMUM):
            	//leave max_exp_len = 0 to not automatically stop logging.
            	if(((String)valueToSet).isEmpty()) {
            		buf = 1;
            	}
            	else {
                	buf = Integer.parseInt((String)valueToSet);
            	}
        		setExperimentDurationMaximumInSecs(buf);
        		
        		returnValue = Integer.toString(getTrialDurationMaximumInSecs());
	        	break;
			case(Configuration.Shimmer3.GuiLabelConfig.BROADCAST_INTERVAL):
            	buf = 1; // Minimum = 1
            	if(!(((String)valueToSet).isEmpty())) {
                	buf = Integer.parseInt((String)valueToSet);
            	}
        		setSyncBroadcastInterval(buf);
        		
        		returnValue = Integer.toString(getSyncBroadcastInterval());
	        	break;
	        	
	        	
////List<Byte[]>
//			case(SensorEXG.GuiLabelConfig.EXG_BYTES):
////				if(valueToSet instanceof List<?>){
//					setEXG1RegisterArray(((List<byte[]>)valueToSet).get(0));
//					setEXG2RegisterArray(((List<byte[]>)valueToSet).get(1));
////				}
//	        	break;

			case(Configuration.Shimmer3.GuiLabelConfig.ENABLED_SENSORS):
				setEnabledAndDerivedSensorsAndUpdateMaps((int)valueToSet, mDerivedSensors);
	    		break;

			case(Configuration.Shimmer3.GuiLabelConfig.ENABLED_SENSORS_IDS):
				setSensorIdsEnabled((Integer[])valueToSet);
	    		break;

			case(Configuration.Shimmer3.GuiLabelConfig.SHIMMER_SAMPLING_RATE):
				setLowPowerGyro(false);
				setLowPowerAccelWR(false);
				setLowPowerMag(false);
	        	returnValue = super.setConfigValueUsingConfigLabel(identifier, configLabel, valueToSet);
	        	checkLowPowerGyro();
	        	checkLowPowerMag();
	        	//TODO
//	        	checkLowPowerAccelWR();
				break;

	        default:
	        	returnValue = super.setConfigValueUsingConfigLabel(identifier, configLabel, valueToSet);
	        	break;
		}
		
        if((configLabel.equals(SensorEXG.GuiLabelConfig.EXG_RESPIRATION_DETECT_PHASE))
        		||(configLabel.equals(SensorEXG.GuiLabelConfig.EXG_REFERENCE_ELECTRODE))){
        	checkConfigOptionValues(configLabel);
        }
			
		return returnValue;

	}

	public String getCenter(){
		return mCenter;
	}
	
	public void setCenter(String value){
		mCenter = value;
	}

	@Override
	public void setShimmerVersionObject(ShimmerVerObject hwfw) {
		super.setShimmerVersionObject(hwfw);
		
//		createInfoMemLayoutObjectIfNeeded();
		
		updateTimestampByteLength();
		updateCurrentCalibInUse();
	}
	
	// --------------- Database related start --------------------------
	
	@Override
	public LinkedHashMap<String, Object> generateConfigMap(COMMUNICATION_TYPE commType) {
//		LinkedHashMap<String, Object> configMapForDb = new LinkedHashMap<String, Object>();

		// New approach to storing Shimmer3 configuration to the database. The
		// keyset moved from DatabaseHandler.getShimmer3ConfigColumns().
		// Values moved from where they were generated in
		// ShimmerObject.getShimmerConfigToInsertInDB()

		LinkedHashMap<String, Object> configMapForDb = super.generateConfigMap(commType);
		
		configMapForDb.put(SensorGSR.DatabaseConfigHandle.GSR_RANGE, (double) getGSRRange());
		
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.USER_BUTTON, (double) getButtonStart());
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.RTC_SOURCE, (double) getRTCSetByBT());
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.MASTER_CONFIG, (double) getMasterShimmer());
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.SINGLE_TOUCH_START, (double) getSingleTouch());
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.TXCO, (double) getTCXO());
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.REAL_TIME_CLOCK_DIFFERENCE, (double) getRTCDifferenceInTicks());

		SensorEXG.addExgConfigToDbConfigMap(configMapForDb, getEXG1RegisterArray(), getEXG2RegisterArray());
		
		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.INITIAL_TIMESTAMP, (double) getInitialTimeStampTicksSd());

		configMapForDb.put(DatabaseConfigHandleShimmerObject.SYNC_WHEN_LOGGING, (double) getSyncWhenLogging());
		configMapForDb.put(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_ESTIMATED, (double) getTrialDurationEstimatedInSecs());
		configMapForDb.put(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_MAXIMUM, (double) getTrialDurationMaximumInSecs());

		configMapForDb.put(ShimmerDevice.DatabaseConfigHandle.LOW_POWER_AUTOSTOP, (double) (isLowBattAutoStop()? 1:0));

		//Now handled in the algorithm class in super
		/**
		if(svo!=null && svo.isShimmerGenGq()){
			configColumns.add(GSRMetricsModule.DatabaseConfigHandle.GSR_PEAK_HOLD_LENGTH_S);
		}
		// This is for the GSR_Peak_Hold GSRMetricModule algorithm as
		// ShimmerObject is currently used for importing GQ SD data via
		// ShimmerSDLog. This approach is improved from Shimmer4 onwards.
		if(shimmerObject.isShimmerGenGq()){
			configValues.add((double) 0);
		}
		configMapForDb.put(key, (double) value);
		*/
		
		return configMapForDb;
	}
	
	@Override
	public void parseConfigMap(ShimmerVerObject svo, LinkedHashMap<String, Object> mapOfConfigPerShimmer, COMMUNICATION_TYPE commType) {
		super.parseConfigMap(svo, mapOfConfigPerShimmer, commType);
		
		if(mapOfConfigPerShimmer.containsKey(SensorGSR.DatabaseConfigHandle.GSR_RANGE)){
			setGSRRange(((Double) mapOfConfigPerShimmer.get(SensorGSR.DatabaseConfigHandle.GSR_RANGE)).intValue());
		}
//		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.EXP_PWR)){
//			setInternalExpPower(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.EXP_PWR)).intValue());
//		}
		
		//RTC_SOURCE Not needed
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.MASTER_CONFIG)){
			setMasterShimmer(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.MASTER_CONFIG))>0? true:false);
		}
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.SINGLE_TOUCH_START)){
			setSingleTouch(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.SINGLE_TOUCH_START))>0? true:false);
		}
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.TXCO)){
			setTCXO(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.TXCO))>0? true:false);
		}
		//RTC Difference
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.REAL_TIME_CLOCK_DIFFERENCE)){
			setRTCDifferenceInTicks(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.REAL_TIME_CLOCK_DIFFERENCE)).longValue());
		}
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.USER_BUTTON)){
			setButtonStart(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.USER_BUTTON))>0? true:false);
		}
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandle.LOW_POWER_AUTOSTOP)){
			setLowBattAutoStop(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandle.LOW_POWER_AUTOSTOP))>0? true:false);
		}
		
		//EXG Configuration
		byte[] exg1Bytes = SensorEXG.parseExgConfigFromDb(mapOfConfigPerShimmer, EXG_CHIP_INDEX.CHIP1, 
				SensorEXG.DatabaseConfigHandle.EXG1_CONFIG_1,
				SensorEXG.DatabaseConfigHandle.EXG1_CONFIG_2,
				SensorEXG.DatabaseConfigHandle.EXG1_LEAD_OFF,
				SensorEXG.DatabaseConfigHandle.EXG1_CH1_SET,
				SensorEXG.DatabaseConfigHandle.EXG1_CH2_SET,
				SensorEXG.DatabaseConfigHandle.EXG1_RLD_SENSE,
				SensorEXG.DatabaseConfigHandle.EXG1_LEAD_OFF_SENSE,
				SensorEXG.DatabaseConfigHandle.EXG1_LEAD_OFF_STATUS,
				SensorEXG.DatabaseConfigHandle.EXG1_RESPIRATION_1,
				SensorEXG.DatabaseConfigHandle.EXG1_RESPIRATION_2);
//		if(exg1Bytes!=null){
//			exgBytesGetConfigFrom(EXG_CHIP_INDEX.CHIP1, exg1Bytes);
//		}
		byte[] exg2Bytes = SensorEXG.parseExgConfigFromDb(mapOfConfigPerShimmer, EXG_CHIP_INDEX.CHIP2, 
				SensorEXG.DatabaseConfigHandle.EXG2_CONFIG_1,
				SensorEXG.DatabaseConfigHandle.EXG2_CONFIG_2,
				SensorEXG.DatabaseConfigHandle.EXG2_LEAD_OFF,
				SensorEXG.DatabaseConfigHandle.EXG2_CH1_SET,
				SensorEXG.DatabaseConfigHandle.EXG2_CH2_SET,
				SensorEXG.DatabaseConfigHandle.EXG2_RLD_SENSE,
				SensorEXG.DatabaseConfigHandle.EXG2_LEAD_OFF_SENSE,
				SensorEXG.DatabaseConfigHandle.EXG2_LEAD_OFF_STATUS,
				SensorEXG.DatabaseConfigHandle.EXG2_RESPIRATION_1,
				SensorEXG.DatabaseConfigHandle.EXG2_RESPIRATION_2);
//		if(exg2Bytes!=null){
//			exgBytesGetConfigFrom(EXG_CHIP_INDEX.CHIP2, exg2Bytes);
//		}
		exgBytesGetConfigFrom(exg1Bytes, exg2Bytes);
		checkExgResolutionFromEnabledSensorsVar();
		
		//Initial TimeStamp
		if(mapOfConfigPerShimmer.containsKey(SensorShimmerClock.DatabaseConfigHandle.INITIAL_TIMESTAMP)){
			setInitialTimeStampTicksSd(((Double) mapOfConfigPerShimmer.get(SensorShimmerClock.DatabaseConfigHandle.INITIAL_TIMESTAMP)).longValue());
		}


		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandleShimmerObject.SYNC_WHEN_LOGGING)){
			setSyncWhenLogging(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandleShimmerObject.SYNC_WHEN_LOGGING)).intValue());
		}

		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_ESTIMATED)){
			setExperimentDurationEstimatedInSecs(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_ESTIMATED)).intValue());
		}
		if(mapOfConfigPerShimmer.containsKey(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_MAXIMUM)){
			setExperimentDurationMaximumInSecs(((Double) mapOfConfigPerShimmer.get(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_MAXIMUM)).intValue());
		}

		//prepareAllMapsAfterConfigRead();

	}

	@Deprecated
	public List<String> getConfigColumnsToInsertInDBLegacy(){
		return getConfigColumnsShimmer3Legacy(getShimmerVerObject(), getExpansionBoardDetails());
	}

	@Deprecated
	public static List<String> getConfigColumnsShimmer3Legacy(ShimmerVerObject svo, ExpansionBoardDetails ebd){
		List<String> configColumns = new ArrayList<String>();

		configColumns.add(ShimmerDevice.DatabaseConfigHandle.SAMPLE_RATE);

		configColumns.add(ShimmerDevice.DatabaseConfigHandle.ENABLE_SENSORS);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.DERIVED_SENSORS);

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.WR_ACC_RATE);
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.WR_ACC_RANGE);
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.WR_ACC_LPM);
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.WR_ACC_HRM);
		} else {
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.WR_ACC_RATE);
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.WR_ACC_RANGE);
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.WR_ACC_LPM);
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.WR_ACC_HRM);
		}
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorMPU9250.DatabaseConfigHandle.GYRO_RATE);
		} else {
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.GYRO_RATE);
		}
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.MAG_RANGE);
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.MAG_RATE);
		} else {
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.MAG_RANGE);
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.MAG_RATE);
		}
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorMPU9250.DatabaseConfigHandle.GYRO_RANGE);
			configColumns.add(SensorMPU9250.DatabaseConfigHandle.ALTERNATIVE_ACC_RANGE);
		} else {
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.GYRO_RANGE);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.ALTERNATIVE_ACC_RANGE);
		}
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedBmp280(svo, ebd)){
			configColumns.add(SensorBMP280.DatabaseConfigHandle.PRESSURE_PRECISION_BMP280);
		} else {
			configColumns.add(SensorBMP180.DatabaseConfigHandle.PRESSURE_PRECISION_BMP180);
		}
		configColumns.add(SensorGSR.DatabaseConfigHandle.GSR_RANGE);
		
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.EXP_PWR);
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_DMP);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_LPF);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MOT_CAL_CFG);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MPL_SAMPLING_RATE);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MPL_SENSOR_FUSION);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MPL_GYRO_TC);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MPL_VECT_COMP);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MAG_DIST);
//			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MPL_ENABLE);
			configColumns.add(SensorMPU9250.DatabaseConfigHandle.MPU_MAG_SAMPLING_RATE);
		} else {
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_DMP);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_LPF);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MOT_CAL_CFG);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MPL_SAMPLING_RATE);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MPL_SENSOR_FUSION);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MPL_GYRO_TC);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MPL_VECT_COMP);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MAG_DIST);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MPL_ENABLE);
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.MPU_MAG_SAMPLING_RATE);
		}
		
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.USER_BUTTON);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.RTC_SOURCE);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.MASTER_CONFIG);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.SINGLE_TOUCH_START);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.TXCO);

		configColumns.add(ShimmerDevice.DatabaseConfigHandle.SHIMMER_VERSION);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.FW_VERSION);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.FW_VERSION_MAJOR);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.FW_VERSION_MINOR);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.FW_VERSION_INTERNAL);

		configColumns.add(ShimmerDevice.DatabaseConfigHandle.CONFIG_TIME);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.REAL_TIME_CLOCK_DIFFERENCE);

		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_CONFIG_1);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_CONFIG_2);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_LEAD_OFF);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_CH1_SET);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_CH2_SET);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_RLD_SENSE);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_LEAD_OFF_SENSE);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_LEAD_OFF_STATUS);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_RESPIRATION_1);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG1_RESPIRATION_2);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_CONFIG_1);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_CONFIG_2);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_LEAD_OFF);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_CH1_SET);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_CH2_SET);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_RLD_SENSE);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_LEAD_OFF_SENSE);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_LEAD_OFF_STATUS);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_RESPIRATION_1);
		configColumns.add(SensorEXG.DatabaseConfigHandle.EXG2_RESPIRATION_2);

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.addAll(SensorLSM303AH.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_WR_ACCEL);
		} else {
			configColumns.addAll(SensorLSM303DLHC.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_WR_ACCEL);
		}

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.addAll(SensorMPU9250.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_GYRO);
		} else {
			configColumns.addAll(SensorMPU9150.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_GYRO);
		}


		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.addAll(SensorLSM303AH.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MAG);
		} else {
			configColumns.addAll(SensorLSM303DLHC.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MAG);
		}

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.addAll(SensorKionixKXTC92050.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_LN_ACC);
		} else {
			configColumns.addAll(SensorKionixKXRB52042.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_LN_ACC);
		}

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.addAll(SensorBMP280.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES);
		} else {
			configColumns.addAll(SensorBMP180.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES);
		}

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.addAll(SensorMPU9250.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MPU_ACC);
			configColumns.addAll(SensorMPU9250.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MPU_MAG);
//			configColumns.addAll(SensorMPU9250.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MPU_MPL_GYRO);			
		} else {
			configColumns.addAll(SensorMPU9150.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MPU_MPL_ACC);
			configColumns.addAll(SensorMPU9150.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MPU_MPL_MAG);
			configColumns.addAll(SensorMPU9150.DatabaseConfigHandle.LIST_OF_CALIB_HANDLES_MPU_MPL_GYRO);			
		}

		configColumns.add(ShimmerDevice.DatabaseConfigHandle.INITIAL_TIMESTAMP);

		configColumns.add(ShimmerDevice.DatabaseConfigHandle.EXP_BOARD_ID);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.EXP_BOARD_REV);
		configColumns.add(ShimmerDevice.DatabaseConfigHandle.EXP_BOARD_REV_SPEC);

		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.WR_ACC_CALIB_TIME);
			configColumns.add(SensorLSM303AH.DatabaseConfigHandle.MAG_CALIB_TIME);
		}
		else {
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.WR_ACC_CALIB_TIME);
			configColumns.add(SensorLSM303DLHC.DatabaseConfigHandle.MAG_CALIB_TIME);
		}
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorMPU9250.DatabaseConfigHandle.GYRO_CALIB_TIME);
		} else {
			configColumns.add(SensorMPU9150.DatabaseConfigHandle.GYRO_CALIB_TIME);
		}
		if(svo!=null && ebd!=null && ShimmerObject.isSupportedNewImuSensors(svo, ebd)){
			configColumns.add(SensorKionixKXTC92050.DatabaseConfigHandle.LN_ACC_CALIB_TIME);
		} else {
			configColumns.add(SensorKionixKXRB52042.DatabaseConfigHandle.LN_ACC_CALIB_TIME);
		}

		configColumns.add(DatabaseConfigHandleShimmerObject.SYNC_WHEN_LOGGING);
		configColumns.add(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_ESTIMATED);
		configColumns.add(DatabaseConfigHandleShimmerObject.TRIAL_DURATION_MAXIMUM);

		return configColumns;
	}
	
	
	@Deprecated
	public List<Double> getShimmerConfigValuesToInsertInDBLegacy(){
		return getDbConfigValuesFromShimmerLegacy(this);
	}

	/** This method only needs to support Shimmer3. ShimmerGQ is handled in the ShimmerGQ class and >=Shimmer4 is via a config map
	 * 
	 * This corresponds to the Database column labels declared in DatabaseHandler.getShimmer3ConfigColumns()
	 *  */
	@Deprecated
	public static List<Double> getDbConfigValuesFromShimmerLegacy(ShimmerDevice shimmerDevice){
		if (shimmerDevice instanceof ShimmerObject){
			ShimmerObject shimmerObject = (ShimmerObject) shimmerDevice;
			List<Double> configValues = new ArrayList<Double>();
			//0-1 Byte = Sampling Rate
			configValues.add(shimmerObject.getSamplingRateShimmer());

			//3-7 Byte = Sensors
			configValues.add((double) shimmerObject.getEnabledSensors());
			//40-71 Byte = Derived Sensors
			configValues.add((double) shimmerObject.getDerivedSensors());

			//The Configuration byte index 8 - 19
			configValues.add((double) shimmerObject.getLSM303DigitalAccelRate());
			configValues.add((double) shimmerObject.getAccelRange());
			configValues.add((double) shimmerObject.getLowPowerAccelEnabled());
			configValues.add((double) shimmerObject.getHighResAccelWREnabled());
			configValues.add((double) shimmerObject.getMPU9X50GyroAccelRate());
			configValues.add((double) shimmerObject.getMagRange());
			configValues.add((double) shimmerObject.getMagRate());
			configValues.add((double) shimmerObject.getGyroRange());
			configValues.add((double) shimmerObject.getMPU9X50AccelRange());
			configValues.add((double) shimmerObject.getPressureResolution());
			configValues.add((double) shimmerObject.getGSRRange());
			configValues.add((double) shimmerObject.getInternalExpPower());
			
			if(!shimmerObject.isSupportedNewImuSensors()){
				configValues.add((double) shimmerObject.getMPU9X50DMP());
				configValues.add((double) shimmerObject.getMPU9X50LPF());
				configValues.add((double) shimmerObject.getMPU9X50MotCalCfg());
				configValues.add((double) shimmerObject.getMPU9X50MPLSamplingRate());
				
				configValues.add((double) shimmerObject.getMPLSensorFusion());
				configValues.add((double) shimmerObject.getMPLGyroCalTC());
				configValues.add((double) shimmerObject.getMPLVectCompCal());
				configValues.add((double) shimmerObject.getMPLMagDistCal());
				configValues.add((double) shimmerObject.getMPLEnable());
			}
			configValues.add((double) shimmerObject.getMPU9X50MagSamplingRate());

			configValues.add((double) shimmerObject.getButtonStart());

			configValues.add((double) shimmerObject.getRTCSetByBT());// RTC source, 1 = it comes from the BT, 0 = from dock
			
//			mConfigValues[25] = sd.mSyncWhenLogging; This is already inserted in the Trial table
			configValues.add((double) shimmerObject.getMasterShimmer());
			configValues.add((double) shimmerObject.getSingleTouch());
			configValues.add((double) shimmerObject.getTCXO());
//			mConfigValues[29] = sd.mBroadcastInterval; This is already inserted in the Trial table

			//Firmware and Shimmer Parameters
			configValues.add((double) shimmerObject.getHardwareVersion());
//			mConfigValues[29] = sd.mMyTrialID; 
//			mConfigValues[30] = sd.mNShimmer; This is already insrted in the Trial table
			configValues.add((double) shimmerObject.getFirmwareIdentifier());
			configValues.add((double) shimmerObject.getFirmwareVersionMajor());
			configValues.add((double) shimmerObject.getFirmwareVersionMinor());
			configValues.add((double) shimmerObject.getFirmwareVersionInternal());

			//Configuration Time
			configValues.add((double) shimmerObject.getConfigTime());

			//RTC Difference
			configValues.add((double) shimmerObject.getRTCDifferenceInTicks());

			//EXG Configuration
			byte[] exg1Array = shimmerObject.getEXG1RegisterArray();
			for(int i=0; i<exg1Array.length; i++)
				configValues.add((double) (exg1Array[i] & 0xFF));

			byte[] exg2Array = shimmerObject.getEXG2RegisterArray();
			for(int i=0; i<exg2Array.length; i++)
				configValues.add((double) (exg2Array[i] & 0xFF));

			//Digital Accel Calibration Configuration
			double[][] offsetVectorWRAccel = shimmerObject.getOffsetVectorMatrixWRAccel();
			double[][] sensitivityMatrixWRAccel = shimmerObject.getSensitivityMatrixWRAccel();
			double[][] alignmentMatrixWRAccel = shimmerObject.getAlignmentMatrixWRAccel();
			addCalibKinematicToDbConfigValues(configValues, offsetVectorWRAccel, sensitivityMatrixWRAccel, alignmentMatrixWRAccel);

			//Gyroscope Calibration Configuration
			double[][] offsetVectorGyroscope = shimmerObject.getOffsetVectorMatrixGyro();
			double[][] sensitivityMatrixGyroscope = shimmerObject.getSensitivityMatrixGyro();
			double[][] alignmentMatrixGyroscope = shimmerObject.getAlignmentMatrixGyro();
			addCalibKinematicToDbConfigValues(configValues, offsetVectorGyroscope, sensitivityMatrixGyroscope, alignmentMatrixGyroscope);

			//Magnetometer Calibration Configuration
			double[][] offsetVectorMagnetometer = shimmerObject.getOffsetVectorMatrixMag();
			double[][] sensitivityMatrixMagnetometer = shimmerObject.getSensitivityMatrixMag();
			double[][] alignmentMatrixMagnetometer = shimmerObject.getAlignmentMatrixMag();
			addCalibKinematicToDbConfigValues(configValues, offsetVectorMagnetometer, sensitivityMatrixMagnetometer, alignmentMatrixMagnetometer);

			//Analog Accel Calibration Configuration
			double[][] offsetVectorAnalogAccel = shimmerObject.getOffsetVectorMatrixAccel();
			double[][] sensitivityMatrixAnalogAccel = shimmerObject.getSensitivityMatrixAccel();
			double[][] alignmentMatrixAnalogAccel = shimmerObject.getAlignmentMatrixAccel();
			addCalibKinematicToDbConfigValues(configValues, offsetVectorAnalogAccel, sensitivityMatrixAnalogAccel, alignmentMatrixAnalogAccel);

			//PRESSURE (BMP180) CAL PARAMS
			configValues.addAll(shimmerObject.mSensorBMPX80.getPressTempConfigValuesLegacy());

			//MPL Accel Calibration Configuration
			double[][] offsetVectorMPLAccel = shimmerObject.getOffsetVectorMPLAccel();
			double[][] sensitivityMatrixMPLAccel = shimmerObject.getSensitivityMatrixMPLAccel();
			double[][] alignmentMatrixMPLAccel = shimmerObject.getAlignmentMatrixMPLAccel();
			addCalibKinematicToDbConfigValues(configValues, offsetVectorMPLAccel, sensitivityMatrixMPLAccel, alignmentMatrixMPLAccel);

			//MPL Mag Calibration Configuration
			double[][] offsetVectorMPLMag = shimmerObject.getOffsetVectorMPLMag();
			double[][] sensitivityMatrixMPLMag = shimmerObject.getSensitivityMatrixMPLMag();
			double[][] alignmentMatrixMPLMag = shimmerObject.getAlignmentMatrixMPLMag();
			addCalibKinematicToDbConfigValues(configValues, offsetVectorMPLMag, sensitivityMatrixMPLMag, alignmentMatrixMPLMag);
				
			if(!shimmerObject.isSupportedNewImuSensors()){
				//MPL Gyro Calibration Configuration
				double[][] offsetVectorMPLGyro = shimmerObject.getOffsetVectorMPLGyro();
				double[][] sensitivityMatrixMPLGyro = shimmerObject.getSensitivityMatrixMPLGyro();
				double[][] alignmentMatrixMPLGyro = shimmerObject.getAlignmentMatrixMPLGyro();
				addCalibKinematicToDbConfigValues(configValues, offsetVectorMPLGyro, sensitivityMatrixMPLGyro, alignmentMatrixMPLGyro);
			}

			//Initial TimeStamp
			configValues.add((double) shimmerObject.getInitialTimeStampTicksSd());

			//Expansion board
			configValues.add((double) shimmerObject.getExpansionBoardId());
			configValues.add((double) shimmerObject.getExpansionBoardRev());
			configValues.add((double) shimmerObject.getExpansionBoardRevSpecial());
			
			configValues.add((double) shimmerObject.getCalibTimeWRAccel());
			configValues.add((double) shimmerObject.getCalibTimeMag());
			configValues.add((double) shimmerObject.getCalibTimeGyro());
			configValues.add((double) shimmerObject.getCalibTimeAccel());
			
			//Not in the SD header so can't add
			configValues.add((double) shimmerObject.getSyncWhenLogging());
			configValues.add((double) shimmerObject.getTrialDurationEstimatedInSecs());
			configValues.add((double) shimmerObject.getTrialDurationMaximumInSecs());
//			configValues.add((double) shimmerObject.getSyncBroadcastInterval());
			
			// This is for the GSR_Peak_Hold GSRMetricModule algorithm as
			// ShimmerObject is currently used for importing GQ SD data via
			// ShimmerSDLog. This approach is improved from Shimmer4 onwards.
			if(shimmerObject.isShimmerGenGq()){
				configValues.add((double) 0);
			}
			
			return configValues;
		} 
		return null;
	}
	
	@Deprecated
	public static void addCalibKinematicToDbConfigValues(List<Double> configValues, double[][] offsetVector, double[][] sensitivityMatrix, double[][] alignmentMatrix) {
		configValues.add(offsetVector[0][0]);
		configValues.add(offsetVector[1][0]);
		configValues.add(offsetVector[2][0]);
		configValues.add(sensitivityMatrix[0][0]);
		configValues.add(sensitivityMatrix[1][1]);
		configValues.add(sensitivityMatrix[2][2]);
		configValues.add(alignmentMatrix[0][0]);
		configValues.add(alignmentMatrix[0][1]);
		configValues.add(alignmentMatrix[0][2]);
		configValues.add(alignmentMatrix[1][0]);
		configValues.add(alignmentMatrix[1][1]);
		configValues.add(alignmentMatrix[1][2]);
		configValues.add(alignmentMatrix[2][0]);
		configValues.add(alignmentMatrix[2][1]);
		configValues.add(alignmentMatrix[2][2]);
	}
	// --------------- Database related end --------------------------

	// --------------- Set Methods Start --------------------------

	public void setUniqueID(String uniqueID){
		mUniqueID = uniqueID;
		String[] idSplit = mUniqueID.split(".");
		if(idSplit.length>=3) {
			mDockID = idSplit[0] + "." + idSplit[1];
			try {
				mSlotNumber = Integer.parseInt(idSplit[2]);
			}
			catch (NumberFormatException nFE) {
			}
		}
	}

	
	/** Use setHardwareVersionAndCreateSensorMaps(hardwareVersion) instead
	 * @param hardwareVersion
	 */
	@Deprecated
	public void initialise(int hardwareVersion) {
		setHardwareVersionAndCreateSensorMaps(hardwareVersion);
	}
	

	public void updateShimmerDriveInfo(ShimmerSDCardDetails shimmerSDCardDetails) {
		this.mShimmerSDCardDetails = shimmerSDCardDetails;
	}
	
	/** setShimmerVerionObject should be used instead
	 * @param firmwareId the firmwareId to set
	 */
	public void setFirmwareIdentifier(int firmwareId) {
		ShimmerVerObject sVOFwId = new ShimmerVerObject(getHardwareVersion(), firmwareId, getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal());
		setShimmerVersionObject(sVOFwId);
	}

	/** setShimmerVerionObject should be used instead
	 * @param firmwareId the firmwareId to set
	 */
	public void setFirmwareVersion(int firmwareVersionMajor, int firmwareVersionMinor, int firmwareVersionInternal) {
		ShimmerVerObject sVOFwId = new ShimmerVerObject(getHardwareVersion(), getFirmwareIdentifier(), firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
		setShimmerVersionObject(sVOFwId);
	}


	// --------------- Set Methods End --------------------------

	
	// --------------- ShimmerGQ BLE related start --------------------------
	
	/** ShimmerGQBle
	 * @param mSamplingDividerVBatt the mSamplingDividerVBatt to set
	 */
	public void setSamplingDividerVBatt(int mSamplingDividerVBatt) {
		this.mSamplingDividerVBatt = mSamplingDividerVBatt;
	}

	/** ShimmerGQBle
	 * @param mSamplingDividerGsr the mSamplingDividerGsr to set
	 */
	public void setSamplingDividerGsr(int mSamplingDividerGsr) {
		this.mSamplingDividerGsr = mSamplingDividerGsr;
	}

	/** ShimmerGQBle
	 * @param mSamplingDividerLsm303dlhcAccel the mSamplingDividerLsm303dlhcAccel to set
	 */
	public void setSamplingDividerLsm303dlhcAccel(int mSamplingDividerLsm303dlhcAccel) {
		this.mSamplingDividerLsm303dlhcAccel = mSamplingDividerLsm303dlhcAccel;
	}

	/** ShimmerGQBle
	 * @param mSamplingDividerBeacon the mSamplingDividerBeacon to set
	 */
	public void setSamplingDividerBeacon(int mSamplingDividerBeacon) {
		this.mSamplingDividerBeacon = mSamplingDividerBeacon;
	}

	public void setExpansionBoardId(int expansionBoardId) {
		mExpansionBoardDetails.mExpansionBoardId = expansionBoardId;
	}

	
	// --------------- ShimmerGQ BLE related end --------------------------


	@Override
	public void parseUartConfigResponse(UartComponentPropertyDetails cPD, byte[] response){
		// Parse response string
		if(cPD==UART_COMPONENT_AND_PROPERTY.BAT.ENABLE){
			//TODO Shimmer3 vs. ShimmerGQ
			if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
				getSensorMap().get(Configuration.Shimmer3.SENSOR_ID.SHIMMER_VBATT).setIsEnabled((response[0]==0)? false:true);
			}
			else if(getHardwareVersion()==HW_ID.SHIMMER_GQ_BLE){
				getSensorMap().get(Configuration.ShimmerGqBle.SENSOR_ID.VBATT).setIsEnabled((response[0]==0)? false:true);
			}
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.BAT.FREQ_DIVIDER){
			setSamplingDividerVBatt(response[0]);
		}

		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.ENABLE){
			//TODO Shimmer3 vs. ShimmerGQ
			if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
				getSensorMap().get(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LSM303_ACCEL).setIsEnabled((response[0]==0)? false:true);
			}
			else if(getHardwareVersion()==HW_ID.SHIMMER_GQ_BLE){
				getSensorMap().get(Configuration.ShimmerGqBle.SENSOR_ID.LSM303DLHC_ACCEL).setIsEnabled((response[0]==0)? false:true);
			}
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.DATA_RATE){
			setLSM303DigitalAccelRate(response[0]);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.RANGE){
			setDigitalAccelRange(response[0]);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.LP_MODE){
			setLowPowerAccelWR((response[0]==0)? false:true);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.HR_MODE){
			setHighResAccelWR((response[0]==0)? false:true);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.FREQ_DIVIDER){
			setSamplingDividerLsm303dlhcAccel(response[0]);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.CALIBRATION){
			parseCalibParamFromPacketAccelLsm(response, CALIB_READ_SOURCE.LEGACY_BT_COMMAND);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.GSR.ENABLE){
			//TODO Shimmer3 vs. ShimmerGQ
			if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
				getSensorMap().get(Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR).setIsEnabled((response[0]==0)? false:true);
			}
			else if(getHardwareVersion()==HW_ID.SHIMMER_GQ_BLE){
				getSensorMap().get(Configuration.ShimmerGqBle.SENSOR_ID.GSR).setIsEnabled((response[0]==0)? false:true);
			}
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.GSR.RANGE){
			setGSRRange(response[0]);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.GSR.FREQ_DIVIDER){
			setSamplingDividerGsr(response[0]);
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.BEACON.ENABLE){
			//TODO Shimmer3 vs. ShimmerGQ
			if(getHardwareVersion()==HW_ID.SHIMMER_3 || getHardwareVersion()==HW_ID.SHIMMER_3R){
				getSensorMap().get(Configuration.ShimmerGqBle.SENSOR_ID.BEACON).setIsEnabled((response[0]==0)? false:true);
			}
			else if(getHardwareVersion()==HW_ID.SHIMMER_GQ_BLE){
				getSensorMap().get(Configuration.ShimmerGqBle.SENSOR_ID.BEACON).setIsEnabled((response[0]==0)? false:true);
			}
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.BEACON.FREQ_DIVIDER){
			setSamplingDividerBeacon(response[0]);
		}
		else {
			super.parseUartConfigResponse(cPD, response);
		}
	}

	@Override
	public byte[] generateUartConfigMessage(UartComponentPropertyDetails cPD){
		
//		System.out.println("Component:" + cPD.component + " Property:" + cPD.property + " ByteArray:" + cPD.byteArray.length);
		
		if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.ENABLE){
			byte[] response = new byte[1]; 
			response[0] = (byte)(getSensorMap().get(Configuration.ShimmerGqBle.SENSOR_ID.LSM303DLHC_ACCEL).isEnabled()? 1:0);
			return response;
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.DATA_RATE){
			byte[] response = new byte[1]; 
			response[0] = (byte)getLSM303DigitalAccelRate();
			return response;
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.RANGE){
			byte[] response = new byte[1]; 
			response[0] = (byte)getAccelRange();
			return response;
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.LP_MODE){
			byte[] response = new byte[1]; 
			response[0] = (byte)(isLowPowerAccelWR()? 1:0);
			return response;
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.HR_MODE){
			byte[] response = new byte[1]; 
			response[0] = (byte)(isHighResAccelWR()? 1:0);
			return response;
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.FREQ_DIVIDER){
			byte[] response = new byte[1]; 
			response[0] = (byte)(getSamplingDividerLsm303dlhcAccel());
			return response;
		}
		else if(cPD==UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.CALIBRATION){
			byte[] response = generateCalParamLSM303DLHCAccel();
			return response;
		}
		else {
			return super.generateUartConfigMessage(cPD);
		}			
	}
	
	@Override
	public double getMinAllowedSamplingRate() {
		double minAllowedSamplingRate = super.getMinAllowedSamplingRate();
		if(isMPLEnabled() || isMPU9150DMP()){
			minAllowedSamplingRate = Math.max(51.2, minAllowedSamplingRate);
		}
		return minAllowedSamplingRate;
	}


	public boolean isSupportedErrorLedControl() {
		return (mShimmerVerObject.compareVersions(FW_ID.LOGANDSTREAM, 0, 7, 12)
				|| mShimmerVerObject.compareVersions(FW_ID.STROKARE, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION, ShimmerVerDetails.ANY_VERSION));
	}

	/** Returns true if the Shimmer is using new sensors. These sensors are:
	 * <li> Use BMP280 instead of BMP180 as barometer. 
	 * 
	 * @return
	 */
	public boolean isSupportedBmp280() {
		return isSupportedNewImuSensors();
	}

	public static boolean isSupportedBmp280(ShimmerVerObject svo, ExpansionBoardDetails ebd) {
		return isSupportedNewImuSensors(svo, ebd);
	}

	/** Returns true if the Shimmer is using new sensors. These sensors are:
	 * <li> Use BMP280 instead of BMP180 as barometer. 
	 * <li> Use MPU9250 instead of MPU9150 as IMU. 
	 * <li> Use KXTC9-2050 instead of KXRB5-2042 as analogue accelerometer. 
	 * <li> Use LSM303AHTR instead of LSM303DLHC as digital accelerometer.
	 * 
	 * @return
	 */
	public boolean isSupportedNewImuSensors() {
		return isSupportedNewImuSensors(getShimmerVerObject(), getExpansionBoardDetails());
	}
	
	public boolean isShimmer3RwithHighGAccelSupport() {
		return isShimmer3RwithHighGAccelSupport(getShimmerVerObject(), getExpansionBoardDetails());
	}
	
	public static boolean isShimmer3RwithHighGAccelSupport(ShimmerVerObject svo, ExpansionBoardDetails ebd) {
				
		if(svo==null || ebd==null){
			return false;
		}
		
		int expBrdId = ebd.getExpansionBoardId();
		int expBrdRev = ebd.getExpansionBoardRev();
		int expBrdRevSpecial = ebd.getExpansionBoardRevSpecial();
		
		if((svo.getHardwareVersion()==HW_ID.SHIMMER_3R) && (
				(expBrdId == HW_ID_SR_CODES.SHIMMER3)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE && expBrdRev == 4 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED && expBrdRev == 4 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 6 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 1)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 8 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 1)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev == 8 && expBrdRevSpecial == 0))){
			return true;
		}
		else {
			return false;
		}
	}
	public boolean isShimmer3RwithAltMagSupport() {
		return isShimmer3RwithAltMagSupport(getShimmerVerObject(), getExpansionBoardDetails());
	}
	
	public static boolean isShimmer3RwithAltMagSupport(ShimmerVerObject svo, ExpansionBoardDetails ebd) {
		if(svo==null || ebd==null){
			return false;
		}
		int expBrdId = ebd.getExpansionBoardId();
		int expBrdRev = ebd.getExpansionBoardRev();
		int expBrdRevSpecial = ebd.getExpansionBoardRevSpecial();
		
		if((svo.getHardwareVersion()==HW_ID.SHIMMER_3R) && (
				(expBrdId == HW_ID_SR_CODES.SHIMMER3 && expBrdRev == 11 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE && expBrdRev == 4 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED && expBrdRev == 4 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 6 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 1)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev == 8 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 0)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev == 7 && expBrdRevSpecial == 1)
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev == 8 && expBrdRevSpecial == 0))){
			return true;
		}
		else {
			return false;
		}
	}

	public static boolean isSupportedNewImuSensors(ShimmerVerObject svo, ExpansionBoardDetails ebd) {
		if(svo==null || ebd==null){
			return false;
		}
		
		int expBrdId = ebd.getExpansionBoardId();
		int expBrdRev = ebd.getExpansionBoardRev();
		int expBrdRevSpecial = ebd.getExpansionBoardRevSpecial();
		
		if((svo.getHardwareVersion()==HW_ID.SHIMMER_3R) ||
				(svo.getHardwareVersion()==HW_ID.SHIMMER_3 &&	(
				(expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev>=Configuration.Shimmer3.NEW_IMU_EXP_REV.EXG_UNIFIED)			// >= SR47-3-0
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED && expBrdRev>=Configuration.Shimmer3.NEW_IMU_EXP_REV.GSR_UNIFIED) 		// >= SR48-3-0
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_BR_AMP_UNIFIED && expBrdRev>=Configuration.Shimmer3.NEW_IMU_EXP_REV.BRIDGE_AMP)	// >= SR49-2-0
				|| (expBrdId==HW_ID_SR_CODES.SHIMMER3 && expBrdRev>=Configuration.Shimmer3.NEW_IMU_EXP_REV.IMU)							// >= SR31-6-0
				|| (expBrdRevSpecial==Configuration.Shimmer3.NEW_IMU_EXP_REV.ANY_EXP_BRD_WITH_SPECIAL_REV)													// == SRx-x-171
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE && expBrdRev>=Configuration.Shimmer3.NEW_IMU_EXP_REV.PROTO3_DELUXE)	// Future unified board
				|| (expBrdId==HW_ID_SR_CODES.EXP_BRD_PROTO3_MINI && expBrdRev>=Configuration.Shimmer3.NEW_IMU_EXP_REV.PROTO3_MINI)		// Future unified board
				))){
			return true;
		}
		else {
			return false;
		}
	}
	
	public boolean isSupportedBtBleControl() {
		//TODO check S3/S3R FW version in-which command was introduced?
		BluetoothModuleVersionDetails bluetoothModuleVersionDetails = getBtFwVerDetails();
		return (bluetoothModuleVersionDetails.isBtModuleVersionKnown()
				&& !bluetoothModuleVersionDetails.isBtModuleVersionRn41()
				&& !bluetoothModuleVersionDetails.isBtModuleVersionRn42());
	}

	@Override
	public LinkedHashMap<String, ChannelDetails> getMapOfAllChannelsForStoringToDB(COMMUNICATION_TYPE commType, CHANNEL_TYPE channelType, boolean isKeyOJCName, boolean showDisabledChannels) {
		LinkedHashMap<String, ChannelDetails> mapOfChannelsForStoringToDb = super.getMapOfAllChannelsForStoringToDB(commType, channelType, isKeyOJCName, showDisabledChannels);
		
		//TODO temp hack. Need to move these channels to their own sensors so that they can be disabled per comm type
		if(commType!=COMMUNICATION_TYPE.SD || (commType==COMMUNICATION_TYPE.SD && !isSyncWhenLogging())){
			mapOfChannelsForStoringToDb.remove(SensorShimmerClock.ObjectClusterSensorName.TIMESTAMP_OFFSET);
		}
		
		if(!isRtcDifferenceSet()){
			mapOfChannelsForStoringToDb.remove(SensorShimmerClock.ObjectClusterSensorName.REAL_TIME_CLOCK);
		}
		
		return mapOfChannelsForStoringToDb;
	}
	
	@Override
	public double getSamplingClockFreq() {
		if(isTCXO()){
			if(isTcxoClock20MHz()){
				//20MHz / 64 = 312500; 
				return 312500.0;
			} else {
				//16.369MHz / 64 = 255765.625;
				return 255765.625;
			}
		} else {
			return super.getSamplingClockFreq();
		}
	}

	private boolean isTcxoClock20MHz() {
		ShimmerVerObject svo = getShimmerVerObject();
		ExpansionBoardDetails ebd = getExpansionBoardDetails();
		
		int expBrdId = ebd.getExpansionBoardId();
		int expBrdRev = ebd.getExpansionBoardRev();
		int expBrdRevSpecial = ebd.getExpansionBoardRevSpecial();

		if((svo.getHardwareVersion()==HW_ID.SHIMMER_3 || svo.getHardwareVersion()==HW_ID.SHIMMER_3R) &&	(
				(expBrdId==HW_ID_SR_CODES.EXP_BRD_EXG_UNIFIED && expBrdRev==1 && expBrdRevSpecial==1))){
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Setting this to false will disable calibration, e.g. raw accelerometer data will not be calibrated. 
	 * Main purpose of disabling calibration is for logging applications, to reduce number of computations needed to compute the data, thus reducing risk of a performance bottleneck and maximizing logging performance
	 * Disabling this can also improve performance on older Android devices
	 * @param enable enables or disables calibration. Note that this needs to be enabled when using the ID as to calculate 3D orientation, see enable3DOrientation
	 */
	public void setEnableCalibration(boolean enable) {
		mEnableCalibration = enable;
	}
	
	/**
	 * Updates the booleans mIsUsingDefaultParams in each Shimmer3 sensor with the current values
	 * Main purpose is to only query if it's using the default params once, e.g. when start streaming, as this can be resource-intensive on old Android devices
	 */
	protected void determineCalibrationParamsForIMU(){		
		if(isShimmerGen2()) {
			mSensorMMA736x.updateIsUsingDefaultLNAccelParam();
			mSensorShimmer2Mag.updateIsUsingDefaultMagParam();
			mSensorShimmer2Gyro.updateIsUsingDefaultGyroParam();
		} else if(isShimmerGen3()){
			mSensorKionixAccel.updateIsUsingDefaultLNAccelParam();
			mSensorLSM303.updateIsUsingDefaultWRAccelParam();
			mSensorLSM303.updateIsUsingDefaultMagParam();
			mSensorMpu9x50.updateIsUsingDefaultGyroParam();
		} else if(isShimmerGen3R()) {
      mSensorLSM6DSV.updateIsUsingDefaultLNAccelParam();
      mSensorLIS2DW12.updateIsUsingDefaultWRAccelParam(); 
			mSensorLIS2MDL.updateIsUsingDefaultMagParam();
      mSensorLSM6DSV.updateIsUsingDefaultGyroParam();  
		} 
	}
	
	/**
	 * Updates buildMsg to output ObjectClusters containing data in the Arrays data structure
	 * This improves performance, and can improve packet reception rate, particularly on Android
	 * Note that this has not been fully tested, and enabling this will disable the Multimap data structure
	 * @param enable
	 */
	public void enableArraysDataStructure(boolean enable) {
		mUseArraysDataStructureInObjectCluster = enable;
	}

	public void setBtFwVerDetails(BluetoothModuleVersionDetails bluetoothModuleVersionDetails) {
		this.bluetoothModuleVersionDetails = bluetoothModuleVersionDetails;
	}

	public BluetoothModuleVersionDetails getBtFwVerDetails() {
		return bluetoothModuleVersionDetails;
	}
	
	@Override
	public String getRadioModel() {
		return getBtFwVerDetails().getUserFriendlyName();
	}
}
