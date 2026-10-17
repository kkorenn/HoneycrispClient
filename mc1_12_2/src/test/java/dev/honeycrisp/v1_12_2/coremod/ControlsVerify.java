package dev.honeycrisp.v1_12_2.coremod;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Runs against both development and production names, without initializing Minecraft or a window. */
public final class ControlsVerify {
	public static void main(String[] args) throws Exception {
		for (String path : args) verify(path);
		System.out.println("CONTROLS VERIFY OK");
	}

	private static void verify(String path) throws Exception {
		Set<String> required = new HashSet<String>(Arrays.asList("GuiButton", "GuiOptionSlider", "GuiSlider",
			"GuiScreenOptionsSounds$Button", "GuiButtonLanguage", "GuiLockIconButton", "GuiButtonExt"));
		int patched = 0;
		try (JarFile jar = new JarFile(path)) {
			java.util.Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.endsWith(".class") || !(name.startsWith("net/minecraft/client/gui/")
					|| name.startsWith("net/minecraftforge/fml/client/config/"))) continue;
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				try (InputStream in = jar.getInputStream(entry)) {
					byte[] buffer = new byte[8192];
					for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
				}
				byte[] original = out.toByteArray();
				byte[] transformed = ControlTransformer.transform(original);
				if (transformed == original) continue;
				patched++;
				required.remove(name.substring(name.lastIndexOf('/') + 1, name.length() - 6));
				ClassNode before = new ClassNode(), after = new ClassNode();
				new ClassReader(original).accept(before, 0);
				new ClassReader(transformed).accept(after, 0);
				for (int i = 0; i < after.methods.size(); i++) {
					MethodNode method = (MethodNode)after.methods.get(i);
					if (method.instructions.size() > 0) new Analyzer(new BasicVerifier()).analyze(after.name, method);
					MethodNode old = (MethodNode)before.methods.get(i);
					// Slider settings/responders and input handlers must remain intact; only artwork calls change.
					if (!calls(old).equals(calls(method))) throw new AssertionError("Behavior changed: " + name + "." + method.name);
				}
			}
		}
		if (!required.isEmpty()) throw new AssertionError("Controls not skinned: " + required);
		System.out.println("Verified " + patched + " control classes in " + path);
	}

	private static java.util.List<String> calls(MethodNode method) {
		java.util.List<String> calls = new java.util.ArrayList<String>();
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode)) continue;
			MethodInsnNode call = (MethodInsnNode)insn;
			if (call.owner.equals("dev/honeycrisp/v1_12_2/ControlGlass1122") || call.name.equals("drawTexturedModalRect")
				|| call.name.equals("func_73729_b") || call.name.equals("drawContinuousTexturedBox")) continue;
			calls.add(call.owner + "." + call.name + call.desc);
		}
		return calls;
	}
}
