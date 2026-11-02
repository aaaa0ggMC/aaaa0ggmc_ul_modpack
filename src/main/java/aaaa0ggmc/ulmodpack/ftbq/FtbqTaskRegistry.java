package aaaa0ggmc.ulmodpack.ftbq;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import com.feed_the_beast.ftblib.lib.icon.ItemIcon;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * FTBQuests 任务类型注册。
 *
 * TaskType 是 FTBQ 的 Forge 自定义注册表条目；@Mod.EventBusSubscriber 在
 * 模组构造期即完成事件订阅，早于任何 RegistryEvent，时序安全。
 * 注册名用全名 aaaa0ggmc_ul_modpack:morph（FTBQ 的 createTask 对带命名空间的
 * 类型串原样解析，任务文件里必须写全名）。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public class FtbqTaskRegistry {

    public static TaskType MORPH;

    private FtbqTaskRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterTasks(RegistryEvent.Register<TaskType> event) {
        MORPH = new TaskType(MorphTask::new);
        MORPH.setRegistryName(UlModpack.MODID, "morph");
        // 任务图标 = 物品图标（Icon.getIcon(String) 会把裸字符串当图片路径解析成 missing texture）
        MORPH.setIcon(ItemIcon.getItemIcon(CommonProxy.ANKH_AMULET));
        event.getRegistry().register(MORPH);
        UlModpack.LOGGER.info("[ul-modpack] FTBQuests task type registered: {}", MORPH.getRegistryName());
    }
}
