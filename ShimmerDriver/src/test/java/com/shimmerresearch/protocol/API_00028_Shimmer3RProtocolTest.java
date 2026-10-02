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
	private static final byte[] GET_SHIMMER_VERSION = { 0x3F };
	private static final byte[] GET_FW_VERSION = { 0x2E };
	/** ACK, GET_SHIMMER_VERSION_RESPONSE, hardware version 10 (Shimmer3R). */
	private static final byte[] SHIMMER3R_VERSION_REPLY = { (byte) 0xFF, 0x25, 0x0A };

	private static List<String> messages(ProtocolOutput out, ProtocolEvent.Type type) {
		List<String> found = new ArrayList<String>();
		for (ProtocolEvent e : out.getEvents()) {
			if (e.type == type) {
				found.add(e.message);
			}
		}
		return found;
	}

	@Test
	public void connectAsksForTheHardwareVersionFirst() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		ProtocolOutput out = p.connect(T0);

		assertEquals(1, out.getWrites().size());
		assertArrayEquals(GET_SHIMMER_VERSION, out.getWrites().get(0));
		assertEquals(Shimmer3RProtocol.State.CONNECTING, p.getState());
		assertEquals(T0 + Shimmer3RProtocol.DEFAULT_TIMEOUT_MS, p.nextDeadline());
	}

	@Test
	public void anAckAndItsResponseMayArriveSeparately() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);

		assertTrue(p.receive(new byte[] { (byte) 0xFF }, T0 + 10).getWrites().isEmpty());
		ProtocolOutput out = p.receive(new byte[] { 0x25, 0x0A }, T0 + 20);

		assertEquals(1, out.getWrites().size());
		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void bytesBeforeTheAckAreDroppedAndReported() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);

		byte[] noisy = { 0x12, 0x34, (byte) 0xFF, 0x25, 0x0A };
		ProtocolOutput out = p.receive(noisy, T0 + 10);

		assertEquals(1, messages(out, ProtocolEvent.Type.DISCARDED).size());
		assertArrayEquals(GET_FW_VERSION, out.getWrites().get(0));
	}

	@Test
	public void aCommandWithNoReplyTimesOut() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);

		assertTrue(p.tick(T0 + Shimmer3RProtocol.DEFAULT_TIMEOUT_MS - 1).isEmpty());
		ProtocolOutput out = p.tick(T0 + Shimmer3RProtocol.DEFAULT_TIMEOUT_MS);

		assertEquals(1, messages(out, ProtocolEvent.Type.ERROR).size());
		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("GET_SHIMMER_VERSION"));
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
		assertEquals(Long.MAX_VALUE, p.nextDeadline());
	}

	@Test
	public void aDeviceThatIsNotAShimmer3RIsRefused() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);

		// Hardware version 3 is a Shimmer3.
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x25, 0x03 }, T0 + 10);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("not a Shimmer3R"));
		assertTrue("nothing more is sent", out.getWrites().isEmpty());
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
	}

	@Test
	public void anUnexpectedResponseFailsTheHandshake() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);

		// A firmware version response where the hardware version was asked for.
		ProtocolOutput out = p.receive(new byte[] { (byte) 0xFF, 0x2F, 3, 0, 1, 0, 1, 16 }, T0 + 10);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("expected response 0x25"));
		assertEquals(Shimmer3RProtocol.State.FAILED, p.getState());
	}

	@Test
	public void startingToStreamBeforeTheHandshakeIsRefused() {
		Shimmer3RProtocol p = new Shimmer3RProtocol();
		p.connect(T0);
		p.receive(SHIMMER3R_VERSION_REPLY, T0 + 10);

		ProtocolOutput out = p.startStreaming(T0 + 20);

		assertTrue(messages(out, ProtocolEvent.Type.ERROR).get(0).contains("startStreaming() called in state CONNECTING"));
	}
}
