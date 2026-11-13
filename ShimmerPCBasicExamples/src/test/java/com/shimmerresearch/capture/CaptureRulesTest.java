package com.shimmerresearch.capture;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;

/**
 * The rules shimmer-host-core's bindings/CAPTURE.md sets every Capture app, with the inputs and
 * results of that repository's Java Capture tests (RecordingTest, ReceptionStatsTest), so that this
 * app's figures and files are the same as those apps'.
 */
public class CaptureRulesTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final double RATE_HZ = 51.2;
	private static final double PERIOD_MS = 1000.0 / RATE_HZ;

	// ------------------------------------------------------------------
	// The recording
	// ------------------------------------------------------------------

	@Test
	public void valuesAreWrittenAsJavaScriptWritesThem() {
		assertEquals("1420786", CsvRecording.number(1420786.0));
		assertEquals("0", CsvRecording.number(-0.0));
		assertEquals("-3", CsvRecording.number(-3.0));
		assertEquals("9.62", CsvRecording.number(9.62));
		assertEquals("-9.62", CsvRecording.number(-9.62));
		assertEquals("0.30000000000000004", CsvRecording.number(0.1 + 0.2));
		assertEquals("NaN", CsvRecording.number(Double.NaN));
		assertEquals("Infinity", CsvRecording.number(Double.POSITIVE_INFINITY));
		assertEquals("-Infinity", CsvRecording.number(Double.NEGATIVE_INFINITY));
		assertEquals("0.000015", CsvRecording.number(0.000015));
		assertEquals("1.5e-7", CsvRecording.number(1.5e-7));
		assertEquals("1e-7", CsvRecording.number(1e-7));
		assertEquals("10000000000000000", CsvRecording.number(1e16));
		assertEquals("1.5e+21", CsvRecording.number(1.5e21));
		assertEquals("282879384806159000", CsvRecording.number(2.82879384806159E17));
		assertEquals("1.18575755e-316", CsvRecording.number(1.18575755E-316));
		assertEquals("1790960000331.5", CsvRecording.number(1790960000331.5));
	}

	@Test
	public void aCellIsEscapedAsRfc4180HasIt() {
		assertEquals("m/(s^2)", CsvRecording.cell("m/(s^2)"));
		assertEquals("\"a,b\"", CsvRecording.cell("a,b"));
		assertEquals("\"say \"\"hi\"\"\"", CsvRecording.cell("say \"hi\""));
		assertEquals("two lines", CsvRecording.cell(" two\r\nlines "));
	}

	@Test
	public void aRecordingIsNamedForItsPlatformMacAndLocalStart() {
		TimeZone utc = TimeZone.getTimeZone("UTC");
		long started = 1793000000000L; // 2026-10-26 07:33:20 UTC
		assertEquals("Shimmer3R_2F31_20261026_073320.csv", CsvRecording.fileName("Shimmer3R", "E747AA7D2F31", started, utc));
		assertEquals("Shimmer3_0000_20261026_073320.csv", CsvRecording.fileName("Shimmer3", null, started, utc));
		assertEquals("Shimmer3_BE69_20261026_083320.csv",
				CsvRecording.fileName("Shimmer3", "00:06:66:66:be:69", started, TimeZone.getTimeZone("GMT+01:00")));
	}

	/** A sample as the driver builds one: each channel calibrated and raw, the host's channels calibrated only */
	private static ObjectCluster sample(double ticks, double hostMs) {
		ObjectCluster o = new ObjectCluster();
		String cal = CHANNEL_TYPE.CAL.toString();
		String raw = CHANNEL_TYPE.UNCAL.toString();
		o.addData("Timestamp", CHANNEL_TYPE.UNCAL, "no_units", ticks);
		o.addData("Timestamp", CHANNEL_TYPE.CAL, "mSecs", ticks / 32.768);
		o.addData("GSR_Skin_Conductance", CHANNEL_TYPE.UNCAL, "uS", 1203);
		o.addData("GSR_Skin_Conductance", CHANNEL_TYPE.CAL, "uS", 9.62);
		o.addData("Accel_LN_X", CHANNEL_TYPE.UNCAL, "no_units", 2047);
		o.addData("Accel_LN_X", CHANNEL_TYPE.CAL, "m/(s^2)", 0.1 + 0.2);
		o.addData("System_Timestamp", CHANNEL_TYPE.CAL, "mSecs", hostMs);
		assertNotNull(cal);
		assertNotNull(raw);
		return o;
	}

	@Test
	public void theHeaderRowsAreTheNamesThenTheUnitsWithTheRawTimestampAsTimestampAlone() throws Exception {
		File folder = tmp.newFolder("rec");
		CsvRecording r = new CsvRecording(folder, "Shimmer3R", "E747AA7D2F31", sample(1420786, 1790960000331.4), 1792999200000L);
		r.append(sample(1420786, 1790960000331.4), 1790960000331.4);
		r.append(sample(1421426, 1790960000351.0), 1790960000351.0);
		r.close();
		assertTrue(r.getFile().getName().startsWith("Shimmer3R_2F31_"));
		String text = new String(Files.readAllBytes(r.getFile().toPath()), StandardCharsets.UTF_8);
		assertTrue("every line ends CRLF", text.endsWith("\r\n"));
		assertFalse("no bare LF", text.replace("\r\n", "").contains("\n"));
		String[] rows = text.split("\r\n", -1);
		assertEquals(5, rows.length);
		List<String> names = Arrays.asList(rows[0].split(","));
		assertEquals(Arrays.asList("HostTime_ms", "TIMESTAMP", "Timestamp_CAL", "GSR_Skin_Conductance_CAL",
				"GSR_Skin_Conductance_RAW", "Accel_LN_X_CAL", "Accel_LN_X_RAW", "System_Timestamp_CAL"), names);
		assertEquals("ms,ticks,mSecs,uS,uS,m/(s^2),no_units,mSecs", rows[1]);
		String[] first = rows[2].split(",");
		assertEquals(names.size(), first.length);
		assertEquals("1790960000331", first[0]);
		assertEquals("1420786", first[1]);
		assertEquals("9.62", first[3]);
		assertEquals("1203", first[4]);
		assertEquals("0.30000000000000004", first[5]);
		assertEquals(2, r.getRows());
	}

	// ------------------------------------------------------------------
	// The figures
	// ------------------------------------------------------------------

	@Test
	public void missingSamplesAreCountedFromGapsInTheDeviceTimestamps() {
		ReceptionStats stats = new ReceptionStats(0);
		stats.reset(RATE_HZ, 0);
		int[] periods = {0, 1, 3, 4, 6};
		for (int i = 0; i < periods.length; i++) {
			stats.onSample(periods[i] * PERIOD_MS, 1000 + periods[i] * PERIOD_MS, 100 * (i + 1));
		}
		ReceptionStats.Snapshot s = stats.snapshot(1000, false);
		assertEquals(5, s.samples);
		assertEquals(2, s.missing);
		assertEquals(100.0 * 5 / 7, s.reception, 1e-9);
	}

	@Test
	public void aStepIsRoundedToWholePeriods() {
		ReceptionStats stats = new ReceptionStats(0);
		stats.reset(RATE_HZ, 0);
		stats.onSample(0, 0, 0);
		stats.onSample(1.4 * PERIOD_MS, 0, 0);
		assertEquals(0, stats.snapshot(0, false).missing);
		stats.onSample(3.0 * PERIOD_MS, 0, 0);
		assertEquals(1, stats.snapshot(0, false).missing);
	}

	@Test
	public void receptionIsReceivedOverReceivedPlusMissing() {
		assertEquals(75.0, ReceptionStats.percent(3, 1), 1e-12);
		assertEquals(100.0, ReceptionStats.percent(10, 0), 1e-12);
		assertTrue(Double.isNaN(new ReceptionStats(0).snapshot(0, false).reception));
	}

	@Test
	public void delayIsArrivalLessDeviceTimeLessTheLeastSoFar() {
		ReceptionStats stats = new ReceptionStats(0);
		stats.reset(RATE_HZ, 0);
		double[] offsets = {5, 0, 30, 10};
		for (int i = 0; i < offsets.length; i++) {
			stats.onSample(i * PERIOD_MS, i * PERIOD_MS + offsets[i], 0);
		}
		ReceptionStats.Snapshot s = stats.snapshot(0, false);
		assertEquals(10, s.delayMs, 1e-9);
		assertEquals(30, s.maxDelayMs, 1e-9);
	}

	@Test
	public void rxPerSecondIsTheSamplesOverTheLastWindow() {
		ReceptionStats stats = new ReceptionStats(0);
		stats.reset(RATE_HZ, 0);
		for (int i = 0; i < 51; i++) {
			stats.onSample(i * PERIOD_MS, i * PERIOD_MS, 10 * i);
		}
		assertEquals(51.0, stats.snapshot(1000, true).rxPerSecond(), 1e-9);
		assertEquals(0.0, stats.snapshot(2000, true).rxPerSecond(), 1e-9);
		assertEquals(51, stats.snapshot(2000, true).samples);
	}

	@Test
	public void lastSampleIsMarkedStalledAfterASecondWhileStreaming() {
		ReceptionStats stats = new ReceptionStats(0);
		stats.reset(RATE_HZ, 0);
		assertEquals(-1, stats.snapshot(500, false).sinceLastMs);
		assertEquals("-", ShimmerCaptureJava.lastSample(-1, true));
		stats.onSample(0, 0, 1000);
		assertEquals(1500, stats.snapshot(2500, false).sinceLastMs);
		assertEquals("1.5 s ago, stalled", ShimmerCaptureJava.lastSample(1500, true));
		assertEquals("1.5 s ago", ShimmerCaptureJava.lastSample(1500, false));
		assertEquals("0.2 s ago", ShimmerCaptureJava.lastSample(200, true));
	}

	@Test
	public void aResetStartsAfresh() {
		ReceptionStats stats = new ReceptionStats(0);
		stats.reset(RATE_HZ, 0);
		stats.onSample(0, 0, 0);
		stats.onSample(4 * PERIOD_MS, 0, 0);
		stats.reset(RATE_HZ, 0);
		ReceptionStats.Snapshot s = stats.snapshot(0, false);
		assertEquals(0, s.samples);
		assertEquals(0, s.missing);
	}

	// ------------------------------------------------------------------
	// The plot and the state
	// ------------------------------------------------------------------

	@Test
	public void channelsGoInThePanelsCaptureMdNames() {
		assertEquals("Low-noise accel", Panels.of("Accel_LN_X"));
		assertEquals("Wide-range accel", Panels.of("Accel_WR_Z"));
		assertEquals("Gyro", Panels.of("Gyro_Y"));
		assertEquals("Mag", Panels.of("Mag_X"));
		assertEquals("Battery", Panels.of("Battery"));
		assertEquals("Pressure", Panels.of("Pressure_BMP280"));
		assertEquals("Temperature", Panels.of("Temperature_BMP280"));
		assertEquals("Temperature", Panels.of("Skin_Temperature"));
		assertEquals("ADC", Panels.of("Int_Exp_A12"));
		assertEquals("ADC", Panels.of("Ext_Exp_A7"));
		assertEquals("ExG", Panels.of("ExG1_CH1_24BIT"));
		assertEquals("ExG", Panels.of("ECG_LL-RA_24BIT"));
		assertEquals("Other", Panels.of("ExG1_Status1"));
		assertEquals("GSR", Panels.of("GSR_Skin_Conductance"));
		assertEquals("PPG", Panels.of("PPG_A13"));
		assertEquals("PPG", Panels.of("PPG1_A12"));
		assertEquals("Other", Panels.of("Sound_Level"));
		assertEquals("Other", Panels.of("Batt_Percentage"));
		assertNull(Panels.of("Timestamp"));
		assertNull(Panels.of("System_Timestamp_Plot"));
		assertNull(Panels.of("Packet_Reception_Rate_Trial"));
	}

	@Test
	public void theDriversStatesAreCaptureMdsStates() {
		assertEquals("READY", CaptureDevice.captureState(BT_STATE.CONNECTED));
		assertEquals("READY", CaptureDevice.captureState(BT_STATE.SDLOGGING));
		assertEquals("STREAMING", CaptureDevice.captureState(BT_STATE.STREAMING));
		assertEquals("STREAMING", CaptureDevice.captureState(BT_STATE.STREAMING_AND_SDLOGGING));
		assertEquals("CONNECTING", CaptureDevice.captureState(BT_STATE.CONNECTING));
		assertEquals("CONFIGURING", CaptureDevice.captureState(BT_STATE.CONFIGURING));
		assertEquals("FAILED", CaptureDevice.captureState(BT_STATE.CONNECTION_FAILED));
		assertEquals("DISCONNECTED", CaptureDevice.captureState(BT_STATE.CONNECTION_LOST));
		assertEquals("DISCONNECTED", CaptureDevice.captureState(BT_STATE.DISCONNECTED));
	}

}
