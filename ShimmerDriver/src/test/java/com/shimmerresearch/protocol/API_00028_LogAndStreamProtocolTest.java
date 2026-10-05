package com.shimmerresearch.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The LogAndStream protocol state machine on hand-written bytes: framing, failure paths and
 * hardware differences that a good recorded Shimmer3R session never exercises. No device,
 * thread or clock is involved.
 */
public class API_00028_LogAndStreamProtocolTest {

	private static final long T0 = 1790960000000L;
	/** When the handshake starts on a link that stays quiet. */
	private static final long T1 = T0 + LogAndStreamProtocol.SETTLE_MS;
	private static final byte[] GET_SHIMMER_VERSION = { 0x3F };
	private static final byte[] GET_FW_VERSION = { 0x2E };
	private static final byte[] STOP_STREAMING = { 0x20 };
	private static final byte[] SET_CRC_OFF = { (byte) 0x8B, 0x00 };
	/** ACK, GET_SHIMMER_VERSION_RESPONSE, hardware version 10 (Shimmer3R). */
	private static final byte[] SHIMMER3R_VERSION_REPLY = { (byte) 0xFF, 0x25, 0x0A };
	/** Stands in for data packets from a stream left running; their content does not matter. */
	private static final byte[] STREAM_BYTES = { 0x00, 0x12, 0x34, 0x56, (byte) 0xFF, 0x00, 0x78 };

	private static List<String> messages(ProtocolOutput out, ProtocolEvent.Type type) {
		List<String> found = new ArrayList<String>();
		for (ProtocolEvent e : out.getEvents()) {
			if (e.type == type) {
				found.add(e.message);
			}
		}
		return found;
	}

	/** Connects on a quiet link and starts the handshake, as the host's first due tick does. */
	private static ProtocolOutput connect(LogAndStreamProtocol p) {
		assertTrue("nothing is written until the link is quiet", p.connect(T0).getWrites().isEmpty());
		return p.tick(T1);
	}

	/** ACK, FW_VERSION_RESPONSE, LogAndStream (ID 3) at major.minor.internal. */
	private static byte[] logAndStreamVersionReply(int major, int minor, int internal) {
		return new byte[] { (byte) 0xFF, 0x2F, 0x03, 0x00, (byte) major, 0x00, (byte) minor, (byte) internal };
	}

	/**
	 * Takes a device through the handshake up to the command after the config bytes, and returns
	 * that command: either the pressure coefficients (0xA7) or the calibration dump (0x9A).
	 * Replies after SET_CRC carry a checksum byte; replies' checksums are not enforced, so its
	 * value does not matter here.
	 */
	private static byte[] commandAfterTheConfigBytes(int hardware, int minor, int internal, byte[] expansionBoard) {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);
		long t = T1;
		p.receive(new byte[] { (byte) 0xFF, 0x25, (byte) hardware }, t += 10);
		p.receive(logAndStreamVersionReply(1, minor, internal), t += 10);
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x65, 0x03, expansionBoard[0], expansionBoard[1], expansionBoard[2] }, t += 10);
		assertArrayEquals("SET_CRC", new byte[] { (byte) 0x8B, 0x01 }, out.getWrites().get(0));
		out = p.receive(new byte[] { (byte) 0xFF, 0x00 }, t += 10);
		for (int chunk = 0; chunk < 3; chunk++) {
			assertEquals("INFOMEM read " + chunk, (byte) 0x8E, out.getWrites().get(0)[0]);
			byte[] reply = new byte[3 + 128 + 1];
			reply[0] = (byte) 0xFF;
			reply[1] = (byte) 0x8D;
			reply[2] = (byte) 0x80;
			Arrays.fill(reply, 3, 3 + 128, (byte) 0xFF); // blank config bytes
			out = p.receive(reply, t += 10);
		}
		assertTrue(messages(out, ProtocolEvent.Type.ERROR).isEmpty());
		return out.getWrites().get(0);
	}

	@Test
	public void aShimmer3IsAccepted() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x25, 0x03 }, T1 + 10);

		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void shimmer3FirmwareOlderThanV1_1_3IsRefused() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);
		p.receive(new byte[] { (byte) 0xFF, 0x25, 0x03 }, T1 + 10);

		ProtocolOutput out = p.receive(logAndStreamVersionReply(1, 1, 2), T1 + 20);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("older than LogAndStream v1.1.3"));
		assertEquals(LogAndStreamProtocol.State.FAILED, p.getState());
	}

	@Test
	public void shimmer3FirmwareV1_1_3IsAccepted() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);
		p.receive(new byte[] { (byte) 0xFF, 0x25, 0x03 }, T1 + 10);

		ProtocolOutput out = p.receive(logAndStreamVersionReply(1, 1, 3), T1 + 20);

		assertArrayEquals("GET_DAUGHTER_CARD_ID", new byte[] { 0x66, 0x03, 0x00 }, out.getWrites().get(0));
	}

	@Test
	public void aShimmer3RWithABmp581OnV1_1_6IsNotAskedForPressureCoefficients() {
		// SR31-11.2 carries a BMP581 by SR number; LogAndStream_Shimmer3R v1.01.006 NACKs the command there.
		byte[] bmp581Board = { 31, 11, 2 };
		byte[] bmp390Board = { 31, 11, 1 };

		assertEquals("v1.1.6, BMP581: skipped", (byte) 0x9A, commandAfterTheConfigBytes(10, 1, 6, bmp581Board)[0]);
		assertEquals("v1.1.7, BMP581: asked", (byte) 0xA7, commandAfterTheConfigBytes(10, 1, 7, bmp581Board)[0]);
		assertEquals("v1.1.6, BMP390: asked", (byte) 0xA7, commandAfterTheConfigBytes(10, 1, 6, bmp390Board)[0]);
		assertEquals("Shimmer3 v1.1.3: asked", (byte) 0xA7, commandAfterTheConfigBytes(3, 1, 3, bmp390Board)[0]);
	}

	@Test
	public void connectWaitsForAQuietLinkThenAsksForTheHardwareVersion() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		assertTrue(p.connect(T0).getWrites().isEmpty());
		assertEquals(T1, p.nextDeadline());
		assertTrue(p.tick(T1 - 1).isEmpty());

		ProtocolOutput out = p.tick(T1);

		assertEquals(1, out.getWrites().size());
		assertArrayEquals(GET_SHIMMER_VERSION, out.getWrites().get(0));
		assertTrue("a quiet link reports nothing", messages(out, ProtocolEvent.Type.DISCARDED).isEmpty());
		assertEquals(LogAndStreamProtocol.State.CONNECTING, p.getState());
		assertEquals(T1 + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS, p.nextDeadline());
	}

	@Test
	public void aDeviceLeftStreamingIsStoppedBeforeTheHandshake() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		p.connect(T0);

		ProtocolOutput first = p.receive(STREAM_BYTES, T0 + 10);
		assertEquals("stopped once", 1, first.getWrites().size());
		assertArrayEquals(STOP_STREAMING, first.getWrites().get(0));
		// More stream, and the STOP_STREAMING ACK, push the start of the handshake back.
		assertTrue(p.receive(STREAM_BYTES, T0 + 100).getWrites().isEmpty());
		long quiet = T0 + 100 + LogAndStreamProtocol.SETTLE_MS;
		assertEquals(quiet, p.nextDeadline());
		assertTrue(p.tick(quiet - 1).isEmpty());

		ProtocolOutput out = p.tick(quiet);

		assertTrue(messages(out, ProtocolEvent.Type.DISCARDED).get(0).contains("dropped " + 2 * STREAM_BYTES.length + " byte(s)"));
		// The earlier session's checksums go first, then the handshake starts as usual.
		assertArrayEquals(SET_CRC_OFF, out.getWrites().get(0));
		assertArrayEquals(GET_SHIMMER_VERSION, p.receive(new byte[] { (byte) 0xFF }, quiet + 10).getWrites().get(0));
		// None of the dropped bytes is mistaken for the reply.
		assertArrayEquals(GET_FW_VERSION, p.receive(SHIMMER3R_VERSION_REPLY, quiet + 20).getWrites().get(0));
	}

	@Test
	public void firmwareThatDoesNotAckChecksumsOffIsNotFailed() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		p.connect(T0);
		p.receive(STREAM_BYTES, T0 + 10);
		long quiet = T0 + 10 + LogAndStreamProtocol.SETTLE_MS;
		assertArrayEquals(SET_CRC_OFF, p.tick(quiet).getWrites().get(0));

		ProtocolOutput out = p.tick(quiet + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).isEmpty());
		assertTrue(messages(out, ProtocolEvent.Type.DISCARDED).get(0).contains("no ACK to SET_CRC_OFF"));
		assertArrayEquals(GET_SHIMMER_VERSION, out.getWrites().get(0));
		assertEquals(LogAndStreamProtocol.State.CONNECTING, p.getState());
	}

	@Test
	public void aDeviceThatDoesNotStopStreamingFailsTheConnection() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		p.connect(T0);
		long giveUp = T0 + LogAndStreamProtocol.SETTLE_GIVE_UP_MS;
		for (long t = T0 + 10; t < giveUp; t += 100) {
			p.receive(STREAM_BYTES, t);
			assertTrue(p.tick(t).isEmpty());
		}

		ProtocolOutput out = p.tick(giveUp);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("did not stop"));
		assertEquals(LogAndStreamProtocol.State.FAILED, p.getState());
		assertEquals(Long.MAX_VALUE, p.nextDeadline());
	}

	@Test
	public void aLostLinkEndsTheProtocol() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		ProtocolOutput out = p.linkLost("device switched off", T1 + 10);

		assertEquals(1, messages(out, ProtocolEvent.Type.LINK_LOST).size());
		assertEquals("device switched off", messages(out, ProtocolEvent.Type.LINK_LOST).get(0));
		assertEquals(LogAndStreamProtocol.State.DISCONNECTED, p.getState());
		assertEquals("no timeout is pending", Long.MAX_VALUE, p.nextDeadline());
		assertTrue("late bytes are ignored", p.receive(SHIMMER3R_VERSION_REPLY, T1 + 20).isEmpty());
		assertTrue("reported once", p.linkLost("again", T1 + 30).isEmpty());
		assertTrue(p.startStreaming(T1 + 40).isEmpty());
		assertTrue(messages(p.connect(T1 + 50), ProtocolEvent.Type.ERROR).get(0).contains("use a new LogAndStreamProtocol"));
		assertEquals("still disconnected, not failed", LogAndStreamProtocol.State.DISCONNECTED, p.getState());
	}

	@Test
	public void anAckAndItsResponseMayArriveSeparately() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		assertTrue(p.receive(new byte[] { (byte) 0xFF }, T1 + 10).getWrites().isEmpty());
		ProtocolOutput out = p.receive(new byte[] { 0x25, 0x0A }, T1 + 20);

		assertEquals(1, out.getWrites().size());
		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void bytesBeforeTheAckAreDroppedAndReported() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		byte[] noisy = { 0x12, 0x34, (byte) 0xFF, 0x25, 0x0A };
		ProtocolOutput out = p.receive(noisy, T1 + 10);

		assertEquals(1, messages(out, ProtocolEvent.Type.DISCARDED).size());
		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void aCommandWithNoReplyTimesOut() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		assertTrue(p.tick(T1 + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS - 1).isEmpty());
		ProtocolOutput out = p.tick(T1 + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS);

		assertEquals(1, messages(out, ProtocolEvent.Type.ERROR).size());
		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("GET_SHIMMER_VERSION"));
		assertEquals(LogAndStreamProtocol.State.FAILED, p.getState());
		assertEquals(Long.MAX_VALUE, p.nextDeadline());
	}

	@Test
	public void aDeviceThatIsNeitherAShimmer3NorAShimmer3RIsRefused() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		// Hardware version 1 is a Shimmer2R.
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x25, 0x01 }, T1 + 10);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("not a Shimmer3 or Shimmer3R"));
		assertTrue("nothing more is sent", out.getWrites().isEmpty());
		assertEquals(LogAndStreamProtocol.State.FAILED, p.getState());
	}

	@Test
	public void anUnexpectedResponseFailsTheHandshake() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);

		// A firmware version response where the hardware version was asked for.
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x2F, 3, 0, 1, 0, 1, 16 }, T1 + 10);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("expected response 0x25"));
		assertEquals(LogAndStreamProtocol.State.FAILED, p.getState());
	}

	@Test
	public void startingToStreamBeforeTheHandshakeIsRefused() {
		LogAndStreamProtocol p = new LogAndStreamProtocol();
		connect(p);
		p.receive(SHIMMER3R_VERSION_REPLY, T1 + 10);

		ProtocolOutput out = p.startStreaming(T1 + 20);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("startStreaming() called in state CONNECTING"));
	}
}
