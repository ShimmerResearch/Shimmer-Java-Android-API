package com.shimmerresearch.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * The Shimmer3R protocol state machine on hand-written bytes: framing and failure paths that a
 * good recorded session never exercises. No device, driver, thread or clock is involved.
 */
public class API_00028_Shimmer3RProtocolTest {

	private static final long T0 = 1790960000000L;
	/** When the handshake starts on a link that stays quiet. */
	private static final long T1 = T0 + Shimmer3RProtocol.SETTLE_MS;
	private static final byte[] GET_SHIMMER_VERSION = { 0x3F };
	private static final byte[] GET_FW_VERSION = { 0x2E };
	private static final byte[] STOP_STREAMING = { 0x20 };
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
	private static ProtocolOutput connect(Shimmer3RProtocol p) {
		assertTrue("nothing is written until the link is quiet", p.connect(T0).getWrites().isEmpty());
		return p.tick(T1);
	}

	@Test
	public void connectWaitsForAQuietLinkThenAsksForTheHardwareVersion() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		assertTrue(p.connect(T0).getWrites().isEmpty());
		assertEquals(T1, p.nextDeadline());
		assertTrue(p.tick(T1 - 1).isEmpty());

		ProtocolOutput out = p.tick(T1);

		assertEquals(1, out.getWrites().size());
		assertArrayEquals(GET_SHIMMER_VERSION, out.getWrites().get(0));
		assertTrue("a quiet link reports nothing", messages(out, ProtocolEvent.Type.DISCARDED).isEmpty());
		assertEquals(Shimmer3RProtocol.State.CONNECTING, p.getState());
		assertEquals(T1 + Shimmer3RProtocol.DEFAULT_TIMEOUT_MS, p.nextDeadline());
	}

	@Test
	public void aDeviceLeftStreamingIsStoppedBeforeTheHandshake() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);

		ProtocolOutput first = p.receive(STREAM_BYTES, T0 + 10);
		assertEquals("stopped once", 1, first.getWrites().size());
		assertArrayEquals(STOP_STREAMING, first.getWrites().get(0));
		// More stream, and the STOP_STREAMING ACK, push the start of the handshake back.
		assertTrue(p.receive(STREAM_BYTES, T0 + 100).getWrites().isEmpty());
		long quiet = T0 + 100 + Shimmer3RProtocol.SETTLE_MS;
		assertEquals(quiet, p.nextDeadline());
		assertTrue(p.tick(quiet - 1).isEmpty());

		ProtocolOutput out = p.tick(quiet);

		assertArrayEquals(GET_SHIMMER_VERSION, out.getWrites().get(0));
		assertTrue(messages(out, ProtocolEvent.Type.DISCARDED).get(0).contains("dropped " + 2 * STREAM_BYTES.length + " byte(s)"));
		// None of those bytes is mistaken for the reply.
		assertArrayEquals(GET_FW_VERSION, p.receive(SHIMMER3R_VERSION_REPLY, quiet + 10).getWrites().get(0));
	}

	@Test
	public void aDeviceThatDoesNotStopStreamingFailsTheConnection() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);
		long giveUp = T0 + Shimmer3RProtocol.SETTLE_GIVE_UP_MS;
		for (long t = T0 + 10; t < giveUp; t += 100) {
			p.receive(STREAM_BYTES, t);
			assertTrue(p.tick(t).isEmpty());
		}

		ProtocolOutput out = p.tick(giveUp);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("did not stop"));
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
		assertEquals(Long.MAX_VALUE, p.nextDeadline());
	}

	@Test
	public void aLostLinkEndsTheProtocol() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);

		ProtocolOutput out = p.linkLost("device switched off", T1 + 10);

		assertEquals(1, messages(out, ProtocolEvent.Type.LINK_LOST).size());
		assertEquals("device switched off", messages(out, ProtocolEvent.Type.LINK_LOST).get(0));
		assertEquals(Shimmer3RProtocol.State.DISCONNECTED, p.getState());
		assertEquals("no timeout is pending", Long.MAX_VALUE, p.nextDeadline());
		assertTrue("late bytes are ignored", p.receive(SHIMMER3R_VERSION_REPLY, T1 + 20).isEmpty());
		assertTrue("reported once", p.linkLost("again", T1 + 30).isEmpty());
		assertTrue(p.startStreaming(T1 + 40).isEmpty());
		assertTrue(messages(p.connect(T1 + 50), ProtocolEvent.Type.ERROR).get(0).contains("use a new Shimmer3RProtocol"));
		assertEquals("still disconnected, not failed", Shimmer3RProtocol.State.DISCONNECTED, p.getState());
	}

	@Test
	public void anAckAndItsResponseMayArriveSeparately() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);

		assertTrue(p.receive(new byte[] { (byte) 0xFF }, T1 + 10).getWrites().isEmpty());
		ProtocolOutput out = p.receive(new byte[] { 0x25, 0x0A }, T1 + 20);

		assertEquals(1, out.getWrites().size());
		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void bytesBeforeTheAckAreDroppedAndReported() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);

		byte[] noisy = { 0x12, 0x34, (byte) 0xFF, 0x25, 0x0A };
		ProtocolOutput out = p.receive(noisy, T1 + 10);

		assertEquals(1, messages(out, ProtocolEvent.Type.DISCARDED).size());
		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void aCommandWithNoReplyTimesOut() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);

		assertTrue(p.tick(T1 + Shimmer3RProtocol.DEFAULT_TIMEOUT_MS - 1).isEmpty());
		ProtocolOutput out = p.tick(T1 + Shimmer3RProtocol.DEFAULT_TIMEOUT_MS);

		assertEquals(1, messages(out, ProtocolEvent.Type.ERROR).size());
		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("GET_SHIMMER_VERSION"));
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
		assertEquals(Long.MAX_VALUE, p.nextDeadline());
	}

	@Test
	public void aDeviceThatIsNotAShimmer3RIsRefused() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);

		// Hardware version 3 is a Shimmer3.
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x25, 0x03 }, T1 + 10);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("not a Shimmer3R"));
		assertTrue("nothing more is sent", out.getWrites().isEmpty());
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
	}

	@Test
	public void anUnexpectedResponseFailsTheHandshake() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);

		// A firmware version response where the hardware version was asked for.
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x2F, 3, 0, 1, 0, 1, 16 }, T1 + 10);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("expected response 0x25"));
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
	}

	@Test
	public void startingToStreamBeforeTheHandshakeIsRefused() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		connect(p);
		p.receive(SHIMMER3R_VERSION_REPLY, T1 + 10);

		ProtocolOutput out = p.startStreaming(T1 + 20);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("startStreaming() called in state CONNECTING"));
	}
}
