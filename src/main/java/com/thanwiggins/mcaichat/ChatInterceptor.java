package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ChatInterceptor {

    // Add these variables to store the last sent prompt
    public static String lastSystemPrompt = "No prompt sent yet.";
    public static String lastUserMessage = "No message sent yet.";

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();

        // Let standard Minecraft commands (like /gamemode, /time set) pass through normally
        if (message.startsWith("/")) {
            return;
        }

        // Intercept all other chat
        event.setCanceled(true); 
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        // Add to chat history so the player can use the UP arrow key
        mc.gui.getChat().addRecentChat(message);

        // Validate API Key
        String apiKey = Config.API_KEY.get();
        if (apiKey == null || apiKey.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Error] Gemini API Key is missing! Click 'Mods' in the menu to configure it."));
            return;
        }

        // Find out who the player is trying to talk to
        Entity targetEntity = getTargetEntity(mc, player);

        if (targetEntity == null) {
            player.sendSystemMessage(Component.literal("§7(No one is around to hear you...)"));
            return;
        }

        // Read their assigned name directly from their NBT data
        String entityName = targetEntity.getPersistentData().getString("mcaichat_name");
        if (entityName.isEmpty()) entityName = targetEntity.getDisplayName().getString(); // fallback just in case
        
        player.sendSystemMessage(Component.literal("§7[You] -> " + entityName + ": §f" + message));
        
        // Build the dynamic System Prompt
        String systemPrompt = PromptBuilder.getSystemPrompt(player, targetEntity);
        
        lastSystemPrompt = systemPrompt;
        lastUserMessage = message;

        // Fire the request to Gemini
        GeminiClient.sendMessage(apiKey, systemPrompt, message, entityName);
    }

    /**
     * Determines which entity the player intends to chat with.
     * Prioritizes the entity in the player's crosshair (raycast). 
     * If looking at nothing, falls back to the absolute closest whitelisted entity in an 8-block radius.
     */
    private static Entity getTargetEntity(Minecraft mc, Player player) {
        // 1. Try Raycast (Crosshair Target)
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (Config.isWhitelisted(hitEntity)) {
                return hitEntity;
            }
        }

        // 2. Try Proximity (8 block radius)
        AABB searchBox = player.getBoundingBox().inflate(8.0D);
        List<Entity> nearbyEntities = player.level().getEntities(player, searchBox, Config::isWhitelisted);

        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity e : nearbyEntities) {
            double dist = player.distanceToSqr(e);
            if (dist < closestDistance) {
                closest = e;
                closestDistance = dist;
            }
        }

        return closest;
    }
}