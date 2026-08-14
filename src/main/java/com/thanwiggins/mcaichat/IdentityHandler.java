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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
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

    // Player-created locations are points, not bounding boxes, so they get the same "treat being
    // this close as standing inside it" convention DragonRoostFinder already uses for its own
    // point-like (non-Structure) features.
    private static final double PLAYER_LOCATION_CORE_RADIUS_SQR = 400.0D; // 20 blocks
    // Matches generateWorldKnowledge's own 16-chunk structure scan (radius=16 chunks * 16 blocks).
    private static final double CIV_SCAN_RADIUS_BLOCKS = 256.0D;

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
                mob.goalSelector.addGoal(2, new DirectiveGoal(mob));

                // restrictCenter/restrictRadius are transient - vanilla never writes them to NBT -
                // so a home tether has to be re-applied every join, not just the one time
                // generateWorldKnowledge originally established it.
                if (data.contains("mcaichat_home_x") && mob instanceof PathfinderMob pathfinderMob) {
                    BlockPos homePos = new BlockPos(
                            data.getInt("mcaichat_home_x"),
                            data.getInt("mcaichat_home_y"),
                            data.getInt("mcaichat_home_z"));
                    pathfinderMob.restrictTo(homePos, Config.HOME_RADIUS.get());
                }
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

        // Player-created locations (see PlayerLocationData) compete for home the same way vanilla
        // civ structures and dragon roosts already do above.
        for (PlayerLocationData.Location location : PlayerLocationData.get(serverLevel).withinRadius(pos, CIV_SCAN_RADIUS_BLOCKS)) {
            double distSqr = pos.distSqr(location.pos);
            double actualDist = distSqr <= PLAYER_LOCATION_CORE_RADIUS_SQR ? 0 : distSqr;
            closestCivDist = considerCivCandidate(data, nearbyCivs, isWanderer, closestCivDist,
                    location.id(), "player_created", biomeNameAt(serverLevel, location.pos), location.pos, actualDist);
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

        // An NPC born into a player-created location already lives there - it shouldn't need the
        // player to say the location's name in chat (see ChatInterceptor.checkForLocationReveal)
        // before it can use the real name instead of the "Player-Created Structure" placeholder.
        String finalHomeId = data.getString("mcaichat_home_id");
        if (data.getString("mcaichat_home_type").equals("player_created") && !finalHomeId.equals("none")) {
            if (PlayerLocationData.get(serverLevel).reveal(finalHomeId, finalHomeId)) {
                NetworkHandler.broadcastLocations(serverLevel);
            }
        }

        // Tether to the entity's own position when its home was settled, rather than the
        // structure's bounding-box center - more reliable for large structures (e.g. a village)
        // where that center can be far from where this specific NPC actually lives.
        if (!isWanderer && !homeId.isEmpty()) {
            data.putInt("mcaichat_home_x", pos.getX());
            data.putInt("mcaichat_home_y", pos.getY());
            data.putInt("mcaichat_home_z", pos.getZ());

            if (entity instanceof PathfinderMob pathfinderMob) {
                pathfinderMob.restrictTo(pos, Config.HOME_RADIUS.get());
            }
        }
    }

    // Compares a single civilization-like candidate against the running closestCivDist, updating
    // home_id/home_type if it wins and always appending it to nearbyCivs. Extracted so
    // considerNewLocation's single-candidate backfill can reuse the exact same comparison the
    // multi-candidate scan above uses.
    private static double considerCivCandidate(CompoundTag data, List<String> nearbyCivs, boolean isWanderer,
                                                 double closestCivDist, String id, String type, String biome,
                                                 BlockPos candidatePos, double actualDist) {
        if (!isWanderer && actualDist <= closestCivDist) {
            closestCivDist = actualDist;
            data.putString("mcaichat_home_id", id);
            data.putString("mcaichat_home_type", type);
        }
        nearbyCivs.add(id + "|" + type + "|" + biome + "|" + candidatePos.getX() + "|" + candidatePos.getZ());
        return closestCivDist;
    }

    private static String biomeNameAt(ServerLevel serverLevel, BlockPos pos) {
        return serverLevel.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
    }

    // Called right after /base new creates a location, so already-tagged nearby NPCs (whose
    // generateWorldKnowledge already ran, and will never run again) learn about it immediately,
    // instead of only NPCs first tagged after this point.
    public static void considerNewLocation(ServerLevel serverLevel, PlayerLocationData.Location location) {
        AABB searchBox = new AABB(location.pos).inflate(CIV_SCAN_RADIUS_BLOCKS);
        List<Entity> nearby = serverLevel.getEntities((Entity) null, searchBox, Config::isWhitelisted);

        for (Entity entity : nearby) {
            CompoundTag data = entity.getPersistentData();
            if (!data.contains("mcaichat_home_id")) continue; // untagged NPCs will see it naturally on their first scan

            List<String> nearbyCivs = new ArrayList<>();
            if (data.contains("mcaichat_nearby_civs", 9)) {
                ListTag existing = data.getList("mcaichat_nearby_civs", 8);
                for (int i = 0; i < existing.size(); i++) nearbyCivs.add(existing.getString(i));
            }
            if (nearbyCivs.stream().anyMatch(civ -> civ.startsWith(location.id() + "|"))) continue; // already known

            boolean isWanderer = Config.isWanderer(entity);
            BlockPos entityPos = entity.blockPosition();
            double distSqr = entityPos.distSqr(location.pos);
            double actualDist = distSqr <= PLAYER_LOCATION_CORE_RADIUS_SQR ? 0 : distSqr;

            // A fresh 50-block cap, same as the initial threshold generateWorldKnowledge's own scan
            // starts with - this is a single new candidate against an NPC whose original scan-time
            // comparisons are long gone, not an attempt to re-derive them.
            considerCivCandidate(data, nearbyCivs, isWanderer, 50.0D * 50.0D,
                    location.id(), "player_created", biomeNameAt(serverLevel, location.pos), location.pos, actualDist);

            ListTag civList = new ListTag();
            for (String civ : nearbyCivs) civList.add(StringTag.valueOf(civ));
            data.put("mcaichat_nearby_civs", civList);

            if (data.getString("mcaichat_home_id").equals(location.id())) {
                // This new location just became the NPC's home - it already knows its own home's
                // name, same exception as generateWorldKnowledge's initial scan applies.
                PlayerLocationData.get(serverLevel).reveal(location.id(), location.id());

                if (entity instanceof PathfinderMob pathfinderMob) {
                    data.putInt("mcaichat_home_x", entityPos.getX());
                    data.putInt("mcaichat_home_y", entityPos.getY());
                    data.putInt("mcaichat_home_z", entityPos.getZ());
                    pathfinderMob.restrictTo(entityPos, Config.HOME_RADIUS.get());
                }
            }
        }
    }
}