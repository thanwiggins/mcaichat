package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: tells the receiving player's own client to run ChatInterceptor's real chat
// pipeline for a message toward a given NPC, as if the player had typed it. Used by
// NpcDirectiveCommands so a /follow, /goto, /stay, or /resume message is actually said by the
// player and gets a real, in-character Gemini response - not a scripted line from the server.
public class TriggerChatPacket {
    public final int entityId;
    public final String message;

    public TriggerChatPacket(int entityId, String message) {
        this.entityId = entityId;
        this.message = message;
    }

    public TriggerChatPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.message = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeUtf(this.message);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            Entity target = mc.level.getEntity(this.entityId);
            if (target != null) {
                ChatInterceptor.sendPlayerMessage(mc.player, target, this.message);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
