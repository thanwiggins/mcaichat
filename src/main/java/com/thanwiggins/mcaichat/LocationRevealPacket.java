package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: the player said a player-created location's real name in conversation with an
// NPC that knows about it. Reveals that location for this NPC's whole home (not just the NPC that
// was talked to), then re-broadcasts the registry so every client's NPC prompts reflect it right
// away. Modeled directly on ConversationStatePacket - the mod's existing "client observed
// something, server should mutate persistent state" packet.
public class LocationRevealPacket {
    public final String locationId;
    public final String homeId;

    public LocationRevealPacket(String locationId, String homeId) {
        this.locationId = locationId;
        this.homeId = homeId;
    }

    public LocationRevealPacket(FriendlyByteBuf buf) {
        this.locationId = buf.readUtf(256);
        this.homeId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.locationId);
        buf.writeUtf(this.homeId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            ServerLevel level = sender.serverLevel();
            if (PlayerLocationData.get(level).reveal(this.locationId, this.homeId)) {
                NetworkHandler.broadcastLocations(level);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
