package com.shimmerresearch.driver.shimmer2r3;

import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;

/**
 * The Bluetooth frame that carries one event channel record to a host that
 * opted in (DEV-971): {@code [0x8A][0xBB][len][record][CRC-16]}, 21 bytes with
 * the firmware's 16-byte record.
 * <p>
 * Format: log-and-stream-common {@code docs/SHIMMER3_BT_COMMUNICATION_PROTOCOL.md}
 * §7.15. The frame has no leading ACK, and carries its own CRC-16 (seed 0xB0CA,
 * an odd length padded with one 0x00, as the link CRC) whatever CRC mode the
 * link is in, never the link CRC on top. A frame is only taken once that CRC
 * checks out, which is what lets a parser find one between data packets with
 * confidence.
 */
public class EventFrame {

	public static final byte INSTREAM_CMD_RESPONSE = (byte) 0x8A;
	/** The frame's subtype after {@link #INSTREAM_CMD_RESPONSE} */
	public static final byte EVENT_RECORD_RESPONSE = (byte) 0xBB;
	/** Prefix, subtype and length byte */
	public static final int HEAD_LENGTH = 3;
	public static final int CRC_LENGTH = 2;
	/** A frame with the firmware's 16-byte record */
	public static final int LENGTH = HEAD_LENGTH+EventRecord.LENGTH+CRC_LENGTH;
	/**
	 * The longest record a frame is believed to carry. The firmware sends 16; a
	 * later one may send more, which a host skips. Anything longer is taken for
	 * a stray 0x8A rather than a giant frame.
	 */
	public static final int MAX_RECORD_LENGTH = 64;

	/** {@link #getFrameLength} result: the bytes so far could still be a frame */
	public static final int NEED_MORE = -1;
	/** {@link #getFrameLength} result: they are not a frame */
	public static final int NOT_A_FRAME = 0;

	private EventFrame() {
	}

	/**
	 * The length of the frame at {@code buf[at]}, of the first {@code available}
	 * bytes there: {@link #NEED_MORE} when they end before that can be told, or
	 * {@link #NOT_A_FRAME} when they are not a frame whose CRC checks out.
	 */
	public static int getFrameLength(byte[] buf, int at, int available) {
		if(available<1) {
			return NEED_MORE;
		}
		if(buf[at]!=INSTREAM_CMD_RESPONSE) {
			return NOT_A_FRAME;
		}
		if(available<2) {
			return NEED_MORE;
		}
		if(buf[at+1]!=EVENT_RECORD_RESPONSE) {
			return NOT_A_FRAME;
		}
		if(available<HEAD_LENGTH) {
			return NEED_MORE;
		}
		int recordLength = buf[at+2]&0xFF;
		if(recordLength<EventRecord.LENGTH || recordLength>MAX_RECORD_LENGTH) {
			return NOT_A_FRAME;
		}
		int total = HEAD_LENGTH+recordLength+CRC_LENGTH;
		if(available<total) {
			return NEED_MORE;
		}
		return isCrcValid(buf, at, total)? total:NOT_A_FRAME;
	}

	/** Same as {@link #getFrameLength(byte[], int, int)} over the rest of the array */
	public static int getFrameLength(byte[] buf, int at) {
		return getFrameLength(buf, at, buf.length-at);
	}

	/** The record in a whole frame starting at {@code buf[at]}, or null when it is not one whose CRC checks out */
	public static EventRecord parse(byte[] buf, int at) {
		if(getFrameLength(buf, at)<=0) {
			return null;
		}
		return EventRecord.parse(buf, at+HEAD_LENGTH);
	}

	private static boolean isCrcValid(byte[] buf, int at, int total) {
		byte[] frame = new byte[total];
		System.arraycopy(buf, at, frame, 0, total);
		return ShimmerCrc.shimmerUartCrcCheck(frame);
	}

}
