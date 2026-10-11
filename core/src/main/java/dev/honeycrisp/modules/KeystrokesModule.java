package dev.honeycrisp.modules;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.ColorSetting;
import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;

/** WASD, mouse buttons (with CPS) and space, each fading into a pressed state. */
public final class KeystrokesModule extends HudModule {
	private static final float K = 22, G = 2, W = 3 * K + 2 * G;
	private static final int W_ = 0, A_ = 1, S_ = 2, D_ = 3, LMB = 4, RMB = 5, SPACE = 6;
	private static final String[] LABELS = {"W", "A", "S", "D", "LMB", "RMB", ""};

	public final BooleanSetting showMouse = setting(new BooleanSetting("mouse", "Show Mouse Buttons", true));
	public final BooleanSetting showCps = setting(new BooleanSetting("cps", "Show CPS", true));
	public final BooleanSetting showSpace = setting(new BooleanSetting("space", "Show Space Bar", true));
	public final ColorSetting keyColor = setting(new ColorSetting("keyColor", "Key Background", 0x66000000));
	public final ColorSetting pressedColor = setting(new ColorSetting("pressedColor", "Pressed Background", 0xCCFFFFFF));
	public final ColorSetting pressedTextColor = setting(new ColorSetting("pressedText", "Pressed Text", 0xFF000000));

	private final CpsModule cps;
	private final float[] anim = new float[7];
	private long lastFrame;
	private float mouseH;

	public KeystrokesModule(CpsModule cps) {
		super("keystrokes", "Keystrokes", "Shows movement keys and clicks", Category.HUD, "keyboard", Anchor.TOP_RIGHT, -4, 22);
		this.cps = cps;
	}

	@Override
	protected void measure(GameInfo game) {
		mouseH = showCps.on() ? Math.max(K, 2 * lineHeight() + 4) : K;
		float h = 2 * (K + G) - G;
		if (showMouse.on()) h += G + mouseH;
		if (showSpace.on()) h += G + K * 0.6f;
		size(W, h);
	}

	@Override
	protected void drawBackground() {
		// Each key draws its own background.
	}

	@Override
	protected void render(Renderer renderer, GameInfo game, float partialTick) {
		Platform p = Honeycrisp.platform();
		long now = p != null ? p.timeMillis() : System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		boolean[] down = new boolean[7];
		if (!preview && p != null) {
			down[W_] = p.isKeyDown(Keys.W);
			down[A_] = p.isKeyDown(Keys.A);
			down[S_] = p.isKeyDown(Keys.S);
			down[D_] = p.isKeyDown(Keys.D);
			down[SPACE] = p.isKeyDown(Keys.SPACE);
			down[LMB] = cps.leftDown();
			down[RMB] = cps.rightDown();
		}
		float k = 1 - (float) Math.exp(-dt * 18);
		for (int i = 0; i < 7; i++) anim[i] += ((down[i] ? 1 : 0) - anim[i]) * k;

		key(W_, K + G, 0, K, K, null);
		key(A_, 0, K + G, K, K, null);
		key(S_, K + G, K + G, K, K, null);
		key(D_, 2 * (K + G), K + G, K, K, null);
		float y = 2 * (K + G);
		if (showMouse.on()) {
			float mw = (W - G) / 2;
			key(LMB, 0, y, mw, mouseH, showCps.on() ? (preview ? 12 : cps.leftCps()) + " CPS" : null);
			key(RMB, mw + G, y, mw, mouseH, showCps.on() ? (preview ? 4 : cps.rightCps()) + " CPS" : null);
			y += mouseH + G;
		}
		if (showSpace.on()) {
			float sh = K * 0.6f;
			key(SPACE, 0, y, W, sh, null);
			fill(W / 2 - 10, y + sh / 2 - 0.5f, 20, 1, lerp(color(), pressedTextColor.argb(), anim[SPACE]));
		}
	}

	private void key(int i, float x, float y, float w, float h, String sub) {
		float t = anim[i];
		if (background.on()) roundRect(x, y, w, h, 3, lerp(keyColor.argb(), pressedColor.argb(), t));
		int tc = lerp(color(), pressedTextColor.argb(), t);
		float lh = lineHeight();
		if (sub == null) {
			if (!LABELS[i].isEmpty()) textCentered(LABELS[i], x + w / 2, y + (h - lh) / 2, tc);
		} else {
			textCentered(LABELS[i], x + w / 2, y + h / 2 - lh - 1, tc);
			textCentered(sub, x + w / 2, y + h / 2 + 1, (tc & 0xFFFFFF) | ((tc >>> 24) * 3 / 4 << 24));
		}
	}

	static int lerp(int a, int b, float t) {
		if (t <= 0.001f) return a;
		if (t >= 0.999f) return b;
		int r = 0;
		for (int s = 0; s < 32; s += 8) {
			int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << s;
		}
		return r;
	}
}
