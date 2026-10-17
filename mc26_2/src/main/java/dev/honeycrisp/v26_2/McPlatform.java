package dev.honeycrisp.v26_2;

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

/** Platform + GameInfo for 26.2 (still GLFW: key codes pass through unchanged). */
public final class McPlatform implements Platform, GameInfo {
	public static final McPlatform INSTANCE = new McPlatform();
	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// Platform

	@Override
	public String minecraftVersion() {
		return "26.2";
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
		com.mojang.blaze3d.platform.Window w = mc().getWindow();
		return (double) w.getWidth() / Math.max(1, w.getScreenWidth());
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
		return glfwKey > 0 && InputConstants.isKeyDown(mc().getWindow(), glfwKey);
	}

	@Override
	public String keyName(int glfwKey) {
		return glfwKey > 0 ? InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString() : "";
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
