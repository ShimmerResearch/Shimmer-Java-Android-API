package com.shimmerresearch.driver.shimmer2r3;

import java.io.Serializable;

/**
 * The answer to GET_EVENT_CHANNEL_STATUS_COMMAND (0xB9), which is
 * {@code [ACK][0xBA][status x 9]} (DEV-971; log-and-stream-common
 * {@code docs/SHIMMER3_BT_COMMUNICATION_PROTOCOL.md} §7.15).
 *
 * <pre>
 * 0     inputs the configuration enables: bit 0 button markers, bit 1 TTL
 * 1     bit 0 the event stream is on for this connection, bit 1 an event file
 *       is open, bit 2 the event file was stopped by an error, bit 3 the TTL
 *       input is armed, bit 4 the TTL input is off for a second after a flood
 * 2     each input's state now: bit 0 button, bit 1 TTL
 * 3..4  the link's next sequence number
 * 5..6  edges lost this session, the event file's dropped count
 * 7..8  records the link lost since the opt-in, because its queue was full
 * </pre>
 */
public class EventChannelStatus implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Length of the status after the 0xBA opcode */
	public static final int LENGTH = 9;

	private final int mInputsEnabled;
	private final int mFlags;
	private final int mState;
	private final int mNextSequenceNumber;
	private final int mSessionDropped;
	private final int mLinkLost;

	private EventChannelStatus(byte[] b, int at) {
		mInputsEnabled = b[at]&0xFF;
		mFlags = b[at+1]&0xFF;
		mState = b[at+2]&0xFF;
		mNextSequenceNumber = EventRecord.uint16(b, at+3);
		mSessionDropped = EventRecord.uint16(b, at+5);
		mLinkLost = EventRecord.uint16(b, at+7);
	}

	/**
	 * Decodes the 9 status bytes at {@code bytes[offset]}.
	 *
	 * @throws IllegalArgumentException when fewer than 9 remain
	 */
	public static EventChannelStatus parse(byte[] bytes, int offset) {
		if(bytes==null || offset<0 || bytes.length<offset+LENGTH) {
			throw new IllegalArgumentException("Event channel status needs 9 bytes.");
		}
		return new EventChannelStatus(bytes, offset);
	}

	/** {@link EventRecord#INPUT_BUTTON}, {@link EventRecord#INPUT_TTL} */
	public int getInputsEnabled() {
		return mInputsEnabled;
	}

	/** The stream is on for this connection, as asked; the firmware applies it moments later */
	public boolean isStreaming() {
		return (mFlags&0x01)!=0;
	}

	public boolean isFileOpen() {
		return (mFlags&0x02)!=0;
	}

	/** The event file was stopped by an error, this session or the last */
	public boolean isFileError() {
		return (mFlags&0x04)!=0;
	}

	public boolean isTtlArmed() {
		return (mFlags&0x08)!=0;
	}

	/** The TTL input is switched off for a second after a flood */
	public boolean isTtlThrottled() {
		return (mFlags&0x10)!=0;
	}

	/** Each input's state now: {@link EventRecord#INPUT_BUTTON}, {@link EventRecord#INPUT_TTL} */
	public int getState() {
		return mState;
	}

	public int getNextSequenceNumber() {
		return mNextSequenceNumber;
	}

	public int getSessionDropped() {
		return mSessionDropped;
	}

	public int getLinkLost() {
		return mLinkLost;
	}

	@Override
	public String toString() {
		return "EventChannelStatus[inputs=" + mInputsEnabled
				+ ", streaming=" + isStreaming() + ", fileOpen=" + isFileOpen() + ", fileError=" + isFileError()
				+ ", ttlArmed=" + isTtlArmed() + ", ttlThrottled=" + isTtlThrottled()
				+ ", state=" + mState + ", nextSeq=" + mNextSequenceNumber
				+ ", sessionDropped=" + mSessionDropped + ", linkLost=" + mLinkLost + "]";
	}

}
