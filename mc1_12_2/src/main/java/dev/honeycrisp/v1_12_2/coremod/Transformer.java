package dev.honeycrisp.v1_12_2.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Call sites Forge has no event for. Runs after FML's deobfuscation, so class names are MCP and member names are SRG
 * in production (MCP in dev): every match accepts both, like Graft's GlTransformer.
 */
public final class Transformer implements IClassTransformer {
	private static final String BRIDGE = "dev/honeycrisp/v1_12_2/HoneycrispMod";

	private static boolean is(String name, String mcp, String srg) {
		return name.equals(mcp) || name.equals(srg);
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		final boolean entityRenderer = transformedName.equals("net.minecraft.client.renderer.EntityRenderer");
		final boolean living = transformedName.equals("net.minecraft.client.renderer.entity.RenderLivingBase");
		final boolean chat = transformedName.equals("net.minecraft.client.gui.GuiNewChat");
		final boolean ingame = transformedName.equals("net.minecraft.client.gui.GuiIngame");
		final boolean armor = transformedName.equals("net.minecraft.client.renderer.entity.layers.LayerArmorBase");
		if (!entityRenderer && !living && !chat && !ingame && !armor) {
			return bytes;
		}
		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		final int[] patched = {0};
		reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String mname, String mdesc, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, mname, mdesc, signature, exceptions);
				final boolean mouse = entityRenderer && mdesc.equals("(FJ)V") && is(mname, "updateCameraAndRender", "func_181560_a");
				final boolean camera = entityRenderer && mdesc.equals("(F)V") && is(mname, "orientCamera", "func_78467_g");
				final boolean brightness = living && mdesc.equals("(Lnet/minecraft/entity/EntityLivingBase;FZ)Z")
					&& is(mname, "setBrightness", "func_177092_a");
				final boolean drawChat = chat && mdesc.equals("(I)V") && is(mname, "drawChat", "func_146230_a");
				final boolean scoreboard = ingame && mdesc.equals("(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V")
					&& is(mname, "renderScoreboard", "func_180475_a");
				final boolean combine = armor && mdesc.equals("()Z") && is(mname, "shouldCombineTextures", "func_177142_b");
				if (!mouse && !camera && !brightness && !drawChat && !scoreboard && !combine) {
					return mv;
				}
				return new MethodVisitor(Opcodes.ASM5, mv) {
					private int puts, rects;
					private boolean score;

					@Override
					public void visitInsn(int opcode) {
						if (combine && opcode == Opcodes.IRETURN) {
							// return HoneycrispMod.armorFlash(false): armor takes the hurt tint like 1.7
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "armorFlash", "(Z)Z", false);
							patched[0]++;
						}
						super.visitInsn(opcode);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String n, String desc, boolean itf) {
						if (mouse && opcode == Opcodes.INVOKEVIRTUAL && desc.equals("(FF)V") && is(n, "turn", "func_70082_c")) {
							// player.turn(dx, dy) -> HoneycrispMod.setAngles(player, dx, dy): freelook takes the mouse
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "setAngles", "(Lnet/minecraft/entity/Entity;FF)V", false);
							patched[0]++;
							return;
						}
						if (brightness && puts < 4 && opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/nio/FloatBuffer")
							&& n.equals("put") && desc.equals("(F)Ljava/nio/FloatBuffer;")) {
							// the first four puts are the hurt tint (1, 0, 0, 0.3)
							super.visitInsn(Opcodes.ICONST_0 + puts++);
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "hurtTint", "(Ljava/nio/FloatBuffer;FI)Ljava/nio/FloatBuffer;", false);
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
