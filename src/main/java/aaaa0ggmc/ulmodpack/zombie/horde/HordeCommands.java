package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.skill.SkillDefinition;
import aaaa0ggmc.morphext.skill.SkillRegistry;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import net.minecraft.command.CommandException;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.Locale;

/**
 * 尸潮 / 技能的调试命令逻辑（由 {@code CommandUlPack} 追加 case 调用，本类不改命令类本身）。
 *
 *  - {@code /ulpack tide <start|stop|status>}：强制发动 / 结束 / 查看尸潮状态；
 *  - {@code /ulpack skill give <player> [skill]}：授予技能（缺省 horde_leadership）。
 */
public final class HordeCommands {

    private HordeCommands() {
    }

    public static void tide(EntityPlayerMP player, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/ulpack tide <start|stop|status>");
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start":
                HordeManager.forceStart(player);
                break;
            case "stop":
                HordeManager.forceStop(player);
                break;
            case "status":
                status(player);
                break;
            default:
                throw new WrongUsageException("/ulpack tide <start|stop|status>");
        }
    }

    public static void skill(EntityPlayerMP player, String[] args, MinecraftServer server) throws CommandException {
        if (args.length < 3 || !"give".equals(args[1].toLowerCase(Locale.ROOT))) {
            throw new WrongUsageException("/ulpack skill give <player> [skill]");
        }
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(args[2]);
        if (target == null) {
            throw new PlayerNotFoundException("commands.generic.player.notFound", new Object[0]);
        }
        String wanted = args.length > 3 ? args[3] : "horde_leadership";
        String skillId = resolveSkillId(wanted);
        if (skillId == null) {
            throw new CommandException("Unknown skill: " + wanted);
        }
        SkillRegistry.grant(target, skillId);
        player.sendMessage(new TextComponentString("Granted " + skillId + " to " + target.getName()));
    }

    private static void status(EntityPlayerMP player) {
        player.sendMessage(new TextComponentString("Horde leadership: " + ZombieForms.hasLeadership(player)));
        player.sendMessage(new TextComponentString("Persuasion: " + Persuasion.get(player)
                + " (cap " + Persuasion.followCap(Persuasion.get(player)) + ")"));
        player.sendMessage(new TextComponentString("Followers: " + HordeManager.followerCount(player)));
        player.sendMessage(new TextComponentString("Tide active: " + HordeManager.isTideActive(player)));
        player.sendMessage(new TextComponentString("Tides cleared: " + HordeManager.tidesCleared(player)));
    }

    /** 命令参数 → 技能 id：先按原样查，再补 {@code ul:} 前缀（{@code horde_leadership} → {@code ul:horde_leadership}）。 */
    private static String resolveSkillId(String arg) {
        if (arg == null) {
            return null;
        }
        String trimmed = arg.trim().toLowerCase(Locale.ROOT);
        SkillDefinition def = SkillRegistry.byId(trimmed);
        if (def != null) {
            return def.id;
        }
        def = SkillRegistry.byId("ul:" + trimmed);
        return def != null ? def.id : null;
    }
}
