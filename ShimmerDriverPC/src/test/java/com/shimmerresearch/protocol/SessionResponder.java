package com.shimmerresearch.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.shimmerresearch.protocol.RecordedSession.Direction;
import com.shimmerresearch.protocol.RecordedSession.Entry;

/**
 * Finds the recorded device reply to each command written during a replay.
 * <p>
 * Commands are matched in order first. A command that is not next in the recording (for instance
 * one sent by a timer that fired at a different moment, or a handshake that orders its commands
 * differently) is answered from its first occurrence anywhere in the recording. A command never
 * recorded gets no answer and is listed in {@link #getUnanswered()}. SET_RWC (0x8F) carries the
 * PC clock, so only its opcode is compared.
 */
public class SessionResponder {

	private static final byte SET_RWC_COMMAND = (byte) 0x8F;

	/** The recorded reply to one command: its RX entries and when the command was sent. */
	public static class Answer {
		public final long txTimeMs;
		public final List<Entry> rx;

		Answer(long txTimeMs, List<Entry> rx) {
			this.txTimeMs = txTimeMs;
			this.rx = rx;
		}
	}

	private final List<Entry> mEntries;
	private final List<byte[]> mUnanswered = Collections.synchronizedList(new ArrayList<byte[]>());
	private int mCursor = 0;

	public SessionResponder(RecordedSession session) {
		mEntries = session.getEntries();
	}

	public List<byte[]> getUnanswered() {
		synchronized (mUnanswered) {
			return new ArrayList<byte[]>(mUnanswered);
		}
	}

	/** The recorded answer to {@code written}, or null if the recording has none. */
	public synchronized Answer answer(byte[] written) {
		int next = nextTx(mCursor);
		if (next >= 0 && sameCommand(mEntries.get(next).bytes, written)) {
			Answer answer = answerAt(next);
			mCursor = next + 1 + answer.rx.size();
			return answer;
		}
		for (int i = 0; i < mEntries.size(); i++) {
			Entry e = mEntries.get(i);
			if (e.direction == Direction.TX && sameCommand(e.bytes, written)) {
				return answerAt(i);
			}
		}
		mUnanswered.add(written.clone());
		return null;
	}

	static boolean sameCommand(byte[] recorded, byte[] written) {
		if (recorded.length == 0 || written.length == 0 || recorded[0] != written[0]) {
			return false;
		}
		return written[0] == SET_RWC_COMMAND || Arrays.equals(recorded, written);
	}

	private Answer answerAt(int txIndex) {
		List<Entry> rx = new ArrayList<Entry>();
		for (int i = txIndex + 1; i < mEntries.size() && mEntries.get(i).direction == Direction.RX; i++) {
			rx.add(mEntries.get(i));
		}
		return new Answer(mEntries.get(txIndex).timeMs, rx);
	}

	private int nextTx(int from) {
		for (int i = from; i < mEntries.size(); i++) {
			if (mEntries.get(i).direction == Direction.TX) {
				return i;
			}
		}
		return -1;
	}
}
