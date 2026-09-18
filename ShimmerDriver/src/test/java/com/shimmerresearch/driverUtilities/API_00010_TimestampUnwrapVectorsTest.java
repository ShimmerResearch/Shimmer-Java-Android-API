package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Runs {@link TimestampUnwrap} against the conformance vectors shared by every
 * Shimmer host API.
 * <p>
 * The vectors live in <code>log-and-stream-common</code> at
 * <code>Test/conformance/timestamp_unwrap.json</code>, alongside the rule they
 * encode (<code>docs/SHIMMER3_STREAMING_DATA_FORMAT.md</code> section 2.1) and
 * the reference implementation that generates them. The copy in this project's
 * test resources is byte-identical to revision 1, taken at commit
 * <code>267f693</code>.
 * <p>
 * The point of the file is that the C# API, pyshimmer and the TypeScript web SDK
 * run the same cases. Four implementations of one wire format drifted apart once
 * already - the same unwrap defect sat in all four - and reviewing them against
 * each other by hand is what let that happen. If this test and its counterparts
 * disagree, one of the four is wrong.
 * <p>
 * {@link API_00009_TimestampUnwrapTest} covers the same rule in a form meant to
 * be read; this one covers it in a form meant to be shared.
 *
 * @author Mark Nolan
 */
public class API_00010_TimestampUnwrapVectorsTest {

	private static final String VECTORS_RESOURCE = "/timestamp_unwrap.json";

	/** The revision of the vector file this test was written against. */
	private static final int EXPECTED_REVISION = 1;

	/**
	 * Every vector the file is expected to contain. Listed rather than discovered
	 * so that a vector quietly dropped upstream fails here instead of silently
	 * reducing what is covered.
	 */
	private static final List<String> EXPECTED_IDS = Arrays.asList(
			"monotonic-24bit",
			"wrap-24bit",
			"wrap-lands-on-zero-24bit",
			"invalid-zero-signature-24bit",
			"invalid-zero-no-cascade-24bit",
			"first-sample-zero-24bit",
			"wrap-16bit",
			"zero-on-16bit-is-a-wrap",
			"backward-step-outside-window-is-a-wrap-24bit",
			"duplicate-24bit",
			"reorder-one-period-24bit",
			"reorder-one-period-16bit",
			"reorder-across-wrap-boundary-24bit",
			"wrap-after-heavy-loss-24bit",
			"wrap-after-heavy-loss-16bit",
			"wrap-spanning-dropout-1p8s-16bit",
			"wrap-spanning-dropout-152s-24bit",
			"rate-unknown-backward-step-is-a-wrap-24bit",
			"rate-unknown-zero-still-rejected-24bit",
			"zero-within-window-of-origin-24bit",
			"zero-within-window-after-wrap-24bit",
			"reorder-window-boundary-inclusive-24bit",
			"reorder-window-boundary-exclusive-24bit",
			"low-rate-clamp-16bit",
			"high-rate-reorder-24bit",
			"reorder-beyond-eight-periods-is-a-wrap-24bit",
			"reorder-onto-origin-then-earlier-packet-24bit");

	private static JsonObject loadVectors() throws Exception {
		InputStream stream = API_00010_TimestampUnwrapVectorsTest.class.getResourceAsStream(VECTORS_RESOURCE);
		assertNotNull("vector file is on the test classpath at " + VECTORS_RESOURCE, stream);
		try {
			InputStreamReader reader = new InputStreamReader(stream, Charset.forName("UTF-8"));
			return new JsonParser().parse(reader).getAsJsonObject();
		} finally {
			stream.close();
		}
	}

	/** Feeds a series of raw values through the unwrapper, as a caller would. */
	private static class Unwrapper {
		double lastUnwrapped = 0;
		double cycle = 0;
		boolean hasPrevious = false;
		boolean lastRejected = false;
		final double window;

		Unwrapper(double window) {
			this.window = window;
		}

		double feed(double rawTicks, int maxTicks) {
			//The six-argument form: a stream knows whether it has seen a sample, and
			//(0, 0) cannot say so on its own once a reorder can land on the origin.
			TimestampUnwrap.Result r = TimestampUnwrap.unwrap(rawTicks, lastUnwrapped, cycle, maxTicks,
					window, hasPrevious);
			hasPrevious = true;
			lastRejected = r.rejected;
			cycle = r.cycle;
			lastUnwrapped = r.unwrapped;
			return r.unwrapped;
		}
	}

	@Test
	public void testVectorFileIsTheRevisionThisTestWasWrittenFor() throws Exception {
		JsonObject doc = loadVectors();
		assertEquals("vector file revision - update this test deliberately, not to make it pass",
				EXPECTED_REVISION, doc.get("revision").getAsInt());
		assertEquals("ticks per second", 32768, doc.get("ticksPerSecond").getAsInt());
		assertEquals("invalid-zero window", TimestampUnwrap.WRAP_WINDOW_TICKS,
				doc.get("invalidZeroWindowTicks").getAsInt());
		assertEquals("reorder periods", TimestampUnwrap.REORDER_PERIODS,
				doc.get("reorderPeriods").getAsInt());
		assertEquals("max window divisor", TimestampUnwrap.MAX_WINDOW_DIVISOR,
				doc.get("maxWindowDivisor").getAsInt());
	}

	@Test
	public void testEveryExpectedVectorIsPresent() throws Exception {
		JsonArray vectors = loadVectors().getAsJsonArray("vectors");
		List<String> found = new ArrayList<String>();
		for (JsonElement element : vectors) {
			found.add(element.getAsJsonObject().get("id").getAsString());
		}
		Set<String> missing = new HashSet<String>(EXPECTED_IDS);
		missing.removeAll(found);
		assertTrue("vectors missing from the file: " + missing, missing.isEmpty());
		Set<String> unexpected = new HashSet<String>(found);
		unexpected.removeAll(EXPECTED_IDS);
		assertTrue("vectors in the file this test does not know about: " + unexpected
				+ " - add them to EXPECTED_IDS once you have read what they assert", unexpected.isEmpty());
		assertEquals("vector count", EXPECTED_IDS.size(), found.size());
	}

	@Test
	public void testAllSharedVectorsAgree() throws Exception {
		JsonArray vectors = loadVectors().getAsJsonArray("vectors");

		for (JsonElement element : vectors) {
			JsonObject vector = element.getAsJsonObject();
			String id = vector.get("id").getAsString();
			int modulo = vector.get("modulo").getAsInt();
			double window = vector.get("reorderWindowTicks").getAsDouble();
			JsonArray raw = vector.getAsJsonArray("raw");
			JsonArray expectedUnwrapped = vector.getAsJsonArray("expectedUnwrapped");
			JsonArray expectedRejected = vector.getAsJsonArray("expectedRejected");

			assertEquals(id + ": vector is self-consistent", raw.size(), expectedUnwrapped.size());
			assertEquals(id + ": vector is self-consistent", raw.size(), expectedRejected.size());

			Unwrapper u = new Unwrapper(window);
			for (int i = 0; i < raw.size(); i++) {
				double got = u.feed(raw.get(i).getAsDouble(), modulo);
				assertEquals(id + " [" + i + "] raw=" + raw.get(i).getAsLong() + ": unwrapped",
						expectedUnwrapped.get(i).getAsDouble(), got, 0.0);
				assertEquals(id + " [" + i + "] raw=" + raw.get(i).getAsLong() + ": rejected",
						expectedRejected.get(i).getAsBoolean(), u.lastRejected);
			}
			assertEquals(id + ": final cycle", vector.get("expectedFinalCycle").getAsDouble(), u.cycle, 0.0);
		}
	}

	@Test
	public void testWindowDerivationCases() throws Exception {
		JsonArray cases = loadVectors().getAsJsonObject("windowDerivation").getAsJsonArray("cases");
		assertTrue("derivation cases present", cases.size() > 0);

		for (JsonElement element : cases) {
			JsonObject testCase = element.getAsJsonObject();
			JsonElement rate = testCase.get("samplingRateHz");
			int modulo = 1 << testCase.get("timestampBits").getAsInt();
			double expected = testCase.get("expectedReorderWindowTicks").getAsDouble();
			double tolerance = testCase.get("tolerance").getAsDouble();

			// JSON has no way to spell "not known", so a null rate stands for it.
			// Every host maps its own absent-rate value onto the same zero window.
			double rateHz = rate.isJsonNull() ? 0.0 : rate.getAsDouble();
			assertEquals("window for rate " + (rate.isJsonNull() ? "null" : rate.getAsString())
					+ " at modulo " + modulo,
					expected, TimestampUnwrap.reorderWindowTicks(rateHz, modulo), tolerance);
		}
	}

	/**
	 * The values JSON cannot carry. An unknown rate arriving as a division result
	 * rather than as a literal zero is the case that matters: a host that lets it
	 * through produces an infinite window, which reads every backward step as a
	 * reorder and loses every wrap.
	 */
	@Test
	public void testWindowIsZeroForEveryShapeOfUnknownRate() {
		int modulo = TimestampUnwrap.TICKS_MAX_3_BYTE;
		assertEquals("positive infinity", 0.0,
				TimestampUnwrap.reorderWindowTicks(Double.POSITIVE_INFINITY, modulo), 0.0);
		assertEquals("negative infinity", 0.0,
				TimestampUnwrap.reorderWindowTicks(Double.NEGATIVE_INFINITY, modulo), 0.0);
		assertEquals("NaN", 0.0, TimestampUnwrap.reorderWindowTicks(Double.NaN, modulo), 0.0);
		assertEquals("zero", 0.0, TimestampUnwrap.reorderWindowTicks(0.0, modulo), 0.0);
		assertEquals("negative", 0.0, TimestampUnwrap.reorderWindowTicks(-5.0, modulo), 0.0);
	}
}
