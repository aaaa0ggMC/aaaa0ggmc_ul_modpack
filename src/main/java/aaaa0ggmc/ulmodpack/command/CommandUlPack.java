package aaaa0ggmc.ulmodpack.command;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.convert.ZombieConversion;
import aaaa0ggmc.ulmodpack.infection.InfectionHandler;
import aaaa0ggmc.ulmodpack.item.ItemAnkhAmulet;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 调试/验收指令（OP）：
 *
 *   /ulpack status              —— 查看护符槽位、当前形态、感染状态
 *   /ulpack equip               —— 把安卡护符放进 AMULET 槽（测试免 GUI）
 *   /ulpack infect [entity]     —— 立刻走一次感染变身（默认 minecraft:zombie）
 *   /ulpack killby [entity]     —— 让指定实体“杀死”自己（走完整死亡流程，测死亡取消）
 */
public class CommandUlPack extends CommandBase {

    @Override
    public String getName() {
        return "ulpack";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("ulp");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ulpack <status|equip|infect|killby|convert|death> [参数]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                status(player);
                break;
            case "equip":
                equip(player);
                break;
            case "infect":
                InfectionHandler.infect(player, resolveForm(player, args, 1), false);
                break;
            case "killby":
                EntityLivingBase killer = resolveForm(player, args, 1);
                player.attackEntityFrom(net.minecraft.util.DamageSource.causeMobDamage(killer), 10000.0F);
                break;
            case "convert": {
                ResourceLocation id = args.length > 1
                        ? new ResourceLocation(args[1])
                        : new ResourceLocation("minecraft", "husk");
                Entity temp = EntityList.createEntityByIDFromName(id, player.world);
                if (!(temp instanceof EntityLivingBase)) {
                    throw new CommandException("Unknown entity id: " + id);
                }
                if (ZombieConversion.convertPlayerTo(player, (Class<? extends EntityLivingBase>) temp.getClass())) {
                    player.sendMessage(new TextComponentString("Converted to " + id));
                } else {
                    throw new CommandException("Conversion failed for " + id);
                }
                break;
            }
            case "death": {
                String cause = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "in_wall";
                net.minecraft.util.DamageSource src;
                switch (cause) {
                    case "in_wall":
                        src = net.minecraft.util.DamageSource.IN_WALL;
                        break;
                    case "drown":
                        src = net.minecraft.util.DamageSource.DROWN;
                        break;
                    case "fire":
                        src = net.minecraft.util.DamageSource.IN_FIRE;
                        break;
                    case "lava":
                        src = net.minecraft.util.DamageSource.LAVA;
                        break;
                    case "cactus":
                        src = net.minecraft.util.DamageSource.CACTUS;
                        break;
                    default:
                        throw new WrongUsageException("/ulpack death <in_wall|drown|fire|lava|cactus>");
                }
                player.attackEntityFrom(src, 10000.0F);
                break;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static EntityLivingBase resolveForm(EntityPlayerMP player, String[] args, int index) throws CommandException {
        ResourceLocation id = args.length > index
                ? new ResourceLocation(args[index])
                : new ResourceLocation("minecraft", "zombie");
        Entity entity = EntityList.createEntityByIDFromName(id, player.world);
        if (!(entity instanceof EntityLivingBase)) {
            throw new CommandException("Unknown entity id: " + id);
        }
        return (EntityLivingBase) entity;
    }

    private static void status(EntityPlayerMP player) {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        int slot = findAnkhSlot(player);
        player.sendMessage(new TextComponentString("Ankh: " + (slot >= 0 ? "equipped (slot " + slot + ")" : "not worn")));
        IMorphing morphing = Morphing.get(player);
        if (morphing == null) {
            player.sendMessage(new TextComponentString("Morph: <no metamorph capability>"));
        } else {
            player.sendMessage(new TextComponentString("Morph: " + (morphing.isMorphed()
                    ? String.valueOf(morphing.getCurrentMorph() == null ? "?" : morphing.getCurrentMorph().name)
                    : "<human>")));
            player.sendMessage(new TextComponentString("Acquired morphs: " + morphing.getAcquiredMorphs().size()));
        }
        net.minecraft.world.biome.Biome biome = aaaa0ggmc.ulmodpack.convert.ZombieConversion.biomeAt(player.world, new net.minecraft.util.math.BlockPos(player));
        StringBuilder types = new StringBuilder();
        for (net.minecraftforge.common.BiomeDictionary.Type t
                : net.minecraftforge.common.BiomeDictionary.getTypes(biome)) {
            if (types.length() > 0) {
                types.append(",");
            }
            types.append(t.getName());
        }
        player.sendMessage(new TextComponentString("Biome: " + biome.getBiomeName()
                + " [" + types + "] dim=" + player.dimension));
        if (handler != null) {
            player.sendMessage(new TextComponentString("Baubles slots: " + handler.getSlots()));
        }
        UlModpack.LOGGER.info("[ul-modpack] status for {}: ankh slot {}", player.getName(), slot);
    }

    private static void equip(EntityPlayerMP player) throws CommandException {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null) {
            throw new CommandException("No baubles handler");
        }
        if (findAnkhSlot(player) >= 0) {
            player.sendMessage(new TextComponentString("Ankh already equipped."));
            return;
        }
        int[] slots = BaubleType.AMULET.getValidSlots();
        int target = -1;
        for (int slot : slots) {
            if (slot < handler.getSlots() && handler.getStackInSlot(slot).isEmpty()) {
                target = slot;
                break;
            }
        }
        if (target < 0) {
            throw new CommandException("No empty amulet slot");
        }
        ItemStack stack = new ItemStack(CommonProxy.ANKH_AMULET);
        handler.setStackInSlot(target, stack);
        if (stack.getItem() instanceof IBauble) {
            ((IBauble) stack.getItem()).onEquipped(stack, player);
        }
        player.sendMessage(new TextComponentString("Ankh amulet equipped in slot " + target + "."));
    }

    private static int findAnkhSlot(EntityPlayerMP player) {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null) {
            return -1;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemAnkhAmulet) {
                return slot;
            }
        }
        return -1;
    }
}
