package dev.honeycrisp.module;

import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnchorTest {
	static final class Box extends HudModule {
		Box() {
			super("box", "Box", "", Category.HUD, "square", Anchor.TOP_LEFT, 4, 4);
		}

		@Override
		protected void measure(GameInfo game) {
			size(40, 20);
		}

		@Override
		protected void render(Renderer renderer, GameInfo game, float partialTick) {
		}
	}

	@Test
	void positions() {
		assertEquals(4, Anchor.position(0, 4, 40, 400));
		assertEquals(400 - 40 - 4, Anchor.position(1, -4, 40, 400));
		assertEquals(180, Anchor.position(.5f, 0, 40, 400));
		assertEquals(0, Anchor.position(0, -50, 40, 400), "clamped on screen");
		assertEquals(360, Anchor.position(1, 50, 40, 400), "clamped on screen");
	}

	@Test
	void offsetIsInverse() {
		for (Anchor a : Anchor.values()) {
			float off = Anchor.offset(a.fx, 123, 40, 400);
			assertEquals(123, Anchor.position(a.fx, off, 40, 400), 1e-4);
		}
	}

	@Test
	void moveToReanchorsAndStaysPutOnResize() {
		Box b = new Box();
		b.layout(null, false);
		b.scale.set(2.0);
		assertEquals(80, b.width());
		b.moveTo(300, 250, 400, 300);
		assertEquals(Anchor.BOTTOM_RIGHT, b.anchor.get());
		assertEquals(300, b.x(400), 1e-4);
		assertEquals(250, b.y(300), 1e-4);
		// Bigger window: stays glued to the bottom-right corner.
		assertEquals(500 - 100, b.x(500), 1e-4);
		assertEquals(400 - 50, b.y(400), 1e-4);
		b.moveTo(160, 2, 400, 300);
		assertEquals(Anchor.TOP_CENTER, b.anchor.get());
		assertEquals(160, b.x(400), 1e-4);
		b.moveTo(-30, -30, 400, 300);
		assertEquals(0, b.x(400));
		assertEquals(0, b.y(300));
	}
}
