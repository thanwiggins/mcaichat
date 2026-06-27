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
        if (event.getScreen() instanceof CreateWorldScreen screen) {
            
            String defaultName = Component.translatable("selectWorld.newWorld").getString();

            for (Object child : screen.children()) {
                if (child instanceof EditBox editBox) {
                    
                    if (editBox.getValue().equals(defaultName) || editBox.getValue().isEmpty()) {
                        String randomName = NPCData.getRandomRealm(new Random());
                        // Stripped the "Realm of " prefix
                        editBox.setValue(randomName); 
                    }
                    
                    break;
                }
            }
        }
    }
}