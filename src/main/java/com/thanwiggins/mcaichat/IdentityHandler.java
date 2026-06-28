package com.thanwiggins.mcaichat;

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

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
            // If the entity doesn't have an AI identity yet, give it one
            if (!data.contains("mcaichat_personality")) {
                
                // Seed the Random with the UUID. This guarantees the Client and Server 
                // independently pick the exact same traits!
                Random random = new Random(entity.getUUID().getLeastSignificantBits());
                
                String name = NPCData.getRandomName(random);
                String personality = NPCData.getRandomPersonality(random);
                
                // Save to NBT
                data.putString("mcaichat_personality", personality);
                data.putString("mcaichat_name", name);
                
                // Set the vanilla Custom Name so it physically tracks above their head
                if (!event.getLevel().isClientSide()) {
                    entity.setCustomName(Component.literal(name));
                    entity.setCustomNameVisible(true);
                    
                    // --- WORLD KNOWLEDGE SCAN ---
                    if (event.getLevel() instanceof ServerLevel serverLevel) {
                        BlockPos pos = entity.blockPosition();
                        ChunkPos chunkPos = new ChunkPos(pos);
                        int radius = 16; // 16 chunks = ~256 blocks
                        
                        List<String> nearbyCivs = new ArrayList<>();
                        String homeId = "";
                        double closestCivDist = 50 * 50; // 50 blocks squared for "Home"
                        
                        boolean rollSecret = random.nextInt(100) < 5;
                        double closestSecretDist = Double.MAX_VALUE;
                        String secretType = "";
                        int secretX = 0;
                        int secretZ = 0;
                        
                        for (int x = -radius; x <= radius; x++) {
                            for (int z = -radius; z <= radius; z++) {
                                ChunkAccess chunk = serverLevel.getChunk(chunkPos.x + x, chunkPos.z + z, net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS, false);
                                if (chunk != null) {
                                    for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                                        StructureStart start = entry.getValue();
                                        if (start != null && start.isValid()) {
                                            ResourceLocation key = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                                            if (key != null) {
                                                String structType = key.getPath();
                                                BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                                                double distSqr = pos.distSqr(startPos);
                                                String structId = key.toString() + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                                                
                                                boolean isCiv = structType.contains("village") || structType.contains("city") || 
                                                                structType.contains("bastion") || structType.contains("fortress") ||
                                                                structType.contains("towns_and_towers") || structType.contains("valarian_conquest");
                                                
                                                if (isCiv) {
                                                    String biome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                                    if (distSqr <= closestCivDist) {
                                                        closestCivDist = distSqr;
                                                        homeId = structId;
                                                        data.putString("mcaichat_home_id", homeId);
                                                        data.putString("mcaichat_home_type", structType);
                                                    }
                                                    nearbyCivs.add(structId + "|" + structType + "|" + biome + "|" + startPos.getX() + "|" + startPos.getZ());
                                                } else if (rollSecret) {
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
                        }
                        
                        // Save Nearby Civilizations to NBT
                        if (!nearbyCivs.isEmpty()) {
                            ListTag civList = new ListTag();
                            for (String civ : nearbyCivs) {
                                civList.add(StringTag.valueOf(civ));
                            }
                            data.put("mcaichat_nearby_civs", civList);
                        }
                        
                        // Save Secret Knowledge to NBT
                        if (rollSecret && !secretType.isEmpty()) {
                            data.putString("mcaichat_secret_type", secretType);
                            data.putInt("mcaichat_secret_x", secretX);
                            data.putInt("mcaichat_secret_z", secretZ);
                        }
                    }
                }
            }
        }
    }
}