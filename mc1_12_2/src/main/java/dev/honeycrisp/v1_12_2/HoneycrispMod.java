package dev.honeycrisp.v1_12_2;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Crosshair;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.hooks.OldAnimation;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.ui.screens.MenuActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.nio.FloatBuffer;

/** Translates Forge events (and the coremod's call sites, the static methods at the bottom) into core Hooks. */
@Mod(modid = "honeycrisp", name = "Honeycrisp Client", useMetadata = true, clientSideOnly = true, acceptableRemoteVersions = "*",
	dependencies = "required-after:graft")
public final class HoneycrispMod {
	private float savedSensitivity = -1, savedGamma = -1;
	private int savedPerspective = -1;
	private WorldInfo timeWorld;
	private long savedTime;

	private static Hooks hooks() {
		return Honeycrisp.hooks();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		try {
			Honeycrisp.init(new Platform1122());
		} catch (Throwable t) {
			System.err.println("[Honeycrisp Client] init failed, running vanilla: " + t);
			return;
		}
		MinecraftForge.EVENT_BUS.register(this);
		final Minecraft mc = Minecraft.getMinecraft();
		MenuActions.set(new MenuActions() { // same screens GuiMainMenu opens; the Honeycrisp menu is the parent
			@Override
			public void singleplayer() {
				mc.displayGuiScreen(new GuiWorldSelection(mc.currentScreen));
			}

			@Override
			public void multiplayer() {
				mc.displayGuiScreen(new GuiMultiplayer(mc.currentScreen));
			}

			@Override
			public void realms() {
				new net.minecraft.realms.RealmsBridge().switchToRealms(mc.currentScreen);
			}

			@Override
			public void options() {
				mc.displayGuiScreen(new GuiOptions(mc.currentScreen, mc.gameSettings));
			}

			@Override
			public void language() {
				mc.displayGuiScreen(new GuiLanguage(mc.currentScreen, mc.gameSettings, mc.getLanguageManager()));
			}

			@Override
			public void quit() {
				mc.shutdown();
			}
		});
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		Hooks h = hooks();
		if (h == null) {
			return;
		}
		if (event.phase == TickEvent.Phase.END) {
			h.onClientTick();
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.player == null) {
			return;
		}
		hold(mc.gameSettings.keyBindSprint, h.sprintHeld(), mc);
		hold(mc.gameSettings.keyBindSneak, h.sneakHeld(), mc);
		if (h.freelookThirdPerson()) {
			if (savedPerspective < 0) {
				savedPerspective = mc.gameSettings.thirdPersonView;
				if (savedPerspective == 0) {
					mc.gameSettings.thirdPersonView = 1;
				}
			}
		} else if (savedPerspective >= 0) {
			mc.gameSettings.thirdPersonView = savedPerspective;
			savedPerspective = -1;
		}
	}

	/** Toggle sprint/sneak: the binding reads as held while the core says so, else as the physical key. */
	private static void hold(KeyBinding binding, boolean toggled, Minecraft mc) {
		int code = binding.getKeyCode();
		boolean physical = mc.currentScreen == null
			&& (code < 0 ? Mouse.isButtonDown(code + 100) : code > 0 && Keyboard.isKeyDown(code));
		KeyBinding.setKeyBindState(code, toggled || physical);
	}

	/** Zoom sensitivity, fullbright and time changer only hold while the frame renders: ticks, saves and packets see vanilla values. */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		Hooks h = hooks();
		Minecraft mc = Minecraft.getMinecraft();
		if (h == null) {
			return;
		}
		if (event.phase == TickEvent.Phase.START) {
			double m = h.mouseSensitivityMultiplier();
			if (m != 1) { // vanilla: delta * (s * 0.6 + 0.2)^3 * 8, so scale the base by cbrt(m)
				savedSensitivity = mc.gameSettings.mouseSensitivity;
				float f = (float)((savedSensitivity * 0.6F + 0.2F) * Math.cbrt(m));
				mc.gameSettings.mouseSensitivity = (f - 0.2F) / 0.6F;
			}
			double gamma = h.gammaOverride();
			if (gamma >= 0) {
				savedGamma = mc.gameSettings.gammaSetting;
				mc.gameSettings.gammaSetting = (float)gamma;
			}
			long time = h.fixedDayTime();
			if (time >= 0 && mc.world != null) {
				timeWorld = mc.world.getWorldInfo();
				savedTime = timeWorld.getWorldTime();
				timeWorld.setWorldTime(Math.floorDiv(savedTime, 24000L) * 24000L + time);
			}
			if (h.freelookActive() && mc.renderGlobal != null) {
				mc.renderGlobal.setDisplayListEntitiesDirty(); // the camera turns while the player's rotation does not
			}
			return;
		}
		if (savedSensitivity >= 0) {
			mc.gameSettings.mouseSensitivity = savedSensitivity;
			savedSensitivity = -1;
		}
		if (savedGamma >= 0) {
			mc.gameSettings.gammaSetting = savedGamma;
			savedGamma = -1;
		}
		if (timeWorld != null) {
			timeWorld.setWorldTime(savedTime);
			timeWorld = null;
		}
	}

	@SubscribeEvent
	public void onKeyInput(InputEvent.KeyInputEvent event) {
		key();
	}

	@SubscribeEvent
	public void onScreenKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
		if (key() && event.isCancelable()) {
			event.setCanceled(true);
		}
	}

	private static boolean key() {
		Hooks h = hooks();
		int key = KeyMap.toGlfw(Keyboard.getEventKey());
		if (h == null || key == Keys.UNKNOWN) {
			return false;
		}
		int action = !Keyboard.getEventKeyState() ? Keys.RELEASE : Keyboard.isRepeatEvent() ? Keys.REPEAT : Keys.PRESS;
		return h.onKey(key, action, Minecraft.getMinecraft().currentScreen != null);
	}

	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		Hooks h = hooks();
		if (h == null) {
			return;
		}
		boolean screen = Minecraft.getMinecraft().currentScreen != null;
		if (event.getDwheel() != 0 && !screen && h.onScroll(Math.signum(event.getDwheel()))) {
			event.setCanceled(true);
		}
		if (event.getButton() < 0) {
			return;
		}
		h.onMouseButton(event.getButton(), event.isButtonstate() ? Keys.PRESS : Keys.RELEASE, screen);
		if (event.getButton() == 0 && event.isButtonstate() && !screen) {
			h.onAttack();
		}
	}

	@SubscribeEvent
	public void onScreenMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
		Hooks h = hooks();
		if (h != null && Mouse.getEventButton() >= 0) {
			h.onMouseButton(Mouse.getEventButton(), Mouse.getEventButtonState() ? Keys.PRESS : Keys.RELEASE, true);
		}
	}

	@SubscribeEvent
	public void onHud(RenderGameOverlayEvent.Post event) {
		Hooks h = hooks();
		if (h != null && event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
			h.onRenderHud(event.getPartialTicks());
		}
	}

	@SubscribeEvent
	public void onCrosshair(RenderGameOverlayEvent.Pre event) {
		Hooks h = hooks();
		Crosshair c = h == null || event.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS ? null : h.crosshair();
		if (c != null) {
			event.setCanceled(true);
			ScaledResolution r = event.getResolution();
			c.draw(Honeycrisp.platform().renderer(), r.getScaledWidth() / 2F, r.getScaledHeight() / 2F);
		}
	}

	@SubscribeEvent
	public void onFov(EntityViewRenderEvent.FOVModifier event) {
		Hooks h = hooks();
		if (h != null) {
			event.setFOV((float)(event.getFOV() * h.fovMultiplier((float)event.getRenderPartialTicks())));
		}
	}

	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent event) {
		Hooks h = hooks();
		if (h != null && event.getGui() instanceof GuiMainMenu && h.replaceMainMenu()) {
			Platform1122.opening = event;
			try {
				h.openMainMenu();
			} finally {
				Platform1122.opening = null;
			}
		}
	}

	// Called from code patched by dev.honeycrisp.v1_12_2.coremod.Transformer.

	/** EntityRenderer.updateCameraAndRender: player.turn(dx, dy). */
	public static void setAngles(Entity entity, float dx, float dy) {
		Hooks h = hooks();
		if (h == null || !h.consumeMouseLook(dx, dy)) {
			entity.turn(dx, dy);
		}
	}

	/** EntityRenderer.orientCamera: every read of the view entity's (prev)rotationYaw. */
	public static float cameraYaw(float yaw) {
		Hooks h = hooks();
		return h != null && h.freelookActive() ? h.freelookYaw() : yaw;
	}

	public static float cameraPitch(float pitch) {
		Hooks h = hooks();
		return h != null && h.freelookActive() ? h.freelookPitch() : pitch;
	}

	/** RenderLivingBase.setBrightness: the hurt tint's put(1), put(0), put(0), put(0.3) (channel 0-3 = r, g, b, a). */
	public static FloatBuffer hurtTint(FloatBuffer buffer, float vanilla, int channel) {
		Hooks h = hooks();
		int c = h == null ? 0 : h.hitColor();
		if (c == 0) {
			return buffer.put(vanilla);
		}
		int shift = channel == 3 ? 24 : 16 - channel * 8;
		return buffer.put((c >>> shift & 255) / 255F);
	}

	/** LayerArmorBase.shouldCombineTextures: true lets the armor layer take the hurt tint (1.7 armor damage flash). */
	public static boolean armorFlash(boolean vanilla) {
		Hooks h = hooks();
		return vanilla || h != null && h.oldAnimation(OldAnimation.ARMOR_DAMAGE_FLASH);
	}

	/** GuiNewChat.drawChat: the line background color, alpha = fade * (chat opacity * 0.9 + 0.1) * 255 / 2. */
	public static int chatBackground(int color) {
		Hooks h = hooks();
		float o = h == null ? -1 : h.chatBackgroundOpacity();
		if (o < 0) {
			return color;
		}
		float opacity = Minecraft.getMinecraft().gameSettings.chatOpacity * 0.9F + 0.1F;
		int a = Math.min(255, (int)((color >>> 24) * 2 / opacity * o));
		return a << 24 | color & 0xFFFFFF;
	}

	/** GuiIngame.renderScoreboard: the "name: §cN" (width) and "§cN" (drawn) strings lose their red score. */
	public static String scoreText(StringBuilder text) {
		String s = text.toString();
		Hooks h = hooks();
		int i = h != null && h.hideScoreboardNumbers() ? s.lastIndexOf("§c") : -1;
		return i < 0 ? s : s.substring(0, i);
	}
}
