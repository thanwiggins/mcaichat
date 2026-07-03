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
import java.util.UUID;

// Persists a rolling one-paragraph memory summary per NPC to disk (one JSON file per world),
// so an NPC still remembers past conversations after the player logs out and back in.
public class ClientMemoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File MEMORY_DIR = FMLPaths.CONFIGDIR.get().resolve("mcaichat_memory").toFile();

    private static Map<UUID, EntityMemory> memoryMap = new HashMap<>();
    private static String currentWorldId = "default";

    public static class EntityMemory {
        public String summary;
        public long lastConvoTick; // in-game tick, not wall-clock time, so elapsed time reads correctly across sessions

        public EntityMemory(String summary, long lastConvoTick) {
            this.summary = summary;
            this.lastConvoTick = lastConvoTick;
        }
    }

    public static void loadWorldMemory() {
        if (!MEMORY_DIR.exists()) MEMORY_DIR.mkdirs();
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            currentWorldId = "mp_" + mc.getCurrentServer().ip.replace(":", "_");
        } else if (mc.getSingleplayerServer() != null) {
            currentWorldId = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_");
        }

        File memoryFile = new File(MEMORY_DIR, currentWorldId + ".json");
        if (memoryFile.exists()) {
            try (FileReader reader = new FileReader(memoryFile)) {
                Type type = new TypeToken<HashMap<UUID, EntityMemory>>(){}.getType();
                memoryMap = GSON.fromJson(reader, type);
                if (memoryMap == null) memoryMap = new HashMap<>();
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Failed to load memory file.");
                memoryMap = new HashMap<>();
            }
        } else {
            memoryMap = new HashMap<>();
        }
    }

    public static void saveWorldMemory() {
        if (!MEMORY_DIR.exists()) MEMORY_DIR.mkdirs();
        File memoryFile = new File(MEMORY_DIR, currentWorldId + ".json");
        try (FileWriter writer = new FileWriter(memoryFile)) {
            GSON.toJson(memoryMap, writer);
        } catch (Exception e) {
            System.err.println("[MC-AI Chat] Failed to save memory file.");
        }
    }

    public static EntityMemory getMemory(UUID entityId) {
        return memoryMap.get(entityId);
    }

    public static void updateMemory(UUID entityId, String newSummary, long currentTick) {
        memoryMap.put(entityId, new EntityMemory(newSummary, currentTick));
        saveWorldMemory();
    }

    // Deletes memory files for worlds that no longer show up in the singleplayer world list -
    // called by WorldDataCleaner once that list (re)loads, since that's the only reliable moment
    // we know a world was deleted.
    public static void pruneDeletedWorlds(Set<String> existingWorldIds) {
        File[] files = MEMORY_DIR.listFiles((dir, name) -> name.startsWith("sp_") && name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            String worldId = file.getName().substring(0, file.getName().length() - ".json".length());
            if (!existingWorldIds.contains(worldId)) {
                file.delete();
            }
        }
    }
}