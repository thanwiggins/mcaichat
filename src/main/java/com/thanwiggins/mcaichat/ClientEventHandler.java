package com.thanwiggins.mcaichat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Load the lore, memory, and social data when the player joins the world
        ClientLoreManager.loadWorldLore();
        ClientMemoryManager.loadWorldMemory();
        ClientSocialManager.loadWorldSocial();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Save everything when the player disconnects or leaves the world
        ClientLoreManager.saveWorldLore();
        ClientMemoryManager.saveWorldMemory();
        ClientSocialManager.saveWorldSocial();
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // If the entity dies and it's an AI character, mark them deceased with the cause
        if (entity.level().isClientSide() && Config.isWhitelisted(entity)) {
            String cause = event.getSource().getLocalizedDeathMessage(entity).getString();
            ClientSocialManager.markDeceased(entity.getUUID(), cause);
        }
    }
}