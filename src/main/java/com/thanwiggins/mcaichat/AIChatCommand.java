package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class AIChatCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("aichat")
            
                // --- DEBUG COMMAND ---
                .then(Commands.literal("debug")
                    .executes(context -> {
                        // Show whichever prompt actually went out most recently - a reactive
                        // player-triggered chat, or an NPC-initiated conversation.
                        boolean initWasLast = ConversationManager.lastInitSystemPromptTick > ChatInterceptor.lastSystemPromptTick;

                        String source = initWasLast ? "NPC-Initiated Conversation" : "Reactive (Player Message)";
                        String sysPrompt = initWasLast ? ConversationManager.lastInitSystemPrompt : ChatInterceptor.lastSystemPrompt;
                        String userMsg = initWasLast ? "(none - " + ConversationManager.lastInitTargetName + " spoke first)" : ChatInterceptor.lastUserMessage;

                        // Print to the background game console for easy copy/pasting
                        System.out.println("====== AI CHAT DEBUG ======");
                        System.out.println("SOURCE: " + source);
                        System.out.println("SYSTEM PROMPT:\n" + sysPrompt);
                        System.out.println("USER MESSAGE:\n" + userMsg);
                        System.out.println("===========================");

                        // Print to the in-game chat box
                        context.getSource().sendSystemMessage(Component.literal("§e--- LAST PROMPT SENT (" + source + ") ---"));
                        context.getSource().sendSystemMessage(Component.literal("§bSystem: §f" + sysPrompt));
                        context.getSource().sendSystemMessage(Component.literal("§bUser: §f" + userMsg));
                        context.getSource().sendSystemMessage(Component.literal("§7(Check your game console/logs to see the cleanly formatted version)"));

                        return 1;
                    })
                )

                // --- LORE DEBUG COMMAND ---
                .then(Commands.literal("lore")
                    .executes(context -> {
                        ClientLoreManager.debugLore = !ClientLoreManager.debugLore;
                        context.getSource().sendSystemMessage(Component.literal(
                            "§eAI Chat Lore Debugging is now: " + (ClientLoreManager.debugLore ? "§aON" : "§cOFF")
                        ));
                        return 1;
                    })
                )
                
                // --- INIT DEBUG COMMAND ---
                .then(Commands.literal("init")
                    .executes(context -> {
                        ConversationManager.debugInit = !ConversationManager.debugInit;
                        context.getSource().sendSystemMessage(Component.literal(
                            "§eAI Chat Init Debugging is now: " + (ConversationManager.debugInit ? "§aON" : "§cOFF")
                        ));
                        return 1;
                    })
                )

                // --- FIND ROOST DEBUG COMMAND ---
                .then(Commands.literal("findroost")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null || mc.level == null) return 0;

                        if (!DragonRoostFinder.isIceAndFireLoaded()) {
                            context.getSource().sendSystemMessage(Component.literal("§cIce and Fire isn't installed - nothing to find."));
                            return 0;
                        }

                        MinecraftServer server = mc.getSingleplayerServer();
                        if (server == null) {
                            context.getSource().sendSystemMessage(Component.literal("§cThis command only works while hosting the world (singleplayer or LAN)."));
                            return 0;
                        }

                        ServerLevel serverLevel = server.getLevel(mc.level.dimension());
                        if (serverLevel == null) return 0;

                        BlockPos playerPos = mc.player.blockPosition();
                        var roosts = DragonRoostFinder.findAll(serverLevel, playerPos, 512.0D);

                        if (roosts.isEmpty()) {
                            context.getSource().sendSystemMessage(Component.literal("§eNo dragon roosts found within 512 blocks."));
                            return 1;
                        }

                        context.getSource().sendSystemMessage(Component.literal("§e--- Found " + roosts.size() + " Roost(s) ---"));
                        for (DragonRoostFinder.Roost roost : roosts) {
                            double distance = Math.sqrt(playerPos.distSqr(roost.pos()));
                            context.getSource().sendSystemMessage(Component.literal(
                                "§a" + roost.type() + " §7at " + roost.pos().toShortString() +
                                " §7(" + Math.round(distance) + " blocks away, biome: " + roost.biome() + ")"
                            ));
                        }
                        return 1;
                    })
                )
        );
    }
}