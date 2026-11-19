package aaaa0ggmc.ulmodpack.dna;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 采样棉签（DNA Swab）：2 羊毛 + 1 木棍合成。
 * 右键白名单动物可采集其 DNA，放入 DNA 重组机使用。
 */
public class ItemDnaSwab extends Item {

    public static final String NBT_ENTITY_ID = "ulDnaEntity";
    public static final String NBT_ENTITY_NAME = "ulDnaName";

    public ItemDnaSwab() {
        setRegistryName(UlModpack.MODID, "dna_swab");
        setTranslationKey(UlModpack.MODID + ".dna_swab");
        setMaxStackSize(16);
        setCreativeTab(CommonProxy.TAB);
    }

    @Nullable
    public static String getSampledEntityId(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) {
            return null;
        }
        String id = stack.getTagCompound().getString(NBT_ENTITY_ID);
        return id.isEmpty() ? null : id;
    }

    public static boolean hasDna(ItemStack stack) {
        return getSampledEntityId(stack) != null;
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player,
                                            EntityLivingBase target, EnumHand hand) {
        if (hasDna(stack)) {
            if (!player.world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("ul.dna_swab.already_sampled"), true);
            }
            return true;
        }
        ResourceLocation key = EntityList.getKey(target);
        if (key == null || !DnaRecipeRegistry.isWhitelisted(key.toString())) {
            if (!player.world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("ul.dna_swab.not_whitelisted"), true);
            }
            return true;
        }

        if (!player.world.isRemote) {
            ItemStack filled = new ItemStack(this, 1);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(NBT_ENTITY_ID, key.toString());
            tag.setString(NBT_ENTITY_NAME, target.getName());
            filled.setTagCompound(tag);

            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            if (stack.isEmpty()) {
                player.setHeldItem(hand, filled);
            } else if (!player.inventory.addItemStackToInventory(filled)) {
                player.dropItem(filled, false);
            }

            player.world.playSound(null, target.posX, target.posY, target.posZ,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7F, 1.35F);
            player.sendStatusMessage(
                    new TextComponentTranslation("ul.dna_swab.sampled", target.getName()), true);
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return hasDna(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        String entityId = getSampledEntityId(stack);
        if (entityId == null) {
            tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.dna_swab.empty_lore"));
            return;
        }
        String name = stack.getTagCompound().getString(NBT_ENTITY_NAME);
        if (name.isEmpty()) {
            name = entityId;
        }
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.dna_swab.sample_line", name));
        DnaRecipe recipe = DnaRecipeRegistry.get(entityId);
        if (recipe != null) {
            StringBuilder reqSb = new StringBuilder();
            for (int i = 0; i < recipe.required.size(); i++) {
                DnaRecipe.ItemRequirement r = recipe.required.get(i);
                if (i > 0) {
                    reqSb.append(", ");
                }
                reqSb.append(r.displayName()).append(" x").append(r.count);
            }
            tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.dna_swab.req_line", reqSb.toString()));
            if (!recipe.optional.isEmpty()) {
                StringBuilder optSb = new StringBuilder();
                for (int i = 0; i < recipe.optional.size(); i++) {
                    DnaRecipe.ItemRequirement r = recipe.optional.get(i).requirement;
                    if (i > 0) {
                        optSb.append(", ");
                    }
                    optSb.append(r.displayName()).append(" x").append(r.count);
                }
                tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.dna_swab.opt_line", optSb.toString()));
            }
        }
    }
}
