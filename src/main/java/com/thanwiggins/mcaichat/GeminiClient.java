package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

// All outbound calls to the Gemini API live here: live chat turns, end-of-conversation memory
// summarization, and one-off structure lore generation. Every call is fire-and-forget async,
// hopping back onto the client thread (via Minecraft.getInstance().execute) to touch game state.
public class GeminiClient {
    private static final HttpClient client = HttpClient.newHttpClient();

    // Sends one conversation turn (the full rolling history, not just the latest message) and
    // prints the reply to chat. Used for both reactive player messages and NPC-initiated greetings.
    public static void sendMessage(String apiKey, String systemPrompt, JsonArray history, String entityName, String colorCode) {
        sendMessage(apiKey, systemPrompt, history, entityName, colorCode, null);
    }

    // Same as above, but runs onReplyReceived right as a successful reply is about to be shown -
    // used to time the NPC-initiated "ding" cue to when the greeting actually arrives, rather
    // than when the request was fired (which could fail, or take a couple seconds).
    public static void sendMessage(String apiKey, String systemPrompt, JsonArray history, String entityName, String colorCode, Runnable onReplyReceived) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

                JsonObject body = new JsonObject();
                JsonObject systemInstruction = new JsonObject();
                JsonObject sysParts = new JsonObject();
                sysParts.addProperty("text", systemPrompt);
                JsonArray sysPartsArray = new JsonArray();
                sysPartsArray.add(sysParts);
                systemInstruction.add("parts", sysPartsArray);
                body.add("system_instruction", systemInstruction);

                // Pass the entire history array to the contents
                body.add("contents", history);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player == null) return;

                    if (response.statusCode() == 200) {
                        if (onReplyReceived != null) onReplyReceived.run();

                        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                        String reply = jsonResponse.getAsJsonArray("candidates")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString();

                        // Add the model's reply to the history using the current game tick
                        ConversationManager.addMessage("model", reply, Minecraft.getInstance().level.getGameTime());
                        // Apply the requested color code to the whole message!
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal(colorCode + "[" + entityName + "]: §f" + reply.trim()));
                    } else {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Gemini Error]: HTTP " + response.statusCode() + " - " + response.body()));
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Gemini Error]: " + e.getMessage()));
                    }
                });
            }
        });
    }

    // Seeds the conversation with a synthetic "say hello" instruction so the NPC has something
    // to respond to, then sends it through the normal sendMessage path.
    public static void initiateConversation(String apiKey, String systemPrompt, String entityName, String colorCode, long currentTick, Runnable onReplyReceived) {
        ConversationManager.addMessage("user", "Please initiate a conversation with me naturally. Say hello!", currentTick);
        sendMessage(apiKey, systemPrompt, ConversationManager.conversationHistory, entityName, colorCode, onReplyReceived);
    }

    // Called once a conversation ends: asks Gemini to list only the NEW memory items worth keeping
    // from the just-finished conversation, which get appended to the NPC's existing dossier below
    // (rather than asking the model to rewrite the whole thing), so old facts can't be dropped.
    public static void summarizeConversation(String apiKey, Entity entity, JsonArray historyArray, long currentTick) {
        CompletableFuture.runAsync(() -> {
            try {
                ClientMemoryManager.EntityMemory oldMem = ClientMemoryManager.getMemory(entity.getUUID());

                // Conversations this short are unlikely to contain anything worth remembering -
                // skip the API call, but still record that a conversation happened just now.
                if (historyArray.size() <= 4) {
                    ClientMemoryManager.updateMemory(entity.getUUID(), oldMem != null ? oldMem.summary : "", currentTick);
                    return;
                }

                String memoryContext = oldMem != null ? oldMem.summary : "No prior memories.";

                StringBuilder rawHistory = new StringBuilder();
                for (int i = 0; i < historyArray.size(); i++) {
                    JsonObject msg = historyArray.get(i).getAsJsonObject();
                    String role = msg.get("role").getAsString();
                    String text = msg.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
                    rawHistory.append(role.equals("user") ? "Player: " : "AI: ").append(text).append("\n");
                }

                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

                // Asks for only the new/changed items (as a bulleted list) instead of a full rewrite,
                // so restating unchanged facts/feelings can't crowd out or drift from older memories.
                String prompt = "You are updating the memory dossier of a Minecraft RPG Fantasy Adventure character after an interaction with the player. Below is your existing memory and a log of the most recent conversation. Follow the instructions followed by TASK: at the end of this message.\n\n"
                              + "Previous Memory:\n" + memoryContext + "\n\n"
                              + "Recent Conversation:\n" + rawHistory.toString() + "\n\n"
                              + "Tips for Identifying Memories:\n"
                              + "- Include tangible facts and anything the character learns during the conversation, but do NOT include notes about conversational asides, such as references to creatures.\n"
                              + "- Include events that have shaped the character's connection to the player and how they view the player, but do NOT include general statements about their rapport with the player that lack storytelling value or present no change from past feelings.\n"
                              + "- Do NOT include memory items if they repeat or reaffirm something or state that the character remains feeling a certain way about the player.\n\n"
                              + "TASK: Review your previous memory and the recent conversation the character just had with the player. List any important new memory items that should be added to the character's memory dossier. If the recent conversation contained no useful information or new memories, return ''. Use a second person perspective when referring to the character. Do not title the list. List all items with a * prefix.";

                JsonObject body = new JsonObject();
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", prompt);
                JsonArray parts = new JsonArray();
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.addProperty("role", "user");
                content.add("parts", parts);
                JsonArray contents = new JsonArray();
                contents.add(content);
                body.add("contents", contents);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    String newItems = jsonResponse.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString().trim();

                    // The model returns '' (per the prompt's TASK instructions) when nothing in the
                    // conversation was worth remembering - leave the dossier untouched in that case.
                    boolean hasNewItems = !newItems.isEmpty() && !newItems.equals("''");

                    if (hasNewItems) {
                        String updatedMemory = oldMem != null ? oldMem.summary + "\n" + newItems : newItems;
                        ClientMemoryManager.updateMemory(entity.getUUID(), updatedMemory, currentTick);
                    } else if (oldMem != null) {
                        // Still bump the tick so "time since last conversation" reflects that this
                        // chat happened, even though nothing new was added to the dossier.
                        ClientMemoryManager.updateMemory(entity.getUUID(), oldMem.summary, currentTick);
                    }
                }
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Memory summarization failed: " + e.getMessage());
            }
        });
    }

    // One-off call made the first time a player discovers a "civilization" structure - writes a
    // short backstory that gets cached forever in ClientLoreManager (never re-rolled).
    public static void generateStructureLore(String apiKey, String structureId, String structureType, String structureName, String category, String biome) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

                String prompt = "You are a worldbuilding assistant for a Minecraft RPG fantasy adventure. Generate a simple paragraph of historical background or lore for a "
                        + category + " structure of type '" + structureType + "' named '" + structureName + "', located in a " + biome + " biome. "
                        + "Provide a few interesting facts about the location, but do not refer to or name surrounding land features, cities, or citizens since you have no context for those. "
                        + "Do not use markdown or special formatting in your response.";

                if (ClientLoreManager.debugLore) {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e[Lore Debug] §fSending prompt: " + prompt));
                        }
                    });
                }

                JsonObject body = new JsonObject();
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", prompt);
                JsonArray parts = new JsonArray();
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.addProperty("role", "user");
                content.add("parts", parts);
                JsonArray contents = new JsonArray();
                contents.add(content);
                body.add("contents", contents);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    String generatedLore = jsonResponse.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString().trim();

                    ClientLoreManager.updateLoreBackground(structureId, generatedLore);
                    ClientLoreManager.reportToServer(structureId);
                    System.out.println("[MC-AI Chat] Generated new lore for " + structureName + "!");

                    if (ClientLoreManager.debugLore) {
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[Lore Debug] §fReceived: " + generatedLore));
                            }
                        });
                    }

                } else {
                    ClientLoreManager.updateLoreBackground(structureId, "A mysterious place with an unknown history.");
                }
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Lore generation failed: " + e.getMessage());
                ClientLoreManager.updateLoreBackground(structureId, "A mysterious place with an unknown history.");
            }
        });
    }
}