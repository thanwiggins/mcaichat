package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent; // <-- Updated import
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class NameplateRenderer {

    @SubscribeEvent
    public static void onRenderNameplate(RenderNameTagEvent event) { // <-- Updated event class
        Entity entity = event.getEntity();
        
        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
            // If they are an AI character, color their nameplate Gold (§6)
            if (data.contains("mcaichat_name")) {
                String name = data.getString("mcaichat_name");
                event.setContent(Component.literal("§6" + name));
            }
        }
    }
}