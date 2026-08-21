package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;

// Client-side cache of the list-type settings Config.java would otherwise read locally: which
// whitelist/blacklist/wanderer entities and structure categorizations to use. Populated only by
// EffectiveConfigSyncPacket - the server resolves these once (a dedicated server's hardcoded
// defaults, or a hosted world's host config) and broadcasts the result, so every client-side
// decision matches whatever the server actually enforces, not this client's own local Config.
// See Config.getEffectiveList for the server-side counterpart of this same resolution.
public class EffectiveConfig {
    public static String whitelistEntities = "minecraft:villager";
    public static String blacklistEntities = "minecraft:armor_stand";
    public static String wandererEntities = "minecraft:wandering_trader";
    public static String customMonsters = "";
    public static String customCreatures = "";
    public static String customWildlife = "";
    public static String civStructures = "";
    public static String nomadStructures = "";
    public static String adventureStructures = "";
    public static String ignoredStructures = "";

    public static void replaceAll(CompoundTag data) {
        whitelistEntities = data.getString("whitelistEntities");
        blacklistEntities = data.getString("blacklistEntities");
        wandererEntities = data.getString("wandererEntities");
        customMonsters = data.getString("customMonsters");
        customCreatures = data.getString("customCreatures");
        customWildlife = data.getString("customWildlife");
        civStructures = data.getString("civStructures");
        nomadStructures = data.getString("nomadStructures");
        adventureStructures = data.getString("adventureStructures");
        ignoredStructures = data.getString("ignoredStructures");
    }

    public static boolean isInList(String list, String id) {
        if (list == null || list.isEmpty()) return false;
        return Arrays.stream(list.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(id));
    }

    public static boolean isWhitelisted(Entity entity) {
        if (entity == null) return false;
        String registryName = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return isInList(whitelistEntities, registryName);
    }

    public static boolean isBlacklisted(Entity entity) {
        if (entity == null) return true;
        String registryName = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return isInList(blacklistEntities, registryName);
    }

    public static boolean isWanderer(Entity entity) {
        if (entity == null) return false;
        String registryName = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return isInList(wandererEntities, registryName);
    }
}
