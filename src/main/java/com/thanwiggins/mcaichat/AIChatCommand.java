package com.thanwiggins.mcaichat;

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
                .then(Commands.literal("whitelist")
                    
                    // --- ADD COMMAND ---
                    .then(Commands.literal("add")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                            // Suggests all vanilla/modded entities
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

                    // --- REMOVE COMMAND ---
                    .then(Commands.literal("remove")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                            // Custom suggestion: Only suggest entities currently on the whitelist
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

                                // Parse the list, remove the target, and stitch it back together
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
        );
    }
}