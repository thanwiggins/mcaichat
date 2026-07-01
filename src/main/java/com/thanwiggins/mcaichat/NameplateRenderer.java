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
        
        if (Config.isWhitelisted(entity)) {
            CompoundTag data = entity.getPersistentData();
            
            // If they are an AI character, color their nameplate
            if (data.contains("mcaichat_name")) {
                String name = data.getString("mcaichat_name");
                                
                Player player = Minecraft.getInstance().player;
                String colorCode = "§6"; // Default to gold if player is null
                
                if (player != null) {
                    colorCode = PromptBuilder.getSentimentColorCode(player, entity);
                }

                // NEW: Permanently override the color code if they know a secret
                if (data.contains("mcaichat_secret_type")) {
                    colorCode = "§b"; // Aqua/Blue color
                }
                
                event.setContent(Component.literal(colorCode + name));
                
                // Seamlessly register them to their social circle if they have a home!
                if (data.contains("mcaichat_home_id")) {
                    String homeId = data.getString("mcaichat_home_id");
                    if (!homeId.isEmpty() && !homeId.equals("none")) {
                        String type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).getPath();
                        String personality = data.getString("mcaichat_personality");
                        String cap = PromptBuilder.getShortCapabilityString(entity);
                        ClientSocialManager.addCitizen(homeId, entity.getUUID(), name, type, personality, cap);
                    }
                }
            }
        }
    }
}