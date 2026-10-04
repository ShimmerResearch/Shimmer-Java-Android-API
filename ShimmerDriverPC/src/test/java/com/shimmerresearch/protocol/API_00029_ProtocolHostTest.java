package com.shimmerresearch.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.protocol.RecordedSession.Entry;

/**
 * {@link ProtocolHost} with real threads, over a transport that answers from the recorded
 * Shimmer3R session: the host side a new radio (Android, iOS, Python) gets for free.
 */
public class API_00029_ProtocolHostTest {

	private static final long WAIT_MS = 10000;

	/** Answers each write from the recording, delivering the reply on its own thread as a radio does. */
	private static final class RecordedDevice implements ProtocolHost.Transport {
		final SessionResponder responder;
		final List<String> writeThreads = Collections.synchronizedList(new ArrayList<String>());
		final ExecutorService radio = Executors.newSingleThreadExecutor();
		volatile ProtocolHost host;

		RecordedDevice(RecordedSession session) {
			responder = new SessionResponder(session);
		}

		@Override
		public void write(byte[] bytes) {
			writeThreads.add(Thread.currentThread().getName());
			SessionResponder.Answer answer = responder.answer(bytes);
			if (answer == null) {
				return;
			}
			for (Entry rx : answer.rx) {
				radio.execute(() -> host.onBytes(rx.bytes));
			}
		}
	}

	private static final class Recorder implements ProtocolHost.Listener {
		final List<ProtocolEvent> events = Collections.synchronizedList(new ArrayList<ProtocolEvent>());
		final List<String> threads = Collections.synchronizedList(new ArrayList<String>());

		@Override
		public void onEvent(ProtocolEvent event) {
			threads.add(Thread.currentThread().getName());
			events.add(event);
		}

		List<ProtocolEvent> ofType(ProtocolEvent.Type type) {
			List<ProtocolEvent> found = new ArrayList<ProtocolEvent>();
			synchronized (events) {
				for (ProtocolEvent e : events) {
					if (e.type == type) {
						found.add(e);
					}
				}
			}
			return found;
		}
	}

	private RecordedDevice mDevice;
	private ProtocolHost mHost;

	private ProtocolHost start(ProtocolHost.Listener listener) throws Exception {
		mDevice = new RecordedDevice(RecordedSession.load(API_00027_Shimmer3RProtocolMatchesDriverTest.SESSION));
		mHost = new ProtocolHost(mDevice, listener);
		mDevice.host = mHost;
		mHost.connect();
		return mHost;
	}

	@After
	public void stop() {
		if (mHost != null) {
			mHost.close();
		}
		if (mDevice != null) {
			mDevice.radio.shutdownNow();
		}
	}

	@Test
	public void theHostStreamsTheSameSamplesAsTheStateMachineOnItsOwn() throws Exception {
		List<ObjectCluster> reference = ProtocolReplay.run(new Shimmer3RProtocol(),
				RecordedSession.load(API_00027_Shimmer3RProtocolMatchesDriverTest.SESSION)).samples;
		Recorder recorder = new Recorder();
		ProtocolHost host = start(recorder);

		waitUntil(() -> !recorder.ofType(ProtocolEvent.Type.INITIALISED).isEmpty());
		assertEquals(Shimmer3RProtocol.State.READY, host.getState());
		assertEquals(51.2, host.getSamplingRate(), 1e-9);
		host.startStreaming();
		waitUntil(() -> recorder.ofType(ProtocolEvent.Type.SAMPLE).size() >= reference.size());

		assertEquals(Collections.emptyList(), messages(recorder.ofType(ProtocolEvent.Type.ERROR)));
		assertEquals(Shimmer3RProtocol.State.STREAMING, host.getState());
		assertEquals(Collections.emptyList(), mDevice.responder.getUnanswered());
		List<ProtocolEvent> samples = recorder.ofType(ProtocolEvent.Type.SAMPLE);
		assertEquals(reference.size(), samples.size());
		for (int i = 0; i < reference.size(); i++) {
			assertEquals(rawTimestamp(reference.get(i)), rawTimestamp(samples.get(i).sample), 0);
			assertEquals(reference.get(i).getFormatClusterValue("Accel_LN_X", "CAL"),
					samples.get(i).sample.getFormatClusterValue("Accel_LN_X", "CAL"), 0);
		}
		// One thread talks to the device and another delivers events.
		assertEquals(Collections.singleton("ProtocolHost-protocol"), new HashSet<String>(mDevice.writeThreads));
		assertEquals(Collections.singleton("ProtocolHost-events"), new HashSet<String>(recorder.threads));
	}

	@Test
	public void aLostLinkIsDeliveredAndEndsTheSession() throws Exception {
		Recorder recorder = new Recorder();
		ProtocolHost host = start(recorder);
		waitUntil(() -> !recorder.ofType(ProtocolEvent.Type.INITIALISED).isEmpty());
		int writesBefore = mDevice.writeThreads.size();

		host.onLinkLost("device switched off");
		waitUntil(() -> host.getState() == Shimmer3RProtocol.State.DISCONNECTED
				&& !recorder.ofType(ProtocolEvent.Type.LINK_LOST).isEmpty());
		host.startStreaming();
		Thread.sleep(200);

		assertEquals("device switched off", recorder.ofType(ProtocolEvent.Type.LINK_LOST).get(0).message);
		// LINK_LOST then DISCONNECTED end the session; the late startStreaming adds nothing.
		ProtocolEvent secondLast = recorder.events.get(recorder.events.size() - 2);
		ProtocolEvent last = recorder.events.get(recorder.events.size() - 1);
		assertEquals(ProtocolEvent.Type.LINK_LOST, secondLast.type);
		assertEquals(Shimmer3RProtocol.State.DISCONNECTED, last.state);
		assertEquals("nothing is written after the link is lost", writesBefore, mDevice.writeThreads.size());
	}

	@Test
	public void aListenerThatThrowsDoesNotStopLaterEvents() throws Exception {
		Recorder recorder = new Recorder();
		ProtocolHost host = start(event -> {
			recorder.onEvent(event);
			if (event.type == ProtocolEvent.Type.STATE_CHANGED) {
				throw new IllegalStateException("thrown on purpose by the test listener");
			}
		});

		waitUntil(() -> !recorder.ofType(ProtocolEvent.Type.INITIALISED).isEmpty());

		assertEquals(Shimmer3RProtocol.State.READY, host.getState());
	}

	private static double rawTimestamp(ObjectCluster oc) {
		return oc.getFormatClusterValue("Timestamp", CHANNEL_TYPE.UNCAL.toString());
	}

	private static List<String> messages(List<ProtocolEvent> events) {
		List<String> messages = new ArrayList<String>();
		for (ProtocolEvent e : events) {
			messages.add(e.message);
		}
		return messages;
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + WAIT_MS;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("timed out after " + WAIT_MS + " ms");
			}
			Thread.sleep(10);
		}
	}
}
