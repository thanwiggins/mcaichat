package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

public class PromptBuilder {
    private static final File PROMPT_FILE = FMLPaths.CONFIGDIR.get().resolve("mcaichat_prompt.txt").toFile();

    private static final String DEFAULT_PROMPT = """
        You are an NPC in Minecraft. The player is chatting with you in-game.
        Keep your responses concise and natural. Do not include roleplay actions.
        The following context will help you know how to respond and how to act towards the player.
        
        === Ambient Details ===
        {AMBIENT_DETAILS}
        
        === World Knowledge ===
        {WORLD_KNOWLEDGE}
        
        === Personal Background ===
        {PERSONAL_BACKGROUND}
        
        === Exigent Circumstances ===
        {EXIGENT_CIRCUMSTANCES}
        """;

    public static String getSystemPrompt(Player player, Entity target) {
        String basePrompt = loadPromptFile();
        
        Level level = player.level();
        BlockPos pos = target.blockPosition();

        // 1. Ambient Details
        String ambient = buildAmbientDetails(level, pos);
        
        // 2. World Knowledge (Placeholders for Phase 4 & 5)
        String world = buildWorldKnowledge(level, pos);
        
        // 3. Personal Background
        String background = buildPersonalBackground(player, target);
        
        // 4. Exigent Circumstances
        String exigent = buildExigentCircumstances(level, player, target);

        // Inject into the template
        return basePrompt
                .replace("{AMBIENT_DETAILS}", ambient)
                .replace("{WORLD_KNOWLEDGE}", world)
                .replace("{PERSONAL_BACKGROUND}", background)
                .replace("{EXIGENT_CIRCUMSTANCES}", exigent.isEmpty() ? "None." : exigent);
    }

    private static String loadPromptFile() {
        try {
            if (!PROMPT_FILE.exists()) {
                Files.writeString(PROMPT_FILE.toPath(), DEFAULT_PROMPT);
            }
            return Files.readString(PROMPT_FILE.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return DEFAULT_PROMPT;
        }
    }

    private static String buildAmbientDetails(Level level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        String biomeNameRaw = biomeHolder.unwrapKey().map(key -> key.location().getPath()).orElse("unknown");
        String biomeName = formatName(biomeNameRaw); // Capitalizes the biome name
        
        long time = level.getDayTime() % 24000;
        String timeOfDay = (time < 12000) ? "Daytime" : (time < 13000) ? "Dusk" : (time < 23000) ? "Nighttime" : "Dawn";
        
        String weather = level.isThundering() ? "Thunderstorm" : level.isRaining() ? "Raining" : "Clear";
        
        // --- Serene Seasons Integration via Reflection ---
        String season = "Unknown Season"; 
        if (ModList.get().isLoaded("sereneseasons")) {
            try {
                Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
                Method getSeasonState = helperClass.getMethod("getSeasonState", Level.class);
                Object seasonState = getSeasonState.invoke(null, level);
                
                Class<?> stateClass = Class.forName("sereneseasons.api.season.ISeasonState");
                Method getSeason = stateClass.getMethod("getSeason");
                Object seasonEnum = getSeason.invoke(seasonState);
                
                if (seasonEnum != null) {
                    season = seasonEnum.toString();
                    season = season.substring(0, 1).toUpperCase() + season.substring(1).toLowerCase();
                }
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Failed to fetch Serene Seasons data.");
                e.printStackTrace();
            }
        }

        return String.format("Biome: %s | Time: %s | Weather: %s | Season: %s", biomeName, timeOfDay, weather, season);
    }

    private static String buildWorldKnowledge(Level level, BlockPos pos) {
        // TODO for Phase 4: Query SavedData for Lore and nearest structures
        String knowledge = "Home: Unknown\nNearby Structures: Unknown";
        
        // 5% chance to know about a secret structure
        if (new Random().nextInt(100) < 5) {
            knowledge += "\nSecret: You have heard rumors of a dangerous hidden structure nearby.";
        }
        
        return knowledge;
    }

    private static String buildPersonalBackground(Player player, Entity target) {
        CompoundTag data = target.getPersistentData();
        String name = data.getString("mcaichat_name");
        
        // Ensure Personality starts with a capital letter
        String personality = data.getString("mcaichat_personality");
        if (personality != null && !personality.isEmpty()) {
            personality = personality.substring(0, 1).toUpperCase() + personality.substring(1);
        }
        
        // Capitalize the Entity Type
        String entityTypeRaw = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).getPath();
        String entityType = formatName(entityTypeRaw);
        
        // Capabilities and Sentiment logic
        boolean isMonster = target instanceof Monster;
        String sentiment = isMonster ? "Hostile toward the player" : "Friendly toward the player";
        String capability = isMonster ? "Trained Warrior - Has Fighting Abilities" : "Normal Citizen - No Fighting Abilities";

        // --- VALARIAN CONQUEST INTEGRATION (Using Vanilla Scoreboard Teams) ---
        String targetRegistryName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        if (targetRegistryName.equals("valarian_conquest:archer") || targetRegistryName.equals("valarian_conquest:soldier")) {
            
            capability = "Trained Warrior - Has Fighting Abilities"; // We know they can fight
            
            // Check if the entity is actually assigned to a faction (team)
            if (target.getTeam() != null) {
                String factionName = target.getTeam().getName();
                
                if (target.isAlliedTo(player)) {
                    sentiment = "Friendly and loyal to the player (You are both in the '" + factionName + "' faction)";
                } else if (player.getTeam() != null) {
                    sentiment = "Hostile toward the player (The player belongs to an enemy faction)";
                } else {
                    sentiment = "Suspicious and guarded (The player is unaligned/not in a faction)";
                }
            }
        }

        // Memory statement now has no period
        return String.format("Name: %s\nEntity Type: %s\nPersonality: %s\nSentiment: %s\nCapabilities: %s\nMemory: None (First interaction with the player)", 
                name, entityType, personality, sentiment, capability);
    }

    private static String buildExigentCircumstances(Level level, Player player, Entity target) {
        StringBuilder exigent = new StringBuilder();

        // Player Vitals
        if (player.getHealth() <= 6.0f) exigent.append("The player is severely injured. ");
        if (player.getFoodData().getFoodLevel() <= 6) exigent.append("The player is starving. ");

        // Loop through the player's active potion effects (Poison, Weakness, Speed, etc.)
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String effectName = effect.getEffect().getDisplayName().getString();
            exigent.append("The player has a '").append(effectName).append("' status effect. ");
        }

        // Scan a 5x5x5 area around the player for fire blocks
        BlockPos playerPos = player.blockPosition();
        int radius = 5;
        boolean fireFound = false;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    if (level.getBlockState(checkPos).is(Blocks.FIRE)) {
                        fireFound = true;
                        break;
                    }
                }
                if (fireFound) break;
            }
            if (fireFound) break;
        }
        
        if (fireFound) {
            exigent.append("A dangerous fire is burning nearby! ");
        }
        
        // Nearby Monsters (Filter out any whitelisted chat entities)
        AABB dangerBox = target.getBoundingBox().inflate(15.0D);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, dangerBox, entity -> !Config.isWhitelisted(entity));
        
        if (!monsters.isEmpty()) {
            String monsterNameRaw = monsters.get(0).getDisplayName().getString();
            exigent.append("There are hostile monsters nearby (").append(monsterNameRaw).append(")! ");
        }

        if (target instanceof LivingEntity livingTarget) {
            // Entity Health Check
            float currentHealth = livingTarget.getHealth();
            float maxHealth = livingTarget.getMaxHealth();
            if (currentHealth <= (maxHealth * 0.3f)) { // If health is at 30% or lower
                exigent.append("You are severely injured and near death. ");
            }
            
            // Entity Status Effects Check (e.g., Poison, Slowness, Wither)
            for (MobEffectInstance effect : livingTarget.getActiveEffects()) {
                String effectName = effect.getEffect().getDisplayName().getString();
                exigent.append("You are currently suffering from the '").append(effectName).append("' status effect. ");
            }
        }

        return exigent.toString().trim();
    }

    /**
     * Helper method to capitalize registry names (e.g. "dark_forest" -> "Dark Forest")
     */
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