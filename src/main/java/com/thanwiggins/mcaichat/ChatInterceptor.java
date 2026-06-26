package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class ChatInterceptor {

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();

        // Check if the player typed our command
        if (message.startsWith("/chat ")) {
            event.setCanceled(true); // Stop the message from going to the server
            String prompt = message.substring(6); // Remove "/chat "

            // Echo the prompt locally
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§7[You] -> Gemini: §f" + prompt));
            
            // Add to chat history so the player can use the UP arrow key
            Minecraft.getInstance().gui.getChat().addRecentChat(message);

            // Validate API Key
            String apiKey = Config.API_KEY.get();
            if (apiKey == null || apiKey.isEmpty()) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Error] Gemini API Key is missing! Click 'Mods' in the menu to configure it."));
                return;
            }
            
            // Fire the request
            GeminiClient.sendMessage(apiKey, prompt);
        }
    }
}