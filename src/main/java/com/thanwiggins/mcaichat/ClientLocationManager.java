package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Client-side mirror of the server's PlayerLocationData, kept fresh via LocationSyncPacket.
// Unlike ClientLoreManager/ClientSocialManager this isn't cached to disk - it's server-authoritative
// truth re-sent on login and on every change, so there's nothing worth persisting locally.
public class ClientLocationManager {
    public static final String PLACEHOLDER_NAME = "Player-Created Structure";

    public static class LocationInfo {
        public final String name;
        public final String description;
        public final Set<String> revealedHomeIds;
        // Non-empty only for locations founded via /base claim - the structure id this location
        // used to be, so PromptBuilder can look up that structure's already-generated lore.
        public final String formerStructureId;

        public LocationInfo(String name, String description, Set<String> revealedHomeIds, String formerStructureId) {
            this.name = name;
            this.description = description;
            this.revealedHomeIds = revealedHomeIds;
            this.formerStructureId = formerStructureId;
        }

        // viewerHomeId is the NPC's own home_id - reveal state is scoped per-civilization,
        // not per-NPC or per-player.
        public String displayName(String viewerHomeId) {
            return revealedHomeIds.contains(viewerHomeId) ? name : PLACEHOLDER_NAME;
        }
    }

    private static Map<String, LocationInfo> locations = new HashMap<>();

    public static void replaceAll(CompoundTag data) {
        Map<String, LocationInfo> updated = new HashMap<>();
        ListTag list = data.getList("locations", 10); // 10 = CompoundTag

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String name = entry.getString("name");
            String description = entry.getString("description");
            String formerStructureId = entry.getString("formerStructureId");

            Set<String> revealed = new HashSet<>();
            ListTag revealedList = entry.getList("revealedHomeIds", 8); // 8 = StringTag
            for (int j = 0; j < revealedList.size(); j++) {
                revealed.add(revealedList.getString(j));
            }

            updated.put("player:" + name.toLowerCase(), new LocationInfo(name, description, revealed, formerStructureId));
        }

        locations = updated;
    }

    public static LocationInfo get(String id) {
        return locations.get(id);
    }
}
