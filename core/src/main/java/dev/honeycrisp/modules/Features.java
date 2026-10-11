package dev.honeycrisp.modules;

import dev.honeycrisp.module.ModuleRegistry;

/** Every v1 feature, in mod-menu order. */
public final class Features {
	private Features() {
	}

	public static ModuleRegistry createRegistry() {
		ModuleRegistry m = new ModuleRegistry();
		m.add(new FpsModule());
		CpsModule cps = m.add(new CpsModule());
		m.add(new KeystrokesModule(cps));
		m.add(new CoordinatesModule());
		m.add(new CompassModule());
		m.add(new PingModule());
		m.add(new ArmorStatusModule());
		m.add(new PotionEffectsModule());
		m.add(new ClockModule());
		m.add(new ServerAddressModule());
		m.add(new SpeedModule());
		m.add(new ToggleSprintModule());
		m.add(new ToggleSneakModule());
		m.add(new OldAnimationsModule());
		m.add(new HitColorModule());
		m.add(new CrosshairModule());
		m.add(new ZoomModule());
		m.add(new FreelookModule());
		m.add(new FullbrightModule());
		m.add(new TimeChangerModule());
		m.add(new ItemPhysicsModule());
		m.add(new ChatModule());
		m.add(new ScoreboardModule());
		m.add(new ClientModule());
		return m;
	}
}
