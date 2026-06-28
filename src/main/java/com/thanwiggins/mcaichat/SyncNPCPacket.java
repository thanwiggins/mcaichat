package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncNPCPacket {
    public final int entityId;
    public final CompoundTag aiData;
    public final String tradesString;

    public SyncNPCPacket(int entityId, CompoundTag aiData, String tradesString) {
        this.entityId = entityId;
        this.aiData = aiData;
        this.tradesString = tradesString;
    }

    public SyncNPCPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.aiData = buf.readAnySizeNbt();
        this.tradesString = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeNbt(this.aiData);
        buf.writeUtf(this.tradesString);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(this.entityId);
                if (entity != null) {
                    entity.getPersistentData().merge(this.aiData);
                    entity.getPersistentData().putString("mcaichat_trades", this.tradesString);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}