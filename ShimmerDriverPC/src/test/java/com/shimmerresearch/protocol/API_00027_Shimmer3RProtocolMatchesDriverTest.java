package com.shimmerresearch.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.BeforeClass;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * The DEV-1134 Shimmer3R protocol state machine against today's driver, on the same recorded
 * session and with no hardware: every packet the state machine decodes must carry the same
 * values, raw and calibrated, as the packet today's driver decodes from the same bytes.
 * <p>
 * Today's driver (ShimmerPC) runs in real time over {@link ReplayByteCommunication}; the state
 * machine runs on a simulated clock through {@link ProtocolReplay}.
 */
public class API_00027_Shimmer3RProtocolMatchesDriverTest {

	static final String SESSION = "/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log";

	/** Channels derived from the PC clock or aggregated over the run, not from the packet's bytes. */
	private static final String[] NOT_PER_PACKET = { "System_Timestamp", "Packet_Reception_Rate", "Event_Marker" };

	private static List<ObjectCluster> sReference;

	@BeforeClass
	public static void runTodaysDriver() throws Exception {
		sReference = TodaysDriver.decode(RecordedSession.load(SESSION));
	}

	@Test
	public void todaysDriverDecodesTheRecordedSession() {
		assertTrue("expected roughly 10 s of 51.2 Hz packets, got " + sReference.size(), sReference.size() > 400);
	}

	@Test
	public void stateMachineCompletesTheHandshakeAndStreams() throws Exception {
		Shimmer3RProtocol protocol = new Shimmer3RProtocol();
		ProtocolReplay replay = ProtocolReplay.run(protocol, RecordedSession.load(SESSION));
		print(replay);

		assertEquals(Collections.emptyList(), errors(replay));
		assertEquals(Shimmer3RProtocol.State.STREAMING, protocol.getState());
		assertEquals(51.2, protocol.getSamplingRate(), 1e-9);
		assertEquals(Collections.emptyList(), unanswered(replay));
	}

	@Test
	public void stateMachineDecodesTheSameValuesAsTodaysDriver() throws Exception {
		ProtocolReplay replay = ProtocolReplay.run(new Shimmer3RProtocol(), RecordedSession.load(SESSION));
		List<ObjectCluster> mine = replay.samples;

		// Pair packets by their device timestamp, so a packet one side missed does not shift the rest.
		Map<Double, ObjectCluster> reference = new LinkedHashMap<Double, ObjectCluster>();
		for (ObjectCluster oc : sReference) {
			reference.put(rawTimestamp(oc), oc);
		}
		int paired = 0;
		List<String> differences = new ArrayList<String>();
		for (ObjectCluster oc : mine) {
			ObjectCluster theirs = reference.get(rawTimestamp(oc));
			if (theirs == null) {
				continue;
			}
			paired++;
			for (String channel : theirs.getChannelNamesByInsertionOrder()) {
				if (!isPerPacket(channel)) {
					continue;
				}
				for (CHANNEL_TYPE format : new CHANNEL_TYPE[] { CHANNEL_TYPE.UNCAL, CHANNEL_TYPE.CAL }) {
					double expected = theirs.getFormatClusterValue(channel, format.toString());
					double actual = oc.getFormatClusterValue(channel, format.toString());
					if (Double.compare(expected, actual) != 0 && differences.size() < 20) {
						differences.add("packet at raw timestamp " + rawTimestamp(oc) + ", " + channel + " " + format
								+ ": driver " + expected + ", state machine " + actual);
					}
				}
			}
		}
		System.out.println("state machine " + mine.size() + " packets, driver " + sReference.size() + ", paired " + paired);
		differences.forEach(System.out::println);

		assertEquals(Collections.emptyList(), differences);
		// Today's driver can lose a packet or two at the start of streaming; the rest must pair up.
		assertTrue("only " + paired + " of " + sReference.size() + " packets paired", paired >= sReference.size() - 2);
	}

	@Test
	public void howTheBytesAreSplitMakesNoDifference() throws Exception {
		RecordedSession session = RecordedSession.load(SESSION);
		ProtocolReplay whole = ProtocolReplay.run(new Shimmer3RProtocol(), session);
		ProtocolReplay oneByteAtATime = ProtocolReplay.run(new Shimmer3RProtocol(), session.splitIntoSingleBytes());

		assertEquals(Collections.emptyList(), errors(oneByteAtATime));
		assertEquals(whole.samples.size(), oneByteAtATime.samples.size());
		for (int i = 0; i < whole.samples.size(); i++) {
			assertEquals(rawTimestamp(whole.samples.get(i)), rawTimestamp(oneByteAtATime.samples.get(i)), 0);
			assertEquals(whole.samples.get(i).getFormatClusterValue("Accel_LN_X", "CAL"),
					oneByteAtATime.samples.get(i).getFormatClusterValue("Accel_LN_X", "CAL"), 0);
		}
	}

	@Test
	public void aCorruptedPacketIsDroppedAndDecodingResumes() throws Exception {
		RecordedSession session = RecordedSession.load(SESSION);
		// The 3rd notification after START_STREAMING; byte 5 is inside its first packet's data.
		int notification = session.firstRxAfter((byte) 0x07) + 2;
		ProtocolReplay clean = ProtocolReplay.run(new Shimmer3RProtocol(), session);
		ProtocolReplay corrupted = ProtocolReplay.run(new Shimmer3RProtocol(), session.corruptRx(notification, 5));

		assertEquals(Collections.emptyList(), errors(corrupted));
		assertEquals("exactly the corrupted packet is lost", clean.samples.size() - 1, corrupted.samples.size());
		boolean reported = false;
		for (ProtocolEvent e : corrupted.events) {
			reported |= e.type == ProtocolEvent.Type.DISCARDED;
		}
		assertTrue("the dropped bytes are reported", reported);
		// Everything after the corrupted packet still decodes to the same values.
		ObjectCluster lastClean = clean.samples.get(clean.samples.size() - 1);
		ObjectCluster lastCorrupted = corrupted.samples.get(corrupted.samples.size() - 1);
		assertEquals(rawTimestamp(lastClean), rawTimestamp(lastCorrupted), 0);
	}

	private static boolean isPerPacket(String channel) {
		for (String excluded : NOT_PER_PACKET) {
			if (channel.startsWith(excluded)) {
				return false;
			}
		}
		return true;
	}

	private static double rawTimestamp(ObjectCluster oc) {
		return oc.getFormatClusterValue("Timestamp", CHANNEL_TYPE.UNCAL.toString());
	}

	private static List<String> errors(ProtocolReplay replay) {
		List<String> errors = new ArrayList<String>();
		for (ProtocolEvent e : replay.events) {
			if (e.type == ProtocolEvent.Type.ERROR) {
				errors.add(e.message);
			}
		}
		return errors;
	}

	private static List<String> unanswered(ProtocolReplay replay) {
		List<String> hex = new ArrayList<String>();
		for (byte[] c : replay.responder.getUnanswered()) {
			hex.add(UtilShimmer.bytesToHexStringWithSpacesFormatted(c));
		}
		return hex;
	}

	private static void print(ProtocolReplay replay) {
		System.out.println("state machine wrote " + replay.written.size() + " commands:");
		for (byte[] w : replay.written) {
			System.out.println("  " + UtilShimmer.bytesToHexStringWithSpacesFormatted(w));
		}
		for (ProtocolEvent e : replay.events) {
			if (e.type != ProtocolEvent.Type.SAMPLE) {
				System.out.println("  event: " + e);
			}
		}
	}

	/** Today's driver over the replay, collecting what it decodes. */
	static final class TodaysDriver extends BasicProcessWithCallBack {
		private final List<ObjectCluster> mPackets = Collections.synchronizedList(new ArrayList<ObjectCluster>());
		private volatile boolean mInitialised = false;

		static List<ObjectCluster> decode(RecordedSession session) throws Exception {
			TodaysDriver collector = new TodaysDriver();
			ReplayByteCommunication replay = new ReplayByteCommunication(session);
			ShimmerPC device = new ShimmerPC("REPLAY");
			device.setTestRadio(replay);
			collector.setWaitForData(device);
			try {
				device.connect("REPLAY", "");
				waitUntil("fully initialised", 30000, () -> collector.mInitialised);
				assertEquals(HW_ID.SHIMMER_3R, device.getHardwareVersion());
				device.startStreaming();
				waitUntil("streaming", 15000, device::isStreaming);
				waitUntil("first packet", 15000, () -> !collector.mPackets.isEmpty());
				// The recording is finite: wait until packets stop arriving.
				int last = -1;
				long deadline = System.currentTimeMillis() + 30000;
				while (collector.mPackets.size() != last && System.currentTimeMillis() < deadline) {
					last = collector.mPackets.size();
					Thread.sleep(1000);
				}
				synchronized (collector.mPackets) {
					return new ArrayList<ObjectCluster>(collector.mPackets);
				}
			} finally {
				try {
					device.disconnect();
				} catch (Exception e) {
					// Clean-up only.
				}
				replay.shutdown();
			}
		}

		@Override
		protected void processMsgFromCallback(ShimmerMsg msg) {
			if (msg.mIdentifier == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE
					&& ((CallbackObject) msg.mB).mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
				mInitialised = true;
			} else if (msg.mIdentifier == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
				mPackets.add((ObjectCluster) msg.mB);
			}
		}
	}

	private static void waitUntil(String what, long timeoutMs, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("timed out waiting for " + what);
			}
			Thread.sleep(50);
		}
	}
}
