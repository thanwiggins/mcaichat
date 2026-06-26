package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class IdentityHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
            // If the entity doesn't have an AI identity yet, give it one
            if (!data.contains("mcaichat_personality")) {
                
                // Seed the Random with the UUID. This guarantees the Client and Server 
                // independently pick the exact same traits!
                Random random = new Random(entity.getUUID().getLeastSignificantBits());
                
                String name = NPCData.getRandomName(random);
                String personality = NPCData.getRandomPersonality(random);
                
                // Save to NBT
                data.putString("mcaichat_personality", personality);
                data.putString("mcaichat_name", name);
                
                // Set the vanilla Custom Name so it physically tracks above their head
                if (!event.getLevel().isClientSide()) {
                    entity.setCustomName(Component.literal(name));
                    entity.setCustomNameVisible(true);
                }
            }
        }
    }
}