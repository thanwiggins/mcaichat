package com.thanwiggins.mcaichat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

// Stores player-authored "special instructions" per entity type (e.g. all Villagers get told
// "you secretly work for the Thieves' Guild"), set from the Creature config screen. This is
// global, not per-world - like the whitelist/blacklist, it's a preference about an entity type
// in general, not something tied to a specific save.
public class EntityInstructionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File INSTRUCTIONS_FILE = FMLPaths.CONFIGDIR.get().resolve("mcaichat_entity_instructions.json").toFile();

    private static Map<String, String> instructions = null;

    private static void ensureLoaded() {
        if (instructions != null) return;

        if (INSTRUCTIONS_FILE.exists()) {
            try (FileReader reader = new FileReader(INSTRUCTIONS_FILE)) {
                Type type = new TypeToken<HashMap<String, String>>(){}.getType();
                instructions = GSON.fromJson(reader, type);
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Failed to load entity instructions file.");
            }
        }

        if (instructions == null) instructions = new HashMap<>();
    }

    public static String get(String entityId) {
        ensureLoaded();
        return instructions.getOrDefault(entityId, "");
    }

    public static void set(String entityId, String instruction) {
        ensureLoaded();
        if (instruction == null || instruction.isBlank()) {
            instructions.remove(entityId);
        } else {
            instructions.put(entityId, instruction);
        }
        save();
    }

    private static void save() {
        try (FileWriter writer = new FileWriter(INSTRUCTIONS_FILE)) {
            GSON.toJson(instructions, writer);
        } catch (Exception e) {
            System.err.println("[MC-AI Chat] Failed to save entity instructions file.");
        }
    }
}
