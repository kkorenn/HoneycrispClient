package dev.honeycrisp.platform;

/** A GPU texture created by {@link Renderer#createTexture}. Adapters subclass or wrap their own type. */
public interface Texture {
	int width();

	int height();
}
