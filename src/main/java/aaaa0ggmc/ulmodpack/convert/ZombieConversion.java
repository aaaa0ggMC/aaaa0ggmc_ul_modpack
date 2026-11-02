package aaaa0ggmc.ulmodpack.convert;

import aaaa0ggmc.ulmodpack.infection.InfectionHandler;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.block.BlockSand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 僵尸形态的转化仪式：处于僵尸族形态的玩家，用特定物品在特定群系
 * 以特定方式"死一次"，死亡会被转化取代（无死亡界面、不掉落），完成换形态。
 *
 * 仪式一览（起始形态为僵尸族任意一种皆可）：
 *   沙葬 → 尸壳      沙漠群系，用沙子把自己埋到窒息而死
 *   水祭 → 僵尸      水域群系（河流/海洋/沼泽），手持水瓶溺水而死
 *   火祭 → 僵尸猪人   下界，手持金锭被烧死
 *
 * 僵尸族形态：minecraft:zombie / husk / zombie_pigman / zombie_villager。
 * 新仪式在 {@link #ritualTarget} 里加规则即可。
 */
public final class ZombieConversion {

    private static final Set<String> ZOMBIE_FORMS = new HashSet<>(Arrays.asList(
            "minecraft:zombie",
            "minecraft:husk",
            "minecraft:zombie_pigman",
            "minecraft:zombie_villager"
    ));

    private ZombieConversion() {
    }

    /** 形态 id 归一：去空白、小写、无命名空间补 minecraft:。 */
    static String normalize(String raw) {
        String s = raw.trim().toLowerCase();
        return s.contains(":") ? s : "minecraft:" + s;
    }

    /** 玩家当前是否处于僵尸族形态。 */
    public static boolean isZombieForm(EntityPlayerMP player) {
        IMorphing morphing = Morphing.get(player);
        if (morphing == null || !morphing.isMorphed()) {
            return false;
        }
        AbstractMorph morph = morphing.getCurrentMorph();
        return morph != null && morph.name != null && ZOMBIE_FORMS.contains(normalize(morph.name));
    }

    /** 玩家当前形态是否为指定形态。 */
    public static boolean isForm(EntityPlayerMP player, String formName) {
        IMorphing morphing = Morphing.get(player);
        if (morphing == null || !morphing.isMorphed()) {
            return false;
        }
        AbstractMorph morph = morphing.getCurrentMorph();
        return morph != null && morph.name != null && normalize(formName).equals(normalize(morph.name));
    }

    /**
     * 取位置的真实群系：读区块存储的群系数组。
     * 本整合包里 World.getBiome（生成层缓存路径）被群系模组改写后恒报错误值，
     * 区块实存数据才是与地形一致的真值。
     */
    public static Biome biomeAt(net.minecraft.world.World world, BlockPos pos) {
        return world.getChunk(pos).getBiome(pos, world.getBiomeProvider());
    }

    /** 仪式条件诊断（排障用）：把各条件的实际取值拼成一行。 */
    public static String diagnose(EntityPlayerMP player, DamageSource source) {
        BlockPos pos = new BlockPos(player);
        Biome biome = biomeAt(player.world, pos);
        return "form=" + isZombieForm(player)
                + " src=" + (source == null ? "?" : source.getDamageType())
                + " waterBiome=" + isWaterBiome(biome)
                + " bottle=" + (findWaterBottleHand(player) != null)
                + " sandy=" + BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY)
                + " buried=" + buriedInSand(player, pos)
                + " nether=" + (player.dimension == -1);
    }

    /** 按仪式条件算出转化目标；null = 不触发。 */
    public static Class<? extends EntityLivingBase> ritualTarget(EntityPlayerMP player, DamageSource source) {
        if (!isZombieForm(player)) {
            return null;
        }
        BlockPos pos = new BlockPos(player);
        Biome biome = biomeAt(player.world, pos);

        // 沙葬 → 尸壳：沙漠里被沙子埋到窒息
        if (source == DamageSource.IN_WALL
                && BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY)
                && buriedInSand(player, pos)) {
            return EntityHusk.class;
        }
        // 水祭 → 僵尸：水域群系持水瓶溺亡
        if (source == DamageSource.DROWN
                && isWaterBiome(biome)
                && findWaterBottleHand(player) != null) {
            return EntityZombie.class;
        }
        // 火祭 → 僵尸猪人：下界持金锭烧死
        if (source.isFireDamage()
                && player.dimension == -1
                && findHeld(player, Items.GOLD_INGOT) != null) {
            return EntityPigZombie.class;
        }
        return null;
    }

    /** 仪式消耗的物品（沙葬的沙子是方块本身，不占背包）。 */
    public static void consumeRitualItem(EntityPlayerMP player, Class<? extends EntityLivingBase> target) {
        if (target == EntityZombie.class) {
            EnumHand hand = findWaterBottleHand(player);
            if (hand != null) {
                ItemStack stack = player.getHeldItem(hand);
                stack.shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    player.setHeldItem(hand, bottle);
                } else if (!player.addItemStackToInventory(bottle)) {
                    player.dropItem(bottle, false);
                }
            }
        } else if (target == EntityPigZombie.class) {
            EnumHand hand = findHeld(player, Items.GOLD_INGOT);
            if (hand != null) {
                player.getHeldItem(hand).shrink(1);
            }
        }
    }

    /** 执行转化：换形态、回满血、特效。返回是否成功。 */
    public static boolean convertPlayerTo(EntityPlayerMP player, Class<? extends EntityLivingBase> targetClass) {
        Entity temp = EntityList.newEntity(targetClass, player.world);
        if (!(temp instanceof EntityLivingBase)) {
            return false;
        }
        AbstractMorph morph = InfectionHandler.buildMorph(temp);
        if (morph == null) {
            return false;
        }
        MorphAPI.acquire(player, morph);
        MorphAPI.morph(player, morph, true);
        player.setHealth(player.getMaxHealth());

        if (player.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) player.world;
            ws.spawnParticle(EnumParticleTypes.CLOUD, player.posX, player.posY + player.height / 2.0D, player.posZ,
                    30, 0.5D, 0.6D, 0.5D, 0.02D);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, player.posX, player.posY + player.height / 2.0D, player.posZ,
                    12, 0.4D, 0.5D, 0.4D, 0.0D);
        }
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 0.8F, 0.9F);
        return true;
    }

    /** 沙葬后把身边的沙子清掉，避免破土前继续窒息。 */
    public static void burstSand(EntityPlayerMP player) {
        BlockPos base = new BlockPos(player);
        for (BlockPos pos : new BlockPos[]{base, base.up(), base.up(2)}) {
            if (isSand(player.world.getBlockState(pos).getBlock())) {
                player.world.setBlockToAir(pos);
            }
        }
    }

    private static boolean buriedInSand(EntityPlayerMP player, BlockPos pos) {
        // 头部所在的一两格是沙子即视为"被沙子掩埋"
        return isSand(player.world.getBlockState(pos.up()).getBlock())
                || isSand(player.world.getBlockState(pos.up(2)).getBlock());
    }

    private static boolean isSand(net.minecraft.block.Block block) {
        // BlockSand 同时覆盖普通沙与红沙
        return block instanceof BlockSand;
    }

    private static boolean isWaterBiome(Biome biome) {
        return BiomeDictionary.hasType(biome, BiomeDictionary.Type.WATER)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.SWAMP);
    }

    /** 找手持物品的那只手；找不到返回 null。 */
    private static EnumHand findHeld(EntityPlayerMP player, Item item) {
        for (EnumHand hand : EnumHand.values()) {
            if (!player.getHeldItem(hand).isEmpty() && player.getHeldItem(hand).getItem() == item) {
                return hand;
            }
        }
        return null;
    }

    private static boolean hasWaterBottle(EntityPlayerMP player) {
        return findWaterBottleHand(player) != null;
    }

    /** 找手持水瓶的那只手；找不到返回 null。 */
    private static EnumHand findWaterBottleHand(EntityPlayerMP player) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (!stack.isEmpty() && stack.getItem() == Items.POTIONITEM
                    && PotionUtils.getPotionFromItem(stack) == PotionType.getPotionTypeForName("water")) {
                return hand;
            }
        }
        return null;
    }
}
