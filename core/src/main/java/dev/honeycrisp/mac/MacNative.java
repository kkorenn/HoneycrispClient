package dev.honeycrisp.mac;

import dev.honeycrisp.Honeycrisp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/**
 * JNI bridge to libhoneycrisp (bundled at natives/libhoneycrisp.dylib). Bitmaps are white, premultiplied RGBA8,
 * top row first. All calls are thread-safe; check {@link #available()} first (false off macOS/arm64 or on load failure).
 */
public final class MacNative {
	private static final boolean AVAILABLE = load();

	private MacNative() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	/** {advance width, ascent, descent, leading} in pixels for the system font at pixelSize (weight 100–900). */
	public static native float[] measureText(String text, float pixelSize, int weight, boolean mono);

	/** One line of white glyphs; out = {width, height, pen x, baseline row from top}. null when nothing is inked. */
	public static native byte[] renderText(String text, float pixelSize, int weight, boolean mono, int[] out);

	/** SF Symbol centered in a box×box bitmap (shrunk to fit); null when the symbol doesn't exist. */
	public static native byte[] renderSymbol(String name, int box, float pointSize, int weight);

	/**
	 * Continuous-corner rect w×h at (margin, margin) in a (w+2·margin)×(h+2·margin) bitmap; inside stroke when
	 * stroke > 0, Gaussian-blurred when sigma > 0. All in pixels.
	 */
	public static native byte[] renderRoundRect(int w, int h, float radius, float stroke, float sigma, int margin);

	/** NSColor.controlAccentColor in sRGB, opaque ARGB. */
	public static native int accentColor();

	public static native boolean darkAppearance();

	/** Main display pixels per point. */
	public static native double backingScale();

	private static boolean load() {
		String os = System.getProperty("os.name", "").toLowerCase();
		String arch = System.getProperty("os.arch", "");
		if (!os.contains("mac") || !(arch.equals("aarch64") || arch.equals("arm64"))) return false;
		try (InputStream in = MacNative.class.getResourceAsStream("/natives/libhoneycrisp.dylib")) {
			if (in == null) throw new IOException("natives/libhoneycrisp.dylib missing from the jar");
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[65536];
			for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
			byte[] lib = buf.toByteArray();
			StringBuilder hash = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-1").digest(lib)) hash.append(String.format("%02x", b));
			Path dir = Honeycrisp.platform() != null ? Honeycrisp.platform().configDir().resolve("natives")
				: Paths.get(System.getProperty("java.io.tmpdir"), "honeycrisp-natives");
			Files.createDirectories(dir);
			Path file = dir.resolve("libhoneycrisp-" + hash.substring(0, 16) + ".dylib");
			if (!Files.exists(file)) {
				Path tmp = Files.createTempFile(dir, "libhoneycrisp", ".tmp");
				Files.write(tmp, lib);
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			System.load(new File(file.toString()).getAbsolutePath());
			return true;
		} catch (Throwable t) {
			System.err.println("[Honeycrisp Client] libhoneycrisp unavailable: " + t);
			return false;
		}
	}
}
