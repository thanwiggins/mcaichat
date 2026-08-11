package com.thanwiggins.mcaichat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Server-side equivalent of ChatInterceptor.getTargetEntity (crosshair-first, else nearest-in-radius
// with line of sight) - the client's mc.hitResult isn't available server-side, so directive commands
// need their own raycast to keep the same "who am I talking to" UX as chat targeting.
public class NpcTargetResolver {
    private static final double CROSSHAIR_REACH = 20.0D;
    private static final double FALLBACK_RADIUS = 8.0D;

    public static Entity getTargetEntity(Player player) {
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 endVec = eyePos.add(viewVec.scale(CROSSHAIR_REACH));
        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(CROSSHAIR_REACH)).inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eyePos, endVec, searchBox,
                NpcTargetResolver::isEligible, CROSSHAIR_REACH * CROSSHAIR_REACH);

        if (hit != null && isEligible(hit.getEntity())) {
            return hit.getEntity();
        }

        AABB fallbackBox = player.getBoundingBox().inflate(FALLBACK_RADIUS);
        List<Entity> nearby = player.level().getEntities(player, fallbackBox,
                e -> isEligible(e) && player.hasLineOfSight(e));

        Entity closest = null;
        double closestDistSqr = Double.MAX_VALUE;
        for (Entity e : nearby) {
            double distSqr = player.distanceToSqr(e);
            if (distSqr < closestDistSqr) {
                closest = e;
                closestDistSqr = distSqr;
            }
        }
        return closest;
    }

    private static boolean isEligible(Entity entity) {
        return Config.isWhitelisted(entity) && !(entity instanceof LivingEntity le && le.isSleeping());
    }
}
