package com.thanwiggins.mcaichat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Load the memory and social data when the player joins the world - lore is no longer
        // disk-cached here, it's re-synced from the server instead (see LoreSyncPacket).
        ClientMemoryManager.loadWorldMemory();
        ClientSocialManager.loadWorldSocial();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Save everything when the player disconnects or leaves the world
        ClientMemoryManager.saveWorldMemory();
        ClientSocialManager.saveWorldSocial();
    }
}