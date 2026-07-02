package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConversationStatePacket {
    public final int entityId;
    public final boolean isChatting;

    public ConversationStatePacket(int entityId, boolean isChatting) {
        this.entityId = entityId;
        this.isChatting = isChatting;
    }

    public ConversationStatePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isChatting = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.isChatting);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && sender.level() != null) {
                Entity entity = sender.level().getEntity(this.entityId);
                if (entity != null) {
                    entity.getPersistentData().putBoolean("mcaichat_is_chatting", this.isChatting);
                    if (this.isChatting) {
                        entity.getPersistentData().putUUID("mcaichat_chatting_player", sender.getUUID());
                    } else {
                        entity.getPersistentData().remove("mcaichat_chatting_player");
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}