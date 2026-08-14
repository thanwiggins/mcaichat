package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

// Client-side mirror of the server's StructureLoreData (kept fresh via LoreSyncPacket), and the
// decision-maker for whether a newly-entered structure is a civilization (worth a Gemini-written
// history), a nomad camp (name only, no lore call), or a generic adventure location. Lore is still
// generated here using the discovering player's own Gemini key, but the result is reported to the
// server (see reportToServer/LoreReportPacket) so it becomes the one shared answer for everyone,
// rather than a per-client local cache - unlike ClientLocationManager's data, this used to be
// cached to disk per world; that's gone now that the server is the source of truth.
public class ClientLoreManager {
    private static Map<String, StructureLore> loreMap = new HashMap<>();

    // The structure/roost the player is currently standing in, used by PromptBuilder to tag
    // an NPC's home as "(here)" when the player and the NPC's home coincide.
    public static String currentStructureId = "none";

    public static boolean debugLore = false;

    public static class StructureLore {
        public String name;
        public String background;
        public String type;
        public String fullKey;

        public StructureLore(String name, String background, String type, String fullKey) {
            this.name = name;
            this.background = background;
            this.type = type;
            this.fullKey = fullKey;
        }
    }

    public static StructureLore getLore(String structureId) {
        return loreMap.get(structureId);
    }

    public static void addLore(String structureId, String name, String background, String type, String fullKey) {
        loreMap.put(structureId, new StructureLore(name, background, type, fullKey));
    }

    public static void updateLoreBackground(String structureId, String background) {
        if (loreMap.containsKey(structureId)) {
            loreMap.get(structureId).background = background;
        }
    }

    // Sends this structure's current entry to the server as this client's report - called only
    // once a structure's lore is actually final (nomad/adventure immediately, civilization once
    // its Gemini call succeeds), never for the "Discovering the history..." placeholder or a
    // failed-generation fallback, so a transient failure can't permanently lock in bad lore for
    // everyone. The server only accepts the first report for a given structure (see
    // StructureLoreData.addIfAbsent) - if another player already reported it first, this is a
    // harmless no-op and the next LoreSyncPacket corrects this client's own copy to match.
    public static void reportToServer(String structureId) {
        StructureLore lore = loreMap.get(structureId);
        if (lore == null) return;

        NetworkHandler.INSTANCE.sendToServer(new LoreReportPacket(structureId, lore.name, lore.background, lore.type, lore.fullKey));
    }

    // Merges one or more server-reported entries (a full dump on login, or a single new entry
    // whenever the server accepts a report) into this client's map. Always overwrites - the
    // server is authoritative, so even if this client generated its own (different) placeholder
    // for a structure, an incoming entry for that same id is the winning version.
    public static void merge(CompoundTag data) {
        ListTag list = data.getList("lore", 10); // 10 = CompoundTag
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            addLore(entry.getString("id"), entry.getString("name"), entry.getString("background"),
                    entry.getString("type"), entry.getString("fullKey"));
        }
    }

    public static Set<String> getKnownStructureKeys() {
        Set<String> keys = new HashSet<>();
        for (StructureLore lore : loreMap.values()) {
            if (lore.fullKey != null && !lore.fullKey.isEmpty()) {
                keys.add(lore.fullKey);
            }
        }
        return keys;
    }

    public static void onStructureEntered(String structureId, String structureType, String biomeRaw) {
        if (structureId.equals("none") || structureId.equals(currentStructureId)) {
            currentStructureId = structureId;
            return;
        }

        currentStructureId = structureId;

        // Lore is generated once per structure and cached forever after - re-entering a
        // known structure should never re-roll its name/category or re-trigger a Gemini call.
        // This also naturally skips generation entirely once the server has already synced this
        // structure's lore in from another player - see LoreSyncPacket.
        if (loreMap.containsKey(structureId)) return;

        if (Config.isInList(Config.IGNORED_STRUCTURES, structureType)) return;

        boolean isCiv = false;
        boolean isNomad = false;

        if (Config.isInList(Config.CIV_STRUCTURES, structureType)) {
            isCiv = true;
        } else if (Config.isInList(Config.NOMAD_STRUCTURES, structureType)) {
            isNomad = true;
        } else if (Config.isInList(Config.ADVENTURE_STRUCTURES, structureType)) {
            // Both remain false (Adventure Structure)
        } else if (structureType.startsWith("iceandfire:") && structureType.endsWith("_dragon_roost")) {
            // Dragon roosts are detected via the resident dragon's home position, not a registered Structure
            isNomad = true;
        } else {
            isCiv = structureType.contains("village") || structureType.contains("city") ||
                    structureType.contains("bastion") || structureType.contains("fortress") ||
                    structureType.contains("towns_and_towers") || structureType.contains("valarian_conquest");
        }

        String rawType = structureType.contains(":") ? structureType.substring(structureType.indexOf(":") + 1) : structureType;
        String formattedBiome = formatName(biomeRaw);
        String formattedType = formatName(rawType);

        if (isCiv) {
            String category = "civilization";
            String name = NPCData.getRandomRealm(new java.util.Random());
            addLore(structureId, name, "Discovering the history of this place...", category, structureType);
            String apiKey = Config.API_KEY.get();
            if (apiKey != null && !apiKey.isEmpty()) {
                GeminiClient.generateStructureLore(apiKey, structureId, formattedType, name, category, formattedBiome);
            }
        } else if (isNomad) {
            String category = "nomad";
            String name = formattedType;
            addLore(structureId, name, "A nomadic settlement.", category, structureType);
            reportToServer(structureId);
        } else {
            String category = "adventure";
            addLore(structureId, formattedType, "A hidden adventure structure.", category, structureType);
            reportToServer(structureId);
        }
    }

    public static String formatName(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.replace("_", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
