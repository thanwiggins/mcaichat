package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
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

        Entity targetEntity = getTargetEntity(mc, player);

        if (targetEntity == null) {
            player.sendSystemMessage(Component.literal("§7(No one is around to hear you...)"));
            return;
        }

        sendPlayerMessage(player, targetEntity, message);
    }

    // Runs the same pipeline onClientChat runs (echo, location-reveal check, conversation
    // bookkeeping, system prompt, Gemini call) for a message that didn't come through real chat
    // input - e.g. NpcDirectiveCommands routes its /follow, /goto, /stay, /resume messages through
    // here (via TriggerChatPacket) so they read as, and actually are, the player talking to the
    // NPC rather than a scripted acknowledgement line.
    public static void sendPlayerMessage(Player player, Entity targetEntity, String message) {
        String apiKey = Config.API_KEY.get();
        if (apiKey == null || apiKey.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Error] Gemini API Key is missing! Click 'Mods' in the menu to configure it."));
            return;
        }

        checkForLocationReveal(targetEntity, message);

        long currentTick = targetEntity.level().getGameTime();

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

    // If the player just said a player-created location's real name to an NPC that knows about it
    // (and that NPC's civilization hasn't already learned it), tell the server so it can reveal
    // that name to every NPC sharing this NPC's home - not just this one. Only checks what this
    // specific NPC knows about (its own home, plus its nearby_civs list) - same "no broader
    // search" scoping /goto's named-location lookup already uses.
    private static void checkForLocationReveal(Entity targetEntity, String message) {
        CompoundTag data = targetEntity.getPersistentData();
        String homeId = data.getString("mcaichat_home_id");
        String lowerMessage = message.toLowerCase();

        List<String> candidateIds = new ArrayList<>();
        if (data.getString("mcaichat_home_type").equals("player_created")) {
            candidateIds.add(homeId);
        }
        if (data.contains("mcaichat_nearby_civs", 9)) {
            ListTag civList = data.getList("mcaichat_nearby_civs", 8);
            for (int i = 0; i < civList.size(); i++) {
                String[] parts = civList.getString(i).split("\\|");
                if (parts.length == 5 && parts[1].equals("player_created")) {
                    candidateIds.add(parts[0]);
                }
            }
        }

        for (String locationId : candidateIds) {
            ClientLocationManager.LocationInfo info = ClientLocationManager.get(locationId);
            if (info == null || info.revealedHomeIds.contains(homeId)) continue;

            if (lowerMessage.contains(info.name.toLowerCase())) {
                NetworkHandler.INSTANCE.sendToServer(new LocationRevealPacket(locationId, homeId));
            }
        }
    }

    // Package-visible (not private) - GotoCommand reuses this for the same crosshair-first,
    // else-nearest-with-line-of-sight targeting UX, rather than duplicating it.
    static Entity getTargetEntity(Minecraft mc, Player player) {
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (Config.isWhitelisted(hitEntity) && !(hitEntity instanceof LivingEntity le && le.isSleeping())) {
                return hitEntity;
            }
        }

        AABB searchBox = player.getBoundingBox().inflate(8.0D);
        List<Entity> nearbyEntities = player.level().getEntities(player, searchBox, e ->
            Config.isWhitelisted(e) && player.hasLineOfSight(e) &&
            !(e instanceof LivingEntity le && le.isSleeping()) // asleep in bed - can't be woken up to chat
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