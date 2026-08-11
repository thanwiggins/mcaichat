package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> server: GotoCommand has already resolved a /goto destination (coordinates, x/z-only,
// or a known location's real nickname - all of which may need client-only data like
// ClientLoreManager/ClientLocationManager) down to a final BlockPos and message; the server just
// applies it, the same way NpcDirectiveCommands' other directives do.
public class GoToPacket {
    public final int entityId;
    public final int x;
    public final int y;
    public final int z;
    public final String message;

    public GoToPacket(int entityId, BlockPos pos, String message) {
        this.entityId = entityId;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.message = message;
    }

    public GoToPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.message = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeUtf(this.message);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity target = sender.level().getEntity(this.entityId);
            if (target == null) return;

            NpcDirectiveCommands.setGotoDirective(target, sender, new BlockPos(this.x, this.y, this.z));
            NpcDirectiveCommands.speak(sender, target, this.message);
        });
        ctx.get().setPacketHandled(true);
    }
}
