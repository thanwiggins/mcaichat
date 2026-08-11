package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: the full player-created-location registry (name, description, reveal state),
// sent on login and whenever the registry changes. Keeps ClientLocationManager - which
// PromptBuilder reads from - in sync with PlayerLocationData's server-authoritative truth.
public class LocationSyncPacket {
    public final CompoundTag data;

    public LocationSyncPacket(CompoundTag data) {
        this.data = data;
    }

    public LocationSyncPacket(FriendlyByteBuf buf) {
        this.data = buf.readAnySizeNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientLocationManager.replaceAll(this.data));
        ctx.get().setPacketHandled(true);
    }
}
