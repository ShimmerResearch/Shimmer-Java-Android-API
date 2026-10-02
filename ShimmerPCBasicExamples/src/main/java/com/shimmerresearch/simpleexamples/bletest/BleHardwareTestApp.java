package com.shimmerresearch.simpleexamples.bletest;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Runs DEV-1132's BLE hardware test matrix against one or two devices and writes a Markdown
 * report. Run it with {@code --transport native} and again with {@code --transport grpc} on the
 * same hardware to compare the native library with the gRPC baseline.
 * <p>
 * From ShimmerPCBasicExamples: {@code ./gradlew runBleTest --args="--device Shimmer3R-2F31"}.
 * {@code --help} lists the options.
 */
public class BleHardwareTestApp {

	public static void main(String[] args) {
		TestOptions options;
		try {
			options = TestOptions.parse(args);
		} catch (IllegalArgumentException e) {
			if (!e.getMessage().isEmpty()) {
				System.err.println(e.getMessage());
				System.err.println();
			}
			System.err.println(TestOptions.usage());
			System.exit(2);
			return;
		}
		System.exit(run(options) ? 0 : 1);
	}

	static boolean run(TestOptions options) {
		BleTestReport report = new BleTestReport(options);
		File reportFile = BleTestReport.fileFor(options, ".md");
		File byteLog = BleTestReport.fileFor(options, ".bytes.log");
		reportFile.getParentFile().mkdirs();

		BleTransport transport = null;
		try {
			transport = BleTransport.create(options, byteLog);
			for (Map.Entry<String, String> e : transport.describe().entrySet()) {
				report.environment(e.getKey(), e.getValue());
			}
			HardwareTests tests = new HardwareTests(options, transport);

			// The kill test runs first: its child process must find the device free.
			if (options.tests.contains("kill")) {
				timed(report, "kill", () -> tests.killApp());
			}

			DeviceUnderTest dut = null;
			if (options.tests.contains("connect") || options.tests.contains("stream") || options.tests.contains("loss")) {
				System.out.println("Looking for " + options.device + " ...");
				dut = transport.open(options.device, options.knownId());
				System.out.println("Found " + dut.label());
			}
			final DeviceUnderTest device = dut;
			if (options.tests.contains("connect")) {
				timed(report, "connect", () -> tests.connectLoop(device));
			}
			if (options.tests.contains("stream")) {
				timed(report, "stream", () -> tests.longStream(device));
			}
			if (options.tests.contains("loss")) {
				long started = System.currentTimeMillis();
				List<TestResult> results = tests.lossAndReconnect(device);
				for (TestResult r : results) {
					r.durationMs = System.currentTimeMillis() - started;
					report.add(r);
					print(r);
				}
			}
			if (options.tests.contains("multi")) {
				if (options.device2 == null) {
					TestResult r = new TestResult("multi").skip("needs --device2");
					report.add(r);
					print(r);
				} else {
					final DeviceUnderTest first = dut != null ? dut : transport.open(options.device, options.knownId());
					final DeviceUnderTest second = transport.open(options.device2, options.knownId2());
					timed(report, "multi", () -> tests.multiDevice(first, second));
				}
			}
		} catch (Exception e) {
			TestResult r = new TestResult("setup").fail(e.getMessage());
			report.add(r);
			print(r);
		} finally {
			if (transport != null) {
				transport.close();
			}
		}

		try {
			report.write(reportFile);
			System.out.println();
			System.out.println("Report: " + reportFile.getAbsolutePath());
			if (options.logBytes && byteLog.isFile()) {
				System.out.println("Byte log: " + byteLog.getAbsolutePath());
			}
		} catch (Exception e) {
			System.err.println("Could not write the report: " + e.getMessage());
		}
		return report.allPassed();
	}

	private interface Test {
		TestResult run() throws Exception;
	}

	private static void timed(BleTestReport report, String name, Test test) {
		System.out.println();
		System.out.println("=== " + name + " ===");
		long started = System.currentTimeMillis();
		TestResult r;
		try {
			r = test.run();
		} catch (Exception e) {
			r = new TestResult(name).fail(e.getMessage());
		}
		r.durationMs = System.currentTimeMillis() - started;
		report.add(r);
		print(r);
	}

	private static void print(TestResult r) {
		System.out.println("--> " + r.test + ": " + r.outcome + " - " + r.summary);
	}
}
