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

// Gives whitelisted entities a persistent identity (name, personality, and eventually a home/secret)
// the first time they're encountered, so the same villager keeps the same name and backstory forever.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class IdentityHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();

            // Only roll a name/personality once per entity - re-joining a chunk shouldn't reroll them
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

    // Runs once per NPC (guarded by the caller checking for "mcaichat_home_id") to find its home
    // structure, catalog nearby civilizations for small talk, and maybe hand it a "secret" to reveal.
    // This is the expensive counterpart to ServerStructureTracker's per-tick nearest-structure lookup:
    // that one just reports what's near the player right now, this one permanently tags one NPC.
    public static void generateWorldKnowledge(Entity entity, ServerLevel serverLevel) {
        CompoundTag data = entity.getPersistentData();
        BlockPos pos = entity.blockPosition();
        ChunkPos chunkPos = new ChunkPos(pos);
        int radius = 16; // chunks - a 33x33 chunk (528x528 block) search area centered on the NPC

        Random random = new Random(entity.getUUID().getLeastSignificantBits());
        List<String> nearbyCivs = new ArrayList<>();
        String homeId = "";
        double closestCivDist = 50 * 50; // blocks, squared - being within 50 blocks claims a structure as "home"

        // Forced wanderers (e.g. Wandering Traders) can still see nearby civilizations for context,
        // they just never claim one as "home" - even if they happen to be standing inside one.
        boolean isWanderer = Config.isWanderer(entity);

        boolean rollSecret = random.nextInt(100) < 5; // 5% of NPCs are given a "secret" location to reveal in conversation

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
                            // Standing inside a structure's bounding box always wins, even if its center
                            // (and thus raw distSqr) is technically farther away than a smaller nearby structure.
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
                                // No explicit config category - guess from the structure's name.
                                // Structures matching none of these keywords default to "adventure".
                                isCiv = fullKey.contains("village") || fullKey.contains("city") ||
                                                fullKey.contains("bastion") || fullKey.contains("fortress") ||
                                                fullKey.contains("towns_and_towers") || fullKey.contains("valarian_conquest");
                                if (!isCiv) isAdv = true;
                            }
                            
                            if (isCiv || isNomad) {
                                String biome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");

                                if (!isWanderer && actualDist <= closestCivDist) {
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
            }
        }

        // Dragon roosts are Features, not Structures, so the loop above never finds them - detect
        // them separately via the resident dragon's home position (no-op without Ice and Fire).
        DragonRoostFinder.Roost roost = DragonRoostFinder.findNearest(serverLevel, pos, radius * 16.0D);
        if (roost != null && !Config.isInList(Config.IGNORED_STRUCTURES, roost.type())) {
            String structType = roost.type().substring(roost.type().indexOf(':') + 1);

            if (!isWanderer && roost.distSqr() <= closestCivDist) {
                closestCivDist = roost.distSqr();
                homeId = roost.id();
                data.putString("mcaichat_home_id", homeId);
                data.putString("mcaichat_home_type", structType);
            }
            nearbyCivs.add(roost.id() + "|" + structType + "|" + roost.biome() + "|" + roost.pos().getX() + "|" + roost.pos().getZ());
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