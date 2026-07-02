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
    public static void initiateConversation(String apiKey, String systemPrompt, String entityName, String colorCode, long currentTick) {
        ConversationManager.addMessage("user", "Please initiate a conversation with me naturally. Say hello!", currentTick);
        sendMessage(apiKey, systemPrompt, ConversationManager.conversationHistory, entityName, colorCode);
    }

    // Called once a conversation ends: asks Gemini to fold the just-finished conversation into the
    // NPC's existing memory (rather than replacing it), so old facts survive across many conversations.
    public static void summarizeConversation(String apiKey, Entity entity, JsonArray historyArray, long currentTick) {
        CompletableFuture.runAsync(() -> {
            try {
                ClientMemoryManager.EntityMemory oldMem = ClientMemoryManager.getMemory(entity.getUUID());
                String memoryContext = oldMem != null ? oldMem.summary : "No prior memories.";

                StringBuilder rawHistory = new StringBuilder();
                for (int i = 0; i < historyArray.size(); i++) {
                    JsonObject msg = historyArray.get(i).getAsJsonObject();
                    String role = msg.get("role").getAsString();
                    String text = msg.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
                    rawHistory.append(role.equals("user") ? "Player: " : "AI: ").append(text).append("\n");
                }

                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

                // Explicitly instructed to merge rather than replace, otherwise the model tends to
                // drop older facts that weren't mentioned again in the most recent conversation.
                String prompt = "You are updating your memory dossier on your interactions the player. Below is your 'Previous Memory' and the 'Recent Conversation'. "
                              + "Write a new, comprehensive memory summary (3-4 sentences) that retains all important historical details (like the player's name, past events, past attitudes, etc.) "
                              + "AND integrates any new things learned from the recent conversation. DO NOT drop important past facts just because they weren't mentioned in the recent conversation.\n\n"
                              + "Previous Memory: " + memoryContext + "\n\n"
                              + "Recent Conversation:\n" + rawHistory.toString();

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
                    String summary = jsonResponse.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString().trim();

                    // Save the memory tagged with the tick the conversation ended
                    ClientMemoryManager.updateMemory(entity.getUUID(), summary, currentTick);
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

                String prompt = "You are a worldbuilding assistant for Minecraft. Generate a 2-3 sentence historical background or lore for a " 
                        + category + " structure of type '" + structureType + "' named '" + structureName + "', located in a " + biome + " biome. "
                        + "Make it fit naturally into a fantasy Minecraft world. Do not use markdown or formatting, just plain text.";

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