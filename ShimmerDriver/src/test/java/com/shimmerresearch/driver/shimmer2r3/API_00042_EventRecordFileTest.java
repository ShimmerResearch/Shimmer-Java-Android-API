package com.shimmerresearch.driver.shimmer2r3;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * DEV-971: the event channel's records, event files, Bluetooth record frames
 * and status, decoded from the bytes in log-and-stream-common
 * docs/SHIMMER3_EVENT_CHANNEL.md §5 and docs/SHIMMER3_BT_COMMUNICATION_PROTOCOL.md
 * §7.15, which the firmware's Test/host/test_event_record.c pins its encoder
 * to. The same inputs and results as the web SDK's tests/events/record.test.ts.
 */
public class API_00042_EventRecordFileTest {

	private static byte[] hex(String s) {
		String[] parts = s.trim().split("\\s+");
		byte[] out = new byte[parts.length];
		for(int i=0;i<parts.length;i++) {
			out[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return out;
	}

	/** The worked example: a Shimmer3R, both inputs on, 2.5 minutes of logging */
	private static final byte[] WORKED_FILE = hex(
			  "53 45 56 54 01 40 10 0A 00 20 12 37 72 35 00 00 "
			+ "00 00 00 00 00 00 00 00 90 5C E4 6A 03 00 01 00 "
			+ "01 19 00 00 01 03 00 00 00 00 00 00 00 00 00 00 "
			+ "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 "
			+ "01 00 00 00 A4 D0 11 37 72 35 00 00 00 01 00 00 "
			+ "01 00 01 00 71 DD 11 37 72 35 00 00 00 00 00 00 "
			+ "01 00 02 00 EC F1 11 37 72 35 00 00 00 01 00 00 "
			+ "01 00 03 00 48 01 12 37 72 35 00 00 00 00 00 00 "
			+ "02 00 04 00 00 20 12 37 72 35 00 00 FF 00 00 00 "
			+ "01 00 05 00 33 53 18 37 72 35 00 00 00 01 00 00 "
			+ "01 00 06 00 3D 6A 18 37 72 35 00 00 00 00 00 00 "
			+ "01 01 07 00 04 20 21 37 72 35 00 00 01 02 00 00 "
			+ "01 01 08 00 4C 21 21 37 72 35 00 00 01 00 00 00 "
			+ "01 01 09 00 10 A0 28 37 72 35 00 00 01 02 00 00 "
			+ "01 01 0A 00 58 A1 28 37 72 35 00 00 01 00 00 00 "
			+ "03 00 0B 00 00 20 4E 37 72 35 00 00 FF 00 00 00 "
			+ "01 00 0C 00 00 20 5D 37 72 35 00 00 00 01 00 00 "
			+ "01 00 0D 00 14 2E 5D 37 72 35 00 00 00 00 00 00 "
			+ "01 00 0E 00 66 46 5D 37 72 35 00 00 00 01 00 00 "
			+ "01 00 0F 00 7B 54 5D 37 72 35 00 00 00 00 00 00 "
			+ "03 80 10 00 AE 87 5D 37 72 35 00 00 FF 00 00 00");

	/** The marker press from that file, as a link's second record, CRC 0x2009 */
	private static final byte[] WORKED_FRAME = hex("8A BB 10 01 00 01 00 33 53 18 37 72 35 00 00 00 01 00 00 09 20");

	@Test
	public void decodesTheHeader() throws Exception {
		EventFile file = EventFile.parse(WORKED_FILE);
		assertEquals(1, file.getVersion());
		assertEquals(64, file.getHeaderLength());
		assertEquals(16, file.getRecordLength());
		assertEquals(10, file.getHardwareId());
		assertEquals(0x357237122000L, file.getFileOpenTicks());
		assertEquals(0L, file.getRtcOffsetTicks());
		assertEquals(0x6AE45C90L, file.getConfigTime());
		assertEquals(3, file.getFirmwareId());
		assertEquals(1, file.getFirmwareMajor());
		assertEquals(1, file.getFirmwareMinor());
		assertEquals(25, file.getFirmwareInternal());
		assertEquals(0, file.getFirstSequenceNumber());
		assertEquals(EventFile.OPEN_REASON_SESSION_START, file.getOpenReason());
		assertEquals(EventRecord.INPUT_BUTTON|EventRecord.INPUT_TTL, file.getInputs());
		// 10:15:00.250 UTC on 30 Oct 2026
		EventRecord init = file.getRecords().get(4);
		assertEquals(1793355300250.0, file.getUnixMs(init), 0.001);
		assertTrue(file.belongsWithDataFile(0x357237122000L));
		assertFalse(file.belongsWithDataFile(0x357237122001L));
	}

	@Test
	public void decodesAll17RecordsComplete() throws Exception {
		EventFile file = EventFile.parse(WORKED_FILE);
		List<EventRecord> records = file.getRecords();
		assertEquals(17, records.size());
		assertEquals(0, file.getTrailingBytes());
		assertTrue(file.isComplete());
		for(int i=0;i<records.size();i++) {
			assertEquals(i, records.get(i).getSequenceNumber());
		}
	}

	@Test
	public void timesEachRecordFromTheFileOpenAsTheDocDoes() throws Exception {
		EventFile file = EventFile.parse(WORKED_FILE);
		long open = file.getFileOpenTicks();
		List<EventRecord> r = file.getRecords();
		assertEquals(-0.619995, r.get(0).getSecondsFrom(open), 1e-5);
		assertEquals(0.0, r.get(4).getSecondsFrom(open), 0.0);
		assertEquals(12.399994, r.get(5).getSecondsFrom(open), 1e-5);
		assertEquals(30.000122, r.get(7).getSecondsFrom(open), 1e-5);
		assertEquals(150.809998, r.get(16).getSecondsFrom(open), 1e-5);
	}

	@Test
	public void readsTheRecordFields() throws Exception {
		List<EventRecord> r = EventFile.parse(WORKED_FILE).getRecords();
		EventRecord press = r.get(0);
		assertEquals(EventRecord.TYPE_EDGE, press.getType());
		assertEquals(EventRecord.CHANNEL_BUTTON, press.getChannel());
		assertTrue(press.isButtonDown());
		assertFalse(press.isTtlHigh());
		assertEquals(0, press.getDropped());
		assertEquals("Button pressed", press.getDescription());
		assertEquals("Button released", r.get(1).getDescription());

		EventRecord init = r.get(4);
		assertEquals(EventRecord.TYPE_INITIAL_STATE, init.getType());
		assertEquals(EventRecord.CHANNEL_NONE, init.getChannel());
		assertEquals("Initial state", init.getDescription());

		EventRecord ttlRise = r.get(7);
		assertTrue(ttlRise.isTimerLatched());
		assertEquals("TTL high", ttlRise.getDescription());
		assertEquals("TTL low", r.get(8).getDescription());
		assertEquals("Heartbeat", r.get(11).getDescription());
		assertEquals("End of file", r.get(16).getDescription());
		assertTrue(r.get(16).isFinal());
	}

	@Test
	public void keepsAFileCutShortAndSaysSo() throws Exception {
		EventFile cut = EventFile.parse(Arrays.copyOf(WORKED_FILE, 64+16*5+7));
		assertEquals(5, cut.getRecords().size());
		assertEquals(7, cut.getTrailingBytes());
		assertFalse(cut.isComplete());
	}

	@Test
	public void skipsHeaderAndRecordBytesBeyondThoseItKnows() throws Exception {
		byte[] longer = new byte[80+20];
		System.arraycopy(WORKED_FILE, 0, longer, 0, 64);
		longer[5] = 80;
		longer[6] = 20;
		System.arraycopy(WORKED_FILE, 64, longer, 80, 16);
		EventFile f = EventFile.parse(longer);
		assertEquals(1, f.getRecords().size());
		assertEquals(0x35723711D0A4L, f.getRecords().get(0).getTicks());
	}

	@Test
	public void refusesWhatIsNotAnEventFile() {
		try {
			EventFile.parse(hex("00 01 02 03 04 05 06 07"));
			fail("not SEVT");
		} catch (EventFile.FormatException e) {
			// expected
		}
		byte[] v2 = WORKED_FILE.clone();
		v2[4] = 2;
		try {
			EventFile.parse(v2);
			fail("version 2");
		} catch (EventFile.FormatException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("version 2"));
		}
	}

	@Test
	public void findsTheCompanionOfADataFile() {
		File data = new File(new File(new File(new File("card", "data"), "Trial_1730283600"), "Shimmer_A1B2-007"), "000");
		File events = EventFile.getCompanionFile(data);
		assertEquals(new File(new File(new File(new File("card", "events"), "Trial_1730283600"), "Shimmer_A1B2-007"), "000"), events);
		assertNull(EventFile.getCompanionFile(new File("000")));
	}

	@Test
	public void decodesTheProtocolDocFrameCrcIncluded() {
		assertEquals(21, EventFrame.getFrameLength(WORKED_FRAME, 0));
		EventRecord rec = EventFrame.parse(WORKED_FRAME, 0);
		assertNotNull(rec);
		assertEquals(EventRecord.TYPE_EDGE, rec.getType());
		assertEquals(1, rec.getSequenceNumber());
		assertEquals(0x357237185333L, rec.getTicks());
		assertEquals(EventRecord.CHANNEL_BUTTON, rec.getChannel());
		assertTrue(rec.isButtonDown());
	}

	@Test
	public void rejectsAFrameWhoseCrcDoesNotCheckOut() {
		byte[] bad = WORKED_FRAME.clone();
		bad[10] ^= 0x01;
		assertEquals(EventFrame.NOT_A_FRAME, EventFrame.getFrameLength(bad, 0));
		assertNull(EventFrame.parse(bad, 0));
	}

	@Test
	public void asksForMoreUntilTheFrameIsWhole() {
		for(int n=1;n<21;n++) {
			assertEquals("after " + n + " bytes", EventFrame.NEED_MORE, EventFrame.getFrameLength(WORKED_FRAME, 0, n));
		}
	}

	@Test
	public void isNotFooledByOther0x8AMessages() {
		assertEquals(EventFrame.NOT_A_FRAME, EventFrame.getFrameLength(hex("8A 71 00 01"), 0));
		assertEquals("shorter than a record", EventFrame.NOT_A_FRAME, EventFrame.getFrameLength(hex("8A BB 05"), 0));
	}

	@Test
	public void measuresAFrameAtAnOffset() {
		byte[] buf = new byte[25];
		System.arraycopy(WORKED_FRAME, 0, buf, 4, WORKED_FRAME.length);
		assertEquals(21, EventFrame.getFrameLength(buf, 4));
	}

	@Test
	public void decodesTheNineStatusBytes() {
		EventChannelStatus s = EventChannelStatus.parse(hex("03 0B 02 05 00 02 00 01 00"), 0);
		assertEquals(3, s.getInputsEnabled());
		assertTrue(s.isStreaming());
		assertTrue(s.isFileOpen());
		assertFalse(s.isFileError());
		assertTrue(s.isTtlArmed());
		assertFalse(s.isTtlThrottled());
		assertEquals(EventRecord.INPUT_TTL, s.getState());
		assertEquals(5, s.getNextSequenceNumber());
		assertEquals(2, s.getSessionDropped());
		assertEquals(1, s.getLinkLost());
	}

	@Test(expected=IllegalArgumentException.class)
	public void theStatusNeedsAllNine() {
		EventChannelStatus.parse(hex("03 0B"), 0);
	}

	/** A data packet's 24-bit timestamp, unwrapped from the stream's start, and the record's 64-bit time */
	@Test
	public void placesARecordOnTheStreamsUnwrappedTimeline() {
		final long wrap = 1L<<24;
		long ticks = 0x357237185333L;
		EventRecord rec = new EventRecord(EventRecord.TYPE_EDGE, 0, 0, ticks, 0, 1, 0);
		long low24 = ticks&(wrap-1);
		// A stream that began in an earlier cycle, three wraps ago
		double reference = 3*wrap+low24+1000;
		assertEquals(3*wrap+low24, rec.getTicksOnStreamTimeline(reference), 0.0);
		// The latest sample a little before the edge
		assertEquals(3*wrap+low24, rec.getTicksOnStreamTimeline(3*wrap+low24-500), 0.0);
		// Across a wrap: the edge just after it, the sample just before
		EventRecord justAfterWrap = new EventRecord(EventRecord.TYPE_EDGE, 0, 0, 0x3572380000A0L, 0, 1, 0);
		assertEquals(5*wrap+0xA0, justAfterWrap.getTicksOnStreamTimeline(5*wrap-200), 0.0);
	}

}
