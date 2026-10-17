package dev.honeycrisp.v26_3;

import com.mojang.blaze3d.platform.InputConstants;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.Screen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Platform + GameInfo for 26.3. 26.x runs on SDL3: keys are SDL scancodes, translated to/from GLFW codes here. */
public final class McPlatform implements Platform, GameInfo {
	public static final McPlatform INSTANCE = new McPlatform();
	private static final int[] SDL_TO_GLFW = new int[512];
	private static final int[] GLFW_TO_SDL = new int[512];

	static {
		java.util.Arrays.fill(SDL_TO_GLFW, -1);
		java.util.Arrays.fill(GLFW_TO_SDL, -1);
		for (int i = 0; i < 26; i++) map(4 + i, 65 + i);           // A–Z
		for (int i = 0; i < 9; i++) map(30 + i, 49 + i);           // 1–9
		map(39, 48);                                               // 0
		for (int i = 0; i < 12; i++) map(58 + i, 290 + i);         // F1–F12
		for (int i = 0; i < 12; i++) map(104 + i, 302 + i);        // F13–F24
		for (int i = 0; i < 9; i++) map(89 + i, 321 + i);          // keypad 1–9
		int[] pairs = {
			40, 257, 41, 256, 42, 259, 43, 258, 44, 32, 45, 45, 46, 61, 47, 91, 48, 93, 49, 92, 51, 59, 52, 39, 53, 96,
			54, 44, 55, 46, 56, 47, 57, 280, 70, 283, 71, 281, 72, 284, 73, 260, 74, 268, 75, 266, 76, 261, 77, 269,
			78, 267, 79, 262, 80, 263, 81, 264, 82, 265, 83, 282, 84, 331, 85, 332, 86, 333, 87, 334, 88, 335, 98, 320,
			99, 330, 101, 348, 103, 336, 224, 341, 225, 340, 226, 342, 227, 343, 228, 345, 229, 344, 230, 346, 231, 347
		};
		for (int i = 0; i < pairs.length; i += 2) map(pairs[i], pairs[i + 1]);
	}

	private static void map(int sdl, int glfw) {
		SDL_TO_GLFW[sdl] = glfw;
		GLFW_TO_SDL[glfw] = sdl;
	}

	public static int toGlfwKey(int sdlScancode) {
		return sdlScancode >= 0 && sdlScancode < 512 ? SDL_TO_GLFW[sdlScancode] : -1;
	}

	public static int toSdlKey(int glfwKey) {
		return glfwKey >= 0 && glfwKey < 512 ? GLFW_TO_SDL[glfwKey] : -1;
	}

	/** SDL action (1 press, 0 release, -1 repeat) → GLFW. */
	public static int toGlfwAction(int sdlAction) {
		return sdlAction == -1 ? 2 : sdlAction;
	}

	/** SDL mouse button (1 left, 2 middle, 3 right, 4+ extra) → GLFW (0 left, 1 right, 2 middle, 3+ extra). */
	public static int toGlfwButton(int sdlButton) {
		return sdlButton == 1 ? 0 : sdlButton == 3 ? 1 : sdlButton == 2 ? 2 : sdlButton - 1;
	}

	/** SDL keymod bits (InputConstants.MOD_*) → GLFW modifier bits. */
	public static int toGlfwMods(int sdl) {
		return ((sdl & 3) != 0 ? 1 : 0) | ((sdl & 192) != 0 ? 2 : 0) | ((sdl & 768) != 0 ? 4 : 0) | ((sdl & 3072) != 0 ? 8 : 0)
			| ((sdl & 8192) != 0 ? 16 : 0) | ((sdl & 4096) != 0 ? 32 : 0);
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// Platform

	@Override
	public String minecraftVersion() {
		return "26.3";
	}

	@Override
	public Renderer renderer() {
		return McRenderer.INSTANCE;
	}

	@Override
	public GameInfo game() {
		return this;
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("honeycrisp");
	}

	@Override
	public int scaledWidth() {
		return mc().getWindow().getGuiScaledWidth();
	}

	@Override
	public int scaledHeight() {
		return mc().getWindow().getGuiScaledHeight();
	}

	@Override
	public double guiScale() {
		return mc().getWindow().getGuiScale();
	}

	@Override
	public double backingScale() {
		return mc().getWindow().getPixelDensity();
	}

	@Override
	public void openScreen(Screen screen) {
		mc().gui.setScreen(new McScreen(screen));
	}

	@Override
	public void closeScreen() {
		mc().gui.setScreen(null);
	}

	@Override
	public boolean isKeyDown(int glfwKey) {
		int sdl = toSdlKey(glfwKey);
		return sdl > 0 && InputConstants.isKeyDown(sdl);
	}

	@Override
	public String keyName(int glfwKey) {
		int sdl = toSdlKey(glfwKey);
		return sdl > 0 ? InputConstants.Type.KEYBOARD.getOrCreate(sdl).getDisplayName().getString() : "";
	}

	@Override
	public long timeMillis() {
		return Util.getMillis();
	}

	// GameInfo

	@Override
	public boolean inWorld() {
		return mc().player != null && mc().level != null;
	}

	@Override
	public int fps() {
		return mc().getFps();
	}

	@Override
	public int ping() {
		LocalPlayer p = mc().player;
		if (p == null || mc().getConnection() == null || mc().hasSingleplayerServer()) return -1;
		PlayerInfo info = mc().getConnection().getPlayerInfo(p.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	@Override
	public double x() {
		return mc().player == null ? 0 : mc().player.getX();
	}

	@Override
	public double y() {
		return mc().player == null ? 0 : mc().player.getY();
	}

	@Override
	public double z() {
		return mc().player == null ? 0 : mc().player.getZ();
	}

	@Override
	public float yaw() {
		return mc().player == null ? 0 : mc().player.getYRot();
	}

	@Override
	public float pitch() {
		return mc().player == null ? 0 : mc().player.getXRot();
	}

	@Override
	public String biome() {
		if (!inWorld()) return "";
		return mc().level.getBiome(mc().player.blockPosition()).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	@Override
	public String server() {
		ServerData s = mc().getCurrentServer();
		return s == null || mc().hasSingleplayerServer() ? "" : s.ip;
	}

	@Override
	public long dayTime() {
		return mc().level == null ? 0 : mc().level.getOverworldClockTime() % 24000L;
	}

	@Override
	public long worldAge() {
		return mc().level == null ? 0 : mc().level.getOverworldClockTime();
	}

	@Override
	public String playerName() {
		return mc().player == null ? mc().getUser().getName() : mc().player.getName().getString();
	}

	@Override
	public boolean sprinting() {
		return mc().player != null && mc().player.isSprinting();
	}

	@Override
	public boolean sneaking() {
		return mc().player != null && mc().player.isShiftKeyDown();
	}

	@Override
	public ItemInfo heldItem() {
		return mc().player == null ? null : item(mc().player.getMainHandItem());
	}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	@Override
	public ItemInfo[] armor() {
		ItemInfo[] out = new ItemInfo[4];
		if (mc().player != null)
			for (int i = 0; i < 4; i++) out[i] = item(mc().player.getItemBySlot(ARMOR[i]));
		return out;
	}

	@Override
	public List<EffectInfo> effects() {
		if (mc().player == null) return Collections.emptyList();
		List<EffectInfo> out = new ArrayList<>();
		for (MobEffectInstance e : mc().player.getActiveEffects()) out.add(new Effect(e));
		return out;
	}

	private static ItemInfo item(ItemStack stack) {
		return stack.isEmpty() ? null : new Item(stack);
	}

	private record Item(ItemStack stack) implements ItemInfo {
		@Override
		public String name() {
			return stack.getHoverName().getString();
		}

		@Override
		public int count() {
			return stack.getCount();
		}

		@Override
		public int durability() {
			return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : -1;
		}

		@Override
		public int maxDurability() {
			return stack.isDamageableItem() ? stack.getMaxDamage() : 0;
		}

		@Override
		public void drawIcon(float x, float y) {
			GuiGraphicsExtractor g = McRenderer.graphics();
			if (g == null) return;
			g.pose().pushMatrix().translate(x, y);
			g.item(stack, 0, 0);
			g.pose().popMatrix();
		}
	}

	private record Effect(MobEffectInstance e) implements EffectInfo {
		@Override
		public String name() {
			return e.getEffect().value().getDisplayName().getString();
		}

		@Override
		public int amplifier() {
			return e.getAmplifier();
		}

		@Override
		public int secondsLeft() {
			return e.isInfiniteDuration() ? -1 : e.getDuration() / 20;
		}

		@Override
		public boolean beneficial() {
			return e.getEffect().value().isBeneficial();
		}

		@Override
		public void drawIcon(float x, float y) {
			GuiGraphicsExtractor g = McRenderer.graphics();
			if (g == null) return;
			g.pose().pushMatrix().translate(x, y);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), 0, 0, 18, 18);
			g.pose().popMatrix();
		}
	}
}
