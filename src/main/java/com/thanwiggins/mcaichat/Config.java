package com.thanwiggins.mcaichat;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.trading.Merchant;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> PLAYER_DISPLAY_NAME;
    public static final ForgeConfigSpec.ConfigValue<String> PLAYER_DESCRIPTION;
    public static final ForgeConfigSpec.ConfigValue<String> WHITELIST_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<String> BLACKLIST_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<String> WANDERER_ENTITIES;
    public static final ForgeConfigSpec.IntValue HOME_RADIUS;
    public static final ForgeConfigSpec.IntValue MIN_LOCATION_DISTANCE;


    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_MONSTERS;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_CREATURES;
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_WILDLIFE;
    
    public static final ForgeConfigSpec.ConfigValue<String> CIV_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<String> NOMAD_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<String> ADVENTURE_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<String> IGNORED_STRUCTURES;

    static {
        BUILDER.push("Gemini API Settings");
        API_KEY = BUILDER.comment("Enter your Google Gemini API Key here")
                         .define("apiKey", "");
        BUILDER.pop();

        BUILDER.push("Game Settings");
        PLAYER_DISPLAY_NAME = BUILDER.comment("Optional name NPCs will know you by instead of your Minecraft username. Leave blank to use your username.")
                         .define("playerDisplayName", "");

        PLAYER_DESCRIPTION = BUILDER.comment("Optional short description of yourself NPCs can reference (e.g. 'a wandering blacksmith'). Leave blank for none.")
                         .define("playerDescription", "");

        WHITELIST_ENTITIES = BUILDER.comment("Comma-separated list of entity IDs that can be chatted with.")
                         .define("whitelistEntities", "minecraft:villager");

        BLACKLIST_ENTITIES = BUILDER.comment("Comma-separated list of entity IDs that the AI should completely ignore.")
                         .define("blacklistEntities", "minecraft:armor_stand");

        WANDERER_ENTITIES = BUILDER.comment("Entities that are always treated as wanderers and never assigned a home structure, even if standing inside one - useful for entities like Wandering Traders that can spawn within a structure's bounds without actually being native to it.")
                         .define("wandererEntities", "minecraft:wandering_trader");

        HOME_RADIUS = BUILDER.comment("Radius (in blocks) an NPC is biased to stay within once it's claimed a home. Doesn't stop them leaving outright, just penalizes wandering further out.")
                         .defineInRange("homeRadius", 32, 1, Integer.MAX_VALUE);

        MIN_LOCATION_DISTANCE = BUILDER.comment("Minimum distance (in blocks) a player-founded location (/base new, /base claim) must be from any other existing structure, civilization, or player-founded location.")
                         .defineInRange("minLocationDistance", 150, 0, Integer.MAX_VALUE);

        CUSTOM_MONSTERS = BUILDER.comment("Entities forced to be classified as monsters").define("customMonsters", "");
        CUSTOM_CREATURES = BUILDER.comment("Entities forced to be classified as creatures").define("customCreatures", "");
        CUSTOM_WILDLIFE = BUILDER.comment("Entities forced to be classified as ambient wildlife").define("customWildlife", "");
        
        CIV_STRUCTURES = BUILDER.comment("Structures forced to be classified as civilizations").define("civStructures", "");
        NOMAD_STRUCTURES = BUILDER.comment("Structures forced to be classified as nomadic camps (no lore)").define("nomadStructures", "");
        ADVENTURE_STRUCTURES = BUILDER.comment("Structures forced to be classified as adventure locations").define("adventureStructures", "");
        IGNORED_STRUCTURES = BUILDER.comment("Structures completely ignored by the AI").define("ignoredStructures", "");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
    
    public static boolean isInList(ForgeConfigSpec.ConfigValue<String> config, String id) {
        String current = config.get();
        if (current == null || current.isEmpty()) return false;
        return Arrays.stream(current.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(id));
    }
    
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

    public static boolean isWanderer(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return false;
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return isInList(WANDERER_ENTITIES, registryName);
    }

    // Mods that implement NPC AI (guarding, patrolling, following, weapon cooldowns) as invisible
    // MobEffects rather than real gameplay conditions. Valarian Conquest applies these permanently
    // (durations in the hundreds of thousands of ticks) whenever an NPC is given a command, so an
    // NPC would otherwise narrate them as if they were an actual potion effect on their character.
    private static final java.util.Set<String> AI_STATE_EFFECT_MODS = java.util.Set.of("valarian_conquest");

    // Valarian Conquest's guard/stand-ground AI reapplies vanilla Slowness at amplifier 200 every
    // tick to lock an NPC in place - a movement-lock hack, not a real status condition an NPC would
    // describe feeling. Amplifier 1 (e.g. from stepping on spikes) is a real gameplay effect and
    // is left alone.
    private static final int FAKE_SLOWNESS_AMPLIFIER_THRESHOLD = 200;

    // Canonical "is this entity hostile toward this specific player" check - shared by
    // PromptBuilder (which delegates its getSentimentColorCode/isHostileToPlayer here for the
    // system-prompt "aggressive" tag) and DirectiveGoal (which uses it to refuse to carry out a
    // /follow, /goto, or /stay directive). Kept here, not in PromptBuilder, since PromptBuilder
    // pulls in client-only classes elsewhere and this needs to be safely callable from server-side
    // AI code too.
    public static String getSentimentColorCode(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) {
        boolean isMonster = target instanceof net.minecraft.world.entity.monster.Monster;
        boolean isValarianFighter = isValarianFighter(target);

        if (isValarianFighter && target.getTeam() != null) {
            if (target.isAlliedTo(player)) {
                return "§a"; // Green (Friendly/Allied)
            } else if (player.getTeam() != null) {
                return "§c"; // Red (Hostile/Enemy Faction)
            } else {
                return "§e"; // Yellow (Suspicious/Unaligned)
            }
        }

        return isMonster ? "§c" : "§a"; // Red for monsters, Green for normal friendly entities
    }

    public static boolean isHostileToPlayer(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) {
        return getSentimentColorCode(player, target).equals("§c");
    }

    // Faction standing between two (non-player) entities - NEUTRAL unless both are Valarian
    // Conquest fighters with assigned teams, in which case ALLIED/HOSTILE mirrors their team
    // alliance. Used by PromptBuilder's danger scan so an NPC can tell friendly reinforcements
    // apart from an enemy raiding party.
    public static EntityRelation getFactionRelation(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        if (isValarianFighter(a) && isValarianFighter(b) && a.getTeam() != null && b.getTeam() != null) {
            return a.isAlliedTo(b) ? EntityRelation.ALLIED : EntityRelation.HOSTILE;
        }
        return EntityRelation.NEUTRAL;
    }

    private static boolean isValarianFighter(net.minecraft.world.entity.Entity entity) {
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return registryName.equals("valarian_conquest:archer") || registryName.equals("valarian_conquest:soldier");
    }

    // Whether this entity is capable of fighting back if attacked - used by /base claim to
    // refuse claiming a structure that still has living defenders.
    public static boolean isCapableFighter(net.minecraft.world.entity.Entity entity) {
        String registryName = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        boolean isMonster = entity instanceof net.minecraft.world.entity.monster.Monster;
        boolean isIronGolem = entity instanceof IronGolem;
        boolean isGuardVillager = registryName.equals("guardvillagers:guard");
        return isMonster || isIronGolem || isGuardVillager || isValarianFighter(entity);
    }

    // One-word-ish capability tag ("Warrior", "Merchant", etc.) shared by the full system prompt
    // and NameplateRenderer/SyncNPCPacket's social-circle registration, so both stay in sync.
    public static String getShortCapabilityString(net.minecraft.world.entity.Entity target) {
        boolean isCapableFighter = isCapableFighter(target);
        boolean isMerchant = target instanceof Merchant;

        if (isCapableFighter && isMerchant) return "Warrior & Merchant";
        if (isCapableFighter) return "Warrior";
        if (isMerchant) return "Merchant";
        return "Citizen";
    }

    public enum EntityRelation {
        ALLIED,
        HOSTILE,
        NEUTRAL
    }

    public static boolean isNarrativeEffect(net.minecraft.world.effect.MobEffectInstance effect) {
        net.minecraft.world.effect.MobEffect mobEffect = effect.getEffect();

        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(mobEffect);
        if (id != null && AI_STATE_EFFECT_MODS.contains(id.getNamespace())) return false;

        if (mobEffect == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN
                && effect.getAmplifier() >= FAKE_SLOWNESS_AMPLIFIER_THRESHOLD) {
            return false;
        }

        return true;
    }
}