package com.thanwiggins.mcaichat;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> WHITELIST_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<String> BLACKLIST_ENTITIES;
    
    // NEW: Configuration overrides
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_MONSTERS;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_WILDLIFE;
    public static final ForgeConfigSpec.ConfigValue<String> CIV_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<String> ADVENTURE_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<String> IGNORED_STRUCTURES;

    static {
        BUILDER.push("Gemini API Settings");
        API_KEY = BUILDER.comment("Enter your Google Gemini API Key here")
                         .define("apiKey", "");
        BUILDER.pop();

        BUILDER.push("Game Settings");
        WHITELIST_ENTITIES = BUILDER.comment("Comma-separated list of entity IDs that can be chatted with.")
                         .define("whitelistEntities", "minecraft:villager");
                         
        BLACKLIST_ENTITIES = BUILDER.comment("Comma-separated list of entity IDs that the AI should completely ignore.")
                         .define("blacklistEntities", "minecraft:armor_stand");
                         
        CUSTOM_MONSTERS = BUILDER.comment("Entities forced to be classified as monsters").define("customMonsters", "");
        CUSTOM_WILDLIFE = BUILDER.comment("Entities forced to be classified as wildlife").define("customWildlife", "");
        
        CIV_STRUCTURES = BUILDER.comment("Structures forced to be classified as civilizations").define("civStructures", "");
        ADVENTURE_STRUCTURES = BUILDER.comment("Structures forced to be classified as adventure locations").define("adventureStructures", "");
        IGNORED_STRUCTURES = BUILDER.comment("Structures completely ignored by the AI").define("ignoredStructures", "");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
    
    // NEW: Helper method to safely read a list
    public static boolean isInList(ForgeConfigSpec.ConfigValue<String> config, String id) {
        String current = config.get();
        if (current == null || current.isEmpty()) return false;
        return Arrays.stream(current.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(id));
    }
    
    // NEW: Helper method to safely manipulate a list
    public static void setCategory(ForgeConfigSpec.ConfigValue<String> config, String id, boolean add) {
        String current = config.get();
        List<String> list = new ArrayList<>(Arrays.asList(current.split(",")));
        list.replaceAll(String::trim);
        list.removeIf(String::isEmpty);

        if (add && !list.contains(id)) list.add(id);
        if (!add) list.remove(id);

        config.set(String.join(",", list));
    }
    
    public static boolean isWhitelisted(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return false;
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return isInList(WHITELIST_ENTITIES, registryName);
    }

    public static boolean isBlacklisted(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return true; 
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return isInList(BLACKLIST_ENTITIES, registryName);
    }
}