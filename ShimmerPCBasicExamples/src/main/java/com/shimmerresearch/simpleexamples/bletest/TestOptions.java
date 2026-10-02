package com.shimmerresearch.simpleexamples.bletest;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Command-line options for {@link BleHardwareTestApp} and {@link KillAppChild}. */
public class TestOptions {

	public static final List<String> ALL_TESTS = Arrays.asList("connect", "stream", "kill", "loss", "multi");

	public String transport = "native";
	public String device;
	public String device2;
	/** gRPC only: MAC addresses without separators, e.g. E8EB1B712F31. Required on Windows. */
	public String mac;
	public String mac2;
	/** Native only: device IDs to fall back to when a device does not advertise (it may be connected already). */
	public String deviceId;
	public String deviceId2;
	public String grpcServer;
	public List<String> tests = new ArrayList<String>(Arrays.asList("connect", "stream", "kill", "loss"));
	public int iterations = 20;
	public int streamSeconds = 600;
	public int multiSeconds = 60;
	public double minReceptionRate = 99.0;
	public int lossTimeoutSeconds = 10;
	public File reportDir = new File(System.getProperty("user.dir"), "ble-test-reports");
	public boolean logBytes = false;

	public static String usage() {
		return String.join("\n",
				"BLE hardware tests for ShimmerBLENative (and ShimmerGRPC, as a baseline).",
				"",
				"  --device <name>        advertised name to match, e.g. Shimmer3R-2F31 (required)",
				"  --transport native|grpc  default native",
				"  --tests <list>         comma-separated: " + String.join(",", ALL_TESTS) + ", or all",
				"                         default connect,stream,kill,loss (multi needs --device2)",
				"  --iterations <n>       connect/disconnect cycles (default 20)",
				"  --stream-seconds <n>   long stream duration (default 600)",
				"  --min-reception <pct>  pass threshold for packet reception rate (default 99.0)",
				"  --loss-timeout <s>     how soon a lost link must be reported (default 10)",
				"  --device2 <name>       second device, for the multi test",
				"  --multi-seconds <n>    multi-device stream duration (default 60)",
				"  --grpc-server <path>   gRPC only: path to ShimmerBLEGrpc.exe",
				"  --mac <mac> / --mac2   gRPC on Windows: device MAC without separators",
				"  --device-id <id> / --device-id2  native: ID to use if the device is connected and so",
				"                         not advertising (Windows: MAC with colons; macOS: UUID)",
				"  --report-dir <dir>     where reports go (default ./ble-test-reports)",
				"  --log-bytes            native only: also log every byte sent and received",
				"",
				"Tests use the device's current configuration; set it up first with ShimmerBLECaptureExample.");
	}

	public static TestOptions parse(String[] args) {
		TestOptions o = new TestOptions();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			switch (a) {
			case "--help":
			case "-h":
				throw new IllegalArgumentException("");
			case "--log-bytes":
				o.logBytes = true;
				continue;
			default:
				break;
			}
			if (i + 1 >= args.length) {
				throw new IllegalArgumentException("missing value for " + a);
			}
			String v = args[++i];
			switch (a) {
			case "--transport":
				o.transport = v;
				break;
			case "--device":
				o.device = v;
				break;
			case "--device2":
				o.device2 = v;
				break;
			case "--mac":
				o.mac = v;
				break;
			case "--mac2":
				o.mac2 = v;
				break;
			case "--device-id":
				o.deviceId = v;
				break;
			case "--device-id2":
				o.deviceId2 = v;
				break;
			case "--grpc-server":
				o.grpcServer = v;
				break;
			case "--tests":
				o.tests = v.equals("all") ? new ArrayList<String>(ALL_TESTS) : new ArrayList<String>(Arrays.asList(v.split(",")));
				break;
			case "--iterations":
				o.iterations = Integer.parseInt(v);
				break;
			case "--stream-seconds":
				o.streamSeconds = Integer.parseInt(v);
				break;
			case "--multi-seconds":
				o.multiSeconds = Integer.parseInt(v);
				break;
			case "--min-reception":
				o.minReceptionRate = Double.parseDouble(v);
				break;
			case "--loss-timeout":
				o.lossTimeoutSeconds = Integer.parseInt(v);
				break;
			case "--report-dir":
				o.reportDir = new File(v);
				break;
			default:
				throw new IllegalArgumentException("unknown option " + a);
			}
		}
		if (o.device == null) {
			throw new IllegalArgumentException("--device is required");
		}
		if (!o.transport.equals("native") && !o.transport.equals("grpc")) {
			throw new IllegalArgumentException("--transport must be native or grpc");
		}
		for (String t : o.tests) {
			if (!ALL_TESTS.contains(t)) {
				throw new IllegalArgumentException("unknown test " + t);
			}
		}
		return o;
	}

	/** What {@link BleTransport#open} falls back to for the first device: a native ID or a gRPC MAC. */
	public String knownId() {
		return transport.equals("grpc") ? mac : deviceId;
	}

	public String knownId2() {
		return transport.equals("grpc") ? mac2 : deviceId2;
	}

	/** The arguments a child process needs to open the same device the same way. */
	public List<String> childArgs() {
		List<String> args = new ArrayList<String>(Arrays.asList("--transport", transport, "--device", device));
		if (mac != null) {
			args.addAll(Arrays.asList("--mac", mac));
		}
		if (grpcServer != null) {
			args.addAll(Arrays.asList("--grpc-server", grpcServer));
		}
		return args;
	}
}
