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
 * DEV-971: a host that opts in with SET_FEATURE / FEATURE_EVENT_STREAM gets
 * every button and TTL record as a frame, {@code [0x8A][0xBB][0x10][record][CRC-16]},
 * with no ACK before it and its own CRC whatever the link's CRC mode
 * (log-and-stream-common docs/SHIMMER3_BT_COMMUNICATION_PROTOCOL.md §7.15).
 * Frames come between data packets while streaming, straight after the opt-in's
 * ACK, and when the Shimmer is idle, including before a command's ACK or reply.
 * <p>
 * Before this the driver knew no 0x8A subtype 0xBB: between data packets it took
 * the frame for a parse error and threw it away with whatever followed it until
 * the next packet boundary, and when idle it cleared the serial buffer.
 * <p>
 * No hardware. The streaming tests hand the parser one byte at a time, as in
 * API_00037_StreamingLogStartRefusedTest; the idle tests run the IOThread over a
 * radio that answers as the firmware does, as in API_00038_EventChannelProbeTest.
 */
public class API_00043_EventStreamTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;
	private static final ShimmerVerObject S3R_V1_1_25 = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);

	private static byte[] hex(String s) {
		String[] parts = s.trim().split("\\s+");
		byte[] out = new byte[parts.length];
		for(int i=0;i<parts.length;i++) {
			out[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return out;
	}

	/** The marker press from the protocol doc, as a link's second record, CRC 0x2009 */
	private static final byte[] MARKER_PRESS = hex("8A BB 10 01 00 01 00 33 53 18 37 72 35 00 00 00 01 00 00 09 20");

	/** A record frame with its own CRC */
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

	/** The first record after an opt-in: the inputs' state then */
	private static final byte[] INITIAL_STATE = frame(EventRecord.TYPE_INITIAL_STATE, 0, 0x357237100000L, EventRecord.CHANNEL_NONE, 0);
	private static final byte[] TTL_RISE = frame(EventRecord.TYPE_EDGE, 2, 0x357237200000L, EventRecord.CHANNEL_TTL, EventRecord.INPUT_TTL);

	private static class Collected implements EventChannelListener {
		final List<EventRecord> records = Collections.synchronizedList(new ArrayList<EventRecord>());
		final List<EventChannelStatus> statuses = Collections.synchronizedList(new ArrayList<EventChannelStatus>());

		@Override
		public void onEventRecord(ShimmerBluetooth shimmer, EventRecord record) {
			records.add(record);
		}

		@Override
		public void onEventChannelStatus(ShimmerBluetooth shimmer, EventChannelStatus status) {
			statuses.add(status);
		}

		List<Integer> sequenceNumbers() {
			List<Integer> seqs = new ArrayList<Integer>();
			synchronized(records) {
				for(EventRecord r:records) {
					seqs.add(r.getSequenceNumber());
				}
			}
			return seqs;
		}
	}

	private final Collected mCollected = new Collected();

	// ------------------------------------------------------------------
	// While streaming
	// ------------------------------------------------------------------

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

	private ShimmerPC mStreamingDevice;
	private QueueRadio mQueueRadio;

	private void streamingShimmer3r(BT_CRC_MODE crcMode) {
		mStreamingDevice = new ShimmerPC("COM99");
		mQueueRadio = new QueueRadio();
		mStreamingDevice.setTestRadio(mQueueRadio);
		mStreamingDevice.setShimmerVersionObjectAndCreateSensorMap(S3R_V1_1_25);
		mStreamingDevice.setPacketSize(PACKET_SIZE);
		mStreamingDevice.setCurrentBtCommsCrcMode(crcMode);
		mStreamingDevice.setIsStreaming(true);
		mStreamingDevice.setBluetoothRadioState(BT_STATE.STREAMING);
		// Queue each parsed packet rather than build an ObjectCluster from it
		mStreamingDevice.mUseProcessingThread = true;
		mStreamingDevice.addEventChannelListener(mCollected);
	}

	/** writeEventStream(true) while streaming, and the IO thread sending it */
	private void optInWhileStreaming() {
		assertTrue(mStreamingDevice.writeEventStream(true));
		assertEquals(1, mStreamingDevice.getListofInstructions().size());
		assertArrayEquals(new byte[] {ShimmerObject.SET_FEATURE, (byte) SHIMMER_FEATURE.EVENT_STREAM.ordinal(), 1},
				mStreamingDevice.getListofInstructions().get(0));
		mStreamingDevice.mCurrentCommand = ShimmerObject.SET_FEATURE;
		mStreamingDevice.setInstructionStackLock(true);
		mStreamingDevice.mWaitForAck = true;
		mStreamingDevice.startTimerCheckForAckOrResp(2);
	}

	private void receive(byte[]... frames) {
		for(byte[] frame:frames){
			mQueueRadio.queue(frame);
		}
		while(!mQueueRadio.rx.isEmpty()){
			mStreamingDevice.processWhileStreaming();
		}
	}

	/** Has a 0x00, a 0x8A, a 0xFE and a 0xFF in it */
	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, (byte) 0xFF, (byte) 0x8A, (byte) 0xFE, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(byte[] payload, BT_CRC_MODE crcMode) {
		byte[] frame = new byte[1+payload.length];
		frame[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, frame, 1, payload.length);
		return withCrc(frame, crcMode);
	}

	private static byte[] bareAck(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, crcMode);
	}

	private static byte[] withCrc(byte[] frame, BT_CRC_MODE crcMode) {
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(frame, frame.length);
		byte[] framed = Arrays.copyOf(frame, frame.length+crcMode.getNumCrcBytes());
		System.arraycopy(crc, 0, framed, frame.length, crcMode.getNumCrcBytes());
		return framed;
	}

	private static String hexOf(byte[] bytes) {
		return UtilShimmer.bytesToHexStringWithSpacesFormatted(bytes);
	}

	private void assertParsed(byte[]... expectedPayloads) {
		List<String> expected = new ArrayList<String>();
		for(byte[] payload:expectedPayloads){
			expected.add(hexOf(payload));
		}
		List<String> parsed = new ArrayList<String>();
		ShimmerBluetooth device = mStreamingDevice;
		for(RawBytePacketWithPCTimeStamp packet:device.mABQPacketByeArray){
			parsed.add(hexOf(packet.mDataArray));
		}
		assertEquals("every data packet is parsed, once and in order", expected, parsed);
	}

	private void assertOptInAcked() {
		assertFalse("the opt-in's ACK is taken", mStreamingDevice.mWaitForAck);
		assertFalse(mStreamingDevice.isInstructionStackLock());
		assertTrue(mStreamingDevice.getListofInstructions().isEmpty());
		assertNull("the ACK timer is cancelled", mStreamingDevice.mTimerCheckForAckOrResp);
		assertTrue(mStreamingDevice.isStreaming());
	}

	/** The opt-in's ACK between two packets, then its first record, then more packets and records */
	private void optInMidStream(BT_CRC_MODE crcMode) {
		streamingShimmer3r(crcMode);
		optInWhileStreaming();
		receive(dataPacket(payload(1), crcMode), bareAck(crcMode), INITIAL_STATE,
				dataPacket(payload(2), crcMode), MARKER_PRESS,
				dataPacket(payload(3), crcMode), TTL_RISE, MARKER_PRESS,
				dataPacket(payload(4), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertOptInAcked();
		assertParsed(payload(1), payload(2), payload(3), payload(4));
		assertEquals(Arrays.asList(0, 1, 2, 1), mCollected.sequenceNumbers());
		EventRecord press = mCollected.records.get(1);
		assertEquals(0x357237185333L, press.getTicks());
		assertTrue(press.isButtonDown());
		assertEquals(4, mStreamingDevice.getEventFramesReceived());
		assertEquals(0, mStreamingDevice.getEventFramesBadCrc());
		assertArrayEquals(new byte[] {ShimmerObject.DATA_PACKET}, mStreamingDevice.mByteArrayOutputStream.toByteArray());
	}

	@After
	public void stopStreamingTimers() {
		if(mStreamingDevice!=null){
			mStreamingDevice.stopTimerCheckForAckOrResp();
			mStreamingDevice.stopTimerReadStatus();
		}
	}

	@Test
	public void framesBetweenPacketsWithoutALinkCrc() {
		optInMidStream(BT_CRC_MODE.OFF);
	}

	@Test
	public void framesBetweenPacketsWithAOneByteLinkCrc() {
		optInMidStream(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void framesBetweenPacketsWithATwoByteLinkCrc() {
		optInMidStream(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/** Data packets paused (the unit closing its SD file, say): the opt-in's ACK and its record at the start of the buffer */
	@Test
	public void theOptInsAckAndRecordWithNoPacketBeforeThem() {
		streamingShimmer3r(BT_CRC_MODE.OFF);
		optInWhileStreaming();
		receive(bareAck(BT_CRC_MODE.OFF), INITIAL_STATE, dataPacket(payload(1), BT_CRC_MODE.OFF),
				dataPacket(payload(2), BT_CRC_MODE.OFF), new byte[] {ShimmerObject.DATA_PACKET});
		assertOptInAcked();
		assertParsed(payload(1), payload(2));
		assertEquals(Arrays.asList(0), mCollected.sequenceNumbers());
	}

	@Test
	public void aFrameWhoseCrcFailsIsDroppedAndTheStreamGoesOn() {
		BT_CRC_MODE crcMode = BT_CRC_MODE.TWO_BYTE_CRC;
		streamingShimmer3r(crcMode);
		optInWhileStreaming();
		byte[] corrupt = MARKER_PRESS.clone();
		corrupt[10] ^= 0x01;
		receive(dataPacket(payload(1), crcMode), bareAck(crcMode), INITIAL_STATE,
				dataPacket(payload(2), crcMode), corrupt,
				dataPacket(payload(3), crcMode), dataPacket(payload(4), crcMode), TTL_RISE,
				dataPacket(payload(5), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertEquals("the corrupt frame is not taken", Arrays.asList(0, 2), mCollected.sequenceNumbers());
		assertEquals(1, mStreamingDevice.getEventFramesBadCrc());
		List<String> parsed = new ArrayList<String>();
		ShimmerBluetooth device = mStreamingDevice;
		for(RawBytePacketWithPCTimeStamp packet:device.mABQPacketByeArray){
			parsed.add(hexOf(packet.mDataArray));
		}
		assertTrue(parsed.contains(hexOf(payload(1))));
		assertTrue(parsed.contains(hexOf(payload(2))));
		assertTrue("the parser resyncs after it", parsed.contains(hexOf(payload(4))));
		assertTrue(parsed.contains(hexOf(payload(5))));
		assertFalse("no packet is made up from the frame's bytes", parsed.size()>5);
	}

	/** The protocol says never to parse these frames without opting in, and the driver doesn't */
	@Test
	public void framesAreNotParsedWithoutAnOptIn() {
		streamingShimmer3r(BT_CRC_MODE.TWO_BYTE_CRC);
		receive(dataPacket(payload(1), BT_CRC_MODE.TWO_BYTE_CRC), MARKER_PRESS,
				dataPacket(payload(2), BT_CRC_MODE.TWO_BYTE_CRC), new byte[] {ShimmerObject.DATA_PACKET});
		assertTrue(mCollected.records.isEmpty());
		assertEquals(0, mStreamingDevice.getEventFramesReceived());
	}

	// ------------------------------------------------------------------
	// Not streaming, with the IOThread running
	// ------------------------------------------------------------------

	/** Answers as LogAndStream_Shimmer3R v1.01.026 does, each reply one burst */
	private static class AnsweringRadio extends WaitingTestRadio {
		/** Sent ahead of the next command's ACK, as a record already queued would be */
		volatile byte[] beforeNextAck = null;
		/** Sent between the next status command's ACK and its reply */
		volatile byte[] betweenAckAndReply = null;

		@Override
		byte[] answer(byte[] buffer) {
			java.io.ByteArrayOutputStream reply = new java.io.ByteArrayOutputStream();
			byte[] early = beforeNextAck;
			if(early!=null) {
				beforeNextAck = null;
				reply.write(early, 0, early.length);
			}
			if(buffer[0]==ShimmerObject.SET_FEATURE && buffer[1]==(byte) SHIMMER_FEATURE.EVENT_STREAM.ordinal()){
				reply.write(ShimmerObject.ACK_COMMAND_PROCESSED);
				if(buffer[2]!=0) {
					// The first record at the opt-in, then one already made
					reply.write(INITIAL_STATE, 0, INITIAL_STATE.length);
					reply.write(MARKER_PRESS, 0, MARKER_PRESS.length);
				}
			}
			else if(buffer[0]==ShimmerObject.GET_EVENT_CHANNEL_STATUS_COMMAND){
				reply.write(ShimmerObject.ACK_COMMAND_PROCESSED);
				byte[] between = betweenAckAndReply;
				if(between!=null) {
					betweenAckAndReply = null;
					reply.write(between, 0, between.length);
				}
				reply.write(ShimmerObject.EVENT_CHANNEL_STATUS_RESPONSE);
				byte[] status = hex("03 0B 02 05 00 02 00 01 00");
				reply.write(status, 0, status.length);
			}
			else {
				reply.write(ShimmerObject.ACK_COMMAND_PROCESSED);
			}
			return reply.toByteArray();
		}
	}

	private ShimmerPC mIdleDevice;
	private AnsweringRadio mAnsweringRadio;

	@After
	public void disconnectIdleDevice() {
		if(mIdleDevice!=null){
			mIdleDevice.stopTimerCheckForAckOrResp();
			mIdleDevice.disconnectNoException();
		}
	}

	/** Connected, not streaming, the IOThread running, and the event channel probe answered */
	private void idleShimmerWithTheEventChannel() throws InterruptedException {
		mAnsweringRadio = new AnsweringRadio();
		mIdleDevice = new ShimmerPC("COM99");
		mIdleDevice.setTestRadio(mAnsweringRadio);
		mIdleDevice.setShimmerVersionObjectAndCreateSensorMap(S3R_V1_1_25);
		mIdleDevice.setIsInitialised(true);
		mIdleDevice.addEventChannelListener(mCollected);
		mIdleDevice.mIOThread = mIdleDevice.new IOThread();
		mIdleDevice.mIOThread.start();
		mIdleDevice.readEventChannelSupport();
		waitForInstructionsDone();
		assertEquals(Boolean.TRUE, mIdleDevice.isEventChannelSupported());
	}

	private void waitForInstructionsDone() throws InterruptedException {
		long end = System.currentTimeMillis()+2000;
		while((!mIdleDevice.getListofInstructions().isEmpty() || mIdleDevice.isInstructionStackLock()) && System.currentTimeMillis()<end){
			Thread.sleep(10);
		}
		assertTrue("every instruction answered", mIdleDevice.getListofInstructions().isEmpty());
	}

	private void waitForRecords(int count) throws InterruptedException {
		long end = System.currentTimeMillis()+2000;
		while(mCollected.records.size()<count && System.currentTimeMillis()<end){
			Thread.sleep(10);
		}
	}

	@Test
	public void anIdleOptInTakesItsRecordsAndKeepsBackToBackFrames() throws Exception {
		idleShimmerWithTheEventChannel();
		assertTrue(mIdleDevice.writeEventStream(true));
		waitForInstructionsDone();
		waitForRecords(2);
		assertEquals(Arrays.asList(0, 1), mCollected.sequenceNumbers());
		assertTrue(mIdleDevice.isEventStreamRequested());

		// Pressed later, with nothing in flight
		mAnsweringRadio.queue(TTL_RISE);
		waitForRecords(3);
		assertEquals(Arrays.asList(0, 1, 2), mCollected.sequenceNumbers());
	}

	@Test
	public void aFrameBeforeACommandsAckAndBeforeAReply() throws Exception {
		idleShimmerWithTheEventChannel();
		assertTrue(mIdleDevice.writeEventStream(true));
		waitForInstructionsDone();
		waitForRecords(2);

		mAnsweringRadio.beforeNextAck = TTL_RISE;
		mAnsweringRadio.betweenAckAndReply = frame(EventRecord.TYPE_EDGE, 3, 0x357237300000L, EventRecord.CHANNEL_TTL, 0);
		assertTrue(mIdleDevice.readEventChannelStatus());
		waitForInstructionsDone();
		waitForRecords(4);
		assertEquals(Arrays.asList(0, 1, 2, 3), mCollected.sequenceNumbers());
		assertEquals("the status still arrives", 1, mCollected.statuses.size());
		EventChannelStatus status = mIdleDevice.getEventChannelStatus();
		assertNotNull(status);
		assertTrue(status.isStreaming());
		assertEquals(5, status.getNextSequenceNumber());
		assertEquals(1, status.getLinkLost());
	}

	@Test
	public void theStatusIsNotAskedOfFirmwareWithoutTheEventChannelOrWhileStreaming() {
		ShimmerPC unknown = new ShimmerPC("COM98");
		unknown.setShimmerVersionObjectAndCreateSensorMap(S3R_V1_1_25);
		assertFalse("not yet known to have it", unknown.readEventChannelStatus());

		ShimmerPC older = new ShimmerPC("COM97");
		older.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 51));
		assertFalse("older than SET_FEATURE", older.writeEventStream(true));
		assertTrue(older.getListofInstructions().isEmpty());
	}

}
