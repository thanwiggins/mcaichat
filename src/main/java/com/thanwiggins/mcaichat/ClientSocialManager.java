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
import java.util.UUID;

// Persists which NPCs share a home structure (their "social circle") to disk per world, so NPCs
// can reference their neighbors/roommates by name - and know if one of them has since died.
public class ClientSocialManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SOCIAL_DIR = FMLPaths.CONFIGDIR.get().resolve("mcaichat_social").toFile();
    
    private static Map<String, Map<UUID, CitizenProfile>> socialMap = new HashMap<>();
    private static String currentWorldId = "default";

    public static class CitizenProfile {
        public String name;
        public String type;
        public String personality;
        public String capabilities;
        public boolean isDeceased;
        public String causeOfDeath;

        public CitizenProfile(String name, String type, String personality, String capabilities) {
            this.name = name;
            this.type = type;
            this.personality = personality;
            this.capabilities = capabilities;
            this.isDeceased = false;
            this.causeOfDeath = "";
        }
    }

    public static void loadWorldSocial() {
        if (!SOCIAL_DIR.exists()) SOCIAL_DIR.mkdirs();
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            currentWorldId = "mp_" + mc.getCurrentServer().ip.replace(":", "_");
        } else if (mc.getSingleplayerServer() != null) {
            currentWorldId = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_");
        }

        File socialFile = new File(SOCIAL_DIR, currentWorldId + ".json");
        if (socialFile.exists()) {
            try (FileReader reader = new FileReader(socialFile)) {
                Type type = new TypeToken<HashMap<String, HashMap<UUID, CitizenProfile>>>(){}.getType();
                socialMap = GSON.fromJson(reader, type);
                if (socialMap == null) socialMap = new HashMap<>();
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Failed to load social file.");
                socialMap = new HashMap<>();
            }
        } else {
            socialMap = new HashMap<>();
        }
    }

    public static void saveWorldSocial() {
        if (!SOCIAL_DIR.exists()) SOCIAL_DIR.mkdirs();
        File socialFile = new File(SOCIAL_DIR, currentWorldId + ".json");
        try (FileWriter writer = new FileWriter(socialFile)) {
            GSON.toJson(socialMap, writer);
        } catch (Exception e) {
            System.err.println("[MC-AI Chat] Failed to save social file.");
        }
    }

    public static void addCitizen(String homeId, UUID uuid, String name, String type, String personality, String capabilities) {
        if (homeId == null || homeId.isEmpty() || homeId.equals("none")) return;
        
        socialMap.putIfAbsent(homeId, new HashMap<>());
        Map<UUID, CitizenProfile> citizens = socialMap.get(homeId);
        
        // Only add if they don't exist yet, to not overwrite death status accidentally
        if (!citizens.containsKey(uuid)) {
            citizens.put(uuid, new CitizenProfile(name, type, personality, capabilities));
            saveWorldSocial();
        }
    }

    public static void markDeceased(UUID uuid, String cause) {
        boolean changed = false;
        for (Map<UUID, CitizenProfile> citizens : socialMap.values()) {
            if (citizens.containsKey(uuid)) {
                citizens.get(uuid).isDeceased = true;
                citizens.get(uuid).causeOfDeath = cause;
                changed = true;
            }
        }
        if (changed) saveWorldSocial();
    }

    public static Map<UUID, CitizenProfile> getCitizens(String homeId) {
        return socialMap.getOrDefault(homeId, new HashMap<>());
    }
}