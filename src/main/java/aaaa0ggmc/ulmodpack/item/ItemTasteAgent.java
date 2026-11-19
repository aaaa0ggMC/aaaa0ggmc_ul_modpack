package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.UlModpackContainer;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 甜蜜素（Sweetener）与苦味剂（Bitterant）：
 * <ul>
 *   <li>甜蜜素：食用获得生命恢复 I（8s），结束后触发中毒 I（6s）；</li>
 *   <li>苦味剂：食用获得力量 I（12s），结束后触发中毒 I（6s）。</li>
 * </ul>
 * 主要作为后续变形相关机器的合成材料。
 */
public class ItemTasteAgent extends ItemFood {

    public enum Kind {
        SWEETENER("sweetener"),
        BITTERANT("bitterant");

        public final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    private final Kind kind;

    public ItemTasteAgent(Kind kind) {
        super(1, 0.2F, false);
        this.kind = kind;
        setRegistryName(UlModpack.MODID, kind.id);
        setTranslationKey(UlModpack.MODID + "." + kind.id);
        setAlwaysEdible();
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 20;
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
        super.onFoodEaten(stack, world, player);
        if (world.isRemote) {
            return;
        }
        int poisonTicks = Math.max(20, UlModpackContainer.instance().strengthService()
                .integer("taste_agent.poison_ticks", 120));
        if (kind == Kind.SWEETENER) {
            int regenTicks = Math.max(20, UlModpackContainer.instance().strengthService()
                    .integer("taste_agent.sweetener_regen_ticks", 160));
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, regenTicks, 0, false, true));
            TasteAgentHandler.scheduleAfterEffectPoison(player, true, regenTicks, poisonTicks);
        } else {
            int strTicks = Math.max(20, UlModpackContainer.instance().strengthService()
                    .integer("taste_agent.bitterant_strength_ticks", 240));
            player.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, strTicks, 0, false, true));
            TasteAgentHandler.scheduleAfterEffectPoison(player, false, strTicks, poisonTicks);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack." + kind.id + ".lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack." + kind.id + ".lore2"));
    }
}
