package dev.honeycrisp.platform;

import java.nio.ByteBuffer;

/**
 * Minimal immediate-mode 2D renderer each version adapter implements. Coordinates are GUI-scaled pixels (top-left
 * origin, floats). Colors are non-premultiplied ARGB; textures are premultiplied RGBA8, drawn with
 * blend (ONE, ONE_MINUS_SRC_ALPHA). Everything else (text, icons, rounded shapes) is built from these in core.
 */
public interface Renderer {
	/** Uploads premultiplied RGBA8 pixels (row-major, top row first, width*height*4 bytes). */
	Texture createTexture(int width, int height, ByteBuffer premultipliedRgba);

	void deleteTexture(Texture texture);

	void fill(float x, float y, float w, float h, int argb);

	/** Draws a texture region; the tint multiplies color and alpha (0xFFFFFFFF = unchanged). */
	void texture(Texture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argbTint);

	/** Nested clip rectangles intersect. */
	void pushClip(float x, float y, float w, float h);

	void popClip();

	/** Optional backdrop blur of what is already drawn under the rectangle. Returns false when unsupported. */
	boolean blur(float x, float y, float w, float h, float radius);
}
