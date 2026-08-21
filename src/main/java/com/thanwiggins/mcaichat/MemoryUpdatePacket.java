package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: the freshly-merged memory dossier for an NPC, produced locally by
// GeminiClient.summarizeConversation at the end of a conversation. Written straight to the
// entity's own persistent data (mcaichat_memory/mcaichat_memory_last_convo_tick) - safe with no
// ownership check, since only the current conversation's claim-holder is ever mid-summarize on
// a given NPC (see ConversationClaimRequestPacket).
public class MemoryUpdatePacket {
    public final int entityId;
    public final String newSummary;
    public final long currentTick;

    public MemoryUpdatePacket(int entityId, String newSummary, long currentTick) {
        this.entityId = entityId;
        this.newSummary = newSummary;
        this.currentTick = currentTick;
    }

    public MemoryUpdatePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.newSummary = buf.readUtf(32767);
        this.currentTick = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeUtf(this.newSummary);
        buf.writeLong(this.currentTick);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity target = sender.level().getEntity(this.entityId);
            if (target == null) return;

            target.getPersistentData().putString("mcaichat_memory", this.newSummary);
            target.getPersistentData().putLong("mcaichat_memory_last_convo_tick", this.currentTick);
        });
        ctx.get().setPacketHandled(true);
    }
}
