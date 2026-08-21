package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: grant/deny reply to ConversationClaimRequestPacket, piggybacking the NPC's
// current shared memory dossier (see the Aside: shared NPC memory section of the multiplayer
// plan) so a granted conversation doesn't need a second round trip to fetch it.
public class ConversationClaimResponsePacket {
    public final int entityId;
    public final boolean granted;
    public final String memorySummary;
    public final long memoryLastConvoTick;

    public ConversationClaimResponsePacket(int entityId, boolean granted, String memorySummary, long memoryLastConvoTick) {
        this.entityId = entityId;
        this.granted = granted;
        this.memorySummary = memorySummary;
        this.memoryLastConvoTick = memoryLastConvoTick;
    }

    public ConversationClaimResponsePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.granted = buf.readBoolean();
        this.memorySummary = buf.readUtf(32767);
        this.memoryLastConvoTick = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.granted);
        buf.writeUtf(this.memorySummary);
        buf.writeLong(this.memoryLastConvoTick);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ConversationManager.onClaimResponse(
                this.entityId, this.granted, this.memorySummary, this.memoryLastConvoTick));
        ctx.get().setPacketHandled(true);
    }
}
