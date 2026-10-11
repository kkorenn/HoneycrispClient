package dev.honeycrisp.platform;

/** GLFW key/mouse codes, the core's single key space (LWJGL2 adapters translate). */
public final class Keys {
	private Keys() {
	}

	public static final int RELEASE = 0, PRESS = 1, REPEAT = 2;
	public static final int MOUSE_LEFT = 0, MOUSE_RIGHT = 1, MOUSE_MIDDLE = 2;
	public static final int UNKNOWN = -1;
	public static final int SPACE = 32, APOSTROPHE = 39, COMMA = 44, MINUS = 45, PERIOD = 46, SLASH = 47;
	public static final int KEY_0 = 48, KEY_9 = 57;
	public static final int A = 65, C = 67, D = 68, E = 69, F = 70, Q = 81, R = 82, S = 83, V = 86, W = 87, X = 88, Z = 90;
	public static final int ESCAPE = 256, ENTER = 257, TAB = 258, BACKSPACE = 259, DELETE = 261;
	public static final int RIGHT = 262, LEFT = 263, DOWN = 264, UP = 265, HOME = 268, END = 269;
	public static final int F1 = 290, F5 = 294;
	public static final int LEFT_SHIFT = 340, LEFT_CONTROL = 341, LEFT_ALT = 342, LEFT_SUPER = 343;
	public static final int RIGHT_SHIFT = 344, RIGHT_CONTROL = 345, RIGHT_ALT = 346, RIGHT_SUPER = 347;
}
