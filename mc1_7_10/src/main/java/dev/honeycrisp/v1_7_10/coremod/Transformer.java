package dev.honeycrisp.v1_7_10.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Call sites Forge has no event for. Runs after FML's deobfuscation, so member names are SRG in production (MCP in dev):
 * every match accepts both, like Graft's GlTransformer. Graft's transformer runs first and has already moved
 * GL11 calls to its own class (same name and descriptor), so GL calls are matched by name, not owner.
 */
public final class Transformer implements IClassTransformer {
	private static final String BRIDGE = "dev/honeycrisp/v1_7_10/HoneycrispMod";

	private static boolean is(String name, String mcp, String srg) {
		return name.equals(mcp) || name.equals(srg);
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		final boolean entityRenderer = transformedName.equals("net.minecraft.client.renderer.EntityRenderer");
		final boolean living = transformedName.equals("net.minecraft.client.renderer.entity.RendererLivingEntity");
		final boolean chat = transformedName.equals("net.minecraft.client.gui.GuiNewChat");
		final boolean ingame = transformedName.equals("net.minecraft.client.gui.GuiIngame");
		if (!entityRenderer && !living && !chat && !ingame) {
			return bytes;
		}
		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		final int[] patched = {0};
		reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String mname, String mdesc, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, mname, mdesc, signature, exceptions);
				final boolean fov = entityRenderer && mdesc.equals("(FZ)F") && is(mname, "getFOVModifier", "func_78481_a");
				final boolean mouse = entityRenderer && mdesc.equals("(F)V") && is(mname, "updateCameraAndRender", "func_78480_b");
				final boolean camera = entityRenderer && mdesc.equals("(F)V") && is(mname, "orientCamera", "func_78467_g");
				final boolean render = living && mdesc.equals("(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V") && is(mname, "doRender", "func_76986_a");
				final boolean drawChat = chat && mdesc.equals("(I)V") && is(mname, "drawChat", "func_146230_a");
				final boolean scoreboard = ingame && mdesc.equals("(Lnet/minecraft/scoreboard/ScoreObjective;IILnet/minecraft/client/gui/FontRenderer;)V")
					&& mname.equals("func_96136_a");
				if (!fov && !mouse && !camera && !render && !drawChat && !scoreboard) {
					return mv;
				}
				return new MethodVisitor(Opcodes.ASM5, mv) {
					private int rects;
					private boolean score, hurtAlpha;

					@Override
					public void visitLdcInsn(Object cst) {
						hurtAlpha = render && cst instanceof Float && (Float)cst == 0.4F;
						super.visitLdcInsn(cst);
					}

					@Override
					public void visitInsn(int opcode) {
						if (fov && opcode == Opcodes.FRETURN) {
							// return HoneycrispMod.fov(value, partialTicks)
							super.visitVarInsn(Opcodes.FLOAD, 1);
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "fov", "(FF)F", false);
							patched[0]++;
						}
						super.visitInsn(opcode);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String n, String desc, boolean itf) {
						boolean alpha = hurtAlpha;
						hurtAlpha = false;
						if (mouse && opcode == Opcodes.INVOKEVIRTUAL && desc.equals("(FF)V") && is(n, "setAngles", "func_70082_c")) {
							// player.setAngles(dx, dy) -> HoneycrispMod.setAngles(player, dx, dy): freelook takes the mouse
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "setAngles", "(Lnet/minecraft/entity/Entity;FF)V", false);
							patched[0]++;
							return;
						}
						if (alpha && opcode == Opcodes.INVOKESTATIC && n.equals("glColor4f") && desc.equals("(FFFF)V")) {
							// the hurt overlay: glColor4f(brightness, 0, 0, 0.4F)
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "hurtColor", "(FFFF)V", false);
							patched[0]++;
							return;
						}
						if (drawChat && opcode == Opcodes.INVOKESTATIC && desc.equals("(IIIII)V") && is(n, "drawRect", "func_73734_a") && rects++ == 0) {
							// the first drawRect is the per-line background
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "chatBackground", "(I)I", false);
							patched[0]++;
						}
						if (scoreboard && score && opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/StringBuilder") && n.equals("toString")) {
							// the StringBuilder that just appended score.getScorePoints()
							score = false;
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "scoreText", "(Ljava/lang/StringBuilder;)Ljava/lang/String;", false);
							patched[0]++;
							return;
						}
						if (scoreboard && desc.equals("()I") && is(n, "getScorePoints", "func_96652_c")) {
							score = true;
						}
						super.visitMethodInsn(opcode, owner, n, desc, itf);
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String n, String desc) {
						super.visitFieldInsn(opcode, owner, n, desc);
						if (!camera || opcode != Opcodes.GETFIELD || !desc.equals("F")) {
							return;
						}
						if (is(n, "rotationYaw", "field_70177_z") || is(n, "prevRotationYaw", "field_70126_B")) {
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "cameraYaw", "(F)F", false);
							patched[0]++;
						} else if (is(n, "rotationPitch", "field_70125_A") || is(n, "prevRotationPitch", "field_70127_C")) {
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "cameraPitch", "(F)F", false);
							patched[0]++;
						}
					}
				};
			}
		}, 0);
		System.out.println("[Honeycrisp Client] patched " + patched[0] + " call sites in " + transformedName);
		return patched[0] > 0 ? writer.toByteArray() : bytes;
	}
}
