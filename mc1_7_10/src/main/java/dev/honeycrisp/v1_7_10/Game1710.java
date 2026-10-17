package dev.honeycrisp.v1_7_10;

import dev.honeycrisp.platform.GameInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Game1710 implements GameInfo {
	private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

	private static Minecraft mc() {
		return Minecraft.getMinecraft();
	}

	private static EntityClientPlayerMP player() {
		return mc().thePlayer;
	}

	@Override
	public boolean inWorld() {
		return mc().theWorld != null && player() != null;
	}

	/** Minecraft.debugFPS is private here; the public debug string starts with it ("60 fps, 3 chunk updates"). */
	@Override
	public int fps() {
		String s = mc().debug;
		int n = 0;
		for (int i = 0; s != null && i < s.length() && Character.isDigit(s.charAt(i)); i++) {
			n = n * 10 + s.charAt(i) - '0';
		}
		return n;
	}

	@Override
	public int ping() {
		if (!inWorld() || mc().isSingleplayer() || mc().getNetHandler() == null) {
			return -1;
		}
		String name = player().getCommandSenderName();
		for (Object o : mc().getNetHandler().playerInfoList) {
			GuiPlayerInfo info = (GuiPlayerInfo)o;
			if (info.name.equals(name)) {
				return info.responseTime;
			}
		}
		return -1;
	}

	@Override
	public double x() {
		return inWorld() ? player().posX : 0;
	}

	@Override
	public double y() {
		return inWorld() ? player().boundingBox.minY : 0;
	}

	@Override
	public double z() {
		return inWorld() ? player().posZ : 0;
	}

	@Override
	public float yaw() {
		return inWorld() ? player().rotationYaw : 0;
	}

	@Override
	public float pitch() {
		return inWorld() ? player().rotationPitch : 0;
	}

	@Override
	public String biome() {
		return inWorld() ? mc().theWorld.getBiomeGenForCoords(MathHelper.floor_double(player().posX), MathHelper.floor_double(player().posZ)).biomeName : "";
	}

	@Override
	public String server() {
		return mc().func_147104_D() == null ? "" : mc().func_147104_D().serverIP;
	}

	@Override
	public long dayTime() {
		return inWorld() ? mc().theWorld.getWorldTime() % 24000 : 0;
	}

	@Override
	public long worldAge() {
		return inWorld() ? mc().theWorld.getWorldTime() : 0;
	}

	@Override
	public String playerName() {
		return mc().getSession().getUsername();
	}

	@Override
	public boolean sprinting() {
		return inWorld() && player().isSprinting();
	}

	@Override
	public boolean sneaking() {
		return inWorld() && player().isSneaking();
	}

	@Override
	public ItemInfo heldItem() {
		return inWorld() ? item(player().getHeldItem()) : null;
	}

	@Override
	public ItemInfo[] armor() {
		ItemInfo[] out = new ItemInfo[4];
		if (inWorld()) {
			for (int i = 0; i < 4; i++) {
				out[i] = item(player().inventory.armorInventory[3 - i]); // vanilla: 0 = boots
			}
		}
		return out;
	}

	private static ItemInfo item(final ItemStack stack) {
		if (stack == null) {
			return null;
		}
		return new ItemInfo() {
			@Override
			public String name() {
				return stack.getDisplayName();
			}

			@Override
			public int count() {
				return stack.stackSize;
			}

			@Override
			public int durability() {
				return stack.isItemStackDamageable() ? stack.getMaxDamage() - stack.getItemDamage() : -1;
			}

			@Override
			public int maxDurability() {
				return stack.isItemStackDamageable() ? stack.getMaxDamage() : -1;
			}

			@Override
			public void drawIcon(float x, float y) {
				GL11.glPushMatrix();
				GL11.glTranslatef(x, y, 0);
				GL11.glEnable(GL11.GL_BLEND);
				OpenGlHelper.glBlendFunc(770, 771, 1, 0);
				GL11.glEnable(GL12.GL_RESCALE_NORMAL);
				RenderHelper.enableGUIStandardItemLighting();
				ScreenWrapper.items().renderItemAndEffectIntoGUI(mc().fontRenderer, mc().getTextureManager(), stack, 0, 0);
				RenderHelper.disableStandardItemLighting();
				GL11.glDisable(GL12.GL_RESCALE_NORMAL);
				GL11.glDisable(GL11.GL_DEPTH_TEST);
				GL11.glPopMatrix();
			}
		};
	}

	@Override
	public List<EffectInfo> effects() {
		if (!inWorld()) {
			return Collections.emptyList();
		}
		List<EffectInfo> out = new ArrayList<EffectInfo>();
		for (Object o : player().getActivePotionEffects()) {
			final PotionEffect effect = (PotionEffect)o;
			final Potion potion = Potion.potionTypes[effect.getPotionID()];
			if (potion == null) {
				continue;
			}
			out.add(new EffectInfo() {
				@Override
				public String name() {
					return I18n.format(potion.getName());
				}

				@Override
				public int amplifier() {
					return effect.getAmplifier();
				}

				@Override
				public int secondsLeft() {
					return effect.getDuration() / 20;
				}

				@Override
				public boolean beneficial() {
					return !potion.isBadEffect();
				}

				@Override
				public void drawIcon(float x, float y) {
					if (!potion.hasStatusIcon()) {
						return;
					}
					int i = potion.getStatusIconIndex();
					mc().getTextureManager().bindTexture(INVENTORY);
					GL11.glColor4f(1, 1, 1, 1);
					GL11.glEnable(GL11.GL_BLEND);
					OpenGlHelper.glBlendFunc(770, 771, 1, 0);
					GL11.glPushMatrix();
					GL11.glTranslatef(x, y, 0);
					Gui.func_146110_a(0, 0, i % 8 * 18, 198 + i / 8 * 18, 18, 18, 256, 256);
					GL11.glPopMatrix();
				}
			});
		}
		return out;
	}
}
