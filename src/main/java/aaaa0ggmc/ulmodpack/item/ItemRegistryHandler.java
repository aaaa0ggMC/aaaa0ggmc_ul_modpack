package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.potion.Potion;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;

import javax.annotation.Nullable;

/** 物品/方块/药水/配方注册（1.12.2 走 RegistryEvent.Register，构造期自动订阅，时机早于注册事件）。 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public class ItemRegistryHandler {

    private ItemRegistryHandler() {
    }

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(CommonProxy.TITAN_STATUE);
        event.getRegistry().register(CommonProxy.LETHE_WATER_BLOCK);
        event.getRegistry().register(CommonProxy.SHELF_OF_PIECES);
        event.getRegistry().register(CommonProxy.BITTERSWEET_ORE);
        event.getRegistry().register(CommonProxy.DNA_RECOMBINATOR);
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                aaaa0ggmc.ulmodpack.dna.TileEntityDnaRecombinator.class,
                new ResourceLocation(UlModpack.MODID, "dna_recombinator"));
    }

    @SubscribeEvent
    public static void onRegisterPotions(RegistryEvent.Register<Potion> event) {
        event.getRegistry().register(CommonProxy.POTION_DEEP_SLEEP);
        event.getRegistry().register(CommonProxy.POTION_OBLIVION);
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(CommonProxy.ANKH_AMULET);
        event.getRegistry().register(CommonProxy.ZEALOUS_FLESH);
        event.getRegistry().register(CommonProxy.TITAN_CROWN);
        event.getRegistry().register(CommonProxy.TITAN_STATUE_ITEM);
        event.getRegistry().register(CommonProxy.SLEEPING_PILL);
        event.getRegistry().register(CommonProxy.LETHE_SOUP);
        event.getRegistry().register(CommonProxy.PIECES);
        event.getRegistry().register(CommonProxy.SHELF_OF_PIECES_ITEM);
        event.getRegistry().register(CommonProxy.BITTERSWEET_ORE_ITEM);
        event.getRegistry().register(CommonProxy.SWEETENER);
        event.getRegistry().register(CommonProxy.BITTERANT);
        event.getRegistry().register(CommonProxy.SWEET_DREAM);
        event.getRegistry().register(CommonProxy.NIGHTMARE);
        event.getRegistry().register(CommonProxy.DNA_SWAB);
        event.getRegistry().register(CommonProxy.REVERSAL_GEM);
        event.getRegistry().register(CommonProxy.WEDDING_RING);
        event.getRegistry().register(CommonProxy.DNA_RECOMBINATOR_ITEM);
    }

    @SubscribeEvent
    public static void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
        ItemStack letheBucket = FluidUtil.getFilledBucket(
                new FluidStack(CommonProxy.FLUID_LETHE, Fluid.BUCKET_VOLUME));
        if (!letheBucket.isEmpty()) {
            ResourceLocation bucketGroup = new ResourceLocation(UlModpack.MODID, "lethe_bucket");
            ShapedOreRecipe bucketRecipe = new ShapedOreRecipe(bucketGroup, letheBucket,
                    "PPP",
                    "PBP",
                    "PPP",
                    'P', CommonProxy.SLEEPING_PILL,
                    'B', Items.WATER_BUCKET) {
                @Override
                public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
                    // 水桶直接变成孟婆汤桶，不额外留下空桶
                    return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
                }
            };
            bucketRecipe.setRegistryName(bucketGroup);
            event.getRegistry().register(bucketRecipe);

            // 8 桶孟婆汤 + 1 书架 -> 1 碎屑书架（返还 8 个空桶）
            ResourceLocation shelfGroup = new ResourceLocation(UlModpack.MODID, "shelf_of_pieces");
            Ingredient letheBucketIng = new Ingredient(letheBucket) {
                @Override
                public boolean apply(@Nullable ItemStack input) {
                    if (input == null || input.isEmpty()) {
                        return false;
                    }
                    FluidStack fs = FluidUtil.getFluidContained(input);
                    return fs != null && fs.getFluid() == CommonProxy.FLUID_LETHE
                            && fs.amount >= Fluid.BUCKET_VOLUME;
                }
            };
            ShapedOreRecipe shelfRecipe = new ShapedOreRecipe(shelfGroup,
                    new ItemStack(CommonProxy.SHELF_OF_PIECES_ITEM),
                    "LLL",
                    "LBL",
                    "LLL",
                    'L', letheBucketIng,
                    'B', Blocks.BOOKSHELF) {
                @Override
                public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
                    NonNullList<ItemStack> rem = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        ItemStack s = inv.getStackInSlot(i);
                        FluidStack fs = FluidUtil.getFluidContained(s);
                        if (fs != null && fs.getFluid() == CommonProxy.FLUID_LETHE) {
                            rem.set(i, new ItemStack(Items.BUCKET));
                        }
                    }
                    return rem;
                }
            };
            shelfRecipe.setRegistryName(shelfGroup);
            event.getRegistry().register(shelfRecipe);
        }
    }
}
