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

    public static void sendMessage(String apiKey, String prompt) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

                // Build the JSON Payload
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", prompt);
                JsonArray parts = new JsonArray();
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.add("parts", parts);
                JsonArray contents = new JsonArray();
                contents.add(content);
                JsonObject body = new JsonObject();
                body.add("contents", contents);

                // Send the HTTP POST Request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // Parse the response back into the Minecraft Main Thread
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

                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[Gemini]: §f" + reply.trim()));
                    } else {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Gemini Error]: HTTP " + response.statusCode()));
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
}