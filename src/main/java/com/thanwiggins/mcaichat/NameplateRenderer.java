package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class NameplateRenderer {

    @SubscribeEvent
    public static void onRenderNameplate(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        
        if (EffectiveConfig.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
            // If they are an AI character, color their nameplate
            if (data.contains("mcaichat_name")) {
                String name = data.getString("mcaichat_name");
                                
                Player player = Minecraft.getInstance().player;
                String colorCode = "§6"; // Default to gold if player is null
                boolean isHostile = false;

                if (player != null) {
                    colorCode = PromptBuilder.getSentimentColorCode(player, entity);
                    isHostile = PromptBuilder.isHostileToPlayer(player, entity);
                }

                // Override the color code if they know a secret - but never on a hostile NPC,
                // otherwise a monster that happens to know a secret would show as the "safe" blue
                // instead of red, hiding the danger from the player at a glance.
                if (data.contains("mcaichat_secret_type") && !isHostile) {
                    colorCode = "§b"; // Aqua/Blue color
                }
                
                // [ ! ] marks an NPC's unprompted greeting; it becomes [ * ] once the NPC has
                // actually replied to the player (its 2nd reply onward), same as a normal chat.
                if (ConversationManager.activeEntity != null && ConversationManager.activeEntity.getUUID().equals(entity.getUUID())) {
                    if (ConversationManager.isNpcInitiated && ConversationManager.modelReplyCount <= 1) {
                        event.setContent(Component.literal("§e[ ! ] " + colorCode + name + " §e[ ! ]"));
                    } else {
                        event.setContent(Component.literal("§e[ * ] " + colorCode + name + " §e[ * ]"));
                    }
                } else {
                    event.setContent(Component.literal(colorCode + name));
                }
            }
        }
    }
}