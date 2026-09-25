package com.shimmerresearch.driver.shimmerGq;

import java.io.Serializable;
import java.util.HashMap;

import com.shimmerresearch.driver.ConfigByteLayout;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;

/**
 * Hold the ShimmerGq's microcontroller information memory layout. This region of
 * the the microcontrollers RAM can be used to configure all properties of the
 * Shimmer when configured through a docking station using Consensys. Variables
 * stored in this class are based on firmware header files for mapping which
 * bits in each information memory byte represents various configurable settings
 * on the Shimmer.
 * 
 * @author Mark Nolan
 *
 */
public class ConfigByteLayoutShimmerGq802154 extends ConfigByteLayout implements Serializable {

	private static final long serialVersionUID = -5729543049033754281L;
	
	public int idxShimmerSamplingRate =				0;
//	public int idxBufferSize =                     	2;
	public int idxSensors0 =                        3;
	public int idxSensors1 =                        4;
	public int idxSensors2 =                        5;
	public int idxConfigSetupByte0 =              	6; //sensors setting bytes
	public int idxConfigSetupByte1 =              	7;
	public int idxConfigSetupByte2 =              	8;
	public int idxConfigSetupByte3 =              	9;
	public int idxEXGADS1292RChip1Config1 =         10;// exg bytes
	public int idxEXGADS1292RChip1Config2 =         11;
	public int idxEXGADS1292RChip1LOff =            12;
	public int idxEXGADS1292RChip1Ch1Set =          13;
	public int idxEXGADS1292RChip1Ch2Set =          14;
	public int idxEXGADS1292RChip1RldSens =         15;
	public int idxEXGADS1292RChip1LOffSens =        16;
	public int idxEXGADS1292RChip1LOffStat =        17;
	public int idxEXGADS1292RChip1Resp1 =           18;
	public int idxEXGADS1292RChip1Resp2 =           19;

	// Derived Channels - used by SW not FW
	public int idxDerivedSensors0 =		    		31;
	public int idxDerivedSensors1 =		    		32;
	public int idxDerivedSensors2 =		    		33;
	
	public int idxDerivedSensors3 =		    		0;
	public int idxDerivedSensors4 =		    		0;
	public int idxDerivedSensors5 =		    		0;
	public int idxDerivedSensors6 =		    		0;
	public int idxDerivedSensors7 =		    		0;

	public int idxSDShimmerName =                 	128+59;   // +12 bytes
	public int idxSDEXPIDName =                  	128+71;   // +12 bytes
	public int idxSDConfigTime0 =                  	128+83;   // +4 bytes
	public int idxSDConfigTime1 =                  	128+84;
	public int idxSDConfigTime2 =                  	128+85;
	public int idxSDConfigTime3 =                  	128+86;
	public int idxSDExperimentConfig0 =             128+89;
	public int idxMacAddress =                     	128+96; // 6bytes
	
	// Masks and Bitshift values
	public int maskShimmerSamplingRate =				0xFF;
	public int maskBufferSize =							0xFF;

	// Sensors
	public int maskSensors = 							0xFF;
	public int byteShiftSensors0 = 						0;
	public int byteShiftSensors1 =						8;
	public int byteShiftSensors2 =						16;
	
	//Config Byte3
	public int bitShiftGSRRange =                       1;
	public int maskGSRRange =                           0x07;
	public int bitShiftEXPPowerEnable =                 0;
	public int maskEXPPowerEnable =                     0x01;
	
	// Derived Channels - used by SW not FW
	public int maskDerivedChannelsByte =				0xFF;
	public int byteShiftDerivedSensors0 =				8*0;
	public int byteShiftDerivedSensors1 =				8*1;
	public int byteShiftDerivedSensors2 =				8*2;

	public int byteShiftDerivedSensors3 =				8*3;
	public int byteShiftDerivedSensors4 =				8*4;
	public int byteShiftDerivedSensors5 =				8*5;
	public int byteShiftDerivedSensors6 =				8*6;
	public int byteShiftDerivedSensors7 =				8*7;
	
	public int lengthShimmerName = 				12;
	public int lengthExperimentName = 			12;

	public int lengthConfigTimeBytes = 			4;
	
	public int bitShiftTimeSyncWhenLogging =	2;
	public int maskTimeSyncWhenLogging = 		0x01;

	public int lengthMacIdBytes = 				6;

	public int bitShiftSDConfigTime0 = 			24;
	public int bitShiftSDConfigTime1 = 			16;
	public int bitShiftSDConfigTime2 = 			8;
	public int bitShiftSDConfigTime3 = 			0;

	public int maskExg1_24bitFlag =			0x10<<(0*8); 
	public int maskExg2_24bitFlag =			0x08<<(0*8); 
	public int maskExg1_16bitFlag =			0x10<<(2*8); 
	public int maskExg2_16bitFlag =			0x08<<(2*8); 
	
	public int idxSrRadioChannel =                128+128+0; // 1bytes
	public int idxSrRadioGroupId =                128+128+1; // 2bytes
	public int idxSrRadioMyAddress =              128+128+3; // 2bytes
	public int idxSrRadioResponseWindow =         128+128+5; // 2bytes

	public int idxSrRadioConfigStart =            idxSrRadioChannel; // 

	public int lengthRadioConfig = 7;

	/**
	 * A NeuroLynQ node's PPG channels: bit 0 green, 1 IR, 2 red (SensorPPGNeuroLynQ.MASK_*).
	 * The byte after the radio settings, which no GQ reads; a node reads it when a session
	 * opens and writes it into each file's header byte 15 (verisense-firmware
	 * gq_file_header.c, IM_PPG_MASK). 0xFF, a byte never written, and 0 both mean green
	 * only, so an image built from zeros configures a node green only.
	 */
	public int idxNeuroLynQPpgMask =              128+128+7; // 1byte
	public int maskNeuroLynQPpgMask =             0x07;

	
	/**
	 * Hold the Shimmer3's microcontroller information memory layout. This
	 * region of the the microcontrollers RAM can be used to configure all
	 * properties of the Shimmer when configured through a docking station using
	 * Consensys. Variables stored in this class are based on firmware header
	 * files for mapping which bits in each information memory byte represents
	 * various configurable settings on the Shimmer.
	 * 
	 * @param firmwareIdentifier
	 * @param firmwareVersionMajor
	 * @param firmwareVersionMinor
	 * @param firmwareVersionInternal
	 */
	public ConfigByteLayoutShimmerGq802154(int firmwareIdentifier, int firmwareVersionMajor, int firmwareVersionMinor, int firmwareVersionInternal) {
		mShimmerVerObject = new ShimmerVerObject(firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
		
		mInfoMemSize = calculateConfigByteLength();

		MSP430_5XX_INFOMEM_D_ADDRESS = 0; 
		MSP430_5XX_INFOMEM_C_ADDRESS = 128; 
		MSP430_5XX_INFOMEM_B_ADDRESS = 256;
		MSP430_5XX_INFOMEM_A_ADDRESS = 384; 
		MSP430_5XX_INFOMEM_LAST_ADDRESS = 511;

		//Include changes to mapping below in order of oldest to newest in seperate "if statements"

		if(mShimmerVerObject.isSupportedEightByteDerivedSensors()){
			idxDerivedSensors3 =		    		118;
			idxDerivedSensors4 =		    		119;
			idxDerivedSensors5 =		    		120;
			idxDerivedSensors6 =		    		121;
			idxDerivedSensors7 =		    		122;
		}
		
//		if(Util.compareVersions(mFirmwareIdentifier,mFirmwareVersionMajor,mFirmwareVersionMinor,mFirmwareVersionInternal,FW_ID.SDLOG,0,10,1)){
//			idxMplCalibration = 128+128+128+0;
//		}
		
	}
	
	public int calculateConfigByteLength(ShimmerVerObject shimmerVersionObject) {
		
		//TODO: should add full FW version checking here to support different size InfoMems in the future
//		if(Util.compareVersions(firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionRelease,
//				FW_ID.SDLOG, 0, 10, 1)) {
//			return 512;
//		}
		
//		if(firmwareIdentifier == FW_ID.SDLOG) {
//			return 384;
//		}
//		else if(firmwareIdentifier == FW_ID.BTSTREAM) {
//			return 128;
//		}
//		else if(firmwareIdentifier == FW_ID.LOGANDSTREAM) {
//			return 384;
//		}
//		else if(firmwareIdentifier == FW_ID.GQ_GSR) {
//			return 128;
//		}
//		else {
//			return 512; 
//		}
		
		return 384;
	}
	
	@Override
	public HashMap<Integer, String> getMapOfByteDescriptions(){
		HashMap<Integer, String> mapOfByteDescriptions = new HashMap<Integer, String>();
		
		mapOfByteDescriptions.put(idxShimmerSamplingRate, "SamplingRate_LSB");
		mapOfByteDescriptions.put(idxShimmerSamplingRate+1, "SamplingRate_MSB");
		mapOfByteDescriptions.put(idxSensors0, "Sensors0");
		mapOfByteDescriptions.put(idxSensors1, "Sensors1");
		mapOfByteDescriptions.put(idxSensors2, "Sensors2");
		mapOfByteDescriptions.put(idxConfigSetupByte0, "ConfigSetupByte0");
		mapOfByteDescriptions.put(idxConfigSetupByte1, "ConfigSetupByte1");
		mapOfByteDescriptions.put(idxConfigSetupByte2, "ConfigSetupByte2");
		mapOfByteDescriptions.put(idxConfigSetupByte3, "ConfigSetupByte3");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1Config1, "EXGADS1292RChip1Config1");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1Config2, "EXGADS1292RChip1Config2");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1LOff, "EXGADS1292RChip1LOff");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1Ch1Set, "EXGADS1292RChip1Ch1Set");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1Ch2Set, "EXGADS1292RChip1Ch2Set");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1RldSens, "EXGADS1292RChip1RldSens");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1LOffSens, "EXGADS1292RChip1LOffSens");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1LOffStat, "EXGADS1292RChip1LOffStat");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1Resp1, "EXGADS1292RChip1Resp1");
		mapOfByteDescriptions.put(idxEXGADS1292RChip1Resp2, "EXGADS1292RChip1Resp2");
		mapOfByteDescriptions.put(idxDerivedSensors0, "DerivedSensors0");
		mapOfByteDescriptions.put(idxDerivedSensors1, "DerivedSensors1");
		mapOfByteDescriptions.put(idxDerivedSensors2, "DerivedSensors2");
		mapOfByteDescriptions.put(idxDerivedSensors3, "DerivedSensors3");
		mapOfByteDescriptions.put(idxDerivedSensors4, "DerivedSensors4");
		mapOfByteDescriptions.put(idxDerivedSensors5, "DerivedSensors5");
		mapOfByteDescriptions.put(idxDerivedSensors6, "DerivedSensors6");
		mapOfByteDescriptions.put(idxDerivedSensors7, "DerivedSensors7");
		
		mapOfByteDescriptions.put(idxSDShimmerName, "SDShimmerName");
		mapOfByteDescriptions.put(idxSDEXPIDName, "SDEXPIDName");
		mapOfByteDescriptions.put(idxSDConfigTime0, "SDConfigTime0");
		mapOfByteDescriptions.put(idxSDConfigTime1, "SDConfigTime1");
		mapOfByteDescriptions.put(idxSDConfigTime2, "SDConfigTime2");
		mapOfByteDescriptions.put(idxSDConfigTime3, "SDConfigTime3");
		mapOfByteDescriptions.put(idxSDExperimentConfig0, "SDExperimentConfig0");
		mapOfByteDescriptions.put(idxMacAddress, "MacAddress");

		mapOfByteDescriptions.put(idxSrRadioChannel, "RadioChannel");
		mapOfByteDescriptions.put(idxSrRadioGroupId, "RadioGroupId_MSB");
		mapOfByteDescriptions.put(idxSrRadioGroupId+1, "RadioGroupId_LSB");
		mapOfByteDescriptions.put(idxSrRadioMyAddress, "RadioMyAddress_MSB");
		mapOfByteDescriptions.put(idxSrRadioMyAddress+1, "RadioMyAddress_LSB");
		mapOfByteDescriptions.put(idxSrRadioResponseWindow, "RadioResponseWindow_MSB");
		mapOfByteDescriptions.put(idxSrRadioResponseWindow+1, "RadioResponseWindow_LSB");
		mapOfByteDescriptions.put(idxNeuroLynQPpgMask, "NeuroLynQPpgMask");
		
		return mapOfByteDescriptions;
	}
	
}
