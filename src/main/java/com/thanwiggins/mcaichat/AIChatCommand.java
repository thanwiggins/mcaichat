package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class AIChatCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("aichat")
            
                // --- DEBUG COMMAND ---
                .then(Commands.literal("debug")
                    .executes(context -> {
                        String sysPrompt = ChatInterceptor.lastSystemPrompt;
                        String userMsg = ChatInterceptor.lastUserMessage;
                        
                        // Print to the background game console for easy copy/pasting
                        System.out.println("====== AI CHAT DEBUG ======");
                        System.out.println("SYSTEM PROMPT:\n" + sysPrompt);
                        System.out.println("USER MESSAGE:\n" + userMsg);
                        System.out.println("===========================");

                        // Print to the in-game chat box
                        context.getSource().sendSystemMessage(Component.literal("§e--- LAST PROMPT SENT ---"));
                        context.getSource().sendSystemMessage(Component.literal("§bSystem: §f" + sysPrompt));
                        context.getSource().sendSystemMessage(Component.literal("§bUser: §f" + userMsg));
                        context.getSource().sendSystemMessage(Component.literal("§7(Check your game console/logs to see the cleanly formatted version)"));
                        
                        return 1;
                    })
                )
                
                // --- WHITELIST COMMANDS ---
                .then(Commands.literal("whitelist")
                    .then(Commands.literal("add")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                            .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                            .executes(context -> {
                                ResourceLocation entityId = context.getArgument("entity", ResourceLocation.class);
                                String currentWhitelist = Config.WHITELIST_ENTITIES.get();
                                String newEntityStr = entityId.toString();

                                if (!currentWhitelist.contains(newEntityStr)) {
                                    String updatedList = currentWhitelist.isEmpty() ? newEntityStr : currentWhitelist + "," + newEntityStr;
                                    Config.WHITELIST_ENTITIES.set(updatedList);
                                    context.getSource().sendSystemMessage(Component.literal("§aAdded " + newEntityStr + " to the AI Chat whitelist!"));
                                } else {
                                    context.getSource().sendSystemMessage(Component.literal("§c" + newEntityStr + " is already whitelisted."));
                                }
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                            .suggests((context, builder) -> {
                                String currentWhitelist = Config.WHITELIST_ENTITIES.get();
                                if (currentWhitelist == null || currentWhitelist.isEmpty()) {
                                    return builder.buildFuture();
                                }
                                return SharedSuggestionProvider.suggest(Arrays.stream(currentWhitelist.split(",")).map(String::trim), builder);
                            })
                            .executes(context -> {
                                ResourceLocation entityId = context.getArgument("entity", ResourceLocation.class);
                                String currentWhitelist = Config.WHITELIST_ENTITIES.get();
                                String removeEntityStr = entityId.toString();

                                List<String> entities = Arrays.stream(currentWhitelist.split(","))
                                        .map(String::trim)
                                        .collect(Collectors.toList());

                                if (entities.contains(removeEntityStr)) {
                                    entities.remove(removeEntityStr);
                                    String updatedList = String.join(",", entities);
                                    Config.WHITELIST_ENTITIES.set(updatedList);
                                    context.getSource().sendSystemMessage(Component.literal("§eRemoved " + removeEntityStr + " from the AI Chat whitelist."));
                                } else {
                                    context.getSource().sendSystemMessage(Component.literal("§c" + removeEntityStr + " is not in the whitelist."));
                                }
                                return 1;
                            })
                        )
                    )
                )

                // --- BLACKLIST COMMANDS ---
                .then(Commands.literal("blacklist")
                    .then(Commands.literal("add")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                            .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                            .executes(context -> {
                                ResourceLocation entityId = context.getArgument("entity", ResourceLocation.class);
                                String currentBlacklist = Config.BLACKLIST_ENTITIES.get();
                                String newEntityStr = entityId.toString();

                                if (!currentBlacklist.contains(newEntityStr)) {
                                    String updatedList = currentBlacklist.isEmpty() ? newEntityStr : currentBlacklist + "," + newEntityStr;
                                    Config.BLACKLIST_ENTITIES.set(updatedList);
                                    context.getSource().sendSystemMessage(Component.literal("§aAdded " + newEntityStr + " to the AI Chat blacklist!"));
                                } else {
                                    context.getSource().sendSystemMessage(Component.literal("§c" + newEntityStr + " is already blacklisted."));
                                }
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                            .suggests((context, builder) -> {
                                String currentBlacklist = Config.BLACKLIST_ENTITIES.get();
                                if (currentBlacklist == null || currentBlacklist.isEmpty()) {
                                    return builder.buildFuture();
                                }
                                return SharedSuggestionProvider.suggest(Arrays.stream(currentBlacklist.split(",")).map(String::trim), builder);
                            })
                            .executes(context -> {
                                ResourceLocation entityId = context.getArgument("entity", ResourceLocation.class);
                                String currentBlacklist = Config.BLACKLIST_ENTITIES.get();
                                String removeEntityStr = entityId.toString();

                                List<String> entities = Arrays.stream(currentBlacklist.split(","))
                                        .map(String::trim)
                                        .collect(Collectors.toList());

                                if (entities.contains(removeEntityStr)) {
                                    entities.remove(removeEntityStr);
                                    String updatedList = String.join(",", entities);
                                    Config.BLACKLIST_ENTITIES.set(updatedList);
                                    context.getSource().sendSystemMessage(Component.literal("§eRemoved " + removeEntityStr + " from the AI Chat blacklist."));
                                } else {
                                    context.getSource().sendSystemMessage(Component.literal("§c" + removeEntityStr + " is not in the blacklist."));
                                }
                                return 1;
                            })
                        )
                    )
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

                // --- CHEST SCANNER DEBUG COMMAND ---
                .then(Commands.literal("scanchests")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null || mc.level == null) return 0;
                        
                        net.minecraft.core.BlockPos playerPos = mc.player.blockPosition();
                        int radius = 16;
                        int found = 0;
                        
                        mc.player.sendSystemMessage(Component.literal("§e--- Scanning 16-Block Radius for Chests ---"));
                        
                        for (int x = -radius; x <= radius; x++) {
                            for (int y = -radius; y <= radius; y++) {
                                for (int z = -radius; z <= radius; z++) {
                                    net.minecraft.core.BlockPos pos = playerPos.offset(x, y, z);
                                    net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(pos);
                                    
                                    if (be != null) {
                                        net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata();
                                        
                                        // If it's a container, let's look inside
                                        if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) {
                                            if (tag.contains("LootTable", 8)) {
                                                String loot = tag.getString("LootTable");
                                                mc.player.sendSystemMessage(Component.literal("§a[VALID LOOT TABLE] §f" + loot + " §7at " + pos.toShortString()));
                                                found++;
                                            } else {
                                                mc.player.sendSystemMessage(Component.literal("§c[NO LOOT TABLE] §7Container found at " + pos.toShortString() + " but it has no LootTable tag."));
                                                mc.player.sendSystemMessage(Component.literal("§8Tag Data: " + tag.toString()));
                                            }
                                        } else {
                                            // Check if it's a custom Ice and Fire block entity
                                            String entityType = net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType()).toString();
                                            if (entityType.contains("iceandfire")) {
                                                mc.player.sendSystemMessage(Component.literal("§b[CUSTOM IAF ENTITY] §f" + entityType + " §7at " + pos.toShortString()));
                                                mc.player.sendSystemMessage(Component.literal("§8Tag Data: " + tag.toString()));
                                                found++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        mc.player.sendSystemMessage(Component.literal("§e--- Scan Complete. Found: " + found + " items of interest ---"));
                        return 1;
                    })
                )
        );
    }
}