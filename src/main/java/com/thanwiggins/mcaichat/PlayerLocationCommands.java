package com.thanwiggins.mcaichat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Server-side commands letting a player found (/base new) and later update (/base edit) a named,
// described location that NPCs perceive the same way they perceive vanilla civ structures - see
// IdentityHandler.generateWorldKnowledge/considerNewLocation. Follows NpcDirectiveCommands' own
// RegisterCommandsEvent pattern, the mod's only other server-command precedent.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class PlayerLocationCommands {
    private static final int NEW_COST = 25;
    private static final int EDIT_COST = 10;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("base")
                .then(Commands.literal("new")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                            .executes(PlayerLocationCommands::baseNew))))
                .then(Commands.literal("edit")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                            .executes(PlayerLocationCommands::baseEdit))))
        );
    }

    // Sent once on login rather than relying only on change-broadcasts, so a player who wasn't
    // online for a create/edit/reveal still starts with an up-to-date ClientLocationManager.
    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendLocationsTo(player);
        }
    }

    private static int baseNew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        String description = StringArgumentType.getString(ctx, "description");
        ServerLevel level = ctx.getSource().getLevel();

        PlayerLocationData data = PlayerLocationData.get(level);
        if (data.exists(name)) {
            player.sendSystemMessage(Component.literal("§cA location named '" + name + "' already exists."));
            return 0;
        }

        if (!spendGold(player, NEW_COST)) {
            player.sendSystemMessage(Component.literal("§cFounding a new location costs " + NEW_COST + " gold ingots."));
            return 0;
        }

        PlayerLocationData.Location location = data.create(name, description, player.getUUID(), player.blockPosition());
        IdentityHandler.considerNewLocation(level, location);
        NetworkHandler.broadcastLocations(level);

        player.sendSystemMessage(Component.literal("§aFounded '" + name + "'."));
        return 1;
    }

    private static int baseEdit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        String description = StringArgumentType.getString(ctx, "description");
        ServerLevel level = ctx.getSource().getLevel();

        PlayerLocationData data = PlayerLocationData.get(level);
        PlayerLocationData.Location location = data.get(name);

        if (location == null) {
            player.sendSystemMessage(Component.literal("§cNo location named '" + name + "' exists."));
            return 0;
        }
        if (!location.creator.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cOnly " + location.name + "'s founder can edit it."));
            return 0;
        }

        if (!spendGold(player, EDIT_COST)) {
            player.sendSystemMessage(Component.literal("§cUpdating a location costs " + EDIT_COST + " gold ingots."));
            return 0;
        }

        data.edit(name, description, player.getUUID());
        NetworkHandler.broadcastLocations(level);

        player.sendSystemMessage(Component.literal("§aUpdated '" + location.name + "'."));
        return 1;
    }

    private static boolean spendGold(Player player, int amount) {
        Inventory inventory = player.getInventory();

        int have = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.GOLD_INGOT)) have += inventory.getItem(i).getCount();
        }
        if (have < amount) return false;

        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.GOLD_INGOT)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return true;
    }
}
