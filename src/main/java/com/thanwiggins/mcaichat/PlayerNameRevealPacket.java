package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: the player said their own (configured) display name in conversation with an
// NPC that doesn't already know it. Records the player's UUID on that specific entity's
// persistent data - see PromptBuilder.knowsPlayerName/getPlayerDisplayName. Modeled directly on
// LocationRevealPacket, except this only affects the one NPC that was told, not its whole home.
public class PlayerNameRevealPacket {
    public final int entityId;

    public PlayerNameRevealPacket(int entityId) {
        this.entityId = entityId;
    }

    public PlayerNameRevealPacket(FriendlyByteBuf buf) {
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
            String playerId = sender.getUUID().toString();
            ListTag known = data.getList("mcaichat_known_players", 8);

            for (int i = 0; i < known.size(); i++) {
                if (known.getString(i).equals(playerId)) return;
            }

            known.add(StringTag.valueOf(playerId));
            data.put("mcaichat_known_players", known);
        });
        ctx.get().setPacketHandled(true);
    }
}
