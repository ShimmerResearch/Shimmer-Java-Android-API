package com.shimmerresearch.simpleexamples.bletest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Markdown report of one run: environment, a summary table and each test's measurements.
 * Markdown so it can be pasted straight into a Jira comment.
 */
public class BleTestReport {

	private final Date mStarted = new Date();
	private final Map<String, String> mEnvironment = new LinkedHashMap<String, String>();
	private final List<TestResult> mResults = new ArrayList<TestResult>();

	public BleTestReport(TestOptions options) {
		mEnvironment.put("transport", options.transport);
		mEnvironment.put("device", options.device + (options.device2 == null ? "" : ", " + options.device2));
		mEnvironment.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
		mEnvironment.put("JVM", System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " ("
				+ System.getProperty("os.arch") + ")");
		mEnvironment.put("started", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(mStarted));
	}

	public void environment(String key, String value) {
		mEnvironment.put(key, value);
	}

	public void add(TestResult result) {
		mResults.add(result);
	}

	public boolean allPassed() {
		for (TestResult r : mResults) {
			if (r.outcome == TestResult.Outcome.FAIL) {
				return false;
			}
		}
		return true;
	}

	/** e.g. ble-test-reports/20261002_213000_native.md */
	public static File fileFor(TestOptions options, String suffix) {
		String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		return new File(options.reportDir, stamp + "_" + options.transport + suffix);
	}

	public void write(File file) throws IOException {
		file.getParentFile().mkdirs();
		try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
			out.println("# BLE hardware test report");
			out.println();
			for (Map.Entry<String, String> e : mEnvironment.entrySet()) {
				out.println("- **" + e.getKey() + ":** " + e.getValue());
			}
			out.println();
			out.println("| Test | Result | Summary | Time |");
			out.println("|---|---|---|---|");
			for (TestResult r : mResults) {
				out.println("| " + r.test + " | " + r.outcome + " | " + r.summary.replace("|", "/") + " | "
						+ r.durationMs / 1000 + " s |");
			}
			for (TestResult r : mResults) {
				if (r.details.isEmpty()) {
					continue;
				}
				out.println();
				out.println("## " + r.test);
				out.println();
				out.println("```");
				for (String line : r.details) {
					out.println(line);
				}
				out.println("```");
			}
		}
	}
}
