package aaaa0ggmc.ulmodpack;

import aaaa0ggmc.uld.IStrengthService;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeManager;
import aaaa0ggmc.ulmodpack.zombie.horde.Persuasion;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanFormHandler;

import java.util.Objects;

/**
 * aaaa0ggmc_ul_modpack DI 组合根。
 *
 * <p>在模组初始化期集中接管对外部 {@link IStrengthService} 的依赖注入，
 * 避免业务类直接硬编码调用 {@code UlStrength.*} 静态门面。</p>
 */
public final class UlModpackContainer {

    private static volatile UlModpackContainer instance = new UlModpackContainer(UlStrength.service());

    private final IStrengthService strengthService;

    public UlModpackContainer(IStrengthService strengthService) {
        this.strengthService = Objects.requireNonNull(strengthService, "strengthService");
    }

    public static UlModpackContainer instance() {
        return instance;
    }

    public static void init(IStrengthService strengthService) {
        UlModpackContainer container = new UlModpackContainer(strengthService);
        instance = container;
        HordeManager.setStrengthService(container.strengthService);
        Persuasion.setStrengthService(container.strengthService);
        TitanFormHandler.setStrengthService(container.strengthService);
    }

    public IStrengthService strengthService() {
        return strengthService;
    }
}
