package aaaa0ggmc.ulmodpack.proxy;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.block.BlockBittersweetOre;
import aaaa0ggmc.ulmodpack.block.BlockShelfOfPieces;
import aaaa0ggmc.ulmodpack.block.BlockTitanStatue;
import aaaa0ggmc.ulmodpack.config.PackConfig;
import aaaa0ggmc.ulmodpack.dna.BlockDnaRecombinator;
import aaaa0ggmc.ulmodpack.dna.ItemDnaSwab;
import aaaa0ggmc.ulmodpack.fluid.BlockFluidLethe;
import aaaa0ggmc.ulmodpack.fluid.FluidLethe;
import aaaa0ggmc.ulmodpack.infection.UlTriggers;
import aaaa0ggmc.ulmodpack.item.ItemAnkhAmulet;
import aaaa0ggmc.ulmodpack.item.ItemDreamMaterial;
import aaaa0ggmc.ulmodpack.item.ItemLetheSoup;
import aaaa0ggmc.ulmodpack.item.ItemPieces;
import aaaa0ggmc.ulmodpack.item.ItemSleepingPill;
import aaaa0ggmc.ulmodpack.item.ItemTasteAgent;
import aaaa0ggmc.ulmodpack.item.ItemTitanCrown;
import aaaa0ggmc.ulmodpack.item.ItemZealousFlesh;
import aaaa0ggmc.ulmodpack.potion.PotionDeepSleep;
import aaaa0ggmc.ulmodpack.potion.PotionOblivion;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    /** 整合包定制物品的创造标签页（后续物品都挂这里）。 */
    public static final CreativeTabs TAB = new CreativeTabs(UlModpack.MODID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(ANKH_AMULET);
        }
    };

    public static final ItemAnkhAmulet ANKH_AMULET = new ItemAnkhAmulet();

    /** 热忱的腐肉：泰坦王冠合成材料；僵尸形态可食用回血。 */
    public static final ItemZealousFlesh ZEALOUS_FLESH = new ItemZealousFlesh();

    /** 泰坦王冠：僵尸形态佩戴变身泰坦，摘下退回原形态。 */
    public static final ItemTitanCrown TITAN_CROWN = new ItemTitanCrown();

    /** 泰坦金色雕塑：两格高的支线纪念收藏品。 */
    public static final BlockTitanStatue TITAN_STATUE = new BlockTitanStatue();
    public static final ItemBlock TITAN_STATUE_ITEM = (ItemBlock) new ItemBlock(TITAN_STATUE)
            .setRegistryName(TITAN_STATUE.getRegistryName());

    /** 安眠药 & 安眠药水效果：通用种族回撤机制。 */
    public static final PotionDeepSleep POTION_DEEP_SLEEP = new PotionDeepSleep();
    public static final ItemSleepingPill SLEEPING_PILL = new ItemSleepingPill();

    /** 孟婆汤（Water of Lethe）流体、方块、碗装成品与「遗忘」药水效果。 */
    public static final FluidLethe FLUID_LETHE = new FluidLethe();
    static {
        FluidRegistry.registerFluid(FLUID_LETHE);
        FluidRegistry.addBucketForFluid(FLUID_LETHE);
    }
    public static final BlockFluidLethe LETHE_WATER_BLOCK = new BlockFluidLethe(FLUID_LETHE);
    public static final ItemLetheSoup LETHE_SOUP = new ItemLetheSoup();
    public static final PotionOblivion POTION_OBLIVION = new PotionOblivion();

    /** Pieces 碎屑 & 碎屑书架（附魔效用 = 4 个书架；非精准采集掉落 3–9 个 Pieces）。 */
    public static final ItemPieces PIECES = new ItemPieces();
    public static final BlockShelfOfPieces SHELF_OF_PIECES = new BlockShelfOfPieces();
    public static final ItemBlock SHELF_OF_PIECES_ITEM = (ItemBlock) new ItemBlock(SHELF_OF_PIECES)
            .setRegistryName(SHELF_OF_PIECES.getRegistryName());

    /** 甘苦矿（主世界生成）与双生掉落物：甜蜜素 & 苦味剂。 */
    public static final BlockBittersweetOre BITTERSWEET_ORE = new BlockBittersweetOre();
    public static final ItemBlock BITTERSWEET_ORE_ITEM = (ItemBlock) new ItemBlock(BITTERSWEET_ORE)
            .setRegistryName(BITTERSWEET_ORE.getRegistryName());
    public static final ItemTasteAgent SWEETENER = new ItemTasteAgent(ItemTasteAgent.Kind.SWEETENER);
    public static final ItemTasteAgent BITTERANT = new ItemTasteAgent(ItemTasteAgent.Kind.BITTERANT);

    /** 美梦 & 噩梦：8 Pieces 包裹甜蜜素/苦味剂合成的机器核心原料。 */
    public static final ItemDreamMaterial SWEET_DREAM = new ItemDreamMaterial("sweet_dream");
    public static final ItemDreamMaterial NIGHTMARE = new ItemDreamMaterial("nightmare");

    /** 采样棉签 & DNA 重组机（FE/RF 供电）。 */
    public static final ItemDnaSwab DNA_SWAB = new ItemDnaSwab();
    public static final BlockDnaRecombinator DNA_RECOMBINATOR = new BlockDnaRecombinator();
    public static final ItemBlock DNA_RECOMBINATOR_ITEM = (ItemBlock) new ItemBlock(DNA_RECOMBINATOR)
            .setRegistryName(DNA_RECOMBINATOR.getRegistryName());

    public void preInit(FMLPreInitializationEvent event) {
        // 通用机制开关（input/按键状态自愈等）先加载，客户端处理器读到的才是生效值；
        // 双端加载，PackConfig 不引用任何客户端类
        PackConfig.init(event.getSuggestedConfigurationFile());
        UlTriggers.init();
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    /**
     * S2C 尸群队伍快照（{@code HordeNetwork.TeamHandler} 中转）。服务端代理空实现；
     * 客户端覆写写入 {@code HordeTeamCache}（B 键状态页数据源）。
     * 专用服务器侧不得在此引用任何客户端类。
     */
    public void onHordeSnapshot(String json) {
    }
}
