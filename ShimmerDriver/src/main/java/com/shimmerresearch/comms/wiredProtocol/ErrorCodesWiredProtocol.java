package com.shimmerresearch.comms.wiredProtocol;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.shimmerresearch.driverUtilities.AbstractErrorCodes;

/**
 * 
 * @author Mark Nolan
 *
 */
public class ErrorCodesWiredProtocol extends AbstractErrorCodes {

	public final static int ERROR_CODES_ID = 7;
	
	// Error type/source/command
	public final static int SHIMMERUART_CMD_ERR_MAC_ID_GET = 			(ERROR_CODES_ID*1000) + 21;
	public final static int SHIMMERUART_CMD_ERR_VERSION_INFO_GET = 		(ERROR_CODES_ID*1000) + 22;
	public final static int SHIMMERUART_CMD_ERR_BATT_STATUS_GET = 		(ERROR_CODES_ID*1000) + 23;
	public final static int SHIMMERUART_CMD_ERR_RTC_CONFIG_TIME_SET = 	(ERROR_CODES_ID*1000) + 25;
	public final static int SHIMMERUART_CMD_ERR_RTC_CONFIG_TIME_GET = 	(ERROR_CODES_ID*1000) + 24;
	public final static int SHIMMERUART_CMD_ERR_RTC_CURRENT_TIME_GET = 	(ERROR_CODES_ID*1000) + 26;
	public final static int SHIMMERUART_CMD_ERR_DAUGHTER_ID_SET = 		(ERROR_CODES_ID*1000) + 28;
	public final static int SHIMMERUART_CMD_ERR_DAUGHTER_ID_GET = 		(ERROR_CODES_ID*1000) + 27;
	public final static int SHIMMERUART_CMD_ERR_DAUGHTER_MEM_SET = 		(ERROR_CODES_ID*1000) + 30;
	public final static int SHIMMERUART_CMD_ERR_DAUGHTER_MEM_GET = 		(ERROR_CODES_ID*1000) + 29;
	public final static int SHIMMERUART_CMD_ERR_INFOMEM_SET = 			(ERROR_CODES_ID*1000) + 32;
	public final static int SHIMMERUART_CMD_ERR_INFOMEM_GET = 			(ERROR_CODES_ID*1000) + 31;
	
	public final static int SHIMMERUART_CMD_ERR_BT_FW_VERSION_INFO_GET = (ERROR_CODES_ID*1000) + 37;
	public final static int SHIMMERUART_CMD_ERR_ENTER_BOOTLOADER_SET =  (ERROR_CODES_ID*1000) + 38;

	// A NeuroLynQ node's storage component (DEV-1061)
	public final static int SHIMMERUART_CMD_ERR_STORAGE_INFO_GET = 		(ERROR_CODES_ID*1000) + 40;
	public final static int SHIMMERUART_CMD_ERR_STORAGE_SESSION_GET = 	(ERROR_CODES_ID*1000) + 41;
	public final static int SHIMMERUART_CMD_ERR_STORAGE_FILES_GET = 		(ERROR_CODES_ID*1000) + 42;
	public final static int SHIMMERUART_CMD_ERR_STORAGE_READ = 			(ERROR_CODES_ID*1000) + 43;
	public final static int SHIMMERUART_CMD_ERR_STORAGE_ERASE_SET = 		(ERROR_CODES_ID*1000) + 44;
	public final static int SHIMMERUART_CMD_ERR_STORAGE_FORMAT_SET = 	(ERROR_CODES_ID*1000) + 45;

	//SR7 (802.15.4 radio) related errors
	public final static int SHIMMERUART_CMD_ERR_RADIO_802154_SET_SETTINGS = 			(ERROR_CODES_ID*1000) + 33;
	public final static int SHIMMERUART_CMD_ERR_RADIO_802154_GET_SETTINGS = 			(ERROR_CODES_ID*1000) + 34;
	public final static int SHIMMERUART_CMD_ERR_RADIO_802154_SPECTRUM_ANALYSER = 		(ERROR_CODES_ID*1000) + 35;
	public final static int SHIMMERUART_CMD_ERR_RADIO_802154_RAW = 		(ERROR_CODES_ID*1000) + 36;

	
	// Errors related to specific commands
	public final static int SHIMMERUART_INFOMEM_WRITE_BUFFER_EXCEEDS_INFO_RANGE = (ERROR_CODES_ID*1000) + 1;
	public final static int SHIMMERUART_INFOMEM_READ_REQEST_EXCEEDS_INFO_RANGE = (ERROR_CODES_ID*1000) + 2;
	
    // low level errors
	public final static int ERR_NONE = 0;
	public final static int SHIMMERUART_COMM_ERR_TIMEOUT = 				(ERROR_CODES_ID*1000) + 5;
	public final static int SHIMMERUART_COMM_ERR_CRC = 					(ERROR_CODES_ID*1000) + 6;
	public final static int SHIMMERUART_COMM_ERR_RESPONSE_BAD_CMD = 	(ERROR_CODES_ID*1000) + 10;
	public final static int SHIMMERUART_COMM_ERR_RESPONSE_BAD_ARG = 	(ERROR_CODES_ID*1000) + 11;
	public final static int SHIMMERUART_COMM_ERR_RESPONSE_BAD_CRC = 	(ERROR_CODES_ID*1000) + 12;
	public final static int SHIMMERUART_COMM_ERR_RESPONSE_UNEXPECTED = 	(ERROR_CODES_ID*1000) + 13;
	public final static int SHIMMERUART_COMM_ERR_MESSAGE_CONTENTS = 	(ERROR_CODES_ID*1000) + 14;
	
	
	public final static int SHIMMERUART_COMM_ERR_MAC_CHANGE = 			(ERROR_CODES_ID*1000) + 17;
	
	
    public static final Map<Integer, String> mMapOfErrorCodes;
    static {
        Map<Integer, String> aMap = new TreeMap<Integer,String>();
    	aMap.put(ERR_NONE, "SHIMMERUART_COMM_ERR_NONE");

    	// Error type/source/command
    	aMap.put(SHIMMERUART_CMD_ERR_MAC_ID_GET, "Error reading MAC ID");
    	aMap.put(SHIMMERUART_CMD_ERR_VERSION_INFO_GET, "Error reading hw fw Shimmer version");
    	aMap.put(SHIMMERUART_CMD_ERR_BATT_STATUS_GET, "Error Reading Batt Status");
    	aMap.put(SHIMMERUART_CMD_ERR_RTC_CONFIG_TIME_SET, "Error writing RTC time");
    	aMap.put(SHIMMERUART_CMD_ERR_RTC_CONFIG_TIME_GET, "Error reading RTC config time");
    	aMap.put(SHIMMERUART_CMD_ERR_RTC_CURRENT_TIME_GET, "Error reading RTC current time");
    	aMap.put(SHIMMERUART_CMD_ERR_DAUGHTER_ID_SET, "Error writing expansion board ID");
    	aMap.put(SHIMMERUART_CMD_ERR_DAUGHTER_ID_GET, "Error reading expansion board ID");
    	aMap.put(SHIMMERUART_CMD_ERR_DAUGHTER_MEM_SET, "Error writing expansion board memory");
    	aMap.put(SHIMMERUART_CMD_ERR_DAUGHTER_MEM_GET, "Error reading expansion board memory");
    	aMap.put(SHIMMERUART_CMD_ERR_INFOMEM_SET, "Error writing InfoMem");
    	aMap.put(SHIMMERUART_CMD_ERR_INFOMEM_GET, "Error reading InfoMem");

    	aMap.put(SHIMMERUART_CMD_ERR_BT_FW_VERSION_INFO_GET, "Error reading BT FW version info");
    	aMap.put(SHIMMERUART_CMD_ERR_ENTER_BOOTLOADER_SET, "Error writing enter bootloader command");

    	aMap.put(SHIMMERUART_CMD_ERR_STORAGE_INFO_GET, "Error reading the node's storage information");
    	aMap.put(SHIMMERUART_CMD_ERR_STORAGE_SESSION_GET, "Error reading a session from the node's storage");
    	aMap.put(SHIMMERUART_CMD_ERR_STORAGE_FILES_GET, "Error reading a session's files from the node's storage");
    	aMap.put(SHIMMERUART_CMD_ERR_STORAGE_READ, "Error reading a file from the node's storage");
    	aMap.put(SHIMMERUART_CMD_ERR_STORAGE_ERASE_SET, "Error erasing sessions from the node's storage");
    	aMap.put(SHIMMERUART_CMD_ERR_STORAGE_FORMAT_SET, "Error formatting the node's storage");

    	//SR7 (802.15.4 radio) related errors
    	aMap.put(SHIMMERUART_CMD_ERR_RADIO_802154_GET_SETTINGS, "Error reading 802.15.4 radio settings");
    	aMap.put(SHIMMERUART_CMD_ERR_RADIO_802154_SET_SETTINGS, "Error writing 802.15.4 radio settings");
    	aMap.put(SHIMMERUART_CMD_ERR_RADIO_802154_SPECTRUM_ANALYSER, "Error reading spectrum analyser");
    	
    	// Errors related to specific commands
        aMap.put(SHIMMERUART_INFOMEM_WRITE_BUFFER_EXCEEDS_INFO_RANGE, "InfoMem write buffer exceeds InfoMem range");
        aMap.put(SHIMMERUART_INFOMEM_READ_REQEST_EXCEEDS_INFO_RANGE, "InfoMem read request exceeds InfoMem range");
        
        // low level errors
    	aMap.put(SHIMMERUART_COMM_ERR_TIMEOUT, "COM port timeout");
    	aMap.put(SHIMMERUART_COMM_ERR_CRC, "Bad CRC on received packet");
    	aMap.put(SHIMMERUART_COMM_ERR_RESPONSE_BAD_CMD, "Device responded with Bad_CMD");
    	aMap.put(SHIMMERUART_COMM_ERR_RESPONSE_BAD_ARG, "Device responded with Bad_ARG");
    	aMap.put(SHIMMERUART_COMM_ERR_RESPONSE_BAD_CRC, "Device responded with Bad_CRC");
    	aMap.put(SHIMMERUART_COMM_ERR_RESPONSE_UNEXPECTED, "ShimmerUART_Comm_Err_Response_Unexpected");
    	aMap.put(SHIMMERUART_COMM_ERR_MESSAGE_CONTENTS, "ShimmerUART_Comm_Err_Message_Content");
    	
    	aMap.put(SHIMMERUART_COMM_ERR_MAC_CHANGE, "Change of Shimmer before an undock was detected");
    	
    	mMapOfErrorCodes = Collections.unmodifiableMap(aMap);
    }
   
}
