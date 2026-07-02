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

import java.util.List;

// Cancels vanilla chat messages and reroutes them to whichever whitelisted entity the
// player is looking at or standing near, so typing in chat feels like talking to that NPC.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ChatInterceptor {

    public static String lastSystemPrompt = "No prompt sent yet.";
    public static String lastUserMessage = "No message sent yet.";
    public static long lastSystemPromptTick = -1;

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();

        if (message.startsWith("/")) return;

        event.setCanceled(true); 
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null) return;
        mc.gui.getChat().addRecentChat(message);

        String apiKey = Config.API_KEY.get();
        if (apiKey == null || apiKey.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Error] Gemini API Key is missing! Click 'Mods' in the menu to configure it."));
            return;
        }

        Entity targetEntity = getTargetEntity(mc, player);

        if (targetEntity == null) {
            player.sendSystemMessage(Component.literal("§7(No one is around to hear you...)"));
            return;
        }

        long currentTick = mc.level.getGameTime();

        if (ConversationManager.activeEntity != null && !ConversationManager.activeEntity.getUUID().equals(targetEntity.getUUID())) {
            ConversationManager.endConversation(currentTick);
        }
        if (ConversationManager.activeEntity == null) {
            ConversationManager.startConversation(targetEntity, currentTick, false); // false: the player started this, not the NPC
        }

        String entityName = targetEntity.getPersistentData().getString("mcaichat_name");
        if (entityName.isEmpty()) entityName = targetEntity.getDisplayName().getString(); 
        
        player.sendSystemMessage(Component.literal("§7[You] -> " + entityName + ": §f" + message));
        
        String systemPrompt = PromptBuilder.getSystemPrompt(player, targetEntity, false);
        lastSystemPrompt = systemPrompt;
        lastUserMessage = message;
        lastSystemPromptTick = currentTick;

        String colorCode = PromptBuilder.getSentimentColorCode(player, targetEntity);

        ConversationManager.addMessage("user", message, currentTick);
        GeminiClient.sendMessage(apiKey, systemPrompt, ConversationManager.conversationHistory, entityName, colorCode);
    }

    private static Entity getTargetEntity(Minecraft mc, Player player) {
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (Config.isWhitelisted(hitEntity)) {
                return hitEntity;
            }
        }

        AABB searchBox = player.getBoundingBox().inflate(8.0D);
        List<Entity> nearbyEntities = player.level().getEntities(player, searchBox, e -> 
            Config.isWhitelisted(e) && player.hasLineOfSight(e)
        );

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