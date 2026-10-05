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

	@Test
	public void pythonReferenceMatchesTheJavaDecoder() throws Exception {
		Assume.assumeTrue("the Python port is not in this repository", REFERENCE.getParentFile().isDirectory());
		List<ObjectCluster> samples = ProtocolReplay.run(new Shimmer3RProtocol(),
				RecordedSession.load(API_00027_Shimmer3RProtocolMatchesDriverTest.SESSION)).samples;
		String csv = toCsv(samples);
		CURRENT.getParentFile().mkdirs();
		Files.write(CURRENT.toPath(), csv.getBytes(StandardCharsets.UTF_8));

		// git may have checked the file out with CRLF line endings.
		String committed = REFERENCE.isFile()
				? new String(Files.readAllBytes(REFERENCE.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n")
				: "";
		assertTrue(REFERENCE + " is out of date: copy " + CURRENT.getAbsolutePath() + " over it", committed.equals(csv));
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
