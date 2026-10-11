package dev.honeycrisp.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpringTest {
	@Test
	void convergesWithoutOvershoot() {
		Spring s = new Spring(16, 1);
		s.target = 4;
		double prev = 1;
		for (int i = 0; i < 120; i++) {
			double v = s.update(1 / 60.0);
			assertTrue(v >= prev - 1e-9 && v <= 4 + 1e-9, "monotonic, no overshoot at frame " + i);
			prev = v;
		}
		assertEquals(4, s.value, 1e-6);
		assertEquals(0, s.velocity, 1e-3);
	}

	@Test
	void frameRateIndependent() {
		Spring a = new Spring(10, 0), b = new Spring(10, 0);
		a.target = b.target = 1;
		for (int i = 0; i < 30; i++) a.update(1 / 60.0);
		for (int i = 0; i < 4; i++) b.update(0.125);
		assertEquals(a.value, b.value, 1e-9);
	}

	@Test
	void hugeStepIsStable() {
		Spring s = new Spring(16, 1);
		s.target = 0;
		assertEquals(0, s.update(100), 1e-9);
	}
}
