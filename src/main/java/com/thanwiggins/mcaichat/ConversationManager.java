package com.thanwiggins.mcaichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ConversationManager {
    public static Entity activeEntity = null;
    public static JsonArray conversationHistory = new JsonArray();
    public static long lastMessageTick = 0; 
    
    public static long lastInitiationTick = 0;
    
    // CHANGED: Replaced seenEntities Set with a Cooldown Map
    public static Map<UUID, Long> initiationCooldowns = new HashMap<>();

    public static void startConversation(Entity target, long currentTick) {
        activeEntity = target;
        conversationHistory = new JsonArray();
        lastMessageTick = currentTick;
    }

    public static void endConversation(long currentTick) {
        if (activeEntity != null && conversationHistory.size() > 0) {
            String apiKey = Config.API_KEY.get();
            if (apiKey != null && !apiKey.isEmpty()) {
                GeminiClient.summarizeConversation(apiKey, activeEntity, conversationHistory.deepCopy(), currentTick);
            }
        }
        activeEntity = null;
        conversationHistory = new JsonArray();
    }

    public static void addMessage(String role, String text, long currentTick) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        
        JsonObject content = new JsonObject();
        content.addProperty("role", role);
        content.add("parts", parts);
        
        conversationHistory.add(content);
        lastMessageTick = currentTick;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long currentTick = mc.level.getGameTime();

        // 1. Check Active Conversation End Conditions
        if (activeEntity != null) {
            boolean timeout = (currentTick - lastMessageTick) > 1200; 
            boolean tooFar = mc.player.distanceToSqr(activeEntity) > 2500; 
            boolean deadOrRemoved = !activeEntity.isAlive() || activeEntity.isRemoved();

            if (timeout || tooFar || deadOrRemoved) {
                endConversation(currentTick);
            }
        }

        // 2. Check AI Initiation Condition
        if (activeEntity == null && (currentTick - lastInitiationTick) > 200) { 
            AABB box = mc.player.getBoundingBox().inflate(8.0D);
            
            // CHANGED: Check if the entity is not in the map, OR if their personal cooldown (6000 ticks = 5 mins) has expired
            List<Entity> nearby = mc.level.getEntities(mc.player, box, e -> 
                Config.isWhitelisted(e) && !Config.isBlacklisted(e) &&
                (!initiationCooldowns.containsKey(e.getUUID()) || (currentTick - initiationCooldowns.get(e.getUUID())) > 6000) &&
                mc.player.hasLineOfSight(e) // <-- Added Line of Sight check!
            );
            
            if (!nearby.isEmpty()) {
                Entity target = nearby.get(0);
                
                // Put them on cooldown whether they win or lose the coin toss
                initiationCooldowns.put(target.getUUID(), currentTick);
                
                // 50% chance to initiate
                if (Math.random() < 0.5) {
                    lastInitiationTick = currentTick;
                    startConversation(target, currentTick);
                    
                    String apiKey = Config.API_KEY.get();
                    if (apiKey != null && !apiKey.isEmpty()) {
                        String sysPrompt = PromptBuilder.getSystemPrompt(mc.player, target);
                        String name = target.getPersistentData().getString("mcaichat_name");
                        if (name.isEmpty()) name = target.getDisplayName().getString();
                        
                        GeminiClient.initiateConversation(apiKey, sysPrompt, name, currentTick);
                    }
                }
            }
        }
    }
}