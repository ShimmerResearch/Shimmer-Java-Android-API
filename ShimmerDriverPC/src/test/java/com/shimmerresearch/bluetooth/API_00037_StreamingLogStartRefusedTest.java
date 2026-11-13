package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-1431, while streaming: firmware that cannot start a log refuses START_LOGGING_ONLY_COMMAND in
 * the middle of a stream as it does when idle (docked or on USB power, no SD card or a bad one,
 * logging turned off in its configuration), and its NACK comes between two data packets, followed
 * by its own link CRC when the link has one (C5, or C5 56).
 * <p>
 * On master the NACK was thrown away with the packet in front of it, and 2 s later the ACK timer's
 * special case took the device for logging. DEV-982's branch for a data packet followed by a NACK
 * did not wait for the NACK's CRC: it cleared the whole buffer at the NACK, so the CRC arrived
 * where the next packet's header should be (a parse error, then a resync), and any bytes already
 * buffered after the NACK went with it. A data packet whose CRC failed took the NACK with it, a
 * 0xFE whose CRC did not match was still taken for one, and a NACK with no command in flight
 * refused whichever command had gone last. Nor was a refused log start told apart from any other
 * refusal.
 * <p>
 * Now the packet before the NACK is kept, the NACK's CRC is checked and consumed, the refusal
 * retires the command, the device is not taken to be logging, the stream goes on, and
 * eventLogStartRefused() is called.
 * <p>
 * No hardware: the parser reads from an in-memory byte queue, one byte at a time as the IO thread
 * does, as in API_00027_StreamingBareAckCrcTest.
 *
 * @author Mark Nolan
 */
public class API_00037_StreamingLogStartRefusedTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;

	private static class QueueRadio implements ByteCommunication {
		final Deque<Byte> rx = new ArrayDeque<Byte>();

		void queue(byte... bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
		}

		@Override
		public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException {
			if(rx.size()<byteCount){
				throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			}
			byte[] out = new byte[byteCount];
			for(int i=0;i<byteCount;i++){
				out[i] = rx.poll();
			}
			return out;
		}

		@Override public int getInputBufferBytesCount() { return rx.size(); }
		@Override public boolean isOpened() { return true; }
		@Override public boolean closePort() { return true; }
		@Override public boolean openPort() { return true; }
		@Override public boolean writeBytes(byte[] buffer) { return true; }
		@Override public boolean setParams(int i, int j, int k, int l) { return true; }
		@Override public boolean purgePort(int i) { return true; }
		@Override public void setByteCommunicationListener(ByteCommunicationListener byteCommListener) { }
		@Override public void removeRadioListenerList() { }
	}

	private ShimmerPC mDevice;
	private QueueRadio mRadio;
	private final List<Byte> mLogStartsRefused = Collections.synchronizedList(new ArrayList<Byte>());
	private final List<Byte> mCommandsRefused = Collections.synchronizedList(new ArrayList<Byte>());

	@After
	public void stopTimers() {
		if(mDevice!=null){
			mDevice.stopTimerCheckForAckOrResp();
			mDevice.stopTimerReadStatus();
		}
	}

	/** A Shimmer3R on LogAndStream v1.01.025 part way through streaming PACKET_SIZE-byte packets */
	private void streamingShimmer3r(BT_CRC_MODE crcMode) {
		mDevice = new ShimmerPC("COM99") {
			@Override
			protected void eventLogStartRefused(byte command) {
				mLogStartsRefused.add(command);
				super.eventLogStartRefused(command);
			}

			@Override
			protected void eventCommandRefused(byte command) {
				mCommandsRefused.add(command);
			}
		};
		mRadio = new QueueRadio();
		mDevice.setTestRadio(mRadio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25));
		mDevice.setPacketSize(PACKET_SIZE);
		mDevice.setCurrentBtCommsCrcMode(crcMode);
		mDevice.setIsStreaming(true);
		mDevice.setBluetoothRadioState(BT_STATE.STREAMING);
		// Queue each parsed packet rather than build an ObjectCluster from it
		mDevice.mUseProcessingThread = true;
	}

	/** startSDLogging() while streaming, and the IO thread sending its START_LOGGING_ONLY */
	private void startSdLoggingWhileStreaming() {
		mDevice.startSDLogging();
		assertEquals("only START_LOGGING_ONLY_COMMAND is queued", 1, mDevice.getListofInstructions().size());
		mDevice.mCurrentCommand = mDevice.getListofInstructions().get(0)[0];
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, mDevice.mCurrentCommand);
		mDevice.setInstructionStackLock(true);
		mDevice.mWaitForAck = true;
		mDevice.startTimerCheckForAckOrResp(2);
	}

	/** Hands the bytes to the streaming parser one at a time, as the IO thread does */
	private void receive(byte[]... frames) {
		for(byte[] frame:frames){
			mRadio.queue(frame);
		}
		while(!mRadio.rx.isEmpty()){
			mDevice.processWhileStreaming();
		}
	}

	/** Has a 0x00, a 0xFE and a 0xFF in it, which a resync would take for the start of a frame */
	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, (byte) 0xFF, 0x07, (byte) 0xFE, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(byte[] payload, BT_CRC_MODE crcMode) {
		byte[] frame = new byte[1+payload.length];
		frame[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, frame, 1, payload.length);
		return withCrc(frame, crcMode);
	}

	/** The firmware's refusal, with its link CRC */
	private static byte[] bareNack(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.NACK_COMMAND_PROCESSED}, crcMode);
	}

	/** The frame followed by the link CRC over all of it, low byte first */
	private static byte[] withCrc(byte[] frame, BT_CRC_MODE crcMode) {
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(frame, frame.length);
		byte[] framed = Arrays.copyOf(frame, frame.length+crcMode.getNumCrcBytes());
		System.arraycopy(crc, 0, framed, frame.length, crcMode.getNumCrcBytes());
		return framed;
	}

	private static String hex(byte[] bytes) {
		return UtilShimmer.bytesToHexStringWithSpacesFormatted(bytes);
	}

	private void assertParsed(byte[]... expectedPayloads) {
		List<String> expected = new ArrayList<String>();
		for(byte[] payload:expectedPayloads){
			expected.add(hex(payload));
		}
		List<String> parsed = new ArrayList<String>();
		// Package-private, so ShimmerPC does not inherit it
		ShimmerBluetooth device = mDevice;
		for(RawBytePacketWithPCTimeStamp packet:device.mABQPacketByeArray){
			parsed.add(hex(packet.mDataArray));
		}
		assertEquals("every good data packet is parsed, once and in order", expected, parsed);
	}

	/** What is left in the parser's buffer */
	private void assertBuffered(byte... expected) {
		assertEquals(hex(expected), hex(mDevice.mByteArrayOutputStream.toByteArray()));
	}

	/** The start retired as refused, the device not logging and still streaming */
	private void assertRefused() {
		assertFalse("the NACK is taken", mDevice.mWaitForAck);
		assertFalse("the next instruction can be sent", mDevice.isInstructionStackLock());
		assertNull("the ACK timer is cancelled, so its special case cannot take the device for logging",
				mDevice.mTimerCheckForAckOrResp);
		assertTrue("the refused start is retired", mDevice.getListofInstructions().isEmpty());
		assertFalse("the device is not taken to be logging", mDevice.isSDLogging());
		assertTrue("the stream goes on", mDevice.isStreaming());
		assertEquals(BT_STATE.STREAMING, mDevice.getBluetoothRadioState());
		assertEquals("eventLogStartRefused, once", Collections.singletonList(ShimmerObject.START_LOGGING_ONLY_COMMAND),
				mLogStartsRefused);
		assertEquals("eventCommandRefused, once", Collections.singletonList(ShimmerObject.START_LOGGING_ONLY_COMMAND),
				mCommandsRefused);
	}

	private void refusedBetweenTwoPackets(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		startSdLoggingWhileStreaming();
		receive(dataPacket(payload(1), crcMode), bareNack(crcMode),
				dataPacket(payload(2), crcMode), dataPacket(payload(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertRefused();
		assertParsed(payload(1), payload(2), payload(3));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}

	@Test
	public void aLogStartRefusedMidStreamWithoutACrc() {
		refusedBetweenTwoPackets(BT_CRC_MODE.OFF);
	}

	@Test
	public void aLogStartRefusedMidStreamWithAOneByteCrc() {
		refusedBetweenTwoPackets(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void aLogStartRefusedMidStreamWithATwoByteCrc() {
		refusedBetweenTwoPackets(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** The refusal is not left to the ACK timer, which would take the device for logging 2 s on */
	@Test
	public void theAckTimerDoesNotLaterTakeTheDeviceForLogging() throws Exception {
		refusedBetweenTwoPackets(BT_CRC_MODE.ONE_BYTE_CRC);
		Thread.sleep(2500);
		assertFalse("still not logging after the ACK timer would have run out", mDevice.isSDLogging());
	}

	/**
	 * The NACK's own CRC shows it is a NACK, so a corrupt data packet ahead of it is dropped without
	 * losing the refusal
	 */
	private void refusedAfterACorruptPacket(BT_CRC_MODE crcMode, int indexToCorrupt) {
		streamingShimmer3r(crcMode);
		startSdLoggingWhileStreaming();
		byte[] corrupt = dataPacket(payload(1), crcMode);
		corrupt[indexToCorrupt] ^= 0x01;
		receive(corrupt, bareNack(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertRefused();
		assertParsed(payload(2));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}

	@Test
	public void aRefusalWithAOneByteCrcAfterACorruptPacket() {
		refusedAfterACorruptPacket(BT_CRC_MODE.ONE_BYTE_CRC, 1);
	}

	/** Only the packet CRC's second byte is wrong, which is checked separately */
	@Test
	public void aRefusalWithATwoByteCrcAfterACorruptPacket() {
		refusedAfterACorruptPacket(BT_CRC_MODE.TWO_BYTE_CRC, 1+PACKET_SIZE+1);
	}

	/**
	 * After a resync the buffer can already hold bytes beyond the NACK. Exactly the packet, the
	 * NACK and its CRC are consumed, and the rest stay as the start of the next frame.
	 */
	private void refusedWithTheNextFrameAlreadyBuffered(BT_CRC_MODE crcMode) throws Exception {
		streamingShimmer3r(crcMode);
		startSdLoggingWhileStreaming();
		byte[] nextFrameStart = Arrays.copyOf(dataPacket(payload(2), crcMode), 4);
		ByteArrayOutputStream buffered = new ByteArrayOutputStream();
		buffered.write(dataPacket(payload(1), crcMode));
		buffered.write(bareNack(crcMode));
		buffered.write(nextFrameStart);
		mDevice.mByteArrayOutputStream.write(buffered.toByteArray());
		for(long timestamp=0;timestamp<buffered.size();timestamp++){
			mDevice.mListofPCTimeStamps.add(timestamp);
		}

		mDevice.processPacket();

		assertRefused();
		assertParsed(payload(1));
		assertBuffered(nextFrameStart);
	}

	@Test
	public void aRefusalWithoutACrcWithTheNextFrameAlreadyBuffered() throws Exception {
		refusedWithTheNextFrameAlreadyBuffered(BT_CRC_MODE.OFF);
	}

	@Test
	public void aRefusalWithATwoByteCrcWithTheNextFrameAlreadyBuffered() throws Exception {
		refusedWithTheNextFrameAlreadyBuffered(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/**
	 * A 0xFE after a packet whose next bytes are not a NACK's CRC is not taken for a refusal: the
	 * command still waits for its reply
	 */
	@Test
	public void aFeWhoseCrcDoesNotMatchIsNotARefusal() {
		BT_CRC_MODE crcMode = BT_CRC_MODE.TWO_BYTE_CRC;
		streamingShimmer3r(crcMode);
		startSdLoggingWhileStreaming();
		receive(dataPacket(payload(1), crcMode), new byte[] {ShimmerObject.NACK_COMMAND_PROCESSED, 0x11, 0x22},
				dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertTrue("the start still waits for its reply", mDevice.mWaitForAck);
		assertTrue("no refusal", mLogStartsRefused.isEmpty() && mCommandsRefused.isEmpty());
	}

	/** A NACK with no command waiting for a reply has nothing to refuse, and the stream goes on */
	@Test
	public void aNackWithNoCommandInFlightIsIgnored() {
		BT_CRC_MODE crcMode = BT_CRC_MODE.ONE_BYTE_CRC;
		streamingShimmer3r(crcMode);
		receive(dataPacket(payload(1), crcMode), bareNack(crcMode),
				dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertTrue("no refusal", mLogStartsRefused.isEmpty() && mCommandsRefused.isEmpty());
		assertTrue(mDevice.isStreaming());
		assertParsed(payload(1), payload(2));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}
}
