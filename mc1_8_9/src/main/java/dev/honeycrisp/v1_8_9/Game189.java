package dev.honeycrisp.v1_8_9;

import dev.honeycrisp.platform.GameInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Game189 implements GameInfo {
	private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

	private static Minecraft mc() {
		return Minecraft.getMinecraft();
	}

	private static EntityPlayerSP player() {
		return mc().thePlayer;
	}

	@Override
	public boolean inWorld() {
		return mc().theWorld != null && player() != null;
	}

	@Override
	public int fps() {
		return Minecraft.getDebugFPS();
	}

	@Override
	public int ping() {
		if (!inWorld() || mc().isSingleplayer() || mc().getNetHandler() == null) {
			return -1;
		}
		NetworkPlayerInfo info = mc().getNetHandler().getPlayerInfo(player().getUniqueID());
		return info == null ? -1 : info.getResponseTime();
	}

	@Override
	public double x() {
		return inWorld() ? player().posX : 0;
	}

	@Override
	public double y() {
		return inWorld() ? player().getEntityBoundingBox().minY : 0;
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
		return inWorld() ? mc().theWorld.getBiomeGenForCoords(new BlockPos(player())).biomeName : "";
	}

	@Override
	public String server() {
		return mc().getCurrentServerData() == null ? "" : mc().getCurrentServerData().serverIP;
	}

	@Override
	public long dayTime() {
		return inWorld() ? mc().theWorld.getWorldTime() % 24000 : 0;
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
				GlStateManager.pushMatrix();
				GlStateManager.translate(x, y, 0);
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
				RenderHelper.enableGUIStandardItemLighting();
				mc().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				GlStateManager.popMatrix();
			}
		};
	}

	@Override
	public List<EffectInfo> effects() {
		if (!inWorld()) {
			return Collections.emptyList();
		}
		List<EffectInfo> out = new ArrayList<EffectInfo>();
		for (final PotionEffect effect : player().getActivePotionEffects()) {
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
					GlStateManager.color(1, 1, 1, 1);
					GlStateManager.enableBlend();
					GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
					GlStateManager.pushMatrix();
					GlStateManager.translate(x, y, 0);
					Gui.drawModalRectWithCustomSizedTexture(0, 0, i % 8 * 18, 198 + i / 8 * 18, 18, 18, 256, 256);
					GlStateManager.popMatrix();
				}
			});
		}
		return out;
	}
}
