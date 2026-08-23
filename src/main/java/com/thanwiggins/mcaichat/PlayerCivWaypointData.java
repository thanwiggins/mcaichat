package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Server-authoritative, per-dimension registry backing the auto-waypoint feature: caches each
// civilization structure's name/position the moment it's first known - independent of
// StructureLoreData, whose civ entries only ever arrive if/when Gemini's background text
// succeeds (see ClientLoreManager.onStructureEntered/CivNameReportPacket, GeminiClient's
// success-only reportToServer call) - and tracks which players have already been credited with
// visiting it, so ServerStructureTracker's per-second scan can tell a brand-new visit apart from
// a repeat one. Scoped per-dimension the same way StructureLoreData is, via level.getDataStorage().
public class PlayerCivWaypointData extends SavedData {
    private static final String STORAGE_ID = "mcaichat_civ_waypoints";

    public record CivEntry(String name, BlockPos pos) {}

    private final Map<String, CivEntry> civs = new HashMap<>(); // keyed by structureId
    private final Map<UUID, Set<String>> visited = new HashMap<>(); // player -> visited structureIds

    public static PlayerCivWaypointData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlayerCivWaypointData::load, PlayerCivWaypointData::new, STORAGE_ID);
    }

    public String getName(String structureId) {
        CivEntry entry = civs.get(structureId);
        return entry == null ? null : entry.name();
    }

    public CivEntry get(String structureId) {
        return civs.get(structureId);
    }

    // First-write-wins for both name and position - a structure's name/position never change
    // once first recorded, same contract as StructureLoreData.addIfAbsent.
    public void recordName(String structureId, String name) {
        CivEntry existing = civs.get(structureId);
        if (existing != null && existing.name() != null) return;
        civs.put(structureId, new CivEntry(name, existing == null ? null : existing.pos()));
        setDirty();
    }

    public void recordPosition(String structureId, BlockPos pos) {
        CivEntry existing = civs.get(structureId);
        if (existing != null && existing.pos() != null) return;
        civs.put(structureId, new CivEntry(existing == null ? null : existing.name(), pos));
        setDirty();
    }

    // Returns true only the first time this player/structure pair is marked - callers use this to
    // decide whether to actually push a waypoint.
    public boolean markVisited(UUID player, String structureId) {
        Set<String> set = visited.computeIfAbsent(player, k -> new HashSet<>());
        if (!set.add(structureId)) return false;
        setDirty();
        return true;
    }

    public Set<String> getVisited(UUID player) {
        return visited.getOrDefault(player, Set.of());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag civList = new ListTag();
        for (Map.Entry<String, CivEntry> entry : civs.entrySet()) {
            CivEntry civ = entry.getValue();
            if (civ.name() == null || civ.pos() == null) continue; // incomplete entries aren't worth persisting
            CompoundTag civTag = new CompoundTag();
            civTag.putString("id", entry.getKey());
            civTag.putString("name", civ.name());
            civTag.putInt("x", civ.pos().getX());
            civTag.putInt("y", civ.pos().getY());
            civTag.putInt("z", civ.pos().getZ());
            civList.add(civTag);
        }
        tag.put("civs", civList);

        ListTag visitedList = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : visited.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", entry.getKey());
            ListTag ids = new ListTag();
            for (String id : entry.getValue()) {
                ids.add(StringTag.valueOf(id));
            }
            playerTag.put("ids", ids);
            visitedList.add(playerTag);
        }
        tag.put("visited", visitedList);
        return tag;
    }

    private static PlayerCivWaypointData load(CompoundTag tag) {
        PlayerCivWaypointData data = new PlayerCivWaypointData();

        ListTag civList = tag.getList("civs", 10); // 10 = CompoundTag
        for (int i = 0; i < civList.size(); i++) {
            CompoundTag civTag = civList.getCompound(i);
            BlockPos pos = new BlockPos(civTag.getInt("x"), civTag.getInt("y"), civTag.getInt("z"));
            data.civs.put(civTag.getString("id"), new CivEntry(civTag.getString("name"), pos));
        }

        ListTag visitedList = tag.getList("visited", 10); // 10 = CompoundTag
        for (int i = 0; i < visitedList.size(); i++) {
            CompoundTag playerTag = visitedList.getCompound(i);
            UUID player = playerTag.getUUID("player");
            Set<String> ids = new HashSet<>();
            ListTag idList = playerTag.getList("ids", 8); // 8 = StringTag
            for (int j = 0; j < idList.size(); j++) {
                ids.add(idList.getString(j));
            }
            data.visited.put(player, ids);
        }
        return data;
    }
}
