package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Assembles the full Gemini system prompt for a conversation out of six context sections
// (ambient details, world knowledge, personal background, social circle, exigent circumstances,
// special instructions), filled into one of two fixed internal templates - one for a
// player-initiated chat, one for an NPC-initiated greeting - since the tone and framing differ
// (responding vs. speaking up first) even though the underlying context-gathering is identical.
// These templates are intentionally not user-editable; per-entity customization instead goes
// through EntityInstructionManager (see the Creature config screen).
public class PromptBuilder {
    private static final String PROMPT_TEMPLATE = """
        You are a character in a realistic RPG adventure story conversing with a player in real-time chat-based environment.
        The following define the current environment, the geography around you, your personal background, your social circle, and immediate events happening around you. Follow the instructions followed by TASK: at the bottom of this message.
        
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

        === Special Instructions ===
        {SPECIAL_INSTRUCTIONS}


        === Response & Story Tips ===
        - Remember that you are human being. You are NOT an NPC. You have your own life, survival concerns, and routines. You are not just a prop waiting for the player. Do not overly protagonize the player.
        - Your dialogue should be purposeful and engaging, not hollow and flowerly. Do not be overly dramatic. Your chat messages should be simple and align with your character's personality.
        - Reveal your knowledge and awareness of your surroundings slowly to the player as the conversation progresses. Do not try to include all your knowledge in your responses. Your knowledge is simply a resource you can draw from to make your character feel real and aware.
        - Format your response in a ONE or at most TWO sentences, and DO NOT include roleplay actions. Only return straight dialogue without quotations marks so that your answer looks natural to the player in a chat window.
        - Avoid repeating static observations or useless environmental facts. Avoid unnecessary and unoriginal repetition of previous events in your memory.

        TASK: Generate a simple, engaging, and situationally aware response to the player's message to you that aligns with your character's personality and sentiment toward them.
        """;

    private static final String INIT_PROMPT_TEMPLATE = """
        You are a character in a realistic RPG adventure story conversing with a player in real-time chat-based environment. You are initiating a conversation with a player who just walked nearby.
        The following raw context sections define your current environment, your history, your background, and immediate events happening around you. Follow the instructions followed by TASK: at the bottom of this message.
        
        === Ambient Details ===
        {AMBIENT_DETAILS}
        
        === Personal Background ===
        {PERSONAL_BACKGROUND}

        === Special Instructions ===
        {SPECIAL_INSTRUCTIONS}

        === Exigent Circumstances ===
        {EXIGENT_CIRCUMSTANCES}

        === Response & Story Tips ===
        - Remember that you are human being. You are NOT an NPC. You have your own life, survival concerns, and routines. You are not just a prop waiting for the player. Do not overly protagonize the player.
        - Your dialogue should be purposeful and engaging, not hollow and flowerly. Do not be overly dramatic. Your chat messages should be simple and align with your character's personality.
        - Reveal your knowledge and awareness of your surroundings slowly to the player as the conversation progresses. Do not try to include all your knowledge in your responses. Your knowledge is simply a resource you can draw from to make your character feel real and aware.
        - Format your response in a ONE or at most TWO sentences, and DO NOT include roleplay actions. Only return straight dialogue without quotations marks so that your answer looks natural to the player in a chat window.
        - Avoid repeating static observations or useless environmental facts. Avoid unnecessary and unoriginal repetition of previous events in your memory.
        
        TASK: Generate a simple, engaging, and situationally aware conversation initiation message to the player that aligns with your character's personality and sentiment toward them.
        """;

    public static String getSystemPrompt(Player player, Entity target, boolean isInitiating) {
        String basePrompt = isInitiating ? INIT_PROMPT_TEMPLATE : PROMPT_TEMPLATE;

        Level level = player.level();
        BlockPos pos = target.blockPosition();

        String ambient = buildAmbientDetails(level, pos);
        String world = buildWorldKnowledge(pos, target);
        String background = buildPersonalBackground(player, target);
        String special = buildSpecialInstructions(target);
        String social = buildSocialCircle(target);
        String exigent = buildExigentCircumstances(level, player, target);

        return basePrompt
                .replace("{AMBIENT_DETAILS}", ambient)
                .replace("{WORLD_KNOWLEDGE}", world)
                .replace("{PERSONAL_BACKGROUND}", background)
                .replace("{SPECIAL_INSTRUCTIONS}", special.isEmpty() ? "None." : special)
                .replace("{SOCIAL_CIRCLE}", social)
                .replace("{EXIGENT_CIRCUMSTANCES}", exigent.isEmpty() ? "None." : exigent);
    }

    // Player-authored, per-entity-type flavor text set via the Creature config screen (e.g.
    // "You secretly work for the Thieves' Guild"), stored globally by EntityInstructionManager.
    private static String buildSpecialInstructions(Entity target) {
        String registryName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        return EntityInstructionManager.get(registryName);
    }

    // Passive scene-setting: biome, time of day, weather, season (if Serene Seasons is installed),
    // and world name.
    private static String buildAmbientDetails(Level level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        String biomeNameRaw = biomeHolder.unwrapKey().map(key -> key.location().getPath()).orElse("unknown");
        String biomeName = formatName(biomeNameRaw);
        
        long time = level.getDayTime() % 24000;
        String timeOfDay = (time < 12000) ? "Daytime" : (time < 13000) ? "Dusk" : (time < 23000) ? "Nighttime" : "Dawn";
        
        String weather = level.isThundering() ? "Thunderstorm" : level.isRaining() ? "Raining" : "Clear";
        
        // Serene Seasons is an optional soft dependency, accessed via reflection so this mod still
        // loads fine without it installed. Any failure here just leaves the season unknown.
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

    // Renders the NBT that IdentityHandler.generateWorldKnowledge computed server-side (home
    // structure, nearby civilizations, an optional secret location) into prose for the prompt.
    private static String buildWorldKnowledge(BlockPos pos, Entity target) {
        String knowledge = "";
        CompoundTag data = target.getPersistentData();

        // 1. Home Knowledge
        String homeId = data.getString("mcaichat_home_id");
        // IdentityHandler always writes "none" (never leaves this truly empty) when nothing
        // claimed a home, so both cases have to be checked - isEmpty() alone never catches it.
        if (!homeId.isEmpty() && !homeId.equals("none")) {
            String homeType = formatName(data.getString("mcaichat_home_type"));
            ClientLoreManager.StructureLore homeLore = ClientLoreManager.getLore(homeId);
            String homeName = (homeLore != null) ? homeLore.name : homeType;

            // Let the NPC know when the player is standing in its home right now
            if (homeId.equals(ClientLoreManager.currentStructureId)) {
                homeName += " (here)";
            }

            String loreText = (homeLore != null) ? homeLore.background : "History currently unknown.";
            knowledge += "Home: " + homeName + "\nLocal Lore/History: " + loreText + "\n";
        } else {
            knowledge += "Home: None (Nomad)\n";
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

                    // Clarify with the raw structure type when it was given a custom/generated name,
                    // e.g. "Oakhaven (Village)" instead of just "Oakhaven"
                    if (civLore != null && !civLore.name.equals(civType)) {
                        civName += " (" + civType + ")";
                    }
                    
                    String direction = getDirection(pos.getX(), pos.getZ(), civX, civZ);
                    double dist = Math.sqrt(Math.pow(pos.getX() - civX, 2) + Math.pow(pos.getZ() - civZ, 2));
                    String relativeDistance = getRelativeDistance(dist);

                    civsBuilder.append("- ").append(civName).append(" located ").append(relativeDistance).append(" to the ").append(direction).append(" in a ").append(civBiome).append(" biome.\n");
                }
            }

            if (civsBuilder.length() > 0) {
                knowledge += "Nearby Locations:\n" + civsBuilder.toString();
            }
        }

        // 3. Secret Adventure Structure Knowledge
        if (data.contains("mcaichat_secret_type")) {
            String secretType = formatName(data.getString("mcaichat_secret_type"));
            int secretX = data.getInt("mcaichat_secret_x");
            int secretZ = data.getInt("mcaichat_secret_z");

            double dist = Math.sqrt(Math.pow(pos.getX() - secretX, 2) + Math.pow(pos.getZ() - secretZ, 2));
            String direction = getDirection(pos.getX(), pos.getZ(), secretX, secretZ);
            String relativeDistance = getRelativeDistance(dist);

            knowledge += "Secret: You know the location of a hidden " + secretType + " that is " + relativeDistance + " to the " + direction + ".";
        }
        
        return knowledge.trim();
    }

    // One-word-ish capability tag ("Warrior", "Merchant", etc.) reused by both the full prompt
    // and NameplateRenderer's social-circle registration, so both stay in sync.
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
    
    // Nameplate/chat color reflecting how this entity feels about the player - monster
    // hostility by default, or faction standing for Valarian Conquest's faction-aware units.
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

    // The NPC's own identity: name, entity type, personality, sentiment toward the player,
    // fighting/trading capabilities, and its memory of past conversations.
    private static String buildPersonalBackground(Player player, Entity target) {
        CompoundTag data = target.getPersistentData();
        String name = data.getString("mcaichat_name");
        
        String personality = data.getString("mcaichat_personality");
        if (personality != null && !personality.isEmpty()) {
            personality = personality.substring(0, 1).toUpperCase() + personality.substring(1);
        }
        
        String targetRegistryName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
        String entityType = formatName(ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).getPath());
        if (target instanceof LivingEntity livingTarget && livingTarget.isBaby()) {
            entityType += " (Baby)";
        }

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
        
        // A client can't read another entity's Merchant offers directly, so this is pre-formatted
        // server-side (see ServerStructureTracker) and synced down via SyncNPCPacket instead.
        String tradingInfo = data.getString("mcaichat_trades");
        if (isMerchant && tradingInfo.isEmpty()) {
            tradingInfo = "\nTrades Available: Currently has no items in stock to trade.";
        }

        ClientMemoryManager.EntityMemory mem = ClientMemoryManager.getMemory(target.getUUID());
        String memoryStr = "None (First interaction with the player)";
        String timeElapsedStr = "N/A";

        if (mem != null) {
            memoryStr = mem.summary;

            // Measured in game ticks rather than real time, so "how long ago" reflects time spent
            // in-world rather than counting time while the game was closed.
            long currentTick = player.level().getGameTime();
            long diffTicks = currentTick - mem.lastConvoTick;

            // 1 in-game hour = 1000 ticks, 1 in-game day = 24000 ticks
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

    // Other NPCs registered under the same home structure (see NameplateRenderer), so this NPC
    // can talk about its "neighbors" - including whether one of them has since died.
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

    // Surfaces short-lived, urgent situational facts (danger, injury, nearby threats) that should
    // outweigh an NPC's default personality/mood for the duration of this one response.
    private static String buildExigentCircumstances(Level level, Player player, Entity target) {
        StringBuilder exigent = new StringBuilder();

        if (player.getHealth() <= 6.0f) exigent.append("The player is severely injured. "); // 3 hearts or less
        if (player.getFoodData().getFoodLevel() <= 6) exigent.append("The player is starving. "); // 3 drumsticks or less

        for (MobEffectInstance effect : player.getActiveEffects()) {
            String effectName = effect.getEffect().getDisplayName().getString();
            exigent.append("The player has a '").append(effectName).append("' status effect. ");
        }

        // Brute-force block scan for nearby fire - expensive (a 33x33x33 cube), but this only
        // runs once per chat message/greeting, not per tick.
        BlockPos playerPos = player.blockPosition();
        int radius = 16;
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
        
        AABB dangerBox = target.getBoundingBox().inflate(16.0D);

        // Every non-blacklisted, non-chattable living entity nearby - hostile, passive, or
        // ambient alike - gets surfaced below, just sorted into separate buckets by type.
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
                // No explicit config override - guess from the entity's own vanilla category/interfaces
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
            if (currentHealth <= (maxHealth * 0.3f)) { // below 30% of max health
                exigent.append("You are severely injured and near death. ");
            }

            // Read from the server-synced summary (see SyncNPCPacket) rather than
            // livingTarget.getActiveEffects() directly - unlike health/food, potion effects aren't
            // part of an entity's always-synced metadata, and don't reliably reach the client here.
            String effectsInfo = target.getPersistentData().getString("mcaichat_effects");
            if (!effectsInfo.isEmpty()) {
                for (String effectName : effectsInfo.split(",")) {
                    exigent.append("You currently have a '").append(effectName).append("' status effect. ");
                }
            }
        }

        return exigent.toString().trim();
    }

    // Describes a block distance in vague relative terms rather than exact numbers - NPCs "know
    // of" locations, they don't have a map with a marker on it.
    private static String getRelativeDistance(double dist) {
        if (dist < 50) return "very close";
        if (dist < 150) return "nearby";
        if (dist < 300) return "a moderate distance";
        return "quite far";
    }

    // Reduces a relative offset to one of 8 compass directions - biased toward the cardinal
    // directions (a 2:1 ratio) rather than splitting evenly into 8 equal angular slices.
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