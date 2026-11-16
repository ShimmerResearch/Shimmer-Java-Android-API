package com.shimmerresearch.capture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Which plot panel a channel goes in, by the start of its name, as shimmer-host-core's
 * bindings/CAPTURE.md ("The plot") has every Capture app group them. The driver's channel names
 * are the library's.
 */
public final class Panels {

	public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
			"Low-noise accel", "Wide-range accel", "Gyro", "Mag", "Battery", "Pressure", "Temperature",
			"ADC", "ExG", "GSR", "PPG", "Other"));

	private static final Pattern ADC = Pattern.compile("(Int|Ext)_Exp_A\\d+");
	private static final Pattern EXG = Pattern.compile("(ExG[12]_CH|Test_CHIP[12]_CH|ECG_|EMG_).*");
	private static final Pattern EXG_STATUS = Pattern.compile(".*_Status\\d");
	private static final Pattern PPG = Pattern.compile("PPG\\d?_A\\d+");

	private Panels() {
	}

	/** The panel for a channel, or null for the timestamps and the reception figures, which are not plotted */
	public static String of(String name) {
		if (name.contains("Timestamp") || name.startsWith("Packet_Reception_Rate")) {
			return null;
		}
		if (name.startsWith("Accel_LN_")) {
			return "Low-noise accel";
		}
		if (name.startsWith("Accel_WR_")) {
			return "Wide-range accel";
		}
		if (name.startsWith("Gyro_")) {
			return "Gyro";
		}
		if (name.startsWith("Mag_")) {
			return "Mag";
		}
		if (name.equals("Battery")) {
			return "Battery";
		}
		if (name.startsWith("Pressure_")) {
			return "Pressure";
		}
		if (name.startsWith("Temperature_") || name.equals("Skin_Temperature")) {
			return "Temperature";
		}
		if (ADC.matcher(name).matches()) {
			return "ADC";
		}
		if (EXG.matcher(name).matches() && !EXG_STATUS.matcher(name).matches()) {
			return "ExG";
		}
		if (name.startsWith("GSR_")) {
			return "GSR";
		}
		if (PPG.matcher(name).matches()) {
			return "PPG";
		}
		return "Other";
	}

}
