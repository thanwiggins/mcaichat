package com.thanwiggins.mcaichat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

// Persists a generated name/category/backstory per discovered structure to disk per world, and
// decides whether a newly-entered structure is a civilization (worth a Gemini-written history),
// a nomad camp (name only, no lore call), or a generic adventure location.
public class ClientLoreManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File LORE_DIR = FMLPaths.CONFIGDIR.get().resolve("mcaichat_lore").toFile();
    
    private static Map<String, StructureLore> loreMap = new HashMap<>();
    private static String currentWorldId = "default";

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

    public static void loadWorldLore() {
        if (!LORE_DIR.exists()) LORE_DIR.mkdirs();
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            currentWorldId = "mp_" + mc.getCurrentServer().ip.replace(":", "_");
        } else if (mc.getSingleplayerServer() != null) {
            currentWorldId = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_");
        }

        File loreFile = new File(LORE_DIR, currentWorldId + ".json");
        if (loreFile.exists()) {
            try (FileReader reader = new FileReader(loreFile)) {
                Type type = new TypeToken<HashMap<String, StructureLore>>(){}.getType();
                loreMap = GSON.fromJson(reader, type);
                if (loreMap == null) loreMap = new HashMap<>();
            } catch (Exception e) {
                loreMap = new HashMap<>();
            }
        } else {
            loreMap = new HashMap<>();
        }
    }

    public static void saveWorldLore() {
        if (!LORE_DIR.exists()) LORE_DIR.mkdirs();
        File loreFile = new File(LORE_DIR, currentWorldId + ".json");
        try (FileWriter writer = new FileWriter(loreFile)) {
            GSON.toJson(loreMap, writer);
        } catch (Exception e) {}
    }

    public static StructureLore getLore(String structureId) {
        return loreMap.get(structureId);
    }

    public static void addLore(String structureId, String name, String background, String type, String fullKey) {
        loreMap.put(structureId, new StructureLore(name, background, type, fullKey));
        saveWorldLore();
    }
    
    public static void updateLoreBackground(String structureId, String background) {
        if (loreMap.containsKey(structureId)) {
            loreMap.get(structureId).background = background;
            saveWorldLore();
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
        } else {
            String category = "adventure";
            addLore(structureId, formattedType, "A hidden adventure structure.", category, structureType);
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