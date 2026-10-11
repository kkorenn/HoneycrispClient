package dev.honeycrisp.v1_7_10;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Crosshair;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.platform.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Translates Forge/FML events (and the coremod's call sites, the static methods at the bottom) into core Hooks.
 * Tick and input events are on FML's bus, render/GUI events on Forge's.
 */
@Mod(modid = "honeycrisp", name = "Honeycrisp Client", useMetadata = true, acceptableRemoteVersions = "*",
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
			Honeycrisp.init(new Platform1710());
		} catch (Throwable t) {
			System.err.println("[Honeycrisp Client] init failed, running vanilla: " + t);
			return;
		}
		FMLCommonHandler.instance().bus().register(this);
		MinecraftForge.EVENT_BUS.register(this);
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
		if (mc.thePlayer == null) {
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
			if (time >= 0 && mc.theWorld != null) {
				timeWorld = mc.theWorld.getWorldInfo();
				savedTime = timeWorld.getWorldTime();
				timeWorld.setWorldTime(Math.floorDiv(savedTime, 24000L) * 24000L + time);
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

	/** Fired for every keyboard event Minecraft polls (in-world, and in screens that allow user input). */
	@SubscribeEvent
	public void onKeyInput(InputEvent.KeyInputEvent event) {
		Hooks h = hooks();
		int key = KeyMap.toGlfw(Keyboard.getEventKey());
		if (h == null || key == Keys.UNKNOWN) {
			return;
		}
		int action = !Keyboard.getEventKeyState() ? Keys.RELEASE : Keyboard.isRepeatEvent() ? Keys.REPEAT : Keys.PRESS;
		h.onKey(key, action, Minecraft.getMinecraft().currentScreen != null);
	}

	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		Hooks h = hooks();
		if (h == null) {
			return;
		}
		boolean screen = Minecraft.getMinecraft().currentScreen != null;
		if (event.dwheel != 0 && !screen && h.onScroll(Math.signum(event.dwheel))) {
			event.setCanceled(true);
		}
		if (event.button < 0) {
			return;
		}
		h.onMouseButton(event.button, event.buttonstate ? Keys.PRESS : Keys.RELEASE, screen);
		if (event.button == 0 && event.buttonstate && !screen) {
			h.onAttack();
		}
	}

	@SubscribeEvent
	public void onHud(RenderGameOverlayEvent.Post event) {
		Hooks h = hooks();
		if (h != null && event.type == RenderGameOverlayEvent.ElementType.ALL) {
			h.onRenderHud(event.partialTicks);
		}
	}

	@SubscribeEvent
	public void onCrosshair(RenderGameOverlayEvent.Pre event) {
		Hooks h = hooks();
		Crosshair c = h == null || event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS ? null : h.crosshair();
		if (c != null) {
			event.setCanceled(true);
			ScaledResolution r = event.resolution;
			c.draw(Honeycrisp.platform().renderer(), r.getScaledWidth() / 2F, r.getScaledHeight() / 2F);
		}
	}

	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent event) {
		Hooks h = hooks();
		if (h != null && event.gui instanceof GuiMainMenu && h.replaceMainMenu()) {
			Platform1710.opening = event;
			try {
				h.openMainMenu();
			} finally {
				Platform1710.opening = null;
			}
		}
	}

	// Called from code patched by dev.honeycrisp.v1_7_10.coremod.Transformer.

	/** EntityRenderer.getFOVModifier: every return (1.7.10 has no FOVModifier event). */
	public static float fov(float fov, float partialTicks) {
		Hooks h = hooks();
		return h == null ? fov : (float)(fov * h.fovMultiplier(partialTicks));
	}

	/** EntityRenderer.updateCameraAndRender: player.setAngles(dx, dy). */
	public static void setAngles(Entity entity, float dx, float dy) {
		Hooks h = hooks();
		if (h == null || !h.consumeMouseLook(dx, dy)) {
			entity.setAngles(dx, dy);
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

	/** RendererLivingEntity.doRender: the hurt overlay's glColor4f(brightness, 0, 0, 0.4). */
	public static void hurtColor(float r, float g, float b, float a) {
		Hooks h = hooks();
		int c = h == null ? 0 : h.hitColor();
		if (c == 0) {
			GL11.glColor4f(r, g, b, a);
		} else {
			GL11.glColor4f((c >> 16 & 255) / 255F * r, (c >> 8 & 255) / 255F * r, (c & 255) / 255F * r, (c >>> 24) / 255F);
		}
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

	/** GuiIngame.func_96136_a (sidebar): the "name: §cN" (width) and "§cN" (drawn) strings lose their red score. */
	public static String scoreText(StringBuilder text) {
		String s = text.toString();
		Hooks h = hooks();
		int i = h != null && h.hideScoreboardNumbers() ? s.lastIndexOf("§c") : -1;
		return i < 0 ? s : s.substring(0, i);
	}
}
