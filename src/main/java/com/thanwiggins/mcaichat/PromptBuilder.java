package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
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
import java.util.stream.Collectors;

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

        String ambient = buildAmbientDetails(level, pos);
        String world = buildWorldKnowledge(level, pos);
        String background = buildPersonalBackground(player, target);
        String exigent = buildExigentCircumstances(level, player, target);

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
        String biomeName = formatName(biomeNameRaw);
        
        long time = level.getDayTime() % 24000;
        String timeOfDay = (time < 12000) ? "Daytime" : (time < 13000) ? "Dusk" : (time < 23000) ? "Nighttime" : "Dawn";
        
        String weather = level.isThundering() ? "Thunderstorm" : level.isRaining() ? "Raining" : "Clear";
        
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
            }
        }

        Minecraft mc = Minecraft.getInstance();
        String worldName = "Unknown World";
        if (mc.getSingleplayerServer() != null) {
            worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            worldName = mc.getCurrentServer().name;
        }

        return String.format("Biome: %s | Time: %s | Weather: %s | Season: %s | World Name: %s", 
                biomeName, timeOfDay, weather, season, worldName);
    }

    private static String buildWorldKnowledge(Level level, BlockPos pos) {
        String knowledge = "";
        String home = "Unknown";
        
        String currentStructId = ClientLoreManager.currentStructureId;
        
        if (!currentStructId.equals("none")) {
            ClientLoreManager.StructureLore lore = ClientLoreManager.getLore(currentStructId);
            if (lore != null) {
                int structX = pos.getX();
                int structZ = pos.getZ();
                String prettyType = "Structure";
                
                try {
                    int lastUnder = currentStructId.lastIndexOf('_');
                    int secondLastUnder = currentStructId.lastIndexOf('_', lastUnder - 1);
                    
                    int chunkZ = Integer.parseInt(currentStructId.substring(lastUnder + 1));
                    int chunkX = Integer.parseInt(currentStructId.substring(secondLastUnder + 1, lastUnder));
                    
                    structX = chunkX * 16 + 8;
                    structZ = chunkZ * 16 + 8;
                    
                    String rawType = currentStructId.substring(0, secondLastUnder);
                    if(rawType.contains(":")) rawType = rawType.substring(rawType.indexOf(":") + 1);
                    prettyType = formatName(rawType);
                } catch (Exception e) {}
                
                double distance = Math.sqrt(Math.pow(pos.getX() - structX, 2) + Math.pow(pos.getZ() - structZ, 2));

                if (lore.type.equals("civilization")) {
                    if (distance <= 50) {
                        home = lore.name;
                    }
                    knowledge += "Home: " + home + "\n";
                    knowledge += "Location: " + lore.name + " (" + prettyType + ")\n";
                    knowledge += "Local Lore/History: " + lore.background;
                } else if (lore.type.equals("adventure")) {
                    knowledge += "Home: " + home + "\n";
                    knowledge += "Location: Wilderness\n";
                    
                    // UPDATED: Strict 250 block knowledge cutoff for adventure structures
                    if (distance <= 250) {
                        String direction = getDirection(pos.getX(), pos.getZ(), structX, structZ);
                        knowledge += "Secret: You have heard rumors of '" + lore.name + "', a hidden " + prettyType.toLowerCase() + " located " + direction + " of here.";
                    } else if (new java.util.Random().nextInt(100) < 5) {
                        knowledge += "Secret: You have heard rumors of a dangerous hidden structure far away.";
                    }
                }
            } else {
                knowledge += "Home: " + home + "\nLocation: A recently discovered structure (history currently unknown).";
            }
        } else {
            knowledge += "Home: " + home + "\nLocation: None in the immediate vicinity.";
            if (new java.util.Random().nextInt(100) < 5) {
                knowledge += "\nSecret: You have heard rumors of a dangerous hidden structure far away.";
            }
        }
        
        return knowledge.trim();
    }

    private static String buildPersonalBackground(Player player, Entity target) {
        CompoundTag data = target.getPersistentData();
        String name = data.getString("mcaichat_name");
        
        String personality = data.getString("mcaichat_personality");
        if (personality != null && !personality.isEmpty()) {
            personality = personality.substring(0, 1).toUpperCase() + personality.substring(1);
        }
        
        String targetRegistryName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        String entityType = formatName(ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).getPath());
        
        boolean isMonster = target instanceof Monster;
        boolean isIronGolem = target instanceof IronGolem;
        boolean isGuardVillager = targetRegistryName.equals("guardvillagers:guard");
        boolean isValarianFighter = targetRegistryName.equals("valarian_conquest:archer") || targetRegistryName.equals("valarian_conquest:soldier");
        
        boolean isCapableFighter = isMonster || isIronGolem || isGuardVillager || isValarianFighter;
        String capability = isCapableFighter ? "Trained Warrior - Has Fighting Abilities" : "Normal Citizen - No Fighting Abilities";

        String sentiment = isMonster ? "Hostile toward the player (The player is a dangerous enemy that must be eliminated)" : "Friendly and welcoming toward the player";

        if (isValarianFighter) {
            if (target.getTeam() != null) {
                String factionName = target.getTeam().getName();
                if (target.isAlliedTo(player)) {
                    sentiment = "Friendly and loyal to the player (You are both in the '" + factionName + "' faction)";
                } else if (player.getTeam() != null) {
                    sentiment = "Hostile toward the player (The player belongs to an enemy faction and is a dangerous foe that must be eliminated)";
                } else {
                    sentiment = "Suspicious and guarded (The player is unaligned/not in a faction)";
                }
            }
        }

        // Fetch Conversation Memory
        ClientMemoryManager.EntityMemory mem = ClientMemoryManager.getMemory(target.getUUID());
        String memoryStr = "None (First interaction with the player)";
        String timeElapsedStr = "N/A";

        if (mem != null) {
            memoryStr = mem.summary;
            
            // Calculate time purely based on Minecraft game ticks
            long currentTick = player.level().getGameTime();
            long diffTicks = currentTick - mem.lastConvoTick;
            
            // 1 in-game hour = 1000 ticks. 1 in-game day = 24000 ticks.
            long inGameHours = diffTicks / 1000;
            long inGameDays = diffTicks / 24000;
            
            if (diffTicks < 1000) {
                timeElapsedStr = "Moments ago";
            } else if (inGameDays < 1) {
                timeElapsedStr = inGameHours + " in-game hours ago";
            } else {
                timeElapsedStr = inGameDays + " in-game days ago";
            }
        }

        return String.format("Name: %s\nEntity Type: %s\nPersonality: %s\nSentiment: %s\nCapabilities: %s\nMemory: %s\nTime Since Last Conversation: %s", 
                name, entityType, personality, sentiment, capability, memoryStr, timeElapsedStr);
    }

    private static String buildExigentCircumstances(Level level, Player player, Entity target) {
        StringBuilder exigent = new StringBuilder();

        if (player.getHealth() <= 6.0f) exigent.append("The player is severely injured. ");
        if (player.getFoodData().getFoodLevel() <= 6) exigent.append("The player is starving. ");

        for (MobEffectInstance effect : player.getActiveEffects()) {
            String effectName = effect.getEffect().getDisplayName().getString();
            exigent.append("The player has a '").append(effectName).append("' status effect. ");
        }

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
        
        AABB dangerBox = target.getBoundingBox().inflate(5.0D);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, dangerBox, 
                entity -> !Config.isWhitelisted(entity) && !Config.isBlacklisted(entity));
        
        if (!monsters.isEmpty()) {
            String monsterNames = monsters.stream()
                    .map(m -> m.getDisplayName().getString())
                    .distinct()
                    .collect(Collectors.joining(", "));
            exigent.append("There are hostile monsters nearby (").append(monsterNames).append(")! ");
        }

        List<Animal> animals = level.getEntitiesOfClass(Animal.class, dangerBox, 
                entity -> entity != target && !Config.isBlacklisted(entity));
        if (!animals.isEmpty()) {
            String animalNames = animals.stream()
                    .map(a -> a.getDisplayName().getString())
                    .distinct()
                    .collect(Collectors.joining(", "));
            exigent.append("There are animals nearby (").append(animalNames).append("). ");
        }
        
        if (target instanceof LivingEntity livingTarget) {
            float currentHealth = livingTarget.getHealth();
            float maxHealth = livingTarget.getMaxHealth();
            if (currentHealth <= (maxHealth * 0.3f)) {
                exigent.append("You are severely injured and near death. ");
            }
            
            for (MobEffectInstance effect : livingTarget.getActiveEffects()) {
                String effectName = effect.getEffect().getDisplayName().getString();
                exigent.append("You are currently suffering from the '").append(effectName).append("' status effect. ");
            }
        }

        return exigent.toString().trim();
    }

    private static String getDirection(int fromX, int fromZ, int toX, int toZ) {
        int dx = toX - fromX;
        int dz = toZ - fromZ;
        
        if (Math.abs(dx) > Math.abs(dz) * 2) {
            return dx > 0 ? "east" : "west";
        } else if (Math.abs(dz) > Math.abs(dx) * 2) {
            return dz > 0 ? "south" : "north";
        } else if (dx > 0 && dz > 0) {
            return "southeast";
        } else if (dx > 0 && dz < 0) {
            return "northeast";
        } else if (dx < 0 && dz > 0) {
            return "southwest";
        } else {
            return "northwest";
        }
    }

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