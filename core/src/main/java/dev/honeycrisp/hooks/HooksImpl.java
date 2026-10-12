package dev.honeycrisp.hooks;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.Config;
import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.ModuleRegistry;
import dev.honeycrisp.module.Setting;
import dev.honeycrisp.modules.ChatModule;
import dev.honeycrisp.modules.ClientModule;
import dev.honeycrisp.modules.CpsModule;
import dev.honeycrisp.modules.CrosshairModule;
import dev.honeycrisp.modules.FreelookModule;
import dev.honeycrisp.modules.FullbrightModule;
import dev.honeycrisp.modules.HitColorModule;
import dev.honeycrisp.modules.ItemPhysicsModule;
import dev.honeycrisp.modules.OldAnimationsModule;
import dev.honeycrisp.modules.ScoreboardModule;
import dev.honeycrisp.modules.TimeChangerModule;
import dev.honeycrisp.modules.ToggleSneakModule;
import dev.honeycrisp.modules.ToggleSprintModule;
import dev.honeycrisp.modules.ZoomModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.ui.Screen;

import java.util.HashSet;
import java.util.Set;

/** Routes every adapter hook to the modules. */
public final class HooksImpl implements Hooks {
	private final Platform platform;
	private final ModuleRegistry modules;
	private final Config config;
	private final Set<String> failed = new HashSet<String>();
	private final ClientModule client;
	private final CpsModule cps;
	private final ZoomModule zoom;
	private final FreelookModule freelook;
	private final ToggleSprintModule sprint;
	private final ToggleSneakModule sneak;
	private final OldAnimationsModule oldAnimations;
	private final HitColorModule hitColor;
	private final ItemPhysicsModule itemPhysics;
	private final CrosshairModule crosshair;
	private final FullbrightModule fullbright;
	private final TimeChangerModule timeChanger;
	private final ChatModule chat;
	private final ScoreboardModule scoreboard;

	/** config may be null (tests). */
	public HooksImpl(Platform platform, ModuleRegistry modules, Config config) {
		this.platform = platform;
		this.modules = modules;
		this.config = config;
		client = modules.get(ClientModule.class);
		cps = modules.get(CpsModule.class);
		zoom = modules.get(ZoomModule.class);
		freelook = modules.get(FreelookModule.class);
		sprint = modules.get(ToggleSprintModule.class);
		sneak = modules.get(ToggleSneakModule.class);
		oldAnimations = modules.get(OldAnimationsModule.class);
		hitColor = modules.get(HitColorModule.class);
		itemPhysics = modules.get(ItemPhysicsModule.class);
		crosshair = modules.get(CrosshairModule.class);
		fullbright = modules.get(FullbrightModule.class);
		timeChanger = modules.get(TimeChangerModule.class);
		chat = modules.get(ChatModule.class);
		scoreboard = modules.get(ScoreboardModule.class);
	}

	@Override
	public void onClientTick() {
		GameInfo game = platform.game();
		for (Module m : modules.all()) {
			if (!m.enabled()) continue;
			try {
				m.onTick(game);
			} catch (RuntimeException e) {
				fail(m, e);
			}
		}
		if (config != null) config.tick();
	}

	@Override
	public void onRenderHud(float partialTick) {
		if (modules.suppressHud) return;
		GameInfo game = platform.game();
		cps.setNow(platform.timeMillis());
		float w = platform.scaledWidth(), h = platform.scaledHeight();
		for (HudModule m : modules.hud()) {
			if (!m.enabled()) continue;
			try {
				m.draw(platform.renderer(), game, partialTick, false, w, h);
			} catch (RuntimeException e) {
				fail(m, e);
			}
		}
	}

	private void fail(Module m, RuntimeException e) {
		if (failed.add(m.id)) {
			System.err.println("[Honeycrisp Client] module " + m.id + " failed (logged once):");
			e.printStackTrace();
		}
	}

	@Override
	public boolean onKey(int glfwKey, int action) {
		return onKey(glfwKey, action, false);
	}

	@Override
	public boolean onKey(int glfwKey, int action, boolean screenOpen) {
		if (!screenOpen && action == Keys.PRESS && glfwKey != Keys.UNKNOWN && glfwKey == client.menuKey.key()) {
			openModMenu();
			return true;
		}
		// Presses only reach enabled modules outside screens; releases always, so nothing stays stuck.
		boolean release = action == Keys.RELEASE;
		if (!release && screenOpen) return false;
		for (Module m : modules.all()) {
			if (!release && !m.enabled()) continue;
			for (Setting<?> s : m.settings()) if (s instanceof KeybindSetting) ((KeybindSetting) s).handle(glfwKey, action);
		}
		return false;
	}

	@Override
	public void onMouseButton(int button, int action) {
		onMouseButton(button, action, false);
	}

	@Override
	public void onMouseButton(int button, int action, boolean screenOpen) {
		cps.mouse(button, action, screenOpen, platform.timeMillis());
	}

	@Override
	public void onAttack() {
		cps.attack(platform.timeMillis());
	}

	@Override
	public boolean onScroll(double amount) {
		return zoom.scroll(amount);
	}

	@Override
	public double fovMultiplier(float partialTick) {
		return zoom.fovMultiplier(platform.timeMillis());
	}

	@Override
	public double mouseSensitivityMultiplier() {
		return zoom.sensitivityMultiplier();
	}

	@Override
	public boolean freelookActive() {
		GameInfo g = platform.game();
		return freelook.update(g.yaw(), g.pitch());
	}

	@Override
	public boolean consumeMouseLook(double dx, double dy) {
		return freelook.look(dx, dy);
	}

	@Override
	public float freelookYaw() {
		return freelook.yaw();
	}

	@Override
	public float freelookPitch() {
		return freelook.pitch();
	}

	@Override
	public boolean freelookThirdPerson() {
		return freelook.active() && freelook.thirdPerson.on();
	}

	@Override
	public boolean oldAnimation(OldAnimation which) {
		return oldAnimations.on(which);
	}

	@Override
	public int hitColor() {
		return hitColor.argb();
	}

	@Override
	public boolean itemPhysics() {
		return itemPhysics.enabled();
	}

	@Override
	public boolean sprintHeld() {
		return sprint.held();
	}

	@Override
	public boolean sneakHeld() {
		return sneak.held();
	}

	@Override
	public Crosshair crosshair() {
		return crosshair.enabled() ? crosshair : null;
	}

	@Override
	public double gammaOverride() {
		return fullbright.gammaOverride();
	}

	@Override
	public long fixedDayTime() {
		return timeChanger.fixedTime();
	}

	@Override
	public float chatBackgroundOpacity() {
		return chat.opacity();
	}

	@Override
	public boolean hideScoreboardNumbers() {
		return scoreboard.hideNumbers();
	}

	@Override
	public boolean replaceMainMenu() {
		// Without adapter MenuActions the custom menu has no Singleplayer/Multiplayer/Quit: keep vanilla's.
		return client.customMainMenu.on() && Honeycrisp.screens().mainMenu != null
				&& dev.honeycrisp.ui.screens.MenuActions.get() != null;
	}

	@Override
	public void openMainMenu() {
		open(Honeycrisp.screens().mainMenu);
	}

	@Override
	public void openModMenu() {
		open(Honeycrisp.screens().modMenu);
	}

	private void open(Honeycrisp.ScreenFactory factory) {
		Screen s = factory != null ? factory.create() : null;
		if (s != null) platform.openScreen(s);
	}
}
