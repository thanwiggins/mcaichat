package com.thanwiggins.mcaichat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Load the lore and memory when the player joins the world
        ClientLoreManager.loadWorldLore();
        ClientMemoryManager.loadWorldMemory();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Save everything when the player disconnects or leaves the world
        ClientLoreManager.saveWorldLore();
        ClientMemoryManager.saveWorldMemory();
    }
}