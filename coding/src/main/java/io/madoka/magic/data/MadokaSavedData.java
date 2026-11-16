package io.madoka.magic.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 魔女结界传送门的存档数据：每位玩家的「进入点」与「主世界入口位置」。
 *
 * <p><b>永远存在主世界的 DataStorage 里</b>：{@link #get(ServerLevel)} 内部强制走主世界，
 * 调用方传哪个维度的 level 进来都不会拿错（维度各自的 storage 随维度卸载难以维护）。</p>
 */
public class MadokaSavedData extends SavedData {

    private static final String NAME = "madoka_magic_portals";
    private static final int ARENA_ORIGIN = 4096;
    private static final int ARENA_SPACING = 512;

    public record Encounter(String theme, BlockPos arena, UUID owner, ResourceLocation entranceDim, BlockPos exit) {
        public boolean defeated() {
            return exit != null;
        }
    }

    /** 一位玩家的传送门记录。 */
    public static class Entry {
        private ResourceLocation prevDim;
        private BlockPos prevPos;
        private Vec3 exactReturn;
        private UUID encounter;
        private ResourceLocation entranceDim;
        private BlockPos entrancePos;

        /** 进结界前所在的维度。 */
        public ResourceLocation getPrevDim() {
            return prevDim;
        }

        /** 进结界前的坐标（返回点）。 */
        public BlockPos getPrevPos() {
            return prevPos;
        }

        public Vec3 getReturnPosition() {
            return exactReturn != null ? exactReturn : prevPos == null ? null : Vec3.atBottomCenterOf(prevPos);
        }

        public UUID getEncounter() {
            return encounter;
        }

        /** 入口传送门所在的维度（通常是主世界）。 */
        public ResourceLocation getEntranceDim() {
            return entranceDim;
        }

        /** 入口传送门的位置；null 表示当前没有存活入口。 */
        public BlockPos getEntrancePos() {
            return entrancePos;
        }

        private boolean isEmpty() {
            return prevPos == null && entrancePos == null;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (prevDim != null && prevPos != null) {
                tag.putString("prevDim", prevDim.toString());
                tag.putInt("prevX", prevPos.getX());
                tag.putInt("prevY", prevPos.getY());
                tag.putInt("prevZ", prevPos.getZ());
                if (exactReturn != null) {
                    tag.putDouble("returnX", exactReturn.x);
                    tag.putDouble("returnY", exactReturn.y);
                    tag.putDouble("returnZ", exactReturn.z);
                }
                if (encounter != null) {
                    tag.putUUID("encounter", encounter);
                }
            }
            if (entranceDim != null && entrancePos != null) {
                tag.putString("entranceDim", entranceDim.toString());
                tag.putInt("entranceX", entrancePos.getX());
                tag.putInt("entranceY", entrancePos.getY());
                tag.putInt("entranceZ", entrancePos.getZ());
            }
            return tag;
        }

        private static Entry load(CompoundTag tag) {
            Entry entry = new Entry();
            if (tag.contains("prevDim", Tag.TAG_STRING)) {
                entry.prevDim = new ResourceLocation(tag.getString("prevDim"));
                entry.prevPos = new BlockPos(tag.getInt("prevX"), tag.getInt("prevY"), tag.getInt("prevZ"));
                if (tag.contains("returnX", Tag.TAG_DOUBLE)) {
                    entry.exactReturn = new Vec3(tag.getDouble("returnX"), tag.getDouble("returnY"), tag.getDouble("returnZ"));
                }
                entry.encounter = tag.hasUUID("encounter") ? tag.getUUID("encounter") : null;
            }
            if (tag.contains("entranceDim", Tag.TAG_STRING)) {
                entry.entranceDim = new ResourceLocation(tag.getString("entranceDim"));
                entry.entrancePos = new BlockPos(tag.getInt("entranceX"), tag.getInt("entranceY"), tag.getInt("entranceZ"));
            }
            return entry;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private int nextArena;

    /** 取存档数据。无论传入哪个维度的 level，都落到主世界的 DataStorage。 */
    public static MadokaSavedData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(MadokaSavedData::load, MadokaSavedData::new, NAME);
    }

    /** 取一位玩家的记录；没有则返回 null。 */
    public Entry getEnter(UUID uuid) {
        return entries.get(uuid);
    }

    /** 记录「进结界前」的维度与坐标（返回点）。 */
    public void recordEnter(UUID uuid, ResourceLocation dim, Vec3 pos, UUID encounter) {
        Entry entry = entries.computeIfAbsent(uuid, k -> new Entry());
        entry.prevDim = dim;
        entry.prevPos = BlockPos.containing(pos);
        entry.exactReturn = pos;
        entry.encounter = encounter;
        setDirty();
    }

    /** 清除返回点（玩家已从出口回到主世界）。 */
    public void clearEnter(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return;
        }
        entry.prevDim = null;
        entry.prevPos = null;
        entry.exactReturn = null;
        entry.encounter = null;
        if (entry.isEmpty()) {
            entries.remove(uuid);
        }
        setDirty();
    }

    /** 记录入口传送门的位置（用于去重与魔女死亡时回收）。 */
    public void setEntrance(UUID uuid, ResourceLocation dim, BlockPos pos) {
        Entry entry = entries.computeIfAbsent(uuid, k -> new Entry());
        entry.entranceDim = dim;
        entry.entrancePos = pos.immutable();
        setDirty();
    }

    /** 清除入口记录（入口门被回收或已消失）。 */
    public void clearEntrance(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return;
        }
        entry.entranceDim = null;
        entry.entrancePos = null;
        if (entry.isEmpty()) {
            entries.remove(uuid);
        }
        setDirty();
    }

    public Encounter createEncounter(UUID id, String theme, UUID owner, ResourceLocation source) {
        int index = nextArena++;
        BlockPos arena = new BlockPos(ARENA_ORIGIN + index % 1024 * ARENA_SPACING, 0,
                ARENA_ORIGIN + index / 1024 * ARENA_SPACING);
        Encounter encounter = new Encounter(theme, arena, owner, source, null);
        encounters.put(id, encounter);
        setDirty();
        return encounter;
    }

    public Encounter getEncounter(UUID id) {
        return encounters.get(id);
    }

    public void defeatEncounter(UUID id, BlockPos exit) {
        Encounter old = encounters.get(id);
        if (old != null && !old.defeated()) {
            encounters.put(id, new Encounter(old.theme(), old.arena(), old.owner(), old.entranceDim(), exit.immutable()));
            setDirty();
        }
    }

    public boolean hasVisitors(UUID id) {
        return entries.values().stream().anyMatch(entry -> id.equals(entry.encounter));
    }

    public void removeEncounter(UUID id) {
        if (encounters.remove(id) != null) {
            setDirty();
        }
    }

    public static MadokaSavedData load(CompoundTag tag) {
        MadokaSavedData data = new MadokaSavedData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            try {
                UUID uuid = UUID.fromString(e.getString("uuid"));
                data.entries.put(uuid, Entry.load(e));
            } catch (IllegalArgumentException ignored) {
                // 跳过损坏的条目
            }
        }
        data.nextArena = Math.max(0, tag.getInt("nextArena"));
        var runs = tag.getList("encounters", Tag.TAG_COMPOUND);
        for (int i = 0; i < runs.size(); i++) {
            var run = runs.getCompound(i);
            var dim = ResourceLocation.tryParse(run.getString("entranceDim"));
            if (!run.hasUUID("id") || dim == null) {
                continue;
            }
            data.encounters.put(run.getUUID("id"), new Encounter(run.getString("theme"),
                    BlockPos.of(run.getLong("arena")), run.hasUUID("owner") ? run.getUUID("owner") : null,
                    dim, run.contains("exit", Tag.TAG_LONG) ? BlockPos.of(run.getLong("exit")) : null));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            CompoundTag entryTag = e.getValue().save();
            entryTag.putString("uuid", e.getKey().toString());
            list.add(entryTag);
        }
        tag.put("entries", list);
        tag.putInt("nextArena", nextArena);
        var runs = new ListTag();
        encounters.forEach((id, run) -> {
            var entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("theme", run.theme().toString());
            entry.putLong("arena", run.arena().asLong());
            entry.putString("entranceDim", run.entranceDim().toString());
            if (run.owner() != null) {
                entry.putUUID("owner", run.owner());
            }
            if (run.defeated()) {
                entry.putLong("exit", run.exit().asLong());
            }
            runs.add(entry);
        });
        tag.put("encounters", runs);
        return tag;
    }
}
