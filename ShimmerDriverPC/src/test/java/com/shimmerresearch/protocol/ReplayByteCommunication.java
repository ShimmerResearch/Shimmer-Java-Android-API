package com.shimmerresearch.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.protocol.RecordedSession.Entry;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * Plays a {@link RecordedSession} back to a driver through its serial-port interface: each
 * command the driver writes is answered with the bytes the real device sent (see
 * {@link SessionResponder}), each delayed after the write as it was in the recording.
 * <p>
 * Delivering a whole answer at once is not equivalent: drivers clear their input around
 * commands, which on real hardware happens before later bytes arrive.
 */
public class ReplayByteCommunication implements ByteCommunication {

	private final SessionResponder mResponder;
	private final LinkedBlockingQueue<Byte> mInput = new LinkedBlockingQueue<Byte>();
	private final List<byte[]> mWritten = Collections.synchronizedList(new ArrayList<byte[]>());
	// A single thread, so answers are delivered in the order they are due.
	private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "replay-rx");
		t.setDaemon(true);
		return t;
	});
	private volatile boolean mOpen = false;

	public ReplayByteCommunication(RecordedSession session) {
		mResponder = new SessionResponder(session);
	}

	/** Every command the driver wrote, in order. */
	public List<byte[]> getWritten() {
		synchronized (mWritten) {
			return new ArrayList<byte[]>(mWritten);
		}
	}

	/** Commands that had no recorded answer. */
	public List<byte[]> getUnanswered() {
		return mResponder.getUnanswered();
	}

	/** Stops delivering scheduled answers. */
	public void shutdown() {
		mScheduler.shutdownNow();
	}

	@Override
	public boolean writeBytes(byte[] buffer) {
		mWritten.add(buffer.clone());
		SessionResponder.Answer answer = mResponder.answer(buffer);
		if (answer == null) {
			return true;
		}
		for (Entry rx : answer.rx) {
			final byte[] bytes = rx.bytes;
			mScheduler.schedule(() -> {
				for (byte b : bytes) {
					mInput.add(b);
				}
			}, Math.max(0, rx.timeMs - answer.txTimeMs), TimeUnit.MILLISECONDS);
		}
		return true;
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
