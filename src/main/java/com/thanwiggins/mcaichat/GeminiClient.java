package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class GeminiClient {
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void sendMessage(String apiKey, String systemPrompt, String userMessage, String entityName) {
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

                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", userMessage);
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

                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[" + entityName + "]: §f" + reply.trim()));
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

    // --- NEW METHOD: Background Lore Generation ---
    // --- NEW METHOD: Background Lore Generation ---
    public static void generateStructureLore(String apiKey, String structureId, String structureType, String structureName, String category, String biome) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

                // Added the Biome injection to the prompt!
                String prompt = "You are a worldbuilding assistant for Minecraft. Generate a 2-3 sentence historical background or lore for a " 
                        + category + " structure of type '" + structureType + "' named '" + structureName + "', located in a " + biome + " biome. "
                        + "Make it fit naturally into a fantasy Minecraft world. Do not use markdown or formatting, just plain text.";

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

                    // Instantly save it to the map when it arrives!
                    ClientLoreManager.addLore(structureId, structureName, generatedLore, category);
                    System.out.println("[MC-AI Chat] Generated new lore for " + structureName + "!");
                } else {
                    ClientLoreManager.addLore(structureId, structureName, "A mysterious place with an unknown history.", category);
                }
            } catch (Exception e) {
                System.err.println("[MC-AI Chat] Lore generation failed: " + e.getMessage());
                ClientLoreManager.addLore(structureId, structureName, "A mysterious place with an unknown history.", category);
            }
        });
    }
}