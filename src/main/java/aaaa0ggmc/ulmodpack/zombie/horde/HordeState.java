package aaaa0ggmc.ulmodpack.zombie.horde;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 尸潮世界数据（{@code ul_horde}，按维度 {@code getPerWorldStorage} 存）。
 *
 * 键 = 玩家 UUID → 一场屠村会话。只存 UUID 与坐标，不存实体引用（实体卸载后为空，
 * 随从关系另随从 NBT {@code ulFollowOwner} 持久化）。第一版只保证单人正确；同世界
 * 可并存多场（每玩家一场）。
 *
 * 字段：
 *  - {@code active}：是否正在进行屠村；
 *  - {@code villageCenter}：目标村庄中心（发动时锁定）；
 *  - {@code startTick}：发动时的世界时间戳；
 *  - {@code lastGolemSpawnTick}：上次刷铁傀儡的世界时间戳（控制 5 秒节奏）；
 *  - {@code abortSinceTick}：玩家连续远离村庄起始时间戳（0 = 未在远离），用于 30 秒中止。
 */
public class HordeState extends WorldSavedData {

    public static final String DATA_NAME = "ul_horde";

    /** 单个玩家的一场尸潮会话。 */
    public static final class Entry {
        public boolean active;
        public BlockPos villageCenter;
        public long startTick;
        public long lastGolemSpawnTick;
        public long abortSinceTick;

        public Entry() {
            this.active = false;
            this.villageCenter = null;
            this.startTick = 0L;
            this.lastGolemSpawnTick = 0L;
            this.abortSinceTick = 0L;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public HordeState() {
        this(DATA_NAME);
    }

    public HordeState(String name) {
        super(name);
    }

    /** 取（或懒建）本维度的尸潮数据。 */
    public static HordeState get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        HordeState data = (HordeState) storage.getOrLoadData(HordeState.class, DATA_NAME);
        if (data == null) {
            data = new HordeState();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public Entry get(UUID owner) {
        return entries.get(owner);
    }

    public Entry getOrCreate(UUID owner) {
        return entries.computeIfAbsent(owner, k -> new Entry());
    }

    public void put(UUID owner, Entry entry) {
        entries.put(owner, entry);
        markDirty();
    }

    public void remove(UUID owner) {
        if (entries.remove(owner) != null) {
            markDirty();
        }
    }

    /** 当前会话的只读快照（tick 遍历用，避免遍历中修改）。 */
    public java.util.Map<UUID, Entry> snapshotEntries() {
        return new java.util.HashMap<>(entries);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        NBTTagList list = nbt.getTagList("Owners", new NBTTagCompound().getId());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            try {
                UUID owner = UUID.fromString(tag.getString("Owner"));
                Entry entry = new Entry();
                entry.active = tag.getBoolean("Active");
                entry.startTick = tag.getLong("StartTick");
                entry.lastGolemSpawnTick = tag.getLong("LastGolem");
                entry.abortSinceTick = tag.getLong("AbortSince");
                if (tag.hasKey("Village")) {
                    NBTTagCompound pos = tag.getCompoundTag("Village");
                    entry.villageCenter = new BlockPos(pos.getInteger("X"), pos.getInteger("Y"), pos.getInteger("Z"));
                }
                entries.put(owner, entry);
            } catch (IllegalArgumentException invalid) {
                // 损坏的 UUID 条目跳过，不影响其余会话
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Owner", e.getKey().toString());
            Entry entry = e.getValue();
            tag.setBoolean("Active", entry.active);
            tag.setLong("StartTick", entry.startTick);
            tag.setLong("LastGolem", entry.lastGolemSpawnTick);
            tag.setLong("AbortSince", entry.abortSinceTick);
            if (entry.villageCenter != null) {
                NBTTagCompound pos = new NBTTagCompound();
                pos.setInteger("X", entry.villageCenter.getX());
                pos.setInteger("Y", entry.villageCenter.getY());
                pos.setInteger("Z", entry.villageCenter.getZ());
                tag.setTag("Village", pos);
            }
            list.appendTag(tag);
        }
        nbt.setTag("Owners", list);
        return nbt;
    }
}
