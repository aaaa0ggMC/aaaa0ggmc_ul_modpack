package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.client.model.ModelTitanCrown;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 泰坦王冠：头盔槽位装备（护甲/韧性同钻石头盔）。
 *
 * <p>在僵尸/不死者（{@code ul:undead}）形态且满足尸群领导与尸潮胜利前置时，
 * 戴上即可化身泰坦（{@code minecraft:giant}），摘下则退回原僵尸形态。
 * 实际变身/退回逻辑由 {@code TitanFormHandler} 监听装备变化与状态驱动。</p>
 */
public class ItemTitanCrown extends ItemArmor {

    public static final ArmorMaterial MATERIAL = EnumHelper.addArmorMaterial(
            "TITAN_CROWN",
            UlModpack.MODID + ":titan_crown",
            33,
            new int[]{3, 6, 8, 3},
            25,
            SoundEvents.ITEM_ARMOR_EQUIP_GOLD,
            2.0F
    );

    @SideOnly(Side.CLIENT)
    private static ModelTitanCrown crownModel;

    public ItemTitanCrown() {
        super(MATERIAL, 0, EntityEquipmentSlot.HEAD);
        setRegistryName(UlModpack.MODID, "titan_crown");
        setTranslationKey(UlModpack.MODID + ".titan_crown");
        setCreativeTab(CommonProxy.TAB);
        setMaxStackSize(1);
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return UlModpack.MODID + ":textures/models/armor/titan_crown_layer_1.png";
    }

    @Nullable
    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack,
                                    EntityEquipmentSlot armorSlot, ModelBiped _default) {
        if (armorSlot != EntityEquipmentSlot.HEAD) {
            return null;
        }
        if (crownModel == null) {
            crownModel = new ModelTitanCrown();
        }
        if (_default != null) {
            crownModel.isSneak = _default.isSneak;
            crownModel.isRiding = _default.isRiding;
            crownModel.isChild = _default.isChild;
            crownModel.swingProgress = _default.swingProgress;
        }
        return crownModel;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.titan_crown.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.titan_crown.lore2"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.titan_crown.lore3"));
    }
}
