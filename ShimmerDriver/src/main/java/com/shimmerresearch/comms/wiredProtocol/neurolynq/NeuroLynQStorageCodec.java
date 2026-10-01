package com.shimmerresearch.comms.wiredProtocol.neurolynq;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The `$` storage component 0x0C: a NeuroLynQ node's session storage, byte for byte
 * (DEV-1061).
 * <p>
 * verisense-firmware docs/VERISENSE_NEUROLYNQ_STORAGE.md section 7 is the definition and
 * Includes/ASM_neurolynq_source/GqDockProtocol/gq_dock_storage.c the firmware's codec.
 * neurolynq-web-sdk src/neurolynq/storage.ts is the web host's; API_00017 holds this one
 * to the same vectors. All fields are little-endian.
 * <ul>
 * <li><b>INFO</b> (36 bytes): what the store is and holds.</li>
 * <li><b>SESSION</b>: one session - its handle, state, counts, and the two folder names
 * a GQ would have written it under.</li>
 * <li><b>FILES</b>: up to 40 {file, size} of a session.</li>
 * <li><b>READ</b>: answered by a burst of data frames, each {handle, file, offset} and up
 * to 245 bytes, then one END frame with the count and CRC-32 of what was sent.</li>
 * <li><b>ERASE</b> and <b>FORMAT</b> carry a four-letter word, so that no stray frame
 * destroys anything.</li>
 * </ul>
 * HARDWARE-VERIFY: written from the firmware's codec and its host tests; no node has
 * answered one of these frames yet.
 */
public final class NeuroLynQStorageCodec {

	private NeuroLynQStorageCodec() {
	}

	/** The component's sizes and limits (gq_dock_storage.h) */
	public static final int INFO_BYTES = 36;
	public static final int END_BYTES = 21;
	/** Data bytes in one burst frame: LEN is a byte, less COMP, PROP and {handle, file, offset} */
	public static final int READ_DATA_MAX = 245;
	/** The most one READ may ask for: more than any file */
	public static final long READ_MAX = 0x01000000L;
	public static final int FILES_PER_ANSWER = 40;
	/** A session's files: the FILES answer counts them in a byte */
	public static final int FILES_PER_SESSION = 255;

	private static final int SESSION_FIXED = 18;

	/** INFO's state byte */
	public static final class STATE {
		public static final int UNFORMATTED = 0;
		/** Stock Verisense data no host has fetched: only FORMAT is accepted */
		public static final int STOCK_DATA = 1;
		public static final int READY = 2;
		public static final int LOGGING = 3;
		public static final int FORMATTING = 4;
		public static final int NAND_ERROR = 5;
		/** Reading what the stock firmware left, just after boot */
		public static final int CHECKING = 6;
	}

	/** INFO's flags byte */
	public static final class FLAG {
		public static final int SESSION_OPEN = 0x01;
		public static final int DIRECTORY_FULL = 0x02;
		public static final int STORAGE_FULL = 0x04;
		public static final int LOGGING_REFUSED = 0x08;
		/** Unformatted over a NAND that holds a store's pages: a directory lost, not a new unit */
		public static final int ORPHANED = 0x10;
	}

	/** A session's state, in SESSION */
	public static final class SESSION_STATE {
		public static final int OPEN = 1;
		public static final int CLOSED = 2;
		public static final int RECOVERED = 3;
	}

	/** How a READ burst ended, in its END frame */
	public static final class END_STATUS {
		public static final int OK = 0;
		/** The file ended before the length asked for */
		public static final int EOF = 1;
		/** A page failed its check on the node */
		public static final int NAND_ERR = 2;
		/** A new request, or a NACK, ended the burst early */
		public static final int ABORTED = 3;
	}

	public static final class ERASE_SCOPE {
		public static final int ONE = 0;
		public static final int UP_TO = 1;
		public static final int ALL = 2;
	}

	public static final class FORMAT_MODE {
		public static final int QUICK = 0;
		public static final int FULL = 1;
	}

	// ------------------------------------------------------------------ bytes

	private static int le16(byte[] p, int at) {
		return (p[at] & 0xFF) | ((p[at + 1] & 0xFF) << 8);
	}

	private static long le32(byte[] p, int at) {
		return (le16(p, at) | ((long) le16(p, at + 2) << 16)) & 0xFFFFFFFFL;
	}

	private static void put16(byte[] p, int at, int v) {
		p[at] = (byte) (v & 0xFF);
		p[at + 1] = (byte) ((v >>> 8) & 0xFF);
	}

	private static void put32(byte[] p, int at, long v) {
		put16(p, at, (int) (v & 0xFFFF));
		put16(p, at + 2, (int) ((v >>> 16) & 0xFFFF));
	}

	private static void need(byte[] p, int bytes, String what) {
		if (p.length < bytes) {
			throw new IllegalArgumentException(what + ": " + p.length + " bytes, need " + bytes);
		}
	}

	// ------------------------------------------------------------------ INFO

	public static final class Info {
		public int layoutVersion;
		/** {@link STATE} */
		public int state;
		/** {@link FLAG} bits */
		public int flags;
		/** 0 unknown, 1 TC58, 2 W25N */
		public int part;
		public int pageBytes;
		public int pagesPerBlock;
		public int blocks;
		public int badBlocks;
		public long capacityKiB;
		public long usedKiB;
		public int sessions;
		public int maxSessions;
		public int generation;
		/** A full format's progress, 0-100 */
		public int formatPercent;
		/** Data bytes in one READ frame (245) */
		public int readFrameMax;
		/** The most one READ may ask for */
		public long readMax;
		public int droppedRecords;
		public int nandErrors;
	}

	public static Info parseInfo(byte[] p) {
		need(p, INFO_BYTES, "STORAGE INFO");
		Info i = new Info();
		i.layoutVersion = p[0] & 0xFF;
		i.state = p[1] & 0xFF;
		i.flags = p[2] & 0xFF;
		i.part = p[3] & 0xFF;
		i.pageBytes = le16(p, 4);
		i.pagesPerBlock = le16(p, 6);
		i.blocks = le16(p, 8);
		i.badBlocks = le16(p, 10);
		i.capacityKiB = le32(p, 12);
		i.usedKiB = le32(p, 16);
		i.sessions = le16(p, 20);
		i.maxSessions = le16(p, 22);
		i.generation = le16(p, 24);
		i.formatPercent = p[26] & 0xFF;
		i.readFrameMax = p[27] & 0xFF;
		i.readMax = le32(p, 28);
		i.droppedRecords = le16(p, 32);
		i.nandErrors = le16(p, 34);
		return i;
	}

	/** The device's half: INFO as a node answers it */
	public static byte[] buildInfo(Info i) {
		byte[] p = new byte[INFO_BYTES];
		p[0] = (byte) i.layoutVersion;
		p[1] = (byte) i.state;
		p[2] = (byte) i.flags;
		p[3] = (byte) i.part;
		put16(p, 4, i.pageBytes);
		put16(p, 6, i.pagesPerBlock);
		put16(p, 8, i.blocks);
		put16(p, 10, i.badBlocks);
		put32(p, 12, i.capacityKiB);
		put32(p, 16, i.usedKiB);
		put16(p, 20, i.sessions);
		put16(p, 22, i.maxSessions);
		put16(p, 24, i.generation);
		p[26] = (byte) i.formatPercent;
		p[27] = (byte) i.readFrameMax;
		put32(p, 28, i.readMax);
		put16(p, 32, i.droppedRecords);
		put16(p, 34, i.nandErrors);
		return p;
	}

	// ---------------------------------------------------------------- SESSION

	public static final class Session {
		public int total;
		public int index;
		/** The session's sequence: stable across reboots, and what FILES, READ and ERASE take */
		public int handle;
		/** {@link SESSION_STATE} */
		public int state;
		/** Bit 0 sync fields, bit 1 SR68 layout, bits 4-6 the PPG mask */
		public int flags;
		public int dbSession;
		/** The session folder's NNN */
		public int folder;
		public int fileCount;
		public long totalBytes;
		/** {@code <trialName>_<configTime>}, as a GQ names it */
		public String trialFolder;
		/** {@code <shimmerName>-<NNN>} */
		public String sessionFolder;
	}

	public static Session parseSession(byte[] p) {
		need(p, SESSION_FIXED + 2, "STORAGE SESSION");
		int trialLen = p[SESSION_FIXED] & 0xFF;
		int sessionAt = SESSION_FIXED + 1 + trialLen;
		need(p, sessionAt + 1, "STORAGE SESSION");
		int sessionLen = p[sessionAt] & 0xFF;
		need(p, sessionAt + 1 + sessionLen, "STORAGE SESSION");
		Session s = new Session();
		s.total = le16(p, 0);
		s.index = le16(p, 2);
		s.handle = le16(p, 4);
		s.state = p[6] & 0xFF;
		s.flags = p[7] & 0xFF;
		s.dbSession = le16(p, 8);
		s.folder = le16(p, 10);
		s.fileCount = le16(p, 12);
		s.totalBytes = le32(p, 14);
		s.trialFolder = new String(p, SESSION_FIXED + 1, trialLen, StandardCharsets.ISO_8859_1);
		s.sessionFolder = new String(p, sessionAt + 1, sessionLen, StandardCharsets.ISO_8859_1);
		return s;
	}

	public static byte[] buildSession(Session s) {
		byte[] trial = s.trialFolder.getBytes(StandardCharsets.ISO_8859_1);
		byte[] session = s.sessionFolder.getBytes(StandardCharsets.ISO_8859_1);
		byte[] p = new byte[SESSION_FIXED + 2 + trial.length + session.length];
		put16(p, 0, s.total);
		put16(p, 2, s.index);
		put16(p, 4, s.handle);
		p[6] = (byte) s.state;
		p[7] = (byte) s.flags;
		put16(p, 8, s.dbSession);
		put16(p, 10, s.folder);
		put16(p, 12, s.fileCount);
		put32(p, 14, s.totalBytes);
		p[SESSION_FIXED] = (byte) trial.length;
		System.arraycopy(trial, 0, p, SESSION_FIXED + 1, trial.length);
		p[SESSION_FIXED + 1 + trial.length] = (byte) session.length;
		System.arraycopy(session, 0, p, SESSION_FIXED + 2 + trial.length, session.length);
		return p;
	}

	// ------------------------------------------------------------------ FILES

	public static final class FileEntry {
		public final int file;
		public final long size;

		public FileEntry(int file, long size) {
			this.file = file;
			this.size = size;
		}
	}

	public static final class Files {
		public int handle;
		/** The session's file count */
		public int total;
		public int first;
		public List<FileEntry> files = new ArrayList<FileEntry>();
	}

	public static Files parseFiles(byte[] p) {
		need(p, 5, "STORAGE FILES");
		int n = p[4] & 0xFF;
		need(p, 5 + 6 * n, "STORAGE FILES");
		Files f = new Files();
		f.handle = le16(p, 0);
		f.total = p[2] & 0xFF;
		f.first = p[3] & 0xFF;
		for (int i = 0; i < n; i++) {
			f.files.add(new FileEntry(le16(p, 5 + 6 * i), le32(p, 7 + 6 * i)));
		}
		f.files = Collections.unmodifiableList(f.files);
		return f;
	}

	public static byte[] buildFiles(Files f) {
		byte[] p = new byte[5 + 6 * f.files.size()];
		put16(p, 0, f.handle);
		p[2] = (byte) f.total;
		p[3] = (byte) f.first;
		p[4] = (byte) f.files.size();
		for (int i = 0; i < f.files.size(); i++) {
			put16(p, 5 + 6 * i, f.files.get(i).file);
			put32(p, 7 + 6 * i, f.files.get(i).size);
		}
		return p;
	}

	// ------------------------------------------------------------- READ burst

	/** One data frame of a READ burst: its bytes and their place in the file */
	public static final class ReadFrame {
		public final int handle;
		public final int file;
		public final long offset;
		public final byte[] data;

		public ReadFrame(int handle, int file, long offset, byte[] data) {
			this.handle = handle;
			this.file = file;
			this.offset = offset;
			this.data = data;
		}
	}

	public static ReadFrame parseReadFrame(byte[] p) {
		need(p, 8, "STORAGE READ frame");
		byte[] data = new byte[p.length - 8];
		System.arraycopy(p, 8, data, 0, data.length);
		return new ReadFrame(le16(p, 0), le16(p, 2), le32(p, 4), data);
	}

	public static byte[] buildReadFrame(ReadFrame f) {
		if (f.data.length > READ_DATA_MAX) {
			throw new IllegalArgumentException("STORAGE READ frame: " + f.data.length + " bytes, at most " + READ_DATA_MAX);
		}
		byte[] p = new byte[8 + f.data.length];
		put16(p, 0, f.handle);
		put16(p, 2, f.file);
		put32(p, 4, f.offset);
		System.arraycopy(f.data, 0, p, 8, f.data.length);
		return p;
	}

	/** The frame that ends a READ burst */
	public static final class ReadEnd {
		public int handle;
		public int file;
		public long reqOffset;
		/** Data bytes the node sent, from reqOffset */
		public long bytesSent;
		public long fileSize;
		/** CRC-32/IEEE of the bytes sent, as java.util.zip.CRC32 computes it */
		public long crc32;
		/** {@link END_STATUS} */
		public int status;
	}

	public static ReadEnd parseReadEnd(byte[] p) {
		need(p, END_BYTES, "STORAGE END");
		ReadEnd e = new ReadEnd();
		e.handle = le16(p, 0);
		e.file = le16(p, 2);
		e.reqOffset = le32(p, 4);
		e.bytesSent = le32(p, 8);
		e.fileSize = le32(p, 12);
		e.crc32 = le32(p, 16);
		e.status = p[20] & 0xFF;
		return e;
	}

	public static byte[] buildReadEnd(ReadEnd e) {
		byte[] p = new byte[END_BYTES];
		put16(p, 0, e.handle);
		put16(p, 2, e.file);
		put32(p, 4, e.reqOffset);
		put32(p, 8, e.bytesSent);
		put32(p, 12, e.fileSize);
		put32(p, 16, e.crc32);
		p[20] = (byte) e.status;
		return p;
	}

	// ------------------------------------------------------ request arguments

	public static byte[] sessionArgs(int index) {
		byte[] p = new byte[2];
		put16(p, 0, index);
		return p;
	}

	public static byte[] filesArgs(int handle, int first, int max) {
		byte[] p = new byte[4];
		put16(p, 0, handle);
		p[2] = (byte) first;
		p[3] = (byte) max;
		return p;
	}

	public static byte[] readArgs(int handle, int file, long offset, long length) {
		byte[] p = new byte[12];
		put16(p, 0, handle);
		put16(p, 2, file);
		put32(p, 4, offset);
		put32(p, 8, length);
		return p;
	}

	/** The arguments of a READ, as the device's half reads them: {handle, file, offset, length} */
	public static long[] parseReadArgs(byte[] p) {
		need(p, 12, "STORAGE READ args");
		return new long[] { le16(p, 0), le16(p, 2), le32(p, 4), le32(p, 8) };
	}

	public static byte[] eraseArgs(int scope, int handle) {
		byte[] p = new byte[7];
		p[0] = (byte) scope;
		put16(p, 1, handle);
		p[3] = 'E';
		p[4] = 'R';
		p[5] = 'A';
		p[6] = 'S';
		return p;
	}

	public static byte[] formatArgs(int mode) {
		return new byte[] { (byte) mode, 'F', 'R', 'M', 'T' };
	}
}
