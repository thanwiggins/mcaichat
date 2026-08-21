package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// Client -> server: "I'd like to start (or resume) a conversation with this NPC." Sent before any
// dialogue is generated - by a player-initiated chat message (ChatInterceptor) or an NPC-initiated
// greeting roll (ConversationManager.onClientTick) alike - so only one player can ever hold an
// NPC's conversation at a time. See ConversationClaimResponsePacket for the grant/deny reply.
public class ConversationClaimRequestPacket {
    public final int entityId;

    public ConversationClaimRequestPacket(int entityId) {
        this.entityId = entityId;
    }

    public ConversationClaimRequestPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity target = sender.level().getEntity(this.entityId);
            if (target == null) return;

            CompoundTag data = target.getPersistentData();
            boolean granted;
            if (data.getBoolean("mcaichat_is_chatting") && data.contains("mcaichat_chatting_player")) {
                java.util.UUID holderId = data.getUUID("mcaichat_chatting_player");
                boolean holderStillOnline = sender.getServer().getPlayerList().getPlayer(holderId) != null;
                granted = holderId.equals(sender.getUUID()) || !holderStillOnline;
            } else {
                granted = true;
            }

            if (granted) {
                data.putBoolean("mcaichat_is_chatting", true);
                data.putUUID("mcaichat_chatting_player", sender.getUUID());
            }

            String memory = data.getString("mcaichat_memory");
            long memoryTick = data.getLong("mcaichat_memory_last_convo_tick");
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender),
                    new ConversationClaimResponsePacket(this.entityId, granted, memory, memoryTick));
        });
        ctx.get().setPacketHandled(true);
    }
}
