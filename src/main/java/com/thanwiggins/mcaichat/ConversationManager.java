package com.thanwiggins.mcaichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

    // How many "model" replies have been given in the current conversation. Used alongside
    // isNpcInitiated so the nameplate can tell an unprompted greeting (the 1st reply) apart from
    // an actual response to the player (the 2nd reply onward) and swap "[ ! ]" for "[ * ]".
    public static int modelReplyCount = 0;

    // Per-entity cooldown so the same NPC can't re-roll initiation every tick once it's near the player.
    public static Map<UUID, Long> initiationCooldowns = new HashMap<>();

    // Mirrors of the last NPC-initiated prompt, kept separately from ChatInterceptor's reactive-prompt
    // fields so /aichat debug can show whichever one actually fired most recently.
    public static String lastInitSystemPrompt = "No initiation sent yet.";
    public static String lastInitTargetName = "";
    public static long lastInitSystemPromptTick = -1;

    // The NPC's shared memory dossier, fetched from the server the moment a conversation claim
    // is granted (piggybacked on ConversationClaimResponsePacket) - see PromptBuilder.
    // memoryLastConvoTick <= 0 means "no prior conversation with anyone", mirroring the old
    // ClientMemoryManager's "no entry yet" case.
    public static String currentMemorySummary = "";
    public static long currentMemoryLastConvoTick = 0;

    // A conversation claim in flight - set by requestClaim, consumed by onClaimResponse once the
    // server replies. Only one claim is ever pending at a time (both entry points - a player's
    // own chat message and an NPC's own initiation roll - go through requestClaim serially).
    private static Entity pendingEntity = null;
    private static Runnable pendingOnGranted = null;
    private static Runnable pendingOnDenied = null;

    // Asks the server for exclusive ownership of this NPC's conversation before any dialogue is
    // generated - see ConversationClaimRequestPacket/ConversationClaimResponsePacket. onGranted
    // should call commitConversation and then proceed (build a prompt, call Gemini); onDenied
    // handles the rejection case (a message for a player-initiated attempt, silence for an
    // NPC-initiated one).
    public static void requestClaim(Entity target, Runnable onGranted, Runnable onDenied) {
        pendingEntity = target;
        pendingOnGranted = onGranted;
        pendingOnDenied = onDenied;
        NetworkHandler.INSTANCE.sendToServer(new ConversationClaimRequestPacket(target.getId()));
    }

    public static void onClaimResponse(int entityId, boolean granted, String memorySummary, long memoryLastConvoTick) {
        if (pendingEntity == null || pendingEntity.getId() != entityId) return;

        Runnable onGranted = pendingOnGranted;
        Runnable onDenied = pendingOnDenied;
        pendingEntity = null;
        pendingOnGranted = null;
        pendingOnDenied = null;

        if (granted) {
            currentMemorySummary = memorySummary;
            currentMemoryLastConvoTick = memoryLastConvoTick;
            if (onGranted != null) onGranted.run();
        } else if (onDenied != null) {
            onDenied.run();
        }
    }

    // Establishes local conversation state once the server has actually granted a claim for
    // this NPC - call only from a requestClaim onGranted callback.
    public static void commitConversation(Entity target, long currentTick, boolean npcInitiated) {
        activeEntity = target;
        conversationHistory = new JsonArray();
        lastMessageTick = currentTick;
        isNpcInitiated = npcInitiated;
        modelReplyCount = 0;
    }

    public static void endConversation(long currentTick) {
        if (activeEntity != null && conversationHistory.size() > 0) {
            String apiKey = Config.API_KEY.get();
            if (apiKey != null && !apiKey.isEmpty()) {
                String playerName = PromptBuilder.getPlayerDisplayName(Minecraft.getInstance().player);
                // Captured now, synchronously - summarizeConversation's actual work runs on a
                // background thread pool, which could otherwise race the currentMemorySummary
                // reset just below.
                GeminiClient.summarizeConversation(apiKey, activeEntity, conversationHistory.deepCopy(), currentTick, playerName, currentMemorySummary);
            }

            NetworkHandler.INSTANCE.sendToServer(new ConversationStatePacket(activeEntity.getId()));
        }
        activeEntity = null;
        conversationHistory = new JsonArray();
        currentMemorySummary = "";
        currentMemoryLastConvoTick = 0;
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
        if (role.equals("model")) modelReplyCount++;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long currentTick = mc.level.getGameTime();

        if (activeEntity != null) {
            boolean timeout = (currentTick - lastMessageTick) > 1200; // 60 seconds of silence ends the conversation
            boolean tooFar = mc.player.distanceToSqr(activeEntity) > 256; // 16 blocks
            boolean deadOrRemoved = !activeEntity.isAlive() || activeEntity.isRemoved();
            boolean fellAsleep = activeEntity instanceof LivingEntity le && le.isSleeping();

            if (timeout || tooFar || deadOrRemoved || fellAsleep) {
                endConversation(currentTick);
            }
        }

        // Only look for a new NPC to strike up a conversation once every 10 seconds, and only when
        // the player isn't already in one and isn't invisible themselves - an NPC shouldn't call
        // out to someone it can't perceive.
        if (activeEntity == null && !mc.player.isInvisible() && (currentTick - lastInitiationTick) > 200) {
            AABB box = mc.player.getBoundingBox().inflate(8.0D);

            List<Entity> nearby = mc.level.getEntities(mc.player, box, e ->
                EffectiveConfig.isWhitelisted(e) && !EffectiveConfig.isBlacklisted(e) &&
                !e.isInvisible() && // an invisible NPC shouldn't give away its position by speaking up
                !(e instanceof LivingEntity le && le.isSleeping()) && // asleep in bed - can't strike up a conversation
                (!initiationCooldowns.containsKey(e.getUUID()) || (currentTick - initiationCooldowns.get(e.getUUID())) > 2400) && // 2 minute per-NPC cooldown
                mc.player.hasLineOfSight(e)
            );

            if (!nearby.isEmpty()) {
                Entity target = nearby.get(0);

                // Put the NPC on cooldown regardless of the roll below, so a miss doesn't let it
                // re-roll again next cycle.
                initiationCooldowns.put(target.getUUID(), currentTick);

                // NPCs that already have a memory of a past conversation (i.e. this isn't anyone's
                // first interaction with them) are far more eager to speak up than total strangers.
                // Among strangers, ones already friendly toward the player are likelier to strike
                // up a chat than hostile ones. mcaichat_memory_last_convo_tick rides along on
                // whatever SyncNPCPacket has already merged into this entity's local shadow copy -
                // no separate lookup needed.
                boolean knowsPlayer = target.getPersistentData().getLong("mcaichat_memory_last_convo_tick") > 0;
                double chance;
                if (knowsPlayer) {
                    chance = 0.5;
                } else {
                    chance = PromptBuilder.isHostileToPlayer(mc.player, target) ? 0.1 : 0.25;
                }
                double roll = Math.random();

                if (debugInit) {
                    mc.player.sendSystemMessage(Component.literal(
                        "§e[Init Debug] §fRolled " + String.format("%.2f", roll) + " (Needs < " + String.format("%.2f", chance) + ") for " + target.getDisplayName().getString()
                    ));
                }

                if (roll < chance) {
                    lastInitiationTick = currentTick;

                    // Claim the NPC's conversation before generating anything - if another player
                    // already holds it, this greeting attempt is dropped silently (the cooldown
                    // above already applies regardless of outcome).
                    requestClaim(target, () -> {
                        commitConversation(target, currentTick, true); // true: the NPC initiated

                        String apiKey = Config.API_KEY.get();
                        if (apiKey == null || apiKey.isEmpty()) return;

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

                        // Sound + note particles cue the player right as the NPC's greeting actually
                        // arrives, rather than the moment the request goes out.
                        GeminiClient.initiateConversation(apiKey, sysPrompt, name, colorCode, currentTick, () -> {
                            Minecraft client = Minecraft.getInstance();
                            if (client.player == null || client.level == null || !target.isAlive()) return;

                            client.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
                            for (int i = 0; i < 4; i++) {
                                client.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE,
                                    target.getX() + (Math.random() - 0.5) * 0.5,
                                    target.getY() + target.getBbHeight() + 0.5D,
                                    target.getZ() + (Math.random() - 0.5) * 0.5,
                                    0.0D, 0.0D, 0.0D);
                            }
                        });
                    }, null); // null onDenied: a lost greeting race stays silent
                }
            }
        }
    }
}