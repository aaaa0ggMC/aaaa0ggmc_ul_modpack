package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import baubles.api.BaubleType;
import baubles.api.IBauble;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 安卡护符：Baubles AMULET（脖子）槽位。
 *
 * 佩戴时 +2 护甲；被僵尸一族杀死时由 {@link aaaa0ggmc.ulmodpack.infection.InfectionHandler}
 * 消耗并触发「感染变身」。
 */
public class ItemAnkhAmulet extends Item implements IBauble {

    /** 固定 UUID：重复套用/移除幂等，玩家重生后残留的修饰符也能按名清掉。 */
    public static final UUID ARMOR_UUID = UUID.fromString("2f6c9b4e-6d5a-4a3f-9c0e-7b2d8a1e5c33");
    public static final String ARMOR_NAME = "aaaa0ggmc_ul_modpack.ankh_armor";
    public static final double ARMOR_AMOUNT = 2.0D;

    public ItemAnkhAmulet() {
        setRegistryName(UlModpack.MODID, "ankh_amulet");
        // stable_39 下显示名 key = "item." + translationKey（即 MCP 的 setUnlocalizedName）
        setTranslationKey(UlModpack.MODID + ".ankh_amulet");
        setMaxStackSize(1);
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.AMULET;
    }

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase player) {
        applyArmor(player);
    }

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {
        removeArmor(player);
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {
        // 自愈式维护：无论经由哪条路径装备/卸下（含被感染碎裂时的直接摘除），
        // 护甲修饰符都精确地“佩戴存在、摘下消失”。
        applyArmor(player);
    }

    /** 服务端套上护甲修饰符（已存在则不动）。 */
    public static void applyArmor(EntityLivingBase living) {
        if (!(living instanceof EntityPlayer) || living.world.isRemote) {
            return;
        }
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        if (attr == null || attr.getModifier(ARMOR_UUID) != null) {
            return;
        }
        attr.applyModifier(new AttributeModifier(ARMOR_UUID, ARMOR_NAME, ARMOR_AMOUNT, 0));
    }

    /** 服务端移除护甲修饰符（不存在则不动）。 */
    public static void removeArmor(EntityLivingBase living) {
        if (!(living instanceof EntityPlayer) || living.world.isRemote) {
            return;
        }
        IAttributeInstance attr = living.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        if (attr == null) {
            return;
        }
        AttributeModifier modifier = attr.getModifier(ARMOR_UUID);
        if (modifier != null) {
            attr.removeModifier(modifier);
        }
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.ankh.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.ankh.lore2"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.ankh.lore3"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.ankh.lore4"));
    }
}
