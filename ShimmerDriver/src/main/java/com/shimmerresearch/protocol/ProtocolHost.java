package com.shimmerresearch.protocol;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.driver.ShimmerDevice;

/**
 * Runs a {@link LogAndStreamProtocol} for an application, so that a transport only moves bytes.
 * <p>
 * Every call into the protocol happens on one thread, so the protocol needs no lock, and a
 * {@link #TICK_MS} timer drives its timeouts. Events reach the {@link Listener} in order on a
 * second thread, so a slow listener (a plot, a file) never holds up the bytes arriving.
 * <p>
 * A transport needs only this: open the link, call {@link #connect()}, pass every notification
 * to {@link #onBytes}, report a lost link with {@link #onLinkLost}, and implement
 * {@link Transport#write}. Any thread may call this class.
 */
public final class ProtocolHost {

	/** The link to the device. */
	public interface Transport {
		/**
		 * Sends one write to the device, without waiting for a response. Called on the host's
		 * protocol thread, one write at a time and in order.
		 */
		void write(byte[] bytes) throws Exception;
	}

	/** Receives the protocol's events, in order, on the host's event thread. */
	public interface Listener {
		void onEvent(ProtocolEvent event);
	}

	/** Wall-clock milliseconds, as {@link LogAndStreamProtocol} expects. Replaceable in tests. */
	interface Clock {
		long nowMs();
	}

	public static final long TICK_MS = 20;

	private final LogAndStreamProtocol mProtocol = new LogAndStreamProtocol();
	private final Transport mTransport;
	private final Listener mListener;
	private final Clock mClock;
	private final ScheduledExecutorService mProtocolThread;
	private final ExecutorService mEventThread;
	private ScheduledFuture<?> mTicker;

	// Copies for other threads, refreshed after every call into the protocol.
	private volatile LogAndStreamProtocol.State mState = LogAndStreamProtocol.State.DISCONNECTED;
	private volatile double mSamplingRate = Double.NaN;
	private volatile boolean mClosed = false;

	public ProtocolHost(Transport transport, Listener listener) {
		this(transport, listener, System::currentTimeMillis);
	}

	ProtocolHost(Transport transport, Listener listener, Clock clock) {
		mTransport = transport;
		mListener = listener;
		mClock = clock;
		mProtocolThread = Executors.newSingleThreadScheduledExecutor(daemon("ProtocolHost-protocol"));
		mEventThread = Executors.newSingleThreadExecutor(daemon("ProtocolHost-events"));
	}

	/** Call once the link is open. Starts the handshake and the timeout timer. */
	public void connect() {
		post(() -> {
			apply(mProtocol.connect(mClock.nowMs()));
			mTicker = mProtocolThread.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
		});
	}

	/** Bytes from the device, in the order they arrived. The array is copied. */
	public void onBytes(byte[] bytes) {
		byte[] copy = bytes.clone();
		post(() -> apply(mProtocol.receive(copy, mClock.nowMs())));
	}

	/** The transport lost the link. The protocol ends in {@link LogAndStreamProtocol.State#DISCONNECTED}. */
	public void onLinkLost(String reason) {
		post(() -> {
			stopTicker();
			apply(mProtocol.linkLost(reason, mClock.nowMs()));
		});
	}

	public void startStreaming() {
		post(() -> apply(mProtocol.startStreaming(mClock.nowMs())));
	}

	public void stopStreaming() {
		post(() -> apply(mProtocol.stopStreaming(mClock.nowMs())));
	}

	/**
	 * Stops both threads. Events already queued are still delivered; nothing reaches the
	 * protocol afterwards. Does not close the transport.
	 */
	public void close() {
		mClosed = true;
		mProtocolThread.shutdownNow();
		mEventThread.shutdown();
	}

	public LogAndStreamProtocol.State getState() {
		return mState;
	}

	public double getSamplingRate() {
		return mSamplingRate;
	}

	/** See {@link LogAndStreamProtocol#getDeviceModel()}: complete once the state is READY. */
	public ShimmerDevice getDeviceModel() {
		return mProtocol.getDeviceModel();
	}

	// --- On the protocol thread ----------------------------------------------------------------

	private void tick() {
		long now = mClock.nowMs();
		if (now >= mProtocol.nextDeadline()) {
			apply(mProtocol.tick(now));
		}
	}

	private void apply(ProtocolOutput out) {
		for (byte[] write : out.getWrites()) {
			try {
				mTransport.write(write);
			} catch (Exception e) {
				// The command's timeout reports the consequence; this says why.
				out.event(ProtocolEvent.error("write failed: " + e.getMessage()));
			}
		}
		mState = mProtocol.getState();
		mSamplingRate = mProtocol.getSamplingRate();
		List<ProtocolEvent> events = out.getEvents();
		if (events.isEmpty()) {
			return;
		}
		try {
			mEventThread.execute(() -> {
				for (ProtocolEvent e : events) {
					try {
						mListener.onEvent(e);
					} catch (RuntimeException listenerFailed) {
						// One failing listener call must not stop the events after it.
						listenerFailed.printStackTrace();
					}
				}
			});
		} catch (RejectedExecutionException closed) {
			// close() was called.
		}
	}

	private void stopTicker() {
		if (mTicker != null) {
			mTicker.cancel(false);
			mTicker = null;
		}
	}

	private void post(Runnable task) {
		if (mClosed) {
			return;
		}
		try {
			mProtocolThread.execute(task);
		} catch (RejectedExecutionException closed) {
			// close() raced with this call.
		}
	}

	private static ThreadFactory daemon(String name) {
		return r -> {
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			return t;
		};
	}
}
