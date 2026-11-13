package com.shimmerresearch.driver.shimmer2r3;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An event file of the Shimmer3 / Shimmer3R event channel (DEV-971):
 * {@code events/<experiment>/<session>/<NNN>} on the SD card, the companion of
 * the data file {@code data/<experiment>/<session>/<NNN>}. It holds the user
 * button's and the TTL input's records for that data file.
 * <p>
 * Format: log-and-stream-common {@code docs/SHIMMER3_EVENT_CHANNEL.md} §4: a
 * 64-byte header, then 16-byte {@link EventRecord}s, all little-endian. The
 * header's own lengths are honoured, so a later format with a longer header or
 * longer records still decodes, its extra bytes skipped.
 *
 * <pre>
 * 0      magic "SEVT"            24  config time, Unix seconds
 * 4      format version, 1       28  firmware ID
 * 5      header length, 64       30  firmware major
 * 6      record length, 16       32  firmware minor
 * 7      hardware ID             33  firmware internal
 * 8..15  file-open time, ticks   34  first record's sequence number
 * 16..23 Shimmer3 RTC offset     36  open reason: 1 session start, 2 split
 *                                37  inputs recorded: bit 0 button, bit 1 TTL
 * </pre>
 */
public class EventFile implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String MAGIC = "SEVT";
	public static final int FORMAT_VERSION = 1;
	public static final int HEADER_LENGTH = 64;

	public static final int OPEN_REASON_SESSION_START = 1;
	public static final int OPEN_REASON_SPLIT = 2;

	/** Thrown for bytes that are not an event file this decoder can read */
	public static class FormatException extends IOException {
		private static final long serialVersionUID = 1L;
		public FormatException(String message) {
			super(message);
		}
	}

	private int mVersion;
	private int mHeaderLength;
	private int mRecordLength;
	private int mHardwareId;
	private long mFileOpenTicks;
	private long mRtcOffsetTicks;
	private long mConfigTime;
	private int mFirmwareId;
	private int mFirmwareMajor;
	private int mFirmwareMinor;
	private int mFirmwareInternal;
	private int mFirstSequenceNumber;
	private int mOpenReason;
	private int mInputs;
	private List<EventRecord> mRecords = new ArrayList<EventRecord>();
	private int mTrailingBytes;

	private EventFile() {
	}

	public static EventFile parse(File file) throws IOException {
		return parse(Files.readAllBytes(file.toPath()));
	}

	/**
	 * Decodes an event file's bytes.
	 *
	 * @throws FormatException when the magic, version or lengths are wrong
	 */
	public static EventFile parse(byte[] bytes) throws FormatException {
		if(bytes==null || bytes.length<8) {
			throw new FormatException("Too short to be an event file.");
		}
		String magic = new String(bytes, 0, 4, StandardCharsets.ISO_8859_1);
		if(!MAGIC.equals(magic)) {
			throw new FormatException("Not an event file: starts \"" + magic + "\", not \"" + MAGIC + "\".");
		}
		EventFile f = new EventFile();
		f.mVersion = bytes[4]&0xFF;
		if(f.mVersion!=FORMAT_VERSION) {
			throw new FormatException("Event file format version " + f.mVersion + " is not supported.");
		}
		f.mHeaderLength = bytes[5]&0xFF;
		f.mRecordLength = bytes[6]&0xFF;
		if(f.mHeaderLength<HEADER_LENGTH || f.mRecordLength<EventRecord.LENGTH) {
			throw new FormatException("Event file header gives a " + f.mHeaderLength + "-byte header and "
					+ f.mRecordLength + "-byte records; at least " + HEADER_LENGTH + " and "
					+ EventRecord.LENGTH + " are needed.");
		}
		if(bytes.length<f.mHeaderLength) {
			throw new FormatException("Event file header is cut short.");
		}

		f.mHardwareId = bytes[7]&0xFF;
		f.mFileOpenTicks = EventRecord.int64(bytes, 8);
		f.mRtcOffsetTicks = EventRecord.int64(bytes, 16);
		f.mConfigTime = ((long)EventRecord.uint16(bytes, 24)) | (((long)EventRecord.uint16(bytes, 26))<<16);
		f.mFirmwareId = EventRecord.uint16(bytes, 28);
		f.mFirmwareMajor = EventRecord.uint16(bytes, 30);
		f.mFirmwareMinor = bytes[32]&0xFF;
		f.mFirmwareInternal = bytes[33]&0xFF;
		f.mFirstSequenceNumber = EventRecord.uint16(bytes, 34);
		f.mOpenReason = bytes[36]&0xFF;
		f.mInputs = bytes[37]&0xFF;

		int pos = f.mHeaderLength;
		while(pos+f.mRecordLength<=bytes.length) {
			f.mRecords.add(EventRecord.parse(bytes, pos));
			pos += f.mRecordLength;
		}
		f.mTrailingBytes = bytes.length-pos;
		return f;
	}

	public int getVersion() {
		return mVersion;
	}

	public int getHeaderLength() {
		return mHeaderLength;
	}

	public int getRecordLength() {
		return mRecordLength;
	}

	/** 3 Shimmer3, 10 Shimmer3R */
	public int getHardwareId() {
		return mHardwareId;
	}

	/** The data file's initial timestamp, all 64 bits: pair the two files by it */
	public long getFileOpenTicks() {
		return mFileOpenTicks;
	}

	/**
	 * Shimmer3: the offset from boot time to real-world time, as SD header bytes
	 * 44-51, added to a record's ticks for real-world time. Shimmer3R: 0, its RTC
	 * already counts real-world time.
	 */
	public long getRtcOffsetTicks() {
		return mRtcOffsetTicks;
	}

	/** Config time, Unix seconds */
	public long getConfigTime() {
		return mConfigTime;
	}

	public int getFirmwareId() {
		return mFirmwareId;
	}

	public int getFirmwareMajor() {
		return mFirmwareMajor;
	}

	public int getFirmwareMinor() {
		return mFirmwareMinor;
	}

	public int getFirmwareInternal() {
		return mFirmwareInternal;
	}

	/** The first record's sequence number; non-zero after a split */
	public int getFirstSequenceNumber() {
		return mFirstSequenceNumber;
	}

	/** {@link #OPEN_REASON_SESSION_START} or {@link #OPEN_REASON_SPLIT} */
	public int getOpenReason() {
		return mOpenReason;
	}

	/** Which inputs the file records: {@link EventRecord#INPUT_BUTTON}, {@link EventRecord#INPUT_TTL} */
	public int getInputs() {
		return mInputs;
	}

	public List<EventRecord> getRecords() {
		return Collections.unmodifiableList(mRecords);
	}

	/** Bytes after the last whole record, from a file cut short mid-write */
	public int getTrailingBytes() {
		return mTrailingBytes;
	}

	/** Whether the file ends with a final record. Without one it was cut short, by a flat battery say. */
	public boolean isComplete() {
		return !mRecords.isEmpty() && mRecords.get(mRecords.size()-1).isFinal();
	}

	/** A record's real-world time in Unix milliseconds: its ticks plus, on a Shimmer3, the file's RTC offset */
	public double getUnixMs(EventRecord record) {
		return (double)(record.getTicks()+mRtcOffsetTicks)*1000.0/EventRecord.TICKS_PER_SECOND;
	}

	/**
	 * Whether this event file belongs with the SD data file whose header gives
	 * {@code dataFileInitialTimestampTicks}: the two carry the same initial
	 * timestamp, so a copied or renamed file can still be paired.
	 */
	public boolean belongsWithDataFile(long dataFileInitialTimestampTicks) {
		return mFileOpenTicks==dataFileInitialTimestampTicks;
	}

	/**
	 * The companion event file of an SD data file, by the card's layout:
	 * {@code data/X/NNN} pairs with {@code events/X/NNN}. Returns null when the
	 * data file is not under a {@code data} folder.
	 */
	public static File getCompanionFile(File dataFile) {
		File session = dataFile.getParentFile();
		File experiment = session==null? null:session.getParentFile();
		File dataRoot = experiment==null? null:experiment.getParentFile();
		if(dataRoot==null || !"data".equalsIgnoreCase(dataRoot.getName())) {
			return null;
		}
		File eventsRoot = new File(dataRoot.getParentFile(), "events");
		return new File(new File(new File(eventsRoot, experiment.getName()), session.getName()), dataFile.getName());
	}

}
