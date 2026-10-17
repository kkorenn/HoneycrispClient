package dev.honeycrisp.modules;

import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.platform.Keys;

/** While the key is held the camera turns on its own; the player keeps their direction. */
public final class FreelookModule extends Module {
	/** Minecraft's Entity.turn factor: degrees per mouse unit. */
	public static final double DEGREES_PER_UNIT = 0.15;
	public final KeybindSetting key = setting(new KeybindSetting("key", "Freelook Key", Keys.LEFT_ALT, KeybindSetting.Mode.HOLD));
	public final BooleanSetting thirdPerson = setting(new BooleanSetting("thirdPerson", "Third Person", true));
	public final BooleanSetting invertPitch = setting(new BooleanSetting("invert", "Invert Pitch", false));

	private boolean active;
	private float yaw, pitch;

	public FreelookModule() {
		super("freelook", "Freelook", "Look around without turning", Category.CAMERA, "eye");
	}

	/** Polled every frame with the player's rotation; captures it as the camera start when freelook begins. */
	public boolean update(float playerYaw, float playerPitch) {
		boolean want = enabled() && key.active();
		if (want && !active) {
			yaw = playerYaw;
			pitch = playerPitch;
		}
		active = want;
		return active;
	}

	public boolean active() {
		return active;
	}

	/** dx/dy as the game would pass them to Entity.turn (already sensitivity-scaled). */
	public boolean look(double dx, double dy) {
		if (!active) return false;
		yaw += (float) (dx * DEGREES_PER_UNIT);
		pitch = clampPitch(pitch + (float) ((invertPitch.on() ? -dy : dy) * DEGREES_PER_UNIT));
		return true;
	}

	public static float clampPitch(float p) {
		return Math.max(-90f, Math.min(90f, p));
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	@Override
	protected void onDisable() {
		key.release();
		active = false;
	}
}
