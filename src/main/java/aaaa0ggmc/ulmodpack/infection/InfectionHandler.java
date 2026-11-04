package aaaa0ggmc.ulmodpack.infection;

import aaaa0ggmc.morphext.conversion.ConversionPipeline;
import aaaa0ggmc.morphext.conversion.ConversionStage;
import aaaa0ggmc.morphext.conversion.DeathContext;
import aaaa0ggmc.morphext.conversion.MorphFactory;
import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.config.AnkhConfig;
import aaaa0ggmc.ulmodpack.item.ItemAnkhAmulet;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.race.RaceResolver;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.google.gson.JsonObject;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.network.AbilityNetwork;

/**
 * 感染变身（死亡转化管线的第 10 级阶段）。
 *
 * 触发条件（全部满足才生效，任何一条不满足都正常死亡、护符纹丝不动）：
 *  1. 玩家种族 ∈ race_whitelist（ankh.json，默认 human + villager）——
 *     种族判定见 {@link aaaa0ggmc.ulmodpack.race.RaceResolver}：已是僵尸族形态
 *     （ul:undead 类别）的玩家判为 undead，不在白名单内，安卡不再触发；
 *  2. 击杀者属于配置的击杀者类别（ankh.json 的 killer_categories，默认 ul:undead，
 *     数据见 assets/aaaa0ggmc_ul_modpack/morphext/form_categories.json）；
 *  3. 佩戴安卡护符（满足前两条后才消耗）。
 *
 * 命中后：
 *  1. 取消死亡（无死亡界面、无掉落、无死亡消息）；
 *  2. 感染变身：人类变击杀者形态；配置了 race_target_forms 的种族变固定目标形态
 *     （村民 → 僵尸村民，不看击杀者形态）；
 *  3. 回满生命（形态会改写最大生命，故在变身之后回血）；
 *  4. 安卡护符碎裂，粒子/音效/文案齐发；
 *  5. 解锁进度「被感染」，记录感染状态并回写显式种族记录 ul.race（感染 = 种族变更）。
 *
 * 种族、白名单、击杀者类别全部走 ankh.json 配置（config/aaaa0ggmc_ul_modpack/ankh.json
 * 可覆盖、可热重载），不在本类维护任何实体名单或种族特判。
 */
public class InfectionHandler implements ConversionStage {

    public static void register() {
        ConversionPipeline.register(new InfectionHandler());
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean handle(DeathContext ctx) {
        if (ctx.source == null) {
            return false;
        }
        EntityPlayerMP player = ctx.player;
        Entity killer = resolveKiller(ctx.source);
        if (killer == null) {
            return false;
        }
        net.minecraft.util.ResourceLocation killerId = EntityList.getKey(killer);
        if (killerId == null) {
            return false;
        }
        // 1) 种族白名单：非可转化种族（如已是僵尸族）绝不消耗护符、绝不取消死亡
        String race = RaceResolver.resolveRace(player, ctx.formId());
        if (!AnkhConfig.isRaceWhitelisted(race)) {
            ctx.note("ankh: race " + race + " not in whitelist " + AnkhConfig.raceWhitelist());
            return false;
        }
        // 2) 击杀者类别（配置，默认 ul:undead）
        if (!isConfiguredKiller(killerId.toString())) {
            ctx.note("ankh: killer " + killerId + " not in " + AnkhConfig.killerCategories());
            return false;
        }
        // 3) 佩戴护符才消耗：全部条件满足后才动护符
        if (!consumeAnkh(player)) {
            ctx.note("ankh: no ankh amulet worn");
            return false;
        }
        // 取消死亡：EntityPlayerMP.onDeath 整个包在 ForgeHooks.onLivingDeath 里，
        // 取消即无死亡界面/无掉落/无死亡消息（1.12.2 Forge 语义）。
        ctx.cancelDeath();
        UlModpack.LOGGER.info("[ul-modpack] Ankh triggered for {} (race {}, slain by {})",
                player.getName(), race, killerId);
        infect(player, killer, true, race);
        return true;
    }

    /** 击杀者是否属于配置的击杀者类别（ankh.json killer_categories，数据驱动）。 */
    private static boolean isConfiguredKiller(String entityId) {
        for (String category : AnkhConfig.killerCategories()) {
            if (FormCategories.has(entityId, category)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把玩家从死亡线上拉回并「感染」成给定实体的形态。
     * 死亡事件与调试指令共用同一路径（种族按当前状态推导）。
     */
    public static void infect(EntityPlayerMP player, Entity formSource, boolean fromDeath) {
        infect(player, formSource, fromDeath, RaceResolver.resolveRace(player));
    }

    /**
     * 感染变身（种族已判定）。
     *
     * @param race 玩家当前种族（决定目标形态：race_target_forms 里有条目就变固定形态）
     */
    private static void infect(EntityPlayerMP player, Entity formSource, boolean fromDeath, String race) {
        if (fromDeath) {
            player.getCombatTracker().reset();
            player.extinguish();
            player.fallDistance = 0.0F;
        }

        AbstractMorph morph = buildInfectionMorph(player, formSource, race);
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

    /**
     * 感染目标形态：配置了 race_target_forms 的种族变固定形态（村民 → 僵尸村民，
     * 不看击杀者），其余变击杀者形态；固定形态构建失败时回退击杀者形态。
     */
    private static AbstractMorph buildInfectionMorph(EntityPlayerMP player, Entity formSource, String race) {
        String forced = AnkhConfig.raceTargetForm(race);
        if (forced != null) {
            AbstractMorph morph = MorphFactory.buildFromFormId(forced, player.world);
            if (morph != null) {
                return morph;
            }
            UlModpack.LOGGER.warn("[ul-modpack] race_target_forms[{}] = {} unavailable — falling back to killer form",
                    race, forced);
        }
        return MorphFactory.buildFromEntity(formSource);
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

    /** 感染记录写进 morphext 玩家数据（失败不影响主流程）；感染 = 种族变更，回写 ul.race。 */
    private static void recordInfectionState(EntityPlayerMP player, Entity formSource, AbstractMorph morph) {
        try {
            JsonObject state = new JsonObject();
            state.addProperty("form", morph == null || morph.name == null ? "" : morph.name);
            state.addProperty("killer", EntityList.getKey(formSource) == null ? "" : EntityList.getKey(formSource).toString());
            state.addProperty("tick", PlayerAbilityStore.now(player));
            // 感染 = 种族变更（实现契约 ul.race）：按结果形态查种族映射，查不到不写（保持原记录）
            String newRace = morph == null ? null : RaceResolver.raceForForm(morph.name);
            PlayerAbilityStore.update(player, (PlayerAbilityData data) -> {
                data.putState("aaaa0ggmc_ul_modpack:infection", state);
                if (newRace != null) {
                    JsonObject raceState = new JsonObject();
                    raceState.addProperty("race", newRace);
                    raceState.addProperty("tick", PlayerAbilityStore.now(player));
                    data.putState(RaceResolver.STATE_ID, raceState);
                }
            });
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
