package com.shimmerresearch.hil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Everything one scenario run found: a JSON report, a CSV of the samples it judged and the
 * driver's own log, written to the folder the runner passes (hil.outDir).
 */
final class HilReport {

	private final String scenario;
	private final String unit;
	private final JsonObject json = new JsonObject();
	private final List<String> failures = new ArrayList<>();
	private List<StreamAnalysis.Sample> samples;

	HilReport(String scenario, String unit, String port, String mac, String type, Map<String, String> args) {
		this.scenario = scenario;
		this.unit = unit;
		json.addProperty("scenario", scenario);
		json.addProperty("unit", unit);
		json.addProperty("port", port);
		json.addProperty("mac", mac);
		json.addProperty("type", type);
		json.addProperty("started", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).format(new Date()));
		JsonObject a = new JsonObject();
		args.forEach(a::addProperty);
		json.add("args", a);
	}

	/** JSON has no NaN or infinity, and a failing run is the likeliest to produce one: store null. */
	static JsonElement num(double v) {
		return Double.isNaN(v) || Double.isInfinite(v) ? JsonNull.INSTANCE : new JsonPrimitive(v);
	}

	void put(String key, Object value) {
		if (value == null) {
			json.add(key, JsonNull.INSTANCE);
		} else if (value instanceof Double || value instanceof Float) {
			json.add(key, num(((Number) value).doubleValue()));
		} else if (value instanceof Number) {
			json.addProperty(key, (Number) value);
		} else if (value instanceof Boolean) {
			json.addProperty(key, (Boolean) value);
		} else if (value instanceof Map) {
			JsonObject o = new JsonObject();
			((Map<?, ?>) value).forEach((k, v) -> o.addProperty(String.valueOf(k), v instanceof Number ? (Number) v : null));
			json.add(key, o);
		} else if (value instanceof List) {
			JsonArray arr = new JsonArray();
			for (Object o : (List<?>) value) {
				arr.add(o instanceof Number ? new JsonPrimitive((Number) o) : new JsonPrimitive(String.valueOf(o)));
			}
			json.add(key, arr);
		} else {
			json.addProperty(key, String.valueOf(value));
		}
	}

	void putStream(String key, StreamAnalysis a) {
		JsonObject o = new JsonObject();
		o.addProperty("received", a.received);
		o.addProperty("missing", a.missing);
		o.add("lossPercent", num(a.lossPercent()));
		o.addProperty("nonIncreasing", a.nonIncreasing);
		o.addProperty("timestampJumps", a.timestampJumps.size());
		o.add("effectiveRateHz", num(a.effectiveRateHz));
		o.add("accelMagnitudeMean", num(a.accelMagnitudeMean));
		o.add("accelMagnitudeStd", num(a.accelMagnitudeStd));
		o.addProperty("gaps", a.gaps.size());
		json.add(key, o);
	}

	/** Every gap and timestamp jump, with when it arrived, to match against what the test was doing. */
	void putGaps(StreamAnalysis a) {
		JsonArray gaps = new JsonArray();
		for (StreamAnalysis.Gap g : a.gaps) {
			JsonObject o = new JsonObject();
			o.addProperty("afterReceivedMs", Math.round(g.afterReceivedMs));
			o.add("afterTimestampMs", num(g.afterTimestampMs));
			o.addProperty("missing", g.missing);
			gaps.add(o);
		}
		json.add("gapList", gaps);
		JsonArray jumps = new JsonArray();
		for (StreamAnalysis.Gap g : a.timestampJumps) {
			JsonObject o = new JsonObject();
			o.addProperty("afterReceivedMs", Math.round(g.afterReceivedMs));
			o.add("afterTimestampMs", num(g.afterTimestampMs));
			o.addProperty("periodsAhead", g.missing + 1);
			jumps.add(o);
		}
		json.add("timestampJumpList", jumps);
	}

	void samples(List<StreamAnalysis.Sample> samples) {
		this.samples = samples;
	}

	/** Records a failed check; the test fails at the end with every one of them. */
	void check(boolean ok, String failure) {
		if (!ok) {
			failures.add(failure);
			System.out.println(scenario + " " + unit + " FAIL: " + failure);
		}
	}

	List<String> failures() {
		return failures;
	}

	/** Writes <scenario>_<unit>.json, .csv (samples, if any) and .driver.log into outDir. */
	void write(File outDir, List<String> events, List<String> driverLog) {
		try {
			json.addProperty("passed", failures.isEmpty());
			JsonArray f = new JsonArray();
			failures.forEach(f::add);
			json.add("failures", f);
			JsonArray e = new JsonArray();
			events.forEach(e::add);
			json.add("events", e);
			if (!outDir.exists() && !outDir.mkdirs()) {
				throw new IOException("cannot create " + outDir);
			}
			String base = scenario + "_" + unit.replaceAll("[^\\w.-]", "_");
			try (Writer w = Files.newBufferedWriter(new File(outDir, base + ".json").toPath(), StandardCharsets.UTF_8)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(json, w);
			}
			if (samples != null) {
				try (Writer w = Files.newBufferedWriter(new File(outDir, base + ".csv").toPath(), StandardCharsets.UTF_8)) {
					w.write("received_ms,timestamp_ms,accel_x,accel_y,accel_z\n");
					for (StreamAnalysis.Sample s : samples) {
						w.write(String.format(Locale.ROOT, "%.0f,%.3f,%.4f,%.4f,%.4f\n",
								s.receivedMs, s.timestampMs, s.accelX, s.accelY, s.accelZ));
					}
				}
			}
			Files.write(new File(outDir, base + ".driver.log").toPath(), driverLog, StandardCharsets.UTF_8);
			System.out.println("report written: " + new File(outDir, base + ".json"));
		} catch (IOException ex) {
			System.out.println("could not write report: " + ex);
		}
	}
}
