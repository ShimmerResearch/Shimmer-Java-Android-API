package com.shimmerresearch.protocol;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;

/**
 * Keeps {@code python/tests/data/java_reference.csv} in step with the Java decoder. That file is
 * the Java side of the Python port's cross-language test (DEV-1134): the state machine's samples
 * for the recorded session, one row per sample, every channel and format.
 * <p>
 * Fails if the file is stale. The current output is always written to
 * {@code build/python-reference/java_reference.csv}; copy it over the stale one. Skipped once
 * the Python port has moved out of this repository.
 */
public class API_00030_PythonReferenceTest {

	private static final File REFERENCE = new File("../python/tests/data/java_reference.csv");
	private static final File CURRENT = new File("build/python-reference/java_reference.csv");
	/** The same for the recorded Shimmer3, whose stream has gaps (used by the Rust core's framing test). */
	private static final File SHIMMER3_REFERENCE = new File("../python/tests/data/java_reference_shimmer3.csv");
	private static final File SHIMMER3_CURRENT = new File("build/python-reference/java_reference_shimmer3.csv");

	@Test
	public void pythonReferenceMatchesTheJavaDecoder() throws Exception {
		checkReference(API_00027_LogAndStreamProtocolMatchesDriverTest.SESSION, REFERENCE, CURRENT);
	}

	@Test
	public void shimmer3ReferenceMatchesTheJavaDecoder() throws Exception {
		checkReference(API_00027_LogAndStreamProtocolMatchesDriverTest.SHIMMER3_SESSION, SHIMMER3_REFERENCE, SHIMMER3_CURRENT);
	}

	private static void checkReference(String session, File reference, File current) throws Exception {
		Assume.assumeTrue("the Python port is not in this repository", reference.getParentFile().isDirectory());
		List<ObjectCluster> samples = ProtocolReplay.run(new LogAndStreamProtocol(), RecordedSession.load(session)).samples;
		String csv = toCsv(samples);
		current.getParentFile().mkdirs();
		Files.write(current.toPath(), csv.getBytes(StandardCharsets.UTF_8));

		// git may have checked the file out with CRLF line endings.
		String committed = reference.isFile()
				? new String(Files.readAllBytes(reference.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n")
				: "";
		assertTrue(reference + " is out of date: copy " + current.getAbsolutePath() + " over it", committed.equals(csv));
	}

	private static final File CALIBRATION_REFERENCE = new File("../python/tests/data/java_calibration_reference.txt");
	private static final File CALIBRATION_CURRENT = new File("build/python-reference/java_calibration_reference.txt");
	/** The recorded device's INQUIRY_RESPONSE payload: 51.2 Hz; mag, gyro, LN accel, battery. */
	private static final byte[] INQUIRY = { (byte) 0x80, 0x02, 0x02, 0x05, 0x0D, 0x08, 0x00, 0x00, 0x00, 0x0A, 0x01,
			0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x00, 0x01, 0x02, 0x03 };
	private static final String[] INERTIAL = { "Accel_LN_X", "Accel_LN_Y", "Accel_LN_Z", "Gyro_X", "Gyro_Y", "Gyro_Z",
			"Mag_X", "Mag_Y", "Mag_Z" };

	/**
	 * The recorded device has nominal calibration, so the replay never exercises real offsets,
	 * sensitivities or off-diagonal alignment. Here the recorded device's config bytes (with
	 * InfoMem calibration blanked and the on-the-fly gyro offset off) are combined with a
	 * crafted calibration dump, and synthetic packets are decoded. The file holds the inputs as
	 * hex and the calibrated outputs, so the Python test decodes exactly the same bytes.
	 */
	@Test
	public void pythonCalibrationReferenceMatchesTheJavaDecoder() throws Exception {
		Assume.assumeTrue("the Python port is not in this repository", REFERENCE.getParentFile().isDirectory());
		LogAndStreamProtocol recorded = new LogAndStreamProtocol();
		ProtocolReplay.run(recorded, RecordedSession.load(API_00027_LogAndStreamProtocolMatchesDriverTest.SESSION));
		byte[] config = recorded.getDeviceModel().getShimmerInfoMemBytesOriginal().clone();
		java.util.Arrays.fill(config, 34, 97, (byte) 0xFF); // LN accel, gyro and mag InfoMem calibration
		config[118] &= ~0x20; // GYRO_ON_THE_FLY_CAL off
		byte[] dump = craftedCalibrationDump();

		LogAndStreamModel model = new LogAndStreamModel();
		model.applyHardwareVersion((byte) 10);
		model.applyFirmwareVersion(new byte[] { 3, 0, 1, 0, 1, 16 });
		model.applyExpansionBoard(new byte[] { 0x30, 0x08, 0x01 });
		model.applyConfigBytes(config);
		model.applyCalibrationDump(dump);
		model.applyInquiry(INQUIRY);
		model.prepareForStreaming();

		StringBuilder sb = new StringBuilder();
		sb.append("config,").append(hex(config)).append('\n');
		sb.append("dump,").append(hex(dump)).append('\n');
		sb.append("inquiry,").append(hex(INQUIRY)).append('\n');
		sb.append("packet");
		for (String channel : INERTIAL) {
			sb.append(',').append(channel);
		}
		sb.append('\n');
		long seed = 20261005L;
		for (int i = 0; i < 40; i++) {
			byte[] packet = new byte[23];
			int ticks = 1000 + 640 * i;
			packet[0] = (byte) ticks;
			packet[1] = (byte) (ticks >> 8);
			packet[2] = (byte) (ticks >> 16);
			for (int field = 0; field < 10; field++) {
				seed = seed * 6364136223846793005L + 1442695040888963407L; // a fixed sequence, the same every run
				int value = field == 9 ? 2800 + (int) ((seed >>> 33) % 100) : (short) (seed >>> 40);
				packet[3 + 2 * field] = (byte) value;
				packet[4 + 2 * field] = (byte) (value >> 8);
			}
			ObjectCluster oc = model.decode(packet, 1790960000000L + 20 * i);
			sb.append(hex(packet));
			for (String channel : INERTIAL) {
				sb.append(',').append(Double.toString(oc.getFormatClusterValue(channel, "CAL")));
			}
			sb.append('\n');
		}
		String text = sb.toString();
		CALIBRATION_CURRENT.getParentFile().mkdirs();
		Files.write(CALIBRATION_CURRENT.toPath(), text.getBytes(StandardCharsets.UTF_8));

		String committed = CALIBRATION_REFERENCE.isFile()
				? new String(Files.readAllBytes(CALIBRATION_REFERENCE.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n")
				: "";
		assertTrue(CALIBRATION_REFERENCE + " is out of date: copy " + CALIBRATION_CURRENT.getAbsolutePath() + " over it",
				committed.equals(text));
	}

	/**
	 * Records for LN accel (sensor 37) range 0, gyro (38) range 1 and mag (42) range 0 with
	 * non-default values; plus an earlier accel record that the later one must replace, an
	 * accel record for an unused range, an all-0xFF mag record that must be skipped, and an
	 * unknown sensor that must be ignored.
	 */
	private static byte[] craftedCalibrationDump() {
		java.io.ByteArrayOutputStream records = new java.io.ByteArrayOutputStream();
		record(records, 37, 0, new int[] { 99, 99, 99 }, new int[] { 1000, 1000, 1000 }, new int[] { -100, 0, 0, 0, 100, 0, 0, 0, -100 });
		record(records, 37, 0, new int[] { 12, -34, 2047 }, new int[] { 1650, 1680, 1700 }, new int[] { -99, 3, -2, 1, 100, 4, 2, -5, -98 });
		record(records, 37, 2, new int[] { 5, 5, 5 }, new int[] { 400, 410, 420 }, new int[] { -100, 0, 0, 0, 100, 0, 0, 0, -100 });
		record(records, 38, 1, new int[] { -50, 3, 7 }, new int[] { 11250, 11500, 11399 }, new int[] { -100, 1, 0, 0, 99, -3, 2, 0, -100 });
		record(records, 42, 0, new int[] { -120, 300, 5 }, new int[] { 650, 670, 690 }, new int[] { -97, 10, -5, 8, -98, 2, -3, 4, -100 });
		byte[] allFF = new byte[21];
		java.util.Arrays.fill(allFF, (byte) 0xFF);
		recordBytes(records, 42, 0, allFF);
		record(records, 99, 0, new int[] { 1, 2, 3 }, new int[] { 4, 5, 6 }, new int[] { 7, 8, 9, 10, 11, 12, 13, 14, 15 });
		byte[] body = records.toByteArray();
		// Length (excluding these two bytes), then the version: HW 10, FW 3, v1.1.16.
		byte[] header = { 0, 0, 10, 0, 3, 0, 1, 0, 1, 16 };
		int length = header.length - 2 + body.length;
		header[0] = (byte) length;
		header[1] = (byte) (length >> 8);
		byte[] dump = java.util.Arrays.copyOf(header, header.length + body.length);
		System.arraycopy(body, 0, dump, header.length, body.length);
		return dump;
	}

	/** Offsets and sensitivities as big-endian i16, alignment as nine i8 (hundredths). */
	private static void record(java.io.ByteArrayOutputStream out, int sensorId, int range, int[] offset, int[] sensitivity,
			int[] alignment) {
		byte[] calibration = new byte[21];
		for (int i = 0; i < 3; i++) {
			calibration[2 * i] = (byte) (offset[i] >> 8);
			calibration[2 * i + 1] = (byte) offset[i];
			calibration[6 + 2 * i] = (byte) (sensitivity[i] >> 8);
			calibration[6 + 2 * i + 1] = (byte) sensitivity[i];
		}
		for (int i = 0; i < 9; i++) {
			calibration[12 + i] = (byte) alignment[i];
		}
		recordBytes(out, sensorId, range, calibration);
	}

	/** Sensor ID (u16 LE), range, length, an 8-byte calibration time, then the calibration bytes. */
	private static void recordBytes(java.io.ByteArrayOutputStream out, int sensorId, int range, byte[] calibration) {
		out.write(sensorId);
		out.write(sensorId >> 8);
		out.write(range);
		out.write(calibration.length);
		for (int i = 0; i < 8; i++) {
			out.write(i == 0 ? 1 : 0);
		}
		out.write(calibration, 0, calibration.length);
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xFF));
		}
		return sb.toString();
	}

	/** Header "index,channel|format|units,...", then one row per sample; values as Double.toString. */
	static String toCsv(List<ObjectCluster> samples) {
		List<String[]> columns = columns(samples.get(0));
		StringBuilder sb = new StringBuilder("index");
		for (String[] c : columns) {
			sb.append(',').append(field(c[0] + "|" + c[1] + "|" + c[2]));
		}
		sb.append('\n');
		for (int i = 0; i < samples.size(); i++) {
			sb.append(i);
			for (String[] c : columns) {
				FormatCluster fc = samples.get(i).getFormatCluster(c[0], c[1]);
				sb.append(',').append(fc == null ? "" : Double.toString(fc.mData));
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/** {channel, format, units} for every format of every channel, in a stable order. */
	private static List<String[]> columns(ObjectCluster first) {
		List<String[]> columns = new ArrayList<String[]>();
		for (String channel : first.getChannelNamesByInsertionOrder()) {
			List<FormatCluster> formats = new ArrayList<FormatCluster>(first.getCollectionOfFormatClusters(channel));
			Collections.sort(formats, Comparator.comparing((FormatCluster f) -> f.mFormat));
			for (FormatCluster f : formats) {
				columns.add(new String[] { channel, f.mFormat, f.mUnits });
			}
		}
		return columns;
	}

	private static String field(String text) {
		if (text.contains(",") || text.contains("\n")) {
			throw new IllegalArgumentException("not CSV-safe: " + text);
		}
		return text;
	}
}
