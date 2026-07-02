package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

// Pre-fills the world name field on the "Create World" screen with a random realm name,
// purely cosmetic flavor to match the NPCs' generated home lore.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class WorldNameRandomizer {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CreateWorldScreen screen) {

            String defaultName = Component.translatable("selectWorld.newWorld").getString();

            // The world name box is always the first EditBox on this screen
            for (Object child : screen.children()) {
                if (child instanceof EditBox editBox) {

                    if (editBox.getValue().equals(defaultName) || editBox.getValue().isEmpty()) {
                        editBox.setValue(NPCData.getRandomRealm(new Random()));
                    }

                    break;
                }
            }
        }
    }
}