package com.shimmerresearch.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.protocol.RecordedSession.Entry;

/**
 * Drives a {@link Shimmer3RProtocol} with a {@link RecordedSession} on a simulated clock: each
 * write is answered from the recording ({@link SessionResponder}), delayed as recorded, and the
 * clock jumps straight to the next delivery or protocol deadline. Nothing sleeps, so a 10 s
 * recording replays in milliseconds and every run is identical.
 */
final class ProtocolReplay {

	/** An arbitrary wall-clock origin for the simulated clock. */
	static final long START_MS = 1790960000000L;

	private static final class Delivery implements Comparable<Delivery> {
		final long timeMs;
		final long order;
		final byte[] bytes;

		Delivery(long timeMs, long order, byte[] bytes) {
			this.timeMs = timeMs;
			this.order = order;
			this.bytes = bytes;
		}

		@Override
		public int compareTo(Delivery o) {
			int byTime = Long.compare(timeMs, o.timeMs);
			return byTime != 0 ? byTime : Long.compare(order, o.order);
		}
	}

	final List<ObjectCluster> samples = new ArrayList<ObjectCluster>();
	final List<ProtocolEvent> events = new ArrayList<ProtocolEvent>();
	final List<byte[]> written = new ArrayList<byte[]>();
	final SessionResponder responder;

	private final PriorityQueue<Delivery> mPending = new PriorityQueue<Delivery>();
	private long mOrder = 0;
	private long mNow = START_MS;

	private ProtocolReplay(RecordedSession session) {
		responder = new SessionResponder(session);
	}

	/** Connects, starts streaming once ready, and runs until the recording is exhausted. */
	static ProtocolReplay run(Shimmer3RProtocol protocol, RecordedSession session) {
		ProtocolReplay replay = new ProtocolReplay(session);
		replay.handle(protocol.connect(replay.mNow));
		boolean started = false;
		while (protocol.getState() != Shimmer3RProtocol.State.FAILED) {
			if (!started && protocol.getState() == Shimmer3RProtocol.State.READY) {
				started = true;
				replay.handle(protocol.startStreaming(replay.mNow));
				continue;
			}
			long nextDelivery = replay.mPending.isEmpty() ? Long.MAX_VALUE : replay.mPending.peek().timeMs;
			long deadline = protocol.nextDeadline();
			if (nextDelivery == Long.MAX_VALUE && deadline == Long.MAX_VALUE) {
				break;
			}
			if (nextDelivery <= deadline) {
				Delivery d = replay.mPending.poll();
				replay.mNow = Math.max(replay.mNow, d.timeMs);
				replay.handle(protocol.receive(d.bytes, replay.mNow));
			} else {
				replay.mNow = deadline;
				replay.handle(protocol.tick(replay.mNow));
			}
		}
		return replay;
	}

	private void handle(ProtocolOutput out) {
		for (ProtocolEvent e : out.getEvents()) {
			events.add(e);
			if (e.type == ProtocolEvent.Type.SAMPLE) {
				samples.add(e.sample);
			}
		}
		for (byte[] w : out.getWrites()) {
			written.add(w);
			SessionResponder.Answer answer = responder.answer(w);
			if (answer == null) {
				continue;
			}
			for (Entry rx : answer.rx) {
				mPending.add(new Delivery(mNow + Math.max(0, rx.timeMs - answer.txTimeMs), mOrder++, rx.bytes));
			}
		}
	}
}
