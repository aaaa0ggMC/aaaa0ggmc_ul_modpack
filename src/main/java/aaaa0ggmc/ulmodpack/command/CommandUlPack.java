package aaaa0ggmc.ulmodpack.command;

import aaaa0ggmc.morphext.conversion.DeathContext;
import aaaa0ggmc.morphext.conversion.MorphFactory;
import aaaa0ggmc.morphext.conversion.WorldQuery;
import aaaa0ggmc.morphext.conversion.rule.ConversionRule;
import aaaa0ggmc.morphext.conversion.rule.ConversionRuleRegistry;
import aaaa0ggmc.morphext.conversion.rule.RuleExecutor;
import aaaa0ggmc.morphext.conversion.rule.RuleMatcher;
import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.infection.InfectionHandler;
import aaaa0ggmc.ulmodpack.item.ItemAnkhAmulet;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
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

import java.util.ArrayList;
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
 *   /ulpack ritual <list|test|debug|reload> [id]
 *                               —— 转化规则（morphext 数据驱动仪式）查看/试触发/排障/重载
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
        return "/ulpack <status|equip|infect|killby|convert|death|ritual> [参数]";
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
                String formId = args.length > 1 ? args[1] : "minecraft:husk";
                AbstractMorph morph = MorphFactory.buildFromFormId(formId, player.world);
                if (morph == null) {
                    throw new CommandException("No morph available for form id: " + formId);
                }
                MorphAPI.acquire(player, morph);
                MorphAPI.morph(player, morph, true);
                player.setHealth(player.getMaxHealth());
                player.sendMessage(new TextComponentString("Converted to " + formId));
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
            case "ritual":
                ritual(player, args);
                break;
            case "tide":
                aaaa0ggmc.ulmodpack.zombie.horde.HordeCommands.tide(player, args);
                break;
            case "skill":
                aaaa0ggmc.ulmodpack.zombie.horde.HordeCommands.skill(player, args, server);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    /** 转化规则（morphext 数据驱动仪式）查看/试触发/排障/重载。 */
    private static void ritual(EntityPlayerMP player, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/ulpack ritual <list|test|debug|reload> [id]");
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "list": {
                List<ConversionRule> rules = ConversionRuleRegistry.all();
                player.sendMessage(new TextComponentString("Conversion rules: " + rules.size()));
                for (ConversionRule rule : rules) {
                    player.sendMessage(new TextComponentString(" - " + rule.id + " [line=" + rule.line
                            + " prio=" + rule.priority + "] -> " + rule.target
                            + " death=" + String.join("/", rule.death.types)));
                }
                break;
            }
            case "test": {
                if (args.length < 3) {
                    throw new WrongUsageException("/ulpack ritual test <id>");
                }
                ConversionRule rule = ConversionRuleRegistry.byId(args[2]);
                if (rule == null) {
                    throw new CommandException("Unknown conversion rule: " + args[2]);
                }
                DeathContext ctx = new DeathContext(player, net.minecraft.util.DamageSource.GENERIC, null);
                boolean ok = RuleExecutor.execute(rule, ctx);
                player.sendMessage(new TextComponentString("Rule " + rule.id + " -> " + (ok ? "converted" : "failed")));
                break;
            }
            case "debug": {
                DeathContext ctx = new DeathContext(player, net.minecraft.util.DamageSource.GENERIC, null);
                player.sendMessage(new TextComponentString("Form: " + ctx.formId()
                        + " categories=[" + String.join(",", ctx.categories()) + "]"));
                for (ConversionRule rule : ConversionRuleRegistry.all()) {
                    List<String> missing = new ArrayList<>();
                    RuleMatcher.evaluate(rule, ctx, missing);
                    player.sendMessage(new TextComponentString(" - " + rule.id + ": "
                            + (missing.isEmpty() ? "READY" : "missing " + String.join(",", missing))));
                }
                break;
            }
            case "reload": {
                ConversionRuleRegistry.reload();
                aaaa0ggmc.morphext.form.FormCategories.reload();
                player.sendMessage(new TextComponentString("Reloaded conversion rules ("
                        + ConversionRuleRegistry.all().size() + ") and form categories ("
                        + aaaa0ggmc.morphext.form.FormCategories.snapshot().size() + ")."));
                break;
            }
            default:
                throw new WrongUsageException("/ulpack ritual <list|test|debug|reload> [id]");
        }
    }

    private static String currentFormId(EntityPlayerMP player) {
        IMorphing morphing = Morphing.get(player);
        if (morphing == null || !morphing.isMorphed() || morphing.getCurrentMorph() == null) {
            return "";
        }
        return String.valueOf(morphing.getCurrentMorph().name);
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
        net.minecraft.world.biome.Biome biome = WorldQuery.biomeAt(player.world, new net.minecraft.util.math.BlockPos(player));
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
        player.sendMessage(new TextComponentString("Form categories: ["
                + String.join(",", FormCategories.categoriesOf(currentFormId(player))) + "]"));
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
