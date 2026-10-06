package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
 * DEV-1147: with a Bluetooth link CRC on, the firmware appends it to every
 * reply, a bare ACK included (ShimBt_sendRsp, log-and-stream-common
 * Comms/shimmer_bt_uart.c). So a SET command sent while streaming is answered
 * FF F4 65, or FF F4 with a 1-byte CRC: F4 65 is the CRC of the single byte
 * 0xFF. processPacket() took the byte after a streaming ACK to be the next
 * packet's DATA_PACKET or an in-stream response's 0x8A, so F4 was a parse
 * error. It threw away the data packet before the ACK and never processed the
 * ACK, and the ACK timer then cleared the instruction queue. For
 * startSDLogging() that meant START_LOGGING_ONLY_COMMAND, queued behind
 * SET_RWC_COMMAND, was never sent.
 * <p>
 * No hardware: the parser reads from an in-memory byte queue, one byte at a
 * time as the IO thread does. It lives in this package because the parser and
 * its state are protected members of ShimmerBluetooth.
 *
 * @author Mark Nolan
 */
public class API_00027_StreamingBareAckCrcTest {

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

	@After
	public void stopAckTimer() {
		if(mDevice!=null){
			mDevice.stopTimerCheckForAckOrResp();
		}
	}

	/** A Shimmer3R part way through streaming PACKET_SIZE-byte packets */
	private void streamingShimmer3r(BT_CRC_MODE crcMode) {
		mDevice = new ShimmerPC("COM99");
		mRadio = new QueueRadio();
		mDevice.setTestRadio(mRadio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 7));
		mDevice.setPacketSize(PACKET_SIZE);
		mDevice.setCurrentBtCommsCrcMode(crcMode);
		mDevice.setIsStreaming(true);
		// Queue each parsed packet rather than build an ObjectCluster from it
		mDevice.mUseProcessingThread = true;
	}

	/** What the IO thread does when it sends the next queued instruction while streaming */
	private void sendNextInstruction() {
		mDevice.mCurrentCommand = mDevice.getListofInstructions().get(0)[0];
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

	/** Has a 0x00 and a 0xFF in it, which a resync would take for the start of a frame */
	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, (byte) 0xFF, 0x07, 0x3C, 0x08, 0x3F, 0x09};
	}

	/** Neither this nor its CRC has a 0x00 or a 0xFF in it */
	private static byte[] payloadWithoutFrameStarts(int i) {
		return new byte[] {(byte) (0x10+i), 0x20, 0x01, 0x11, 0x07, 0x3C, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(byte[] payload, BT_CRC_MODE crcMode) {
		byte[] frame = new byte[1+payload.length];
		frame[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, frame, 1, payload.length);
		return withCrc(frame, crcMode);
	}

	/** The reply to a SET command */
	private static byte[] bareAck(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, crcMode);
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

	private void assertAckProcessed() {
		assertFalse("the ACK is taken", mDevice.mWaitForAck);
		assertFalse("the next instruction can be sent", mDevice.isInstructionStackLock());
		assertNull("the ACK timer is cancelled, so it cannot clear the instruction queue", mDevice.mTimerCheckForAckOrResp);
	}

	/** What is left in the parser's buffer, each byte with its PC timestamp */
	private void assertBuffered(byte... expected) {
		assertEquals(hex(expected), hex(mDevice.mByteArrayOutputStream.toByteArray()));
		assertEquals("one PC timestamp per buffered byte", expected.length, mDevice.mListofPCTimeStamps.size());
	}

	/**
	 * startSDLogging() while streaming sends SET_RWC_COMMAND and then
	 * START_LOGGING_ONLY_COMMAND, and the firmware answers each with a bare ACK
	 */
	private void startSdLoggingWhileStreaming(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.startSDLogging();

		sendNextInstruction();
		assertEquals(ShimmerObject.SET_RWC_COMMAND, mDevice.mCurrentCommand);
		receive(dataPacket(payload(1), crcMode), bareAck(crcMode),
				dataPacket(payload(2), crcMode), dataPacket(payload(3), crcMode));
		assertAckProcessed();
		assertEquals("START_LOGGING_ONLY_COMMAND is still queued", 1, mDevice.getListofInstructions().size());

		sendNextInstruction();
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, mDevice.mCurrentCommand);
		receive(dataPacket(payload(4), crcMode), bareAck(crcMode),
				dataPacket(payload(5), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertAckProcessed();
		assertTrue(mDevice.getListofInstructions().isEmpty());
		assertTrue(mDevice.isSDLogging());
		assertEquals(BT_STATE.STREAMING_AND_SDLOGGING, mDevice.getBluetoothRadioState());

		assertParsed(payload(1), payload(2), payload(3), payload(4), payload(5));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}

	@Test
	public void bareAckWithOneByteCrcWhileStreaming() {
		startSdLoggingWhileStreaming(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void bareAckWithTwoByteCrcWhileStreaming() {
		startSdLoggingWhileStreaming(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** Already worked: with no CRC the ACK is followed by the next packet's DATA_PACKET */
	@Test
	public void bareAckWithoutCrcWhileStreaming() {
		startSdLoggingWhileStreaming(BT_CRC_MODE.OFF);
	}

	/** Already worked: an in-stream response's 0x8A follows the ACK, and its CRC comes at the end */
	private void inStreamResponseWhileStreaming(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.readBattery();
		sendNextInstruction();
		// [ACK][INSTREAM_CMD_RESPONSE][VBATT_RESPONSE][ADC, LSB first][charger status][CRC]
		byte[] vbatt = withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
				ShimmerObject.VBATT_RESPONSE, 0x24, 0x0B, (byte) 0x80}, crcMode);
		receive(dataPacket(payload(1), crcMode), vbatt, dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertAckProcessed();
		assertTrue(mDevice.getListofInstructions().isEmpty());
		assertEquals(0x0B24, mDevice.getBattStatusDetails().getBattAdcValue(), 0);
		assertParsed(payload(1), payload(2));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}

	@Test
	public void inStreamResponseWithOneByteCrcWhileStreaming() {
		inStreamResponseWhileStreaming(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void inStreamResponseWithTwoByteCrcWhileStreaming() {
		inStreamResponseWhileStreaming(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/**
	 * The ACK's own CRC shows it is an ACK, so a corrupt data packet ahead of it
	 * is dropped without losing the ACK
	 */
	private void bareAckAfterACorruptPacket(BT_CRC_MODE crcMode, int indexToCorrupt) {
		streamingShimmer3r(crcMode);
		mDevice.stopSDLogging();
		sendNextInstruction();
		byte[] corrupt = dataPacket(payload(1), crcMode);
		corrupt[indexToCorrupt] ^= 0x01;
		receive(corrupt, bareAck(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertAckProcessed();
		assertTrue(mDevice.getListofInstructions().isEmpty());
		assertParsed(payload(2));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}

	@Test
	public void bareAckWithOneByteCrcAfterACorruptPacket() {
		bareAckAfterACorruptPacket(BT_CRC_MODE.ONE_BYTE_CRC, 1);
	}

	/** Only the CRC's second byte is wrong, which is checked separately */
	@Test
	public void bareAckWithTwoByteCrcAfterACorruptPacket() {
		bareAckAfterACorruptPacket(BT_CRC_MODE.TWO_BYTE_CRC, 1+PACKET_SIZE+1);
	}

	/**
	 * After a resync the buffer can already hold bytes beyond the ACK. Exactly
	 * the packet, the ACK and its CRC are consumed, and the rest stay with their
	 * timestamps as the start of the next frame.
	 */
	private void bareAckWithTheNextFrameAlreadyBuffered(BT_CRC_MODE crcMode) throws Exception {
		streamingShimmer3r(crcMode);
		mDevice.stopSDLogging();
		sendNextInstruction();
		byte[] nextFrameStart = Arrays.copyOf(dataPacket(payload(2), crcMode), 4);
		ByteArrayOutputStream buffered = new ByteArrayOutputStream();
		buffered.write(dataPacket(payload(1), crcMode));
		buffered.write(bareAck(crcMode));
		buffered.write(nextFrameStart);
		mDevice.mByteArrayOutputStream.write(buffered.toByteArray());
		for(long timestamp=0;timestamp<buffered.size();timestamp++){
			mDevice.mListofPCTimeStamps.add(timestamp);
		}

		mDevice.processPacket();

		assertAckProcessed();
		assertParsed(payload(1));
		assertBuffered(nextFrameStart);
		assertEquals("the timestamps kept are those of the bytes kept",
				Long.valueOf(buffered.size()-nextFrameStart.length), mDevice.mListofPCTimeStamps.get(0));
	}

	@Test
	public void bareAckWithOneByteCrcWithTheNextFrameAlreadyBuffered() throws Exception {
		bareAckWithTheNextFrameAlreadyBuffered(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void bareAckWithTwoByteCrcWithTheNextFrameAlreadyBuffered() throws Exception {
		bareAckWithTheNextFrameAlreadyBuffered(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/**
	 * A packet whose 2-byte CRC fails on its second byte is dropped, and only it.
	 * checkCrc() used to skip to the next frame itself, and its caller then
	 * skipped again, past the start of the next packet.
	 */
	@Test
	public void twoByteCrcFailingOnItsSecondByteDropsOnlyThatPacket() {
		BT_CRC_MODE crcMode = BT_CRC_MODE.TWO_BYTE_CRC;
		streamingShimmer3r(crcMode);
		byte[] corrupt = dataPacket(payloadWithoutFrameStarts(1), crcMode);
		corrupt[corrupt.length-1] ^= 0x01;
		receive(corrupt, dataPacket(payloadWithoutFrameStarts(2), crcMode),
				dataPacket(payloadWithoutFrameStarts(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertParsed(payloadWithoutFrameStarts(2), payloadWithoutFrameStarts(3));
		assertBuffered(ShimmerObject.DATA_PACKET);
	}
}
