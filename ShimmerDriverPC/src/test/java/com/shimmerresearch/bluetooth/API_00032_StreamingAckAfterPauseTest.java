package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
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
 * DEV-1291: after STOP_LOGGING_ONLY_COMMAND the unit stops sending data packets
 * for about 100 ms while it closes its SD file. A command sent in that pause is
 * answered at once, so its ACK comes straight after the previous ACK's CRC with
 * no data packet before it. processPacket() only looks for an ACK after a data
 * packet, so it threw the reply away; the command timed out, and the timeout
 * dropped every instruction queued behind it. Both a Shimmer3 and a Shimmer3R do
 * it (found by the Java HIL, DEV-1269).
 * <p>
 * Only with a link CRC: without one the previous ACK isn't processed until the
 * stream resumes, so the next command never goes out during the pause.
 * <p>
 * No hardware: the parser reads from an in-memory byte queue, one byte at a
 * time as the IO thread does, as in API_00027 and API_00030.
 */
public class API_00032_StreamingAckAfterPauseTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;

	/** Docked, sensing, RTC set and streaming; not SD logging */
	private static final byte STATUS_DOCKED_AND_STREAMING = 0x17;

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

	/** An initialised Shimmer3R part way through streaming PACKET_SIZE-byte packets, and logging */
	private void streamingShimmer3r(BT_CRC_MODE crcMode) {
		mDevice = new ShimmerPC("COM99");
		mRadio = new QueueRadio();
		mDevice.setTestRadio(mRadio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 7));
		mDevice.setPacketSize(PACKET_SIZE);
		mDevice.setCurrentBtCommsCrcMode(crcMode);
		mDevice.setIsStreaming(true);
		mDevice.setIsInitialised(true);
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

	/** Sends STOP_LOGGING_ONLY_COMMAND, at the head of the queue, and receives its ACK after a data packet */
	private void stopLoggingAndReceiveItsAck(BT_CRC_MODE crcMode) {
		assertEquals(ShimmerObject.STOP_LOGGING_ONLY_COMMAND, mDevice.getListofInstructions().get(0)[0]);
		sendNextInstruction();
		receive(dataPacket(1, crcMode), bareAck(crcMode));
		assertCompleted();
		// The unit now pauses its data packets while it closes its SD file
	}

	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10+i), 0x20, 0x01, 0x11, 0x07, 0x3C, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(int i, BT_CRC_MODE crcMode) {
		byte[] payload = payload(i);
		byte[] frame = new byte[1+payload.length];
		frame[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, frame, 1, payload.length);
		return withCrc(frame, crcMode);
	}

	/** The reply to a SET command */
	private static byte[] bareAck(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, crcMode);
	}

	/** The reply to GET_STATUS_COMMAND, and the firmware's unprompted status push: [ACK][INSTREAM_CMD_RESPONSE][STATUS_RESPONSE][status][CRC] */
	private byte[] statusReply(BT_CRC_MODE crcMode) {
		byte[] status = mDevice.isSupportedUSBPluggedInStatus()
				? new byte[] {STATUS_DOCKED_AND_STREAMING, 0x00}
				: new byte[] {STATUS_DOCKED_AND_STREAMING};
		byte[] frame = new byte[3+status.length];
		frame[0] = ShimmerObject.ACK_COMMAND_PROCESSED;
		frame[1] = ShimmerObject.INSTREAM_CMD_RESPONSE;
		frame[2] = ShimmerObject.STATUS_RESPONSE;
		System.arraycopy(status, 0, frame, 3, status.length);
		return withCrc(frame, crcMode);
	}

	/** The reply to GET_VBATT_COMMAND: [ACK][INSTREAM_CMD_RESPONSE][VBATT_RESPONSE][ADC, LSB first][charger status][CRC] */
	private static byte[] vbattReply(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
				ShimmerObject.VBATT_RESPONSE, 0x24, 0x0B, (byte) 0x80}, crcMode);
	}

	/** The frame followed by the link CRC over all of it, low byte first */
	private static byte[] withCrc(byte[] frame, BT_CRC_MODE crcMode) {
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(frame, frame.length);
		byte[] framed = Arrays.copyOf(frame, frame.length+crcMode.getNumCrcBytes());
		System.arraycopy(crc, 0, framed, frame.length, crcMode.getNumCrcBytes());
		return framed;
	}

	private void assertParsed(byte[]... expectedPayloads) {
		List<String> expected = new ArrayList<String>();
		for(byte[] payload:expectedPayloads){
			expected.add(UtilShimmer.bytesToHexStringWithSpacesFormatted(payload));
		}
		List<String> parsed = new ArrayList<String>();
		// Package-private, so ShimmerPC does not inherit it
		ShimmerBluetooth device = mDevice;
		for(RawBytePacketWithPCTimeStamp packet:device.mABQPacketByeArray){
			parsed.add(UtilShimmer.bytesToHexStringWithSpacesFormatted(packet.mDataArray));
		}
		assertEquals("every data packet is parsed, once and in order", expected, parsed);
	}

	private void assertCompleted() {
		assertFalse("the ACK is taken", mDevice.mWaitForAck);
		assertFalse("the next instruction can be sent", mDevice.isInstructionStackLock());
		assertNull("the ACK timer is cancelled", mDevice.mTimerCheckForAckOrResp);
	}

	/** GET_STATUS_COMMAND sent in the pause: its reply must complete it, and nothing queued behind it may be lost */
	private void statusReplyInThePause(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.stopSDLogging();
		mDevice.readStatusLogAndStream();
		mDevice.readBattery();
		stopLoggingAndReceiveItsAck(crcMode);

		sendNextInstruction();
		assertEquals(ShimmerObject.GET_STATUS_COMMAND, mDevice.mCurrentCommand);
		receive(statusReply(crcMode), dataPacket(2, crcMode), dataPacket(3, crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertCompleted();
		assertTrue("the status reply is decoded", mDevice.isDocked());
		assertEquals("GET_VBATT_COMMAND is still queued", 1, mDevice.getListofInstructions().size());
		assertEquals(ShimmerObject.GET_VBATT_COMMAND, mDevice.getListofInstructions().get(0)[0]);
		assertParsed(payload(1), payload(2), payload(3));
	}

	@Test
	public void statusReplyInThePauseWithOneByteCrc() {
		statusReplyInThePause(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void statusReplyInThePauseWithTwoByteCrc() {
		statusReplyInThePause(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** GET_VBATT_COMMAND sent in the pause */
	private void batteryReplyInThePause(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.stopSDLogging();
		mDevice.readBattery();
		stopLoggingAndReceiveItsAck(crcMode);

		sendNextInstruction();
		receive(vbattReply(crcMode), dataPacket(2, crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertCompleted();
		assertTrue(mDevice.getListofInstructions().isEmpty());
		assertEquals(0x0B24, mDevice.getBattStatusDetails().getBattAdcValue(), 0);
		assertParsed(payload(1), payload(2));
	}

	@Test
	public void batteryReplyInThePauseWithOneByteCrc() {
		batteryReplyInThePause(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void batteryReplyInThePauseWithTwoByteCrc() {
		batteryReplyInThePause(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** A SET command sent in the pause: its bare ACK must complete it */
	private void bareAckInThePause(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.stopSDLogging();
		mDevice.startSDLogging(); // START_LOGGING_ONLY_COMMAND: no clock write to a streaming Shimmer3R (DEV-1337)
		mDevice.readBattery();
		assertEquals(3, mDevice.getListofInstructions().size());
		stopLoggingAndReceiveItsAck(crcMode);

		sendNextInstruction();
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, mDevice.mCurrentCommand);
		receive(bareAck(crcMode), dataPacket(2, crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertCompleted();
		assertEquals("GET_VBATT_COMMAND is still queued", 1, mDevice.getListofInstructions().size());
		assertEquals(ShimmerObject.GET_VBATT_COMMAND, mDevice.getListofInstructions().get(0)[0]);
		assertParsed(payload(1), payload(2));
	}

	@Test
	public void bareAckInThePauseWithOneByteCrc() {
		bareAckInThePause(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void bareAckInThePauseWithTwoByteCrc() {
		bareAckInThePause(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/**
	 * An ACK at the start of the buffer is taken only for the reply the command in
	 * flight expects: a status push there does not complete GET_VBATT_COMMAND.
	 */
	private void anotherReplyAtTheStartIsNotTaken(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.readBattery();
		sendNextInstruction();

		receive(statusReply(crcMode), dataPacket(1, crcMode), dataPacket(2, crcMode));
		assertEquals("GET_VBATT_COMMAND is still in flight", ShimmerObject.GET_VBATT_COMMAND, mDevice.mCurrentCommand);
		assertTrue("it is still waiting for its ACK", mDevice.mWaitForAck);
		assertTrue("the next instruction is not sent before it completes", mDevice.isInstructionStackLock());
		assertEquals(1, mDevice.getListofInstructions().size());
	}

	@Test
	public void anotherReplyAtTheStartIsNotTakenWithOneByteCrc() {
		anotherReplyAtTheStartIsNotTaken(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void anotherReplyAtTheStartIsNotTakenWithTwoByteCrc() {
		anotherReplyAtTheStartIsNotTaken(BT_CRC_MODE.TWO_BYTE_CRC);
	}
}
