package dev.honeycrisp.modules;

import dev.honeycrisp.platform.Keys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureLogicTest {
	@Test
	void cpsWindow() {
		ClickCounter c = new ClickCounter();
		for (int i = 0; i < 10; i++) c.click(1000 + i * 100); // 1000..1900
		assertEquals(10, c.count(1900));
		assertEquals(10, c.count(1999));
		assertEquals(9, c.count(2000), "click at 1000 leaves the (now-1000, now] window");
		assertEquals(1, c.count(2899));
		assertEquals(0, c.count(5000));
		for (int i = 0; i < 100; i++) c.click(6000);
		assertEquals(64, c.count(6000), "capped by the ring");
	}

	@Test
	void cpsIgnoresScreensAndUsesAttackOnlyAsFallback() {
		CpsModule m = new CpsModule();
		m.attack(10);
		m.setNow(10);
		assertEquals(1, m.leftCps());
		m.mouse(Keys.MOUSE_LEFT, Keys.PRESS, false, 20);
		m.attack(20);
		m.mouse(Keys.MOUSE_LEFT, Keys.PRESS, true, 30);
		m.mouse(Keys.MOUSE_RIGHT, Keys.PRESS, false, 40);
		m.setNow(50);
		assertEquals(2, m.leftCps());
		assertEquals(1, m.rightCps());
		assertTrue(m.rightDown());
	}

	@Test
	void zoomFactorScrollAndSpring() {
		ZoomModule z = new ZoomModule();
		z.smooth.set(false);
		assertEquals(1, z.fovMultiplier(0));
		assertFalse(z.scroll(1), "no scroll when not zoomed");
		z.key.handle(Keys.C, Keys.PRESS);
		assertEquals(0.25, z.fovMultiplier(10), 1e-9);
		assertEquals(0.25, z.sensitivityMultiplier(), 1e-9);
		assertTrue(z.scroll(2));
		assertEquals(4 * 1.44, 1 / z.fovMultiplier(20), 1e-9);
		for (int i = 0; i < 100; i++) z.scroll(1);
		assertEquals(ZoomModule.MAX_ZOOM, 1 / z.fovMultiplier(30), 1e-9);
		for (int i = 0; i < 200; i++) z.scroll(-1);
		assertEquals(1, 1 / z.fovMultiplier(40), 1e-9, "never zooms out past 1");
		z.key.handle(Keys.C, Keys.RELEASE);
		assertEquals(1, z.fovMultiplier(50));
		z.key.handle(Keys.C, Keys.PRESS);
		assertEquals(0.25, z.fovMultiplier(60), 1e-9, "scroll resets on a new zoom");
		z.scaleSensitivity.set(false);
		assertEquals(1, z.sensitivityMultiplier());
	}

	@Test
	void zoomAnimatesSmoothly() {
		ZoomModule z = new ZoomModule();
		z.fovMultiplier(0);
		z.key.handle(Keys.C, Keys.PRESS);
		double mid = 1 / z.fovMultiplier(50);
		assertTrue(mid > 1 && mid < 4, "eased: " + mid);
		long t = 50;
		for (int i = 0; i < 60; i++) z.fovMultiplier(t += 16);
		assertEquals(0.25, z.fovMultiplier(t + 16), 1e-4);
	}

	@Test
	void freelookAngles() {
		FreelookModule f = new FreelookModule();
		f.setEnabled(true);
		assertFalse(f.update(10, 20));
		assertFalse(f.look(100, 100), "not consumed while inactive");
		f.key.handle(Keys.LEFT_ALT, Keys.PRESS);
		assertTrue(f.update(10, 20));
		assertEquals(10, f.yaw());
		assertEquals(20, f.pitch());
		assertTrue(f.update(50, 50), "player rotation no longer drives the camera");
		assertEquals(10, f.yaw());
		assertTrue(f.look(100, -40));
		assertEquals(25, f.yaw(), 1e-4);
		assertEquals(14, f.pitch(), 1e-4);
		f.look(0, 10000);
		assertEquals(90, f.pitch());
		f.invertPitch.set(true);
		f.look(0, 10000);
		assertEquals(-90, f.pitch());
		f.key.handle(Keys.LEFT_ALT, Keys.RELEASE);
		assertFalse(f.update(50, 50));
		f.key.handle(Keys.LEFT_ALT, Keys.PRESS);
		f.update(-170, 5);
		assertEquals(-170, f.yaw(), "restarts from the player's view");
	}

	@Test
	void directions() {
		assertEquals(0, CoordinatesModule.facingIndex(0));
		assertEquals(1, CoordinatesModule.facingIndex(90));
		assertEquals(2, CoordinatesModule.facingIndex(-180));
		assertEquals(3, CoordinatesModule.facingIndex(-90));
		assertEquals(3, CoordinatesModule.facingIndex(630));
		assertEquals(0, CompassModule.bearing(180), 1e-9);
		assertEquals(90, CompassModule.bearing(-90), 1e-9);
		assertEquals(180, CompassModule.bearing(0), 1e-9);
		assertEquals(270, CompassModule.bearing(450), 1e-9);
	}

	@Test
	void formatting() {
		ClockModule c = new ClockModule();
		assertEquals("9:05", c.format(9, 5, 3));
		c.format.set(ClockModule.Format.H12);
		c.seconds.set(true);
		assertEquals("12:00:07 AM", c.format(0, 0, 7));
		assertEquals("1:30:00 PM", c.format(13, 30, 0));
		assertEquals("1:05", PotionEffectsModule.time(65));
		assertEquals("1:00:05", PotionEffectsModule.time(3605));
		assertEquals("Speed II", PotionEffectsModule.title("Speed", 1));
	}

	@Test
	void speedFromPositionDeltas() {
		SpeedModule s = new SpeedModule();
		for (int i = 0; i <= 10; i++) s.tick(i * 0.3, 64 + i, i * 0.4);
		assertEquals(0.5 * 20 * 10 / 10, s.speed(), 1e-9);
		s.tick(1000, 64, 1000);
		assertTrue(s.speed() < 10, "teleports ignored");
	}
}
