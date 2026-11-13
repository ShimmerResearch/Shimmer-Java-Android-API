package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.EventChannelListener;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.SHIMMER_FEATURE;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.shimmer2r3.EventChannelStatus;
import com.shimmerresearch.driver.shimmer2r3.EventRecord;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * Repro tests for the review findings on #348, written before any fix. Each
 * asserts the behaviour the protocol needs; a failure here confirms the
 * finding on the unchanged code.
 */
public class API_00044_EventStreamReviewReproTest {

	private static final int PACKET_SIZE = 9;
	private static final ShimmerVerObject S3R = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);

	private static byte[] frame(int type, int seq, long ticks, int channel, int state) {
		byte[] f = new byte[21];
		f[0] = (byte) 0x8A;
		f[1] = (byte) 0xBB;
		f[2] = 16;
		f[3] = (byte) type;
		f[5] = (byte) seq;
		f[6] = (byte) (seq>>8);
		for(int i=0;i<8;i++) {
			f[7+i] = (byte) (ticks>>(8*i));
		}
		f[15] = (byte) channel;
		f[16] = (byte) state;
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(f, 19);
		f[19] = crc[0];
		f[20] = crc[1];
		return f;
	}

	private static final byte[] PRESS = frame(EventRecord.TYPE_EDGE, 1, 0x357237185333L, EventRecord.CHANNEL_BUTTON, 1);
	private static final byte[] RELEASE = frame(EventRecord.TYPE_EDGE, 2, 0x357237186333L, EventRecord.CHANNEL_BUTTON, 0);

	private static class Collected implements EventChannelListener {
		final List<EventRecord> records = Collections.synchronizedList(new ArrayList<EventRecord>());
		final List<EventChannelStatus> statuses = Collections.synchronizedList(new ArrayList<EventChannelStatus>());
		@Override public void onEventRecord(ShimmerBluetooth shimmer, EventRecord record) { records.add(record); }
		@Override public void onEventChannelStatus(ShimmerBluetooth shimmer, EventChannelStatus status) { statuses.add(status); }
	}

	private final Collected mCollected = new Collected();
	private final List<Byte> mAcksProcessed = Collections.synchronizedList(new ArrayList<Byte>());

	// ------------------------------------------------------------------
	// Streaming parser, fed one byte at a time
	// ------------------------------------------------------------------

	private static class QueueRadio implements ByteCommunication {
		final Deque<Byte> rx = new ArrayDeque<Byte>();
		void queue(byte... bytes) { for(byte b:bytes) rx.add(b); }
		@Override public byte[] readBytes(int n, int timeout) throws SerialPortTimeoutException {
			if(rx.size()<n) throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			byte[] out = new byte[n];
			for(int i=0;i<n;i++) out[i] = rx.poll();
			return out;
		}
		@Override public int getInputBufferBytesCount() { return rx.size(); }
		@Override public boolean isOpened() { return true; }
		@Override public boolean closePort() { return true; }
		@Override public boolean openPort() { return true; }
		@Override public boolean writeBytes(byte[] buffer) { return true; }
		@Override public boolean setParams(int i, int j, int k, int l) { return true; }
		@Override public boolean purgePort(int i) { return true; }
		@Override public void setByteCommunicationListener(ByteCommunicationListener l) { }
		@Override public void removeRadioListenerList() { }
	}

	private ShimmerPC mStreaming;
	private QueueRadio mQueue;

	private void streamingShimmer3r(BT_CRC_MODE crcMode) {
		mStreaming = new ShimmerPC("COM99") {
			private static final long serialVersionUID = 1L;
			@Override
			protected void processAckFromSetCommand(byte currentCommand) {
				mAcksProcessed.add(currentCommand);
				super.processAckFromSetCommand(currentCommand);
			}
		};
		mQueue = new QueueRadio();
		mStreaming.setTestRadio(mQueue);
		mStreaming.setShimmerVersionObjectAndCreateSensorMap(S3R);
		mStreaming.setPacketSize(PACKET_SIZE);
		mStreaming.setCurrentBtCommsCrcMode(crcMode);
		mStreaming.setIsStreaming(true);
		mStreaming.setBluetoothRadioState(BT_STATE.STREAMING);
		mStreaming.mUseProcessingThread = true;
		mStreaming.addEventChannelListener(mCollected);
	}

	/** writeEventStream(true) acknowledged earlier, so frames may arrive; nothing left in flight */
	private void alreadyOptedIn() {
		assertTrue(mStreaming.writeEventStream(true));
		mStreaming.getListofInstructions().clear();
		mStreaming.setInstructionStackLock(false);
		mStreaming.mWaitForAck = false;
		mStreaming.mWaitForResponse = false;
	}

	private void receive(byte[]... chunks) {
		for(byte[] c:chunks) mQueue.queue(c);
		while(!mQueue.rx.isEmpty()) mStreaming.processWhileStreaming();
	}

	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, 0x02, 0x07, 0x05, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(byte[] payload, BT_CRC_MODE crcMode) {
		byte[] f = new byte[1+payload.length];
		f[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, f, 1, payload.length);
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(f, f.length);
		byte[] out = Arrays.copyOf(f, f.length+crcMode.getNumCrcBytes());
		System.arraycopy(crc, 0, out, f.length, crcMode.getNumCrcBytes());
		return out;
	}

	private List<String> parsed() {
		List<String> out = new ArrayList<String>();
		ShimmerBluetooth d = mStreaming;
		for(RawBytePacketWithPCTimeStamp p:d.mABQPacketByeArray) out.add(UtilShimmer.bytesToHexStringWithSpacesFormatted(p.mDataArray));
		return out;
	}

	@After
	public void stopTimers() {
		if(mStreaming!=null) {
			mStreaming.stopTimerCheckForAckOrResp();
			mStreaming.stopTimerReadStatus();
		}
		if(mIdle!=null) {
			mIdle.stopTimerCheckForAckOrResp();
			mIdle.disconnectNoException();
		}
	}

	/**
	 * Finding 2: an ACK with no command waiting for it, then a frame. The SET
	 * that went last must not have its ACK handling run a second time.
	 */
	@Test
	public void finding2_anAckNobodyWaitsForBeforeAFrameDoesNotReRunTheLastSetsAck() {
		streamingShimmer3r(BT_CRC_MODE.OFF);
		alreadyOptedIn();
		// The last command was a SET that completed long ago
		mStreaming.mCurrentCommand = ShimmerObject.SET_SAMPLING_RATE_COMMAND;
		receive(dataPacket(payload(1), BT_CRC_MODE.OFF), new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, PRESS,
				dataPacket(payload(2), BT_CRC_MODE.OFF), new byte[] {ShimmerObject.DATA_PACKET});
		assertEquals("the record is taken", 1, mCollected.records.size());
		assertEquals("no SET's ACK is processed: none was in flight", Collections.emptyList(), mAcksProcessed);
	}

	/**
	 * Finding 3: no link CRC, opted in, and a byte lost from a packet, so the
	 * parser's boundary falls one byte late and lands on a data byte of 0x8A.
	 * The misframed bytes must not be handed on as a packet; before #348 they
	 * were discarded to the next packet boundary.
	 */
	@Test
	public void finding3_withoutACrcAMisframedPacketBeforeA0x8ADataByteIsNotTakenAsAPacket() {
		streamingShimmer3r(BT_CRC_MODE.OFF);
		alreadyOptedIn();
		byte[] good = dataPacket(payload(1), BT_CRC_MODE.OFF);
		// Packet 2 loses its 4th byte on the link, and its 2nd byte happens to be 0x8A
		byte[] p2 = new byte[] {0x20, (byte) 0x8A, 0x01, 0x02, 0x07, 0x05, 0x08, 0x3F, 0x09};
		byte[] p2Lost = new byte[1+p2.length-1];
		p2Lost[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(p2, 0, p2Lost, 1, 3);
		System.arraycopy(p2, 4, p2Lost, 4, p2.length-4);
		byte[] p3 = dataPacket(new byte[] {(byte) 0x8A, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x00}, BT_CRC_MODE.OFF);
		receive(good, p2Lost, p3, dataPacket(payload(4), BT_CRC_MODE.OFF), new byte[] {ShimmerObject.DATA_PACKET});
		List<String> got = parsed();
		// The bytes the parser framed as packet 2: 8 of p2's and the next packet's 0x00 header
		byte[] misframed = new byte[PACKET_SIZE];
		System.arraycopy(p2Lost, 1, misframed, 0, p2Lost.length-1);
		misframed[PACKET_SIZE-1] = ShimmerObject.DATA_PACKET;
		assertFalse("misframed bytes " + UtilShimmer.bytesToHexStringWithSpacesFormatted(misframed) + " were taken as a packet: " + got,
				got.contains(UtilShimmer.bytesToHexStringWithSpacesFormatted(misframed)));
	}

	/**
	 * Finding 5, the consequence: a status reply, [ACK][0xBA][9], arriving
	 * between data packets is not taken, so the command never completes.
	 */
	@Test
	public void finding5_aStatusReplyBetweenDataPacketsIsTaken() {
		streamingShimmer3r(BT_CRC_MODE.OFF);
		mStreaming.mCurrentCommand = ShimmerObject.GET_EVENT_CHANNEL_STATUS_COMMAND;
		mStreaming.getListofInstructions().add(new byte[] {ShimmerObject.GET_EVENT_CHANNEL_STATUS_COMMAND});
		mStreaming.setInstructionStackLock(true);
		mStreaming.mWaitForAck = true;
		byte[] reply = new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.EVENT_CHANNEL_STATUS_RESPONSE,
				0x03, 0x0B, 0x02, 0x05, 0x00, 0x02, 0x00, 0x01, 0x00};
		receive(dataPacket(payload(1), BT_CRC_MODE.OFF), reply, dataPacket(payload(2), BT_CRC_MODE.OFF),
				dataPacket(payload(3), BT_CRC_MODE.OFF), new byte[] {ShimmerObject.DATA_PACKET});
		assertEquals("the status reply is taken", 1, mCollected.statuses.size());
		assertFalse("the command completes", mStreaming.mWaitForAck);
	}

	/**
	 * Finding 5, the precondition: readEventChannelStatus() is queued behind a
	 * start of streaming that has not been acknowledged yet, so its reply comes
	 * once packets flow.
	 */
	@Test
	public void finding5_theStatusIsNotQueuedBehindAStartOfStreaming() throws Exception {
		ShimmerPC s = new ShimmerPC("COM98");
		s.setShimmerVersionObjectAndCreateSensorMap(S3R);
		// As the probe leaves it on a unit with the event channel
		java.lang.reflect.Method m = com.shimmerresearch.driver.ShimmerObject.class.getDeclaredMethod("setEventChannelSupported", Boolean.class);
		m.setAccessible(true);
		m.invoke(s, Boolean.TRUE);
		s.startStreaming();
		boolean sent = s.readEventChannelStatus();
		boolean queuedAfterStart = false;
		boolean startSeen = false;
		for(byte[] ins:s.getListofInstructions()) {
			if(ins[0]==ShimmerObject.START_STREAMING_COMMAND) startSeen = true;
			if(startSeen && ins[0]==ShimmerObject.GET_EVENT_CHANNEL_STATUS_COMMAND) queuedAfterStart = true;
		}
		s.stopTimerCheckForAckOrResp();
		s.stopTimerReadStatus();
		assertFalse("the status is queued after a start of streaming (sent=" + sent + ")", queuedAfterStart);
	}

	// ------------------------------------------------------------------
	// Idle, with the IOThread running
	// ------------------------------------------------------------------

	private static class AnsweringRadio implements ByteCommunication {
		final ConcurrentLinkedDeque<Byte> rx = new ConcurrentLinkedDeque<Byte>();
		void queue(byte... bytes) { for(byte b:bytes) rx.add(b); }
		@Override public boolean writeBytes(byte[] buffer) { queue(ShimmerObject.ACK_COMMAND_PROCESSED); return true; }
		@Override public byte[] readBytes(int n, int timeout) throws SerialPortTimeoutException {
			if(rx.size()<n) throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			byte[] out = new byte[n];
			for(int i=0;i<n;i++) out[i] = rx.poll();
			return out;
		}
		@Override public int getInputBufferBytesCount() { return rx.size(); }
		@Override public boolean isOpened() { return true; }
		@Override public boolean closePort() { return true; }
		@Override public boolean openPort() { return true; }
		@Override public boolean setParams(int i, int j, int k, int l) { return true; }
		@Override public boolean purgePort(int i) { return true; }
		@Override public void setByteCommunicationListener(ByteCommunicationListener l) { }
		@Override public void removeRadioListenerList() { }
	}

	private ShimmerPC mIdle;
	private AnsweringRadio mAnswering;

	private void idleOptedIn() throws InterruptedException {
		mAnswering = new AnsweringRadio();
		mIdle = new ShimmerPC("COM97");
		mIdle.setTestRadio(mAnswering);
		mIdle.setShimmerVersionObjectAndCreateSensorMap(S3R);
		mIdle.setIsInitialised(true);
		mIdle.addEventChannelListener(mCollected);
		mIdle.mIOThread = mIdle.new IOThread();
		mIdle.mIOThread.start();
		assertTrue(mIdle.writeEventStream(true));
		long end = System.currentTimeMillis()+2000;
		while((!mIdle.getListofInstructions().isEmpty() || mIdle.isInstructionStackLock()) && System.currentTimeMillis()<end) {
			Thread.sleep(10);
		}
		assertTrue(mIdle.getListofInstructions().isEmpty());
		assertTrue(mIdle.isEventStreamRequested());
	}

	private void waitForRecords(int n) throws InterruptedException {
		long end = System.currentTimeMillis()+1500;
		while(mCollected.records.size()<n && System.currentTimeMillis()<end) Thread.sleep(10);
	}

	/**
	 * Finding 1, as the firmware can send it: an unsolicited status push,
	 * [ACK][0x8A][0x71][status x 2] (a dock, an undock, the button starting
	 * logging), with event records right behind it. Both records must arrive.
	 */
	@Test
	public void finding1_recordsRightAfterAnUnsolicitedStatusPushAreKept() throws Exception {
		idleOptedIn();
		// Status: RTC set (bit 2), so the driver writes no clock in answer
		mAnswering.queue(ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE, ShimmerObject.STATUS_RESPONSE, (byte) 0x04, (byte) 0x00);
		mAnswering.queue(PRESS);
		mAnswering.queue(RELEASE);
		waitForRecords(2);
		assertEquals("both records behind the status push arrive", 2, mCollected.records.size());
	}

	/**
	 * Finding 1, the case named in the review: a bare ACK nobody waits for,
	 * then two records.
	 */
	@Test
	public void finding1_twoRecordsAfterAStrayAckAreBothKept() throws Exception {
		idleOptedIn();
		mAnswering.queue(ShimmerObject.ACK_COMMAND_PROCESSED);
		mAnswering.queue(PRESS);
		mAnswering.queue(RELEASE);
		waitForRecords(2);
		assertEquals(2, mCollected.records.size());
	}

}
