package com.shimmerresearch.comms.wiredProtocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.shimmerresearch.comms.wiredProtocol.UartComponentPropertyDetails.PERMISSION;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;

import java.lang.reflect.*;

/**
 * Contains the packet contents for communication via the Shimmer's UART.
 * 
 * @author Mark Nolan
 *
 */
public class UartPacketDetails {
	
	public static String PACKET_HEADER = "$"; // 0x24
	
	public static final int PACKET_OVERHEAD_RESPONSE_DATA 	= 5;			// Header + CMD + LENGTH + COMP + PROP 	------------- (+ CRC MSB + CRC LSB) -> CRC already included in length 
	public static final int PACKET_OVERHEAD_RESPONSE_OTHER 	= 4; 			// Header + CMD	+ CRC MSB + CRC LSB
	
	/** Enum listing all of the Shimmer UART data packet commands */
	public static enum UART_PACKET_CMD {
		WRITE				((byte)0x01),
		DATA_RESPONSE		((byte)0x02),
		READ				((byte)0x03),
		
		BAD_CMD_RESPONSE	((byte)0xfc),	//252
		BAD_ARG_RESPONSE	((byte)0xfd),	//253
		BAD_CRC_RESPONSE	((byte)0xfe),	//254
		ACK_RESPONSE		((byte)0xff);	//255
		
	    private final byte command;

	    /** @param command */
	    private UART_PACKET_CMD(final byte command) {
	        this.command = command;
	    }
	    
	    public byte toCmdByte() {
	        return command;
	    }
	}

	/** Enum listing all of the components that can be configured using the Shimmer UART commands */
	public static enum UART_COMPONENT {
		MAIN_PROCESSOR		((byte)0x01),
		BAT					((byte)0x02), // this is treated as a sensor
		DAUGHTER_CARD		((byte)0x03),
		PPG					((byte)0x04),
		GSR					((byte)0x05),
		LSM303DLHC_ACCEL	((byte)0x06),
		MPU9X50_ACCEL		((byte)0x07),
		BEACON				((byte)0x08),
		RADIO_802154		((byte)0x09),
		RADIO_BLUETOOTH		((byte)0x0A),
		TEST				((byte)0x0B),
		/** A NeuroLynQ node's session storage (DEV-1061). A GQ answers it BAD_CMD. */
		STORAGE			((byte)0x0C);
		
	    private final byte command;

	    /** @param command */
	    private UART_COMPONENT(final byte command) {
	        this.command = command;
	    }
	    
	    public byte toCmdByte() {
	        return command;
	    }
	}
	
	public static ShimmerVerObject svoGqBle = 	new ShimmerVerObject(HW_ID.SHIMMER_GQ_BLE,FW_ID.GQ_BLE,0,0,5,ShimmerVerDetails.ANY_VERSION);
	public static ShimmerVerObject svoGq802154NR = 	new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_NR,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
	public static ShimmerVerObject svoGq802154LR = 	new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_LR,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
	public static ShimmerVerObject svoGq802154Shimmer2r  = 	new ShimmerVerObject(HW_ID.SHIMMER_2R_GQ,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
	public static ShimmerVerObject svoS3Test = 	new ShimmerVerObject(HW_ID.SHIMMER_3,FW_ID.LOGANDSTREAM,0,16,7,ShimmerVerDetails.ANY_VERSION);
	public static ShimmerVerObject svoS3RTest = 	new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,0,1,0,ShimmerVerDetails.ANY_VERSION);
	public static ShimmerVerObject svoS3RUsbComms = 	new ShimmerVerObject(HW_ID.SHIMMER_3R,FW_ID.LOGANDSTREAM,1,0,27,ShimmerVerDetails.ANY_VERSION);
	
	//TODO improve
	public static List<ShimmerVerObject> listOfCompatibleVersionInfoGqBle = Arrays.asList(svoGqBle);
	public static List<ShimmerVerObject> listOfCompatibleVersionInfoGq802154 = Arrays.asList(svoGq802154NR, svoGq802154LR, svoGq802154Shimmer2r);
	public static List<ShimmerVerObject> listOfCompatibleVersionInfoGq = Arrays.asList(svoGqBle, svoGq802154NR, svoGq802154LR, svoGq802154Shimmer2r);
	public static List<ShimmerVerObject> listOfCompatibleVersionInfoTest = Arrays.asList(svoS3Test, svoS3RTest);
	public static List<ShimmerVerObject> listOfCompatibleVersionInfoUsbDfu = Arrays.asList(svoS3RUsbComms);
	/** A NeuroLynQ node: Verisense hardware running the GQ 802.15.4 firmware (DEV-1061) */
	public static ShimmerVerObject svoNeuroLynQNode = new ShimmerVerObject(ShimmerVerDetails.ANY_VERSION,FW_ID.GQ_802154,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION,ShimmerVerDetails.ANY_VERSION);
	public static List<ShimmerVerObject> listOfCompatibleVersionInfoNeuroLynQNode = Arrays.asList(svoNeuroLynQNode);
	
	/** Class listing all of the components and property combinations that can be used with the Shimmer UART commands */
	public static class UART_COMPONENT_AND_PROPERTY {
		/** AKA the Shimmer itself or a SPAN dongle */
		public static class MAIN_PROCESSOR { 
			public static final UartComponentPropertyDetails ENABLE           = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "ENABLE");
			public static final UartComponentPropertyDetails SAMPLE_RATE      = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x01, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "SAMPLE_RATE");
			public static final UartComponentPropertyDetails MAC              = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x02, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "MAC");
			public static final UartComponentPropertyDetails VER              = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x03, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGqBle, "VER");
			public static final UartComponentPropertyDetails RTC_CFG_TIME     = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x04, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGqBle, "RTC_CFG_TIME");
			public static final UartComponentPropertyDetails CURR_LOCAL_TIME  = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x05, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "CURR_LOCAL_TIME");
			public static final UartComponentPropertyDetails INFOMEM          = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x06, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "INFOMEM");
			public static final UartComponentPropertyDetails LED0_STATE		  = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x07, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGq802154, "LED_TOGGLE");
			public static final UartComponentPropertyDetails DEVICE_BOOT	  = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x08, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGq802154, "DEVICE_BOOT");
			public static final UartComponentPropertyDetails ENTER_BOOTLOADER = new UartComponentPropertyDetails(UART_COMPONENT.MAIN_PROCESSOR, 0x09, PERMISSION.WRITE_ONLY, listOfCompatibleVersionInfoUsbDfu, "ENTER_BOOTLOADER");
		}
		public static class BAT {
			public static final UartComponentPropertyDetails ENABLE           = new UartComponentPropertyDetails(UART_COMPONENT.BAT, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "ENABLE");
			public static final UartComponentPropertyDetails VALUE            = new UartComponentPropertyDetails(UART_COMPONENT.BAT, 0x02, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGqBle, "VALUE");
			public static final UartComponentPropertyDetails FREQ_DIVIDER     = new UartComponentPropertyDetails(UART_COMPONENT.BAT, 0x06, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "DIVIDER");
		}
		public static class GSR {
			public static final UartComponentPropertyDetails ENABLE           = new UartComponentPropertyDetails(UART_COMPONENT.GSR, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "ENABLE");
			public static final UartComponentPropertyDetails RANGE            = new UartComponentPropertyDetails(UART_COMPONENT.GSR, 0x03, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "RANGE");
			public static final UartComponentPropertyDetails FREQ_DIVIDER     = new UartComponentPropertyDetails(UART_COMPONENT.GSR, 0x06, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "DIVIDER");
		}
		public static class PPG {
			public static final UartComponentPropertyDetails ENABLE           = new UartComponentPropertyDetails(UART_COMPONENT.PPG, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "ENABLE");
			public static final UartComponentPropertyDetails FREQ_DIVIDER     = new UartComponentPropertyDetails(UART_COMPONENT.PPG, 0x06, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "DIVIDER");
		}
		public static class DAUGHTER_CARD {
			public static final UartComponentPropertyDetails CARD_ID          = new UartComponentPropertyDetails(UART_COMPONENT.DAUGHTER_CARD, 0x02, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGq, "CARD_ID");
			public static final UartComponentPropertyDetails CARD_MEM         = new UartComponentPropertyDetails(UART_COMPONENT.DAUGHTER_CARD, 0x03, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGq, "CARD_MEM");
		}
		public static class LSM303DLHC_ACCEL {
			public static final UartComponentPropertyDetails ENABLE           = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "ENABLE");
			public static final UartComponentPropertyDetails DATA_RATE        = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x02, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "DATA_RATE");
			public static final UartComponentPropertyDetails RANGE            = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x03, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "RANGE");
			public static final UartComponentPropertyDetails LP_MODE          = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x04, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "LP_MODE");
			public static final UartComponentPropertyDetails HR_MODE          = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x05, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "HR_MODE");
			public static final UartComponentPropertyDetails FREQ_DIVIDER     = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x06, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "FREQ_DIVIDER");
			public static final UartComponentPropertyDetails CALIBRATION      = new UartComponentPropertyDetails(UART_COMPONENT.LSM303DLHC_ACCEL, 0x07, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "CALIBRATION");
		}
		public static class BEACON {
			public static final UartComponentPropertyDetails ENABLE           = new UartComponentPropertyDetails(UART_COMPONENT.BEACON, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "ENABLE");
			public static final UartComponentPropertyDetails FREQ_DIVIDER     = new UartComponentPropertyDetails(UART_COMPONENT.BEACON, 0x06, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGqBle, "DIVIDER");
		}
		
		public static class RADIO_802154 {
			public static final UartComponentPropertyDetails SETTINGS			= new UartComponentPropertyDetails(UART_COMPONENT.RADIO_802154, 0x00, PERMISSION.READ_WRITE, listOfCompatibleVersionInfoGq802154, "SETTINGS", true);
			public static final UartComponentPropertyDetails TX_TO_SHIMMER		= new UartComponentPropertyDetails(UART_COMPONENT.RADIO_802154, 0x05, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGq802154, "TX_TO_SHIMMER");
			public static final UartComponentPropertyDetails RX_FROM_SHIMMER	= new UartComponentPropertyDetails(UART_COMPONENT.RADIO_802154, 0x06, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGq802154, "RX_FROM_SHIMMER");
			public static final UartComponentPropertyDetails SPECTRUM_SCAN		= new UartComponentPropertyDetails(UART_COMPONENT.RADIO_802154, 0x07, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoGq802154, "SPECTRUM_SCAN");
		}

		public static class BLUETOOTH {
			public static final UartComponentPropertyDetails VER          = new UartComponentPropertyDetails(UART_COMPONENT.RADIO_BLUETOOTH, 0x03, PERMISSION.READ_ONLY, null, "BT_FW_VER");
		}
		
		/**
		 * A NeuroLynQ node's session storage (DEV-1061): verisense-firmware
		 * docs/VERISENSE_NEUROLYNQ_STORAGE.md section 7, and neurolynq.NeuroLynQStorageCodec for the bytes.
		 */
		public static class STORAGE {
			/** 36 bytes: state, geometry, capacity, sessions */
			public static final UartComponentPropertyDetails INFO             = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x00, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_INFO");
			/** GET idx u16: one session, oldest first */
			public static final UartComponentPropertyDetails SESSION          = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x01, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_SESSION");
			/** GET handle u16, first u8, max u8: up to 40 {file, size} */
			public static final UartComponentPropertyDetails FILES            = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x02, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_FILES");
			/** GET handle, file, offset u32, len u32: answered by a burst of these, then END */
			public static final UartComponentPropertyDetails READ             = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x03, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_READ");
			/** The frame that ends a READ burst. Never requested. */
			public static final UartComponentPropertyDetails END              = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x04, PERMISSION.READ_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_END");
			/** SET scope u8, handle u16, 'ERAS' */
			public static final UartComponentPropertyDetails ERASE            = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x05, PERMISSION.WRITE_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_ERASE");
			/** SET mode u8, 'FRMT': ACKed at once, progress in INFO */
			public static final UartComponentPropertyDetails FORMAT           = new UartComponentPropertyDetails(UART_COMPONENT.STORAGE, 0x06, PERMISSION.WRITE_ONLY, listOfCompatibleVersionInfoNeuroLynQNode, "STORAGE_FORMAT");
		}

		public static class DEVICE_TEST {
			public static final UartComponentPropertyDetails MAIN_TEST          = new UartComponentPropertyDetails(UART_COMPONENT.TEST, 0x00, PERMISSION.WRITE_ONLY, listOfCompatibleVersionInfoTest, "Main Test");
			public static final UartComponentPropertyDetails LED_TEST          = new UartComponentPropertyDetails(UART_COMPONENT.TEST, 0x01, PERMISSION.WRITE_ONLY, listOfCompatibleVersionInfoTest, "LED Test");
			public static final UartComponentPropertyDetails IC_TEST          = new UartComponentPropertyDetails(UART_COMPONENT.TEST, 0x02, PERMISSION.WRITE_ONLY, listOfCompatibleVersionInfoTest, "IC Test");
		}

	}

	public static final Map<String,UartComponentPropertyDetails> mMapOfUartDeviceTest;
	static {
		Map<String,UartComponentPropertyDetails> aMap = new LinkedHashMap<String,UartComponentPropertyDetails>();
        aMap.put(UART_COMPONENT_AND_PROPERTY.DEVICE_TEST.MAIN_TEST.mPropertyName,UART_COMPONENT_AND_PROPERTY.DEVICE_TEST.MAIN_TEST);
        aMap.put(UART_COMPONENT_AND_PROPERTY.DEVICE_TEST.LED_TEST.mPropertyName,UART_COMPONENT_AND_PROPERTY.DEVICE_TEST.LED_TEST);
        aMap.put(UART_COMPONENT_AND_PROPERTY.DEVICE_TEST.IC_TEST.mPropertyName,UART_COMPONENT_AND_PROPERTY.DEVICE_TEST.IC_TEST);
        mMapOfUartDeviceTest = Collections.unmodifiableMap(aMap);
    }
	
	public static final List<UartComponentPropertyDetails> mListOfUartCommandsConfig;
	static {
    	List<UartComponentPropertyDetails> aMap = new ArrayList<UartComponentPropertyDetails>();
        
        aMap.add(UART_COMPONENT_AND_PROPERTY.BAT.ENABLE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.BAT.FREQ_DIVIDER);

    	aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.ENABLE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.DATA_RATE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.RANGE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.LP_MODE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.HR_MODE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.FREQ_DIVIDER);
        aMap.add(UART_COMPONENT_AND_PROPERTY.LSM303DLHC_ACCEL.CALIBRATION);
        
        aMap.add(UART_COMPONENT_AND_PROPERTY.GSR.ENABLE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.GSR.RANGE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.GSR.FREQ_DIVIDER);

        aMap.add(UART_COMPONENT_AND_PROPERTY.BEACON.ENABLE);
        aMap.add(UART_COMPONENT_AND_PROPERTY.BEACON.FREQ_DIVIDER);

        aMap.add(UART_COMPONENT_AND_PROPERTY.RADIO_802154.SETTINGS);

        mListOfUartCommandsConfig = Collections.unmodifiableList(aMap);
    }
    
    
	public static UART_PACKET_CMD getUartCommandParsed(byte comparisonByte) {
		for(UART_PACKET_CMD command:UART_PACKET_CMD.values()){
			if(command.toCmdByte()==comparisonByte){
				return command;
			}
		}
		return null;
	}
    
	public static UART_COMPONENT getUartComponentParsed(byte comparisonByte) {
		for(UART_COMPONENT component:UART_COMPONENT.values()){
			if(component.toCmdByte()==comparisonByte){
				return component;
			}
		}
		return null;
	}

	public static UartComponentPropertyDetails getUartPropertyParsed(byte uartComponentByte, byte uartPropertyByte) {
	    Class<?>[] myClasses = UART_COMPONENT_AND_PROPERTY.class.getDeclaredClasses();
	    for (Class myClass: myClasses) {
		    Field[] f = myClass.getDeclaredFields();
		    for (Field field : f) {
		        int modifier = field.getModifiers();
		        if (Modifier.isStatic(modifier)){
					try {
						UartComponentPropertyDetails uCPD = (UartComponentPropertyDetails) field.get( null );
						if(uCPD.mComponentByte==uartComponentByte && uCPD.mPropertyByte==uartPropertyByte){
							return uCPD;
						}
					} catch (IllegalArgumentException | IllegalAccessException e) {
//						e.printStackTrace();
					}
		        }
		    }
	    }
		return null;
	}

}    
