package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class WorldNameRandomizer {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Check if the screen that just opened is the World Creation screen
        if (event.getScreen() instanceof CreateWorldScreen screen) {
            
            // Get the default localized text (e.g., "New World" in English)
            String defaultName = Component.translatable("selectWorld.newWorld").getString();

            // Search through the screen's widgets to find the text box
            for (Object child : screen.children()) {
                if (child instanceof EditBox editBox) {
                    
                    // Only overwrite it if it's the exact default text.
                    // This prevents us from overwriting a custom name if the user 
                    // resizes their game window (which re-initializes the screen).
                    if (editBox.getValue().equals(defaultName) || editBox.getValue().isEmpty()) {
                        String randomName = NPCData.getRandomRealm(new Random());
                        editBox.setValue("Realm of " + randomName); // Example: "Realm of Oakhaven"
                    }
                    
                    break; // The first EditBox is always the Name field, so we can stop searching.
                }
            }
        }
    }
}