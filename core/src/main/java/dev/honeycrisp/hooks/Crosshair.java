package dev.honeycrisp.hooks;

/** Custom crosshair (Crosshair module). Adapters cancel the vanilla crosshair and call {@link #draw} instead. */
public interface Crosshair {
	/** Draws centered at GUI-scaled (cx, cy). */
	void draw(dev.honeycrisp.platform.Renderer renderer, float cx, float cy);
}
