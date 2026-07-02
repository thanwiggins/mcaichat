package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PromptBuilder {
    private static final File PROMPT_FILE = FMLPaths.CONFIGDIR.get().resolve("mcaichat_prompt.txt").toFile();
    private static final File INIT_PROMPT_FILE = FMLPaths.CONFIGDIR.get().resolve("mcaichat_init_prompt.txt").toFile(); // NEW

    private static final String DEFAULT_PROMPT = """
        You are a character in Minecraft conversing with a player in real-time.
        The following raw context sections define your current environment, your history, your background, and immediate events happening around you.
        
        === Ambient Details ===
        {AMBIENT_DETAILS}
        
        === World Knowledge ===
        {WORLD_KNOWLEDGE}
        
        === Personal Background ===
        {PERSONAL_BACKGROUND}
        
        === Social Circle ===
        {SOCIAL_CIRCLE}
        
        === Exigent Circumstances ===
        {EXIGENT_CIRCUMSTANCES}

        === Response & Story Tips ===
        - Remember that you have your own life, survival concerns, and routines. You are not just a prop waiting for the player. Do not overly protagonize the player.
        - Chat with the player like a character from a realistic RPG adventure story, not a meaningless NPC. Your dialogue should be purposeful and engaging, not hollow and flowerly.
        - Do not try to include all your knowledge in your response. Your knowledge is simply a resource you can draw from to make your character feel real and alive.
        
        [CRITICAL OPERATION & DIALOGUE RULES]
        Review all context above, and generate your response using the following strict priority guidelines:
        1. CORE DRIVERS (HIGHEST PRIORITY): Your response must be driven entirely by your designated Personality and your current Sentiment/Faction Standing towards the player. This defines your active tone, vocabulary, and mood. Keep responses concise and natural.
        2. SITUATIONAL REACTIVITY (MEDIUM PRIORITY): As the conversation progresses, weave in relevant facts from Exigent Circumstances, World Knowledge, or past Memories organically based on your Entity Type and Capabilities.
           - ANTI-REPETITION CONSTRAINT: Never act like a mechanical game notification state tracker. Avoid repeating static updates or useless environmental facts. React naturally and act as a character in a fantasy RPG story, filtering it through your character's personality and background, rather than sounding like a cheap robot. Acknowledge situational changes with realistic variance, then shift focus forward.
        3. BACKGROUND SUBTEXT (LOWEST PRIORITY): Treat Ambient Details, Trading Stock details, and your Social Circle roster as passive background that can be referred to when it comes up in a conversation. Do not list items like a catalog or randomly name-drop roommates unless explicitly asked or contextually critical.
        """;

    private static final String DEFAULT_INIT_PROMPT = """
        You are a character in Minecraft. You are initiating a conversation with a player who just walked nearby.
        The following raw context sections define your current environment, your history, your background, and immediate events happening around you.
        
        === Ambient Details ===
        {AMBIENT_DETAILS}
        
        === World Knowledge ===
        {WORLD_KNOWLEDGE}
        
        === Personal Background ===
        {PERSONAL_BACKGROUND}
        
        === Social Circle ===
        {SOCIAL_CIRCLE}
        
        === Exigent Circumstances ===
        {EXIGENT_CIRCUMSTANCES}

        === Response & Story Tips ===
        - Remember that you have your own life, survival concerns, and routines. You are not just a prop waiting for the player. Do not overly protagonize the player.
        - Chat with the player like a character from a realistic RPG adventure story, not a meaningless NPC. Your dialogue should be purposeful and engaging, not hollow and flowerly.
        - Do not try to include all your knowledge in your response. Your knowledge is simply a resource you can draw from to make your character feel real and alive.
        
        [CRITICAL OPERATION & GREETING RULES]
        Review all context above, and generate your greeting using the following strict priority guidelines:
        1. CORE DRIVERS (HIGHEST PRIORITY): Your initiation dialogue must be driven by your designated Personality and your current Sentiment/Faction Standing. This dictates your tone, greeting style, and character voice. Keep it concise.
        2. SITUATIONAL REACTIVITY (MEDIUM PRIORITY): Look at Exigent Circumstances or World Knowledge to determine *why* you are calling out to the player.
           - ANTI-REPETITION CONSTRAINT: Never act like a mechanical game status monitor. Avoid repeating useless facts or stale text. Do not repeatedly yell mechanical warnings. React naturally and act as a character in a fantasy RPG story, filtering it through your character's personality and background, rather than sounding like a cheap robot.
        3. BACKGROUND SUBTEXT (LOWEST PRIORITY): Treat Ambient Details, your Name, your specific Trading Stock catalog, and your Social Circle roster as passive subtext. Do not introduce your trading options or roommate details in your greeting unless it is explicitly context-critical to a high-priority situational trigger.
        """;

    public static String getSystemPrompt(Player player, Entity target, boolean isInitiating) {
        // Choose which base prompt to load based on the boolean flag
        String basePrompt = isInitiating ? loadInitPromptFile() : loadPromptFile();
        
        Level level = player.level();
        BlockPos pos = target.blockPosition();

        String ambient = buildAmbientDetails(level, pos);
        String world = buildWorldKnowledge(pos, target);
        String background = buildPersonalBackground(player, target);
        String social = buildSocialCircle(target);
        String exigent = buildExigentCircumstances(level, player, target);

        return basePrompt
                .replace("{AMBIENT_DETAILS}", ambient)
                .replace("{WORLD_KNOWLEDGE}", world)
                .replace("{PERSONAL_BACKGROUND}", background)
                .replace("{SOCIAL_CIRCLE}", social)
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

    // NEW: Loader for the initiation prompt
    private static String loadInitPromptFile() {
        try {
            if (!INIT_PROMPT_FILE.exists()) {
                Files.writeString(INIT_PROMPT_FILE.toPath(), DEFAULT_INIT_PROMPT);
            }
            return Files.readString(INIT_PROMPT_FILE.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return DEFAULT_INIT_PROMPT;
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

    private static String buildWorldKnowledge(BlockPos pos, Entity target) {
        String knowledge = "";
        CompoundTag data = target.getPersistentData();
        
        // 1. Home Knowledge
        String homeId = data.getString("mcaichat_home_id");
        if (!homeId.isEmpty()) {
            String homeType = formatName(data.getString("mcaichat_home_type"));
            ClientLoreManager.StructureLore homeLore = ClientLoreManager.getLore(homeId);
            String homeName = (homeLore != null) ? homeLore.name : homeType;
            
            // --- NEW: Append (here) if currently inside their home structure ---
            if (homeId.equals(ClientLoreManager.currentStructureId)) {
                homeName += " (here)";
            }
            
            String loreText = (homeLore != null) ? homeLore.background : "History currently unknown.";
            knowledge += "Home: " + homeName + "\nLocal Lore/History: " + loreText + "\n";
        } else {
            knowledge += "Home: Nomad / No specific home\n";
        }
        
        // 2. Surrounding Civilizations
        if (data.contains("mcaichat_nearby_civs", 9)) { 
            ListTag civList = data.getList("mcaichat_nearby_civs", 8); 
            StringBuilder civsBuilder = new StringBuilder();
            
            for (int i = 0; i < civList.size(); i++) {
                String[] parts = civList.getString(i).split("\\|");
                if (parts.length == 5) {
                    String civId = parts[0];
                    String civType = formatName(parts[1]);
                    String civBiome = formatName(parts[2]);
                    int civX = Integer.parseInt(parts[3]);
                    int civZ = Integer.parseInt(parts[4]);
                    
                    if (civId.equals(homeId)) continue;
                    
                    ClientLoreManager.StructureLore civLore = ClientLoreManager.getLore(civId);
                    String civName = (civLore != null) ? civLore.name : civType; 
                    
                    // --- NEW: Append the structure type if a custom name was assigned ---
                    if (civLore != null && !civLore.name.equals(civType)) {
                        civName += " (" + civType + ")";
                    }
                    
                    String direction = getDirection(pos.getX(), pos.getZ(), civX, civZ);
                    
                    civsBuilder.append("- ").append(civName).append(" located to the ").append(direction).append(" in a ").append(civBiome).append(" biome.\n");
                }
            }
            
            if (civsBuilder.length() > 0) {
                knowledge += "Nearby Civilizations:\n" + civsBuilder.toString();
            }
        }
        
        // 3. Secret Adventure Structure Knowledge
        if (data.contains("mcaichat_secret_type")) {
            String secretType = formatName(data.getString("mcaichat_secret_type"));
            int secretX = data.getInt("mcaichat_secret_x");
            int secretZ = data.getInt("mcaichat_secret_z");
            
            double dist = Math.sqrt(Math.pow(pos.getX() - secretX, 2) + Math.pow(pos.getZ() - secretZ, 2));
            String direction = getDirection(pos.getX(), pos.getZ(), secretX, secretZ);
            
            String relativeDistance;
            if (dist < 50) relativeDistance = "very close";
            else if (dist < 150) relativeDistance = "nearby";
            else if (dist < 300) relativeDistance = "a moderate distance";
            else relativeDistance = "quite far";

            knowledge += "Secret: You know the location of a hidden " + secretType + " that is " + relativeDistance + " to the " + direction + ".";
        }
        
        return knowledge.trim();
    }

    public static String getShortCapabilityString(Entity target) {
        String targetRegistryName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        boolean isMonster = target instanceof Monster;
        boolean isIronGolem = target instanceof IronGolem;
        boolean isGuardVillager = targetRegistryName.equals("guardvillagers:guard");
        boolean isValarianFighter = targetRegistryName.equals("valarian_conquest:archer") || targetRegistryName.equals("valarian_conquest:soldier");
        boolean isMerchant = target instanceof net.minecraft.world.item.trading.Merchant;
        
        boolean isCapableFighter = isMonster || isIronGolem || isGuardVillager || isValarianFighter;
        
        if (isCapableFighter && isMerchant) return "Warrior & Merchant";
        if (isCapableFighter) return "Warrior";
        if (isMerchant) return "Merchant";
        return "Citizen";
    }
    
    public static String getSentimentColorCode(Player player, Entity target) {
        String targetRegistryName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        boolean isMonster = target instanceof Monster;
        boolean isValarianFighter = targetRegistryName.equals("valarian_conquest:archer") || targetRegistryName.equals("valarian_conquest:soldier");

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
        boolean isValarianFighter = targetRegistryName.equals("valarian_conquest:archer") || targetRegistryName.equals("valarian_conquest:soldier");
        boolean isMerchant = target instanceof Merchant;
        
        String shortCap = getShortCapabilityString(target);
        String capability = "Normal Citizen - No Fighting Abilities";
        
        if (shortCap.equals("Warrior & Merchant")) capability = "Trained Warrior & Merchant - Has Fighting Abilities and trades items";
        else if (shortCap.equals("Warrior")) capability = "Trained Warrior - Has Fighting Abilities";
        else if (shortCap.equals("Merchant")) capability = "Merchant - Trades items with the player";

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
        
        // --- UPDATED: Read trades from the server-synced packet! ---
        String tradingInfo = data.getString("mcaichat_trades");
        if (isMerchant && tradingInfo.isEmpty()) {
            tradingInfo = "\nTrades Available: Currently has no items in stock to trade.";
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

        return String.format("Name: %s\nEntity Type: %s\nPersonality: %s\nSentiment: %s\nCapabilities: %s%s\nMemory: %s\nTime Since Last Conversation: %s", 
                name, entityType, personality, sentiment, capability, tradingInfo, memoryStr, timeElapsedStr);
    }

    private static String buildSocialCircle(Entity target) {
        CompoundTag data = target.getPersistentData();
        String homeId = data.getString("mcaichat_home_id");
        if (homeId.isEmpty() || homeId.equals("none")) {
            return "You do not belong to a specific home, so you have no recognized compatriots.";
        }

        Map<UUID, ClientSocialManager.CitizenProfile> citizens = ClientSocialManager.getCitizens(homeId);
        if (citizens.size() <= 1) {
            return "You are currently the only known member of your home.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("You know the following individuals who share your home:\n");
        for (Map.Entry<UUID, ClientSocialManager.CitizenProfile> entry : citizens.entrySet()) {
            if (entry.getKey().equals(target.getUUID())) continue; // Skip themselves
            
            ClientSocialManager.CitizenProfile profile = entry.getValue();
            sb.append("- ").append(profile.name)
              .append(" (").append(formatName(profile.type)).append(")")
              .append(" | Personality: ").append(profile.personality)
              .append(" | Capabilities: ").append(profile.capabilities);
              
            if (profile.isDeceased) {
                String cause = (profile.causeOfDeath != null && !profile.causeOfDeath.isEmpty()) ? profile.causeOfDeath : "Unknown causes";
                sb.append(" [DECEASED - ").append(cause).append("]");
            }
            sb.append("\n");
        }
        
        return sb.toString().trim();
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
        
        AABB dangerBox = target.getBoundingBox().inflate(24.0D);

        // Removed the !AmbientCreature and !WaterAnimal exclusions
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, dangerBox, 
            entity -> entity != target 
                && entity != player 
                && !Config.isBlacklisted(entity) 
                && !Config.isWhitelisted(entity)
        );

        List<String> hostileNames = new ArrayList<>();
        List<String> creatureNames = new ArrayList<>();
        List<String> wildlifeNames = new ArrayList<>();

        for (LivingEntity entity : nearbyEntities) {
            String name = entity.getDisplayName().getString();
            String registryName = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
            
            boolean isHostile = false;
            boolean isCreature = false;
            boolean isWildlife = false;

            if (Config.isInList(Config.CUSTOM_MONSTERS, registryName)) {
                isHostile = true;
            } else if (Config.isInList(Config.CUSTOM_CREATURES, registryName)) {
                isCreature = true;
            } else if (Config.isInList(Config.CUSTOM_WILDLIFE, registryName)) {
                isWildlife = true;
            } else {
                // Default logic
                if (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER) {
                    isHostile = true;
                } else if (entity instanceof AmbientCreature || entity instanceof WaterAnimal) {
                    isWildlife = true;
                } else {
                    isCreature = true;
                }
            }
            
            if (isHostile) {
                if (!hostileNames.contains(name)) hostileNames.add(name);
            } else if (isWildlife) {
                if (!wildlifeNames.contains(name)) wildlifeNames.add(name);
            } else if (isCreature) {
                if (!creatureNames.contains(name)) creatureNames.add(name);
            }
        }

        if (!hostileNames.isEmpty()) {
            String joinedHostiles = String.join(", ", hostileNames);
            exigent.append("There are dangerous monsters nearby (").append(joinedHostiles).append(")! ");
        }

        if (!creatureNames.isEmpty()) {
            String joinedCreatures = String.join(", ", creatureNames);
            exigent.append("There are creatures roaming nearby (").append(joinedCreatures).append("). ");
        }

        if (!wildlifeNames.isEmpty()) {
            String joinedWildlife = String.join(", ", wildlifeNames);
            exigent.append("There is ambient wildlife nearby (").append(joinedWildlife).append("). ");
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