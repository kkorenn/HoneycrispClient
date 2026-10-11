package dev.honeycrisp.v1_7_10.coremod;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/** After Graft's plugin (1001), so both see deobfuscated class names and SRG member names. */
@IFMLLoadingPlugin.Name("Honeycrisp Client")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions("dev.honeycrisp.v1_7_10.coremod.")
public final class HoneycrispPlugin implements IFMLLoadingPlugin {
	@Override
	public String[] getASMTransformerClass() {
		return new String[] {"dev.honeycrisp.v1_7_10.coremod.Transformer"};
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
