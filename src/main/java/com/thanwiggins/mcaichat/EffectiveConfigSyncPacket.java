package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: the resolved whitelist/blacklist/wanderer/categorization lists - a dedicated
// server's hardcoded defaults, or a hosted world's host Config - sent on login and whenever the
// host saves a change in the config-edit screens. See EffectiveConfig/Config.getEffectiveList.
public class EffectiveConfigSyncPacket {
    public final CompoundTag data;

    public EffectiveConfigSyncPacket(CompoundTag data) {
        this.data = data;
    }

    public EffectiveConfigSyncPacket(FriendlyByteBuf buf) {
        this.data = buf.readAnySizeNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> EffectiveConfig.replaceAll(this.data));
        ctx.get().setPacketHandled(true);
    }
}
