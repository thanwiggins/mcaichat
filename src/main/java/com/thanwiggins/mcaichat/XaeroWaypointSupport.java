package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Registers hidden-by-default Xaero's Minimap waypoints for civilizations the local player has
 * visited, via Xaero's own third-party waypoints API - the same mechanism Xaero's built-in
 * Waystones support uses (xaero.hud.compat.mods.SupportWaystones). This is the only class in the
 * mod that references Xaero classes; every entry point checks XAERO_LOADED first, so nothing here
 * is ever touched (no NoClassDefFoundError risk) when Xaero's Minimap isn't installed - same soft-
 * dependency style as DragonRoostFinder's ICEANDFIRE_LOADED check.
 */
public class XaeroWaypointSupport {
    private static final boolean XAERO_LOADED = ModList.get().isLoaded("xaerominimap");
    private static final ResourceLocation ORIGIN_ID = new ResourceLocation(GeminiMod.MODID, "civilizations");

    // Called from CivWaypointSyncPacket.handle for both the login dump and each single-entry
    // push - always additive, never wipes anything already registered this session.
    public static void apply(CompoundTag data) {
        if (!XAERO_LOADED) return;

        ListTag list = data.getList("waypoints", 10); // 10 = CompoundTag
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String structureId = entry.getString("id");
            String name = entry.getString("name");
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(entry.getString("dimension")));
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));

            addWaypoint(structureId, name, dimension, pos);
        }
    }

    private static void addWaypoint(String structureId, String name, ResourceKey<Level> dimension, BlockPos pos) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return; // not in a world yet (or Xaero hasn't initialized) - nothing to do

        MinimapWorldRootContainer rootContainer = session.getWorldManager().getAutoRootContainer();
        String subContainerNode = rootContainer.getSession().getDimensionHelper().getDimensionDirectoryName(dimension);
        MinimapWorldContainer dimensionContainer = rootContainer.addSubContainer(rootContainer.getPath().resolve(subContainerNode));

        ThirdPartyWaypoints waypoints = dimensionContainer.getThirdPartyWaypointManager().get(ORIGIN_ID);

        String initial = name.isEmpty() ? "C" : name.substring(0, 1).toUpperCase();
        WaypointColor color = WaypointColor.fromIndex(Math.floorMod(structureId.hashCode(), WaypointColor.values().length));

        Waypoint waypoint = new Waypoint(pos.getX(), pos.getY(), pos.getZ(), name, initial, color, WaypointPurpose.NORMAL);
        waypoint.setDisabled(true); // hidden by default - still visible/toggleable in Xaero's waypoint list
        waypoints.add(structureId, waypoint);
    }
}
