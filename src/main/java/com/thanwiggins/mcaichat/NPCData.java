package com.thanwiggins.mcaichat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Flavor-text word lists loaded from bundled CSVs: NPC names/personalities, and realm names
// used both for NPC home lore and for suggesting a name on the world-creation screen.
public class NPCData {
    public static final List<String> NAMES = new ArrayList<>();
    public static final List<String> PERSONALITIES = new ArrayList<>();
    public static final List<String> REALMS = new ArrayList<>();

    static {
        loadListFromFile("/assets/mcaichat/Names.csv", NAMES, "Alex");
        loadListFromFile("/assets/mcaichat/Personalities.csv", PERSONALITIES, "Mysterious and quiet.");
        loadListFromFile("/assets/mcaichat/Realms.csv", REALMS, "Oakhaven");
    }

    private static void loadListFromFile(String path, List<String> list, String fallback) {
        try {
            InputStream is = NPCData.class.getResourceAsStream(path);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equals("#VALUE!")) {
                        list.add(line);
                    }
                }
                reader.close();
            } else {
                System.err.println("[MC-AI Chat] WARNING: Could not find resource file: " + path);
                list.add(fallback);
            }
        } catch (Exception e) {
            System.err.println("[MC-AI Chat] ERROR: Failed to read resource file: " + path);
            e.printStackTrace();
            list.add(fallback);
        }
    }

    public static String getRandomName(Random random) {
        if (NAMES.isEmpty()) return "Unknown";
        return NAMES.get(random.nextInt(NAMES.size()));
    }

    public static String getRandomPersonality(Random random) {
        if (PERSONALITIES.isEmpty()) return "Blank slate.";
        return PERSONALITIES.get(random.nextInt(PERSONALITIES.size()));
    }

    public static String getRandomRealm(Random random) {
        if (REALMS.isEmpty()) return "Unknown Realm";
        return REALMS.get(random.nextInt(REALMS.size()));
    }
}