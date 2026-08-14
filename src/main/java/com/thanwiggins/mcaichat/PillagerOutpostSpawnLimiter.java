package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

// Vanilla lets a Pillager Outpost naturally respawn pillagers (up to 8 alive at once) forever, with
// no lifetime limit - which also means /base claim's hasLivingDefenders check (see
// PlayerLocationCommands) can never succeed against one once pillagers are whitelisted as that
// structure's defenders, since a replacement always lands before the last defender is dead. This
// gives each outpost a one-time lifetime supply instead, via Config.MAX_PILLAGERS_PER_OUTPOST, so
// it can eventually run dry and actually be cleared/claimed. Patrols and raid-summoned pillagers
// (MobSpawnType.PATROL/EVENT/REINFORCEMENT) are untouched - only the outpost's own ambient
// respawn cycle (MobSpawnType.NATURAL) is capped.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class PillagerOutpostSpawnLimiter {
    private static final String PILLAGER_OUTPOST_KEY = "minecraft:pillager_outpost";

    // Vanilla ships no such tag itself (there's no data/minecraft/tags/worldgen/structure/
    // pillager_outpost.json in the base game), but structure-variant mods that add their own
    // outposts under a different namespace - e.g. Towns and Towers' 22 towns_and_towers:*
    // biome variants - opt their structures into this shared tag specifically so other mods can
    // treat them uniformly. Checking it costs nothing when it's empty/undefined, so this is safe
    // to always check alongside the literal vanilla key below.
    private static final TagKey<Structure> PILLAGER_OUTPOST_TAG =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation("pillager_outpost"));

    // Outposts spawn pillagers in a roughly 72-block-wide volume centered on the watchtower, well
    // beyond the structure's own generated bounding box, so a spawn has to be associated with the
    // nearest outpost within this radius rather than requiring strict bounding-box containment.
    private static final double ASSOCIATION_RADIUS_BLOCKS = 45.0;
    private static final int SCAN_RADIUS_CHUNKS = 4;

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (Config.MAX_PILLAGERS_PER_OUTPOST.get() <= 0) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (event.getEntity().getType() != EntityType.PILLAGER) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = new BlockPos((int) event.getX(), (int) event.getY(), (int) event.getZ());
        String outpostId = findNearbyOutpostId(level, pos);
        if (outpostId == null) return;

        PillagerOutpostSpawnData data = PillagerOutpostSpawnData.get(level);
        if (data.getCount(outpostId) >= Config.MAX_PILLAGERS_PER_OUTPOST.get()) {
            event.setSpawnCancelled(true);
            return;
        }
        data.increment(outpostId);
    }

    // Same chunk-scan technique PlayerLocationCommands/IdentityHandler already use to find nearby
    // structures, narrowed to a small radius - outposts are a single-point feature, not a
    // sprawling multi-piece structure like a village - and matching either the exact vanilla
    // structure key or membership in PILLAGER_OUTPOST_TAG, so modded outpost variants count too.
    private static String findNearbyOutpostId(ServerLevel level, BlockPos pos) {
        double maxDistSqr = ASSOCIATION_RADIUS_BLOCKS * ASSOCIATION_RADIUS_BLOCKS;
        ChunkPos centerChunk = new ChunkPos(pos);
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (int x = -SCAN_RADIUS_CHUNKS; x <= SCAN_RADIUS_CHUNKS; x++) {
            for (int z = -SCAN_RADIUS_CHUNKS; z <= SCAN_RADIUS_CHUNKS; z++) {
                ChunkAccess chunk = level.getChunk(centerChunk.x + x, centerChunk.z + z, ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;

                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start == null || !start.isValid()) continue;

                    Structure structure = entry.getKey();
                    ResourceLocation key = structureRegistry.getKey(structure);
                    boolean isVanillaOutpost = key != null && key.toString().equals(PILLAGER_OUTPOST_KEY);
                    boolean isTaggedOutpost = structureRegistry.wrapAsHolder(structure).is(PILLAGER_OUTPOST_TAG);
                    if (!isVanillaOutpost && !isTaggedOutpost) continue;

                    BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                    double distSqr = start.getBoundingBox().isInside(pos) ? 0.0 : pos.distSqr(startPos);
                    if (distSqr <= maxDistSqr) {
                        String fullKey = key != null ? key.toString() : PILLAGER_OUTPOST_KEY;
                        return fullKey + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                    }
                }
            }
        }
        return null;
    }
}
