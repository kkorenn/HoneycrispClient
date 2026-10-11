package dev.honeycrisp.v1_12_2;

import java.util.Arrays;

/** LWJGL2 Keyboard codes ↔ GLFW key codes. */
final class KeyMap {
	private static final int[] TO_GLFW = new int[256];
	private static final int[] TO_LWJGL = new int[349];

	static {
		Arrays.fill(TO_GLFW, -1);
		Arrays.fill(TO_LWJGL, 0);
		map(1, 256); // escape
		for (int i = 1; i <= 9; i++) {
			map(1 + i, 48 + i);
		}
		map(11, 48);
		row("QWERTYUIOP", 16);
		row("ASDFGHJKL", 30);
		row("ZXCVBNM", 44);
		for (int i = 0; i < 10; i++) {
			map(59 + i, 290 + i); // F1-F10
		}
		map(87, 300);
		map(88, 301);
		for (int i = 0; i < 6; i++) {
			map(100 + i, 302 + i); // F13-F18
		}
		map(113, 308);
		int[] pairs = {
			12, 45, 13, 61, 14, 259, 15, 258, 26, 91, 27, 93, 28, 257, 29, 341, 39, 59, 40, 39, 41, 96, 42, 340, 43, 92,
			51, 44, 52, 46, 53, 47, 54, 344, 55, 332, 56, 342, 57, 32, 58, 280, 69, 282, 70, 281,
			71, 327, 72, 328, 73, 329, 74, 333, 75, 324, 76, 325, 77, 326, 78, 334, 79, 321, 80, 322, 81, 323, 82, 320, 83, 330,
			141, 336, 156, 335, 157, 345, 167, 161, 181, 331, 183, 283, 184, 346, 197, 284,
			199, 268, 200, 265, 201, 266, 203, 263, 205, 262, 207, 269, 208, 264, 209, 267, 210, 260, 211, 261,
			219, 343, 220, 347, 221, 348
		};
		for (int i = 0; i < pairs.length; i += 2) {
			map(pairs[i], pairs[i + 1]);
		}
	}

	private KeyMap() {
	}

	private static void row(String keys, int first) {
		for (int i = 0; i < keys.length(); i++) {
			map(first + i, keys.charAt(i));
		}
	}

	private static void map(int lwjgl, int glfw) {
		TO_GLFW[lwjgl] = glfw;
		TO_LWJGL[glfw] = lwjgl;
	}

	static int toGlfw(int lwjgl) {
		return lwjgl >= 0 && lwjgl < TO_GLFW.length ? TO_GLFW[lwjgl] : -1;
	}

	/** 0 (KEY_NONE) when LWJGL2 has no such key. */
	static int toLwjgl(int glfw) {
		return glfw >= 0 && glfw < TO_LWJGL.length ? TO_LWJGL[glfw] : 0;
	}
}
