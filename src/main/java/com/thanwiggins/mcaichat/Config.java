package com.thanwiggins.mcaichat;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> WHITELIST_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<String> BLACKLIST_ENTITIES; // Added Blacklist Variable

    static {
        BUILDER.push("Gemini API Settings");
        API_KEY = BUILDER.comment("Enter your Google Gemini API Key here")
                         .define("apiKey", "");
        BUILDER.pop();

        BUILDER.push("Game Settings");
        WHITELIST_ENTITIES = BUILDER.comment("Comma-separated list of entity IDs that can be chatted with.")
                         .define("whitelistEntities", "minecraft:villager");
                         
        // Define the Blacklist (Defaults to ignoring armor stands)
        BLACKLIST_ENTITIES = BUILDER.comment("Comma-separated list of entity IDs that the AI should completely ignore.")
                         .define("blacklistEntities", "minecraft:armor_stand");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
    
    public static boolean isWhitelisted(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return false;
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        String whitelistStr = WHITELIST_ENTITIES.get();
        if (whitelistStr == null || whitelistStr.isEmpty()) return false;
        
        return java.util.Arrays.stream(whitelistStr.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equalsIgnoreCase(registryName));
    }

    // New Blacklist Check Method
    public static boolean isBlacklisted(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return true; // Treat null as blacklisted so it's ignored
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        String blacklistStr = BLACKLIST_ENTITIES.get();
        if (blacklistStr == null || blacklistStr.isEmpty()) return false;
        
        return java.util.Arrays.stream(blacklistStr.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equalsIgnoreCase(registryName));
    }
}