package dev.honeycrisp.v1_8_9.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Call sites Forge has no event for. Runs after FML's deobfuscation, so class names are MCP and member names are SRG
 * in production (MCP in dev): every match accepts both, like Graft's GlTransformer.
 */
public final class Transformer implements IClassTransformer {
	private static final String BRIDGE = "dev/honeycrisp/v1_8_9/HoneycrispMod";

	private static boolean is(String name, String mcp, String srg) {
		return name.equals(mcp) || name.equals(srg);
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		if (TREE.contains(transformedName)) {
			return tree(transformedName, bytes);
		}
		final boolean entityRenderer = transformedName.equals("net.minecraft.client.renderer.EntityRenderer");
		final boolean living = transformedName.equals("net.minecraft.client.renderer.entity.RendererLivingEntity");
		if (!entityRenderer && !living) {
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
				if (!mouse && !camera && !brightness) {
					return mv;
				}
				return new MethodVisitor(Opcodes.ASM5, mv) {
					private int puts;

					@Override
					public void visitMethodInsn(int opcode, String owner, String n, String desc, boolean itf) {
						if (mouse && opcode == Opcodes.INVOKEVIRTUAL && desc.equals("(FF)V") && is(n, "setAngles", "func_70082_c")) {
							// player.setAngles(dx, dy) -> HoneycrispMod.setAngles(player, dx, dy): freelook takes the mouse
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

	private static final java.util.List<String> TREE = java.util.Arrays.asList("net.minecraft.client.gui.GuiNewChat",
		"net.minecraft.client.gui.GuiIngame", "net.minecraft.client.renderer.ItemRenderer",
		"net.minecraft.client.renderer.entity.layers.LayerArmorBase", "net.minecraft.client.renderer.entity.RenderEntityItem");

	private static MethodInsnNode bridge(String name, String desc) {
		return new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, name, desc, false);
	}

	private static boolean call(AbstractInsnNode insn, String mcp, String srg, String desc) {
		return insn instanceof MethodInsnNode && ((MethodInsnNode) insn).desc.equals(desc) && is(((MethodInsnNode) insn).name, mcp, srg);
	}

	/** Patches written against the tree API; each method is matched by MCP or SRG name plus descriptor. */
	static byte[] tree(String transformedName, byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		int patched = 0;
		for (MethodNode m : node.methods) {
			InsnList code = m.instructions;
			if (transformedName.endsWith("GuiNewChat") && m.desc.equals("(I)V") && is(m.name, "drawChat", "func_146230_a")) {
				// the first drawRect is the line background
				for (AbstractInsnNode i = code.getFirst(); i != null; i = i.getNext()) {
					if (call(i, "drawRect", "func_73734_a", "(IIIII)V")) {
						code.insertBefore(i, bridge("chatBackground", "(I)I"));
						patched++;
						break;
					}
				}
			} else if (transformedName.endsWith("GuiIngame") && is(m.name, "renderScoreboard", "func_180475_a")
				&& m.desc.equals("(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V")) {
				for (AbstractInsnNode i = code.getFirst(); i != null; i = i.getNext()) {
					AbstractInsnNode next = i.getNext();
					if (call(i, "getScorePoints", "func_96652_c", "()I") && call(next, "append", "append", "(I)Ljava/lang/StringBuilder;")) {
						code.set(next, bridge("scoreAppend", "(Ljava/lang/StringBuilder;I)Ljava/lang/StringBuilder;"));
						patched++;
					}
				}
			} else if (transformedName.endsWith("ItemRenderer") && m.desc.equals("(F)V") && is(m.name, "renderItemInFirstPerson", "func_78440_a")) {
				// transformFirstPersonItem(equip, 0.0F) in the use-action switch: NONE, EAT/DRINK, BLOCK, BOW (branch 0-3)
				int swing = -1, branch = 0;
				for (AbstractInsnNode i = code.getFirst(); i != null; i = i.getNext()) {
					if (call(i, "getSwingProgress", "func_70678_g", "(F)F") && i.getNext().getOpcode() == Opcodes.FSTORE) {
						swing = ((VarInsnNode) i.getNext()).var;
					} else if (swing >= 0 && i.getOpcode() == Opcodes.FCONST_0
						&& call(i.getNext(), "transformFirstPersonItem", "func_178096_b", "(FF)V")) {
						InsnList add = new InsnList();
						add.add(new VarInsnNode(Opcodes.FLOAD, swing));
						add.add(new InsnNode(Opcodes.ICONST_0 + branch++));
						add.add(bridge("useSwing", "(FFI)F"));
						code.insert(i, add);
						patched++;
					}
				}
			} else if (transformedName.endsWith("LayerArmorBase") && m.desc.equals("()Z") && is(m.name, "shouldCombineTextures", "func_177142_b")) {
				for (AbstractInsnNode i = code.getFirst(); i != null; i = i.getNext()) {
					if (i.getOpcode() == Opcodes.IRETURN) {
						code.insertBefore(i, bridge("armorFlash", "(Z)Z"));
						patched++;
					}
				}
			} else if (transformedName.endsWith("RenderEntityItem") && is(m.name, "func_177077_a", "func_177077_a")
				&& m.desc.equals("(Lnet/minecraft/entity/item/EntityItem;DDDFLnet/minecraft/client/resources/model/IBakedModel;)I")) {
				boolean bob = false, spin = false;
				for (AbstractInsnNode i = code.getFirst(); i != null; i = i.getNext()) {
					if (!bob && call(i, "sin", "func_76126_a", "(F)F")) {
						code.insert(i, bridge("itemBob", "(F)F"));
						bob = true;
						patched++;
					} else if (!spin && call(i, "rotate", "func_179114_b", "(FFFF)V")) {
						code.insertBefore(i, new VarInsnNode(Opcodes.ALOAD, 1));
						MethodInsnNode b = bridge("itemSpin", "(FFFFLnet/minecraft/entity/item/EntityItem;)V");
						code.set(i, b);
						i = b;
						spin = true;
						patched++;
					}
				}
			}
		}
		System.out.println("[Honeycrisp Client] patched " + patched + " call sites in " + transformedName);
		if (patched == 0) {
			return bytes;
		}
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}
}
