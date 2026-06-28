package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        
        === Social Circle ===
        {SOCIAL_CIRCLE}
        
        === Exigent Circumstances ===
        {EXIGENT_CIRCUMSTANCES}
        """;

    public static String getSystemPrompt(Player player, Entity target) {
        String basePrompt = loadPromptFile();
        
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
            String homeName = (homeLore != null) ? homeLore.name : "Unknown " + homeType;
            String loreText = (homeLore != null) ? homeLore.background : "History currently unknown.";
            knowledge += "Home: " + homeName + "\nLocal Lore/History: " + loreText + "\n";
        } else {
            knowledge += "Home: Nomad / No specific home\n";
        }
        
        // 2. Surrounding Civilizations
        if (data.contains("mcaichat_nearby_civs", 9)) { // 9 is ListTag
            ListTag civList = data.getList("mcaichat_nearby_civs", 8); // 8 is StringTag
            StringBuilder civsBuilder = new StringBuilder();
            
            for (int i = 0; i < civList.size(); i++) {
                String[] parts = civList.getString(i).split("\\|");
                if (parts.length == 5) {
                    String civId = parts[0];
                    String civType = formatName(parts[1]);
                    String civBiome = formatName(parts[2]);
                    int civX = Integer.parseInt(parts[3]);
                    int civZ = Integer.parseInt(parts[4]);
                    
                    // Don't list their home again as a nearby civilization
                    if (civId.equals(homeId)) continue;
                    
                    ClientLoreManager.StructureLore civLore = ClientLoreManager.getLore(civId);
                    String civName = (civLore != null) ? civLore.name : "An unknown " + civType.toLowerCase();
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
        
        String tradingInfo = "";
        if (isMerchant) {
            Merchant merchant = (Merchant) target;
            if (!merchant.getOffers().isEmpty()) {
                StringBuilder trades = new StringBuilder();
                for (MerchantOffer offer : merchant.getOffers()) {
                    String itemA = formatName(ForgeRegistries.ITEMS.getKey(offer.getBaseCostA().getItem()).getPath());
                    String itemResult = formatName(ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()).getPath());
                    
                    trades.append(offer.getBaseCostA().getCount()).append(" ").append(itemA);
                    
                    if (!offer.getCostB().isEmpty()) {
                        String itemB = formatName(ForgeRegistries.ITEMS.getKey(offer.getCostB().getItem()).getPath());
                        trades.append(" and ").append(offer.getCostB().getCount()).append(" ").append(itemB);
                    }
                    
                    trades.append(" for ").append(offer.getResult().getCount()).append(" ").append(itemResult).append(", ");
                }
                
                if (trades.length() > 0) {
                    trades.setLength(trades.length() - 2); 
                    tradingInfo = "\nTrades Available: Accepts " + trades.toString() + ".";
                }
            } else {
                tradingInfo = "\nTrades Available: Currently has no items in stock to trade.";
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