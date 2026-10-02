package com.shimmerresearch.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.protocol.RecordedSession.Direction;
import com.shimmerresearch.protocol.RecordedSession.Entry;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * Plays a {@link RecordedSession} back to a driver: each command the driver writes is answered
 * with the bytes the real device sent after that command in the recording.
 * <p>
 * Commands are matched in order first. A command that is not next in the recording (for instance
 * one sent by a timer that fired at a different moment) is answered from its first occurrence
 * anywhere in the recording, and a command never recorded is logged in {@link #getUnanswered()}
 * and left unanswered. SET_RWC (0x8F) carries the PC clock, so only its opcode is compared.
 */
public class ReplayByteCommunication implements ByteCommunication {

	private static final byte SET_RWC_COMMAND = (byte) 0x8F;

	private final List<Entry> mEntries;
	private final LinkedBlockingQueue<Byte> mInput = new LinkedBlockingQueue<Byte>();
	private final List<byte[]> mWritten = Collections.synchronizedList(new ArrayList<byte[]>());
	private final List<byte[]> mUnanswered = Collections.synchronizedList(new ArrayList<byte[]>());
	// A single thread, so answers are delivered in the order they are due.
	private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "replay-rx");
		t.setDaemon(true);
		return t;
	});
	private int mCursor = 0;
	private volatile boolean mOpen = false;

	public ReplayByteCommunication(RecordedSession session) {
		mEntries = session.getEntries();
	}

	/** Every command the driver wrote, in order. */
	public List<byte[]> getWritten() {
		synchronized (mWritten) {
			return new ArrayList<byte[]>(mWritten);
		}
	}

	/** Commands that had no recorded answer. */
	public List<byte[]> getUnanswered() {
		synchronized (mUnanswered) {
			return new ArrayList<byte[]>(mUnanswered);
		}
	}

	static boolean sameCommand(byte[] recorded, byte[] written) {
		if (recorded.length == 0 || written.length == 0 || recorded[0] != written[0]) {
			return false;
		}
		return written[0] == SET_RWC_COMMAND || Arrays.equals(recorded, written);
	}

	@Override
	public synchronized boolean writeBytes(byte[] buffer) {
		mWritten.add(buffer.clone());
		int next = nextTx(mCursor);
		if (next >= 0 && sameCommand(mEntries.get(next).bytes, buffer)) {
			mCursor = enqueueAnswer(next);
			return true;
		}
		for (int i = 0; i < mEntries.size(); i++) {
			Entry e = mEntries.get(i);
			if (e.direction == Direction.TX && sameCommand(e.bytes, buffer)) {
				enqueueAnswer(i);
				return true;
			}
		}
		mUnanswered.add(buffer.clone());
		return true;
	}

	/**
	 * Schedules the RX entries that follow the TX at {@code txIndex}, each delayed after the write
	 * as it was in the recording, and returns the index after them. Delivering a whole answer at
	 * once is not equivalent: drivers clear their input around commands, which on real hardware
	 * happens before later bytes arrive.
	 */
	private int enqueueAnswer(int txIndex) {
		long txMs = mEntries.get(txIndex).timeMs;
		int i = txIndex + 1;
		for (; i < mEntries.size() && mEntries.get(i).direction == Direction.RX; i++) {
			final byte[] bytes = mEntries.get(i).bytes;
			long delay = Math.max(0, mEntries.get(i).timeMs - txMs);
			mScheduler.schedule(new Runnable() {
				@Override
				public void run() {
					for (byte b : bytes) {
						mInput.add(b);
					}
				}
			}, delay, TimeUnit.MILLISECONDS);
		}
		return i;
	}

	/** Stops delivering scheduled answers. */
	public void shutdown() {
		mScheduler.shutdownNow();
	}

	private int nextTx(int from) {
		for (int i = from; i < mEntries.size(); i++) {
			if (mEntries.get(i).direction == Direction.TX) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int getInputBufferBytesCount() {
		return mInput.size();
	}

	@Override
	public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException {
		byte[] result = new byte[byteCount];
		long deadline = System.currentTimeMillis() + timeout;
		for (int i = 0; i < byteCount; i++) {
			Byte b = null;
			try {
				b = mInput.poll(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (b == null) {
				throw new SerialPortTimeoutException("replay", "readBytes", timeout);
			}
			result[i] = b;
		}
		return result;
	}

	@Override
	public boolean isOpened() {
		return mOpen;
	}

	@Override
	public boolean openPort() {
		mOpen = true;
		return true;
	}

	@Override
	public boolean closePort() {
		mOpen = false;
		return true;
	}

	@Override
	public boolean setParams(int i, int j, int k, int l) {
		return true;
	}

	@Override
	public boolean purgePort(int i) {
		mInput.clear();
		return true;
	}

	@Override
	public void setByteCommunicationListener(ByteCommunicationListener byteCommListener) {
	}

	@Override
	public void removeRadioListenerList() {
	}
}
