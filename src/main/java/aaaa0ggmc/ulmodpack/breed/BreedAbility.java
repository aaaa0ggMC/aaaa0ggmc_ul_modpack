package aaaa0ggmc.ulmodpack.breed;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbstractActiveAbility;
import aaaa0ggmc.morphext.ability.ActivationResult;
import aaaa0ggmc.morphext.ability.ExecutionSide;
import aaaa0ggmc.morphext.ability.MorphContext;
import aaaa0ggmc.morphext.ability.ServerAbilityService;
import aaaa0ggmc.morphext.ability.TemporaryAbilityRegistry;
import aaaa0ggmc.morphext.variant.MorphVariantHelper;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.dna.DnaRecipe;
import aaaa0ggmc.ulmodpack.item.ItemWeddingRing;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeManager;
import com.google.gson.JsonObject;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 结婚戒指临时授予的「繁殖」主动技能（{@code aaaa0ggmc_ul_modpack:breed}）。
 *
 * <p>玩家佩戴结婚戒指后临时获得本技能；对准白名单生物、自身处于对应形态/变种且手持指定物品时，
 * 消耗物品并产生对应后代（类原版繁殖逻辑）。</p>
 */
public final class BreedAbility extends AbstractActiveAbility {

    public static final String ID = UlModpack.MODID + ":breed";
    private static final String SOURCE_RING = "ulmodpack:wedding_ring";
    private static final String NBT_BREED_COOLDOWN = "ulBreedCooldownUntil";
    private static final String NBT_BREED_PARENT_PLAYER = "ulBreedParentPlayer";
    private static final double WIDE_REACH_SQ = 64.0D;
    private static final int ABILITY_COOLDOWN_TICKS = 1200;

    private static boolean registered = false;

    private BreedAbility() {
        super(ID,
                "morphext.ability." + UlModpack.MODID + ".breed.name",
                "morphext.ability." + UlModpack.MODID + ".breed.desc",
                new ItemStack(CommonProxy.WEDDING_RING));
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        AbilityRegistry.register(new BreedAbility());
        TemporaryAbilityRegistry.register(SOURCE_RING, player ->
                ItemWeddingRing.isEquipped(player)
                        ? Collections.singletonList(ID)
                        : Collections.emptyList());
        aaaa0ggmc.morphext.faction.FactionGraphEngine.getInstance().registerRelationOverride((observer, target) -> {
            if (target instanceof EntityPlayer && observer.getEntityData().hasUniqueId(NBT_BREED_PARENT_PLAYER)) {
                java.util.UUID parentId = observer.getEntityData().getUniqueId(NBT_BREED_PARENT_PLAYER);
                if (parentId != null && parentId.equals(target.getUniqueID())) {
                    return aaaa0ggmc.morphext.faction.FactionRelation.RELATIVE;
                }
            }
            return null;
        });
        try {
            ServerAbilityService.register(ID, new ServerHandler());
        } catch (RuntimeException ex) {
            UlModpack.LOGGER.warn("[breed] Failed registering Breed server handler: {}", ex.toString());
        }
    }

    @Override
    public ExecutionSide getExecutionSide() {
        return ExecutionSide.SERVER;
    }

    @Override
    public int clientPickTarget(EntityPlayer player) {
        return HordeManager.findLookedLivingId(player);
    }

    @Override
    public ActivationResult activate(EntityPlayer player, MorphContext context) {
        return new ActivationResult(ActivationResult.Status.SUCCESS, 0);
    }

    private static final class ServerHandler implements ServerAbilityService.Handler {

        @Override
        public boolean isAvailable(EntityPlayerMP player, String abilityId) {
            return ItemWeddingRing.isEquipped(player);
        }

        @Override
        public int getCooldownTicks(EntityPlayerMP player, String abilityId) {
            return ABILITY_COOLDOWN_TICKS;
        }

        @Override
        public ActivationResult activate(EntityPlayerMP player, String abilityId) {
            return activate(player, abilityId, null, null);
        }

        @Override
        public ActivationResult activate(EntityPlayerMP player, String abilityId,
                                         @Nullable JsonObject params,
                                         @Nullable EntityLivingBase target) {
            if (target == null || target.isDead || player.getDistanceSq(target) > WIDE_REACH_SQ) {
                player.sendStatusMessage(new TextComponentTranslation("ul.breed.no_target"), true);
                return new ActivationResult(ActivationResult.Status.FAILED, 0, "");
            }

            ResourceLocation targetKey = EntityList.getKey(target);
            if (targetKey == null) {
                player.sendStatusMessage(new TextComponentTranslation("ul.breed.not_whitelisted"), true);
                return new ActivationResult(ActivationResult.Status.FAILED, 0, "");
            }
            List<BreedingRule> rules = BreedingRuleRegistry.getRules(targetKey.toString());
            if (rules.isEmpty()) {
                player.sendStatusMessage(new TextComponentTranslation("ul.breed.not_whitelisted"), true);
                return new ActivationResult(ActivationResult.Status.FAILED, 0, "");
            }

            IMorphing morphing = Morphing.get(player);
            AbstractMorph morph = morphing == null ? null : morphing.getCurrentMorph();
            if (morph == null || morph.name == null || morph.name.isEmpty()) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ul.breed.wrong_morph_need", entityNameComponent(rules.get(0).requiredMorph)), true);
                return new ActivationResult(ActivationResult.Status.FAILED, 0, "");
            }

            NBTTagCompound morphData = (morph instanceof EntityMorph)
                    ? ((EntityMorph) morph).getEntityData() : null;
            String playerVariant = MorphVariantHelper.getVariantKey(morph);
            NBTTagCompound targetNbt = new NBTTagCompound();
            target.writeToNBT(targetNbt);

            net.minecraft.util.text.ITextComponent failMsg = new TextComponentTranslation(
                    "ul.breed.wrong_morph_need", entityNameComponent(rules.get(0).requiredMorph));
            BreedingRule matchedRule = null;
            EnumHand matchedHand = null;
            DnaRecipe.ItemRequirement matchedReq = null;

            for (BreedingRule rule : rules) {
                if (!rule.requiredMorph.equalsIgnoreCase(morph.name)) {
                    failMsg = new TextComponentTranslation(
                            "ul.breed.wrong_morph_need", entityNameComponent(rule.requiredMorph));
                    continue;
                }
                if (rule.requireAdult) {
                    if (isMorphBaby(playerVariant, morphData) || target.isChild()) {
                        failMsg = new TextComponentTranslation("ul.breed.not_adult");
                        continue;
                    }
                }
                if (!rule.requiredPlayerVariants.isEmpty()
                        && !matchesAnyVariant(playerVariant, rule.requiredPlayerVariants)) {
                    failMsg = new TextComponentTranslation("ul.breed.wrong_variant");
                    continue;
                }
                if (!matchesNbt(morphData, rule.requiredPlayerNbt)) {
                    failMsg = new TextComponentTranslation("ul.breed.wrong_variant");
                    continue;
                }
                if (!matchesNbt(targetNbt, rule.requiredTargetNbt)) {
                    failMsg = new TextComponentTranslation("ul.breed.target_invalid");
                    continue;
                }
                int remainTicks = getTargetBreedCooldownTicks(target);
                if (remainTicks > 0) {
                    failMsg = new TextComponentTranslation("ul.breed.target_cooldown_sec",
                            Math.max(1, (remainTicks + 19) / 20));
                    continue;
                }
                if (!rule.acceptedItems.isEmpty()) {
                    MatchedItem found = findHeldRequirement(player, rule.acceptedItems);
                    if (found == null) {
                        failMsg = new TextComponentTranslation(
                                "ul.breed.wrong_item_need", itemListComponent(rule.acceptedItems));
                        continue;
                    }
                    matchedHand = found.hand;
                    matchedReq = found.req;
                }
                matchedRule = rule;
                break;
            }

            if (matchedRule == null) {
                player.sendStatusMessage(failMsg, true);
                return new ActivationResult(ActivationResult.Status.FAILED, 0, "");
            }

            if (!spawnOffspring(player, target, matchedRule, morphData, targetNbt)) {
                player.sendStatusMessage(new TextComponentTranslation("ul.breed.spawn_failed"), true);
                return new ActivationResult(ActivationResult.Status.FAILED, 0, "");
            }

            if (matchedHand != null && matchedReq != null && !player.capabilities.isCreativeMode) {
                player.getHeldItem(matchedHand).shrink(matchedReq.count);
            }
            applyTargetCooldown(target, matchedRule.cooldownTicks);
            spawnBreedEffects(player, target, matchedRule.xp);
            player.sendStatusMessage(new TextComponentTranslation("ul.breed.success"), true);
            return new ActivationResult(ActivationResult.Status.SUCCESS, ABILITY_COOLDOWN_TICKS);
        }
    }

    private static boolean isMorphBaby(String variantKey, @Nullable NBTTagCompound morphData) {
        if (variantKey != null && variantKey.toLowerCase().contains("baby")) {
            return true;
        }
        return morphData != null && morphData.hasKey("Age") && morphData.getInteger("Age") < 0;
    }

    private static boolean matchesAnyVariant(String actual, List<String> expected) {
        for (String v : expected) {
            if ("*".equals(v) || v.equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesNbt(@Nullable NBTTagCompound nbt, Map<String, Object> expected) {
        if (expected.isEmpty()) {
            return true;
        }
        if (nbt == null) {
            return false;
        }
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (val instanceof Boolean) {
                boolean expectedBool = (Boolean) val;
                boolean actualBool = nbt.hasKey(key) && nbt.getBoolean(key);
                if (actualBool != expectedBool) {
                    return false;
                }
            } else if (val instanceof Number) {
                if (!nbt.hasKey(key) || nbt.getInteger(key) != ((Number) val).intValue()) {
                    return false;
                }
            } else if (val != null) {
                if (!nbt.hasKey(key) || !val.toString().equals(nbt.getString(key))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int getTargetBreedCooldownTicks(EntityLivingBase target) {
        int animalAge = (target instanceof EntityAnimal) ? Math.max(0, ((EntityAnimal) target).getGrowingAge()) : 0;
        long until = target.getEntityData().getLong(NBT_BREED_COOLDOWN);
        long customRemain = Math.max(0L, until - target.world.getTotalWorldTime());
        return (int) Math.max(animalAge, customRemain);
    }

    private static net.minecraft.util.text.ITextComponent entityNameComponent(String entityId) {
        String transName = EntityList.getTranslationName(new ResourceLocation(entityId));
        if (transName != null && !transName.isEmpty()) {
            return new TextComponentTranslation("entity." + transName + ".name");
        }
        return new net.minecraft.util.text.TextComponentString(entityId);
    }

    private static net.minecraft.util.text.ITextComponent itemListComponent(
            List<DnaRecipe.ItemRequirement> options) {
        net.minecraft.util.text.TextComponentString out = new net.minecraft.util.text.TextComponentString("");
        int limit = Math.min(options.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                out.appendText(" / ");
            }
            DnaRecipe.ItemRequirement req = options.get(i);
            Item item = Item.getByNameOrId(req.itemId);
            if (item != null) {
                ItemStack stack = new ItemStack(item, 1, Math.max(0, req.meta));
                out.appendSibling(new TextComponentTranslation(stack.getTranslationKey() + ".name"));
            } else {
                out.appendText(req.itemId);
            }
            if (req.count > 1) {
                out.appendText(" x" + req.count);
            }
        }
        if (options.size() > limit) {
            out.appendText("...");
        }
        return out;
    }

    private static void applyTargetCooldown(EntityLivingBase target, int cooldownTicks) {
        if (target instanceof EntityAnimal) {
            ((EntityAnimal) target).setGrowingAge(cooldownTicks);
        }
        target.getEntityData().setLong(NBT_BREED_COOLDOWN, target.world.getTotalWorldTime() + cooldownTicks);
    }

    private static final class MatchedItem {
        final EnumHand hand;
        final DnaRecipe.ItemRequirement req;

        MatchedItem(EnumHand hand, DnaRecipe.ItemRequirement req) {
            this.hand = hand;
            this.req = req;
        }
    }

    @Nullable
    private static MatchedItem findHeldRequirement(EntityPlayer player, List<DnaRecipe.ItemRequirement> options) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack held = player.getHeldItem(hand);
            if (held.isEmpty()) {
                continue;
            }
            for (DnaRecipe.ItemRequirement req : options) {
                Item item = Item.getByNameOrId(req.itemId);
                if (item != null && held.getItem() == item
                        && (req.meta < 0 || held.getMetadata() == req.meta)
                        && held.getCount() >= req.count) {
                    return new MatchedItem(hand, req);
                }
            }
        }
        return null;
    }

    private static boolean spawnOffspring(EntityPlayerMP player, EntityLivingBase target, BreedingRule rule,
                                          @Nullable NBTTagCompound morphData, NBTTagCompound targetNbt) {
        Entity spawned = EntityList.createEntityByIDFromName(new ResourceLocation(rule.offspringEntity), target.world);
        if (spawned == null) {
            return false;
        }
        double x = (player.posX + target.posX) * 0.5D;
        double y = target.posY;
        double z = (player.posZ + target.posZ) * 0.5D;
        spawned.setLocationAndAngles(x, y, z, target.world.rand.nextFloat() * 360.0F, 0.0F);
        if (spawned instanceof EntityLiving) {
            ((EntityLiving) spawned).onInitialSpawn(
                    target.world.getDifficultyForLocation(new BlockPos(spawned)), null);
        }
        if (rule.offspringBaby && spawned instanceof EntityAgeable) {
            ((EntityAgeable) spawned).setGrowingAge(-24000);
        }
        if (!rule.inheritNbtKeys.isEmpty()) {
            NBTTagCompound childNbt = new NBTTagCompound();
            spawned.writeToNBT(childNbt);
            for (String key : rule.inheritNbtKeys) {
                NBTTagCompound source = null;
                boolean hasMorph = morphData != null && morphData.hasKey(key);
                boolean hasTarget = targetNbt.hasKey(key);
                if (hasMorph && hasTarget) {
                    source = target.world.rand.nextBoolean() ? morphData : targetNbt;
                } else if (hasMorph) {
                    source = morphData;
                } else if (hasTarget) {
                    source = targetNbt;
                }
                if (source != null) {
                    childNbt.setTag(key, source.getTag(key).copy());
                }
            }
            spawned.readFromNBT(childNbt);
        }
        spawned.getEntityData().setUniqueId(NBT_BREED_PARENT_PLAYER, player.getUniqueID());
        return target.world.spawnEntity(spawned);
    }

    private static void spawnBreedEffects(EntityPlayerMP player, EntityLivingBase target, int xp) {
        if (player.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) player.world;
            ws.spawnParticle(EnumParticleTypes.HEART,
                    target.posX, target.posY + target.height * 0.8D, target.posZ,
                    7, 0.35D, 0.35D, 0.35D, 0.02D);
            ws.spawnParticle(EnumParticleTypes.HEART,
                    player.posX, player.posY + player.height * 0.8D, player.posZ,
                    5, 0.35D, 0.35D, 0.35D, 0.02D);
            ws.playSound(null, target.posX, target.posY, target.posZ,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.8F, 1.2F);
        }
        if (xp > 0 && target.world.getGameRules().getBoolean("doMobLoot")) {
            target.world.spawnEntity(new EntityXPOrb(
                    target.world, target.posX, target.posY + 0.5D, target.posZ, xp));
        }
    }
}
