package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: one home's citizen roster (broadcast whenever it changes) or the full
// registry (sent on login) - same one-payload-format-for-both-uses trick LoreSyncPacket uses.
// Keeps ClientSocialManager - which PromptBuilder reads from - in sync with SocialRosterData's
// server-authoritative truth.
public class SocialRosterSyncPacket {
    public final CompoundTag data;

    public SocialRosterSyncPacket(CompoundTag data) {
        this.data = data;
    }

    public SocialRosterSyncPacket(FriendlyByteBuf buf) {
        this.data = buf.readAnySizeNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSocialManager.merge(this.data));
        ctx.get().setPacketHandled(true);
    }
}
