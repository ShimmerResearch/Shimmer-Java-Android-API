package com.shimmerresearch.driver.ble.nativeble;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds and loads the shimmerble native library for the running JVM, then checks its version.
 * <p>
 * Looks, in order:
 * <ol>
 * <li>at the path in the {@value #PATH_PROPERTY} system property, if set;</li>
 * <li>next to the jar (or classes folder) holding this class, as {@code <lib>} or
 * {@code native/<platform>/<lib>} - the case for an installed application;</li>
 * <li>inside the jar at {@code /native/<platform>/<lib>}, extracted once to a per-user cache
 * folder - the case for examples run from an IDE or Gradle.</li>
 * </ol>
 * {@code <platform>} is chosen from the JVM's {@code os.name} and {@code os.arch}, so an x86_64 JVM
 * on an Apple silicon Mac needs the macos-x64 library, not macos-arm64.
 */
public final class NativeBleLoader {

	/** Must equal the version in ShimmerBLENativeLib/Cargo.toml. */
	public static final String EXPECTED_NATIVE_VERSION = "0.1.0";
	/** System property overriding where the library is loaded from. */
	public static final String PATH_PROPERTY = "shimmer.ble.lib";
	public static final String LIBRARY_NAME = "shimmerble";

	private static boolean sLoaded = false;

	private NativeBleLoader() {
	}

	/** Loads the library once per JVM. Later calls return immediately. */
	public static synchronized void load() throws NativeBleException {
		if (sLoaded) {
			return;
		}
		String platform = platformDirectory(System.getProperty("os.name"), System.getProperty("os.arch"));
		String fileName = System.mapLibraryName(LIBRARY_NAME);
		List<String> looked = new ArrayList<String>();

		File library = findLibrary(platform, fileName, looked);
		if (library == null) {
			throw new NativeBleException("BLE native library " + fileName + " not found for " + platform
					+ "; looked in: " + String.join(", ", looked));
		}
		try {
			System.load(library.getAbsolutePath());
		} catch (UnsatisfiedLinkError e) {
			throw new NativeBleException("Could not load " + library + " (" + e.getMessage() + ")", e);
		}

		String version = NativeBle.nativeVersion();
		if (!EXPECTED_NATIVE_VERSION.equals(version)) {
			throw new NativeBleException("BLE native library " + library + " is version " + version
					+ ", but this driver expects " + EXPECTED_NATIVE_VERSION + ". Rebuild or update it.");
		}
		sLoaded = true;
	}

	/**
	 * The folder name for a platform, e.g. "windows-x64" or "macos-arm64".
	 *
	 * @throws NativeBleException for a platform no library is built for
	 */
	static String platformDirectory(String osName, String osArch) throws NativeBleException {
		String os = osName == null ? "" : osName.toLowerCase();
		String arch = osArch == null ? "" : osArch.toLowerCase();
		boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
		boolean x64 = arch.equals("amd64") || arch.equals("x86_64");

		String family;
		if (os.startsWith("windows")) {
			family = "windows";
		} else if (os.startsWith("mac")) {
			family = "macos";
		} else if (os.startsWith("linux")) {
			family = "linux";
		} else {
			throw new NativeBleException("BLE native library is not available for OS " + osName);
		}
		if (arm64) {
			return family + "-arm64";
		}
		if (x64) {
			return family + "-x64";
		}
		throw new NativeBleException("BLE native library is not available for " + osName + " on " + osArch
				+ " (needs a 64-bit JVM)");
	}

	private static File findLibrary(String platform, String fileName, List<String> looked)
			throws NativeBleException {
		String override = System.getProperty(PATH_PROPERTY);
		if (override != null && !override.isEmpty()) {
			File f = new File(override);
			looked.add(f.getPath());
			// An explicit override that does not exist is an error, not something to fall back from.
			if (!f.isFile()) {
				throw new NativeBleException(PATH_PROPERTY + " points at " + f + ", which does not exist");
			}
			return f;
		}

		File codeLocation = codeLocation();
		if (codeLocation != null) {
			File dir = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
			File[] candidates = { new File(dir, fileName), new File(dir, "native/" + platform + "/" + fileName) };
			for (File candidate : candidates) {
				looked.add(candidate.getPath());
				if (candidate.isFile()) {
					return candidate;
				}
			}
		}

		String resource = "/native/" + platform + "/" + fileName;
		looked.add("classpath:" + resource);
		return extractResource(resource, fileName);
	}

	private static File codeLocation() {
		try {
			return new File(NativeBleLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException | SecurityException | NullPointerException e) {
			return null;
		}
	}

	/**
	 * Copies a library out of the jar into ~/.shimmer/native/shimmerble-&lt;version&gt;/. The folder is
	 * versioned so a running JVM, which locks the file on Windows, never blocks a newer copy.
	 */
	private static File extractResource(String resource, String fileName) throws NativeBleException {
		InputStream in = NativeBleLoader.class.getResourceAsStream(resource);
		if (in == null) {
			return null;
		}
		File dir = new File(System.getProperty("user.home"),
				".shimmer/native/" + LIBRARY_NAME + "-" + EXPECTED_NATIVE_VERSION);
		File target = new File(dir, fileName);
		try {
			byte[] bundled;
			try {
				bundled = readAll(in);
			} finally {
				in.close();
			}
			// Compare contents, not size: during development a rebuilt library keeps its version.
			if (target.isFile() && Arrays.equals(bundled, Files.readAllBytes(target.toPath()))) {
				return target;
			}
			Files.createDirectories(dir.toPath());
			File temp = File.createTempFile(fileName, ".tmp", dir);
			Files.write(temp.toPath(), bundled);
			try {
				Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} finally {
				Files.deleteIfExists(temp.toPath());
			}
		} catch (IOException e) {
			// A copy already in use (and so locked) by another JVM is still fine to load.
			if (target.isFile()) {
				return target;
			}
			throw new NativeBleException("Could not extract " + resource + " to " + dir + ": " + e.getMessage(), e);
		}
		return target;
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[64 * 1024];
		int n;
		while ((n = in.read(chunk)) > 0) {
			out.write(chunk, 0, n);
		}
		return out.toByteArray();
	}
}
