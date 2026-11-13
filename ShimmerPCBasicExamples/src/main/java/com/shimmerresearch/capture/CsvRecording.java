package com.shimmerresearch.capture;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;

/**
 * One recording of the stream, written as shimmer-host-core's bindings/CAPTURE.md ("Files") has
 * every Capture app write it, and as shimmer-capture-web writes it, so that one script reads any
 * app's files:
 * <ul>
 * <li>named {@code <platform>_<MAC last 4>_<yyyymmdd_hhmmss>.csv}, the start in local time;</li>
 * <li>a row of names: {@code HostTime_ms}, {@code TIMESTAMP}, then every channel the driver decodes,
 * in its order, as {@code <channel>_CAL} or {@code <channel>_RAW}, each once per format; the raw
 * {@code Timestamp} is {@code TIMESTAMP} alone;</li>
 * <li>a row of units: {@code ms}, {@code ticks}, then each channel's;</li>
 * <li>a row per sample: when this host received it (whole ms since the Unix epoch), the raw
 * timestamp, then the values, each written as JavaScript writes a number;</li>
 * <li>RFC 4180 cells, lines ended CRLF, flushed once a second.</li>
 * </ul>
 * The columns are taken from the first sample, in the driver's own order, which is the order the
 * library reproduces.
 */
public final class CsvRecording implements AutoCloseable {

	static final String CRLF = "\r\n";
	private static final long FLUSH_MS = 1000;
	private static final String CAL = CHANNEL_TYPE.CAL.toString();
	private static final String UNCAL = CHANNEL_TYPE.UNCAL.toString();
	static final String TIMESTAMP = "Timestamp";

	/** A column after HostTime_ms and TIMESTAMP: a channel in one format */
	static final class Column {
		final String channel;
		final String format;
		final String name;
		final String units;

		Column(String channel, String format, String units) {
			this.channel = channel;
			this.format = format;
			this.name = channel + (CAL.equals(format) ? "_CAL" : "_RAW");
			this.units = units == null ? "" : units;
		}
	}

	/** The columns of a sample, in the driver's order: each channel calibrated, then raw */
	static List<Column> columnsOf(ObjectCluster sample) {
		List<Column> columns = new ArrayList<Column>();
		for (String channel : sample.getChannelNamesByInsertionOrder()) {
			Collection<FormatCluster> formats = sample.getCollectionOfFormatClusters(channel);
			FormatCluster cal = ObjectCluster.returnFormatCluster(formats, CAL);
			FormatCluster raw = ObjectCluster.returnFormatCluster(formats, UNCAL);
			if (cal != null) {
				columns.add(new Column(channel, CAL, cal.mUnits));
			}
			if (raw != null && !TIMESTAMP.equals(channel)) {
				columns.add(new Column(channel, UNCAL, raw.mUnits));
			}
		}
		return columns;
	}

	final File file;
	private final Writer out;
	private final List<Column> columns;
	private long rows;
	private long flushedAt;

	/** Creates the file in {@code folder} and writes its two header rows, with the columns of {@code first} */
	public CsvRecording(File folder, String platform, String macAddress, ObjectCluster first, long startedMs) throws IOException {
		if (!folder.isDirectory() && !folder.mkdirs()) {
			throw new IOException("cannot make " + folder);
		}
		file = new File(folder, fileName(platform, macAddress, startedMs, TimeZone.getDefault()));
		columns = columnsOf(first);
		out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
		List<String> names = new ArrayList<String>();
		names.add("HostTime_ms");
		names.add("TIMESTAMP");
		List<String> units = new ArrayList<String>();
		units.add("ms");
		units.add("ticks");
		for (Column c : columns) {
			names.add(c.name);
			units.add(c.units);
		}
		try {
			writeRow(names);
			writeRow(units);
			out.flush();
		} catch (IOException e) {
			try {
				out.close();
			} catch (IOException closing) {
				// the write's error is the one reported
			}
			throw e;
		}
		flushedAt = System.currentTimeMillis();
	}

	public File getFile() {
		return file;
	}

	public long getRows() {
		return rows;
	}

	/** One sample, received at {@code hostMs} (ms since the Unix epoch) */
	public void append(ObjectCluster sample, double hostMs) throws IOException {
		StringBuilder row = new StringBuilder();
		row.append(number(Math.floor(hostMs + 0.5))).append(',');
		row.append(number(sample.getFormatClusterValue(TIMESTAMP, UNCAL)));
		for (Column c : columns) {
			row.append(',').append(number(sample.getFormatClusterValue(c.channel, c.format)));
		}
		out.write(row.append(CRLF).toString());
		rows++;
		long now = System.currentTimeMillis();
		if (now - flushedAt >= FLUSH_MS) {
			out.flush();
			flushedAt = now;
		}
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	private void writeRow(List<String> cells) throws IOException {
		StringBuilder row = new StringBuilder();
		for (int i = 0; i < cells.size(); i++) {
			row.append(i == 0 ? "" : ",").append(cell(cells.get(i)));
		}
		out.write(row.append(CRLF).toString());
	}

	/** {@code Shimmer3R_2F31_20261103_142501.csv}: the MAC's last four hex digits, upper case, 0000 for none */
	static String fileName(String platform, String macAddress, long startedMs, TimeZone zone) {
		String hex = (macAddress == null ? "" : macAddress).replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
		SimpleDateFormat stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT);
		stamp.setTimeZone(zone);
		return platform + "_" + (hex.isEmpty() ? "0000" : hex.substring(Math.max(0, hex.length() - 4))) + "_"
				+ stamp.format(new Date(startedMs)) + ".csv";
	}

	/**
	 * A value as JavaScript's Number.prototype.toString writes it: the shortest digits that read back
	 * to the same double; plain from 1e-7 up to 1e21, with an exponent outside ({@code 1.5e-7},
	 * {@code 1.5e+21}); a whole number with no decimal point; {@code NaN}, {@code Infinity},
	 * {@code -Infinity}. Java's Double.toString is not always the shortest before Java 19, so the
	 * digits are found here.
	 */
	static String number(double value) {
		if (Double.isNaN(value)) {
			return "NaN";
		}
		if (Double.isInfinite(value)) {
			return value < 0 ? "-Infinity" : "Infinity";
		}
		if (value == 0) {
			return "0";
		}
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		double magnitude = Math.abs(value);
		BigDecimal exact = new BigDecimal(magnitude);
		BigDecimal shortest = exact;
		for (int precision = 1; precision <= 17; precision++) {
			BigDecimal rounded = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
			if (rounded.doubleValue() == magnitude) {
				shortest = rounded;
				break;
			}
		}
		shortest = shortest.stripTrailingZeros();
		String digits = shortest.unscaledValue().toString();
		int k = digits.length();
		// n: where the decimal point goes, counted from the first digit
		int n = k - shortest.scale();
		String sign = value < 0 ? "-" : "";
		if (k <= n && n <= 21) {
			return sign + digits + zeros(n - k);
		}
		if (0 < n && n <= 21) {
			return sign + digits.substring(0, n) + "." + digits.substring(n);
		}
		if (-6 < n && n <= 0) {
			return sign + "0." + zeros(-n) + digits;
		}
		String fraction = k > 1 ? "." + digits.substring(1) : "";
		return sign + digits.charAt(0) + fraction + (n > 0 ? "e+" : "e-") + Math.abs(n - 1);
	}

	/** A cell as RFC 4180 has it: white space runs made one space, quoted (quotes doubled) if it holds a comma or a quote */
	static String cell(String text) {
		String one = text.trim().replaceAll("\\s+", " ");
		return one.contains(",") || one.contains("\"") ? "\"" + one.replace("\"", "\"\"") + "\"" : one;
	}

	private static String zeros(int count) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < count; i++) {
			b.append('0');
		}
		return b.toString();
	}

}
