package com.shimmerresearch.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A recorded BLE session in the text format LoggingBleRadio writes (DEV-1132): one line per
 * write ("TX") or notification ("RX"), e.g.
 *
 * <pre>
 * 22:10:04.925  TX h7 [0x03]
 * 22:10:04.955  RX h7 [0xFF 0x04 0x80 0x02]
 * </pre>
 *
 * Other lines (connect, scan, comments starting with #) are skipped.
 */
public class RecordedSession {

	public enum Direction {
		TX, RX
	}

	public static class Entry {
		/** Milliseconds since the first TX or RX line. */
		public final long timeMs;
		public final Direction direction;
		public final byte[] bytes;

		Entry(long timeMs, Direction direction, byte[] bytes) {
			this.timeMs = timeMs;
			this.direction = direction;
			this.bytes = bytes;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(direction + " +" + timeMs + "ms");
			for (int i = 0; i < Math.min(bytes.length, 12); i++) {
				sb.append(String.format(" %02X", bytes[i]));
			}
			return bytes.length > 12 ? sb + " ..." : sb.toString();
		}
	}

	private static final Pattern LINE = Pattern
			.compile("^(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s+(TX|RX) h\\d+ \\[([^\\]]*)\\]");

	private final List<Entry> mEntries;

	private RecordedSession(List<Entry> entries) {
		mEntries = Collections.unmodifiableList(entries);
	}

	public List<Entry> getEntries() {
		return mEntries;
	}

	/** This session with every RX entry split into one-byte notifications, at the same time. */
	public RecordedSession splitIntoSingleBytes() {
		List<Entry> split = new ArrayList<Entry>();
		for (Entry e : mEntries) {
			if (e.direction == Direction.TX) {
				split.add(e);
				continue;
			}
			for (byte b : e.bytes) {
				split.add(new Entry(e.timeMs, Direction.RX, new byte[] { b }));
			}
		}
		return new RecordedSession(split);
	}

	/** This session with one byte of the {@code n}th RX entry (counting from 0) XOR-ed with 0xFF. */
	public RecordedSession corruptRx(int n, int byteIndex) {
		List<Entry> copy = new ArrayList<Entry>();
		int rx = 0;
		for (Entry e : mEntries) {
			if (e.direction == Direction.RX && rx++ == n) {
				byte[] bytes = e.bytes.clone();
				bytes[byteIndex] ^= (byte) 0xFF;
				copy.add(new Entry(e.timeMs, Direction.RX, bytes));
			} else {
				copy.add(e);
			}
		}
		return new RecordedSession(copy);
	}

	/** Index (among RX entries) of the first RX entry after the TX entry that starts with {@code opcode}. */
	public int firstRxAfter(byte opcode) {
		boolean seen = false;
		int rx = 0;
		for (Entry e : mEntries) {
			if (e.direction == Direction.TX && e.bytes.length > 0 && e.bytes[0] == opcode) {
				seen = true;
			} else if (e.direction == Direction.RX) {
				if (seen) {
					return rx;
				}
				rx++;
			}
		}
		return -1;
	}

	/** Loads a session from the test classpath, e.g. "/protocol/shimmer3r_...bytes.log". */
	public static RecordedSession load(String resource) throws IOException {
		InputStream in = RecordedSession.class.getResourceAsStream(resource);
		if (in == null) {
			throw new IOException("test resource not found: " + resource);
		}
		List<Entry> entries = new ArrayList<Entry>();
		long firstMs = -1;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = LINE.matcher(line);
				if (!m.find()) {
					continue;
				}
				long ms = ((Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 60
						+ Long.parseLong(m.group(3))) * 1000 + Long.parseLong(m.group(4));
				if (firstMs < 0) {
					firstMs = ms;
				}
				entries.add(new Entry(ms - firstMs, Direction.valueOf(m.group(5)), parseHex(m.group(6))));
			}
		}
		return new RecordedSession(entries);
	}

	/** Parses "0xFF 0x04 0x80" into bytes. */
	static byte[] parseHex(String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return new byte[0];
		}
		String[] parts = trimmed.split("\\s+");
		byte[] bytes = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			bytes[i] = (byte) Integer.parseInt(parts[i].replace("0x", "").replace("0X", ""), 16);
		}
		return bytes;
	}
}
