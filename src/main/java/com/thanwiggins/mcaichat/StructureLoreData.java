package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

// Server-authoritative registry of generated structure lore (name/background/category per
// structure id), one per dimension - the server-side counterpart to ClientLoreManager, which is
// now just a synced read-only mirror (see LoreSyncPacket) rather than its own source of truth.
// Lore is still generated client-side (each player's own Gemini key - see GeminiClient), but the
// FIRST client to successfully generate it for a given structure reports it here via
// LoreReportPacket, and every other player - including ones who join later - gets that exact text
// instead of each independently generating their own.
public class StructureLoreData extends SavedData {
    private static final String STORAGE_ID = "mcaichat_lore";

    public record LoreEntry(String name, String background, String type, String fullKey) {}

    private final Map<String, LoreEntry> lore = new HashMap<>();

    public static StructureLoreData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StructureLoreData::load, StructureLoreData::new, STORAGE_ID);
    }

    public LoreEntry get(String structureId) {
        return lore.get(structureId);
    }

    public Map<String, LoreEntry> all() {
        return lore;
    }

    // First-writer-wins, matching ClientLoreManager's existing "generated once, never re-rolled"
    // contract - returns false (does nothing) if this structure already has lore, so a client
    // racing to report the same brand-new structure as another player can't overwrite the winner.
    public boolean addIfAbsent(String structureId, LoreEntry entry) {
        if (lore.containsKey(structureId)) return false;
        lore.put(structureId, entry);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, LoreEntry> entry : lore.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey());
            entryTag.putString("name", entry.getValue().name());
            entryTag.putString("background", entry.getValue().background());
            entryTag.putString("type", entry.getValue().type());
            entryTag.putString("fullKey", entry.getValue().fullKey());
            list.add(entryTag);
        }
        tag.put("lore", list);
        return tag;
    }

    private static StructureLoreData load(CompoundTag tag) {
        StructureLoreData data = new StructureLoreData();
        ListTag list = tag.getList("lore", 10); // 10 = CompoundTag
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.lore.put(entry.getString("id"), new LoreEntry(
                    entry.getString("name"),
                    entry.getString("background"),
                    entry.getString("type"),
                    entry.getString("fullKey")));
        }
        return data;
    }
}
