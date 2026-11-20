package aaaa0ggmc.ulmodpack.ftbq;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.icon.ItemIcon;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * FTBQuests 形态检测任务：检测玩家「变成了什么」及其 NBT 变体（幼年 / 特定 NBT / 任意变种）。
 *
 * <p>图标默认根据首个目标形态动态生成对应生物的刷怪蛋图标，无刷怪蛋时退回采样棉签图标。</p>
 */
public class MorphTask extends Task {

    public List<String> morphs = new ArrayList<>();
    public boolean acquired;
    public boolean baby;
    public boolean anyVariant;
    public String nbtMatch = "";

    @Nullable
    private transient NBTTagCompound parsedNbtCache;
    @Nullable
    private transient String parsedNbtKey;

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
        if (baby) {
            tag.setBoolean("baby", true);
        }
        if (anyVariant) {
            tag.setBoolean("any_variant", true);
        }
        if (nbtMatch != null && !nbtMatch.isEmpty()) {
            tag.setString("nbt_match", nbtMatch);
        }
    }

    @Override
    public void readData(NBTTagCompound tag) {
        super.readData(tag);
        morphs.clear();
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
        baby = tag.getBoolean("baby");
        anyVariant = tag.getBoolean("any_variant");
        nbtMatch = tag.getString("nbt_match").trim();
        parsedNbtCache = null;
        parsedNbtKey = null;
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(String.join(",", morphs));
        data.writeBoolean(acquired);
        data.writeBoolean(baby);
        data.writeBoolean(anyVariant);
        data.writeString(nbtMatch != null ? nbtMatch : "");
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
        baby = data.readBoolean();
        anyVariant = data.readBoolean();
        nbtMatch = data.readString().trim();
        parsedNbtCache = null;
        parsedNbtKey = null;
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
        config.addBool("baby", () -> baby, (b) -> baby = b, false);
        config.addBool("any_variant", () -> anyVariant, (b) -> anyVariant = b, false);
        config.addString("nbt_match",
                () -> nbtMatch != null ? nbtMatch : "",
                (s) -> {
                    nbtMatch = s != null ? s.trim() : "";
                    parsedNbtCache = null;
                    parsedNbtKey = null;
                },
                "");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Icon getAltIcon() {
        if (!morphs.isEmpty()) {
            ResourceLocation id = new ResourceLocation(normalize(morphs.get(0)));
            if (EntityList.ENTITY_EGGS.containsKey(id)) {
                ItemStack egg = new ItemStack(Items.SPAWN_EGG);
                ItemMonsterPlacer.applyEntityIdToItemStack(egg, id);
                return ItemIcon.getItemIcon(egg);
            }
        }
        return super.getAltIcon();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getAltTitle() {
        return net.minecraft.client.resources.I18n.format("ftbquests.task.aaaa0ggmc_ul_modpack.morph")
                + ": " + String.join(", ", morphs);
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 10;
    }

    @Nullable
    NBTTagCompound getExpectedNbt() {
        if (nbtMatch == null || nbtMatch.isEmpty()) {
            return null;
        }
        if (!nbtMatch.equals(parsedNbtKey)) {
            parsedNbtKey = nbtMatch;
            try {
                parsedNbtCache = JsonToNBT.getTagFromJson(nbtMatch);
            } catch (Exception e) {
                parsedNbtCache = null;
            }
        }
        return parsedNbtCache;
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
            boolean nameMatched = false;
            for (String candidate : task.morphs) {
                if (name.equals(normalize(candidate))) {
                    nameMatched = true;
                    break;
                }
            }
            if (!nameMatched) {
                return false;
            }
            if (!task.baby && !task.anyVariant && (task.nbtMatch == null || task.nbtMatch.isEmpty())) {
                return true;
            }
            NBTTagCompound fullTag = new NBTTagCompound();
            morph.toNBT(fullTag);
            NBTTagCompound entityData = fullTag.getCompoundTag("EntityData");

            if (task.baby) {
                boolean isBaby = (entityData.hasKey("Age") && entityData.getInteger("Age") < 0)
                        || fullTag.getBoolean("Baby");
                if (!isBaby) {
                    return false;
                }
            }
            if (task.anyVariant && !hasNonDefaultVariant(entityData)) {
                return false;
            }
            NBTTagCompound expected = task.getExpectedNbt();
            if (expected != null && !NBTUtil.areNBTEquals(expected, entityData, true)) {
                return false;
            }
            return true;
        }

        private static boolean hasNonDefaultVariant(NBTTagCompound entityData) {
            if (entityData.getBoolean("Sheared") || entityData.getBoolean("Saddle")) {
                return true;
            }
            if (entityData.hasKey("Color") && entityData.getByte("Color") != 0) {
                return true;
            }
            if (entityData.hasKey("Variant") && entityData.getInteger("Variant") != 0) {
                return true;
            }
            if (entityData.hasKey("RabbitType") && entityData.getInteger("RabbitType") != 0) {
                return true;
            }
            if (entityData.hasKey("CatType") && entityData.getInteger("CatType") != 0) {
                return true;
            }
            return false;
        }
    }
}
