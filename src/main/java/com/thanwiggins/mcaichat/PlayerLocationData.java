package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Server-authoritative registry of player-founded locations (/base new, /base edit), one per
// dimension. Player locations compete for NPC "home" the same way vanilla civ structures do (see
// IdentityHandler.generateWorldKnowledge), so this has to be persistent and shared across every
// NPC/player - unlike the rest of this mod's state, which lives in per-entity NBT.
public class PlayerLocationData extends SavedData {
    private static final String STORAGE_ID = "mcaichat_locations";

    public static class Location {
        public final String name;
        public String description;
        public final UUID creator;
        public final BlockPos pos;
        public final Set<String> revealedHomeIds = new HashSet<>();
        // Set only by /base claim - the structure id this location used to be, so PromptBuilder
        // can splice that structure's already-generated lore (see ClientLoreManager) back into
        // this location's description instead of losing it when the structure gets conquered.
        public String formerStructureId = "";

        public Location(String name, String description, UUID creator, BlockPos pos) {
            this.name = name;
            this.description = description;
            this.creator = creator;
            this.pos = pos;
        }

        // Reused everywhere else in the mod that threads a structId-shaped string
        // (mcaichat_home_id, mcaichat_nearby_civs entries) - stable, since only descriptions
        // are editable, never names.
        public String id() {
            return "player:" + name.toLowerCase();
        }
    }

    private final Map<String, Location> locations = new HashMap<>(); // keyed by name.toLowerCase()

    public static PlayerLocationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlayerLocationData::load, PlayerLocationData::new, STORAGE_ID);
    }

    public boolean exists(String name) {
        return locations.containsKey(name.toLowerCase());
    }

    public Location create(String name, String description, UUID creator, BlockPos pos) {
        String key = name.toLowerCase();
        if (locations.containsKey(key)) return null;

        Location location = new Location(name, description, creator, pos);
        locations.put(key, location);
        setDirty();
        return location;
    }

    public boolean edit(String name, String newDescription, UUID requester) {
        Location location = locations.get(name.toLowerCase());
        if (location == null || !location.creator.equals(requester)) return false;

        location.description = newDescription;
        setDirty();
        return true;
    }

    public Location get(String name) {
        return locations.get(name.toLowerCase());
    }

    public Location getById(String id) {
        for (Location location : locations.values()) {
            if (location.id().equals(id)) return location;
        }
        return null;
    }

    // Location counts are expected to be small (player-founded bases, not world chunks), so a
    // plain linear scan is fine - no need for spatial indexing.
    public List<Location> withinRadius(BlockPos origin, double radiusBlocks) {
        double radiusSqr = radiusBlocks * radiusBlocks;
        List<Location> result = new ArrayList<>();
        for (Location location : locations.values()) {
            if (origin.distSqr(location.pos) <= radiusSqr) result.add(location);
        }
        return result;
    }

    public Collection<Location> all() {
        return locations.values();
    }

    public boolean reveal(String id, String homeId) {
        Location location = getById(id);
        if (location == null || location.revealedHomeIds.contains(homeId)) return false;

        location.revealedHomeIds.add(homeId);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Location location : locations.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", location.name);
            entry.putString("description", location.description);
            entry.putUUID("creator", location.creator);
            entry.putInt("x", location.pos.getX());
            entry.putInt("y", location.pos.getY());
            entry.putInt("z", location.pos.getZ());
            entry.putString("formerStructureId", location.formerStructureId);

            ListTag revealed = new ListTag();
            for (String homeId : location.revealedHomeIds) {
                revealed.add(StringTag.valueOf(homeId));
            }
            entry.put("revealedHomeIds", revealed);

            list.add(entry);
        }
        tag.put("locations", list);
        return tag;
    }

    private static PlayerLocationData load(CompoundTag tag) {
        PlayerLocationData data = new PlayerLocationData();
        ListTag list = tag.getList("locations", 10); // 10 = CompoundTag

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String name = entry.getString("name");
            Location location = new Location(
                    name,
                    entry.getString("description"),
                    entry.getUUID("creator"),
                    new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
            location.formerStructureId = entry.getString("formerStructureId");

            ListTag revealed = entry.getList("revealedHomeIds", 8); // 8 = StringTag
            for (int j = 0; j < revealed.size(); j++) {
                location.revealedHomeIds.add(revealed.getString(j));
            }

            data.locations.put(name.toLowerCase(), location);
        }
        return data;
    }
}
