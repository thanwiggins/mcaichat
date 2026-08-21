package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: tells the server the sending player has ended a conversation with this NPC,
// releasing whatever claim they held on it (see ConversationClaimRequestPacket for how a claim is
// acquired in the first place). Only clears the claim if the sender actually still holds it, so a
// stale/duplicate release from a conversation that already lost its claim to someone else can't
// steal it back.
public class ConversationStatePacket {
    public final int entityId;

    public ConversationStatePacket(int entityId) {
        this.entityId = entityId;
    }

    public ConversationStatePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || sender.level() == null) return;

            Entity entity = sender.level().getEntity(this.entityId);
            if (entity == null) return;

            var data = entity.getPersistentData();
            if (data.contains("mcaichat_chatting_player") && data.getUUID("mcaichat_chatting_player").equals(sender.getUUID())) {
                data.putBoolean("mcaichat_is_chatting", false);
                data.remove("mcaichat_chatting_player");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
