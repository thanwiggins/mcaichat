package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: one or more structure lore entries (name, background, category), sent as a
// full dump on login and as a single-entry broadcast whenever a new structure's lore is accepted
// (see LoreReportPacket/StructureLoreData). Modeled on LocationSyncPacket, but merges into
// ClientLoreManager's map rather than replacing it wholesale, since a single-entry update must
// never wipe out everything else this client already knows.
public class LoreSyncPacket {
    public final CompoundTag data;

    public LoreSyncPacket(CompoundTag data) {
        this.data = data;
    }

    public LoreSyncPacket(FriendlyByteBuf buf) {
        this.data = buf.readAnySizeNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientLoreManager.merge(this.data));
        ctx.get().setPacketHandled(true);
    }
}
