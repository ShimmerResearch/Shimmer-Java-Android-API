package com.shimmerresearch.protocol;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;

/**
 * Keeps the Shimmer3 decode references of the Rust core ({@code ShimmerProtocolCore/tests/data/})
 * in step with the Java decoder (DEV-1134). The recorded Shimmer3 carries its own calibration and
 * the newer sensors, so its replay never exercises a Shimmer3's default calibrations, the older
 * sensors (KXRB5-2042, MPU9150, LSM303DLHC) or a calibration dump. Each case here takes the
 * recorded device's config bytes with InfoMem calibration blanked, gives it an expansion board
 * and a calibration dump, and decodes synthetic packets that also carry temperature and pressure,
 * whose sizes differ from a Shimmer3R's. Each file holds the inputs as hex and every channel of
 * every decoded packet.
 * <p>
 * Fails if a file is stale. The current output is always written to
 * {@code build/rust-reference/}; copy it over the stale one. Skipped once the Rust core has moved
 * out of this repository.
 */
public class API_00031_Shimmer3DecodeReferenceTest {

	private static final File DATA = new File("../ShimmerProtocolCore/tests/data");
	private static final File CURRENT = new File("build/rust-reference");

	private static final int ANALOG_ACCEL = 2, MPU9X50_GYRO = 30, LSM303_MAG = 32;
	/** Gyro range 2 (±1000 dps) in bits 0-1, LSM303DLHC mag range 7 (±8.1 Ga) in bits 5-7. */
	private static final byte CONFIG_SETUP_BYTE_2 = (byte) 0xEE;

	/** The older sensors, all at their defaults. SR31-4: a Shimmer3 before the new IMU. */
	@Test
	public void olderSensorsAtTheirDefaults() throws Exception {
		check("shimmer3_older_sensors_defaults", new byte[] { 31, 4, 0 }, new byte[0]);
	}

	/** The newer sensors, all at their defaults. SR48-4-2, as the recorded device. */
	@Test
	public void newerSensorsAtTheirDefaults() throws Exception {
		check("shimmer3_newer_sensors_defaults", new byte[] { 48, 4, 2 }, new byte[0]);
	}

	/**
	 * The older sensors with a calibration dump. The mag record is for range 7, the configured
	 * one, yet the driver decodes with range 1's calibration, here its default: see the Rust
	 * model's mag_range_in_use.
	 */
	@Test
	public void olderSensorsWithACalibrationDump() throws Exception {
		check("shimmer3_older_sensors_dump", new byte[] { 31, 4, 0 }, dump(7));
	}

	/** The newer sensors with a calibration dump. */
	@Test
	public void newerSensorsWithACalibrationDump() throws Exception {
		check("shimmer3_newer_sensors_dump", new byte[] { 48, 4, 2 }, dump(0));
	}

	private static void check(String name, byte[] expansionBoard, byte[] dump) throws Exception {
		Assume.assumeTrue("the Rust core is not in this repository", DATA.getParentFile().isDirectory());
		LogAndStreamProtocol recorded = new LogAndStreamProtocol();
		ProtocolReplay.run(recorded, RecordedSession.load(API_00027_LogAndStreamProtocolMatchesDriverTest.SHIMMER3_SESSION));
		byte[] config = recorded.getDeviceModel().getShimmerInfoMemBytesOriginal().clone();
		Arrays.fill(config, 34, 97, (byte) 0xFF); // LN accel, gyro and mag InfoMem calibration
		config[118] &= ~0x20; // GYRO_ON_THE_FLY_CAL off
		config[8] = CONFIG_SETUP_BYTE_2;
		// The recorded inquiry, with the same setup byte; channels: LN accel, battery, temperature,
		// pressure, gyro, mag.
		byte[] inquiry = { (byte) 0x80, 0x02, 0x01, (byte) 0x9B, CONFIG_SETUP_BYTE_2, 0x08, 0x0C, 0x01,
				0x00, 0x01, 0x02, 0x03, 0x1A, 0x1B, 0x0A, 0x0B, 0x0C, 0x07, 0x08, 0x09 };

		LogAndStreamModel model = new LogAndStreamModel();
		model.applyHardwareVersion((byte) 3);
		model.applyFirmwareVersion(new byte[] { 3, 0, 1, 0, 1, 3 }); // LogAndStream v1.1.3
		model.applyExpansionBoard(expansionBoard);
		model.applyConfigBytes(config);
		model.applyCalibrationDump(dump);
		model.applyInquiry(inquiry);
		model.prepareForStreaming();

		List<byte[]> packets = new ArrayList<byte[]>();
		List<ObjectCluster> samples = new ArrayList<ObjectCluster>();
		long seed = 20261005L;
		for (int i = 0; i < 40; i++) {
			byte[] packet = new byte[3 + 2 * 3 + 2 + 2 + 3 + 2 * 3 + 2 * 3];
			int ticks = 1000 + 640 * i;
			packet[0] = (byte) ticks;
			packet[1] = (byte) (ticks >> 8);
			packet[2] = (byte) (ticks >> 16);
			for (int b = 3; b < packet.length; b++) {
				seed = seed * 6364136223846793005L + 1442695040888963407L; // a fixed sequence, the same every run
				packet[b] = (byte) (seed >>> 40);
			}
			// The battery, little-endian at 9-10: 2500-2899, 3.66-4.25 V, so the percentage varies.
			int battery = 2500 + 10 * i;
			packet[9] = (byte) battery;
			packet[10] = (byte) (battery >> 8);
			packets.add(packet);
			samples.add(model.decode(packet, 1790960000000L + 20 * i));
		}

		StringBuilder sb = new StringBuilder();
		sb.append("expansion,").append(hex(expansionBoard)).append('\n');
		sb.append("config,").append(hex(config)).append('\n');
		sb.append("dump,").append(hex(dump)).append('\n');
		sb.append("inquiry,").append(hex(inquiry)).append('\n');
		List<String[]> columns = columns(samples.get(0));
		sb.append("packet");
		for (String[] c : columns) {
			sb.append(',').append(c[0]).append('|').append(c[1]).append('|').append(c[2]);
		}
		sb.append('\n');
		for (int i = 0; i < samples.size(); i++) {
			sb.append(hex(packets.get(i)));
			for (String[] c : columns) {
				FormatCluster fc = samples.get(i).getFormatCluster(c[0], c[1]);
				sb.append(',').append(fc == null ? "" : Double.toString(fc.mData));
			}
			sb.append('\n');
		}
		String text = sb.toString();
		File reference = new File(DATA, "java_" + name + ".txt");
		File current = new File(CURRENT, reference.getName());
		current.getParentFile().mkdirs();
		Files.write(current.toPath(), text.getBytes(StandardCharsets.UTF_8));

		// git may have checked the file out with CRLF line endings.
		String committed = reference.isFile()
				? new String(Files.readAllBytes(reference.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n")
				: "";
		assertTrue(reference + " is out of date: copy " + current.getAbsolutePath() + " over it", committed.equals(text));
	}

	/**
	 * Records for LN accel range 0, gyro range 2 and mag at {@code magRange}, with non-default
	 * values; an earlier gyro record that the later one must replace, and an unknown sensor that
	 * must be ignored.
	 */
	private static byte[] dump(int magRange) {
		ByteArrayOutputStream records = new ByteArrayOutputStream();
		record(records, ANALOG_ACCEL, 0, new int[] { 2100, 2000, 2200 }, new int[] { 85, 88, 90 }, new int[] { 2, -99, 1, -98, 0, 3, -1, 2, -100 });
		record(records, MPU9X50_GYRO, 2, new int[] { 9, 9, 9 }, new int[] { 3000, 3000, 3000 }, new int[] { 0, -100, 0, -100, 0, 0, 0, 0, -100 });
		record(records, MPU9X50_GYRO, 2, new int[] { -40, 25, 3 }, new int[] { 3270, 3300, 3311 }, new int[] { 1, -100, 2, -99, -1, 0, 0, 3, -98 });
		record(records, LSM303_MAG, magRange, new int[] { 30, -45, 12 }, new int[] { 640, 655, 700 }, new int[] { 2, -98, 0, 99, 1, -4, 0, 3, -100 });
		record(records, 99, 0, new int[] { 1, 2, 3 }, new int[] { 4, 5, 6 }, new int[] { 7, 8, 9, 10, 11, 12, 13, 14, 15 });
		byte[] body = records.toByteArray();
		// Length (excluding these two bytes), then the version: HW 3, FW 3, v1.1.3.
		byte[] header = { 0, 0, 3, 0, 3, 0, 1, 0, 1, 3 };
		int length = header.length - 2 + body.length;
		header[0] = (byte) length;
		header[1] = (byte) (length >> 8);
		byte[] dump = Arrays.copyOf(header, header.length + body.length);
		System.arraycopy(body, 0, dump, header.length, body.length);
		return dump;
	}

	/** Offsets and sensitivities as big-endian i16, alignment as nine i8 (hundredths); then as recordBytes. */
	private static void record(ByteArrayOutputStream out, int sensorId, int range, int[] offset, int[] sensitivity, int[] alignment) {
		out.write(sensorId);
		out.write(sensorId >> 8);
		out.write(range);
		out.write(21);
		for (int i = 0; i < 8; i++) {
			out.write(i == 0 ? 1 : 0); // calibration time
		}
		for (int i = 0; i < 3; i++) {
			out.write(offset[i] >> 8);
			out.write(offset[i]);
		}
		for (int i = 0; i < 3; i++) {
			out.write(sensitivity[i] >> 8);
			out.write(sensitivity[i]);
		}
		for (int i = 0; i < 9; i++) {
			out.write(alignment[i]);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xFF));
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
				if (f.mUnits.contains(",") || channel.contains(",")) {
					throw new IllegalArgumentException("not CSV-safe: " + channel);
				}
				columns.add(new String[] { channel, f.mFormat, f.mUnits });
			}
		}
		return columns;
	}
}
