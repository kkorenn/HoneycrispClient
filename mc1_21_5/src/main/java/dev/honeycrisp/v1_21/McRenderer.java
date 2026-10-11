package dev.honeycrisp.v1_21;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;
import dev.honeycrisp.v1_21.mixin.GuiGraphicsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * 1.21.5's GuiGraphics draws immediately into RenderType batches (PoseStack, depth-tested z layers). Core quads go
 * through depth-less render types and are flushed on every type switch and around each core draw, so they paint in
 * call order on top of whatever vanilla drew before.
 */
public final class McRenderer implements Renderer {
	public static final McRenderer INSTANCE = new McRenderer();
	/** Screen.render draws the background itself (1.21.5 only; 1.21.6+ calls renderBackground before render). */
	static final boolean RENDER_DRAWS_BACKGROUND = true;
	/** GUI_TEXTURED with premultiplied-alpha blending and no depth (1.21.5 has no such pipeline). */
	private static final RenderPipeline PREMULTIPLIED = RenderPipeline.builder()
		.withLocation(ResourceLocation.fromNamespaceAndPath("honeycrisp", "pipeline/gui_textured_premultiplied"))
		.withVertexShader("core/position_tex_color")
		.withFragmentShader("core/position_tex_color")
		.withUniform("ModelViewMat", UniformType.MATRIX4X4)
		.withUniform("ProjMat", UniformType.MATRIX4X4)
		.withUniform("ColorModulator", UniformType.VEC4)
		.withSampler("Sampler0")
		.withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
		.build();
	private static GuiGraphics graphics;
	private static RenderType batch;

	private McRenderer() {
	}

	/** Runs core drawing with {@code g} as the target (HUD, screens, crosshair). */
	public static void draw(GuiGraphics g, Runnable r) {
		GuiGraphics prev = graphics;
		g.flush();
		graphics = g;
		batch = null;
		try {
			r.run();
		} finally {
			g.flush();
			graphics = prev;
			batch = null;
		}
	}

	private static VertexConsumer buffer(GuiGraphics g, RenderType type) {
		if (batch != type) {
			g.flush();
			batch = type;
		}
		return ((GuiGraphicsAccess) g).honeycrisp$bufferSource().getBuffer(type);
	}

	static void item(ItemStack stack, float x, float y) {
		GuiGraphics g = graphics;
		if (g == null) return;
		g.flush();
		batch = null;
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.renderItem(stack, 0, 0);
		g.pose().popPose();
		g.flush();
	}

	static void effect(Holder<MobEffect> effect, float x, float y) {
		GuiGraphics g = graphics;
		if (g == null) return;
		g.flush();
		batch = null;
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.blitSprite(RenderType::guiTextured, Minecraft.getInstance().getMobEffectTextures().get(effect), 0, 0, 18, 18);
		g.pose().popPose();
		g.flush();
	}

	static final class Tex implements Texture {
		final DynamicTexture texture;
		final RenderType type;
		final int width, height;

		Tex(DynamicTexture texture, int width, int height) {
			this.texture = texture;
			this.width = width;
			this.height = height;
			type = RenderType.create("honeycrisp_texture", 1536, PREMULTIPLIED, RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.EmptyTextureStateShard(() -> RenderSystem.setShaderTexture(0, texture.getTexture()), () -> {}))
				.createCompositeState(false));
		}

		@Override
		public int width() {
			return width;
		}

		@Override
		public int height() {
			return height;
		}
	}

	@Override
	public Texture createTexture(int width, int height, ByteBuffer premultipliedRgba) {
		NativeImage image = new NativeImage(width, height, false);
		MemoryUtil.memCopy(MemoryUtil.memAddress(premultipliedRgba), image.getPointer(), (long) width * height * 4);
		DynamicTexture t = new DynamicTexture(() -> "honeycrisp", image);
		t.setFilter(true, false);
		return new Tex(t, width, height);
	}

	@Override
	public void deleteTexture(Texture texture) {
		Tex t = (Tex) texture;
		if (batch == t.type && graphics != null) graphics.flush();
		t.texture.close();
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		GuiGraphics g = graphics;
		if (g == null || w <= 0 || h <= 0) return;
		Matrix4f m = g.pose().last().pose();
		VertexConsumer vc = buffer(g, RenderType.guiOverlay());
		vc.addVertex(m, x, y, 0).setColor(argb);
		vc.addVertex(m, x, y + h, 0).setColor(argb);
		vc.addVertex(m, x + w, y + h, 0).setColor(argb);
		vc.addVertex(m, x + w, y, 0).setColor(argb);
	}

	@Override
	public void texture(Texture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argbTint) {
		GuiGraphics g = graphics;
		if (g == null || w <= 0 || h <= 0) return;
		// Premultiplied pipeline: the vertex color multiplies rgb and alpha, so premultiply the tint too.
		int a = argbTint >>> 24;
		int tint = a << 24 | ((argbTint >> 16 & 255) * a / 255) << 16 | ((argbTint >> 8 & 255) * a / 255) << 8 | (argbTint & 255) * a / 255;
		Matrix4f m = g.pose().last().pose();
		VertexConsumer vc = buffer(g, ((Tex) texture).type);
		vc.addVertex(m, x, y, 0).setUv(u0, v0).setColor(tint);
		vc.addVertex(m, x, y + h, 0).setUv(u0, v1).setColor(tint);
		vc.addVertex(m, x + w, y + h, 0).setUv(u1, v1).setColor(tint);
		vc.addVertex(m, x + w, y, 0).setUv(u1, v0).setColor(tint);
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		if (graphics != null) graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
	}

	@Override
	public void popClip() {
		if (graphics != null) graphics.disableScissor();
	}

	// ponytail: no backdrop blur on 1.21.5 (would need a framebuffer copy + blur pass); core falls back to tinted glass.
	@Override
	public boolean blur(float x, float y, float w, float h, float radius) {
		return false;
	}
}
