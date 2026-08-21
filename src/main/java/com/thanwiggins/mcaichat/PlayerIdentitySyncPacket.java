package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

// Server -> client: one player's identity (broadcast whenever PlayerIdentityUpdatePacket is
// received) or every currently-online player's identity (sent to a newly-joining player) - same
// one-payload-shape-for-both-uses trick LoreSyncPacket/SocialRosterSyncPacket use. Feeds
// PromptBuilder's client-side identity cache so a chosen display name/description resolves for
// any online player, not just the local one.
public class PlayerIdentitySyncPacket {
    public record Entry(UUID playerId, String displayName, String description) {}

    public final List<Entry> entries;

    public PlayerIdentitySyncPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public PlayerIdentitySyncPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(buf.readUUID(), buf.readUtf(64), buf.readUtf(150)));
        }
        this.entries = list;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entries.size());
        for (Entry entry : this.entries) {
            buf.writeUUID(entry.playerId());
            buf.writeUtf(entry.displayName());
            buf.writeUtf(entry.description());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            for (Entry entry : this.entries) {
                PlayerIdentityCache.put(entry.playerId(), entry.displayName(), entry.description());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
