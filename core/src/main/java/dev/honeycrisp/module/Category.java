package dev.honeycrisp.module;

public enum Category {
	HUD("HUD", "rectangle.3.group"),
	PVP("PvP", "figure.fencing"),
	CAMERA("Camera", "camera"),
	VISUAL("Visual", "eye"),
	COSMETICS("Cosmetics", "tshirt"),
	GENERAL("General", "gearshape");

	public final String displayName;
	public final String sfSymbol;

	Category(String displayName, String sfSymbol) {
		this.displayName = displayName;
		this.sfSymbol = sfSymbol;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
