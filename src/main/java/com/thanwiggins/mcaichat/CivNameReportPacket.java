package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: this client just assigned a civilization structure its immediate (possibly
// pre-lore) display name - see ClientLoreManager.onStructureEntered. Deliberately independent of
// LoreReportPacket/StructureLoreData, which only ever reports a civ once Gemini's background text
// succeeds (GeminiClient's failure/no-API-key paths never call reportToServer) - the waypoint
// feature can't wait on that. First report for a given structure wins - see
// PlayerCivWaypointData.recordName.
public class CivNameReportPacket {
    public final String structureId;
    public final String name;

    public CivNameReportPacket(String structureId, String name) {
        this.structureId = structureId;
        this.name = name;
    }

    public CivNameReportPacket(FriendlyByteBuf buf) {
        this.structureId = buf.readUtf(256);
        this.name = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.structureId);
        buf.writeUtf(this.name);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            PlayerCivWaypointData.get(sender.serverLevel()).recordName(this.structureId, this.name);
        });
        ctx.get().setPacketHandled(true);
    }
}
