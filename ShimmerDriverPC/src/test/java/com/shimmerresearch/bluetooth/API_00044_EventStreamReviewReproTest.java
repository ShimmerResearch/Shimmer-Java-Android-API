package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

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
 * DEV-971: the parser defects found in #348's review, each first reproduced by
 * a test here that failed on #348's code, then fixed; and, after #349's
 * review, the same cases with a 1-byte and a 2-byte link CRC, since the driver
 * turns the link CRC on at connect where the firmware has it.
 * <p>
 * The streaming tests hand the parser one byte at a time on the test's thread.
 * The idle tests run the IOThread over {@link WaitingTestRadio}, whose bursts
 * arrive whole and whose reads wait, so the driver's reads cannot race the
 * test's writes.
 */
public class API_00044_EventStreamReviewReproTest {

	private static final int PACKET_SIZE = 9;
	private static final ShimmerVerObject S3R = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);
	private static final BT_CRC_MODE[] CRC_MODES = {BT_CRC_MODE.OFF, BT_CRC_MODE.ONE_BYTE_CRC, BT_CRC_MODE.TWO_BYTE_CRC};

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

	private static final byte[] INITIAL_STATE = frame(EventRecord.TYPE_INITIAL_STATE, 0, 0x357237100000L, EventRecord.CHANNEL_NONE, 0);
	private static final byte[] PRESS = frame(EventRecord.TYPE_EDGE, 1, 0x357237185333L, EventRecord.CHANNEL_BUTTON, 1);
	private static final byte[] RELEASE = frame(EventRecord.TYPE_EDGE, 2, 0x357237186333L, EventRecord.CHANNEL_BUTTON, 0);

	/** A reply as the firmware frames it on a link with {@code crcMode}: its bytes, then the link CRC over them */
	private static byte[] withLinkCrc(byte[] bytes, BT_CRC_MODE crcMode) {
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(bytes, bytes.length);
		byte[] out = Arrays.copyOf(bytes, bytes.length+crcMode.getNumCrcBytes());
		System.arraycopy(crc, 0, out, bytes.length, crcMode.getNumCrcBytes());
		return out;
	}

	private static class Collected implements EventChannelListener {
		final List<EventRecord> records = Collections.synchronizedList(new ArrayList<EventRecord>());
		final List<EventChannelStatus> statuses = Collections.synchronizedList(new ArrayList<EventChannelStatus>());
		@Override public void onEventRecord(ShimmerBluetooth shimmer, EventRecord record) { records.add(record); }
		@Override public void onEventChannelStatus(ShimmerBluetooth shimmer, EventChannelStatus status) { statuses.add(status); }
		void clear() { records.clear(); statuses.clear(); }
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
		if(mStreaming!=null) {
			mStreaming.stopTimerCheckForAckOrResp();
			mStreaming.stopTimerReadStatus();
		}
		mCollected.clear();
		mAcksProcessed.clear();
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
		return withLinkCrc(f, crcMode);
	}

	private static String hex(byte[] b) {
		return UtilShimmer.bytesToHexStringWithSpacesFormatted(b);
	}

	private List<String> parsed() {
		List<String> out = new ArrayList<String>();
		ShimmerBluetooth d = mStreaming;
		for(RawBytePacketWithPCTimeStamp p:d.mABQPacketByeArray) out.add(hex(p.mDataArray));
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
	 * that went last must not have its ACK handling run a second time. No link
	 * CRC: with one, a bare ACK goes through the older bare-ACK branch, which
	 * acknowledges unguarded for a data packet too (not changed here).
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
		assertEquals(Arrays.asList(hex(payload(1)), hex(payload(2))), parsed());
	}

	/**
	 * Finding 3: no link CRC, opted in, and a byte lost from a packet, so the
	 * parser's boundary falls one byte late and lands on a data byte of 0x8A.
	 * The misframed bytes must not be handed on as a packet, and the parser must
	 * resync so that the good packets after them still arrive.
	 */
	@Test
	public void finding3_withoutACrcAMisframedPacketBeforeA0x8ADataByteIsNotTakenAsAPacket() {
		streamingShimmer3r(BT_CRC_MODE.OFF);
		alreadyOptedIn();
		byte[] good = dataPacket(payload(1), BT_CRC_MODE.OFF);
		// Packet 2 loses its 4th byte on the link
		byte[] p2 = new byte[] {0x20, (byte) 0x8A, 0x01, 0x02, 0x07, 0x05, 0x08, 0x3F, 0x09};
		byte[] p2Lost = new byte[1+p2.length-1];
		p2Lost[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(p2, 0, p2Lost, 1, 3);
		System.arraycopy(p2, 4, p2Lost, 4, p2.length-4);
		// Packet 3's first data byte is 0x8A, where the late boundary lands
		byte[] p3Payload = new byte[] {(byte) 0x8A, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x01};
		receive(good, p2Lost, dataPacket(p3Payload, BT_CRC_MODE.OFF), dataPacket(payload(4), BT_CRC_MODE.OFF),
				dataPacket(payload(5), BT_CRC_MODE.OFF), new byte[] {ShimmerObject.DATA_PACKET});
		byte[] misframed = new byte[PACKET_SIZE];
		System.arraycopy(p2Lost, 1, misframed, 0, p2Lost.length-1);
		misframed[PACKET_SIZE-1] = ShimmerObject.DATA_PACKET;
		List<String> got = parsed();
		assertFalse("misframed bytes " + hex(misframed) + " were taken as a packet: " + got, got.contains(hex(misframed)));
		assertEquals("the good packets either side arrive, the damaged one does not",
				Arrays.asList(hex(payload(1)), hex(p3Payload), hex(payload(4)), hex(payload(5))), got);
	}

	/**
	 * Finding 5, the consequence, with each link CRC: a status reply,
	 * [ACK][0xBA][9], between data packets is taken, the command retired, the
	 * lock released and the packets either side parsed.
	 */
	@Test
	public void finding5_aStatusReplyBetweenDataPacketsIsTaken() {
		for(BT_CRC_MODE crcMode:CRC_MODES) {
			streamingShimmer3r(crcMode);
			mStreaming.mCurrentCommand = ShimmerObject.GET_EVENT_CHANNEL_STATUS_COMMAND;
			mStreaming.getListofInstructions().add(new byte[] {ShimmerObject.GET_EVENT_CHANNEL_STATUS_COMMAND});
			mStreaming.setInstructionStackLock(true);
			mStreaming.mWaitForAck = true;
			mStreaming.startTimerCheckForAckOrResp(2);
			byte[] reply = withLinkCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.EVENT_CHANNEL_STATUS_RESPONSE,
					0x03, 0x0B, 0x02, 0x05, 0x00, 0x02, 0x00, 0x01, 0x00}, crcMode);
			receive(dataPacket(payload(1), crcMode), reply, dataPacket(payload(2), crcMode),
					dataPacket(payload(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			String m = crcMode + ": ";
			assertEquals(m + "the status reply is taken", 1, mCollected.statuses.size());
			assertEquals(m + "its values", 5, mCollected.statuses.get(0).getNextSequenceNumber());
			assertFalse(m + "no longer waits for the ACK", mStreaming.mWaitForAck);
			assertTrue(m + "the command is retired", mStreaming.getListofInstructions().isEmpty());
			assertFalse(m + "the next instruction can be sent", mStreaming.isInstructionStackLock());
			assertNull(m + "the ACK timer is cancelled", mStreaming.mTimerCheckForAckOrResp);
			assertEquals(m + "the packets either side are parsed",
					Arrays.asList(hex(payload(1)), hex(payload(2)), hex(payload(3))), parsed());
		}
	}

	/**
	 * Finding 5, the precondition: readEventChannelStatus() is not queued
	 * behind a start of streaming that has not been acknowledged yet.
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
		assertFalse("refused", sent);
		assertFalse("the status is queued after a start of streaming", queuedAfterStart);
	}

	// ------------------------------------------------------------------
	// Idle, with the IOThread running
	// ------------------------------------------------------------------

	/** Answers every command with an ACK, with its link CRC, then {@link #afterOptInAck} for the opt-in, all in one burst */
	private static class AckingRadio extends WaitingTestRadio {
		volatile BT_CRC_MODE crcMode = BT_CRC_MODE.OFF;
		volatile byte[] afterOptInAck = new byte[0];

		@Override
		byte[] answer(byte[] command) {
			byte[] ack = withLinkCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, crcMode);
			if(command[0]==ShimmerObject.SET_FEATURE && command[1]==(byte) SHIMMER_FEATURE.EVENT_STREAM.ordinal() && command[2]!=0) {
				return concat(ack, afterOptInAck);
			}
			return ack;
		}
	}

	private ShimmerPC mIdle;
	private AckingRadio mAcking;

	/** Connected over a link with {@code crcMode}, not streaming, opted in; {@code afterOptInAck} comes in the opt-in's ACK's burst */
	private void idleOptedIn(BT_CRC_MODE crcMode, byte[] afterOptInAck) throws InterruptedException {
		if(mIdle!=null) {
			mIdle.stopTimerCheckForAckOrResp();
			mIdle.disconnectNoException();
		}
		mCollected.clear();
		mAcking = new AckingRadio();
		mAcking.crcMode = crcMode;
		mAcking.afterOptInAck = afterOptInAck;
		mIdle = new ShimmerPC("COM97");
		mIdle.setTestRadio(mAcking);
		mIdle.setShimmerVersionObjectAndCreateSensorMap(S3R);
		mIdle.setCurrentBtCommsCrcMode(crcMode);
		mIdle.setIsInitialised(true);
		mIdle.addEventChannelListener(mCollected);
		mIdle.mIOThread = mIdle.new IOThread();
		mIdle.mIOThread.start();
		assertTrue(mIdle.writeEventStream(true));
		long end = System.currentTimeMillis()+2000;
		while((!mIdle.getListofInstructions().isEmpty() || mIdle.isInstructionStackLock()) && System.currentTimeMillis()<end) {
			Thread.sleep(10);
		}
		assertTrue(crcMode + ": the opt-in is answered", mIdle.getListofInstructions().isEmpty());
	}

	private void waitForRecords(int n) throws InterruptedException {
		long end = System.currentTimeMillis()+1500;
		while(mCollected.records.size()<n && System.currentTimeMillis()<end) Thread.sleep(10);
		Thread.sleep(50); // and long enough to see an extra one
	}

	/**
	 * The opt-in, with each link CRC: the firmware ACKs it, with the ACK's link
	 * CRC, and sends the first record straight after, often in the same burst.
	 * Both records in that burst must arrive.
	 */
	@Test
	public void theRecordsInTheOptInsOwnBurstArriveWithEachLinkCrc() throws Exception {
		for(BT_CRC_MODE crcMode:CRC_MODES) {
			idleOptedIn(crcMode, WaitingTestRadio.concat(INITIAL_STATE, PRESS));
			waitForRecords(2);
			assertEquals(crcMode + ": both records in the opt-in's burst arrive", 2, mCollected.records.size());
		}
	}

	/**
	 * Finding 1, as the firmware sends it, with each link CRC: an unsolicited
	 * status push, [ACK][0x8A][0x71][status x 2] and its link CRC (on a dock, an
	 * undock, a press that starts logging), with two records right behind it in
	 * the same burst. Both records must arrive.
	 */
	@Test
	public void finding1_recordsRightAfterAnUnsolicitedStatusPushAreKept() throws Exception {
		for(BT_CRC_MODE crcMode:CRC_MODES) {
			idleOptedIn(crcMode, new byte[0]);
			// Status: RTC set (bit 2), so the driver writes no clock in answer
			byte[] push = withLinkCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
					ShimmerObject.STATUS_RESPONSE, (byte) 0x04, (byte) 0x00}, crcMode);
			mAcking.queue(push, PRESS, RELEASE);
			waitForRecords(2);
			assertEquals(crcMode + ": both records behind the status push arrive", 2, mCollected.records.size());
		}
	}

	/**
	 * Finding 1's other half, with each link CRC: a frame read straight after an
	 * ACK has no link CRC after it (it carries its own), so none is cleared and
	 * the record behind it is intact. The ACK here has no CRC of its own: this
	 * exercises that branch, not a sequence the firmware is known to send.
	 */
	@Test
	public void finding1_noLinkCrcIsClearedAfterAFrameReadBehindAnAck() throws Exception {
		for(BT_CRC_MODE crcMode:CRC_MODES) {
			idleOptedIn(crcMode, new byte[0]);
			mAcking.queue(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, PRESS, RELEASE);
			waitForRecords(2);
			assertEquals(crcMode + ": both records arrive", 2, mCollected.records.size());
		}
	}

}
