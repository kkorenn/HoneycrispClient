package dev.honeycrisp.v1_12_2.coremod;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Skins shared vanilla/Forge controls without replacing their input or slider-update code. */
final class ControlTransformer {
	private static final String BUTTON = "net/minecraft/client/gui/GuiButton";
	private static final String EXT = "net/minecraftforge/fml/client/config/GuiButtonExt";
	private static final String BRIDGE = "dev/honeycrisp/v1_12_2/ControlGlass1122";
	private static final String RECT = "(L" + BUTTON + ";IIIIII)V";
	private static final String BOX = "(Lnet/minecraft/util/ResourceLocation;IIIIIIIIIIIIF)V";

	static byte[] transform(byte[] bytes) {
		ClassReader reader = new ClassReader(bytes);
		String type = reader.getClassName();
		if (!type.equals(BUTTON) && !type.equals(EXT) && !BUTTON.equals(reader.getSuperName()) && !EXT.equals(reader.getSuperName())) {
			return bytes;
		}
		final boolean standard = type.equals(BUTTON);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		final int[] count = {0};
		reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				final boolean draw = (name.equals("drawButton") || name.equals("func_191745_a"))
					&& desc.equals("(Lnet/minecraft/client/Minecraft;IIF)V");
				final boolean drag = (name.equals("mouseDragged") || name.equals("func_146119_b"))
					&& desc.equals("(Lnet/minecraft/client/Minecraft;II)V");
				if (!draw && !drag) return mv;
				return new MethodVisitor(Opcodes.ASM5, mv) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						if (opcode == Opcodes.INVOKEVIRTUAL && desc.equals("(IIIIII)V")
							&& (name.equals("drawTexturedModalRect") || name.equals("func_73729_b"))) {
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE,
								drag ? "sliderPart" : standard ? "buttonPart" : "iconPart", RECT, false);
							count[0]++;
							return;
						}
						if (opcode == Opcodes.INVOKESTATIC && owner.equals("net/minecraftforge/fml/client/config/GuiUtils")
							&& name.equals("drawContinuousTexturedBox") && desc.equals(BOX)) {
							super.visitVarInsn(Opcodes.ALOAD, 0);
							super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "forgeBox",
								BOX.substring(0, BOX.length() - 2) + "L" + BUTTON + ";)V", false);
							count[0]++;
							return;
						}
						super.visitMethodInsn(opcode, owner, name, desc, itf);
					}
				};
			}
		}, 0);
		if (count[0] == 0) return bytes;
		System.out.println("[Honeycrisp Client] glass artwork: " + type + " (" + count[0] + " calls)");
		return writer.toByteArray();
	}
}
