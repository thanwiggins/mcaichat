package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: the nearest structure/roost to a player, sent once per second by
// ServerStructureTracker. Drives ClientLoreManager's naming and lore generation.
public class StructurePacket {
    public final String structureId;
    public final String structureType;
    public final String biomeName;

    public StructurePacket(String structureId, String structureType, String biomeName) {
        this.structureId = structureId;
        this.structureType = structureType;
        this.biomeName = biomeName;
    }

    public StructurePacket(FriendlyByteBuf buf) {
        this.structureId = buf.readUtf(256);
        this.structureType = buf.readUtf(256);
        this.biomeName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.structureId);
        buf.writeUtf(this.structureType);
        buf.writeUtf(this.biomeName);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientLoreManager.onStructureEntered(this.structureId, this.structureType, this.biomeName));
        ctx.get().setPacketHandled(true);
    }
}