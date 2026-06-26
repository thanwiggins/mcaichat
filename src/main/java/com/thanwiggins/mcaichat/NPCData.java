package com.thanwiggins.mcaichat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NPCData {
    public static final List<String> NAMES = new ArrayList<>();
    public static final List<String> PERSONALITIES = new ArrayList<>();

    // This static block runs once when the mod starts up
    static {
        loadListFromFile("/assets/mcaichat/Names.csv", NAMES, "Alex");
        loadListFromFile("/assets/mcaichat/Personalities.csv", PERSONALITIES, "Mysterious and quiet.");
    }

    private static void loadListFromFile(String path, List<String> list, String fallback) {
        try {
            // Read the file directly from the mod's bundled resources
            InputStream is = NPCData.class.getResourceAsStream(path);
            
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Ignore empty lines or the word "#VALUE!" from bad excel exports
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
}