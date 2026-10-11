package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;

/** Clicks per second; also owns mouse-button state for Keystrokes. */
public final class CpsModule extends TextHudModule {
	public final BooleanSetting showRight = setting(new BooleanSetting("right", "Show Right Click", true));
	public final ClickCounter left = new ClickCounter(), right = new ClickCounter();
	private boolean leftDown, rightDown, sawLeftButton;
	private long now;

	public CpsModule() {
		super("cps", "CPS", "Clicks per second", Category.HUD, "cursorarrow.click", Anchor.TOP_LEFT, 4, 22);
		enabledByDefault();
	}

	/** Mouse button events; clicks inside screens don't count but releases always update the state. */
	public void mouse(int button, int action, boolean screenOpen, long now) {
		boolean press = action == Keys.PRESS;
		if (button == Keys.MOUSE_LEFT) {
			sawLeftButton = true;
			if (press && !screenOpen) left.click(now);
			leftDown = press && !screenOpen;
		} else if (button == Keys.MOUSE_RIGHT) {
			if (press && !screenOpen) right.click(now);
			rightDown = press && !screenOpen;
		}
	}

	/** Fallback for adapters that only report attacks, not raw mouse buttons. */
	public void attack(long now) {
		if (!sawLeftButton) left.click(now);
	}

	public boolean leftDown() {
		return leftDown;
	}

	public boolean rightDown() {
		return rightDown;
	}

	/** Timestamp for count(); set by HooksImpl each frame. */
	public void setNow(long now) {
		this.now = now;
	}

	public int leftCps() {
		return left.count(now);
	}

	public int rightCps() {
		return right.count(now);
	}

	@Override
	protected String text(GameInfo game) {
		return showRight.on() ? leftCps() + " | " + rightCps() + " CPS" : leftCps() + " CPS";
	}

	@Override
	protected String previewText() {
		return showRight.on() ? "12 | 4 CPS" : "12 CPS";
	}
}
