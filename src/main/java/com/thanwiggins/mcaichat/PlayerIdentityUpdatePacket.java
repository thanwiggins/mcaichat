package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// Client -> server: the sender's chosen display name/description (GeminiConfigScreen). Persisted
// on the sender's own player entity data (survives relog, same idiom as mcaichat_known_players),
// and broadcast to every other online player so PromptBuilder.getPlayerDisplayName resolves
// correctly for someone other than the local player - see PlayerIdentitySyncPacket.
public class PlayerIdentityUpdatePacket {
    public final String displayName;
    public final String description;

    public PlayerIdentityUpdatePacket(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public PlayerIdentityUpdatePacket(FriendlyByteBuf buf) {
        this.displayName = buf.readUtf(64);
        this.description = buf.readUtf(150);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.displayName);
        buf.writeUtf(this.description);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            sender.getPersistentData().putString("mcaichat_player_display_name", this.displayName);
            sender.getPersistentData().putString("mcaichat_player_description", this.description);

            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new PlayerIdentitySyncPacket(java.util.List.of(
                            new PlayerIdentitySyncPacket.Entry(sender.getUUID(), this.displayName, this.description))));
        });
        ctx.get().setPacketHandled(true);
    }
}
