package dev.honeycrisp.v1_12_2.coremod;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/** After Graft's plugin (1001), so both see deobfuscated class names and SRG member names. */
@IFMLLoadingPlugin.Name("Honeycrisp Client")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions("dev.honeycrisp.v1_12_2.coremod.")
public final class HoneycrispPlugin implements IFMLLoadingPlugin {
	@Override
	public String[] getASMTransformerClass() {
		return new String[] {"dev.honeycrisp.v1_12_2.coremod.Transformer"};
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
