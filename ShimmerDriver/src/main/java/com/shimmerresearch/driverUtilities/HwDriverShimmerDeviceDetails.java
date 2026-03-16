package com.shimmerresearch.driverUtilities;

import com.codeminders.hidapi.HIDDeviceInfo;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;

/** Holds system HW information of Shimmer supported devices (used by DeviceInfo)
 * 
 * @author Mark Nolan
 *
 */
public class HwDriverShimmerDeviceDetails {
	
	public static enum DEVICE_TYPE {
		UNKOWN("Unknown"),
		BASICDOCK("Dock"),
		BASE15("Base15U"),
		BASE6("Base6U"),
		SPAN("Span"),
		
		RN42("RN42"),
		RNBT("RNBT"),
		SHIMMER3("Shimmer3"),
		SHIMMER3_OUTPUT("OUTPUT"),
		SHIMMER_ECG_MD("ShimmerECGmd"),
		SHIMMER3R("Shimmer3R"),
		SHIMMER4("Shimmer4"),
		
		LUMAFIT("Lumafit"),
		NONIN_ONYX_II("Nonin_Onyx_II"),
		QTI_DIRECTTEMP("Qti_DirectTemp"),
		KEYBOARD_AND_MOUSE("KeyboardAndMouse"),
		ARDUINO("Arduino"),
		WII_BALANCE_BOARD("Wii_BalanceBoard"),
		WEBCAM("Webcam"),
		HOST_CPU_USAGE("CPU Usage"),
		SWEATCH("SwEatch"),
		VERISENSE("Verisense"),
		KEYBOARD("Keyboard");

		private String deviceLabel = "";
		
		DEVICE_TYPE(String deviceLabel){
			this.deviceLabel = deviceLabel;
		}
		
		public String getLabel() {
			return deviceLabel;
		}
		
		@Override
		public String toString() {
			return deviceLabel;
		}
	}
    
	public enum SPAN_VERSION{
		UNKNOWN,
		SPAN_SR1_3_1, // 115200 baud
		SPAN_SR1_3_0, // 230400 baud
		VIRTUAL; 
		
		public boolean isSupported(){
			return this==SPAN_VERSION.SPAN_SR1_3_1;
		}
	}

	public enum DEVICE_STATE{
		STATE_NONE,
		STATE_READY,
		STATE_BUSY
	}

//	public class DOCK_STATE {
//		public final static int STATE_NONE = 0 ;
//		public final static int STATE_READY = 1;
//		public final static int STATE_BUSY = 2;
//	}
	
    public static final class SH_SEARCH {
    	
    	public static final String[] BASIC_DOCK_OSX = new String[] {
    			"SHIMMER USB READER",
    	};
    	
        public static final String[] DOCK = new String[] {
    		"Shimmer Dock",
        };

        public static final String[] BASE6 = new String[] {
    		"Base6",
//    		"Base6U",
        };
        
        public static final String[] BASE15 = new String[] {
    		"Base15",
//    		"Base15U",
    		"SmartDock",
    		"SMART DOCK"
        };
        
        public static final String[] SPAN_SR1_3_0 = new String[] {
    		"SHIMMER RESEARCH SPAN 5/6/2009",
        };

        public static final String[] SPAN_SR1_3_1 = new String[] {
//        	"SHIMMER SPAN SR1-3.1 25/9/2015",
    		"SHIMMER SPAN SR1-3.1",
        };

        public static final String[] DISK_DRIVE = new String[] {
    		"Shimmer",
    		"Generic" // Some docks went out unprogrammed?
        };
        
        public static final class MASS_STORAGE_DEVICE {
	        public static final String[] HARDWARE_ID = new String[] {
	    		"VID_0424&PID_4050", // Docks and Bases
	        };
	        public static final String[] BUS_DESCRIPTION = new String[] {
	    		"SHIMMER",
	    		"Ultra Fast Media Reader"  // Some docks went out unprogrammed?
	        };
        }

        public static final String[] PORTABLE_DEVICE = new String[] {
    		"Shimmer",
    		"Generic" // Some docks went out unprogrammed?
        };
        
        public static final String[] STORAGE_VOL = new String[] {
    		"shimmer",
    		"Generic" // Some docks went out unprogrammed?
        };

        
        public static final String[] COMP_DEV_DOCK = new String[] {
    		"VID_0403&PID_6010",
        };
        
        public static final String[] COMP_DEV_BASE = new String[] {
    		"VID_0403&PID_6011",
        };

        public static final String[] USB_HUB = new String[] {
    		"VID_0424&PID_2640",
        };
        
        public static final String[] SHIMMER_3R_USB_DFU = new String[] {
        	"VID_0483&PID_DF11",
        };

		public static final String[] SHIMMER = new String[] {
    		"shimmer",
        };
		
		public static final String[] VERISENSE_USB = new String[] {
	    	"VID_1915&PID_520F",
	    };
		
		public static final String[] VERISENSE = new String[] {
		    "verisense",
		};

		public static final String[] SHIMMER3R_DFU_DEVICE_BUS_DESCRITION = new String[] { "DFU in HS Mode" };

		public static final class SERVICE_DESCRIPTION {
			public static final String[] COMPOSITE_DEVICE = new String[]{"USB Composite Device"};
			
			public static final String[] SERIAL_CONVERTER = new String[]{"USB Serial Converter"};
		}


		public static final class SERIAL_CONVERTER {
	        public static final String[] DOCK = new String[] {
	    		"VID_0403&PID_6010&MI",
	        };
	
	        public static final String[] BASE = new String[] {
	    		"VID_0403&PID_6011&MI",
	        };
		}
        
        public static final class SERIAL_PORT {
	        /** Used in the Shimmer Dock (Base1) and Shimmer SPAN */
	        public static final String[] FTDI_FT2232H = new String[] {
	    		"VID_0403&PID_6010&MI",
	        };
	
	        /** Used in the Shimmer Base6 and Base15 */
	        public static final String[] FTDI_FT4232H = new String[] {
	    		"VID_0403&PID_6011&MI",
	        };
	        
	        public static final String FTDI_VEND_ID = "0403";
	        public static final String FTDI_FT2232H_PROD_ID = "6010";
	        public static final String FTDI_FT4232H_PROD_ID = "6011";
	        
	        public static final String VIRTUAL_PORT = "COM0COM";
	        
	        public static final String[] SHIMMER_3R = new String[] {
	        		"VID_0483&PID_5710" // USBX drivers
	        };
        }

        public static final class BT {
            public static final String[] WIN_DRIVER = new String[] {
        		"BTHENUM",
            };
            public static final class TOSHIBA_DRIVER {
                public static final String[] ENUM_NAME = new String[] {
            		"BLUETOOTH", // Enumerator name
                };
                public static final String[] MANUFACTURER = new String[] {
            		"TOSHIBA", // Manufacturer name
                };
            }
            
	        public static final String SHIMMER3 = "Shimmer3";
	        public static final String SHIMMER3_RN4678_BLE = "S3BLE";
	        public static final String SHIMMER3_CYW20820_BLE_SUFFIX = "-BLE";
	        public static final String SHIMMER_ECG_MD = "ShimmerECGmd";
	        public static final String SHIMMER4 = "Shimmer4";
	        public static final String RN42 = "RN42";
	        public static final String RNBT = "RNBT";
			public static final String VERISENSE = "Verisense";

            public static final String[] SHIMMER_DEVICE = new String[] {
            	SHIMMER3,
            	DEVICE_TYPE.SHIMMER3_OUTPUT.getLabel(),
            	SHIMMER_ECG_MD,
            	SHIMMER4,
            	RN42,
            	RNBT,
            	VERISENSE
            };
            
	        public static final String MANUFACTURER_NONIN = "Nonin";
	        public static final String MANUFACTURER_LUMAFIT = "Lumafit";
            
            public static final String[] THIRD_PARTY_DEVICE = new String[] {
            	MANUFACTURER_NONIN,
            	MANUFACTURER_LUMAFIT
            };
        }
    }
    
    
	public static final class BASE_HARDWARE_IDS{
		public static final int BASE15U = 1;
		public static final int BASE6U = 2;
	}
	
    public DEVICE_TYPE deviceType = DEVICE_TYPE.UNKOWN;
	
    public HwDriverDeviceDetails usbHub = null;
	public HwDriverDeviceDetails compositeDevice = null;
	public HwDriverDeviceDetails serialConverterBaseFw = null;
	public HwDriverDeviceDetails serialConverterBaseUart = null;
	public HwDriverDeviceDetails serialConverterShimmerFw = null;
	public HwDriverDeviceDetails serialConverterShimmerUart = null;

	public HwDriverDeviceDetails serialPortBaseFw = null;
	public HwDriverDeviceDetails serialPortBaseUart = null;
	public HwDriverDeviceDetails serialPortShimmerFw = null;
	public HwDriverDeviceDetails serialPortShimmerUart = null;

	public HwDriverDeviceDetails massStorageDevice = null;
	public HwDriverDeviceDetails diskDrive = null;
	public HwDriverDeviceDetails portableDevice = null;
	public HwDriverDeviceDetails storageVolume = null;

	public HwDriverDeviceDetails dfuDevice = null;

	public String mDockID= "";
	public String mSmartDockFwComPort= "";
	public String mSmartDockUartComPort= "";
	public String mShimmerFwComPort= "";
	public String mShimmerUartComPort= "";
	public String mShimmerDrivePath = "";
	public int mNumberOfSlots = 1;
	
	public HIDDeviceInfo mHidDeviceInfo = null;
	
	/** Unique HW ID (e.g., FTDI serial ID or location path). */
    public String mUniqueHwID = "";
	public SPAN_VERSION mSpanVersion = SPAN_VERSION.UNKNOWN;

	public HwDriverShimmerDeviceDetails() {
		// TODO Auto-generated constructor stub
	}

	public HwDriverShimmerDeviceDetails(String dockAssignedId, String smartDockBSLComPort, String smartDockUARTComPort, String shimmerBSLComPort, String shimmerUARTComPort, String shimmerDrivePath){
		mDockID = dockAssignedId;
		mSmartDockFwComPort = smartDockBSLComPort;
		mSmartDockUartComPort = smartDockUARTComPort;
		mShimmerFwComPort = shimmerBSLComPort;
		mShimmerUartComPort = shimmerUARTComPort;
		mShimmerDrivePath = shimmerDrivePath;
	}
	
	public HwDriverShimmerDeviceDetails(DEVICE_TYPE deviceType, HIDDeviceInfo hidDeviceInfo) {
	    this.deviceType = deviceType;
		mHidDeviceInfo = hidDeviceInfo;
		if(mHidDeviceInfo!=null){
			mDockID = mHidDeviceInfo.getSerial_number();
		}
	}

	public void setDeviceType(DEVICE_TYPE deviceType) {
	    this.deviceType = deviceType;
//	    dockTypeParsed = DOCK_LABEL[this.deviceType.ordinal()];
	    
	    if(this.deviceType == DEVICE_TYPE.BASICDOCK) {
		    mNumberOfSlots = 1;
	    }
	    else if(this.deviceType == DEVICE_TYPE.BASE15) {
		    mNumberOfSlots = 15;
	    }
	    else if(this.deviceType == DEVICE_TYPE.BASE6) {
		    mNumberOfSlots = 6;
	    }
	}
	
	public static DEVICE_TYPE getDeviceTypeFromLabel(String label){
		for (DEVICE_TYPE deviceType:DEVICE_TYPE.values()) {
			if(deviceType.getLabel().equals(label)){
				return deviceType;
			}
		}
		return DEVICE_TYPE.UNKOWN;
	}

	public String getProduct_string() {
		if(mHidDeviceInfo!=null){
			return mHidDeviceInfo.getProduct_string();
		}
		return deviceType.getLabel();
	}
	
}
