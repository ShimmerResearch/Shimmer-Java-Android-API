package com.shimmerresearch.hil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * A ShimmerPC that behaves exactly as an app's does (timers on, the real serial port) but counts
 * what the tests need to see: each in-stream reply by type, each status the driver decoded, each
 * battery reading, and every line the driver logs, whether or not verbose mode is on. Nothing here
 * changes what the driver does.
 */
class HilShimmerPC extends ShimmerPC {

	private static final long serialVersionUID = 1L;

	/** Lines kept per session; enough for minutes of commands, bounded if something loops. */
	private static final int MAX_LOG_LINES = 50000;

	private final long t0;
	// What the session counted is transient: the driver's threads add to it all the time, and
	// configureAccelOnly copies this device by serialising it (deepClone()), which threw
	// ConcurrentModificationException when a line was logged mid-copy (DEV-1310).
	private transient Map<String, Integer> inStreamReplies = new TreeMap<>();
	private transient List<String> log = new ArrayList<>();
	private volatile int statusesDecoded;
	private volatile int batteryReadings;
	/** When each battery reply was decoded, in ms on the session's clock (HilSession.nowMs()). */
	private transient List<Double> batteryReplyTimesMs = new ArrayList<>();

	HilShimmerPC(String comPort, long t0Nanos) {
		super(comPort);
		this.t0 = t0Nanos;
	}

	/** A copy starts counting from nothing; a deserialised object runs no field initialisers. */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		inStreamReplies = new TreeMap<>();
		log = new ArrayList<>();
		batteryReplyTimesMs = new ArrayList<>();
	}

	@Override
	protected void processInstreamResponse(byte inStreamResponseCommand, boolean shouldClearCrcFromBuffer) {
		synchronized (inStreamReplies) {
			inStreamReplies.merge(replyName(inStreamResponseCommand), 1, Integer::sum);
		}
		super.processInstreamResponse(inStreamResponseCommand, shouldClearCrcFromBuffer);
	}

	@Override
	protected void eventLogAndStreamStatusChanged(byte currentCommand) {
		statusesDecoded++;
		super.eventLogAndStreamStatusChanged(currentCommand);
	}

	@Override
	public void setBattStatusDetails(ShimmerBattStatusDetails s) {
		batteryReadings++;
		synchronized (batteryReplyTimesMs) {
			batteryReplyTimesMs.add((System.nanoTime() - t0) / 1e6);
		}
		super.setBattStatusDetails(s);
	}

	List<Double> batteryReplyTimesMs() {
		synchronized (batteryReplyTimesMs) {
			return new ArrayList<>(batteryReplyTimesMs);
		}
	}

	@Override
	public void consolePrintLn(String message) {
		record(message);
		super.consolePrintLn(message);
	}

	@Override
	public void consolePrintErrLn(String message) {
		record("ERR " + message);
		super.consolePrintErrLn(message);
	}

	/** The driver prints an exception's message to System.out, past consolePrintLn; keep it in the log too. */
	@Override
	protected void consolePrintException(String message, StackTraceElement[] stackTrace) {
		record("EXCEPTION " + message);
		super.consolePrintException(message, stackTrace);
	}

	private void record(String message) {
		String line = String.format(Locale.ROOT, "%9.0f %s", (System.nanoTime() - t0) / 1e6, message);
		synchronized (log) {
			if (log.size() < MAX_LOG_LINES) {
				log.add(line);
			}
		}
	}

	static String replyName(byte b) {
		switch (b) {
		case STATUS_RESPONSE:
			return "STATUS";
		case DIR_RESPONSE:
			return "DIR";
		case VBATT_RESPONSE:
			return "VBATT";
		default:
			return String.format(Locale.ROOT, "0x%02X", b & 0xFF);
		}
	}

	int inStreamReplies(String type) {
		synchronized (inStreamReplies) {
			return inStreamReplies.getOrDefault(type, 0);
		}
	}

	Map<String, Integer> inStreamReplies() {
		synchronized (inStreamReplies) {
			return new TreeMap<>(inStreamReplies);
		}
	}

	/** Which of the driver's periodic timers exist right now: an app runs with all three. */
	String timers() {
		List<String> t = new ArrayList<>();
		if (mTimerReadStatus != null) {
			t.add("status");
		}
		if (mTimerCheckAlive != null) {
			t.add("alive");
		}
		if (mTimerReadBattStatus != null) {
			t.add("battery");
		}
		return t.isEmpty() ? "none" : String.join("+", t);
	}

	/** Instructions waiting to be sent, read through the driver's own (synchronized) accessor. */
	int queuedInstructions() {
		return getListofInstructions().size();
	}

	int statusesDecoded() {
		return statusesDecoded;
	}

	int batteryReadings() {
		return batteryReadings;
	}

	int logSize() {
		synchronized (log) {
			return log.size();
		}
	}

	List<String> log() {
		synchronized (log) {
			return new ArrayList<>(log);
		}
	}

	/** Lines logged since index from that match the pattern anywhere. */
	int countSince(int from, java.util.regex.Pattern pattern) {
		int n = 0;
		synchronized (log) {
			for (int i = Math.max(0, from); i < log.size(); i++) {
				if (pattern.matcher(log.get(i)).find()) {
					n++;
				}
			}
		}
		return n;
	}

	/** Lines logged since index from that contain any of the given texts. */
	int countSince(int from, String... texts) {
		int n = 0;
		synchronized (log) {
			for (int i = Math.max(0, from); i < log.size(); i++) {
				for (String t : texts) {
					if (log.get(i).contains(t)) {
						n++;
						break;
					}
				}
			}
		}
		return n;
	}
}
