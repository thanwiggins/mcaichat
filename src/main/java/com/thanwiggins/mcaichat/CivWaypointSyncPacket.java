package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: one or more visited-civilization waypoint entries (structureId, name,
// dimension, position), sent as a full dump on login (see NetworkHandler.sendCivWaypointsTo) and
// as a single-entry push the moment ServerStructureTracker credits a player with a brand-new
// visit. Modeled on LoreSyncPacket - always merges/re-applies into Xaero rather than replacing
// anything, since a single-entry update must never disturb waypoints registered earlier in the
// session (see XaeroWaypointSupport.apply).
public class CivWaypointSyncPacket {
    public final CompoundTag data;

    public CivWaypointSyncPacket(CompoundTag data) {
        this.data = data;
    }

    public CivWaypointSyncPacket(FriendlyByteBuf buf) {
        this.data = buf.readAnySizeNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> XaeroWaypointSupport.apply(this.data));
        ctx.get().setPacketHandled(true);
    }
}
