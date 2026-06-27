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

public class ClientLoreManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File LORE_DIR = FMLPaths.CONFIGDIR.get().resolve("mcaichat_lore").toFile();
    
    private static Map<String, StructureLore> loreMap = new HashMap<>();
    private static String currentWorldId = "default";
    public static String currentStructureId = "none";
    public static String currentStructureType = "none";
    
    // NEW: Debug toggle for the /aichat lore command
    public static boolean debugLore = false;

    public static class StructureLore {
        public String name;
        public String background;
        public String type;

        public StructureLore(String name, String background, String type) {
            this.name = name;
            this.background = background;
            this.type = type;
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
                System.err.println("[MC-AI Chat] Failed to load lore file.");
                e.printStackTrace();
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
        } catch (Exception e) {
            System.err.println("[MC-AI Chat] Failed to save lore file.");
            e.printStackTrace();
        }
    }

    public static StructureLore getLore(String structureId) {
        return loreMap.get(structureId);
    }

    public static void addLore(String structureId, String name, String background, String type) {
        loreMap.put(structureId, new StructureLore(name, background, type));
        saveWorldLore();
    }

    public static void onStructureEntered(String structureId, String structureType, String biomeRaw) {
        if (structureId.equals("none") || structureId.equals(currentStructureId)) {
            currentStructureId = structureId;
            currentStructureType = structureType;
            return; 
        }
        
        currentStructureId = structureId;
        currentStructureType = structureType;
        
        if (loreMap.containsKey(structureId)) {
            return;
        }
        
        // NEW: Added towns_and_towers and valarian_conquest
        boolean isCiv = structureType.contains("village") || structureType.contains("city") || 
                        structureType.contains("bastion") || structureType.contains("fortress") ||
                        structureType.contains("towns_and_towers") || structureType.contains("valarian_conquest");
        String category = isCiv ? "civilization" : "adventure";
        
        String name = "Unknown";
        if (isCiv) {
            name = NPCData.getRandomRealm(new java.util.Random());
        } else {
            String rawType = structureType.contains(":") ? structureType.substring(structureType.indexOf(":") + 1) : structureType;
            name = formatName(rawType); 
        }

        String formattedBiome = formatName(biomeRaw);
        
        addLore(structureId, name, "Discovering the history of this place...", category);
        
        String apiKey = Config.API_KEY.get();
        if (apiKey != null && !apiKey.isEmpty()) {
            GeminiClient.generateStructureLore(apiKey, structureId, structureType, name, category, formattedBiome);
        }
    }

    private static String formatName(String input) {
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