package aaaa0ggmc.ulmodpack.ftbq;

import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.List;

/**
 * FTBQuests 形态检测任务：检测玩家「变成了什么」。
 *
 * type 名：{@code aaaa0ggmc_ul_modpack:morph}
 * 字段：
 *   morphs   形态 id 列表（任一命中即算完成；写 "zombie" 自动补 minecraft: 前缀）
 *   acquired true = 检查“曾获得过的形态”列表；false（默认）= 检查“当前形态”
 *
 * autoSubmitOnPlayerTick=10：FTBQuests 每 10 tick 自动提交一次，
 * 玩家变身的瞬间即可完成任务，无需点击。
 *
 * 任务源（quests/*.json）两种写法：
 *   "morph:minecraft:zombie,minecraft:husk"                （简写）
 *   { type: "morph", morphs: ["minecraft:zombie"], acquired: true }  （对象）
 */
public class MorphTask extends Task {

    public List<String> morphs = new ArrayList<>();
    public boolean acquired;

    public MorphTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskRegistry.MORPH;
    }

    @Override
    public TaskData createData(QuestData data) {
        return new Data(this, data);
    }

    @Override
    public void writeData(NBTTagCompound tag) {
        super.writeData(tag);
        NBTTagList list = new NBTTagList();
        for (String s : morphs) {
            list.appendTag(new NBTTagString(s));
        }
        tag.setTag("morphs", list);
        if (acquired) {
            tag.setBoolean("acquired", true);
        }
    }

    @Override
    public void readData(NBTTagCompound tag) {
        super.readData(tag);
        morphs.clear();
        // 兼容字符串（逗号分隔）与字符串列表两种写法
        if (tag.hasKey("morphs", 8)) {
            for (String s : tag.getString("morphs").split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    morphs.add(t);
                }
            }
        } else if (tag.hasKey("morphs", 9)) {
            NBTTagList list = tag.getTagList("morphs", 8);
            for (int i = 0; i < list.tagCount(); i++) {
                String t = list.getStringTagAt(i).trim();
                if (!t.isEmpty()) {
                    morphs.add(t);
                }
            }
        }
        acquired = tag.getBoolean("acquired");
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(String.join(",", morphs));
        data.writeBoolean(acquired);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        morphs.clear();
        for (String part : data.readString().split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                morphs.add(t);
            }
        }
        acquired = data.readBoolean();
    }

    @Override
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("morphs",
                () -> String.join(",", morphs),
                (s) -> {
                    morphs.clear();
                    for (String part : s.split(",")) {
                        String t = part.trim();
                        if (!t.isEmpty()) {
                            morphs.add(t);
                        }
                    }
                },
                "String");
        config.addBool("acquired", () -> acquired, (b) -> acquired = b, false);
    }

    @Override
    public String getAltTitle() {
        return net.minecraft.client.resources.I18n.format("ftbquests.task.aaaa0ggmc_ul_modpack.morph")
                + ": " + String.join(", ", morphs);
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 10;
    }

    /** 形态 id 归一：去空白、小写、无命名空间补 minecraft:。 */
    static String normalize(String raw) {
        String s = raw.trim().toLowerCase();
        return s.contains(":") ? s : "minecraft:" + s;
    }

    public static class Data extends BooleanTaskData<MorphTask> {

        public Data(MorphTask task, QuestData data) {
            super(task, data);
        }

        @Override
        public boolean canSubmit(EntityPlayerMP player) {
            if (task.morphs.isEmpty()) {
                return false;
            }
            IMorphing morphing = Morphing.get(player);
            if (morphing == null) {
                return false;
            }
            if (task.acquired) {
                for (AbstractMorph morph : morphing.getAcquiredMorphs()) {
                    if (matches(morph)) {
                        return true;
                    }
                }
                return false;
            }
            return matches(morphing.getCurrentMorph());
        }

        private boolean matches(AbstractMorph morph) {
            if (morph == null || morph.name == null) {
                return false;
            }
            String name = normalize(morph.name);
            for (String want : task.morphs) {
                if (normalize(want).equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
