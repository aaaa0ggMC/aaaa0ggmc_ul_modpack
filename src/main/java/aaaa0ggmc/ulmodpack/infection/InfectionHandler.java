package aaaa0ggmc.ulmodpack.infection;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.item.ItemAnkhAmulet;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.google.gson.JsonObject;
import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.network.AbilityNetwork;

/**
 * 感染变身：佩戴安卡护符的玩家被僵尸一族（EntityZombie 及其子类：僵尸、尸壳、
 * 僵尸村民、僵尸猪人、巨人）杀死时——
 *
 *  1. 取消死亡（无死亡界面、无掉落、无死亡消息）；
 *  2. 化身为击杀者的形态（Metamorph，获得 + 强制变身）；
 *  3. 回满生命（形态会改写最大生命，故在变身之后回血）；
 *  4. 安卡护符碎裂，粒子/音效/文案齐发；
 *  5. 解锁进度「被感染」，并在 morphext 玩家数据里记录一条感染状态。
 */
public class InfectionHandler {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new InfectionHandler());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        Entity killer = resolveKiller(event.getSource());
        if (!(killer instanceof EntityZombie)) {
            return;
        }
        if (!consumeAnkh(player)) {
            return;
        }
        // 取消死亡：EntityPlayerMP.onDeath 整个包在 ForgeHooks.onLivingDeath 里，
        // 取消即无死亡界面/无掉落/无死亡消息（1.12.2 Forge 语义）。
        event.setCanceled(true);
        UlModpack.LOGGER.info("[ul-modpack] Ankh triggered for {} (slain by {})",
                player.getName(), EntityList.getKey(killer));
        infect(player, killer, true);
    }

    /**
     * 把玩家从死亡线上拉回并「感染」成给定实体的形态。
     * 死亡事件与调试指令共用同一路径。
     */
    public static void infect(EntityPlayerMP player, Entity formSource, boolean fromDeath) {
        if (fromDeath) {
            player.getCombatTracker().reset();
            player.extinguish();
            player.fallDistance = 0.0F;
        }

        AbstractMorph morph = buildMorph(formSource);
        if (morph != null) {
            // 先拿到形态再变身：acquire 让玩家永久拥有该形态，morph 强制切换
            MorphAPI.acquire(player, morph);
            MorphAPI.morph(player, morph, true);
        } else {
            UlModpack.LOGGER.warn("[ul-modpack] No morph available for {} — rescue continues without transformation",
                    EntityList.getKey(formSource));
        }

        // 形态会改写最大生命，变身之后回满
        player.setHealth(player.getMaxHealth());

        spawnInfectionEffects(player);

        if (UlTriggers.INFECTED != null) {
            UlTriggers.INFECTED.trigger(player);
        }

        player.sendMessage(new TextComponentTranslation("aaaa0ggmc_ul_modpack.msg.shatter"));
        player.sendStatusMessage(new TextComponentTranslation("aaaa0ggmc_ul_modpack.msg.infected", formSource.getName()), true);

        recordInfectionState(player, formSource, morph);
    }

    /** 摘掉一个安卡护符（找到即消耗一个并清理护甲修饰符）。 */
    public static boolean consumeAnkh(EntityPlayerMP player) {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == CommonProxy.ANKH_AMULET) {
                ItemStack rest = stack.copy();
                rest.shrink(1);
                handler.setStackInSlot(slot, rest);
                ItemAnkhAmulet.removeArmor(player);
                return true;
            }
        }
        return false;
    }

    /** 从击杀者实体构建 Metamorph 形态（与 Metamorph 自身击杀获取形态同一套路径）。 */
    public static AbstractMorph buildMorph(Entity formSource) {
        try {
            String name = MorphManager.INSTANCE.morphNameFromEntity(formSource);
            if (name == null || !MorphManager.INSTANCE.hasMorph(name)) {
                return null;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Name", name);
            tag.setTag("EntityData", EntityUtils.stripEntityNBT(formSource.serializeNBT()));
            return MorphManager.INSTANCE.morphFromNBT(tag);
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Failed to build morph from {}: {}",
                    EntityList.getKey(formSource), t.toString());
            return null;
        }
    }

    /** 感染记录写进 morphext 玩家数据（失败不影响主流程）。 */
    private static void recordInfectionState(EntityPlayerMP player, Entity formSource, AbstractMorph morph) {
        try {
            JsonObject state = new JsonObject();
            state.addProperty("form", morph == null || morph.name == null ? "" : morph.name);
            state.addProperty("killer", EntityList.getKey(formSource) == null ? "" : EntityList.getKey(formSource).toString());
            state.addProperty("tick", PlayerAbilityStore.now(player));
            PlayerAbilityStore.update(player, (PlayerAbilityData data) ->
                    data.putState("aaaa0ggmc_ul_modpack:infection", state));
            AbilityNetwork.syncPlayer(player, "");
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Failed to record infection state into morphext store: {}", t.toString());
        }
    }

    private static void spawnInfectionEffects(EntityPlayerMP player) {
        WorldServer world = (WorldServer) player.world;
        double x = player.posX;
        double y = player.posY + player.height / 2.0D;
        double z = player.posZ;
        // 图腾式的橙色粒子 + 金屑碎裂
        world.spawnParticle(EnumParticleTypes.TOTEM, x, y, z, 80, 0.6D, 0.9D, 0.6D, 0.15D);
        world.spawnParticle(EnumParticleTypes.ITEM_CRACK, x, y, z, 48, 0.5D, 0.7D, 0.5D, 0.08D,
                Item.getIdFromItem(Items.GOLD_INGOT), 0);
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 1.0F, 0.7F);
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, 0.9F);
    }

    private static Entity resolveKiller(DamageSource source) {
        Entity trueSource = source.getTrueSource();
        return trueSource != null ? trueSource : source.getImmediateSource();
    }
}
