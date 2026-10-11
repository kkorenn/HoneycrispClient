package dev.honeycrisp.module;

import dev.honeycrisp.modules.CrosshairModule;
import dev.honeycrisp.modules.Features;
import dev.honeycrisp.modules.ZoomModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
	@Test
	void roundTripThroughFile(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("honeycrisp/config.json");
		ModuleRegistry a = Features.createRegistry();
		Config ca = new Config(file, a);
		ZoomModule zoom = a.get(ZoomModule.class);
		zoom.factor.set(7.5);
		zoom.key.setMode(KeybindSetting.Mode.TOGGLE);
		zoom.setEnabled(false);
		CrosshairModule ch = a.get(CrosshairModule.class);
		ch.setEnabled(true);
		ch.style.set(CrosshairModule.Style.CIRCLE);
		ch.color.set(0x80FF00FF);
		ch.color.setChroma(true);
		HudModule fps = (HudModule) a.byId("fps");
		fps.anchor.set(Anchor.BOTTOM_RIGHT);
		fps.offsetX.set(-12.5);
		assertTrue(ca.dirty());
		ca.save();
		assertFalse(ca.dirty());

		ModuleRegistry b = Features.createRegistry();
		Config cb = new Config(file, b);
		cb.load();
		assertFalse(cb.dirty(), "loading must not schedule a save");
		assertEquals(ca.toJson(), cb.toJson());
		assertEquals(7.5, b.get(ZoomModule.class).factor.value());
		assertEquals(KeybindSetting.Mode.TOGGLE, b.get(ZoomModule.class).key.mode());
		assertFalse(b.get(ZoomModule.class).enabled());
		assertTrue(b.get(CrosshairModule.class).color.chroma());
		assertEquals(0x80FF00FF, b.get(CrosshairModule.class).color.rawArgb());
		assertEquals(Anchor.BOTTOM_RIGHT, ((HudModule) b.byId("fps")).anchor.get());
	}

	@Test
	void toleratesUnknownMissingAndWrongTypes() {
		ModuleRegistry r = Features.createRegistry();
		Config c = new Config(Path.of("unused"), r);
		c.fromJson("{\"version\": 99, \"extra\": [1, 2], \"modules\": {"
				+ "\"gone\": {\"enabled\": true},"
				+ "\"zoom\": {\"factor\": \"big\", \"smooth\": 3, \"key\": {\"mode\": \"NOPE\"}, \"newThing\": null},"
				+ "\"crosshair\": {\"enabled\": true, \"size\": 999, \"style\": \"dot\"},"
				+ "\"fps\": 5}}");
		ZoomModule z = r.get(ZoomModule.class);
		assertEquals(4.0, z.factor.value());
		assertTrue(z.smooth.on());
		CrosshairModule ch = r.get(CrosshairModule.class);
		assertTrue(ch.enabled());
		assertEquals(15.0, ch.size.value(), "clamped to max");
		assertEquals(CrosshairModule.Style.DOT, ch.style.get());
	}

	@Test
	void corruptFileKeepsDefaultsAndBacksUp(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config.json");
		Files.write(file, "{\"modules\": {".getBytes());
		ModuleRegistry r = Features.createRegistry();
		new Config(file, r).load();
		assertTrue(r.get(ZoomModule.class).enabled());
		assertTrue(Files.exists(dir.resolve("config.json.bad")));
	}

	@Test
	void jsonCodec() {
		Object v = Json.parse(" {\"a\": [1, -2.5e2, true, null], \"s\": \"q\\\"\\\\\\n\\u00e9\\t\", \"o\": {}} ");
		Map<?, ?> m = (Map<?, ?>) v;
		assertEquals(List.of(1.0, -250.0, true), ((List<?>) m.get("a")).subList(0, 3));
		assertNull(((List<?>) m.get("a")).get(3));
		assertEquals("q\"\\\né\t", m.get("s"));
		assertEquals(v, Json.parse(Json.write(v)));
		assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1,}"));
		assertThrows(IllegalArgumentException.class, () -> Json.parse("[1] x"));
	}

	@Test
	void numberSettingStepsAndClamps() {
		NumberSetting n = new NumberSetting("n", "N", 1, 0.5, 3, 0.05);
		n.set(1.234);
		assertEquals(1.25, n.value());
		n.set(-4);
		assertEquals(0.5, n.value());
		n.set(Double.NaN);
		assertEquals(0.5, n.value());
	}

	@Test
	void keybindHoldAndToggle() {
		KeybindSetting k = new KeybindSetting("k", "K", 67, KeybindSetting.Mode.TOGGLE);
		assertFalse(k.handle(68, 1));
		k.handle(67, 1);
		k.handle(67, 2); // repeat doesn't flip
		k.handle(67, 0);
		assertTrue(k.active());
		k.handle(67, 1);
		assertFalse(k.active());
		k.setMode(KeybindSetting.Mode.HOLD);
		assertTrue(k.active(), "still physically down");
		k.handle(67, 0);
		assertFalse(k.active());
	}
}
