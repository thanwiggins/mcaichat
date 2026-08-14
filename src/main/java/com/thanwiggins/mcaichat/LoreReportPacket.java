package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: this client just finished generating (or locally deciding on) lore for a
// structure it hadn't seen before - see ClientLoreManager.reportToServer. The server accepts it
// only if nobody's reported this structure first (StructureLoreData.addIfAbsent), then broadcasts
// it to everyone via LoreSyncPacket, so it becomes the one shared answer instead of every player
// generating (and possibly getting a different) backstory for the same structure.
public class LoreReportPacket {
    public final String structureId;
    public final String name;
    public final String background;
    public final String type;
    public final String fullKey;

    public LoreReportPacket(String structureId, String name, String background, String type, String fullKey) {
        this.structureId = structureId;
        this.name = name;
        this.background = background;
        this.type = type;
        this.fullKey = fullKey;
    }

    public LoreReportPacket(FriendlyByteBuf buf) {
        this.structureId = buf.readUtf(256);
        this.name = buf.readUtf(256);
        this.background = buf.readUtf(32767);
        this.type = buf.readUtf(256);
        this.fullKey = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.structureId);
        buf.writeUtf(this.name);
        buf.writeUtf(this.background);
        buf.writeUtf(this.type);
        buf.writeUtf(this.fullKey);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            ServerLevel level = sender.serverLevel();
            StructureLoreData.LoreEntry entry = new StructureLoreData.LoreEntry(this.name, this.background, this.type, this.fullKey);
            if (StructureLoreData.get(level).addIfAbsent(this.structureId, entry)) {
                NetworkHandler.broadcastNewLore(level, this.structureId, entry);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
