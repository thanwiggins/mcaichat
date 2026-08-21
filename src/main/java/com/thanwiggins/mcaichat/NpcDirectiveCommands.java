package com.thanwiggins.mcaichat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

// Server-side commands letting a player redirect an NPC's movement (/follow, /stay, /resume) -
// see GotoCommand for /goto, which has to run client-side to match named locations by their real
// nickname. Combat and mobs that are aggressive toward the player by nature never take the
// directive at all - see DirectiveGoal.isActiveDirective(). The command still runs either way,
// since the message itself is said by the player (see speak()) and deserves a real, in-character
// reply regardless of whether the NPC actually complies.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class NpcDirectiveCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("follow")
                .executes(ctx -> follow(ctx, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> follow(ctx, StringArgumentType.getString(ctx, "message"))))
        );

        event.getDispatcher().register(
            Commands.literal("stay")
                .executes(ctx -> stay(ctx, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> stay(ctx, StringArgumentType.getString(ctx, "message"))))
        );

        event.getDispatcher().register(
            Commands.literal("resume")
                .executes(ctx -> resume(ctx, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> resume(ctx, StringArgumentType.getString(ctx, "message"))))
        );
    }

    private static int follow(CommandContext<CommandSourceStack> ctx, String message) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;
        if (!requireUnownedOrOwnedBy(player, target)) return 0;

        target.getPersistentData().putString("mcaichat_directive", "FOLLOW");
        target.getPersistentData().putUUID("mcaichat_directive_player", player.getUUID());

        speak(player, target, message != null ? message : "Follow me!");
        return 1;
    }

    private static int stay(CommandContext<CommandSourceStack> ctx, String message) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;
        if (!requireUnownedOrOwnedBy(player, target)) return 0;

        target.getPersistentData().putString("mcaichat_directive", "STAY");
        target.getPersistentData().putUUID("mcaichat_directive_player", player.getUUID());

        speak(player, target, message != null ? message : "Stay here!");
        return 1;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx, String message) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;
        if (!requireUnownedOrOwnedBy(player, target)) return 0;

        // Directives never touch restrictTo, so clearing this is all it takes for wander
        // (and home-tether bias, if the NPC has a home) to resume on its own.
        target.getPersistentData().putString("mcaichat_directive", "NONE");
        target.getPersistentData().remove("mcaichat_directive_player");

        speak(player, target, message != null ? message : "We're all done!");
        return 1;
    }

    // Package-visible - GoToPacket's handler (the server-side landing spot for /goto, which
    // resolves everything client-side in GotoCommand) reuses this exact NBT write.
    static void setGotoDirective(Entity target, ServerPlayer player, BlockPos pos) {
        target.getPersistentData().putString("mcaichat_directive", "GOTO");
        target.getPersistentData().putUUID("mcaichat_directive_player", player.getUUID());
        target.getPersistentData().putInt("mcaichat_directive_x", pos.getX());
        target.getPersistentData().putInt("mcaichat_directive_y", pos.getY());
        target.getPersistentData().putInt("mcaichat_directive_z", pos.getZ());
    }

    // Directives lock to whoever claims them first and stay locked - even across the owner
    // going offline - until that same player explicitly /resume's the NPC. Any other player's
    // /follow, /stay, /goto, or /resume attempt on an already-owned NPC is rejected outright.
    // Package-visible - GoToPacket.handle reuses this same check before calling setGotoDirective.
    static boolean requireUnownedOrOwnedBy(ServerPlayer player, Entity target) {
        var data = target.getPersistentData();
        if (!data.contains("mcaichat_directive_player")) return true;

        java.util.UUID ownerId = data.getUUID("mcaichat_directive_player");
        if (ownerId.equals(player.getUUID())) return true;

        // A directive of "NONE" with a stale owner UUID left over from a prior /resume-less
        // claim isn't actually locked to anyone.
        String directive = data.getString("mcaichat_directive");
        if (directive.isEmpty() || directive.equals("NONE")) return true;

        String npcName = target.getPersistentData().getString("mcaichat_name");
        if (npcName.isEmpty()) npcName = target.getDisplayName().getString();

        player.sendSystemMessage(Component.literal(
                "§7" + npcName + " is already following someone else's orders."));
        return false;
    }

    private static Entity requireTarget(ServerPlayer player) {
        Entity target = NpcTargetResolver.getTargetEntity(player);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§7(No one is around to hear you...)"));
        }
        return target;
    }

    // The directive's message is said BY THE PLAYER, to the NPC - not a scripted line from the
    // NPC. Routed back to the issuing player's own client (via TriggerChatPacket) to run through
    // ChatInterceptor.sendPlayerMessage, the same real chat pipeline typing in chat would use, so
    // it actually gets a live, in-character Gemini response.
    static void speak(ServerPlayer player, Entity npc, String message) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new TriggerChatPacket(npc.getId(), message));
    }
}
