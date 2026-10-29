package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-1262: while streaming, the branch of processPacket() for an in-stream
 * response (FF 8A ...) completed the command in flight whatever the response
 * was: it cancelled the ACK timer, cleared the wait flags, removed the head of
 * the instruction queue and unlocked it. The firmware also sends a status
 * response unprompted (dock, undock, the user button, a low-battery stop), so
 * such a push removed a SET command that was still waiting for its ACK, or a
 * queued instruction not yet sent, and the next ACK was matched to the wrong
 * instruction. The protocol matches a response to the command in flight by
 * its opcode (log-and-stream-common docs/SHIMMER3_BT_COMMUNICATION_PROTOCOL.md
 * section 5.4, rule 2).
 * <p>
 * No hardware: the parser reads from an in-memory byte queue, one byte at a
 * time as the IO thread does, as in API_00027.
 */
public class API_00030_StreamingInstreamResponseQueueTest {

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

	/**
	 * An initialised Shimmer3R part way through streaming PACKET_SIZE-byte
	 * packets. Initialised, so parsing a status push does not queue an RTC write.
	 */
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

	/** The firmware's unprompted status response: [ACK][INSTREAM_CMD_RESPONSE][STATUS_RESPONSE][status][CRC] */
	private byte[] statusPush(BT_CRC_MODE crcMode) {
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

	private void assertStillWaitingFor(byte command, int queueSize) {
		assertEquals("the command in flight is unchanged", command, mDevice.mCurrentCommand);
		assertEquals("nothing is removed from the queue", queueSize, mDevice.getListofInstructions().size());
		assertEquals("the command in flight is still at the head of the queue", command, mDevice.getListofInstructions().get(0)[0]);
		assertTrue("it is still waiting for its ACK", mDevice.mWaitForAck);
		assertTrue("the next instruction is not sent before it completes", mDevice.isInstructionStackLock());
		assertNotNull("its ACK timer still runs", mDevice.mTimerCheckForAckOrResp);
	}

	private void assertCompleted() {
		assertFalse("the ACK is taken", mDevice.mWaitForAck);
		assertFalse("the next instruction can be sent", mDevice.isInstructionStackLock());
		assertNull("the ACK timer is cancelled", mDevice.mTimerCheckForAckOrResp);
	}

	/**
	 * startSDLogging() queues START_LOGGING_ONLY_COMMAND (no clock write to a
	 * streaming Shimmer3R, DEV-1337), and readBattery() queues GET_VBATT_COMMAND
	 * behind it. A status push between START_LOGGING_ONLY_COMMAND and its ACK must
	 * leave both queued, and the ACK must then complete START_LOGGING_ONLY_COMMAND,
	 * not GET_VBATT_COMMAND.
	 */
	private void statusPushWhileASetCommandAwaitsItsAck(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.startSDLogging();
		mDevice.readBattery();
		assertEquals(2, mDevice.getListofInstructions().size());
		sendNextInstruction();
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, mDevice.mCurrentCommand);

		receive(dataPacket(1, crcMode), statusPush(crcMode), dataPacket(2, crcMode));
		assertTrue("the status push is still processed", mDevice.isDocked());
		assertStillWaitingFor(ShimmerObject.START_LOGGING_ONLY_COMMAND, 2);

		receive(dataPacket(3, crcMode), bareAck(crcMode), dataPacket(4, crcMode), dataPacket(5, crcMode));
		assertCompleted();
		assertEquals("GET_VBATT_COMMAND is still queued", 1, mDevice.getListofInstructions().size());
		assertEquals(ShimmerObject.GET_VBATT_COMMAND, mDevice.getListofInstructions().get(0)[0]);
	}

	@Test
	public void statusPushWhileASetCommandAwaitsItsAckWithoutCrc() {
		statusPushWhileASetCommandAwaitsItsAck(BT_CRC_MODE.OFF);
	}

	@Test
	public void statusPushWhileASetCommandAwaitsItsAckWithOneByteCrc() {
		statusPushWhileASetCommandAwaitsItsAck(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void statusPushWhileASetCommandAwaitsItsAckWithTwoByteCrc() {
		statusPushWhileASetCommandAwaitsItsAck(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** A status push is not the reply to GET_VBATT_COMMAND, so the command keeps waiting for its own */
	private void statusPushWhileAGetCommandAwaitsItsReply(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.readBattery();
		sendNextInstruction();

		receive(dataPacket(1, crcMode), statusPush(crcMode), dataPacket(2, crcMode));
		assertTrue("the status push is still processed", mDevice.isDocked());
		assertStillWaitingFor(ShimmerObject.GET_VBATT_COMMAND, 1);

		receive(dataPacket(3, crcMode), vbattReply(crcMode), dataPacket(4, crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertCompleted();
		assertTrue(mDevice.getListofInstructions().isEmpty());
		assertEquals(0x0B24, mDevice.getBattStatusDetails().getBattAdcValue(), 0);
	}

	@Test
	public void statusPushWhileAGetCommandAwaitsItsReplyWithoutCrc() {
		statusPushWhileAGetCommandAwaitsItsReply(BT_CRC_MODE.OFF);
	}

	@Test
	public void statusPushWhileAGetCommandAwaitsItsReplyWithOneByteCrc() {
		statusPushWhileAGetCommandAwaitsItsReply(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void statusPushWhileAGetCommandAwaitsItsReplyWithTwoByteCrc() {
		statusPushWhileAGetCommandAwaitsItsReply(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** With no command in flight, a status push must not remove a queued instruction not yet sent */
	private void statusPushWithNothingInFlight(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		mDevice.readBattery();

		receive(dataPacket(1, crcMode), statusPush(crcMode), dataPacket(2, crcMode));
		assertTrue("the status push is still processed", mDevice.isDocked());
		assertEquals("GET_VBATT_COMMAND is still queued", 1, mDevice.getListofInstructions().size());
		assertEquals(ShimmerObject.GET_VBATT_COMMAND, mDevice.getListofInstructions().get(0)[0]);
		assertFalse(mDevice.isInstructionStackLock());
	}

	@Test
	public void statusPushWithNothingInFlightWithoutCrc() {
		statusPushWithNothingInFlight(BT_CRC_MODE.OFF);
	}

	@Test
	public void statusPushWithNothingInFlightWithOneByteCrc() {
		statusPushWithNothingInFlight(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void statusPushWithNothingInFlightWithTwoByteCrc() {
		statusPushWithNothingInFlight(BT_CRC_MODE.TWO_BYTE_CRC);
	}
}
