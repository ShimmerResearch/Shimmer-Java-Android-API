package com.shimmerresearch.protocol;

import java.util.Arrays;

import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.SystemTimestampPlot;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.exceptions.ShimmerException;

/**
 * The Shimmer3R device model behind {@link Shimmer3RProtocol}: configuration, calibration and
 * packet decoding, reused unchanged from the existing driver.
 * <p>
 * It is a {@link ShimmerBluetooth} that is never connected. The protocol state machine feeds it
 * the parsed handshake responses through the same methods ShimmerBluetooth's own response
 * handlers call, and asks it to decode packets with the same {@code buildMsg}. Nothing here does
 * I/O, starts a thread or runs a timer. Commands the driver code queues internally (for instance
 * in {@link #prepareForStreaming()}) are never sent.
 */
final class Shimmer3RModel extends ShimmerBluetooth {

	private static final long serialVersionUID = 4120931754310295612L;

	private final transient SystemTimestampPlot mSystemTimestampPlot = new SystemTimestampPlot();

	Shimmer3RModel() {
		super();
		mUseProcessingThread = false;
	}

	// --- Handshake responses, in the order the driver applies them ----------------------------

	/** GET_SHIMMER_VERSION_RESPONSE, as ShimmerBluetooth.processResponseCommand applies it. */
	void applyHardwareVersion(byte hardwareVersion) {
		setHardwareVersion(hardwareVersion);
	}

	/** FW_VERSION_RESPONSE: identifier (2 bytes LE), major (2 bytes LE), minor, internal. */
	void applyFirmwareVersion(byte[] six) {
		int id = ((six[1] & 0xFF) << 8) + (six[0] & 0xFF);
		int major = ((six[3] & 0xFF) << 8) + (six[2] & 0xFF);
		int minor = six[4] & 0xFF;
		int internal = six[5] & 0xFF;
		setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(getHardwareVersion(), id, major, minor, internal));
	}

	/** DAUGHTER_CARD_ID_RESPONSE: the three expansion board ID bytes. */
	void applyExpansionBoard(byte[] three) {
		setExpansionBoardDetailsAndCreateSensorMap(new ExpansionBoardDetails(three));
	}

	/** Length of the config bytes (InfoMem) for this firmware. Valid after the firmware version. */
	int configByteLength() {
		return getConfigByteLayout().calculateConfigByteLength();
	}

	int configByteStartAddress() {
		return getConfigByteLayout().MSP430_5XX_INFOMEM_D_ADDRESS;
	}

	/** All config bytes, once every INFOMEM_RESPONSE chunk has arrived. */
	void applyConfigBytes(byte[] all) {
		setShimmerInfoMemBytes(all);
	}

	/**
	 * PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE payload (sensor ID, then coefficients). Ports the
	 * checks of ShimmerBluetooth.processPressureCalibCoefficientsResponse, which reads from the
	 * connection itself and so cannot be called here.
	 *
	 * @return null if applied, otherwise why it was rejected
	 */
	String applyPressureCoefficients(byte[] payload) {
		if (payload.length == 0) {
			return "zero length, no sensor ID";
		}
		int sensorId = payload[0] & 0xFF;
		byte[] coefficients = Arrays.copyOfRange(payload, 1, payload.length);
		int expectedLength = getPressureCalibCoefficientByteLength(sensorId);
		if (expectedLength < 0 || coefficients.length != expectedLength) {
			return "sensor ID " + sensorId + " with " + coefficients.length + " coefficient bytes, expected " + expectedLength;
		}
		if (getHardwareVersion() == HW_ID.SHIMMER_3R) {
			if (sensorId != PRESSURE_SENSOR_ID.BMP390 && sensorId != PRESSURE_SENSOR_ID.BMP581) {
				return "a Shimmer3R carries a BMP390 or BMP581, not sensor ID " + sensorId;
			}
			setPressureSensorIdInBand(sensorId);
		}
		// A BMP581 self-compensates, so it has no coefficients to apply.
		if (coefficients.length > 0) {
			retrievePressureCalibrationParametersFromPacket(coefficients, CALIB_READ_SOURCE.LEGACY_BT_COMMAND);
		}
		return null;
	}

	/** The whole calibration dump, once every RSP_CALIB_DUMP_COMMAND chunk has arrived. */
	void applyCalibrationDump(byte[] all) {
		calibByteDumpParse(all, CALIB_READ_SOURCE.RADIO_DUMP);
	}

	/** INQUIRY_RESPONSE payload: settings followed by the channel list. */
	void applyInquiry(byte[] inquiry) {
		interpretInqResponse(inquiry);
	}

	void applyCrcMode(BT_CRC_MODE mode) {
		setCurrentBtCommsCrcMode(mode);
	}

	// --- Streaming ------------------------------------------------------------------------------

	/**
	 * Resets decoding state exactly as ShimmerBluetooth.startStreaming does (packet loss,
	 * calibration selection, timestamps), by calling it. The commands it queues are discarded.
	 */
	void prepareForStreaming() throws ShimmerException {
		startStreaming();
		mSystemTimestampPlot.reset();
		getListofInstructions().clear();
	}

	/** Called once START_STREAMING has been acknowledged, as the driver does on that ACK. */
	void streamingStarted() {
		mIsStreaming = true;
	}

	void streamingStopped() {
		mIsStreaming = false;
	}

	/** Decodes one data packet (without its header and CRC), as ShimmerBluetooth.buildAndSendMsg does. */
	ObjectCluster decode(byte[] packet, long pcTimeMs) {
		ObjectCluster objectCluster = buildMsg(packet, COMMUNICATION_TYPE.BLUETOOTH, false, pcTimeMs);
		return mSystemTimestampPlot.processSystemTimestampPlot(objectCluster);
	}

	// --- ShimmerBluetooth hooks: this model is never connected, so none of them do anything --

	@Override
	public void connect(String address, String bluetoothLibrary) {
		throw new UnsupportedOperationException("Shimmer3RModel is never connected; Shimmer3RProtocol does the I/O");
	}

	@Override
	protected boolean bytesAvailableToBeRead() {
		return false;
	}

	@Override
	protected int availableBytes() {
		return 0;
	}

	@Override
	protected void writeBytes(byte[] data) {
	}

	@Override
	protected byte[] readBytes(int numberofBytes) {
		return null;
	}

	@Override
	protected byte readByte() {
		return 0;
	}

	@Override
	protected void stop() {
	}

	@Override
	protected void connectionLost() {
	}

	@Override
	protected void sendProgressReport(BluetoothProgressReportPerCmd pr) {
	}

	@Override
	protected void isReadyForStreaming() {
	}

	@Override
	protected void isNowStreaming() {
	}

	@Override
	protected void hasStopStreaming() {
	}

	@Override
	protected void sendStatusMsgPacketLossDetected() {
	}

	@Override
	protected void inquiryDone() {
	}

	@Override
	protected void sendStatusMSGtoUI(String msg) {
	}

	@Override
	protected void printLogDataForDebugging(String msg) {
	}

	@Override
	public void startOperation(BT_STATE currentOperation) {
	}

	@Override
	public void startOperation(BT_STATE currentOperation, int totalNumOfCmds) {
	}

	@Override
	public void finishOperation(BT_STATE currentOperation) {
	}

	@Override
	protected void eventLogAndStreamStatusChanged(byte currentCommand) {
	}

	@Override
	protected void batteryStatusChanged() {
	}

	@Override
	protected void dockedStateChange() {
	}

	@Override
	protected void dataHandler(ObjectCluster ojc) {
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
	}

	@Override
	protected void interpretDataPacketFormat(Object object, COMMUNICATION_TYPE commType) {
	}

	@Override
	public ShimmerDevice deepClone() {
		throw new UnsupportedOperationException("Shimmer3RModel is internal to Shimmer3RProtocol");
	}

	// As in ShimmerGRPC and ShimmerBLENative.
	@Override
	public void createConfigBytesLayout() {
		int hardwareVersion = mShimmerVerObject.mHardwareVersion == HW_ID.UNKNOWN ? HW_ID.SHIMMER_3 : mShimmerVerObject.mHardwareVersion;
		mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(),
				getFirmwareVersionMinor(), getFirmwareVersionInternal(), hardwareVersion);
	}
}
