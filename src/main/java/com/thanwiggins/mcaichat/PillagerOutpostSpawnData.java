package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

// Lifetime pillager-spawn counters, one per Pillager Outpost, one registry per dimension - see
// PillagerOutpostSpawnLimiter. Structurally mirrors PlayerLocationData (a list of id/value entries
// rather than using structure ids as raw NBT compound keys, since they contain colons).
public class PillagerOutpostSpawnData extends SavedData {
    private static final String STORAGE_ID = "mcaichat_outpost_spawns";

    private final Map<String, Integer> spawnCounts = new HashMap<>();

    public static PillagerOutpostSpawnData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PillagerOutpostSpawnData::load, PillagerOutpostSpawnData::new, STORAGE_ID);
    }

    public int getCount(String outpostId) {
        return spawnCounts.getOrDefault(outpostId, 0);
    }

    public void increment(String outpostId) {
        spawnCounts.merge(outpostId, 1, Integer::sum);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : spawnCounts.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey());
            entryTag.putInt("count", entry.getValue());
            list.add(entryTag);
        }
        tag.put("outposts", list);
        return tag;
    }

    private static PillagerOutpostSpawnData load(CompoundTag tag) {
        PillagerOutpostSpawnData data = new PillagerOutpostSpawnData();
        ListTag list = tag.getList("outposts", 10); // 10 = CompoundTag
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.spawnCounts.put(entry.getString("id"), entry.getInt("count"));
        }
        return data;
    }
}
