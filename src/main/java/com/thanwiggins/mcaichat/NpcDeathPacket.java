package com.thanwiggins.mcaichat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class NpcDeathPacket {
    public final UUID entityUuid;
    public final String cause;

    public NpcDeathPacket(UUID entityUuid, String cause) {
        this.entityUuid = entityUuid;
        this.cause = cause;
    }

    public NpcDeathPacket(FriendlyByteBuf buf) {
        this.entityUuid = buf.readUUID();
        this.cause = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.entityUuid);
        buf.writeUtf(this.cause);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientSocialManager.markDeceased(this.entityUuid, this.cause);
        });
        ctx.get().setPacketHandled(true);
    }
}