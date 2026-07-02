package com.thanwiggins.mcaichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

// Owns the client's single active conversation: who it's with, its message history, and
// when it should time out. Also rolls the odds for NPCs to strike up a conversation on
// their own each tick, independent of the player clicking/messaging them first.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ConversationManager {
    public static Entity activeEntity = null;
    public static JsonArray conversationHistory = new JsonArray();
    public static long lastMessageTick = 0;

    public static long lastInitiationTick = 0;

    public static boolean debugInit = false;

    // Whether the NPC spoke first. Drives the "[ ! ]"/"[ * ]" nameplate marker and which
    // half of the split-brain prompt logic (reactive vs. initiation) supplied the last prompt.
    public static boolean isNpcInitiated = false;

    // Per-entity cooldown so the same NPC can't re-roll initiation every tick once it's near the player.
    public static Map<UUID, Long> initiationCooldowns = new HashMap<>();

    // Mirrors of the last NPC-initiated prompt, kept separately from ChatInterceptor's reactive-prompt
    // fields so /aichat debug can show whichever one actually fired most recently.
    public static String lastInitSystemPrompt = "No initiation sent yet.";
    public static String lastInitTargetName = "";
    public static long lastInitSystemPromptTick = -1;

    public static void startConversation(Entity target, long currentTick, boolean npcInitiated) {
        activeEntity = target;
        conversationHistory = new JsonArray();
        lastMessageTick = currentTick;
        isNpcInitiated = npcInitiated;

        // Tells the server to flag this entity as "chatting", which ChattingGoal reads to make it
        // stop wandering and look at the player for the duration of the conversation.
        NetworkHandler.INSTANCE.sendToServer(new ConversationStatePacket(target.getId(), true));
    }

    public static void endConversation(long currentTick) {
        if (activeEntity != null && conversationHistory.size() > 0) {
            String apiKey = Config.API_KEY.get();
            if (apiKey != null && !apiKey.isEmpty()) {
                GeminiClient.summarizeConversation(apiKey, activeEntity, conversationHistory.deepCopy(), currentTick);
            }
            
            NetworkHandler.INSTANCE.sendToServer(new ConversationStatePacket(activeEntity.getId(), false));
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

        if (activeEntity != null) {
            boolean timeout = (currentTick - lastMessageTick) > 1200; // 60 seconds of silence ends the conversation
            boolean tooFar = mc.player.distanceToSqr(activeEntity) > 576; // 24 blocks
            boolean deadOrRemoved = !activeEntity.isAlive() || activeEntity.isRemoved();

            if (timeout || tooFar || deadOrRemoved) {
                endConversation(currentTick);
            }
        }

        // Only look for a new NPC to strike up a conversation once every 10 seconds, and only when
        // the player isn't already in one.
        if (activeEntity == null && (currentTick - lastInitiationTick) > 200) {
            AABB box = mc.player.getBoundingBox().inflate(8.0D);

            List<Entity> nearby = mc.level.getEntities(mc.player, box, e ->
                Config.isWhitelisted(e) && !Config.isBlacklisted(e) &&
                (!initiationCooldowns.containsKey(e.getUUID()) || (currentTick - initiationCooldowns.get(e.getUUID())) > 6000) && // 5 minute per-NPC cooldown
                mc.player.hasLineOfSight(e)
            );

            if (!nearby.isEmpty()) {
                Entity target = nearby.get(0);

                // Put the NPC on cooldown regardless of the roll below, so a miss doesn't let it
                // re-roll again next cycle.
                initiationCooldowns.put(target.getUUID(), currentTick);

                double roll = Math.random(); // 50/50 chance the NPC actually speaks up

                if (debugInit) {
                    mc.player.sendSystemMessage(Component.literal(
                        "§e[Init Debug] §fRolled " + String.format("%.2f", roll) + " (Needs < 0.50) for " + target.getDisplayName().getString()
                    ));
                }

                if (roll < 0.5) {
                    lastInitiationTick = currentTick;
                    startConversation(target, currentTick, true); // true: the NPC initiated

                    // Sound + note particles cue the player that this NPC is speaking up on its own
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
                    for (int i = 0; i < 4; i++) {
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, 
                            target.getX() + (Math.random() - 0.5) * 0.5, 
                            target.getY() + target.getBbHeight() + 0.5D, 
                            target.getZ() + (Math.random() - 0.5) * 0.5, 
                            0.0D, 0.0D, 0.0D);
                    }

                    String apiKey = Config.API_KEY.get();
                    if (apiKey != null && !apiKey.isEmpty()) {
                        
                        String sysPrompt = PromptBuilder.getSystemPrompt(mc.player, target, true);

                        String name = target.getPersistentData().getString("mcaichat_name");
                        if (name.isEmpty()) name = target.getDisplayName().getString();

                        lastInitSystemPrompt = sysPrompt;
                        lastInitTargetName = name;
                        lastInitSystemPromptTick = currentTick;

                        String colorCode = PromptBuilder.getSentimentColorCode(mc.player, target);
                        
                        if (debugInit) {
                            System.out.println("====== AI CHAT INIT DEBUG ======");
                            System.out.println("ROLL: " + roll);
                            System.out.println("PROMPT:\n" + sysPrompt);
                            System.out.println("================================");
                            
                            mc.player.sendSystemMessage(Component.literal("§e[Init Debug] §fSending Initiation Prompt for " + name + " (Check game console for cleaner formatting)"));
                            mc.player.sendSystemMessage(Component.literal("§7" + sysPrompt));
                        }

                        GeminiClient.initiateConversation(apiKey, sysPrompt, name, colorCode, currentTick);
                    }
                }
            }
        }
    }
}