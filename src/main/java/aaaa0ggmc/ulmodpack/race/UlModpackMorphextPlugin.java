package aaaa0ggmc.ulmodpack.race;

import aaaa0ggmc.morphext.spi.IMorphextPlugin;
import aaaa0ggmc.morphext.spi.MorphextPluginContext;
import aaaa0ggmc.ulmodpack.UlModpack;

/**
 * ul 包接入 morphext 核心 SPI 的插件（{@code META-INF/services} 自动发现）。
 *
 * <p>作用：把 {@link RaceManager} 注册成核心唯一形态写入点
 * （{@code MorphTransitionService}）的观察者，让核心侧发生的形态变更
 * （{@code RuleExecutor} 的死亡转化、{@code MetamorphServerAbilities} 的能力授予/收回形态）
 * 也经过整合包的决策——即「打断临时状态」（清临时形态栈 / 清安眠药效 / 清 DNA pending）。</p>
 *
 * <p>依赖方向：内容层 → 核心。核心只暴露 SPI，不引用本类
 * （AGENTS.md §1.2；docs/dna-recombinator-design.md §2.3）。</p>
 *
 * <p>时序：{@code MorphextContainer.firePreInit()} 在 FML PreInit 调用，早于任何
 * {@code RuleExecutor} 执行，满足「监听器须在首次执行前注册」的要求（§2.7 代价 2）。</p>
 */
public final class UlModpackMorphextPlugin implements IMorphextPlugin {

    @Override
    public String id() {
        return "aaaa0ggmc_ul_modpack:race_transition";
    }

    @Override
    public void onPreInit(MorphextPluginContext context) {
        context.registerMorphTransitionListener(RaceManager.coreListener());
        UlModpack.LOGGER.info("[RaceManager] registered core morph-transition listener (plugin={})", id());
    }
}
