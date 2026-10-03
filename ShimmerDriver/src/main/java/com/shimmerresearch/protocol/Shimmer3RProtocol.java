package com.shimmerresearch.protocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;

/**
 * The Shimmer3R Bluetooth protocol as an I/O-free state machine (DEV-1134 prototype).
 * <p>
 * The host owns the transport and the clock. It calls {@link #connect}, then passes every
 * received chunk to {@link #receive} and calls {@link #tick} when {@link #nextDeadline} is due.
 * Each call returns a {@link ProtocolOutput}: the bytes to write, one entry per write, and the
 * events to deliver. Nothing here blocks, sleeps, starts a thread or calls back into the host, so
 * the same object works over any transport and can be driven by recorded bytes in a test.
 * <p>
 * Times are wall-clock milliseconds (System.currentTimeMillis): SET_RWC sends the clock to the
 * device. Decoding and calibration are delegated unchanged to the existing driver code through
 * {@link Shimmer3RModel}.
 * <p>
 * Scope: Shimmer3R with firmware that reads config bytes and the calibration dump over Bluetooth.
 * Not handled yet: a device still streaming when the handshake starts, unsolicited status
 * responses, and in-stream commands while streaming.
 */
public final class Shimmer3RProtocol {

	public enum State {
		DISCONNECTED, CONNECTING, READY, STARTING, STREAMING, STOPPING, FAILED
	}

	/** How long to wait for an ACK or response, as in ShimmerBluetooth. */
	public static final long DEFAULT_TIMEOUT_MS = 2000;
	/** For the large memory reads. */
	public static final long LONG_TIMEOUT_MS = 5000;
	/** The most bytes the firmware returns per config-byte or calibration-dump read. */
	static final int MEM_CHUNK = 128;
	/** Config bytes (firmware code 6) and the calibration dump (7) over Bluetooth. */
	static final int MIN_FIRMWARE_VERSION_CODE = 7;
	/** Shimmer3R inquiry: 11 settings bytes, the 10th of which is the channel count. */
	static final int INQUIRY_SETTINGS_LENGTH = 11;
	static final int INQUIRY_CHANNEL_COUNT_INDEX = 9;

	private static final byte ACK = ShimmerObject.ACK_COMMAND_PROCESSED;
	private static final byte DATA_PACKET = ShimmerObject.DATA_PACKET;

	/** Reads the payload that follows a response code. */
	private interface Response {
		/** Payload length given the bytes received so far (from the payload's start), or -1 if not yet known. */
		int length(RxBuffer rx, int start);

		void handle(byte[] payload, long nowMs, ProtocolOutput out);
	}

	private static final class Command {
		final String name;
		final byte[] bytes;
		/** null for a command that is only acknowledged. */
		final Byte responseCode;
		final Response response;
		final long timeoutMs;

		Command(String name, byte[] bytes, Byte responseCode, Response response, long timeoutMs) {
			this.name = name;
			this.bytes = bytes;
			this.responseCode = responseCode;
			this.response = response;
			this.timeoutMs = timeoutMs;
		}
	}

	private final Shimmer3RModel mModel = new Shimmer3RModel();
	private final Deque<Command> mQueue = new ArrayDeque<Command>();
	private final RxBuffer mRx = new RxBuffer();
	private State mState = State.DISCONNECTED;
	private Command mInFlight;
	private boolean mAckSeen;
	private long mDeadline = Long.MAX_VALUE;
	private int mCrcBytes = 0;
	private int mDroppedBytes = 0;

	private byte[] mConfigBytes = new byte[0];
	private int mConfigLength;
	private byte[] mCalibDump = new byte[0];
	private int mCalibDumpLength = -1;

	public State getState() {
		return mState;
	}

	/** When the host should next call {@link #tick}, or Long.MAX_VALUE if nothing is pending. */
	public long nextDeadline() {
		return mInFlight == null ? Long.MAX_VALUE : mDeadline;
	}

	public double getSamplingRate() {
		return mModel.getSamplingRateShimmer();
	}

	/**
	 * The device as configured by the handshake, for read-only uses such as listing its enabled
	 * channels in a UI. Complete once the state is {@link State#READY}. Changing it does not
	 * change the device: this state machine does not write configuration yet.
	 */
	public ShimmerDevice getDeviceModel() {
		return mModel;
	}

	/** Starts the handshake. */
	public ProtocolOutput connect(long nowMs) {
		ProtocolOutput out = new ProtocolOutput();
		if (mState != State.DISCONNECTED) {
			return failed(out, "connect() called in state " + mState);
		}
		setState(State.CONNECTING, out);
		mQueue.add(new Command("GET_SHIMMER_VERSION", new byte[] { ShimmerObject.GET_SHIMMER_VERSION_COMMAND_NEW },
				ShimmerObject.GET_SHIMMER_VERSION_RESPONSE, fixed(1, this::onHardwareVersion), DEFAULT_TIMEOUT_MS));
		mQueue.add(new Command("GET_FW_VERSION", new byte[] { ShimmerObject.GET_FW_VERSION_COMMAND },
				ShimmerObject.FW_VERSION_RESPONSE, fixed(6, this::onFirmwareVersion), DEFAULT_TIMEOUT_MS));
		sendNext(nowMs, out);
		return out;
	}

	public ProtocolOutput startStreaming(long nowMs) {
		ProtocolOutput out = new ProtocolOutput();
		if (mState != State.READY) {
			return failed(out, "startStreaming() called in state " + mState);
		}
		try {
			mModel.prepareForStreaming();
		} catch (ShimmerException e) {
			return failed(out, "could not prepare for streaming: " + e.getMessage());
		}
		setState(State.STARTING, out);
		mQueue.add(new Command("START_STREAMING", new byte[] { ShimmerObject.START_STREAMING_COMMAND }, null, null,
				DEFAULT_TIMEOUT_MS));
		sendNext(nowMs, out);
		return out;
	}

	public ProtocolOutput stopStreaming(long nowMs) {
		ProtocolOutput out = new ProtocolOutput();
		if (mState != State.STREAMING) {
			return failed(out, "stopStreaming() called in state " + mState);
		}
		setState(State.STOPPING, out);
		mQueue.add(new Command("STOP_STREAMING", new byte[] { ShimmerObject.STOP_STREAMING_COMMAND }, null, null,
				DEFAULT_TIMEOUT_MS));
		sendNext(nowMs, out);
		return out;
	}

	/** Bytes received from the device, in the order they arrived. */
	public ProtocolOutput receive(byte[] bytes, long nowMs) {
		ProtocolOutput out = new ProtocolOutput();
		if (mState == State.DISCONNECTED || mState == State.FAILED) {
			return out;
		}
		mRx.append(bytes);
		boolean progressed = true;
		while (progressed && mRx.size() > 0 && mState != State.FAILED) {
			boolean streamingBytes = mState == State.STREAMING || mState == State.STOPPING;
			progressed = streamingBytes ? receiveStreaming(nowMs, out) : receiveResponse(nowMs, out);
		}
		return out;
	}

	/** Fires a command timeout if one is due. */
	public ProtocolOutput tick(long nowMs) {
		ProtocolOutput out = new ProtocolOutput();
		if (mInFlight != null && nowMs >= mDeadline) {
			failed(out, "no " + (mAckSeen ? "response" : "ACK") + " to " + mInFlight.name + " within " + mInFlight.timeoutMs + " ms");
		}
		return out;
	}

	// --- Commands and responses -------------------------------------------------------------

	private void sendNext(long nowMs, ProtocolOutput out) {
		if (mInFlight != null || mState == State.FAILED) {
			return;
		}
		Command next = mQueue.poll();
		if (next == null) {
			onQueueEmpty(out);
			return;
		}
		mInFlight = next;
		mAckSeen = false;
		mDeadline = nowMs + next.timeoutMs;
		byte[] bytes = next.bytes;
		if (bytes[0] == ShimmerObject.SET_RWC_COMMAND) {
			// Stamped when sent, not when queued, as the driver does.
			bytes = concat(new byte[] { ShimmerObject.SET_RWC_COMMAND },
					UtilShimmer.convertMilliSecondsToShimmerRtcDataBytesLSB(nowMs));
		} else if (bytes[0] == ShimmerObject.SET_CRC_COMMAND) {
			// The device answers SET_CRC already in the new mode.
			BT_CRC_MODE mode = BT_CRC_MODE.values()[bytes[1]];
			mModel.applyCrcMode(mode);
			mCrcBytes = mode.getNumCrcBytes();
		}
		out.write(bytes);
	}

	private void complete(long nowMs, ProtocolOutput out) {
		Command done = mInFlight;
		mInFlight = null;
		mDeadline = Long.MAX_VALUE;
		if (done.bytes[0] == ShimmerObject.START_STREAMING_COMMAND) {
			mModel.streamingStarted();
			setState(State.STREAMING, out);
		} else if (done.bytes[0] == ShimmerObject.STOP_STREAMING_COMMAND) {
			mModel.streamingStopped();
			setState(State.READY, out);
		}
		sendNext(nowMs, out);
	}

	private void onQueueEmpty(ProtocolOutput out) {
		if (mState == State.CONNECTING) {
			setState(State.READY, out);
			out.event(ProtocolEvent.initialised("Shimmer3R " + mModel.getFirmwareVersionParsed() + ", "
					+ mModel.getSamplingRateShimmer() + " Hz, packet size " + mModel.getPacketSize()));
		}
	}

	/** Not streaming: an ACK, then the expected response (if any), then the CRC. */
	private boolean receiveResponse(long nowMs, ProtocolOutput out) {
		if (mInFlight == null) {
			// Nothing was asked for. Unsolicited bytes are not handled yet; drop them.
			mDroppedBytes += mRx.size();
			mRx.consume(mRx.size());
			reportDropped(out);
			return false;
		}
		if (!mAckSeen) {
			int ack = mRx.indexOf(ACK);
			if (ack < 0) {
				mDroppedBytes += mRx.size();
				mRx.consume(mRx.size());
				return false;
			}
			mDroppedBytes += ack;
			mRx.consume(ack);
			if (mInFlight.responseCode == null) {
				// ACK only: the ACK and its CRC.
				if (mRx.size() < 1 + mCrcBytes) {
					return false;
				}
				checkResponseCrc(mRx.copy(0, 1 + mCrcBytes), 1, out);
				mRx.consume(1 + mCrcBytes);
				reportDropped(out);
				complete(nowMs, out);
				return true;
			}
			mRx.consume(1);
			mAckSeen = true;
			reportDropped(out);
		}
		if (mRx.size() < 1) {
			return false;
		}
		if (mRx.get(0) != mInFlight.responseCode) {
			failed(out, "expected response " + hex(mInFlight.responseCode) + " to " + mInFlight.name + ", got " + hex(mRx.get(0)));
			return false;
		}
		int length = mInFlight.response.length(mRx, 1);
		if (length < 0 || mRx.size() < 1 + length + mCrcBytes) {
			return false;
		}
		byte[] payload = mRx.copy(1, length);
		// The CRC covers the ACK too, which is no longer in the buffer.
		byte[] framed = concat(new byte[] { ACK }, mRx.copy(0, 1 + length + mCrcBytes));
		checkResponseCrc(framed, 2 + length, out);
		mRx.consume(1 + length + mCrcBytes);
		mInFlight.response.handle(payload, nowMs, out);
		if (mState != State.FAILED) {
			complete(nowMs, out);
		}
		return true;
	}

	/** Streaming: data packets, plus the ACK of STOP_STREAMING. */
	private boolean receiveStreaming(long nowMs, ProtocolOutput out) {
		byte first = mRx.get(0);
		if (first == DATA_PACKET) {
			int packetSize = mModel.getPacketSize();
			// With no CRC, the next packet's header is what confirms this packet's length.
			int needed = 1 + packetSize + (mCrcBytes > 0 ? mCrcBytes : 1);
			if (mRx.size() < needed) {
				return false;
			}
			boolean valid = mCrcBytes > 0 ? packetCrcMatches(packetSize) : isPacketStart(mRx.get(1 + packetSize));
			if (!valid) {
				mRx.consume(1);
				mDroppedBytes++;
				return true;
			}
			reportDropped(out);
			ObjectCluster sample = mModel.decode(mRx.copy(1, packetSize), nowMs);
			mRx.consume(1 + packetSize + mCrcBytes);
			out.event(ProtocolEvent.sample(sample));
			return true;
		}
		if (first == ACK && mInFlight != null && !mAckSeen && mInFlight.responseCode == null) {
			if (mRx.size() < 1 + mCrcBytes) {
				return false;
			}
			mRx.consume(1 + mCrcBytes);
			reportDropped(out);
			complete(nowMs, out);
			return true;
		}
		mRx.consume(1);
		mDroppedBytes++;
		return true;
	}

	private boolean packetCrcMatches(int packetSize) {
		byte[] frame = mRx.copy(0, 1 + packetSize + mCrcBytes);
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(frame, 1 + packetSize);
		if (frame[1 + packetSize] != crc[0]) {
			return false;
		}
		return mCrcBytes < 2 || frame[2 + packetSize] == crc[1];
	}

	private static boolean isPacketStart(byte b) {
		return b == DATA_PACKET || b == ACK;
	}

	/** Responses' CRCs are reported, not enforced: the existing driver does not check them at all. */
	private void checkResponseCrc(byte[] framed, int coveredLength, ProtocolOutput out) {
		if (mCrcBytes == 0) {
			return;
		}
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(framed, coveredLength);
		boolean ok = framed[coveredLength] == crc[0] && (mCrcBytes < 2 || framed[coveredLength + 1] == crc[1]);
		if (!ok) {
			out.event(ProtocolEvent.discarded("CRC mismatch on the reply to " + mInFlight.name + " (not enforced)"));
		}
	}

	private void reportDropped(ProtocolOutput out) {
		if (mDroppedBytes > 0) {
			out.event(ProtocolEvent.discarded(mDroppedBytes + " unexpected byte(s) dropped"));
			mDroppedBytes = 0;
		}
	}

	// --- Handshake steps ----------------------------------------------------------------------

	private void onHardwareVersion(byte[] payload, long nowMs, ProtocolOutput out) {
		mModel.applyHardwareVersion(payload[0]);
		if (mModel.getHardwareVersion() != HW_ID.SHIMMER_3R) {
			failed(out, "device reports hardware version " + (payload[0] & 0xFF) + ", not a Shimmer3R");
		}
	}

	private void onFirmwareVersion(byte[] payload, long nowMs, ProtocolOutput out) {
		mModel.applyFirmwareVersion(payload);
		if (mModel.getFirmwareVersionCode() < MIN_FIRMWARE_VERSION_CODE) {
			failed(out, "firmware " + mModel.getFirmwareVersionParsed() + " cannot send its config and calibration over Bluetooth");
			return;
		}
		// The rest of the handshake depends on the firmware, so it is queued only now.
		mQueue.add(new Command("GET_DAUGHTER_CARD_ID", new byte[] { ShimmerObject.GET_DAUGHTER_CARD_ID_COMMAND, 0x03, 0x00 },
				ShimmerObject.DAUGHTER_CARD_ID_RESPONSE, lengthPrefixed(1, this::onExpansionBoard), DEFAULT_TIMEOUT_MS));
		if (mModel.isBtCrcModeSupported()) {
			mQueue.add(new Command("SET_CRC", new byte[] { ShimmerObject.SET_CRC_COMMAND, (byte) BT_CRC_MODE.ONE_BYTE_CRC.ordinal() },
					null, null, DEFAULT_TIMEOUT_MS));
		}
		mConfigLength = mModel.configByteLength();
		mConfigBytes = new byte[0];
		int start = mModel.configByteStartAddress();
		for (int offset = 0; offset < mConfigLength; offset += MEM_CHUNK) {
			int size = Math.min(MEM_CHUNK, mConfigLength - offset);
			mQueue.add(memRead("GET_INFOMEM", ShimmerObject.GET_INFOMEM_COMMAND, start + offset, size,
					ShimmerObject.INFOMEM_RESPONSE, lengthPrefixed(1, this::onConfigBytes)));
		}
		mQueue.add(new Command("GET_PRESSURE_CALIBRATION_COEFFICIENTS",
				new byte[] { ShimmerObject.GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND },
				ShimmerObject.PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE, lengthPrefixed(1, this::onPressureCoefficients),
				DEFAULT_TIMEOUT_MS));
		mCalibDump = new byte[0];
		mCalibDumpLength = -1;
		mQueue.add(calibDumpRead(0, MEM_CHUNK));
		mQueue.add(new Command("INQUIRY", new byte[] { ShimmerObject.INQUIRY_COMMAND }, ShimmerObject.INQUIRY_RESPONSE,
				inquiryResponse(), DEFAULT_TIMEOUT_MS));
		mQueue.add(new Command("SET_RWC", new byte[] { ShimmerObject.SET_RWC_COMMAND }, null, null, DEFAULT_TIMEOUT_MS));
	}

	private void onExpansionBoard(byte[] payload, long nowMs, ProtocolOutput out) {
		// [length, id, revision, special revision], as the driver splits it.
		mModel.applyExpansionBoard(Arrays.copyOfRange(payload, 1, 4));
	}

	private void onConfigBytes(byte[] payload, long nowMs, ProtocolOutput out) {
		mConfigBytes = concat(mConfigBytes, Arrays.copyOfRange(payload, 1, payload.length));
		if (mConfigBytes.length >= mConfigLength) {
			mModel.applyConfigBytes(mConfigBytes);
		}
	}

	private void onPressureCoefficients(byte[] payload, long nowMs, ProtocolOutput out) {
		String rejected = mModel.applyPressureCoefficients(Arrays.copyOfRange(payload, 1, payload.length));
		if (rejected != null) {
			out.event(ProtocolEvent.discarded("pressure calibration coefficients rejected: " + rejected));
		}
	}

	private void onCalibDump(byte[] payload, long nowMs, ProtocolOutput out) {
		// [length, address LSB, address MSB, data...]
		byte[] data = Arrays.copyOfRange(payload, 3, payload.length);
		boolean first = mCalibDumpLength < 0;
		mCalibDump = concat(mCalibDump, data);
		if (first) {
			// The dump starts with its own length, which does not count those two bytes.
			mCalibDumpLength = (((data[1] & 0xFF) << 8) | (data[0] & 0xFF)) + 2;
			List<Command> rest = new ArrayList<Command>();
			for (int address = MEM_CHUNK; address < mCalibDumpLength; address += MEM_CHUNK) {
				rest.add(calibDumpRead(address, Math.min(MEM_CHUNK, mCalibDumpLength - address)));
			}
			// Read the rest of the dump before anything else that is queued.
			for (int i = rest.size() - 1; i >= 0; i--) {
				mQueue.addFirst(rest.get(i));
			}
		}
		if (mCalibDump.length >= mCalibDumpLength) {
			mModel.applyCalibrationDump(Arrays.copyOf(mCalibDump, mCalibDumpLength));
		}
	}

	private Command calibDumpRead(int address, int size) {
		return memRead("GET_CALIB_DUMP", ShimmerObject.GET_CALIB_DUMP_COMMAND, address, size,
				ShimmerObject.RSP_CALIB_DUMP_COMMAND, lengthPrefixed(3, this::onCalibDump));
	}

	/** [command, length, address LSB, address MSB], as ShimmerBluetooth.readMemCommand. */
	private static Command memRead(String name, byte command, int address, int size, byte responseCode, Response response) {
		byte[] bytes = new byte[] { command, (byte) size, (byte) (address & 0xFF), (byte) ((address >> 8) & 0xFF) };
		return new Command(name + "@" + address, bytes, responseCode, response, LONG_TIMEOUT_MS);
	}

	private Response inquiryResponse() {
		return new Response() {
			@Override
			public int length(RxBuffer rx, int start) {
				if (rx.size() < start + INQUIRY_SETTINGS_LENGTH) {
					return -1;
				}
				return INQUIRY_SETTINGS_LENGTH + (rx.get(start + INQUIRY_CHANNEL_COUNT_INDEX) & 0xFF);
			}

			@Override
			public void handle(byte[] payload, long nowMs, ProtocolOutput out) {
				mModel.applyInquiry(payload);
			}
		};
	}

	// --- Helpers --------------------------------------------------------------------------------

	private interface Handler {
		void handle(byte[] payload, long nowMs, ProtocolOutput out);
	}

	private static Response fixed(final int length, final Handler handler) {
		return new Response() {
			@Override
			public int length(RxBuffer rx, int start) {
				return length;
			}

			@Override
			public void handle(byte[] payload, long nowMs, ProtocolOutput out) {
				handler.handle(payload, nowMs, out);
			}
		};
	}

	/** A payload whose first byte is the length of the data that follows {@code headerLength} bytes. */
	private static Response lengthPrefixed(final int headerLength, final Handler handler) {
		return new Response() {
			@Override
			public int length(RxBuffer rx, int start) {
				return rx.size() <= start ? -1 : headerLength + (rx.get(start) & 0xFF);
			}

			@Override
			public void handle(byte[] payload, long nowMs, ProtocolOutput out) {
				handler.handle(payload, nowMs, out);
			}
		};
	}

	private void setState(State state, ProtocolOutput out) {
		if (mState != state) {
			mState = state;
			out.event(ProtocolEvent.stateChanged(state));
		}
	}

	private ProtocolOutput failed(ProtocolOutput out, String why) {
		mInFlight = null;
		mDeadline = Long.MAX_VALUE;
		mQueue.clear();
		out.event(ProtocolEvent.error(why));
		setState(State.FAILED, out);
		return out;
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] joined = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, joined, a.length, b.length);
		return joined;
	}

	private static String hex(byte b) {
		return String.format("0x%02X", b & 0xFF);
	}

	/** A growable byte buffer consumed from the front. */
	static final class RxBuffer {
		private byte[] mData = new byte[256];
		private int mStart = 0;
		private int mEnd = 0;

		int size() {
			return mEnd - mStart;
		}

		byte get(int i) {
			return mData[mStart + i];
		}

		void append(byte[] bytes) {
			if (mEnd + bytes.length > mData.length) {
				int size = size();
				byte[] grown = new byte[Math.max(mData.length, (size + bytes.length) * 2)];
				System.arraycopy(mData, mStart, grown, 0, size);
				mData = grown;
				mStart = 0;
				mEnd = size;
			}
			System.arraycopy(bytes, 0, mData, mEnd, bytes.length);
			mEnd += bytes.length;
		}

		void consume(int n) {
			mStart += Math.min(n, size());
			if (mStart == mEnd) {
				mStart = 0;
				mEnd = 0;
			}
		}

		byte[] copy(int from, int length) {
			return Arrays.copyOfRange(mData, mStart + from, mStart + from + length);
		}

		int indexOf(byte b) {
			for (int i = mStart; i < mEnd; i++) {
				if (mData[i] == b) {
					return i - mStart;
				}
			}
			return -1;
		}
	}
}
