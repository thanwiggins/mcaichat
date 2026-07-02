package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects Ice and Fire dragon roosts without any compile-time dependency on Ice and Fire.
 * Roosts are worldgen Features, not registered Structures, so they never show up via
 * Registries.STRUCTURE. Instead, this reads the resident dragon's own home-position NBT
 * (public on EntityDragonBase, permanent for the life of the dragon) via the entity's
 * standard vanilla save data - no I&F classes are ever referenced, so this is a no-op
 * (never matches anything) on a world where Ice and Fire isn't installed.
 */
public class DragonRoostFinder {
    private static final boolean ICEANDFIRE_LOADED = ModList.get().isLoaded("iceandfire");

    public record Roost(String id, String type, String biome, BlockPos pos, double distSqr) {}

    public static boolean isIceAndFireLoaded() {
        return ICEANDFIRE_LOADED;
    }

    public static Roost findNearest(ServerLevel level, BlockPos origin, double searchRadius) {
        List<Roost> all = findAll(level, origin, searchRadius);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<Roost> findAll(ServerLevel level, BlockPos origin, double searchRadius) {
        if (!ICEANDFIRE_LOADED) return List.of();

        AABB searchBox = new AABB(origin).inflate(searchRadius);
        List<Entity> dragons = level.getEntities((Entity) null, searchBox, DragonRoostFinder::isDragon);

        List<Roost> roosts = new ArrayList<>();

        for (Entity dragon : dragons) {
            String element = getDragonElement(dragon);
            if (element == null) continue;

            CompoundTag tag = dragon.saveWithoutId(new CompoundTag());
            if (!tag.getBoolean("HasHomePosition")) continue;

            BlockPos homePos = new BlockPos(tag.getInt("HomeAreaX"), tag.getInt("HomeAreaY"), tag.getInt("HomeAreaZ"));
            double distSqr = origin.distSqr(homePos);
            // Treat the roost as having a ~20 block radius so it wins ties against overlapping vanilla structures
            double actualDist = distSqr <= 400 ? 0 : distSqr;

            String fullKey = "iceandfire:" + element + "_dragon_roost";
            String id = fullKey + "_" + (homePos.getX() >> 4) + "_" + (homePos.getZ() >> 4);
            String biome = level.getBiome(homePos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
            roosts.add(new Roost(id, fullKey, biome, homePos, actualDist));
        }

        roosts.sort(Comparator.comparingDouble(Roost::distSqr));
        return roosts;
    }

    private static boolean isDragon(Entity entity) {
        return getDragonElement(entity) != null;
    }

    private static String getDragonElement(Entity entity) {
        String key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        return switch (key) {
            case "iceandfire:fire_dragon" -> "fire";
            case "iceandfire:ice_dragon" -> "ice";
            case "iceandfire:lightning_dragon" -> "lightning";
            default -> null;
        };
    }
}
