package aaaa0ggmc.ulmodpack.ftbq;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * FTBQuests 通用「玩家进度数值」任务：读 morphext 玩家数据里的一个状态键，
 * 按其中的数值判断是否达标。**不含任何具体模组/整合包语义**——键名、阈值全部来自任务配置。
 *
 * type 名：{@code aaaa0ggmc_ul_modpack:progress}
 * 字段：
 *   key   玩家数据状态键（如 "ul:ritual/sand_burial"）
 *   min   需要达到的数值，缺省 1；必须 >= 1
 *
 * 取值规则（键 = morphext 玩家数据 states 下的一个 JSON 对象）：
 *   键不存在            -> 0
 *   键存在且有数字 count -> count
 *   键存在（无 count）   -> 1
 * 值 >= min 即完成。
 *
 * autoSubmitOnPlayerTick=10：FTBQuests 每 10 tick 自动提交一次，
 * 状态写入的瞬间即可完成任务，无需点击。提交发生在服务端 PlayerTickEvent
 * （FTBQuestsEventHandler.onPlayerTick 只处理 !world.isRemote），
 * 因此这里读 morphext 玩家数据是合法的服务端主线程调用。
 *
 * 任务源（quests/*.json）两种写法：
 *   "progress:ul:ritual/sand_burial"                                （简写，min=1）
 *   { type: "progress", key: "ul:tide/cleared", min: 1 }             （对象）
 */
public class ProgressTask extends Task {

    public String key = "";
    public int min = 1;

    public ProgressTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskRegistry.PROGRESS;
    }

    @Override
    public TaskData createData(QuestData data) {
        return new Data(this, data);
    }

    @Override
    public void writeData(NBTTagCompound tag) {
        super.writeData(tag);
        tag.setString("key", key == null ? "" : key);
        if (min != 1) {
            tag.setInteger("min", min);
        }
    }

    @Override
    public void readData(NBTTagCompound tag) {
        super.readData(tag);
        key = tag.getString("key");
        // getInteger 缺键返回 0：按缺省 1 处理，顺带挡住 0/负数的脏数据
        min = tag.getInteger("min");
        if (min < 1) {
            min = 1;
        }
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(key == null ? "" : key);
        data.writeInt(min);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        key = data.readString();
        min = data.readInt();
        if (min < 1) {
            min = 1;
        }
    }

    @Override
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("key", () -> key == null ? "" : key, s -> key = s == null ? "" : s, "");
        config.addInt("min", () -> min, i -> min = i, 1, 1, Integer.MAX_VALUE);
    }

    @Override
    public String getAltTitle() {
        return net.minecraft.client.resources.I18n.format("ftbquests.task.aaaa0ggmc_ul_modpack.progress")
                + ": " + (key == null ? "" : key);
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 10;
    }

    /** 键是否可用（空键的任务永远不完成）。 */
    public boolean hasKey() {
        return key != null && !key.trim().isEmpty();
    }

    /**
     * 读取进度值。PlayerAbilityStore.get 在数据损坏/非服务端主线程时抛
     * IllegalStateException，这里吞掉并当作 0：任务只是不完成，不该炸掉 tick 循环。
     */
    static long readProgress(EntityPlayerMP player, String key) {
        try {
            PlayerAbilityData data = PlayerAbilityStore.get(player);
            if (data == null) {
                return 0L;
            }
            JsonObject root = data.toJson();
            JsonElement states = root.get("states");
            if (states == null || !states.isJsonObject()) {
                return 0L;
            }
            JsonElement state = states.getAsJsonObject().get(key);
            if (state == null || !state.isJsonObject()) {
                return 0L;  // 键不存在
            }
            JsonElement count = state.getAsJsonObject().get("count");
            if (count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()) {
                return count.getAsLong();
            }
            return 1L;  // 键存在但没有 count：按 1 次计
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public static class Data extends BooleanTaskData<ProgressTask> {

        public Data(ProgressTask task, QuestData data) {
            super(task, data);
        }

        @Override
        public boolean canSubmit(EntityPlayerMP player) {
            if (!task.hasKey()) {
                return false;
            }
            return ProgressTask.readProgress(player, task.key) >= task.min;
        }
    }
}
