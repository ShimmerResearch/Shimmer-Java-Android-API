package com.shimmerresearch.driver.shimmer2r3;

import java.io.Serializable;

/**
 * One record of the Shimmer3 / Shimmer3R event channel (DEV-971): a user button
 * or TTL input edge with its own RTC time, or a record that describes the
 * inputs' state. One 16-byte layout serves both the event file on the SD card
 * and the frames a Bluetooth host takes after opting in.
 * <p>
 * Format: log-and-stream-common {@code docs/SHIMMER3_EVENT_CHANNEL.md} §4.2.
 * All fields are little-endian.
 *
 * <pre>
 * 0      type        1 edge, 2 initial state, 3 heartbeat, 4 overflow
 * 1      flags       bit 0 timer-latched, 1 synthesized, 2 late, 3 stale, 7 final
 * 2..3   sequence    in a file it continues across a split; over Bluetooth it is
 *                    the link's own, from 0 at the opt-in
 * 4..11  time        RTC ticks, 32768 a second, all 64 bits
 * 12     channel     0 button, 1 TTL, 0xFF none
 * 13     state       every input after this record: bit 0 button, bit 1 TTL
 * 14..15 dropped     edges lost so far (this session in a file, since the
 *                    opt-in over Bluetooth), stopping at 0xFFFF
 * </pre>
 */
public class EventRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	/** The RTC the records are timed by */
	public static final int TICKS_PER_SECOND = 32768;
	public static final int LENGTH = 16;

	public static final int TYPE_EDGE = 0x01;
	public static final int TYPE_INITIAL_STATE = 0x02;
	public static final int TYPE_HEARTBEAT = 0x03;
	public static final int TYPE_OVERFLOW = 0x04;

	/** The time was latched by a timer (TTL edges) */
	public static final int FLAG_HW_CAPTURE = 0x01;
	/** An edge the debounce first took as bounce, put back at its own time */
	public static final int FLAG_SYNTHESIZED = 0x02;
	/** A TTL capture read more than 32.768 ms after it happened */
	public static final int FLAG_LATE = 0x04;
	/** Reserved */
	public static final int FLAG_STATE_STALE = 0x08;
	/** The last record of a file */
	public static final int FLAG_FINAL = 0x80;

	public static final int CHANNEL_BUTTON = 0x00;
	public static final int CHANNEL_TTL = 0x01;
	/** Records that describe no one input: initial state, heartbeat */
	public static final int CHANNEL_NONE = 0xFF;

	/** Bits of a record's state, and of a file header's or status's inputs */
	public static final int INPUT_BUTTON = 0x01;
	public static final int INPUT_TTL = 0x02;

	private final int mType;
	private final int mFlags;
	private final int mSequenceNumber;
	private final long mTicks;
	private final int mChannel;
	private final int mState;
	private final int mDropped;

	public EventRecord(int type, int flags, int sequenceNumber, long ticks, int channel, int state, int dropped) {
		mType = type;
		mFlags = flags;
		mSequenceNumber = sequenceNumber;
		mTicks = ticks;
		mChannel = channel;
		mState = state;
		mDropped = dropped;
	}

	/**
	 * Decodes the 16-byte record at {@code bytes[offset]}.
	 *
	 * @throws IllegalArgumentException when fewer than 16 bytes remain
	 */
	public static EventRecord parse(byte[] bytes, int offset) {
		if(bytes==null || offset<0 || bytes.length<offset+LENGTH) {
			throw new IllegalArgumentException("An event record needs 16 bytes; "
					+ (bytes==null? 0:Math.max(0, bytes.length-offset)) + " remain.");
		}
		return new EventRecord(
				bytes[offset]&0xFF,
				bytes[offset+1]&0xFF,
				uint16(bytes, offset+2),
				int64(bytes, offset+4),
				bytes[offset+12]&0xFF,
				bytes[offset+13]&0xFF,
				uint16(bytes, offset+14));
	}

	public int getType() {
		return mType;
	}

	public int getFlags() {
		return mFlags;
	}

	/**
	 * In a file it continues across a split. Over Bluetooth it is the link's
	 * own, from 0 at the opt-in, so a gap is records the link lost.
	 */
	public int getSequenceNumber() {
		return mSequenceNumber;
	}

	/** RTC ticks, 32768 a second, all 64 bits. The low 24 bits line up with a data packet's timestamp. */
	public long getTicks() {
		return mTicks;
	}

	public int getChannel() {
		return mChannel;
	}

	/** Every input's state after this record: {@link #INPUT_BUTTON}, {@link #INPUT_TTL} */
	public int getState() {
		return mState;
	}

	/** Edges lost so far: this session in a file, since the opt-in over Bluetooth. Stops at 0xFFFF. */
	public int getDropped() {
		return mDropped;
	}

	public boolean isButtonDown() {
		return (mState&INPUT_BUTTON)!=0;
	}

	public boolean isTtlHigh() {
		return (mState&INPUT_TTL)!=0;
	}

	public boolean isEdge() {
		return mType==TYPE_EDGE;
	}

	public boolean isFinal() {
		return (mFlags&FLAG_FINAL)!=0;
	}

	public boolean isTimerLatched() {
		return (mFlags&FLAG_HW_CAPTURE)!=0;
	}

	/** The time in seconds since {@code originTicks}, for example an event file's open time */
	public double getSecondsFrom(long originTicks) {
		return (double)(mTicks-originTicks)/TICKS_PER_SECOND;
	}

	/**
	 * The record's time on a stream's unwrapped timestamp. A data packet carries
	 * only the low 24 bits of the same clock (they wrap every 512 s), and the
	 * driver unwraps those from wherever the stream began. This picks, of the
	 * values whose low 24 bits match the record's, the one nearest
	 * {@code referenceUnwrappedTicks}, such as a recent sample's unwrapped
	 * timestamp. Right while the two are within 256 s of each other.
	 */
	public double getTicksOnStreamTimeline(double referenceUnwrappedTicks) {
		final long wrap = 1L<<24;
		long low24 = mTicks&(wrap-1);
		long reference = (long)Math.floor(referenceUnwrappedTicks);
		long candidate = reference-Math.floorMod(reference-low24, wrap);
		if(reference-candidate>wrap/2) {
			candidate += wrap;
		}
		return candidate;
	}

	/** A short description, such as "Button pressed" or "TTL low" */
	public String getDescription() {
		switch(mType) {
		case TYPE_EDGE:
			if(mChannel==CHANNEL_BUTTON) {
				return isButtonDown()? "Button pressed":"Button released";
			}
			if(mChannel==CHANNEL_TTL) {
				return isTtlHigh()? "TTL high":"TTL low";
			}
			return "Edge on channel " + mChannel;
		case TYPE_INITIAL_STATE:
			return "Initial state";
		case TYPE_HEARTBEAT:
			return isFinal()? "End of file":"Heartbeat";
		case TYPE_OVERFLOW:
			return "Edges lost";
		default:
			return String.format("Record type 0x%02X", mType);
		}
	}

	@Override
	public String toString() {
		return "EventRecord[seq=" + mSequenceNumber + ", " + getDescription()
				+ ", ticks=0x" + Long.toHexString(mTicks)
				+ ", flags=0x" + Integer.toHexString(mFlags)
				+ ", state=" + mState + ", dropped=" + mDropped + "]";
	}

	static int uint16(byte[] b, int at) {
		return (b[at]&0xFF) | ((b[at+1]&0xFF)<<8);
	}

	static long int64(byte[] b, int at) {
		long v = 0;
		for(int i=7;i>=0;i--) {
			v = (v<<8) | (b[at+i]&0xFF);
		}
		return v;
	}

}
