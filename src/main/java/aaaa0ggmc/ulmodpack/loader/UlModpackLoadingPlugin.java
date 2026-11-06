package aaaa0ggmc.ulmodpack.loader;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FML coremod 插件 + MixinBooter 早期 mixin 加载（与 morphext
 * {@code MorphExtLoadingPlugin} 同一模式：IFMLLoadingPlugin + IEarlyMixinLoader，
 * 客户端与 dedicated server 实测可用的组合；loader 类放 mixin 包之外，
 * 避免被 MixinTransformer 拦截）。
 *
 * <p>本插件承载「原版生物硬编码 EntityPlayer 检测 → morphext 统一仇恨接口」的
 * 逐生物注入（见 {@code mixins.ulmodpack.json}）。</p>
 */
@Name("UlModpackCore")
@MCVersion("1.12.2")
@SortingIndex(Integer.MIN_VALUE)
public class UlModpackLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
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

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ulmodpack.json");
    }
}
