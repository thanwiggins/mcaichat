package com.thanwiggins.mcaichat;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(GeminiMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    // Registration order fixes each packet's numeric ID for this mod version - add new packets
    // at the end rather than reordering these, so old and new clients/servers never disagree on IDs.
    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, StructurePacket.class,
                StructurePacket::encode,
                StructurePacket::new,
                StructurePacket::handle);

        INSTANCE.registerMessage(id++, SyncNPCPacket.class,
                SyncNPCPacket::encode,
                SyncNPCPacket::new,
                SyncNPCPacket::handle);

        INSTANCE.registerMessage(id++, ConversationStatePacket.class,
                ConversationStatePacket::encode,
                ConversationStatePacket::new,
                ConversationStatePacket::handle);

        INSTANCE.registerMessage(id++, NpcDeathPacket.class,
                NpcDeathPacket::encode,
                NpcDeathPacket::new,
                NpcDeathPacket::handle);
    }
}