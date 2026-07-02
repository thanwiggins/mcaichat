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
import net.minecraft.world.entity.Mob; 
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

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
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
            
            if (!event.getLevel().isClientSide() && entity instanceof Mob mob) {
                mob.goalSelector.addGoal(3, new ChattingGoal(mob));
            }
        }
    }

    public static void generateWorldKnowledge(Entity entity, ServerLevel serverLevel) {
        CompoundTag data = entity.getPersistentData();
        BlockPos pos = entity.blockPosition();
        ChunkPos chunkPos = new ChunkPos(pos);
        int radius = 16; 
        
        Random random = new Random(entity.getUUID().getLeastSignificantBits());
        List<String> nearbyCivs = new ArrayList<>();
        String homeId = "";
        double closestCivDist = 50 * 50; 
        
        boolean rollSecret = random.nextInt(100) < 5;
        String npcName = data.getString("mcaichat_name");
        if (npcName.isEmpty()) npcName = "Unknown NPC";
        
        double closestSecretDist = Double.MAX_VALUE;
        String secretType = "";
        int secretX = 0;
        int secretZ = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkAccess chunk = serverLevel.getChunk(chunkPos.x + x, chunkPos.z + z, net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;

                // 1. Standard Structure Starts
                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start != null && start.isValid()) {
                        ResourceLocation key = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                        if (key != null) {
                            String fullKey = key.toString(); 
                            String structType = key.getPath();
                            BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                            double distSqr = pos.distSqr(startPos);
                            // BUGFIX: Treat isInside as 0 distance so it wins ties, without inflating closestCivDist!
                            double actualDist = start.getBoundingBox().isInside(pos) ? 0 : distSqr;
                            String structId = fullKey + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                            
                            if (Config.isInList(Config.IGNORED_STRUCTURES, fullKey)) continue;

                            boolean isCiv = false;
                            boolean isNomad = false;
                            boolean isAdv = false;

                            if (Config.isInList(Config.CIV_STRUCTURES, fullKey)) {
                                isCiv = true;
                            } else if (Config.isInList(Config.NOMAD_STRUCTURES, fullKey)) {
                                isNomad = true;
                            } else if (Config.isInList(Config.ADVENTURE_STRUCTURES, fullKey)) {
                                isAdv = true;
                            } else {
                                isCiv = fullKey.contains("village") || fullKey.contains("city") || 
                                                fullKey.contains("bastion") || fullKey.contains("fortress") ||
                                                fullKey.contains("towns_and_towers") || fullKey.contains("valarian_conquest");
                                if (!isCiv) isAdv = true; 
                            }
                            
                            if (isCiv || isNomad) {
                                String biome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                
                                if (actualDist <= closestCivDist) {
                                    closestCivDist = actualDist;
                                    homeId = structId;
                                    data.putString("mcaichat_home_id", homeId);
                                    data.putString("mcaichat_home_type", structType);
                                }
                                nearbyCivs.add(structId + "|" + structType + "|" + biome + "|" + startPos.getX() + "|" + startPos.getZ());
                            } else if (isAdv && rollSecret) {
                                if (actualDist < closestSecretDist) {
                                    closestSecretDist = actualDist;
                                    secretType = structType;
                                    secretX = startPos.getX();
                                    secretZ = startPos.getZ();
                                }
                            }
                        }
                    }
                }
                
                // 2. Chest Loot Table Scanning for Ice & Fire Context
                if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk) {
                    for (Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry : levelChunk.getBlockEntities().entrySet()) {
                        net.minecraft.world.level.block.entity.BlockEntity be = entry.getValue();
                        
                        if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) {
                            CompoundTag tag = be.saveWithFullMetadata();
                            if (tag.contains("LootTable", 8)) {
                                String lootTable = tag.getString("LootTable");
                                if (lootTable.startsWith("iceandfire:")) {
                                    String rawName = lootTable;
                                    if (rawName.contains("/")) {
                                        rawName = rawName.substring(rawName.lastIndexOf('/') + 1);
                                    } else {
                                        rawName = rawName.substring(rawName.indexOf(':') + 1);
                                    }
                                    
                                    if (rawName.equals("cyclops_cave") || rawName.endsWith("dragon_roost") || rawName.endsWith("dragon_male_cave") || rawName.endsWith("dragon_female_cave") || rawName.equals("hydra_cave")) {
                                        BlockPos bePos = entry.getKey();
                                        double distSqr = pos.distSqr(bePos);
                                        // Treat the chest as having a ~30 block bounding box radius so it beats overlapping vanilla structures
                                        double actualDist = (distSqr <= 900) ? 0 : distSqr; 
                                        
                                        String structType = rawName;
                                        String fullKey = "iceandfire:" + rawName;
                                        String structId = fullKey + "_" + chunk.getPos().x + "_" + chunk.getPos().z;
                                        
                                        boolean isNomadL = rawName.equals("cyclops_cave") || rawName.endsWith("dragon_roost");
                                        boolean isAdvL = !isNomadL;

                                        if (isNomadL) {
                                            String biome = serverLevel.getBiome(bePos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                            if (actualDist <= closestCivDist) {
                                                closestCivDist = actualDist;
                                                homeId = structId;
                                                data.putString("mcaichat_home_id", homeId);
                                                data.putString("mcaichat_home_type", structType);
                                            }
                                            String civStr = structId + "|" + structType + "|" + biome + "|" + bePos.getX() + "|" + bePos.getZ();
                                            if (!nearbyCivs.contains(civStr)) nearbyCivs.add(civStr);
                                        } else if (isAdvL && rollSecret) {
                                            if (actualDist < closestSecretDist) {
                                                closestSecretDist = actualDist;
                                                secretType = structType;
                                                secretX = bePos.getX();
                                                secretZ = bePos.getZ();
                                            }
                                        }
                                    }
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
        
        if (rollSecret && !secretType.isEmpty()) {
            data.putString("mcaichat_secret_type", secretType);
            data.putInt("mcaichat_secret_x", secretX);
            data.putInt("mcaichat_secret_z", secretZ);
        }

        if (!data.contains("mcaichat_home_id")) {
            data.putString("mcaichat_home_id", "none");
        }
    }
}