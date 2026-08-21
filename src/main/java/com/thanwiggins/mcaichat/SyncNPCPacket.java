package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: mirrors an NPC's persistent AI data (name, personality, home, etc.) plus
// pre-formatted trade and status-effect summaries, since a client can't reliably read another
// entity's Merchant offers, and - it turns out - can't reliably read another entity's active
// MobEffectInstances either (unlike health/food, effects aren't part of an entity's always-synced
// metadata; they ride a separate packet that doesn't consistently reach observers for every entity).
public class SyncNPCPacket {
    public final int entityId;
    public final CompoundTag aiData;
    public final String tradesString;
    public final String effectsString;

    public SyncNPCPacket(int entityId, CompoundTag aiData, String tradesString, String effectsString) {
        this.entityId = entityId;
        this.aiData = aiData;
        this.tradesString = tradesString;
        this.effectsString = effectsString;
    }

    public SyncNPCPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.aiData = buf.readAnySizeNbt();
        this.tradesString = buf.readUtf(32767);
        this.effectsString = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeNbt(this.aiData);
        buf.writeUtf(this.tradesString);
        buf.writeUtf(this.effectsString);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(this.entityId);
                if (entity != null) {
                    entity.getPersistentData().merge(this.aiData);
                    entity.getPersistentData().putString("mcaichat_trades", this.tradesString);
                    entity.getPersistentData().putString("mcaichat_effects", this.effectsString);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}