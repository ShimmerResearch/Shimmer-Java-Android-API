package com.shimmerresearch.verisense;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense;
import com.shimmerresearch.algorithms.AbstractAlgorithm;
import com.shimmerresearch.algorithms.verisense.gyroAutoCal.GyroOnTheFlyCalModuleVerisense;
import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.radioProtocol.RadioListener;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerDeviceCallbackAdapter;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.calibration.CalibDetailsKinematic;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.DEVICE_TYPE;
import com.shimmerresearch.sensors.AbstractSensor;
import com.shimmerresearch.sensors.SensorADC;
import com.shimmerresearch.sensors.AbstractSensor.GuiLabelConfigCommon;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.communication.payloads.AbstractPayload;
import com.shimmerresearch.verisense.communication.payloads.OperationalConfigPayload;
import com.shimmerresearch.verisense.communication.payloads.ProductionConfigPayload;
import com.shimmerresearch.verisense.communication.payloads.StatusPayload;
import com.shimmerresearch.verisense.communication.payloads.TimePayload;
import com.shimmerresearch.verisense.communication.payloads.OperationalConfigPayload.OP_CONFIG_BIT_MASK;
import com.shimmerresearch.verisense.communication.payloads.OperationalConfigPayload.OP_CONFIG_BIT_SHIFT;
import com.shimmerresearch.verisense.communication.payloads.OperationalConfigPayload.OP_CONFIG_BYTE_INDEX;
import com.shimmerresearch.verisense.communication.VerisenseProtocolByteCommunication;
import com.shimmerresearch.verisense.communication.VerisenseProtocolByteCommunication.VERISENSE_EVENT_ACK_RECEIVED;
import com.shimmerresearch.verisense.payloaddesign.PayloadContentsDetails;
import com.shimmerresearch.verisense.payloaddesign.VerisenseTimeDetails;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.PAYLOAD_CONFIG_BYTE_INDEX;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.BYTE_COUNT;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.DATA_COMPRESSION_MODE;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;
import com.shimmerresearch.verisense.sensors.ISensorConfig;
import com.shimmerresearch.verisense.sensors.SensorBattVoltageVerisense;
import com.shimmerresearch.verisense.sensors.SensorGSRVerisense;
import com.shimmerresearch.verisense.sensors.SensorLIS2DW12;
import com.shimmerresearch.verisense.sensors.SensorLSM6DS3;
import com.shimmerresearch.verisense.sensors.SensorLSM6DSV;
import com.shimmerresearch.verisense.sensors.SensorMAX86150;
import com.shimmerresearch.verisense.sensors.SensorMAX86916;
import com.shimmerresearch.verisense.sensors.SensorMAX86XXX;
import com.shimmerresearch.verisense.sensors.SensorVerisenseClock;

/** 
 * 
 * @author Mark Nolan, Sriram Raju Dandu
 *
 */
public class VerisenseDevice extends ShimmerDevice implements Serializable{

	public static final String VERISENSE_PREFIX = "Verisense";
	
	private static final long serialVersionUID = -5496745972549472824L;

	private static final Integer INVALID_VALUE = -1;

    VerisenseProtocolByteCommunication mProtocol;

	protected transient ShimmerDeviceCallbackAdapter mDeviceCallbackAdapter = new ShimmerDeviceCallbackAdapter(this);
	public DATA_COMPRESSION_MODE dataCompressionMode = DATA_COMPRESSION_MODE.NONE;
	public PASSKEY_MODE passkeyMode = PASSKEY_MODE.NONE;
	public BATTERY_TYPE batteryType = BATTERY_TYPE.ZINC_AIR;

	public enum PASSKEY_MODE {
		SECURE,
		NONE,
		CUSTOM;
	}

	public enum BATTERY_TYPE {
		ZINC_AIR,
		NIMH;
	}
	
	private static int DEFAULT_HW_ID = HW_ID.VERISENSE_IMU;
	private ShimmerVerObject defaultSvo = new ShimmerVerObject(DEFAULT_HW_ID, FW_ID.UNKNOWN, FW_ID.UNKNOWN, FW_ID.UNKNOWN, FW_ID.UNKNOWN);
	private ExpansionBoardDetails defaultEbd = new ExpansionBoardDetails(DEFAULT_HW_ID, 0, INVALID_VALUE);
	
	private byte resetReason = RESET_REASON.CLEARED.bitMask;
	public enum RESET_REASON {
		POR_OR_BOD((byte) 0x00, "POR_OR_BOD", "Battery died or disconnected"),
		RESETPIN((byte) 0x01, "RESETPIN", "Reset pin triggered"),
		DOG((byte) 0x02, "WATCHDOG", "FW froze"),
		SREQ((byte) 0x04, "SREQ", "FW update"),
		LOCKUP((byte) 0x08, "LOCKUP", "FW lockup"),
		OFF((byte) 0x10, "OFF", "Off"),
		LPCOMP((byte) 0x20, "LPCOMP", "Low-power comparator"),
		DIF((byte) 0x40, "DIF", "DIF"),
		VBUS((byte) 0x80, "VBUS", "VBUS"),
		CLEARED((byte) 0xFF, "None", "None");
		
		public byte bitMask = 0;
		public String descriptionShort = "";
		public String descriptionLong = "";
		
		private RESET_REASON(byte bitMask, String descriptionShort, String descriptionLong) {
			this.bitMask = bitMask;
			this.descriptionShort = descriptionShort;
			this.descriptionLong = descriptionLong;
		}

		public static RESET_REASON findValueForShortDescription(String descriptionShort) {
			for (VerisenseDevice.RESET_REASON resetReason : VerisenseDevice.RESET_REASON.values()) {
				if (descriptionShort.equals(resetReason.descriptionShort)) {
					return resetReason;
				}
			}
			return null;
		}
	}
	
	public class CSV_HEADER_LINES {
		public static final String RESET = "Reset: Reason = "; 
	}

	
	private Integer resetCounter = null;
	private Integer firstPayloadIndexAfterBoot = null;
	public boolean isExtendedPayloadConfig = true;

	// Saving in global map as we only need to do this once per datablock sensor ID per payload, not for each datablock
	private HashMap<DATABLOCK_SENSOR_ID, List<SENSORS>> mapOfSensorIdsPerDataBlock = new HashMap<DATABLOCK_SENSOR_ID, List<SENSORS>>();
	
	// Operational config
	private boolean bluetoothEnabled = true, usbEnabled = false, prioritiseLongTermFlash = true, deviceEnabled = true, recordingEnabled = true; 
	private long recordingStartTimeMinutes = 0, recordingEndTimeMinutes = 0;
	private int bleConnectionRetriesPerDay = 3;
	private BLE_TX_POWER bleTxPower = BLE_TX_POWER.MINUS_12_DBM;
	private PendingEventSchedule pendingEventScheduleDataTransfer = new PendingEventSchedule(24, 60, 10, 15);
	private PendingEventSchedule pendingEventScheduleStatusSync = new PendingEventSchedule(24, 60, 10, 15);
	private PendingEventSchedule pendingEventScheduleRwcSync = new PendingEventSchedule(24, 60, 10, 15);
	private int adaptiveSchedulerInterval = 65535;
	private int adaptiveSchedulerFailCount = 255;
	private int ppgRecordingDurationSeconds = 0;
	private int ppgRecordingIntervalMinutes = 0;

	public static enum BLE_TX_POWER implements ISensorConfig {
		PLUS_8_DBM("+8 dBm", (byte) 0x08),
		PLUS_7_DBM("+7 dBm", (byte) 0x07),
		PLUS_6_DBM("+6 dBm", (byte) 0x06),
		PLUS_5_DBM("+5 dBm", (byte) 0x05),
		PLUS_4_DBM("+4 dBm", (byte) 0x04),
		PLUS_3_DBM("+3 dBm", (byte) 0x03),
		PLUS_2_DBM("+2 dBm", (byte) 0x02),
		PLUS_0_DBM("0 dBm", (byte) 0x00),
		MINUS_4_DBM("-4 dBm", (byte) 0xFC),
		MINUS_8_DBM("-8 dBm", (byte) 0xF8),
		MINUS_12_DBM("-12 dBm", (byte) 0xF4),
		MINUS_16_DBM("-16 dBm", (byte) 0xF0),
		MINUS_20_DBM("-20 dBm", (byte) 0xEC),
		MINUS_40_DBM("-40 dBm", (byte) 0xFF);
		
		private String label;
		private byte byteMask;

		private BLE_TX_POWER(String label, byte byteMask) {
			this.label = label;
			this.byteMask = byteMask;
		}

		public String getLabel() {
			return label;
		}

		public byte getByteMask() {
			return byteMask;
		}

		public static BLE_TX_POWER getSettingFromMask(byte byteMask) {
			for(BLE_TX_POWER bleTxPower : BLE_TX_POWER.values()) {
				if(bleTxPower.getByteMask()==byteMask) {
					return bleTxPower;
				}
			}
			return null;
		}
	}

	// Verisense Communication
    protected HashMap<COMMUNICATION_TYPE, VerisenseProtocolByteCommunication> mapOfVerisenseProtocolByteCommunication = new HashMap<COMMUNICATION_TYPE, VerisenseProtocolByteCommunication>();
	private COMMUNICATION_TYPE currentStreamingCommsRoute = COMMUNICATION_TYPE.BLUETOOTH;
	private transient StatusPayload status;
	private transient OperationalConfigPayload opConfig;
	private transient ProductionConfigPayload prodConfigPayload;

	public static class FW_CHANGES {
		/** FW Version and Reset Reason */
		public static final ShimmerVerObject CCF19_027 = new ShimmerVerObject(FW_ID.UNKNOWN, 0, 34, 1); 
		/** Reset Reason, Reset Counter, First Payload Index */
		public static final ShimmerVerObject CCF19_035 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 1, 2);
		/** PPG support (i.e., 2 bytes HW ver in payload header + 3 additional config. bytes) */
		public static final ShimmerVerObject CCF20_012_1 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 2);
		/** PPG support (i.e., LED1_PA added to payload config) */
		public static final ShimmerVerObject CCF20_012_2 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 9);
		/** PPG support (i.e., LED2_PA, LED3_PA, LED4_PA added to payload config) */
		public static final ShimmerVerObject CCF20_012_3 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 35);
		/** PPG support (i.e., LED1-4 RGE byte added to payload config) */
		public static final ShimmerVerObject CCF20_012_4 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 70);
		/** Multiple sensor support */
		public static final ShimmerVerObject CCF20_012_5 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 73);
		/** RTC ticks added back into payload footer */
		public static final ShimmerVerObject CCF20_012_6 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 74);
		/** Microcontroller time (ticks and minutes) added into payload footer */
		public static final ShimmerVerObject CCF21_010_1 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 85);
		/** Ticks per data block are now Microcontroller ticks instead of RWC ticks and
		 * RTC time (ticks and minutes) in payload footer is now the RWC offset */
		public static final ShimmerVerObject CCF21_010_2 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 87);
		/** GSR support with ADC sampling rate byte added to header */
		public static final ShimmerVerObject CCF21_010_3 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 88);
		/**
		 * Second-generation hardware support (SR68-9/10, SR61-5/6). The firmware
		 * refers to this as "payload design v9": it writes a 32-byte config header
		 * (bytes 4..35, vs 25 bytes / bytes 4..28 previously; bytes 34..35 are the
		 * calibration-blob CRC) and introduces the
		 * LSM6DSV/ambient-light/algorithm-hub/skin-temperature data blocks.
		 * <p>
		 * In this driver's own (separate) payload-design numbering this is the next
		 * step after V12 ({@link #CCF21_010_3}), exposed as
		 * {@link VerisenseDevice#isPayloadDesignV13orAbove()}. The two "v9" numbers
		 * are NOT the same axis - see that method's Javadoc.
		 * <p>
		 * Confirmed against a real SR68-9 device: it reports FW v2.00.004 (the doc's
		 * "1.04.024" is stale). The 36-byte total header = 4 (index+length) + 32 config.
		 */
		public static final ShimmerVerObject CCF_GEN2 = new ShimmerVerObject(FW_ID.UNKNOWN, 2, 0, 4);
	}

	public static class FW_SPECIAL_VERSIONS {
		// FW v0.31.000 had the battery voltage added to the payload footer ASM-425
		public static final ShimmerVerObject V_0_31_000 = new ShimmerVerObject(FW_ID.UNKNOWN, 0, 32, 0);
		// FW v1.02.065 with ASM-1511 (Gen2 LTF chip) and ASM-1535 (uint16_t error in scheduler)
		public static final ShimmerVerObject V_1_02_071 = new ShimmerVerObject(FW_ID.UNKNOWN, 1, 2, 71);
	}
	
	public static class SENSOR_CONFIG_STRINGS {
		public static final String SAMPLING_RATE_CONFIGURED = "Configured = ";
		public static final String SAMPLING_RATE_CALCULATED = "Calculated = ";
		public static final String RANGE = "Range = ";
		public static final String RESOLUTION = "Resolution = ";
		public static final String RATE = "Rate = ";
	}

	public static class CSV_LINE_TITLES {
		public static final String ALGORITHM_CONFIG = "Algorithm config: ";
		public static final String SENSOR_CONFIG = "Sensor config: ";
	}

	public static List<SENSORS> LIST_OF_PPG_SENSORS = Arrays.asList(new SENSORS[] {SENSORS.MAX86150, SENSORS.MAX86916});

	public enum CHARGER_STATUS_LTC4123 {
		ERROR("Error", "Zinc-Air battery/reverse polarity detection/ battery temperature out of range/UVCL at the beginning of the charge cycle"),
		CHARGING("Charging", "Powered on/charging"),
		COMPLETE("Charging complete", "Charging complete"),
		NOT_CHARGING("Not Charging", "No power /not charging");
		
		public String descriptionShort;
		public String descriptionLong;
		
		private CHARGER_STATUS_LTC4123(String descriptionShort, String descriptionLong) {
			this.descriptionShort = descriptionShort;
			this.descriptionLong = descriptionLong;
		}
	}

	public enum CHARGER_STATUS_LM3658D {
		POWER_DOWN("Power-down", "Power-Down, charging is suspended or interrupted"),
		CHARGING("Charging", "Pre-qualification mode, CC and CV charging, Top-off mode"),
		COMPLETE("Complete", "Charge is completed"),
		NOT_CHARGING("Bad Battery", "Bad battery (Safety timer expired), or LDO mode");
		
		public String descriptionShort;
		public String descriptionLong;
		
		private CHARGER_STATUS_LM3658D(String descriptionShort, String descriptionLong) {
			this.descriptionShort = descriptionShort;
			this.descriptionLong = descriptionLong;
		}
	}

	/**
	 * 
	 */
	public VerisenseDevice() {
		super.setDefaultShimmerConfiguration();

		setUniqueId(DEVICE_TYPE.VERISENSE.getLabel());
		setShimmerUserAssignedName(mUniqueID);
		setMacIdFromUart(mUniqueID);
	}
	
	public VerisenseDevice(COMMUNICATION_TYPE commType) {
		this();
		addCommunicationRoute(commType);
	}

	@Override
	public String getFirmwareVersionParsed() {
		if(mShimmerVerObject.mFirmwareVersionMajor==FW_ID.UNKNOWN) {
			return UtilVerisenseDriver.FEATURE_NOT_AVAILABLE;
		} else {
			return mShimmerVerObject.getFirmwareVersionParsedVersionNumberFilled();
		}
	}
	
	@Override
	public void setShimmerUserAssignedName(String shimmerUserAssignedName) {
		if(!shimmerUserAssignedName.isEmpty()){
			//Remove any invalid characters
			shimmerUserAssignedName = shimmerUserAssignedName.replace("-", "_");
			shimmerUserAssignedName = shimmerUserAssignedName.replaceAll(INVALID_TRIAL_NAME_CHAR, "");

			//Don't allow the first char to be numeric - causes problems with MATLAB variable names
			if(UtilShimmer.isNumeric("" + shimmerUserAssignedName.charAt(0))){
				shimmerUserAssignedName = "S" + shimmerUserAssignedName; 
			}
		}
		else{
			shimmerUserAssignedName = ShimmerObject.DEFAULT_SHIMMER_NAME + "_" + this.getMacIdFromUartParsed();
		}

		//Limit the name to 12 Char
		if(shimmerUserAssignedName.length()>20) {
			setShimmerUserAssignedNameNoLengthCheck(shimmerUserAssignedName.substring(0, 22));
		}
		else { 
			setShimmerUserAssignedNameNoLengthCheck(shimmerUserAssignedName);
		}
	}

	@Override
	public byte[] configBytesGenerate(boolean generateForWritingToShimmer, COMMUNICATION_TYPE commType) {
		byte[] configBytes = null;
		
		if (commType == COMMUNICATION_TYPE.SD) {
			int payloadConfigBytesSize = PayloadContentsDetails.calculatePayloadConfigBytesSize(mShimmerVerObject);
			configBytes = new byte[payloadConfigBytesSize];
			
			long enabledSensors = getEnabledSensors();
			configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0] = (byte) (enabledSensors & 0xFF);
			if(isPayloadDesignV4orAbove()) {
				configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG2] = (byte) ((enabledSensors >> 8) & 0xFF);
			}
			
			configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0] |= isExtendedPayloadConfig? (0x01<<4):0x00;
			configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0] |= ((dataCompressionMode.ordinal()&0x03)<<0);
			
			for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()) {
				abstractSensor.configBytesGenerate(this, configBytes, commType);
			}

			if(isExtendedPayloadConfig) {
				configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_FW_MAJOR] = (byte) (mShimmerVerObject.mFirmwareVersionMajor&0xFF);
				configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_FW_MINOR] = (byte) (mShimmerVerObject.mFirmwareVersionMinor&0xFF);
				configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_FW_INTERNAL_LSB] = (byte) (mShimmerVerObject.mFirmwareVersionInternal&0xFF);
				configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_FW_INTERNAL_MSB] = (byte) ((mShimmerVerObject.mFirmwareVersionInternal>>8)&0xFF);
				
				if(isPayloadDesignV2orAbove()) {
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.RESET_REASON] = resetReason;
				}
				if(isPayloadDesignV3orAbove()) {
					int resetCounter = (this.resetCounter==null? 0:this.resetCounter);
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.RESET_COUNTER_LSB] = (byte) (resetCounter & 0xFF);
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.RESET_COUNTER_MSB] = (byte) ((resetCounter>>8) & 0xFF);
					int firstPayloadIndexAfterBoot = (this.firstPayloadIndexAfterBoot==null? 0:this.firstPayloadIndexAfterBoot);
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.FIRST_PAYLOAD_AFTER_RESET_LSB] = (byte) (firstPayloadIndexAfterBoot & 0xFF);
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.FIRST_PAYLOAD_AFTER_RESET_MSB] = (byte) ((firstPayloadIndexAfterBoot>>8) & 0xFF);
				}
				
				if(isPayloadDesignV4orAbove()) {
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_HW_MAJOR] = (byte) getExpansionBoardId();
					configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_HW_MINOR] = (byte) getExpansionBoardRev();
				}
			}
		} else {
			if(generateForWritingToShimmer) {
				configBytes = OperationalConfigPayload.getDefaultPayloadConfigForFwVersion(mShimmerVerObject);
			} else {
				configBytes = new byte[OperationalConfigPayload.getPayloadConfigSizeForFwVersion(mShimmerVerObject)];
			}

			configBytes[OP_CONFIG_BYTE_INDEX.HEADER_BYTE] = AbstractPayload.VALID_CONFIG_BYTE;
			
			long enabledSensors = getEnabledSensors();
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] = (byte) (enabledSensors & OP_CONFIG_BIT_MASK.ENABLED_SENSORS_GEN_CFG_0);
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_1] = (byte) ((enabledSensors >> 8) & OP_CONFIG_BIT_MASK.ENABLED_SENSORS_GEN_CFG_1);
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_2] = (byte) ((enabledSensors >> 8) & OP_CONFIG_BIT_MASK.ENABLED_SENSORS_GEN_CFG_2);

			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] |= bluetoothEnabled? OP_CONFIG_BIT_MASK.BLUETOOTH_ENABLED:0x00;
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] |= usbEnabled? OP_CONFIG_BIT_MASK.USB_ENABLED:0x00;
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] |= prioritiseLongTermFlash? OP_CONFIG_BIT_MASK.PRIORITISE_LONG_TERM_FLASH_STORAGE:0x00;
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] |= deviceEnabled? OP_CONFIG_BIT_MASK.DEVICE_ENABLED:0x00;
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] |= recordingEnabled? OP_CONFIG_BIT_MASK.RECORDING_ENABLED:0x00;

			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_1] |= dataCompressionMode.ordinal() & OP_CONFIG_BIT_MASK.DATA_COMPRESSION_MODE;

			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_2] |= (passkeyMode.ordinal() & OP_CONFIG_BIT_MASK.PASSKEY_MODE) << OP_CONFIG_BIT_SHIFT.PASSKEY_MODE;
			configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_2] |= (batteryType.ordinal() & OP_CONFIG_BIT_MASK.BATTERY_TYPE) << OP_CONFIG_BIT_SHIFT.BATTERY_TYPE;

			configBytes[OP_CONFIG_BYTE_INDEX.START_TIME_BYTE_0] = (byte) (recordingStartTimeMinutes & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.START_TIME_BYTE_1] = (byte) ((recordingStartTimeMinutes >> 8) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.START_TIME_BYTE_2] = (byte) ((recordingStartTimeMinutes >> 16) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.START_TIME_BYTE_3] = (byte) ((recordingStartTimeMinutes >> 24) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.END_TIME_BYTE_0] = (byte) (recordingEndTimeMinutes & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.END_TIME_BYTE_1] = (byte) ((recordingEndTimeMinutes >> 8) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.END_TIME_BYTE_2] = (byte) ((recordingEndTimeMinutes >> 16) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.END_TIME_BYTE_3] = (byte) ((recordingEndTimeMinutes >> 24) & 0xFF);
			
			configBytes[OP_CONFIG_BYTE_INDEX.BLE_RETRY_COUNT] = (byte) bleConnectionRetriesPerDay;
			configBytes[OP_CONFIG_BYTE_INDEX.BLE_TX_POWER] = bleTxPower.getByteMask();

			byte[] pendingEventScheduleDataTransferAry = pendingEventScheduleDataTransfer.generateByteArray();
			System.arraycopy(pendingEventScheduleDataTransferAry, 0, configBytes, OP_CONFIG_BYTE_INDEX.BLE_DATA_TRANS_WKUP_INT_HRS, pendingEventScheduleDataTransferAry.length);
			byte[] pendingEventScheduleStatusSyncAry = pendingEventScheduleStatusSync.generateByteArray();
			System.arraycopy(pendingEventScheduleStatusSyncAry, 0, configBytes, OP_CONFIG_BYTE_INDEX.BLE_STATUS_WKUP_INT_HRS, pendingEventScheduleStatusSyncAry.length);
			byte[] pendingEventScheduleRwcSyncAry = pendingEventScheduleRwcSync.generateByteArray();
			System.arraycopy(pendingEventScheduleRwcSyncAry, 0, configBytes, OP_CONFIG_BYTE_INDEX.BLE_RTC_SYNC_WKUP_INT_HRS, pendingEventScheduleRwcSyncAry.length);

			configBytes[OP_CONFIG_BYTE_INDEX.ADAPTIVE_SCHEDULER_INT_LSB] = (byte) (adaptiveSchedulerInterval & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.ADAPTIVE_SCHEDULER_INT_MSB] = (byte) ((adaptiveSchedulerInterval >> 8) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.ADAPTIVE_SCHEDULER_FAILCOUNT_MAX] = (byte) adaptiveSchedulerFailCount;

			configBytes[OP_CONFIG_BYTE_INDEX.PPG_REC_DUR_SECS_LSB] = (byte) (ppgRecordingDurationSeconds & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.PPG_REC_DUR_SECS_MSB] = (byte) ((ppgRecordingDurationSeconds >> 8) & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.PPG_REC_INT_MINS_LSB] = (byte) (ppgRecordingIntervalMinutes & 0xFF);
			configBytes[OP_CONFIG_BYTE_INDEX.PPG_REC_INT_MINS_MSB] = (byte) ((ppgRecordingIntervalMinutes >> 8) & 0xFF);

			for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()) {
				abstractSensor.configBytesGenerate(this, configBytes, commType);
			}
		}
		mConfigBytes = configBytes;
		return configBytes;
	}
	
	public void configBytesParseAndInitialiseAlgorithms(byte[] configBytes, COMMUNICATION_TYPE commType) {
		
		this.configBytesParse(configBytes, commType);

		// No issue calling this here at the moment as there is nothing that relies on
		// the sampling rates of individual sensors inside DataProcessingVerisense
		// (e.g., filters). If we want to add something in the future, we need to find a
		// way to pass individual sensor sampling rates to that class.
		initaliseDataProcessing();

		if(isPayloadDesignV8orAbove()) {
			initializeAlgorithmsWithDifferentRatesPerSensor();
		} else {
			initializeAlgorithms();
		}
	}
	
	@Override
	public void configureFromClone(ShimmerDevice shimmerDeviceClone) throws ShimmerException {
		super.configureFromClone(shimmerDeviceClone);
		configBytesParse(shimmerDeviceClone.configBytesGenerate(true));
	}
	
	private void writeAndReadOperationalConfig() throws ShimmerException {
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).writeAndReadOperationalConfig(getShimmerConfigBytes());
	}
	
	@Override
	public void configBytesParse(byte[] configBytes, COMMUNICATION_TYPE commType) {
		mConfigBytes = configBytes;
		
		long enabledSensors = 0 ; 
				
		if (commType == COMMUNICATION_TYPE.SD) {
			isExtendedPayloadConfig = isExtendedPayloadConfig(configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0]);
			dataCompressionMode = DATA_COMPRESSION_MODE.values()[(configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0]>>0)&0x03];
			
			ShimmerVerObject svo = defaultSvo;
			ExpansionBoardDetails eBD = defaultEbd;
			
			if(isExtendedPayloadConfig){
				byte[] payloadConfigFwVer = new byte[4];
				System.arraycopy(configBytes, PAYLOAD_CONFIG_BYTE_INDEX.REV_FW_MAJOR, payloadConfigFwVer, 0, payloadConfigFwVer.length);
				svo = parseFirmwareVersionToShimmerVerObject(payloadConfigFwVer);
			}
			setShimmerVersionObject(svo);
			
			if(isExtendedPayloadConfig){
				setResetReason(configBytes[PAYLOAD_CONFIG_BYTE_INDEX.RESET_REASON]);
				
				if(isPayloadDesignV3orAbove()) {
					Integer resetCounter = ((configBytes[PAYLOAD_CONFIG_BYTE_INDEX.RESET_COUNTER_MSB] & 0xFF) << 8) | (configBytes[PAYLOAD_CONFIG_BYTE_INDEX.RESET_COUNTER_LSB] & 0xFF);
					setResetCounter(resetCounter);
					Integer firstPayloadResetAfterBoot = ((configBytes[PAYLOAD_CONFIG_BYTE_INDEX.FIRST_PAYLOAD_AFTER_RESET_MSB] & 0xFF) << 8) | (configBytes[PAYLOAD_CONFIG_BYTE_INDEX.FIRST_PAYLOAD_AFTER_RESET_LSB] & 0xFF);
					setFirstPayloadResetAfterBoot(firstPayloadResetAfterBoot);
				}
				
				if(isPayloadDesignV4orAbove()) {
					int hwVerMajor = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_HW_MAJOR];
					hwVerMajor = correctHwVersion(hwVerMajor);
					int hwVerMinor = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.REV_HW_MINOR];
					setHardwareVersion(hwVerMajor);
					eBD = new ExpansionBoardDetails(hwVerMajor, hwVerMinor, INVALID_VALUE);
				}
			}
			setExpansionBoardDetails(eBD);
			
			// sensorAndConfigMapsCreate needs to be called after the ShimmerVerObject
			// and ExpansionBoardDetails have been set but before any sensor config is processed.
			sensorAndConfigMapsCreate();

			enabledSensors = (configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0] & 0xE0);
			int payloadConfig2Bitmask = 0;
			if(isPayloadDesignV12orAbove()) {
				payloadConfig2Bitmask = 0xFE;
			} else if(isPayloadDesignV4orAbove()) {
				payloadConfig2Bitmask = 0xFC;
			}
			if(payloadConfig2Bitmask>0) {
				enabledSensors |= ((configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG2] & payloadConfig2Bitmask) << 8);
			}
		} else {
			enabledSensors = (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] & OP_CONFIG_BIT_MASK.ENABLED_SENSORS_GEN_CFG_0);
			enabledSensors |= (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_1] & OP_CONFIG_BIT_MASK.ENABLED_SENSORS_GEN_CFG_1) << 8;
			enabledSensors |= (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_2] & OP_CONFIG_BIT_MASK.ENABLED_SENSORS_GEN_CFG_2) << 8;
			
			bluetoothEnabled = (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] & OP_CONFIG_BIT_MASK.BLUETOOTH_ENABLED) == OP_CONFIG_BIT_MASK.BLUETOOTH_ENABLED;
			usbEnabled = (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] & OP_CONFIG_BIT_MASK.USB_ENABLED) == OP_CONFIG_BIT_MASK.USB_ENABLED;
			prioritiseLongTermFlash = (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] & OP_CONFIG_BIT_MASK.PRIORITISE_LONG_TERM_FLASH_STORAGE) == OP_CONFIG_BIT_MASK.PRIORITISE_LONG_TERM_FLASH_STORAGE;
			deviceEnabled = (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] & OP_CONFIG_BIT_MASK.DEVICE_ENABLED) == OP_CONFIG_BIT_MASK.DEVICE_ENABLED;
			recordingEnabled = (configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_0] & OP_CONFIG_BIT_MASK.RECORDING_ENABLED) == OP_CONFIG_BIT_MASK.RECORDING_ENABLED; 

			dataCompressionMode = DATA_COMPRESSION_MODE.values()[configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_1] & OP_CONFIG_BIT_MASK.DATA_COMPRESSION_MODE];
			passkeyMode = PASSKEY_MODE.values()[(configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_2] >> OP_CONFIG_BIT_SHIFT.PASSKEY_MODE) & OP_CONFIG_BIT_MASK.PASSKEY_MODE];
			batteryType = BATTERY_TYPE.values()[(configBytes[OP_CONFIG_BYTE_INDEX.GEN_CFG_2] >> OP_CONFIG_BIT_SHIFT.BATTERY_TYPE) & OP_CONFIG_BIT_MASK.BATTERY_TYPE];

			recordingStartTimeMinutes = AbstractPayload.parseByteArrayAtIndex(configBytes, OP_CONFIG_BYTE_INDEX.START_TIME_BYTE_0, CHANNEL_DATA_TYPE.UINT24);
			recordingEndTimeMinutes = AbstractPayload.parseByteArrayAtIndex(configBytes, OP_CONFIG_BYTE_INDEX.END_TIME_BYTE_0, CHANNEL_DATA_TYPE.UINT24);
			
			bleConnectionRetriesPerDay = configBytes[OP_CONFIG_BYTE_INDEX.BLE_RETRY_COUNT];
			bleTxPower = BLE_TX_POWER.getSettingFromMask(configBytes[OP_CONFIG_BYTE_INDEX.BLE_TX_POWER]);
			
			pendingEventScheduleDataTransfer = new PendingEventSchedule(configBytes, OP_CONFIG_BYTE_INDEX.BLE_DATA_TRANS_WKUP_INT_HRS);
			pendingEventScheduleStatusSync = new PendingEventSchedule(configBytes, OP_CONFIG_BYTE_INDEX.BLE_STATUS_WKUP_INT_HRS);
			pendingEventScheduleRwcSync = new PendingEventSchedule(configBytes, OP_CONFIG_BYTE_INDEX.BLE_RTC_SYNC_WKUP_INT_HRS);
			
			adaptiveSchedulerInterval = (int) AbstractPayload.parseByteArrayAtIndex(configBytes, OP_CONFIG_BYTE_INDEX.ADAPTIVE_SCHEDULER_INT_LSB, CHANNEL_DATA_TYPE.UINT16);
			adaptiveSchedulerFailCount = configBytes[OP_CONFIG_BYTE_INDEX.ADAPTIVE_SCHEDULER_FAILCOUNT_MAX] & 0xFF;
			
			ppgRecordingDurationSeconds = (int) AbstractPayload.parseByteArrayAtIndex(configBytes, OP_CONFIG_BYTE_INDEX.PPG_REC_DUR_SECS_LSB, CHANNEL_DATA_TYPE.UINT16);
			ppgRecordingIntervalMinutes = (int) AbstractPayload.parseByteArrayAtIndex(configBytes, OP_CONFIG_BYTE_INDEX.PPG_REC_INT_MINS_LSB, CHANNEL_DATA_TYPE.UINT16);
		}

		if(commType==COMMUNICATION_TYPE.SD && isPayloadDesignV13orAbove()) {
			// Second-generation: ACCEL2/GYRO enable bits (PAYLOAD_CONFIG0 bits 6/5) map to the
			// LSM6DSV (not LSM6DS3); LIS2MDL mag enable is in GEN_CFG_3 (abs byte 29) bit 2.
			// The PAYLOAD_CONFIG2-derived enables (GSR/VBATT/PPG, bits 8-15, computed above)
			// are unchanged from gen-1 and must be preserved - only remap the gen-1
			// PAYLOAD_CONFIG0 accel1/accel2/gyro bits (0xE0).
			enabledSensors &= ~0xE0L;
			int gen2Cfg0 = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.PAYLOAD_CONFIG0] & 0xFF;
			if((gen2Cfg0 & (1<<6))!=0) { enabledSensors |= Configuration.Verisense.SensorBitmap.LSM6DSV_ACCEL; }
			if((gen2Cfg0 & (1<<5))!=0) { enabledSensors |= Configuration.Verisense.SensorBitmap.LSM6DSV_GYRO; }
			int gen2GenCfg3 = configBytes[PAYLOAD_CONFIG_BYTE_INDEX.GEN_CFG_3] & 0xFF;
			if((gen2GenCfg3 & (1<<2))!=0) { enabledSensors |= Configuration.Verisense.SensorBitmap.LSM6DSV_MAG; }
		}
		setEnabledAndDerivedSensorsAndUpdateMaps(enabledSensors, mDerivedSensors, commType);
		
		for(AbstractSensor abstractSensor:mMapOfSensorClasses.values()) {
			abstractSensor.configBytesParse(this, configBytes, commType);
		}

		// Useful for debugging during development
//		printSensorParserAndAlgoMaps();
	}
	
	/** Fix for a legacy HW ID assignment issue
	 * @param hardwareVersion
	 * @return
	 */
	public static int correctHwVersion(int hardwareVersion) {
		if(hardwareVersion==0) {
			hardwareVersion = HW_ID.VERISENSE_DEV_BRD;
		} else if(hardwareVersion==1) {
			hardwareVersion = HW_ID.VERISENSE_IMU;
		}
		return hardwareVersion;
	}

	@Override
	public void algorithmMapUpdateFromEnabledSensorsVars() {
		updateDerivedSensorsFromEnabledSensors();
		super.algorithmMapUpdateFromEnabledSensorsVars();
	}
	
	public void updateDerivedSensorsFromEnabledSensors() {
		mDerivedSensors = 0;
		if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DS3_GYRO)) {
			mDerivedSensors |= GyroOnTheFlyCalModuleVerisense.algoGyroOnTheFlyCalVerisense.mDerivedSensorBitmapID;
		}
	}

	/**
	 * @see ShimmerDevice.initializeAlgorithms() 
	 */
	public void initializeAlgorithmsWithDifferentRatesPerSensor() {
		for (AbstractAlgorithm aa:mMapOfAlgorithmModules.values()){
			try {
				if(aa.isEnabled()){
					//TODO make this more dynamic (maybe by including a sensor class key list in the AbstractAlgorithm class)
					SENSORS sensorClassKey = null;
					if(aa instanceof GyroOnTheFlyCalModuleVerisense) {
						sensorClassKey = SENSORS.LSM6DS3;
					}
					
					if(sensorClassKey!=null) {
						double sensorSamplingRate = getSamplingRateForSensor(sensorClassKey);
						
						aa.setShimmerSamplingRate(sensorSamplingRate);
						aa.initialize();
					}
				} else {
					//TODO stop the algorithm
				}
			} catch (Exception e1) {
				consolePrintException("Error initialising algorithm module\t" + aa.getAlgorithmName(), e1.getStackTrace());
			}
		}
	}
	
	//TODO copied from the ShimmerVerObject class, create a new method there
	public static ShimmerVerObject parseFirmwareVersionToShimmerVerObject(byte[] payloadConfigFwVer) {
		ShimmerVerObject svo = new ShimmerVerObject();
		svo.mHardwareVersion = DEFAULT_HW_ID;
		svo.mFirmwareVersionMajor = payloadConfigFwVer[0]&0xFF;
		svo.mFirmwareVersionMinor = payloadConfigFwVer[1]&0xFF;
		svo.mFirmwareVersionInternal = ((payloadConfigFwVer[2]&0xFF) | (payloadConfigFwVer[3]<<8))&0xFFFF;
		return svo;
	}

	public boolean isPayloadDesignV1orAbove() {
		return PayloadContentsDetails.isPayloadDesignV1orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV2orAbove() {
		return PayloadContentsDetails.isPayloadDesignV2orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV3orAbove() {
		return PayloadContentsDetails.isPayloadDesignV3orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV4orAbove() {
		return PayloadContentsDetails.isPayloadDesignV4orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV5orAbove() {
		return PayloadContentsDetails.isPayloadDesignV5orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV6orAbove() {
		return PayloadContentsDetails.isPayloadDesignV6orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV7orAbove() {
		return PayloadContentsDetails.isPayloadDesignV7orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV8orAbove() {
		return PayloadContentsDetails.isPayloadDesignV8orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV9orAbove() {
		return PayloadContentsDetails.isPayloadDesignV9orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV10orAbove() {
		return PayloadContentsDetails.isPayloadDesignV10orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV11orAbove() {
		return PayloadContentsDetails.isPayloadDesignV11orAbove(getShimmerVerObject());
	}

	public boolean isPayloadDesignV12orAbove() {
		return PayloadContentsDetails.isPayloadDesignV12orAbove(getShimmerVerObject());
	}

	/**
	 * Second-generation payload design (firmware "v9" / 32-byte config header,
	 * bytes 4..35, of which bytes 34..35 are the calibration-blob CRC).
	 * @see PayloadContentsDetails#isPayloadDesignV13orAbove(ShimmerVerObject)
	 */
	public boolean isPayloadDesignV13orAbove() {
		return PayloadContentsDetails.isPayloadDesignV13orAbove(getShimmerVerObject());
	}

	/**
	 * Whether a given Verisense hardware revision is second-generation
	 * (SR68-9/10, SR61-5/6). Mirrors the TypeScript SDK
	 * (shimmer-web-sdk: hardwareModels.ts {@code isVerisenseSecondGenerationHardware})
	 * and the firmware Model IC matrix
	 * (verisense-firmware/docs/VERISENSE_MODEL_IC_MATRIX.md):
	 * <ul>
	 *   <li>SR61, minor &ge; 5</li>
	 *   <li>SR68, minor &ge; 9</li>
	 *   <li>any future major revision above SR68 (68)</li>
	 * </ul>
	 * Gen-2 is distinguished by hardware major/minor revision, NOT by a separate
	 * {@code HW_ID_SR_CODES} value (SR68-9/10 is still the Pulse+ major id 68).
	 *
	 * @param hwMajor REV_HW_MAJOR from the payload header / production config
	 * @param hwMinor REV_HW_MINOR from the payload header / production config
	 */
	public static boolean isSecondGenerationHardware(int hwMajor, int hwMinor) {
		if(hwMajor > HW_ID.VERISENSE_PULSE_PLUS) {
			return true;
		}
		if(hwMajor == HW_ID.VERISENSE_IMU && hwMinor >= 5) {
			return true;
		}
		if(hwMajor == HW_ID.VERISENSE_PULSE_PLUS && hwMinor >= 9) {
			return true;
		}
		return false;
	}

	/**
	 * @return
	 * @see PayloadContentsDetails.isCsvHeaderDesignAzMarkingPoint()
	 */
	public boolean isCsvHeaderDesignAzMarkingPoint() {
		return PayloadContentsDetails.isCsvHeaderDesignAzMarkingPoint(getShimmerVerObject());
	}
	
	//TODO move to ShimmerDriver
	public static boolean isFwMajorMinorInternalVerEqual(ShimmerVerObject svo, ShimmerVerObject svo2) {
		if(svo.getFirmwareVersionMajor()==svo2.getFirmwareVersionMajor()
				&& svo.getFirmwareVersionMinor()==svo2.getFirmwareVersionMinor()
				&& svo.getFirmwareVersionInternal()==svo2.getFirmwareVersionInternal()) {
			return true;
		}
		return false;
	}

	@Override
	public double getSamplingRateShimmer() {
		if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LIS2DW12_ACCEL)) {
			return getSamplingRateForSensor(SENSORS.LIS2DW12);
		} else if(isEitherLsm6ds3ChannelEnabled()) {
			return getSamplingRateForSensor(SENSORS.LSM6DS3);
		} else if(isAnyLsm6dsvChannelEnabled()) {
			return getSamplingRateForSensor(SENSORS.LSM6DSV);
		} else if(isHwPpgAndAnyMaxChEnabled()) {
			return getSamplingRateForSensor(getPpgSensorClassKey());
		} else if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.VBATT)) {
			return getSamplingRateForSensor(SENSORS.Battery);
		} else if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.GSR)) {
			return getSamplingRateForSensor(SENSORS.GSR);
		}
		return 0.0;
	}

	public AbstractSensor getAbstractSensorPpg() {
		SENSORS sensorClassKey = getPpgSensorClassKey();
		return getSensorClass(sensorClassKey);
	}

	public SENSORS getPpgSensorClassKey() {
		for(SENSORS sensorClassKey:LIST_OF_PPG_SENSORS) {
			AbstractSensor abstractSensor = getSensorClass(sensorClassKey);
			if(abstractSensor!=null) {
				return sensorClassKey;
			}
		}
		return null;
	}

	public SENSORS getPrimaryAccel() {
		if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LIS2DW12_ACCEL)) {
			return SENSORS.LIS2DW12;
		} else if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DS3_ACCEL)) {
			return SENSORS.LSM6DS3;
		}
		return null;
	}

	public double getSamplingRateAccel() {
		AbstractSensor.SENSORS sensorClassKey = getPrimaryAccel();
		return getSamplingRateForSensor(sensorClassKey);
	}

	public double getSamplingRateForSensor(AbstractSensor.SENSORS sensorClassKey) {
		if(sensorClassKey!=null) {
			AbstractSensor abstractSensor = getSensorClass(sensorClassKey);
			if(abstractSensor!=null) {
				Object returnValue = abstractSensor.getConfigValueUsingConfigLabel(GuiLabelConfigCommon.RATE);
				if(returnValue!=null && returnValue instanceof Double) {
					return (double)returnValue;
				}
			}
		}
		return 0.0;
	}

	public double getFastestSamplingRateOfSensors(COMMUNICATION_TYPE commType) {
		double fastestSamplingRate = 0.0;
		if(mMapOfSensorClasses!=null){
			for(Entry<SENSORS, AbstractSensor> entry:getMapOfEnabledAbstractSensors(commType).entrySet()){
				double sensorSamplingRate = getSamplingRateForSensor(entry.getKey());
				fastestSamplingRate = Math.max(fastestSamplingRate, sensorSamplingRate);
			}
		}
		return fastestSamplingRate;
	}
	
	public LinkedHashMap<SENSORS, AbstractSensor> getMapOfEnabledAbstractSensors(COMMUNICATION_TYPE commType) {
		LinkedHashMap<SENSORS, AbstractSensor> mapOfEnabledAbstractSensors = new LinkedHashMap<SENSORS, AbstractSensor>();
		if(mMapOfSensorClasses!=null){
			for(Entry<SENSORS, AbstractSensor> entry:mMapOfSensorClasses.entrySet()){
				if(entry.getValue().isAnySensorChannelEnabled(commType)) {
					mapOfEnabledAbstractSensors.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return mapOfEnabledAbstractSensors;
	}

	public boolean compareFwVersions(ShimmerVerObject svo) {
		return compareFwVersions(mShimmerVerObject, svo);
	}

	//TODO move to ShimmerVerObject or UtilShimmer class?
	public static boolean compareFwVersions(ShimmerVerObject svo1, ShimmerVerObject svo2) {
		return UtilShimmer.compareVersions(svo1.getFirmwareVersionMajor(), svo1.getFirmwareVersionMinor(), svo1.getFirmwareVersionInternal(),
				svo2.getFirmwareVersionMajor(), svo2.getFirmwareVersionMinor(), svo2.getFirmwareVersionInternal());
	}

	/**
	 * Checks Bit 4 of the first payload config byte to determine if the payload
	 * configuration is of the latest design (i.e., extended configuration bytes)
	 * in-which the number of bytes is greater then 2. The subsequet FW version
	 * bytes can subsequently be used to determine the exact quantity of bytes in
	 * the payload configuration.
	 * 
	 * @param payloadConfig byte 0
	 * @return true if the EXT_CFG bit (bit 4) is high
	 */
	public static boolean isExtendedPayloadConfig(byte payloadConfig) {
		return (payloadConfig&0x10)==0x10;
	}
	
	public String generateResetReasonStr() {
		StringBuilder sb = new StringBuilder();
		sb.append(CSV_HEADER_LINES.RESET);
		if(isPayloadDesignV2orAbove()) {
			String resetReasonByte = " " + UtilShimmer.byteToHexStringFormatted(resetReason);
			if(resetReason==RESET_REASON.POR_OR_BOD.bitMask) {
				sb.append(RESET_REASON.POR_OR_BOD.descriptionShort + resetReasonByte);
			}
			else if(resetReason==RESET_REASON.CLEARED.bitMask) {
				sb.append(RESET_REASON.CLEARED.descriptionShort + resetReasonByte);
			} else {
				List<RESET_REASON> listOfReasons = new ArrayList<RESET_REASON>();
				for(RESET_REASON resetReasonToCheck:RESET_REASON.values()) {
					if(resetReasonToCheck!=RESET_REASON.POR_OR_BOD 
							&& resetReasonToCheck!=RESET_REASON.CLEARED
							&& (resetReason&resetReasonToCheck.bitMask)==resetReasonToCheck.bitMask) {
						listOfReasons.add(resetReasonToCheck);
					}
				}

				for(int i=0;i<listOfReasons.size();i++) {
					RESET_REASON resetReasonBit = listOfReasons.get(i);
					sb.append(resetReasonBit.descriptionShort);
					// Add a ";" unless this is the last loop
					if(i!=listOfReasons.size()-1) {
						sb.append(";");
					}
				}
				
				sb.append(resetReasonByte);
			}
		} else {
			sb.append(UtilVerisenseDriver.FEATURE_NOT_AVAILABLE);
		}

		sb.append("; Count = ");
		if(isPayloadDesignV3orAbove()) {
			sb.append(resetCounter==null? UtilVerisenseDriver.FEATURE_NOT_AVAILABLE:resetCounter);
		} else {
			sb.append(UtilVerisenseDriver.FEATURE_NOT_AVAILABLE);
		}

		sb.append("; Start Index = ");
		if(isPayloadDesignV3orAbove()) {
			sb.append(firstPayloadIndexAfterBoot==null? UtilVerisenseDriver.FEATURE_NOT_AVAILABLE:firstPayloadIndexAfterBoot);
		} else {
			sb.append(UtilVerisenseDriver.FEATURE_NOT_AVAILABLE);
		}

		return sb.toString();
	}

	//TODO move to sensor classes?
	public String generateSensorConfigStrSingleSensor(AbstractSensor.SENSORS sensorClassKey, double calculatedSamplingRate) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(CSV_LINE_TITLES.SENSOR_CONFIG);
		if(isCsvHeaderDesignAzMarkingPoint()) {
			sb.append(SENSOR_CONFIG_STRINGS.RATE);
			sb.append(getSamplingRateShimmer());
			sb.append(" ");
			sb.append(CHANNEL_UNITS.FREQUENCY);
			sb.append("; ");
		}
		
		if((sensorClassKey==AbstractSensor.SENSORS.LIS2DW12) 
				&& isSensorEnabled(Configuration.Verisense.SENSOR_ID.LIS2DW12_ACCEL)) {
			SensorLIS2DW12 sensorLis2dw12 = getSensorLIS2DW12();
			
			// Accel1 section
			if(isCsvHeaderDesignAzMarkingPoint()) {
				sb.append(SensorLIS2DW12.ACCEL_ID);
				sb.append(" ");
			} else {
				sb.append(generateCalcSamplingRateConfigStr(sensorClassKey, sensorLis2dw12.getAccelRateFreq(), calculatedSamplingRate));
			}

			// E.g. +/- 4g
			sb.append(SENSOR_CONFIG_STRINGS.RANGE);
			sb.append(sensorLis2dw12.getAccelRangeString());
			
			if(isCsvHeaderDesignAzMarkingPoint()) {
				// E.g. Low-Power
				sb.append("; Mode = ");
				sb.append(sensorLis2dw12.getAccelModeString());
				
				sb.append("; LP_Mode = ");
				sb.append(sensorLis2dw12.getAccelLpModeString());
			} else {
				sb.append("; Mode = ");
				sb.append(sensorLis2dw12.getAccelModeMergedString());
				sb.append("; ");
				sb.append(SENSOR_CONFIG_STRINGS.RESOLUTION);
				sb.append(sensorLis2dw12.getAccelResolutionString());
			}
			
			if(!isCsvHeaderDesignAzMarkingPoint()) {
				sb.append("}");
			}
		} else if((sensorClassKey==AbstractSensor.SENSORS.LSM6DS3)
				&& isEitherLsm6ds3ChannelEnabled()) {
			SensorLSM6DS3 sensorLsm6ds3 = getSensorLSM6DS3();
			
			// Accel2 section
			if(isCsvHeaderDesignAzMarkingPoint()) {
				sb.append(SensorLSM6DS3.ACCEL_ID);
				sb.append(" ");
			} else {
				sb.append(generateCalcSamplingRateConfigStr(sensorClassKey, sensorLsm6ds3.getRateFreq(), calculatedSamplingRate));
				sb.append("Accel ");
			}

			sb.append(SENSOR_CONFIG_STRINGS.RANGE);
			if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DS3_ACCEL) 
					|| isCsvHeaderDesignAzMarkingPoint()) {
				sb.append(sensorLsm6ds3.getAccelRangeString());
			}
			else {
				sb.append("Off");
			}

			// Gyro section
			sb.append("; Gyro ");
			sb.append(SENSOR_CONFIG_STRINGS.RANGE);
			if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DS3_GYRO) 
					|| isCsvHeaderDesignAzMarkingPoint()) {
				sb.append(sensorLsm6ds3.getGyroRangeString());
			}
			else {
				sb.append("Off");
			}
			
			
			if(!isCsvHeaderDesignAzMarkingPoint()) {
				sb.append("; ");
				sb.append(SENSOR_CONFIG_STRINGS.RESOLUTION);
				sb.append("16-bit");
				sb.append("}");
			}
		} else if((sensorClassKey==AbstractSensor.SENSORS.LSM6DSV)
				&& isAnyLsm6dsvChannelEnabled()) {
			// Second-generation IMU (LSM6DSV accel/gyro + LIS2MDL mag). Gen-2 always uses the
			// new (non-AZ-marking-point) CSV header design.
			SensorLSM6DSV sensorLsm6dsv = getSensorLSM6DSV();

			sb.append(generateCalcSamplingRateConfigStr(sensorClassKey, sensorLsm6dsv.getRateFreq(), calculatedSamplingRate));

			sb.append("Accel ");
			sb.append(SENSOR_CONFIG_STRINGS.RANGE);
			sb.append(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL) ? sensorLsm6dsv.getAccelRangeString() : "Off");

			sb.append("; Gyro ");
			sb.append(SENSOR_CONFIG_STRINGS.RANGE);
			sb.append(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO) ? sensorLsm6dsv.getGyroRangeString() : "Off");

			sb.append("; Mag ");
			sb.append(SENSOR_CONFIG_STRINGS.RATE);
			if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG)) {
				sb.append(sensorLsm6dsv.getMagRateFreq());
				sb.append(" ");
				sb.append(CHANNEL_UNITS.FREQUENCY);
			} else {
				sb.append("Off");
			}

			sb.append("; ");
			sb.append(SENSOR_CONFIG_STRINGS.RESOLUTION);
			sb.append("16-bit");
			sb.append("}");
		} else if(LIST_OF_PPG_SENSORS.contains(sensorClassKey) && isHwPpgAndAnyMaxChEnabled()) {
			AbstractSensor abstractSensor = getAbstractSensorPpg();
			if(abstractSensor!=null) {
				SensorMAX86XXX sensorMAX86XXX = (SensorMAX86XXX)abstractSensor;
				
				// PPG section
				if(isCsvHeaderDesignAzMarkingPoint()) {
					sb.append("PPG ");
				} else {
					sb.append(generateCalcSamplingRateConfigStr(sensorClassKey, sensorMAX86XXX.getSamplingRateFreq(), calculatedSamplingRate));
				}

				sb.append("Pulse Width = ");				// E.g. 50 us
				int ppgPulseWidthConfigValue = sensorMAX86XXX.getPpgPulseWidthConfigValue();
				String ppgPulseWidth = SensorMAX86XXX.CONFIG_OPTION_PPG_PULSE_WIDTH.getConfigStringFromConfigValue(ppgPulseWidthConfigValue);
				ppgPulseWidth = ppgPulseWidth.replaceAll(CHANNEL_UNITS.MICROSECONDS, (" "+CHANNEL_UNITS.MICROSECONDS));
				sb.append(ppgPulseWidth);
				
				// Pulse amplitude
				if(isPayloadDesignV5orAbove()) {
					sb.append("; ");
					
					List<Integer> listOfEnabledMaxPpgCh = new ArrayList<Integer>();
					int[] ppgChArray = new int[] {
							// MAX86150 and MAX86916 PPG channels
							Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_RED,
							Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_IR,
							// MAX86916 PPG channels
							Configuration.Verisense.SENSOR_ID.MAX86916_PPG_GREEN,
							Configuration.Verisense.SENSOR_ID.MAX86916_PPG_BLUE};
					for(int ppgCh:ppgChArray) {
						if(isSensorEnabled(ppgCh)) {
							listOfEnabledMaxPpgCh.add(ppgCh);
						}
					}
					
					if (listOfEnabledMaxPpgCh.size() > 0) {
						sb.append("LED Amplitude [");
						for(int chIdx=0;chIdx<listOfEnabledMaxPpgCh.size();chIdx++) {
							if(chIdx>0 && chIdx<(listOfEnabledMaxPpgCh.size())) {
								sb.append(", ");
							}
							Integer sensorId = listOfEnabledMaxPpgCh.get(chIdx);

							if (sensorId == Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_RED) {
								sb.append("Red = ");
								sb.append(sensorMAX86XXX.getPpgLedAmplitudeRedString());
							} else if (sensorId == Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_IR) {
								sb.append("IR = ");
								sb.append(sensorMAX86XXX.getPpgLedAmplitudeIrString());
							} else {
								if(abstractSensor instanceof SensorMAX86916) {
									SensorMAX86916 sensorMAX86916 = (SensorMAX86916)abstractSensor;
									if (sensorId == Configuration.Verisense.SENSOR_ID.MAX86916_PPG_GREEN) {
										sb.append("Green = ");
										sb.append(sensorMAX86916.getPpgLedAmplitudeGreenString());
									}
									else if (sensorId == Configuration.Verisense.SENSOR_ID.MAX86916_PPG_BLUE) {
										sb.append("Blue = ");
										sb.append(sensorMAX86916.getPpgLedAmplitudeBlueString());
									}
								}
							}
						}
						sb.append("]");
					}
					
				} else {
					if(abstractSensor instanceof SensorMAX86150) {
						SensorMAX86150 sensorMAX86150 = (SensorMAX86150)abstractSensor;
						sb.append("; Amplitude = ");
						sb.append(sensorMAX86150.getPpgLedAmplitudeRedString());
					}
				}
				
				// Sample average
				sb.append("; Sample Average = ");
				int ppgSampleAverageConfigValue = sensorMAX86XXX.getPpgSampleAverageConfigValue();
				String ppgSampleAverage = SensorMAX86XXX.CONFIG_OPTION_PPG_SAMPLE_AVG.getConfigStringFromConfigValue(ppgSampleAverageConfigValue);
				sb.append(ppgSampleAverage);
				
				// Resolution
				sb.append("; ");
				sb.append(SENSOR_CONFIG_STRINGS.RESOLUTION);
				int ppgAdcResolutionConfigValue = sensorMAX86XXX.getPpgAdcResolutionConfigValue();
				String ppgAdcResolution = SensorMAX86XXX.CONFIG_OPTION_PPG_ADC_RESOLUTION.getConfigStringFromConfigValue(ppgAdcResolutionConfigValue);
				sb.append(ppgAdcResolution);
				
				if(!isCsvHeaderDesignAzMarkingPoint()) {
					sb.append("}");
				}
			}
		// ADC based channels	
		} else if((sensorClassKey==AbstractSensor.SENSORS.Battery && isSensorEnabled(Configuration.Verisense.SENSOR_ID.VBATT))
				|| (sensorClassKey==AbstractSensor.SENSORS.GSR && isSensorEnabled(Configuration.Verisense.SENSOR_ID.GSR))) {
			sb.append(generateCalcSamplingRateConfigStr(sensorClassKey, getSamplingRateForSensor(sensorClassKey), calculatedSamplingRate));

			sb.append(SENSOR_CONFIG_STRINGS.RESOLUTION);
			sb.append("12-bit");
			sb.append("}");
		}
		
		
		return sb.toString();
	}
	
	private StringBuilder generateCalcSamplingRateConfigStr(SENSORS sensorClassKey, double configuredSamplingRate, double calculatedSamplingRate) {
		StringBuilder sb = new StringBuilder();
		sb.append(sensorClassKey.toString());
		sb.append(" {Sampling Rate [");
		sb.append(SENSOR_CONFIG_STRINGS.SAMPLING_RATE_CONFIGURED);
		sb.append(configuredSamplingRate);
		sb.append(" ");
		sb.append(CHANNEL_UNITS.FREQUENCY);

		sb.append(", ");
		sb.append(SENSOR_CONFIG_STRINGS.SAMPLING_RATE_CALCULATED);
		if(!Double.isNaN(calculatedSamplingRate)) {
			sb.append(UtilVerisenseDriver.formatDoubleToNdecimalPlaces(calculatedSamplingRate, 3));
			sb.append(" ");
			sb.append(CHANNEL_UNITS.FREQUENCY);
		} else {
			sb.append(UtilVerisenseDriver.UNAVAILABLE);
		}
		sb.append("]; ");
		return sb;
	}

	public void setResetReason(byte resetReason) {
		this.resetReason = resetReason;
	}

	public byte getResetReason() {
		return resetReason;
	}

	public void setResetCounter(Integer resetCounter) {
		this.resetCounter = resetCounter;
	}

	public Integer getResetCounter() {
		return this.resetCounter;
	}
	
	public void setFirstPayloadResetAfterBoot(Integer firstPayloadIndexAfterBoot) {
		this.firstPayloadIndexAfterBoot = firstPayloadIndexAfterBoot;
	}
	
	public Integer getFirstPayloadIndexAfterBoot() {
		return firstPayloadIndexAfterBoot;
	}
	
	public double calibrateTemperature(long temperatureUncal) {
		double temperatureCal = 0.0;
		
		if(isPayloadDesignV9orAbove()) {
			temperatureCal = calibrateNrf52Temperatue(temperatureUncal);
		} else {
			if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LIS2DW12_ACCEL)) {
				temperatureCal = SensorLIS2DW12.calibrateTemperature(temperatureUncal);
			} else if(isEitherLsm6ds3ChannelEnabled()) {
				temperatureCal = SensorLSM6DS3.calibrateTemperature(temperatureUncal);
			} else if(isHwPpgAndAnyMaxChEnabled()) {
				temperatureCal = calibrateNrf52Temperatue(temperatureUncal);
			}
		}
		
		return temperatureCal;
	}

	public boolean isSpiChannelEnabled() {
		if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LIS2DW12_ACCEL) || isEitherLsm6ds3ChannelEnabled()) {
			return true;
		}
		return false;
	}
	
	public boolean isHwPpgAndAnyMaxChEnabled() {
		return (doesHwSupportMax86xxx() 
				&& (isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_RED)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_IR)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86150_ECG)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86916_PPG_GREEN)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86916_PPG_BLUE)));
	}
	
	public boolean isAnAdcChEnabled() {
		return (isSensorEnabled(Configuration.Verisense.SENSOR_ID.VBATT)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.GSR));
	}

	//TODO set derived algorithm enabled bit like as done for PPG rather then checking
	public boolean isAnAccelEnabled() {
		if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LIS2DW12_ACCEL)
				|| isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DS3_ACCEL)) {
			return true;
		}
		return false;
	}

	public static boolean isSensorKeyAnAccel(SENSORS sensorClassKey) {
		if(sensorClassKey==SENSORS.LIS2DW12 || sensorClassKey==SENSORS.LSM6DS3) {
			return true;
		}
		return false;
	}

	public boolean isEitherLsm6ds3ChannelEnabled() {
		return isSensorEnabled(Verisense.SENSOR_ID.LSM6DS3_ACCEL) || isSensorEnabled(Verisense.SENSOR_ID.LSM6DS3_GYRO);
	}

	public boolean isAnyLsm6dsvChannelEnabled() {
		return isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_ACCEL)
				|| isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_GYRO)
				|| isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_MAG);
	}
	
	public boolean doesHwSupportMax86xxx() {
		return doesHwSupportMax86xxx(getHardwareVersion());
	}

	public static boolean doesHwSupportMax86xxx(int hwId) {
		return (hwId==HW_ID.VERISENSE_DEV_BRD || hwId==HW_ID.VERISENSE_PPG || hwId==HW_ID.VERISENSE_PULSE_PLUS);
	}

	public boolean doesHwSupportUsb() {
		int hwId = getHardwareVersion();
		return (hwId!=HW_ID.VERISENSE_GSR_PLUS);
	}

	public boolean doesHwSupportLsm6ds3() {
		int hwId = getHardwareVersion();
		return (hwId==HW_ID.VERISENSE_DEV_BRD 
				|| hwId==HW_ID.VERISENSE_IMU 
				|| hwId==HW_ID.VERISENSE_GSR_PLUS 
				|| hwId==HW_ID.VERISENSE_PPG);
	}

	public boolean doesHwSupportShortTermFlash() {
		int hwId = getHardwareVersion();
		return (hwId==HW_ID.VERISENSE_DEV_BRD || hwId==HW_ID.VERISENSE_IMU || hwId==HW_ID.VERISENSE_GSR_PLUS);
	}

	public boolean doesHwSupportMax30002() {
		int hwId = getHardwareVersion();
		int hwRev = getExpansionBoardRev();
		return (hwId==HW_ID.VERISENSE_DEV_BRD || (hwId==HW_ID.VERISENSE_PULSE_PLUS && hwRev >=1 && hwRev <=4));
	}

	public boolean doesHwSupportGsr() {
		int hwId = getHardwareVersion();
		int hwRev = getExpansionBoardRev();
		return (hwId==HW_ID.VERISENSE_GSR_PLUS || (hwId==HW_ID.VERISENSE_PULSE_PLUS && hwRev >=5));
	}

	public CalibDetailsKinematic getCurrentCalibDetails(int sensorId) {
		if (isSensorEnabled(sensorId)) {
			CalibDetailsKinematic calibDetailsGyro = (CalibDetailsKinematic) getConfigValueUsingConfigLabel(sensorId, AbstractSensor.GuiLabelConfigCommon.CALIBRATION_CURRENT_PER_SENSOR);
			if(!calibDetailsGyro.isOffsetVectorUsingDefault() || !calibDetailsGyro.isSensitivityUsingDefault()) {
				return calibDetailsGyro;
			}
		}
		return null;
	}

	@Override
	public VerisenseDevice deepClone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (VerisenseDevice) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void sensorAndConfigMapsCreate() {
		mMapOfSensorClasses = new LinkedHashMap<SENSORS, AbstractSensor>();

		// Time is set in the OJC using this sensor class. The algorithms need it set in the OJC before they can be processed.
		addSensorClass(SENSORS.CLOCK, new SensorVerisenseClock(this));
		addSensorClass(SENSORS.LIS2DW12, new SensorLIS2DW12(this));
		
		if(compareFwVersions(getShimmerVerObject(), VerisenseDevice.FW_CHANGES.CCF21_010_3)) {
			addSensorClass(SENSORS.Battery, new SensorBattVoltageVerisense(this));
		}

		if(isPayloadDesignV13orAbove()) {
			// Second-generation IMU (LSM6DSV accel/gyro + LIS2MDL mag via sensor hub)
			addSensorClass(SENSORS.LSM6DSV, new SensorLSM6DSV(this));
		} else if(doesHwSupportLsm6ds3()) {
			addSensorClass(SENSORS.LSM6DS3, new SensorLSM6DS3(this));
		}
		
		if(doesHwSupportMax86xxx()) {
			int hwId = getHardwareVersion();
			int expansionBoardRev = getExpansionBoardRev();
			if((hwId==HW_ID.VERISENSE_DEV_BRD || hwId==HW_ID.VERISENSE_PPG)
					&& (expansionBoardRev == -1 || expansionBoardRev == 0)) {
				addSensorClass(SENSORS.MAX86150, new SensorMAX86150(this));
			} else {
				addSensorClass(SENSORS.MAX86916, new SensorMAX86916(this));
			}
		}

		if(doesHwSupportMax30002()) {
			// TODO add BioZ support
		}

		if(doesHwSupportGsr()) {
			addSensorClass(SENSORS.GSR, new SensorGSRVerisense(getShimmerVerObject()));
		}
		
		super.sensorAndConfigMapsCreateCommon();
		generateParserMap();
	}
	
	@Override
	public void handleSpecCasesAfterSensorMapUpdateFromEnabledSensors() {
		// The clock sensor/channels are inherently enabled and not controlled by enabled sensor bits so we need to force it on here
		SensorDetails sensorDetailTimestamp = getSensorDetails(Configuration.Verisense.SENSOR_ID.VERISENSE_TIMESTAMP);
		if(sensorDetailTimestamp!=null){
			sensorDetailTimestamp.setIsEnabled(true);
		}
	}

	@Override
	protected void interpretDataPacketFormat(Object object, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void createConfigBytesLayout() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		// TODO Auto-generated method stub
		
	}

	public void resetCalibParamAndAlgorithmBuffers() {
		// Config has changed so, incase the range has changed, setting
		// defaultMatrixMultipliedInverseAMSM to null will force the code to check
		// whether it's using the correct calibration parameters.
		resetAllCalibParametersToDefault();
		
		resetAlgorithmBuffers();
	}
	
	public void resetAlgorithmBufferGyroOnTheFly() {
		List<AbstractAlgorithm> listOfEnabledAlgorithmModules = getListOfEnabledAlgorithmModules();
		Iterator<AbstractAlgorithm> iterator = listOfEnabledAlgorithmModules.iterator();
		while(iterator.hasNext()) {
			AbstractAlgorithm abstractAlgorithm = iterator.next();
			if(abstractAlgorithm instanceof GyroOnTheFlyCalModuleVerisense) {
				try {
					abstractAlgorithm.resetAlgorithmBuffers();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public void resetAlgorithmBuffersAssociatedWithSensor(SENSORS sensorClassKey) {
		if(sensorClassKey==SENSORS.LSM6DS3) {
			resetAlgorithmBufferGyroOnTheFly();
		} 
	}

	public static double calibrateNrf52Temperatue(long temperatureUncal) {
		return temperatureUncal * 0.25;
	}

	/**
	 * Created based on ShimmerDevice.buildMsg(). Whereas that function expects all
	 * sensor bytes to be in the same packet and have the same sampling rate, this
	 * method parses each sensor separately.
	 * 
	 * @param newPacket
	 * @param listOfSensorClassKeys
	 * @param timeMsCurrentSample
	 * @return
	 */
	public ObjectCluster buildMsgForSensorList(byte[] newPacket, COMMUNICATION_TYPE commType, List<SENSORS> listOfSensorClassKeys, double timeMsCurrentSample) {
		// Arguments normally passed into ShimmerDevice.buildMsg()
		boolean isTimeSyncEnabled = false; 
		
		ObjectCluster ojc = new ObjectCluster(mShimmerUserAssignedName, getMacId());
		ojc.mRawData = newPacket;
		ojc.createArrayData(getNumberOfEnabledChannels(commType));

		incrementPacketsReceivedCounters();

		int index=0;
		for(SENSORS sensorClassKey : listOfSensorClassKeys) {
			TreeMap<Integer, SensorDetails> sensorMap = getSensorClass(sensorClassKey).mSensorMap;
			if(sensorMap!=null){
				for (SensorDetails sensor:sensorMap.values()){
					if(sensor.isEnabled()) {
						int length = sensor.getExpectedPacketByteArray(commType);
						byte[] sensorByteArray = new byte[length];
						//TODO process API sensors, not just bytes from Shimmer packet 
						if (length!=0){ //if length 0 means there are no channels to be processed
							if((index+sensorByteArray.length)<=newPacket.length){
								System.arraycopy(newPacket, index, sensorByteArray, 0, sensorByteArray.length);
							}
							else{
								consolePrintLn(mShimmerUserAssignedName + " ERROR PARSING " + sensor.mSensorDetailsRef.mGuiFriendlyLabel);
							}
						}
						sensor.processData(sensorByteArray, commType, ojc, isTimeSyncEnabled, timeMsCurrentSample);

//						if(debug)
//							System.out.println(sensor.mSensorDetailsRef.mGuiFriendlyLabel + "\texpectedPacketArraySize:" + length + "\tcurrentIndex:" + index);
						index += length;
					}
				}
				
				
			} else {
				consolePrintErrLn("ERROR!!!! Sensor Map null");
			}
		}

		//After sensor data has been processed, now process any filters or Algorithms 
		ojc = processData(ojc);

//		if(sensorClassKey==SENSORS.MAX86916) {
//			ojc.consolePrintChannelsAndDataSingleLine();
//		}
		
		return ojc;
	}

	@Override
	protected ObjectCluster processData(ObjectCluster ojc) {
		return super.processData(ojc);
	}
	
	// TODO move to ShimmerVerObject
	public boolean isFwMajorMinorInternalVerSet() {
		ShimmerVerObject svo = getShimmerVerObject();
		if (svo.mFirmwareVersionMajor != FW_ID.UNKNOWN 
				&& svo.mFirmwareVersionMinor != FW_ID.UNKNOWN
				&& svo.mFirmwareVersionInternal != FW_ID.UNKNOWN) {
			return true;
		}
		return false;
	}
	
	// TODO move to ShimmerDevice
	public int getExpectedDataPacketSize(SENSORS sensorClassKey){
		int dataPacketSize = 0;
		AbstractSensor abstractSensor = getSensorClass(sensorClassKey);
		for(SensorDetails sensorDetails:abstractSensor.mSensorMap.values()) {
			if(sensorDetails.isEnabled()) {
				dataPacketSize += sensorDetails.getExpectedDataPacketSize();
			}
		}
		return dataPacketSize;
	}
	
	public static double calibrateVerisenseAdcChannelToVolts(int hwId, double unCalData){
		double offset = 0; double vRefP = 0.6; double gain = 6.0; 
		if(hwId==HW_ID.VERISENSE_GSR_PLUS) {
			offset = 0; 
			vRefP = 0.75;
			gain = 4.0;
		}
		double calData = SensorADC.calibrateU12AdcValueToVolts(unCalData, offset, vRefP, gain);
		return calData;
	}

	public static double calibrateVerisenseAdcChannelToMillivolts(int hwId, double unCalData){
		return calibrateVerisenseAdcChannelToVolts(hwId, unCalData) * 1000.0;
	}

	public List<SENSORS> getSensorKeysForDatablockId(DATABLOCK_SENSOR_ID datablockSensorId) {
		List<SENSORS> listOfSensorIds = new ArrayList<SENSORS>();
		switch(datablockSensorId) {
			case ADC:
				if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.VBATT)) {
					listOfSensorIds.add(SENSORS.Battery);
				}
				if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.GSR)) {
					listOfSensorIds.add(SENSORS.GSR);
				}
				break;
			case ACCEL_1:
				listOfSensorIds.add(SENSORS.LIS2DW12);
				break;
			case GYRO_ACCEL2:
				listOfSensorIds.add(SENSORS.LSM6DS3);
				break;
			case LSM6DSV:
				listOfSensorIds.add(SENSORS.LSM6DSV);
				break;
			case PPG:
				SENSORS sensorClassKey = getPpgSensorClassKey();
				if(sensorClassKey!=null) {
					listOfSensorIds.add(sensorClassKey);
				}
				break;
			case BIOZ:
				listOfSensorIds.add(SENSORS.BIOZ);
				break;
			default:
				break;
		}
		return listOfSensorIds;
	}

	public int calculateFifoBlockSize(SENSORS sensorClassKey) {
		int dataBlockSize = Integer.MIN_VALUE;
		switch(sensorClassKey) {
			case LIS2DW12:
				dataBlockSize = SensorLIS2DW12.FIFO_SIZE_IN_CHIP;
				break;
			case LSM6DS3:
				AbstractSensor abstractSensor = getSensorClass(SENSORS.LSM6DS3);
				if(abstractSensor!=null) {
					SensorLSM6DS3 sensorLSM6DS3 = (SensorLSM6DS3)abstractSensor;
					dataBlockSize = sensorLSM6DS3.getFifoByteSizeInChip();
				}
				break;
			case MAX86150:
			case MAX86916:
				dataBlockSize = 0;
				// 3 bytes each channel with a max of 32 samples per FIFO each
				int fifoBytesPerChannel = 3*SensorMAX86XXX.MAX_SAMPLES_PER_FIFO;
				if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_RED)) {
					dataBlockSize += fifoBytesPerChannel;
				}
				if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86XXX_PPG_IR)) {
					dataBlockSize += fifoBytesPerChannel;
				}
				if (sensorClassKey == SENSORS.MAX86150) {
					if (isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86150_ECG)) {
						dataBlockSize += fifoBytesPerChannel;
					}
				}
				if (sensorClassKey == SENSORS.MAX86916) {
					if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86916_PPG_GREEN)) {
						dataBlockSize += fifoBytesPerChannel;
					}
					if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.MAX86916_PPG_BLUE)) {
						dataBlockSize += fifoBytesPerChannel;
					}
				}
				break;
			case Battery:
			case GSR:
				// There is a single buffer of a fixed size for all ADC channels (e.g., battery, GSR) no matter how many are enabled
				dataBlockSize = SensorBattVoltageVerisense.ADC_BYTE_BUFFER_SIZE;
				break;
			case BIOZ:
				// TODO add support in future if needed
				break;
			default:
				break;
		}
		return dataBlockSize;
	}

	public void setProtocol(COMMUNICATION_TYPE commType, VerisenseProtocolByteCommunication protocol) {
		addCommunicationRoute(commType);
		
		this.mapOfVerisenseProtocolByteCommunication.put(commType, protocol);
		
		protocol.addRadioListener(new RadioListener() {

			@Override
			public void startOperationCallback(BT_STATE currentOperation, int totalNumOfCmds) {
				startOperation(currentOperation, totalNumOfCmds);
			}
			
			@Override
			public void finishOperationCallback(BT_STATE currentOperation) {
				finishOperation(currentOperation);
			}

			@Override
			public void sendProgressReportCallback(BluetoothProgressReportPerCmd progressReportPerCmd) {
				sendProgressReport(progressReportPerCmd);
			}

			@Override
			public void isNowStreamingCallback() {
				// TODO Auto-generated method stub
				setBluetoothRadioState(BT_STATE.STREAMING);
			}

			@Override
			public void initialiseStreamingCallback() {
				// TODO Auto-generated method stub

			}

			@Override
			public void hasStopStreamingCallback() {
				// TODO Auto-generated method stub
				setBluetoothRadioState(BT_STATE.CONNECTED);
			}

			@Override
			public void eventSetIsStreaming(boolean isStreaming) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventSetIsSensing(boolean isSensing) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventSetIsSDLogging(boolean isSdLogging) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventSetIsInitialised(boolean isInitialised) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventSetIsDocked(boolean isDocked) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventSetHaveAttemptedToRead(boolean haveAttemptedToRead) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventResponseReceived(int responseCommand, Object parsedResponse) {
				System.out.println("VerisenseDevice Response: " + responseCommand + " " + parsedResponse.getClass().getSimpleName());
				
				System.out.println(((AbstractPayload)parsedResponse).generateDebugString());
				
				if(parsedResponse instanceof StatusPayload) {
					status = (StatusPayload) parsedResponse;
					
				} else if(parsedResponse instanceof ProductionConfigPayload) {
					prodConfigPayload = (ProductionConfigPayload) parsedResponse;
					setUniqueId(prodConfigPayload.verisenseId);
					setShimmerVersionObject(prodConfigPayload.shimmerVerObject);
					setHardwareVersion(prodConfigPayload.expansionBoardDetails.mExpansionBoardId);
					setExpansionBoardDetails(prodConfigPayload.expansionBoardDetails);
					sensorAndConfigMapsCreate();
					
				} else if(parsedResponse instanceof OperationalConfigPayload) {
					opConfig = (OperationalConfigPayload) parsedResponse;
					configBytesParseAndInitialiseAlgorithms(opConfig.getPayloadContents(), currentStreamingCommsRoute);
					printSensorParserAndAlgoMaps();
					
				} else if(parsedResponse instanceof TimePayload) {
					//TODO needed?
				}
				
			}

			@Override
			public void eventNewResponse(byte[] responseBytes) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventNewPacket(byte[] packetByteArray, long pcTimestamp) {
				System.out.println("VerisenseDevice New Packet: " + pcTimestamp);
				
				try {
					DataBlockDetails dataBlockDetails = parseDataBlockMetaData(packetByteArray, pcTimestamp);
					parseDataBlockData(dataBlockDetails, packetByteArray, BYTE_COUNT.PAYLOAD_CONTENTS_GEN8_SENSOR_ID + BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS, currentStreamingCommsRoute);
					
					System.out.println("Number of ObjectClusters generated: " + dataBlockDetails.getOjcArray().length);
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			@Override
			public void eventLogAndStreamStatusChangedCallback(int lastSentInstruction) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventError(ShimmerException dE) {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventDockedStateChange() {
				// TODO Auto-generated method stub

			}

			@Override
			public void eventAckReceived(int commandAndProperty) {
				// TODO Auto-generated method stub
				if(commandAndProperty == VERISENSE_EVENT_ACK_RECEIVED.VERISENSE_WRITE_OP_ACK)
				{
					mDeviceCallbackAdapter.writeOpConfigCompleted();
				}
				else if (commandAndProperty == VERISENSE_EVENT_ACK_RECEIVED.VERISENSE_ERASE_FLASH_AND_LOOKUP_ACK) {
					mDeviceCallbackAdapter.eraseDataCompleted();
				}
				
			}

			@Override
			public void disconnected() {
				// TODO Auto-generated method stub
				setBluetoothRadioState(BT_STATE.DISCONNECTED);

			}

			@Override
			public void connected() {
				// TODO Auto-generated method stub

			}

			@Override
			public void isNowStreamLoggedDataCallback() {
				// TODO Auto-generated method stub
				setBluetoothRadioState(BT_STATE.STREAMING_LOGGED_DATA);
			}

			@Override
			public void hasStopStreamLoggedDataCallback(String binFilePath) {
				mDeviceCallbackAdapter.readLoggedDataCompleted(binFilePath);
				// TODO Auto-generated method stub
				setBluetoothRadioState(BT_STATE.CONNECTED);
			}

			@Override
			public void eventNewSyncPayloadReceived(int payloadIndex, boolean crcError, double transferRateBytes, String binFilePath) {
				// TODO Auto-generated method stub
				mDeviceCallbackAdapter.newSyncPayloadReceived(payloadIndex, crcError, transferRateBytes, binFilePath);
			}
		});
	}
	
	@Override
	public void removeCommunicationRoute(COMMUNICATION_TYPE communicationType) {
		super.removeCommunicationRoute(communicationType);
		mapOfVerisenseProtocolByteCommunication.remove(communicationType);
	}

	public long endTimeMinutes = 0;
	public long endTimeTicksLatest = -1;
	
	/** Used during real-time streaming
	 * @param byteBuffer
	 * @return
	 * @throws IOException
	 */
	public DataBlockDetails parseDataBlockMetaData(byte[] byteBuffer, long pcTimestampMs) throws IOException {
		DataBlockDetails dataBlockDetails = parseDataBlockMetaData(byteBuffer, 0, 0, 0, 0);
		
		// Streaming data block only contains microcontroller ticks value so we need to track the minutes in SW
		long endTimeTicksCurrent = dataBlockDetails.getTimeDetailsUcClock().getEndTimeTicks();
		if(endTimeTicksLatest!=-1) {
			if (endTimeTicksCurrent<endTimeTicksLatest) {
				endTimeMinutes++;
			}
		}
		endTimeTicksLatest = endTimeTicksCurrent;
		dataBlockDetails.setUcClockOrRwcClockEndTimeMinutesAndCalculateTimings(endTimeMinutes, true);
		
		//TODO temp here, setting the RWC in the datablock to be the same as the UC clock
		VerisenseTimeDetails timeDetailsRwc = dataBlockDetails.getTimeDetailsRwc();
		VerisenseTimeDetails timeDetailsUc = dataBlockDetails.getTimeDetailsUcClock();
		timeDetailsRwc.setStartTimeMs(timeDetailsUc.getStartTimeMs());
		timeDetailsRwc.setEndTimeMs(timeDetailsUc.getEndTimeMs());
		
		return dataBlockDetails;
	}

	public DataBlockDetails parseDataBlockMetaData(byte[] byteBuffer, int dataBlockStartByteIndexInPayload, int dataBlockStartByteIndexInFile, int dataBlockIndexInPayload, int payloadIndex) throws IOException {
		int sensorId = byteBuffer[dataBlockStartByteIndexInPayload];
		
		int ticksByteIndex = dataBlockStartByteIndexInPayload + BYTE_COUNT.PAYLOAD_CONTENTS_GEN8_SENSOR_ID;
		long rwcEndTimeTicks = VerisenseTimeDetails.parseTimeTicksAtIndex(byteBuffer, ticksByteIndex);
		
		DataBlockDetails dataBlockDetails = null;
		DATABLOCK_SENSOR_ID[] payloadSensorIdValues = DATABLOCK_SENSOR_ID.values();
		// -1 because PPG_MAX86150 has been temporarily added to PAYLOAD_SENSOR_ID 
		if(sensorId>0 && sensorId<payloadSensorIdValues.length-1) {
			DATABLOCK_SENSOR_ID datablockSensorId = payloadSensorIdValues[sensorId];
			
			List<SENSORS> listOfSensorClassKeys = getOrCreateListOfSensorClassKeysForDataBlockId(datablockSensorId);
			
			dataBlockDetails = new DataBlockDetails(datablockSensorId, payloadIndex, dataBlockIndexInPayload, listOfSensorClassKeys, 
					dataBlockStartByteIndexInFile, dataBlockStartByteIndexInPayload, 
					rwcEndTimeTicks, isPayloadDesignV11orAbove());

			SENSORS firstSensorClasskey = dataBlockDetails.listOfSensorClassKeys.get(0);
			int qtySensorDataBytesInDatablock;
			int lsm6dsvNumEntries = 0;
			if(datablockSensorId == DATABLOCK_SENSOR_ID.LSM6DSV) {
				// Variable-length tagged FIFO: [2-byte NUM_SAMPLES][N x 7-byte entries].
				int numSamplesIndex = dataBlockStartByteIndexInPayload + BYTE_COUNT.PAYLOAD_CONTENTS_GEN8_SENSOR_ID + BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS;
				lsm6dsvNumEntries = (byteBuffer[numSamplesIndex] & 0xFF) | ((byteBuffer[numSamplesIndex+1] & 0xFF) << 8);
				qtySensorDataBytesInDatablock = 2 + lsm6dsvNumEntries * 7;
			} else {
				qtySensorDataBytesInDatablock = calculateFifoBlockSize(firstSensorClasskey);
			}
			int dataPacketSize = 0;
			for(SENSORS sensorClassKey:dataBlockDetails.getListOfSensorClassKeys()) {
				dataPacketSize += getExpectedDataPacketSize(sensorClassKey);
			}
			// All sensors/channels within a single datablock share the same sampling rate so it's ok to pick the first one here
			double samplingRate = getSamplingRateForSensor(firstSensorClasskey);
			
			dataBlockDetails.setMetadata(qtySensorDataBytesInDatablock, dataPacketSize, samplingRate);
			if(datablockSensorId == DATABLOCK_SENSOR_ID.LSM6DSV) {
				// sampleCount drives per-sample timing + ojc array sizing; use the aligned
				// accel (or gyro) stream count, not the raw tagged-entry count.
				int numSamplesIndex = dataBlockStartByteIndexInPayload + BYTE_COUNT.PAYLOAD_CONTENTS_GEN8_SENSOR_ID + BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS;
				dataBlockDetails.setSampleCount(countLsm6dsvAlignedSamples(byteBuffer, numSamplesIndex + 2, lsm6dsvNumEntries));
			}
		} else {
			IOException e = new IOException("Error parsing Data Block. SensorID=" + UtilShimmer.intToHexStringFormatted(sensorId, 1, false) + " not supported. "
					+ "File byte index " + UtilShimmer.intToHexStringFormatted(dataBlockStartByteIndexInFile, 4, true)
					+ ", Payload index " + payloadIndex
					+ ", DataBlock index in payload " + dataBlockIndexInPayload
					+ ". See console report.");
			throw(e);
		}
		
		return dataBlockDetails;
	}
	
	public HashMap<DATABLOCK_SENSOR_ID, List<SENSORS>> getMapOfSensorIdsPerDataBlock() {
		return mapOfSensorIdsPerDataBlock;
	}
	
	public void clearMapOfSensorClassKeysForDataBlockId() {
		mapOfSensorIdsPerDataBlock.clear();
	}
	
	public List<SENSORS> getOrCreateListOfSensorClassKeysForDataBlockId(DATABLOCK_SENSOR_ID datablockSensorId) {
		List<SENSORS> listOfSensorClassKeys = mapOfSensorIdsPerDataBlock.get(datablockSensorId);
		if(listOfSensorClassKeys==null) {
			listOfSensorClassKeys = getSensorKeysForDatablockId(datablockSensorId);
			
			// Additional channels that are calculated by the API go at the end of the list
			// or else the flow in parseDataBlockMetaData will break (i.e., it relies on
			// having the sensor(s) be the first entry in the list)
			if(isPayloadDesignV8orAbove()) {
				listOfSensorClassKeys.add(SENSORS.CLOCK);
			}
			
			mapOfSensorIdsPerDataBlock.put(datablockSensorId, listOfSensorClassKeys);
		}
		return listOfSensorClassKeys;
	}

	public void parseDataBlockData(DataBlockDetails dataBlockDetails, byte[] byteBuffer, int currentByteIndex, COMMUNICATION_TYPE commType) {
		if(dataBlockDetails.datablockSensorId == DATABLOCK_SENSOR_ID.LSM6DSV) {
			parseDataBlockDataLsm6dsv(dataBlockDetails, byteBuffer, currentByteIndex, commType);
			return;
		}

		double timeMsCurrentSample = dataBlockDetails.getStartTimeRwcMs();

		for(int y=0;y<dataBlockDetails.getSampleCount();y++) {
			byte[] byteBuf = new byte[dataBlockDetails.dataPacketSize];
			System.arraycopy(byteBuffer, currentByteIndex, byteBuf, 0, byteBuf.length);

			ObjectCluster ojcCurrent = buildMsgForSensorList(byteBuf, commType, dataBlockDetails.listOfSensorClassKeys, timeMsCurrentSample);
			dataHandler(ojcCurrent);
			dataBlockDetails.setOjcArrayAtIndex(y, ojcCurrent);

			timeMsCurrentSample += (dataBlockDetails.getTimestampDiffInS() * 1000);

			currentByteIndex += dataBlockDetails.dataPacketSize;
		}
	}

	/** LSM6DSV tag values within the FIFO TAG_CNT byte (bits 7:3). */
	private static final int LSM6DSV_TAG_GYRO = 0x01;
	private static final int LSM6DSV_TAG_ACCEL = 0x02;
	private static final int LSM6DSV_TAG_MAG = 0x0E;

	/**
	 * Count the reference-stream samples in an LSM6DSV tagged-FIFO block - used to
	 * drive per-sample timing. Accel and gyro share the same ODR and are interleaved
	 * 1:1 so either can act as the reference; if neither is enabled (a mag-only
	 * enable configuration - not currently producible by the firmware as the sensor
	 * hub needs accel/gyro running, but expressible in the header) the mag stream is
	 * counted instead. Timestamp (tag 0x04) and other entries are ignored.
	 */
	private int countLsm6dsvAlignedSamples(byte[] byteBuffer, int entriesStart, int numEntries) {
		int refTag;
		if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL)) {
			refTag = LSM6DSV_TAG_ACCEL;
		} else if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO)) {
			refTag = LSM6DSV_TAG_GYRO;
		} else if(isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG)) {
			refTag = LSM6DSV_TAG_MAG;
		} else {
			return 0;
		}
		int count = 0;
		for(int k=0;k<numEntries;k++) {
			int tag = ((byteBuffer[entriesStart + k*7] & 0xFF) >> 3) & 0x1F;
			if(tag==refTag) { count++; }
		}
		return count;
	}

	/**
	 * Dedicated parser for the LSM6DSV variable-length tagged FIFO. The block is
	 * {@code [2-byte NUM_SAMPLES][N x 7-byte entries]} where each entry is
	 * {@code [TAG_CNT][X][Y][Z]} (3 x int16 LE) and the 5-bit tag (TAG_CNT bits 7:3)
	 * distinguishes accel (0x02), gyro (0x01) and mag (0x0E) streams; timestamp
	 * entries (0x04) are skipped.
	 * <p>
	 * Accel and gyro share the LSM6DSV ODR and are interleaved 1:1, so they are
	 * emitted as aligned samples through {@link #buildMsgForSensorList}. (Mag is read
	 * via the sensor hub at a different rate; full per-stream mag timestamping is TODO
	 * - mag bytes are currently left zero so accel/gyro stay byte-aligned.)
	 */
	private void parseDataBlockDataLsm6dsv(DataBlockDetails dataBlockDetails, byte[] byteBuffer, int currentByteIndex, COMMUNICATION_TYPE commType) {
		int numEntries = (byteBuffer[currentByteIndex] & 0xFF) | ((byteBuffer[currentByteIndex+1] & 0xFF) << 8);
		int entriesStart = currentByteIndex + 2;

		List<byte[]> accelSamples = new ArrayList<byte[]>();
		List<byte[]> gyroSamples = new ArrayList<byte[]>();
		List<byte[]> magSamples = new ArrayList<byte[]>();
		for(int k=0;k<numEntries;k++) {
			int eo = entriesStart + k*7;
			int tag = ((byteBuffer[eo] & 0xFF) >> 3) & 0x1F;
			if(tag==LSM6DSV_TAG_ACCEL || tag==LSM6DSV_TAG_GYRO || tag==LSM6DSV_TAG_MAG) {
				byte[] xyz = new byte[6];
				System.arraycopy(byteBuffer, eo+1, xyz, 0, 6);
				if(tag==LSM6DSV_TAG_ACCEL) { accelSamples.add(xyz); }
				else if(tag==LSM6DSV_TAG_GYRO) { gyroSamples.add(xyz); }
				else { magSamples.add(xyz); }
			}
			// tag 0x04 (timestamp) intentionally skipped
		}

		boolean accelEn = isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL);
		boolean gyroEn = isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO);
		boolean magEn = isSensorEnabled(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG);

		int alignedCount = accelEn ? accelSamples.size() : (gyroEn ? gyroSamples.size() : 0);
		int magCount = magEn ? magSamples.size() : 0;
		dataBlockDetails.setupOjcArray(alignedCount + magCount);

		// SensorDetails for per-stream enable toggling. Accel + gyro are synchronous (same
		// ODR, 1:1 interleaved) so they share one OJC per timestamp. Mag is read via the
		// LSM6DSV sensor hub at its own (lower) cadence, so it is emitted as its own OJC
		// stream - the CSV writer keeps streams separate by per-OJC channel filtering, but
		// we must NOT let the accel/gyro OJCs carry mag (toggle mag off for pass 1) nor the
		// mag OJCs carry accel/gyro (toggle them off for pass 2).
		AbstractSensor sensorClass = getSensorClass(SENSORS.LSM6DSV);
		SensorDetails accelDet = (sensorClass!=null)? sensorClass.mSensorMap.get(Configuration.Verisense.SENSOR_ID.LSM6DSV_ACCEL) : null;
		SensorDetails gyroDet = (sensorClass!=null)? sensorClass.mSensorMap.get(Configuration.Verisense.SENSOR_ID.LSM6DSV_GYRO) : null;
		SensorDetails magDet = (sensorClass!=null)? sensorClass.mSensorMap.get(Configuration.Verisense.SENSOR_ID.LSM6DSV_MAG) : null;
		boolean accelOrig = accelDet!=null && accelDet.isEnabled();
		boolean gyroOrig = gyroDet!=null && gyroDet.isEnabled();
		boolean magOrig = magDet!=null && magDet.isEnabled();

		int ojcIndex = 0;
		double startTimeMs = dataBlockDetails.getStartTimeRwcMs();
		double alignedDiffMs = dataBlockDetails.getTimestampDiffInS() * 1000;

		// Guarantee the per-stream enabled states are restored even if parsing throws
		// mid-pass - otherwise the device would be left with corrupted enable state for
		// the remainder of the file.
		try {
			// Pass 1: aligned accel/gyro (mag disabled so these OJCs carry no mag channels)
			if(alignedCount>0) {
				if(magDet!=null) { magDet.setIsEnabled(false); }
				int alignedPacketSize = getExpectedDataPacketSize(SENSORS.LSM6DSV);
				double timeMsCurrentSample = startTimeMs;
				for(int i=0;i<alignedCount;i++) {
					byte[] byteBuf = new byte[alignedPacketSize];
					int offset = 0;
					if(accelEn && i<accelSamples.size()) { System.arraycopy(accelSamples.get(i), 0, byteBuf, offset, 6); }
					if(accelEn) { offset += 6; }
					if(gyroEn && i<gyroSamples.size()) { System.arraycopy(gyroSamples.get(i), 0, byteBuf, offset, 6); }

					ObjectCluster ojcCurrent = buildMsgForSensorList(byteBuf, commType, dataBlockDetails.listOfSensorClassKeys, timeMsCurrentSample);
					dataHandler(ojcCurrent);
					dataBlockDetails.setOjcArrayAtIndex(ojcIndex++, ojcCurrent);
					timeMsCurrentSample += alignedDiffMs;
				}
			}

			// Pass 2: mag stream (only mag enabled), spread evenly across the block duration
			if(magCount>0) {
				if(accelDet!=null) { accelDet.setIsEnabled(false); }
				if(gyroDet!=null) { gyroDet.setIsEnabled(false); }
				if(magDet!=null) { magDet.setIsEnabled(true); }
				int magPacketSize = getExpectedDataPacketSize(SENSORS.LSM6DSV);
				double blockDurationMs = alignedDiffMs * alignedCount;
				if(!(blockDurationMs>0)) { // covers 0 and NaN (no aligned stream -> 0 * Infinity)
					// Mag-only enable configuration (no aligned accel/gyro stream to derive the
					// block duration from): fall back to the configured mag output rate. Not
					// currently producible by the firmware (the sensor hub requires accel/gyro
					// to be running) but handled defensively.
					SensorLSM6DSV sensorLsm6dsv = getSensorLSM6DSV();
					double magRateHz = (sensorLsm6dsv!=null)? sensorLsm6dsv.getMagRateFreq() : 0;
					blockDurationMs = (magRateHz>0)? (magCount * (1000.0/magRateHz)) : 0;
				}
				double magDiffMs = (blockDurationMs>0)? blockDurationMs/magCount : 0;
				double magTimeMs = startTimeMs;
				for(int i=0;i<magCount;i++) {
					byte[] byteBuf = new byte[magPacketSize];
					System.arraycopy(magSamples.get(i), 0, byteBuf, 0, 6);
					ObjectCluster ojcCurrent = buildMsgForSensorList(byteBuf, commType, dataBlockDetails.listOfSensorClassKeys, magTimeMs);
					dataHandler(ojcCurrent);
					dataBlockDetails.setOjcArrayAtIndex(ojcIndex++, ojcCurrent);
					magTimeMs += magDiffMs;
				}
			}
		} finally {
			// restore original per-stream enabled states
			if(accelDet!=null) { accelDet.setIsEnabled(accelOrig); }
			if(gyroDet!=null) { gyroDet.setIsEnabled(gyroOrig); }
			if(magDet!=null) { magDet.setIsEnabled(magOrig); }
		}
	}

	@Override
	protected void dataHandler(ObjectCluster ojc) {
		mDeviceCallbackAdapter.dataHandler(ojc);
	}
	
	@Override
	protected double correctSamplingRate(double rateHz) {
		// As Verisense uses different sampling clocks for different sensors, it is not
		// applicable to correct the sampling rate as we would have done for Shimmer3.
		// The only sensors that this logic could be applied to are the ADC based sensor
		// channels such as VBatt and GSR as these are both based on the 32768Hz crystal
		// in the Verisense.
		return rateHz;
	}

	/**
	 * @see VerisenseDevice#connect(COMMUNICATION_TYPE)
	 */
	@Override
	public void connect() throws ShimmerException {
		try {
			this.connect(currentStreamingCommsRoute);
		} catch (Exception ex) {
			if (currentStreamingCommsRoute.equals(COMMUNICATION_TYPE.BLUETOOTH)) {
				setBluetoothRadioState(BT_STATE.DISCONNECTED);
			}
			throw ex;
		}
	}
	
	/**
	 * @see VerisenseDevice#disconnect(COMMUNICATION_TYPE)
	 */
	@Override
	public void disconnect() throws ShimmerException {
		this.disconnect(currentStreamingCommsRoute);
		
	}
	
	/** 
	 * Disconnect from the verisense device
	 * @throws ShimmerException  if verisenseProtocolByteCommunication is not set
	 */
	public void disconnect(COMMUNICATION_TYPE commType) throws ShimmerException {
		VerisenseProtocolByteCommunication verisenseProtocolByteCommunication = mapOfVerisenseProtocolByteCommunication.get(commType);
		if(verisenseProtocolByteCommunication!=null) {
			verisenseProtocolByteCommunication.disconnect();
			if (commType.equals(COMMUNICATION_TYPE.BLUETOOTH)){
				setBluetoothRadioState(BT_STATE.DISCONNECTED);
			}
		} else {
			throw new ShimmerException("VerisenseProtocolByteCommunication not set");
		}
	}
	
	/** 
	 * To initialize a BLE connection with the verisense device and
	 * read the status, production configuration, operation configuration
	 * @throws ShimmerException  if verisenseProtocolByteCommunication is not set
	 */
	public void connect(COMMUNICATION_TYPE commType) throws ShimmerException {
		VerisenseProtocolByteCommunication verisenseProtocolByteCommunication = mapOfVerisenseProtocolByteCommunication.get(commType);
		if(verisenseProtocolByteCommunication!=null) {
			try {
				if (commType.equals(COMMUNICATION_TYPE.BLUETOOTH)){
					setBluetoothRadioState(BT_STATE.CONNECTING);
				}
				verisenseProtocolByteCommunication.connect();
				verisenseProtocolByteCommunication.readStatus();
				verisenseProtocolByteCommunication.readProductionConfig();
				verisenseProtocolByteCommunication.readOperationalConfig();
				if (commType.equals(COMMUNICATION_TYPE.BLUETOOTH)){
					setBluetoothRadioState(BT_STATE.CONNECTED);
					mDeviceCallbackAdapter.isReadyForStreaming();
				}
			} catch (ShimmerException e) {
				e.printStackTrace();
				try {
					disconnect(commType);
				} catch (ShimmerException e2) {
					e.printStackTrace();
				}
				throw(e);
			}
		} else {
			throw new ShimmerException("VerisenseProtocolByteCommunication not set");
		}
	}
	
	/** 
	 * Start streaming
	 * @throws ShimmerException  if the device is already streaming
	 */
	@Override
	public void startStreaming() throws ShimmerException {
		super.startStreaming();
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).startStreaming();
		
		//TODO reset this as part of the sensor map
		AbstractSensor abstractSensor = getSensorClass(SENSORS.CLOCK);
		if(abstractSensor!=null && abstractSensor instanceof SensorVerisenseClock) {
			SensorVerisenseClock sensorVerisenseClock = (SensorVerisenseClock)abstractSensor;
			sensorVerisenseClock.getSystemTimestampPlot().reset();
		}
	}

	/** 
	 * Stop streaming
	 * @throws ShimmerException  if the device is not already streaming
	 */
	@Override
	public void stopStreaming() throws ShimmerException {
		super.stopStreaming();
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).stopStreaming();
	}

	/** 
	 * Start data sync
	 */
	public void readLoggedData() throws ShimmerException {
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).readLoggedData();
	}
	
	public void setRootPathForBinFile(String path) {
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).setRootPathForBinFile(path);
	}
	
	public void deleteData() throws Exception {
		
			mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).eraseDataTask().waitForCompletion();
		
	}
	
	/**
	 * @return Null if sensor not supported by current hardware
	 * @see SensorLIS2DW12
	 */
	public SensorLIS2DW12 getSensorLIS2DW12() {
		AbstractSensor abstractSensor = getSensorClass(SENSORS.LIS2DW12);
		if(abstractSensor!=null && abstractSensor instanceof SensorLIS2DW12) {
			return (SensorLIS2DW12) abstractSensor;
		}
		return null;
	}
	
	/**
	 * @return Null if sensor not supported by current hardware
	 * @see SensorLSM6DS3
	 */
	public SensorLSM6DS3 getSensorLSM6DS3() {
		AbstractSensor abstractSensor = getSensorClass(SENSORS.LSM6DS3);
		if(abstractSensor!=null && abstractSensor instanceof SensorLSM6DS3) {
			return (SensorLSM6DS3) abstractSensor;
		}
		return null;
	}

	public SensorLSM6DSV getSensorLSM6DSV() {
		AbstractSensor abstractSensor = getSensorClass(SENSORS.LSM6DSV);
		if(abstractSensor!=null && abstractSensor instanceof SensorLSM6DSV) {
			return (SensorLSM6DSV) abstractSensor;
		}
		return null;
	}

	/**
	 * @return Null if sensor not supported by current hardware
	 * @see SensorGSRVerisense
	 */
	public SensorGSRVerisense getSensorGsr() {
		AbstractSensor abstractSensor = getSensorClass(SENSORS.GSR);
		if(abstractSensor!=null && abstractSensor instanceof SensorGSRVerisense) {
			return (SensorGSRVerisense) abstractSensor;
		}
		return null;
	}

	/**
	 * @return Null if sensor not supported by current hardware
	 * @see SensorMAX86916
	 */
	public SensorMAX86916 getSensorMax86916() {
		AbstractSensor abstractSensor = getSensorClass(SENSORS.MAX86916);
		if(abstractSensor!=null && abstractSensor instanceof SensorMAX86916) {
			return (SensorMAX86916) abstractSensor;
		}
		return null;
	}

	/**
	 * @return Null if sensor not supported by current hardware
	 * @see SensorBattVoltageVerisense
	 */
	public SensorBattVoltageVerisense getSensorBatteryVoltage() {
		AbstractSensor abstractSensor = getSensorClass(SENSORS.Battery);
		if(abstractSensor!=null && abstractSensor instanceof SensorBattVoltageVerisense) {
			return (SensorBattVoltageVerisense) abstractSensor;
		}
		return null;
	}

	/** 
	 * @param array of sensor Id to be enabled
	 * @return enabled sensor Id
	 * @see setSensorEnabledState
	 */
	public Integer[] setSensorsEnabled(int[] sensorIds) {
		disableAllSensors();
		List<Integer> listOfEnabledSensorIds = new ArrayList<Integer>();
		for(int sensorId : sensorIds) {
			if(setSensorEnabledState(sensorId, true)) {
				listOfEnabledSensorIds.add(sensorId);
			}
		}
		return listOfEnabledSensorIds.toArray(new Integer[listOfEnabledSensorIds.size()]);
	}

	/** @see setSensorEnabledState
	 */
	public boolean setSensorEnabledStateAccel1(boolean isEnabled) {
		return setSensorEnabledState(Verisense.SENSOR_ID.LIS2DW12_ACCEL, isEnabled);
	}

	/** @see setSensorEnabledState
	 */
	public boolean setSensorEnabledStateAccel2(boolean isEnabled) {
		return setSensorEnabledState(Verisense.SENSOR_ID.LSM6DS3_ACCEL, isEnabled);
	}
	
	/** @see setSensorEnabledState
	 */
	public boolean setSensorEnabledStateGyro(boolean isEnabled) {
		return setSensorEnabledState(Verisense.SENSOR_ID.LSM6DS3_GYRO, isEnabled);
	}
	
	/** @see setSensorEnabledState
	 */
	public boolean setSensorEnabledStateGsr(boolean isEnabled) {
		return setSensorEnabledState(Verisense.SENSOR_ID.GSR, isEnabled);
	}

	/** @see setSensorEnabledState
	 */
	public boolean setSensorEnabledStatePpg(boolean isEnabled) {
		return setSensorEnabledState(Verisense.SENSOR_ID.MAX86916_PPG_BLUE, isEnabled);
	}

	/** @see setSensorEnabledState
	 */
	public boolean setSensorEnabledStateBatteryVoltage(boolean isEnabled) {
		return setSensorEnabledState(Verisense.SENSOR_ID.VBATT, isEnabled);
	}

	/**
	 * Set the current bluetooth state.
	 * @param BT_STATE
	 * @return true if state is changed
	 */
	@Override
	public boolean setBluetoothRadioState(BT_STATE state) {
		boolean isChanged = super.setBluetoothRadioState(state);
		mDeviceCallbackAdapter.setBluetoothRadioState(state, isChanged);
		return isChanged;
	}
	
	/**
	 * The date and time (in minutes since 1 January 1970) the ASM sensor will start collecting data.
	 * The clock system on a Verisense sensor is in local time (e.g. Unix time expressed in your time zone, e.g. for Kuala Lumpur Unix time + 08:00).
	 * @return the recording start time.
	 */
	public long getRecordingStartTimeMinutes() {
		return recordingStartTimeMinutes;
	}

	/**
	 * The date and time (in minutes since 1 January 1970) the ASM sensor will start collecting data.
	 * The clock system on a Verisense sensor is in local time (e.g. Unix time expressed in your time zone, e.g. for Kuala Lumpur Unix time + 08:00).
	 * @param recordingStartTimeMinutes  the recording start time.
	 */
	public void setRecordingStartTimeMinutes(long recordingStartTimeMinutes) {
		this.recordingStartTimeMinutes = UtilShimmer.nudgeLong(recordingStartTimeMinutes, 0, (long) (Math.pow(2, 4*8)-1));
	}

	/**
	 * The date and time (in minutes since 1 January 1970) the ASM sensor will stop collecting data.
	 * The clock system on a Verisense sensor is in local time (e.g. Unix time expressed in your time zone, e.g. for Kuala Lumpur Unix time + 08:00).
	 * @return the recording stop time.
	 */
	public long getRecordingEndTimeMinutes() {
		return recordingEndTimeMinutes;
	}

	/**
	 * The date and time (in minutes since 1 January 1970) the ASM sensor will stop collecting data.
	 * The clock system on a Verisense sensor is in local time (e.g. Unix time expressed in your time zone, e.g. for Kuala Lumpur Unix time + 08:00).
	 * @param recordingStopTimeMinutes  the recording stop time.
	 */
	public void setRecordingEndTimeMinutes(long recordingEndTimeMinutes) {
		this.recordingEndTimeMinutes = UtilShimmer.nudgeLong(recordingEndTimeMinutes, 0, (long) (Math.pow(2, 4*8)-1));
	}

	/**
	 * The number of BLE wake-up retries to carry out if there are any pending events
	 * @return the retry count.
	 */
	public int getBleConnectionRetriesPerDay() {
		return bleConnectionRetriesPerDay;
	}

	/**
	 * The number of BLE wake-up retries to carry out if there are any pending events
	 * @param bleConnectionTriesPerDay  the retry count.
	 */
	public void setBleConnectionRetriesPerDay(int bleConnectionTriesPerDay) {
		this.bleConnectionRetriesPerDay = UtilShimmer.nudgeInteger(bleConnectionTriesPerDay, 0, (int) (Math.pow(2, 8)-1));
	}

	/**
	 * @see BLE_TX_POWER
	 * @return bluetooth power setting.
	 */
	public BLE_TX_POWER getBleTxPower() {
		return bleTxPower;
	}

	/**
	 * @see BLE_TX_POWER
	 * @param bleTxPower  bluetooth power setting.
	 */
	public void setBleTxPower(BLE_TX_POWER bleTxPower) {
		this.bleTxPower = bleTxPower;
	}

	/**
	 * Data transfer schedule
	 * @return data transfer schedule.
	 * @see PendingEventSchedule
	 */
	public PendingEventSchedule getPendingEventScheduleDataTransfer() {
		return pendingEventScheduleDataTransfer;
	}

	/**
	 * Data transfer schedule
	 * @param pendingEventScheduleDataTransfer data transfer schedule.
	 * @see PendingEventSchedule
	 */
	public void setPendingEventScheduleDataTransfer(PendingEventSchedule pendingEventScheduleDataTransfer) {
		this.pendingEventScheduleDataTransfer = pendingEventScheduleDataTransfer;
	}

	/**
	 * Status transfer schedule
	 * @return status transfer schedule.
	 * @see PendingEventSchedule
	 */
	public PendingEventSchedule getPendingEventScheduleStatusSync() {
		return pendingEventScheduleStatusSync;
	}

	/**
	 * Status transfer schedule
	 * @param pendingEventScheduleStatusSync status transfer schedule.
	 * @see PendingEventSchedule
	 */
	public void setPendingEventScheduleStatusSync(PendingEventSchedule pendingEventScheduleStatusSync) {
		this.pendingEventScheduleStatusSync = pendingEventScheduleStatusSync;
	}

	/**
	 * RTC sync schedule
	 * @return RTC sync schedule.
	 * @see PendingEventSchedule
	 */
	public PendingEventSchedule getPendingEventScheduleRwcSync() {
		return pendingEventScheduleRwcSync;
	}

	/**
	 * RTC sync schedule
	 * @param pendingEventScheduleRwcSync RTC sync schedule.
	 * @see PendingEventSchedule
	 */
	public void setPendingEventScheduleRwcSync(PendingEventSchedule pendingEventScheduleRwcSync) {
		this.pendingEventScheduleRwcSync = pendingEventScheduleRwcSync;
	}

	/**
	 * The number of minute�s interval the ASM sensor will wait after a failed connection attempt before turning on the scheduler again
	 * @return interval in minutes, if this value is set to either 0 or 65535 then the adaptive scheduler will never be turned on.
	 */
	public int getAdaptiveSchedulerInterval() {
		return adaptiveSchedulerInterval;
	}

	/**
	 * The number of minute�s interval the ASM sensor will wait after a failed connection attempt before turning on the scheduler again
	 * @param adaptiveSchedulerInterval interval in minutes, if this value is set to either 0 or 65535 then the adaptive scheduler will never be turned on.
	 */
	public void setAdaptiveSchedulerInterval(int adaptiveSchedulerInterval) {
		this.adaptiveSchedulerInterval = UtilShimmer.nudgeInteger(adaptiveSchedulerInterval, 0, (int) (Math.pow(2, 16)-1));
	}

	/**
	 * Each time the sensor fails to clear all pending events during a scheduled wake-up event, a fail counter is incremented. 
     * When the fail counter reaches the adaptive scheduler maximum fail count, the sensor will turn on the adaptive scheduler 
     * and the scheduler will be set to wake-up based on the interval
	 * @return adaptive scheduler maximum fail count.
	 */
	public int getAdaptiveSchedulerFailCount() {
		return adaptiveSchedulerFailCount;
	}

	/**
	 * Each time the sensor fails to clear all pending events during a scheduled wake-up event, a fail counter is incremented. 
     * When the fail counter reaches the adaptive scheduler maximum fail count, the sensor will turn on the adaptive scheduler 
     * and the scheduler will be set to wake-up based on the interval
	 * @param adaptiveSchedulerFailCount  adaptive scheduler maximum fail count.
	 */
	public void setAdaptiveSchedulerFailCount(int adaptiveSchedulerFailCount) {
		this.adaptiveSchedulerFailCount = UtilShimmer.nudgeInteger(adaptiveSchedulerFailCount, 0, (int) (Math.pow(2, 8)-1));
	}

	/**
	 * Is bluetooth enabled (bluetooth will always be enabled when USB powered)
	 * @return bluetoothEnabled.
	 */
	public boolean isBluetoothEnabled() {
		return bluetoothEnabled;
	}

	/**
	 * Disable/Enable bluetooth (bluetooth will always be enabled when USB powered)
	 * @return bluetoothEnabled.
	 */
	public void setBluetoothEnabled(boolean bluetoothEnabled) {
		this.bluetoothEnabled = bluetoothEnabled;
	}

	/**
	 * Is USB enabled
	 * @return usbEnabled.
	 */
	public boolean isUsbEnabled() {
		return usbEnabled;
	}

	/**
	 * Disable/Enable USB
	 * @param usbEnabled.
	 */
	public void setUsbEnabled(boolean usbEnabled) {
		this.usbEnabled = usbEnabled;
	}

	/**
	 * If the recording is only to use the long-term flash and bypass the usage of the short-term flash then the value is set to 1, otherwise 0.
	 * If expected data upload interval from ASM is longer then the short-term flash is capable of storing, it is more energy efficient to write directly to long-term flash.
	 * @return prioritiseLongTermFlash.
	 */
	public boolean isPrioritiseLongTermFlash() {
		return prioritiseLongTermFlash;
	}

	/**
	 * If the recording is only to use the long-term flash and bypass the usage of the short-term flash then the value is set to 1, otherwise 0.
	 * If expected data upload interval from ASM is longer then the short-term flash is capable of storing, it is more energy efficient to write directly to long-term flash.
	 * @param prioritiseLongTermFlash.
	 */
	public void setPrioritiseLongTermFlash(boolean prioritiseLongTermFlash) {
		this.prioritiseLongTermFlash = prioritiseLongTermFlash;
	}

	/**
	 * Is the device enabled
	 * @return deviceEnabled.
	 */
	public boolean isDeviceEnabled() {
		return deviceEnabled;
	}

	/**
	 * Disable/Enable the Verisense Device
	 * @param deviceEnabled  deviceEnabled.
	 */
	public void setDeviceEnabled(boolean deviceEnabled) {
		this.deviceEnabled = deviceEnabled;
	}

	/**
	 * Is logging enabled
	 * @return recordingEnabled.
	 */
	public boolean isRecordingEnabled() {
		return recordingEnabled;
	}

	/**
	 * Disable/Enable the logging of data
	 * @param recordingEnabled  recordingEnabled.
	 */
	public void setRecordingEnabled(boolean recordingEnabled) {
		this.recordingEnabled = recordingEnabled;
	}

	public HashMap<COMMUNICATION_TYPE, VerisenseProtocolByteCommunication> getMapOfVerisenseProtocolByteCommunication() {
		return mapOfVerisenseProtocolByteCommunication;
	}

	public void setMapOfVerisenseProtocolByteCommunication(HashMap<COMMUNICATION_TYPE, VerisenseProtocolByteCommunication> mapOfVerisenseProtocolByteCommunication) {
		this.mapOfVerisenseProtocolByteCommunication = mapOfVerisenseProtocolByteCommunication;
	}
	
	/**
	 * @param type Note not all communications types relies on a process that needs to be close, as of now only BLE requires it, this should be done when closing the app
	 */
	public void stopCommunicationProcess(COMMUNICATION_TYPE type) {
		mapOfVerisenseProtocolByteCommunication.get(type).stop();
	}

	public PASSKEY_MODE getPasskeyMode() {
		return passkeyMode;
	}
	
	public void setPasskeyMode(PASSKEY_MODE passkeyMode) {
		this.passkeyMode = passkeyMode;
	}

	/** 
	 * @return dataCompressionMode
	 */
	public DATA_COMPRESSION_MODE getDataCompressionMode() {
		return dataCompressionMode;
	}

	/** 
	 * @param dataCompressionMode
	 */
	public void setDataCompressionMode(DATA_COMPRESSION_MODE dataCompressionMode) {
		this.dataCompressionMode = dataCompressionMode;
	}

	/** Used to set the battery type that is currently connected to the sensor. 
	 * This is used internally in the firmware to manage how the sensor utilizes the battery in order to ensure optimum battery life.
	 * @param BATTERY_TYPE
	 */
	public BATTERY_TYPE getBatteryType() {
		return batteryType;
	}

	/** Used to set the battery type that is currently connected to the sensor. 
	 * This is used internally in the firmware to manage how the sensor utilizes the battery in order to ensure optimum battery life.
	 * @return BATTERY_TYPE
	 */
	public void setBatteryType(BATTERY_TYPE batteryType) {
		this.batteryType = batteryType;
	}

	/** 0 represents always on
	 * @return ppgRecordingDurationSeconds
	 */
	public int getPpgRecordingDurationSeconds() {
		return ppgRecordingDurationSeconds;
	}
	
	/** Enable continuous PPG recording
	 */
	public void setPpgContinuousRecording() {
		setPpgRecordingDurationSeconds(0);
		setPpgRecordingIntervalMinutes(0);
	}

	/** 0 represents always on
	 * @param ppgRecordingDurationSeconds
	 */
	public void setPpgRecordingDurationSeconds(int ppgRecordingDurationSeconds) {
		this.ppgRecordingDurationSeconds = UtilShimmer.nudgeInteger(ppgRecordingDurationSeconds, 0, (int) (Math.pow(2, 16)-1));
	}

	/** 0 represents always on
	 * @return ppgRecordingIntervalMinutes
	 */
	public int getPpgRecordingIntervalMinutes() {
		return ppgRecordingIntervalMinutes;
	}

	/** 0 represents always on
	 * @param ppgRecordingIntervalMinutes
	 */
	public void setPpgRecordingIntervalMinutes(int ppgRecordingIntervalMinutes) {
		this.ppgRecordingIntervalMinutes = UtilShimmer.nudgeInteger(ppgRecordingIntervalMinutes, 0, (int) (Math.pow(2, 16)-1));
	}

	/** Update sensor configuration
	 * @param sensorConfig
	 */
	@Override
	public void setSensorConfig(ISensorConfig sensorConfig) {
		if(sensorConfig instanceof BLE_TX_POWER) {
			setBleTxPower((BLE_TX_POWER)sensorConfig);
		} else {
			super.setSensorConfig(sensorConfig);
		}
	}

	/** Get all sensor config
	 * @return List of sensor config
	 */
	@Override
	public List<ISensorConfig> getSensorConfig() {
		List<ISensorConfig> listOfConfig = new ArrayList<>();
		listOfConfig.addAll(super.getSensorConfig());
		listOfConfig.add(bleTxPower);
		return listOfConfig;
	}

	/**
	 * @return currentStreamingCommsRoute
	 */
	public COMMUNICATION_TYPE getCurrentStreamingCommsRoute() {
		return currentStreamingCommsRoute;
	}

	/**
	 * @param currentStreamingCommsRoute
	 */
	public void setCurrentStreamingCommsRoute(COMMUNICATION_TYPE currentStreamingCommsRoute) {
		this.currentStreamingCommsRoute = currentStreamingCommsRoute;
	}
	

	/**
	 * @return trialName
	 */
	public String getTrialName() {
		return mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).getTrialName();
	}
	
	/**
	 * @param trialName
	 */
	@Override
	public void setTrialName(String trialName) {
		super.setTrialName(trialName);
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).setTrialName(trialName);
	}
	
	/**
	 * @param participantID
	 */
	public void setParticipantID(String participant) {
		mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).participantID = participant;
	}
	
	/**
	 * @return participantID
	 */
	public String getParticipantID() {
		return mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).participantID;
	}
	
	/**
	 * @return The binary file path
	 */
	public String getDataFilePath() {
		return mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).getDataFilePath();
	}
	
	@Deprecated // not sure if this is used for the file parser if its not we can delete this method. the proper way to get the payload index will be through MSG_IDENTIFIER_SYNC_PROGRESS as this method will fail on Android 
	public int getPayloadIndex() {
		return mapOfVerisenseProtocolByteCommunication.get(currentStreamingCommsRoute).rxVerisenseMessageInProgress.payloadIndex;
	}
	
	protected void sendProgressReport(BluetoothProgressReportPerCmd pRPC) {
		mDeviceCallbackAdapter.sendProgressReport(pRPC);
	}
	
	public void startOperation(BT_STATE currentOperation, int totalNumOfCmds){
		mDeviceCallbackAdapter.startOperation(currentOperation, totalNumOfCmds);
	}
	
	public void finishOperation(BT_STATE btState){
		mDeviceCallbackAdapter.finishOperation(btState);
	}
	
	@Override
	public void operationStart(BT_STATE btState){
		startOperation(btState, 1);
		try {
			writeAndReadOperationalConfig();
		} catch (ShimmerException e) {
			e.printStackTrace();
		}
	}

	public boolean isChargerLtc4123Present() {
		return isChargerLtc4123Present(getShimmerVerObject());
	}

	public static boolean isChargerLtc4123Present(ShimmerVerObject svo) {
		return isChargerLtc4123Present(svo.getShimmerExpansionBoardId(), svo.getShimmerExpansionBoardRev(), svo.getShimmerExpansionBoardRevSpecial());
	}
	
	public static boolean isChargerLtc4123Present(int hwVerMajor, int hwVerMinor, int hwVerInternal) {
		return ((hwVerMajor == HW_ID.VERISENSE_PULSE_PLUS && hwVerMinor == 7 && hwVerInternal ==1)
				|| (hwVerMajor == HW_ID.VERISENSE_PULSE_PLUS && hwVerMinor >= 8));
	}

	public boolean isChargerLm3658dPresent() {
		return isChargerLm3658dPresent(getShimmerVerObject());
	}

	public static boolean isChargerLm3658dPresent(ShimmerVerObject svo) {
		return isChargerLm3658dPresent(svo.getShimmerExpansionBoardId(), svo.getShimmerExpansionBoardRev(), svo.getShimmerExpansionBoardRevSpecial());
	}

	public static boolean isChargerLm3658dPresent(int hwVerMajor, int hwVerMinor, int hwVerInternal) {
		return hwVerMajor == HW_ID.VERISENSE_GSR_PLUS;
	}

}
