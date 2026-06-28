package com.thanwiggins.mcaichat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class IdentityHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_ID_GEN = false; // Turned off to prevent spam

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
            // Only assign Name and Personality on spawn. World Knowledge is deferred!
            if (!data.contains("mcaichat_personality")) {
                Random random = new Random(entity.getUUID().getLeastSignificantBits());
                String name = NPCData.getRandomName(random);
                String personality = NPCData.getRandomPersonality(random);
                
                data.putString("mcaichat_personality", personality);
                data.putString("mcaichat_name", name);
                
                if (!event.getLevel().isClientSide()) {
                    entity.setCustomName(Component.literal(name));
                    entity.setCustomNameVisible(true);
                }
            }
        }
    }

    // This is now called from ServerStructureTracker when a player gets close!
    public static void generateWorldKnowledge(Entity entity, ServerLevel serverLevel) {
        CompoundTag data = entity.getPersistentData();
        BlockPos pos = entity.blockPosition();
        ChunkPos chunkPos = new ChunkPos(pos);
        int radius = 16; 
        
        Random random = new Random(entity.getUUID().getLeastSignificantBits());
        List<String> nearbyCivs = new ArrayList<>();
        String homeId = "";
        double closestCivDist = 50 * 50; 
        
        // --- 1. LOG THE INITIAL ROLL ---
        boolean rollSecret = random.nextInt(100) < 5;
        String npcName = data.getString("mcaichat_name");
        if (npcName.isEmpty()) npcName = "Unknown NPC";
        
        LOGGER.info("[Secret Debug] Generating World Knowledge for: " + npcName);
        LOGGER.info("[Secret Debug] Did " + npcName + " pass the 5% secret roll? " + (rollSecret ? "YES!" : "No."));
        // -------------------------------
        
        double closestSecretDist = Double.MAX_VALUE;
        String secretType = "";
        int secretX = 0;
        int secretZ = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkAccess chunk = serverLevel.getChunk(chunkPos.x + x, chunkPos.z + z, net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;

                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start != null && start.isValid()) {
                        ResourceLocation key = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                        if (key != null) {
                            String fullKey = key.toString(); // e.g. "valarian_conquest:visgothian_outpost"
                            String structType = key.getPath();
                            BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                            double distSqr = pos.distSqr(startPos);
                            String structId = fullKey + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                            
                            // --- NEW: CATEGORY OVERRIDE ---
                            if (Config.isInList(Config.IGNORED_STRUCTURES, fullKey)) continue;

                            boolean isCiv = false;
                            boolean isAdv = false;

                            if (Config.isInList(Config.CIV_STRUCTURES, fullKey)) {
                                isCiv = true;
                            } else if (Config.isInList(Config.ADVENTURE_STRUCTURES, fullKey)) {
                                isAdv = true;
                            } else {
                                // Default Logic
                                isCiv = fullKey.contains("village") || fullKey.contains("city") || 
                                                fullKey.contains("bastion") || fullKey.contains("fortress") ||
                                                fullKey.contains("towns_and_towers") || fullKey.contains("valarian_conquest");
                                isAdv = !isCiv; // By default everything not a civilization is an adventure
                            }
                            // ------------------------------
                            
                            if (isCiv) {
                                String biome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                
                                if (start.getBoundingBox().isInside(pos) || distSqr <= closestCivDist) {
                                    closestCivDist = distSqr;
                                    homeId = structId;
                                    data.putString("mcaichat_home_id", homeId);
                                    data.putString("mcaichat_home_type", structType);
                                }
                                nearbyCivs.add(structId + "|" + structType + "|" + biome + "|" + startPos.getX() + "|" + startPos.getZ());
                            } else if (isAdv && rollSecret) {
                                if (distSqr < closestSecretDist) {
                                    closestSecretDist = distSqr;
                                    secretType = structType;
                                    secretX = startPos.getX();
                                    secretZ = startPos.getZ();
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (!nearbyCivs.isEmpty()) {
            ListTag civList = new ListTag();
            for (String civ : nearbyCivs) {
                civList.add(StringTag.valueOf(civ));
            }
            data.put("mcaichat_nearby_civs", civList);
        }
        
        // --- 2. LOG THE FINAL OUTCOME ---
        if (rollSecret) {
            if (!secretType.isEmpty()) {
                LOGGER.info("[Secret Debug] SUCCESS: " + npcName + " learned about a hidden '" + secretType + "' at X:" + secretX + " Z:" + secretZ);
                data.putString("mcaichat_secret_type", secretType);
                data.putInt("mcaichat_secret_x", secretX);
                data.putInt("mcaichat_secret_z", secretZ);
            } else {
                LOGGER.info("[Secret Debug] FAILED: " + npcName + " passed the roll, but NO adventure structures were found within 16 chunks.");
            }
        }
        // --------------------------------

        // Failsafe: Mark that we completed the scan so we don't scan this NPC again
        if (!data.contains("mcaichat_home_id")) {
            data.putString("mcaichat_home_id", "none");
        }
    }
}