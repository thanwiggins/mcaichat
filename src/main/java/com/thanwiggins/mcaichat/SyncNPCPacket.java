package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

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
                    registerSocialCircle(entity);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // Registers a newly-synced NPC into its home's social circle right away, rather than
    // relying solely on NameplateRenderer - which only runs once a player actually looks at
    // this specific entity's nameplate. Without this, an NPC placed into an existing home
    // wouldn't show up in its new housemates' social circles until someone happened to look at it.
    private static void registerSocialCircle(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        String name = data.getString("mcaichat_name");
        if (name.isEmpty()) return;

        String homeId = data.getString("mcaichat_home_id");
        if (homeId.isEmpty() || homeId.equals("none")) return;

        String type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).getPath();
        String personality = data.getString("mcaichat_personality");
        String cap = PromptBuilder.getShortCapabilityString(entity);
        ClientSocialManager.addCitizen(homeId, entity.getUUID(), name, type, personality, cap);
    }
}