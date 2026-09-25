package com.shimmerresearch.driver;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.shimmerresearch.comms.wiredProtocol.ErrorCodesWiredProtocol;
import com.shimmerresearch.driverUtilities.DockJobDetails;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.DEVICE_STATE;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.exceptions.ShimmerException.ExceptionLevel;


/**
 * @author JC, Mark Nolan
 *
 */
public class MsgDock {

	//callback msg options
	public final static int MSG_ID_UNKNOWN = 0;

	public final static int MSG_ID_SOURCE_SPAN_MANAGER = 1000;
	public final static int MSG_ID_SOURCE_PLOT_MANAGER = 1001;
	public final static int MSG_ID_SOURCE_BLUETOOTH_MANAGER = 1002;
	public final static int MSG_ID_SOURCE_DOCK_MANAGER = 1003;
	public final static int MSG_ID_SOURCE_PLATFORM_MANAGER = 1004;
	public final static int MSG_ID_SOURCE_ALGORITHM = 1005;
	public final static int MSG_ID_SOURCE_ALGORITHM_MANAGER = 1006;
	public final static int MSG_ID_SOURCE_EVENT_MARKERS = 1007;
	public final static int MSG_ID_SOURCE_RESULT_AGGREGATOR = 1008;
	public final static int MSG_ID_SOURCE_RESULT_AGGREGATOR_STOP = 1009;
	public final static int MSG_ID_SOURCE_DATABASE_MANAGER = 1010;
	public final static int MSG_ID_SOURCE_DATA_IMPORT_MANAGER = 1011;
	public final static int MSG_ID_SOURCE_DATA_PROCESS_MANAGER = 1012;
	public final static int MSG_ID_SOURCE_VIDEO_MANAGER = 1013;
	public final static int MSG_ID_SOURCE_SIMULATOR = 1014;
	public final static int MSG_ID_SOURCE_NEUROHOME_SERVER = 1015;
	public final static int MSG_ID_SOURCE_S3_UPLOAD_MANAGER = 1016;
	public final static int MSG_ID_SOURCE_S3_DOWNLOAD_MANAGER = 1017;
	

	// --------------- DockManager Start -------------------------
	public final static int MSG_ID_BSL_FW_WRITE_SUCCESS = 30;
	public final static int MSG_ID_BSL_FW_WRITE_PROGRESS = 31; 
	public final static int MSG_ID_BSL_FW_WRITE_FAIL = 32;
	public final static int MSG_ID_BSL_FW_WRITE_BUSY = 33;
	public final static int MSG_ID_BSL_FW_WRITE_PROGRESS_LOG = 34;
	public final static int MSG_ID_BSL_FW_WRITE_FINISHED_PER_DOCK = 35;
	public final static int MSG_ID_BSL_FW_WRITE_FINISHED_ALL = 36;
	
	public final static int MSG_ID_DOCK_SLOT_DOCKED = 40;
	public final static int MSG_ID_DOCK_SLOT_REMOVED = 41;
	public final static int MSG_ID_SMARTDOCK_NO_STATE = 44;
	public final static int MSG_ID_SMARTDOCK_IS_BUSY = 42;
	public final static int MSG_ID_SMARTDOCK_IS_FREE = 43;
	public final static int MSG_ID_SMARTDOCK_ACTIVE_SLOT_CHANGE = 44;
//	public final static int MSG_ID_SMARTDOCK_ERROR = 49; //USED TO INDICATE CASES WHERE THE SHIMMER SHOULD BE REDOCKED
	
	public final static int MSG_ID_SHIMMER_DETAILS_CLEARED = 45;
	
	public final static int MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_SUCCESS = 46;
	public final static int MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_ERROR = 47;
	public final static int MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_FAIL = 48;
	public final static int MSG_ID_SHIMMERUART_INFOMEM_WRITE_SUCCESS = 49;
	public final static int MSG_ID_SHIMMERUART_INFOMEM_WRITE_FAIL = 50;
	public final static int MSG_ID_SHIMMERUART_INFOMEM_WRITE_PROGRESS = 51;
	public final static int MSG_ID_SHIMMERUART_PACKET_RX = 52;
	public final static int MSG_ID_SHIMMERUART_UNEXPECTED_PACKET = 59;
	
	public final static int MSG_ID_DOCK_INFOMEM_WRITE_FINISHED_PER_DOCK = 53;
	public final static int MSG_ID_DOCK_INFOMEM_WRITE_FINISHED_ALL = 54;
	
	public final static int MSG_ID_DOCK_JOB_STARTED_PER_DOCK = 56;
	public final static int MSG_ID_DOCK_JOB_SUCCESS_PER_DOCK = 57;
	public final static int MSG_ID_DOCK_JOB_FAIL_PER_DOCK = 58;

	/** A NeuroLynQ node's sessions coming off it during an SD scan, as it has no card:
	 * {@link #mBytesDone} of {@link #mBytesTotal} (DEV-1061) */
	public final static int MSG_ID_DOCK_NODE_DOWNLOAD_PROGRESS = 60;
	
	public final static int MSG_ID_DOCKMANAGER_INITIALIZED_SUCCESS = 91;
	public final static int MSG_ID_DOCKMANAGER_INITIALIZED_FAIL = 92;
	public final static int MSG_ID_DOCK_PLUGGED_UNPLUGGED = 93;
	public final static int MSG_ID_DOCK_INITIALISED_STATE_CHANGE = 94;
	
	public final static int MSG_ID_DOCK_STATE_CHANGE = 100;
	public final static int MSG_ID_DOCK_SD_SCAN_UPDATE = 101;
	public final static int MSG_ID_DOCK_SD_SCAN_SUCCESS = 102;
	public final static int MSG_ID_DOCK_SD_SCAN_FAIL = 103;
	public final static int MSG_ID_DOCK_SD_SCAN_FINISHED_PER_DOCK = 104; 
	public final static int MSG_ID_DOCK_SD_COPY_PER_FILE_COMPLETE = 105;
	public final static int MSG_ID_DOCK_SD_COPY_PER_FILE_START = 106;
	public final static int MSG_ID_DOCK_SD_COPY_SUCCESS = 107;
	public final static int MSG_ID_DOCK_SD_COPY_FAILED = 108;

//	public final static int MSG_ID_DOCK_SD_SCAN_COMPLETED = 110;
	public final static int MSG_ID_DOCK_SD_COPY_COMPLETED = 111;
	public final static int MSG_ID_DOCK_SD_CLEAR_UPDATE = 112;
	public final static int MSG_ID_DOCK_SD_CLEAR_FAILED = 113;
	public final static int MSG_ID_DOCK_SD_CLEAR_FINISHED_PER_DOCK = 114;
	public final static int MSG_ID_DOCK_SD_CLEAR_SUCCESS = 115;
//	public final static int MSG_ID_DOCK_WRITE_INFOMEM_UPDATE = 113;
//	public final static int MSG_ID_DOCK_WRITE_INFOMEM_COMPLETED = 114;

	public final static int MSG_ID_DOCK_OPERATION_PROGRESS = 200;
	public final static int MSG_ID_DOCK_OPERATION_FINISHED = 201;
	public final static int MSG_ID_DOCK_OPERATION_CANCELLED = 202;

	//TODO move closer to SmartDockUart?
	//TODO rename UART to MSG?
//	public final static int MSG_ID_SMARTDOCK_UART_ERR_NONE = 110;
	public final static int MSG_ID_SMARTDOCK_UART_AUTONOTIFY_MESSAGE = 111;
	public final static int MSG_ID_SMARTDOCK_UART_AUTONOTIFY_STATE_ON = 112;
	public final static int MSG_ID_SMARTDOCK_UART_AUTONOTIFY_STATE_OFF = 113;
	public final static int MSG_ID_SMARTDOCK_UART_QUERY_SLOTS = 114;
	public final static int MSG_ID_SMARTDOCK_UART_HWFW_VERSION = 115;
	public final static int MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_WITHOUT_SD = 116;
	public final static int MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_WITH_SD = 117;
	public final static int MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_DISCONNECTED = 118;
	public final static int MSG_ID_SMARTDOCK_UART_BOOT_MESSAGE = 119;
	public final static int MSG_ID_SMARTDOCK_UART_SLOT_MAP_UPDATE = 120;
	public final static int MSG_ID_SMARTDOCK_UART_BSL_MASK_DOCK_STATE = 121;
	public final static int MSG_ID_SMARTDOCK_UART_BSL_MASK_SHIMMER_STATE = 122;
	public final static int MSG_ID_SMARTDOCK_UART_GPIO_TO_SHIMMERS = 123;
	public final static int MSG_ID_SMARTDOCK_UART_ALL_SHIMMER_RST = 124;
	public final static int MSG_ID_SMARTDOCK_UART_INDICATOR_LEDS = 125;
	public final static int MSG_ID_SMARTDOCK_UART_ERROR = 129;

	// --------------- DockManager End -------------------------

	// --------------- SpanManager Start -------------------------
	public final static int MSG_ID_SPANMANAGER_INITIALIZED_SUCCESS = 95;
	// --------------- SpanManager end -------------------------

	
	//ImportManager and DataprocessingManager
	public final static int MSG_ID_DATA_SYNC_UPDATE = 109;
	
	//ImportManager
	public final static int MSG_ID_DATA_SESSION_FINISHED = 110;
	
	//DockManager and ImportManager
	public final static int MSG_ID_DATA_OPERATION_PROGRESS = 300;
	public final static int MSG_ID_DATA_OPERATION_FINISHED = 301;
	
	//DataprocessingManager
	public final static int MSG_ID_PROCESS_DATA_OPERATION_PROGRESS = 400;
	public final static int MSG_ID_PROCESS_DATA_OPERATION_FINISHED = 401;
	
	public final static int MSG_ID_APPLY_EVENTS_UPDATE = 410;
	public final static int MSG_ID_APPLY_SD_CARD_GSR_HRV_PROCESSING = 411;
	
	public final static int MSG_ID_CLEARSKY_ALG_UPDATE = 430;
	public final static int MSG_ID_CLEARSKY_ALG_FINISHED = 431;
	public final static int MSG_ID_CLEARSKY_ALG_FAILED = 432;

	public final static int MSG_ID_APPLY_ZSCORE_RESULT_AGGREGATOR_PROCESSING = 500; //progress 0 - 100%
	public final static int MSG_ID_APPLY_ZSCORE_RESULT_AGGREGATOR_SUCCESS = 501;
	public final static int MSG_ID_APPLY_ZSCORE_RESULT_AGGREGATOR_FAILURE = 502;
	
	public final static int MSG_ID_PROCESS_ZSCORE_UPDATE = 510; //progress 0 - 100%
	public final static int MSG_ID_PROCESS_ZSCORE_SUCCESS = 511;
	public final static int MSG_ID_PROCESS_ZSCORE_FAILURE = 512;
	public final static int MSG_ID_PROCESS_ZSCORE_CANCELLED = 513;
	public final static int MSG_ID_PROCESS_ZSCORE_DELETE_TEMP_DATA = 514;
	
	public final static int MSG_ID_PROCESS_PEAKS_UPDATE = 520; //progress 0 - 100%
	public final static int MSG_ID_PROCESS_PEAKS_SUCCESS = 521;
	public final static int MSG_ID_PROCESS_PEAKS_FAILURE = 522;
	public final static int MSG_ID_PROCESS_PEAKS_CANCELLED  = 523;

	
	//ImportManager
	public final static int MSG_ID_IMPORT_DB_PARSER_UPDATE = 130;
	public final static int MSG_ID_IMPORT_DB_PARSER_SUCCESS = 131;
	public final static int MSG_ID_IMPORT_DB_PARSER_FAILURE_SQLITE_EXCEPTION = 132;
	public final static int MSG_ID_IMPORT_DB_PARSER_FAILURE_IO_EXCEPTION = 133;
	
	public final static int MSG_ID_IMPORT_DB_SYNC_FAILURE_SQLITE_EXCEPTION = 134;

	//this three are used for clearsky
	public final static int MSG_ID_IMPORT_DATA_PARSER_TO_FILE_UPDATE = 137;
	public final static int MSG_ID_IMPORT_DATA_PARSER_TO_FILE_SUCCESS = 138;
	public final static int MSG_ID_IMPORT_DATA_PARSER_TO_FILE_FAILURE_IO_EXCEPTION = 139;	

	//PlotManager
//	public final static int MSG_ID_PLOT_UPDATE = 150;
//	public final static int MSG_ID_PLOT_CLOSED = 151;
//	public final static int MSG_ID_PLOT_OUT_OF_BOUNDS = 152;
	
	public final static int MSG_ID_EVENT_PULSE = 160;
	public final static int MSG_ID_EVENT_TOGGLE_START = 161;
	public final static int MSG_ID_EVENT_TOGGLE_END = 162;
	public final static int MSG_ID_EVENT_TRIAL_AND_CONFIG_TIME = 163;
	
	//DatabaseManager
	public final static int MSG_ID_BT_TO_DB_FINISHED = 170;
	public final static int MSG_ID_BT_TO_DB_BUFFER_FILLED = 171;
	public final static int MSG_ID_BT_TO_DB_FAIL = 172;
	public final static int MSG_ID_BT_TO_DB_STARTED = 173;
	public final static int MSG_ID_DB_NUM_DB = 180;
	public final static int MSG_ID_DB_READING_DB_START = 181;
	public final static int MSG_ID_DB_READING_DB_END = 182;
	public final static int MSG_ID_DB_MANAGER_LOADED_SUCCESS = 183;
	public final static int MSG_ID_DB_PLAYBACK_TIMER_UPDATE = 184;
	public final static int MSG_ID_DB_PLAYBACK_STARTED_STOPPED = 185;
	public final static int MSG_ID_BT_TO_DB_RECORDING_THREAD_STARTED = 186;
	
	//AlgorithmManager
	public final static int MSG_ID_DATA_TO_ALGO = 190;
	
	public final static int MSG_ID_NEURO_MANAGER_END_TRIAL = 1018;
	
    public static final Map<Integer, String> mMapOfMsgCodes;
    static {
        Map<Integer, String> aMap = new TreeMap<Integer,String>();
        aMap.put(MSG_ID_UNKNOWN, "MSG_ID_UNKNOWN");
        aMap.put(MSG_ID_BSL_FW_WRITE_SUCCESS, "MSG_ID_BSL_FW_WRITE_SUCCESS");
        aMap.put(MSG_ID_BSL_FW_WRITE_PROGRESS, "MSG_ID_BSL_FW_WRITE_PROGRESS");
        aMap.put(MSG_ID_BSL_FW_WRITE_FAIL, "MSG_ID_BSL_FW_WRITE_FAIL");
        aMap.put(MSG_ID_BSL_FW_WRITE_BUSY, "MSG_ID_BSL_FW_WRITE_BUSY");
        aMap.put(MSG_ID_BSL_FW_WRITE_PROGRESS_LOG, "MSG_ID_BSL_FW_WRITE_PROGRESS_LOG");
        aMap.put(MSG_ID_BSL_FW_WRITE_FINISHED_PER_DOCK, "MSG_ID_BSL_FW_WRITE_FINISHED_PER_DOCK");
        aMap.put(MSG_ID_BSL_FW_WRITE_FINISHED_ALL, "MSG_ID_BSL_FW_WRITE_FINISHED_ALL");
        aMap.put(MSG_ID_DOCK_SLOT_DOCKED, "MSG_ID_SMARTDOCK_SLOT_DOCKED");
        aMap.put(MSG_ID_DOCK_SLOT_REMOVED, "MSG_ID_SMARTDOCK_SLOT_REMOVED");
        aMap.put(MSG_ID_SMARTDOCK_NO_STATE, "MSG_ID_SMARTDOCK_NO_STATE");
        aMap.put(MSG_ID_SMARTDOCK_IS_BUSY, "MSG_ID_SMARTDOCK_IS_BUSY");
        aMap.put(MSG_ID_SMARTDOCK_IS_FREE, "MSG_ID_SMARTDOCK_IS_FREE");
        aMap.put(MSG_ID_SMARTDOCK_ACTIVE_SLOT_CHANGE, "MSG_ID_SMARTDOCK_ACTIVE_SLOT_CHANGE");
//        aMap.put(MSG_ID_SMARTDOCK_ERROR, "MSG_ID_SMARTDOCK_ERROR");
        aMap.put(MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_SUCCESS, "MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_SUCCESS");
        aMap.put(MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_ERROR, "MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_ERROR");
        aMap.put(MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_FAIL, "MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_FAIL");
        aMap.put(MSG_ID_SHIMMERUART_INFOMEM_WRITE_SUCCESS, "MSG_ID_SHIMMERUART_INFOMEM_WRITE_SUCCESS");
        aMap.put(MSG_ID_SHIMMERUART_INFOMEM_WRITE_FAIL, "MSG_ID_SHIMMERUART_INFOMEM_WRITE_FAIL");
        aMap.put(MSG_ID_SHIMMERUART_PACKET_RX, "MSG_ID_SHIMMERUART_PACKET_RX");
        aMap.put(MSG_ID_SHIMMERUART_UNEXPECTED_PACKET, "MSG_ID_SHIMMERUART_UNEXPECTED_PACKET");
        
        aMap.put(MSG_ID_DOCK_INFOMEM_WRITE_FINISHED_PER_DOCK, "MSG_ID_DOCK_INFOMEM_WRITE_FINISHED_PER_DOCK");
        aMap.put(MSG_ID_DOCK_INFOMEM_WRITE_FINISHED_ALL, "MSG_ID_DOCK_INFOMEM_WRITE_FINISHED_ALL");
        
        aMap.put(MSG_ID_DOCK_JOB_STARTED_PER_DOCK, "MSG_ID_DOCK_JOB_STARTED_PER_DOCK");
        aMap.put(MSG_ID_DOCK_JOB_SUCCESS_PER_DOCK, "MSG_ID_DOCK_JOB_SUCCESS_PER_DOCK");
        aMap.put(MSG_ID_DOCK_JOB_FAIL_PER_DOCK, "MSG_ID_DOCK_JOB_FAIL_PER_DOCK");
        aMap.put(MSG_ID_DOCK_NODE_DOWNLOAD_PROGRESS, "MSG_ID_DOCK_NODE_DOWNLOAD_PROGRESS");
        
        aMap.put(MSG_ID_DOCKMANAGER_INITIALIZED_SUCCESS, "MSG_ID_DOCKMANAGER_INITIALIZED_SUCCESS");
        aMap.put(MSG_ID_DOCKMANAGER_INITIALIZED_FAIL, "MSG_ID_DOCKMANAGER_INITIALIZED_FAIL");
        aMap.put(MSG_ID_DOCK_PLUGGED_UNPLUGGED, "MSG_ID_DOCK_PLUGGED_UNPLUGGED");
        aMap.put(MSG_ID_DOCK_INITIALISED_STATE_CHANGE, "MSG_ID_DOCK_INITIALISED_STATE_CHANGE");
        
        aMap.put(MSG_ID_DOCK_STATE_CHANGE, "MSG_ID_DOCK_STATE_CHANGE");
        aMap.put(MSG_ID_SHIMMERUART_INFOMEM_WRITE_PROGRESS, "MSG_ID_SHIMMERUART_INFOMEM_WRITE_PROGRESS");
        aMap.put(MSG_ID_DOCK_SD_SCAN_UPDATE, "MSG_ID_DOCK_SD_SCAN_UPDATE");
        aMap.put(MSG_ID_DOCK_SD_SCAN_SUCCESS, "MSG_ID_DOCK_SD_SCAN_SUCCESS");
        aMap.put(MSG_ID_DOCK_SD_SCAN_FAIL, "MSG_ID_DOCK_SD_SCAN_FAILED");
        aMap.put(MSG_ID_DOCK_SD_SCAN_FINISHED_PER_DOCK, "MSG_ID_DOCK_SD_SCAN_FINISHED_PER_DOCK");
        aMap.put(MSG_ID_DOCK_SD_COPY_PER_FILE_COMPLETE, "MSG_ID_DOCK_SD_COPY_UPDATE");
        aMap.put(MSG_ID_DOCK_SD_COPY_SUCCESS, "MSG_ID_DOCK_SD_COPY_SUCCESS");
        aMap.put(MSG_ID_DOCK_SD_COPY_FAILED, "MSG_ID_DOCK_SD_COPY_FAILED");
        aMap.put(MSG_ID_DOCK_SD_COPY_COMPLETED, "MSG_ID_DOCK_SD_COPY_COMPLETED");
        aMap.put(MSG_ID_DOCK_SD_CLEAR_UPDATE, "MSG_ID_DOCK_SD_CLEAR_UPDATE");
        aMap.put(MSG_ID_DATA_SYNC_UPDATE, "MSG_ID_DATA_SYNC_UPDATE");
        
//        aMap.put(MSG_ID_SMARTDOCK_UART_ERR_NONE, "MSG_ID_SMARTDOCK_UART_ERR_NONE");
        aMap.put(MSG_ID_SMARTDOCK_UART_AUTONOTIFY_MESSAGE, "MSG_ID_SMARTDOCK_UART_AUTONOTIFY_MESSAGE");
        aMap.put(MSG_ID_SMARTDOCK_UART_AUTONOTIFY_STATE_ON, "MSG_ID_SMARTDOCK_UART_AUTONOTIFY_STATE_ON");
        aMap.put(MSG_ID_SMARTDOCK_UART_AUTONOTIFY_STATE_OFF, "MSG_ID_SMARTDOCK_UART_AUTONOTIFY_STATE_OFF");
        aMap.put(MSG_ID_SMARTDOCK_UART_QUERY_SLOTS, "MSG_ID_SMARTDOCK_UART_QUERY_SLOTS");
        aMap.put(MSG_ID_SMARTDOCK_UART_HWFW_VERSION, "MSG_ID_SMARTDOCK_UART_HWFW_VERSION");
        aMap.put(MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_WITHOUT_SD, "MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_WITHOUT_SD");
        aMap.put(MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_WITH_SD, "MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_WITH_SD");
        aMap.put(MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_DISCONNECTED, "MSG_ID_SMARTDOCK_UART_ACTIVE_SLOT_DISCONNECTED");
        aMap.put(MSG_ID_SMARTDOCK_UART_BOOT_MESSAGE, "MSG_ID_SMARTDOCK_UART_BOOT_MESSAGE");
        aMap.put(MSG_ID_SMARTDOCK_UART_SLOT_MAP_UPDATE, "MSG_ID_SMARTDOCK_UART_SLOT_MAP_UPDATE");
        
        aMap.put(MSG_ID_SMARTDOCK_UART_BSL_MASK_DOCK_STATE, "MSG_ID_SMARTDOCK_UART_BSL_MASK_DOCK_STATE");
        aMap.put(MSG_ID_SMARTDOCK_UART_BSL_MASK_SHIMMER_STATE, "MSG_ID_SMARTDOCK_UART_BSL_MASK_SHIMMER_STATE");
        aMap.put(MSG_ID_SMARTDOCK_UART_GPIO_TO_SHIMMERS, "MSG_ID_SMARTDOCK_UART_GPIO_TO_SHIMMERS");
        aMap.put(MSG_ID_SMARTDOCK_UART_ALL_SHIMMER_RST, "MSG_ID_SMARTDOCK_UART_ALL_SHIMMER_RST");
        aMap.put(MSG_ID_SMARTDOCK_UART_INDICATOR_LEDS, "MSG_ID_SMARTDOCK_UART_INDICATOR_LEDS");
        
        aMap.put(MSG_ID_SMARTDOCK_UART_ERROR, "MSG_ID_SMARTDOCK_UART_ERROR");
        
        aMap.put(MSG_ID_DOCK_OPERATION_PROGRESS, "MSG_ID_OPERATION_PROGRESS");
        aMap.put(MSG_ID_DOCK_OPERATION_FINISHED, "MSG_ID_OPERATION_FINISHED");
        aMap.put(MSG_ID_DOCK_OPERATION_CANCELLED, "MSG_ID_DOCK_OPERATION_CANCELLED");
        
        aMap.put(MSG_ID_DATA_OPERATION_PROGRESS, "MSG_ID_DATA_OPERATION_PROGRESS");
        aMap.put(MSG_ID_DATA_OPERATION_FINISHED, "MSG_ID_DATA_OPERATION_FINISHED");
        
        aMap.put(MSG_ID_IMPORT_DB_PARSER_UPDATE, "MSG_IDENTIFIER_DB_PARSER_UPDATE");
        aMap.put(MSG_ID_IMPORT_DB_PARSER_SUCCESS, "MSG_IDENTIFIER_DB_PARSER_SUCCESS");
        aMap.put(MSG_ID_IMPORT_DB_PARSER_FAILURE_SQLITE_EXCEPTION, "MSG_IDENTIFIER_DB_PARSER_FAILURE_SQLITE_EXCEPTION");
        aMap.put(MSG_ID_IMPORT_DB_PARSER_FAILURE_IO_EXCEPTION, "MSG_IDENTIFIER_DB_PARSER_FAILURE_IO_EXCEPTION");
        
        aMap.put(MSG_ID_IMPORT_DB_SYNC_FAILURE_SQLITE_EXCEPTION, "MSG_IDENTIFIER_DB_SYNC_FAILURE_SQLITE_EXCEPTION");
        
        aMap.put(MSG_ID_IMPORT_DATA_PARSER_TO_FILE_UPDATE, "MSG_IDENTIFIER_DATA_PARSER_TO_FILE_UPDATE");
        aMap.put(MSG_ID_IMPORT_DATA_PARSER_TO_FILE_SUCCESS, "MSG_IDENTIFIER_DATA_PARSER_TO_FILE_SUCCESS");
        aMap.put(MSG_ID_IMPORT_DATA_PARSER_TO_FILE_FAILURE_IO_EXCEPTION, "MSG_IDENTIFIER_DATA_PARSER_TO_FILE_FAILURE_IO_EXCEPTION");
        
//        aMap.put(MSG_ID_PLOT_UPDATE, "MSG_ID_PLOT_UPDATE");
//        aMap.put(MSG_ID_PLOT_CLOSED, "MSG_ID_PLOT_CLOSED");
//        aMap.put(MSG_ID_PLOT_OUT_OF_BOUNDS, "MSG_ID_PLOT_OUT_OF_BOUNDS");

        aMap.put(MSG_ID_BT_TO_DB_FINISHED, "MSG_ID_BT_TO_DB_FINISHED");
        aMap.put(MSG_ID_BT_TO_DB_BUFFER_FILLED, "MSG_ID_BT_TO_DB_BUFFER_FILLED");
        aMap.put(MSG_ID_BT_TO_DB_RECORDING_THREAD_STARTED, "MSG_ID_BT_TO_DB_RECORDING");
        aMap.put(MSG_ID_BT_TO_DB_FAIL, "MSG_ID_BT_TO_DB_FAIL");
        
        aMap.put(MSG_ID_DB_PLAYBACK_TIMER_UPDATE, "MSG_ID_DB_PLAYBACK_TIMER_UPDATE");
        aMap.put(MSG_ID_DB_PLAYBACK_STARTED_STOPPED, "MSG_ID_DB_PLAYBACK_STARTED_STOPPED");
        
        aMap.put(MSG_ID_SOURCE_SPAN_MANAGER, "MSG_ID_SOURCE_SPAN_MANAGER");
        aMap.put(MSG_ID_SOURCE_PLOT_MANAGER, "MSG_ID_SOURCE_PLOT_MANAGER");
        aMap.put(MSG_ID_SOURCE_BLUETOOTH_MANAGER, "MSG_ID_SOURCE_BLUETOOTH_MANAGER");
        aMap.put(MSG_ID_SOURCE_DOCK_MANAGER, "MSG_ID_SOURCE_DOCK_MANAGER");
        aMap.put(MSG_ID_SOURCE_PLATFORM_MANAGER, "MSG_ID_SOURCE_PLATFORM_MANAGER");
        aMap.put(MSG_ID_SOURCE_ALGORITHM, "MSG_ID_SOURCE_ALGORITHM");
        aMap.put(MSG_ID_SOURCE_ALGORITHM_MANAGER, "MSG_ID_SOURCE_ALGORITHM_MANAGER");
        aMap.put(MSG_ID_SOURCE_EVENT_MARKERS, "MSG_ID_SOURCE_EVENT_MARKERS");
        aMap.put(MSG_ID_SOURCE_RESULT_AGGREGATOR, "MSG_ID_SOURCE_RESULT_AGGREGATOR");
        aMap.put(MSG_ID_SOURCE_DATABASE_MANAGER, "MSG_ID_SOURCE_DATABASE_MANAGER");
        aMap.put(MSG_ID_SOURCE_DATA_IMPORT_MANAGER, "MSG_ID_SOURCE_DATA_IMPORT_MANAGER");
        aMap.put(MSG_ID_SOURCE_DATA_PROCESS_MANAGER, "MSG_ID_SOURCE_DATA_PROCESS_MANAGER");
        aMap.put(MSG_ID_SOURCE_VIDEO_MANAGER, "MSG_ID_SOURCE_VIDEO_MANAGER");
    	
    	mMapOfMsgCodes = Collections.unmodifiableMap(aMap);
    }
	
	public Object mObject;
	public int mMsgID = -1; //identifies what type of dock this is 0 for basic 1 for advance
	public int mCurrentOperation = -1;
	public int mSlotNumber = -1; //only applicable for the smart dock
	public String mUniqueID = "";
	public String mBSLComPort = "";
	public String mUARTComPort = "";
	public String mDockID = "";
	/** currently just used as a fall back when undocking Shimmers */
	public String mMacID = "";
	public DEVICE_STATE mDockState = DEVICE_STATE.STATE_NONE;
	
	/**
	 * Used for BSL progress reporting and lists SmartDock UART responses
	 */
	public int mFwImageTotalSize = 0;
	public int mFwImageWriteProgress = 0;
	public float mFwImageWriteSpeed = 0;
	/** A NeuroLynQ node's download (MSG_ID_DOCK_NODE_DOWNLOAD_PROGRESS): bytes read so far, and in all */
	public long mBytesDone = 0;
	public long mBytesTotal = 0;
	public String mMessage = "";
	public double mValue;
	/** Just used for Shimmer4 BSL programming as each stage takes longer no needs more GUI feedback */
	public String mFwImageWriteCurrentAction = "";
	
	public int mSessionId;
	
	public int mErrorCode;
	public int mErrorCodeLowLevel;
	public int mErrorCodeLowBsl;
	
	/** Contains the error message as copied from a caught exception. */
	public String mExceptionMsg;
	
	/** Contains the stracktrace as copied from a caught exception. */
	public StackTraceElement[] mExceptionStackTrace;
	
	/** Indicates whether the Exception is critical to the operation underway. */
	public ExceptionLevel mExceptionLevel = ExceptionLevel.HIGH;

	
	public byte[] mSlotMap = new byte[]{};
	
	public DockJobDetails mCurrentJobDetails;
	
	/**SmartDockActiveSlotDetails
	 * @see SmartDockActiveSlotDetails
	 */
	public int mConnectionType = 0;
	public int mIndicatorLEDsBitmap = 0;

//	public DockException mDockException;
	
//	public MsgDock(){
//	}

	/** Used exclusively by the DockManager/ImportManager class for messages associated with no specific slot or dock
	 * @param msgID
	 */
	public MsgDock(int msgID){
		mMsgID = msgID;
	}
	
	/** Used exclusively by the DockManager class for messages associated with an operations progress 
	 * @param msgID
	 */
	public MsgDock(int msgID,int currentOperation){
		mMsgID = msgID;
		mCurrentOperation = currentOperation;
	}
	
	/** Used by MspBsl with uniqueID, SmartDockUartListener with dockID
	 * @param msgIdentifier
	 * @param uniqueID
	 */
	public MsgDock(int msgIdentifier,String iD){
		mMsgID = msgIdentifier;
		mUniqueID = iD;
		String[] subString = iD.split("\\.");
		mDockID  = subString[0]+"."+subString[1];
		if(subString.length>2) {
			mSlotNumber = Integer.parseInt(subString[2]);
		}
		else {
			mSlotNumber = -1;
		}
	}

	/** Used by SmartDock for BSL operations
	 * @param msgIdentifier
	 * @param uniqueID
	 */
	public MsgDock(String dockID,int msgIdentifier){
		mMsgID = msgIdentifier;
		mDockID  = dockID;
		mUniqueID  = dockID;
		mSlotNumber = -1;
//		mUniqueID = mDockID + "." + String.format("%02d",mSlotNumber);
	}
	
	/** Used by BasicDock, SmartDock, SmartDockUart and SmartDockUartListener
	 * @param msgID
	 * @param dockID
	 * @param slotIdentifier
	 */
	public MsgDock(int msgID,String dockID,int slotIdentifier){
		mMsgID = msgID;
		mSlotNumber = slotIdentifier;
		mDockID = dockID;
		mUniqueID = convertDockIdAndSlotNumberToUniqueId(mDockID, mSlotNumber);
	}

	
	/** Used by SmartDock for specific SmartDock operation requests
	 * @param msgID
	 * @param dockID
	 * @param slotIdentifier
	 */
	public MsgDock(int msgID, String dockID, DockJobDetails currentJobDetails) {
		mMsgID = msgID;
		mDockID = dockID;
//		mSlotNumber = -1;
//		mUniqueID = mDockID + "." + String.format("%02d",mSlotNumber);
		
		//For Dock jobs keep 
		mUniqueID = dockID;
		mSlotNumber = convertUniqueIdToSlotNumer(mUniqueID);

		mCurrentJobDetails = currentJobDetails;
	}

	public MsgDock(int msgIdDockSlotRemoved, String dockID, int slotNumber, String macId) {
		this(msgIdDockSlotRemoved, dockID, slotNumber);
		mMacID = macId;
	}
	
	public static int convertUniqueIdToSlotNumer(String uniqueId){
		int slotNumber = -1;
		String[] subString = uniqueId.split("\\.");
		if(subString.length>2) {
			slotNumber = Integer.parseInt(subString[2]);
		}
		return slotNumber;
	}

	public static String convertUniqueIdToDockId(String uniqueId){
		String[] subString = uniqueId.split("\\.");
		return (subString[0]+"."+subString[1]);
	}

	public static String convertDockIdAndSlotNumberToUniqueId(String dockId, int slotNumber){
		return (dockId + "." + String.format("%02d",slotNumber));
	}

	public String getMsgDockErrString() {
		String errorString = "";

		String id = mUniqueID;
		if(mSlotNumber == -1) {
			id = mDockID;
		}
		String msgID = "Unknown Error";
		if(ShimmerException.mMapOfErrorCodes.containsKey(mMsgID)) {
			msgID = ShimmerException.mMapOfErrorCodes.get(mMsgID);
		}
		String errorCode = "Unknown Error";
		if(ShimmerException.mMapOfErrorCodes.containsKey(mErrorCode)) {
			errorCode = ShimmerException.mMapOfErrorCodes.get(mErrorCode);
		}
		String lowLevelErrorCode = "Unknown Error";
		if(ShimmerException.mMapOfErrorCodes.containsKey(mErrorCodeLowLevel)) {
			lowLevelErrorCode = ShimmerException.mMapOfErrorCodes.get(mErrorCodeLowLevel);
		}
		String exceptionInfo = "";
		if(mExceptionMsg!=null && !mExceptionMsg.isEmpty()) {
			exceptionInfo = "Further info: " + mExceptionMsg;
		}

		if((mMsgID==MsgDock.MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_FAIL) 
			&&(mDockID.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.BASICDOCK.getLabel()))
			&&(mErrorCodeLowLevel==ErrorCodesWiredProtocol.SHIMMERUART_COMM_ERR_TIMEOUT)){

//		if(msg.mMsgID == MsgDock.MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_FAIL) {
//			if((msg.mDockID.contains(HwDriverShimmerDeviceDetails.DOCK_LABEL[HwDriverShimmerDeviceDetails.DEVICE_TYPE.BASICDOCK.ordinal()]))
//					&&(msg.mErrorCodeLowLevel==ErrorCodesShimmerUart.SHIMMERUART_COMM_ERR_TIMEOUT)){
//				utilShimmer.consolePrintLn(id + " - unable to detect/read Shimmer - " + lowLevelErrorCode);
//			}
//			else {
			errorString = ("CAUGHT MSGDOCK EXCEPTION - " + id + " - MSG_ID_SHIMMERUART_READ_SHIMMER_DETAILS_FAIL " + errorCode + " " + lowLevelErrorCode);
//			}
		}
		else {
			errorString += ("CAUGHT MSGDOCK EXCEPTION\n");
			errorString += ("\t" + "UniqueID: " + id
					+ "\n\t" + "MsgID: " + "(" + mMsgID + ") " + msgID 
					+ "\n\t" + "Action: " + "(" + mErrorCode + ") " + errorCode 
					+ "\n\t" + "LowLevelError: " + "(" + mErrorCodeLowLevel + ") " + lowLevelErrorCode
					+ "\n\t" + exceptionInfo
					+ "\n");
			
			if(mExceptionStackTrace!=null){
				String stackTraceString = UtilShimmer.convertStackTraceToString(mExceptionStackTrace);
				if(!stackTraceString.isEmpty()) {
					errorString += (stackTraceString);
				}
			}
		}
		
		return errorString;
	}
	
}
