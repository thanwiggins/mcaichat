package com.thanwiggins.mcaichat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Client-side /goto. Unlike /follow, /stay, /resume (plain server commands - see
// NpcDirectiveCommands), matching a named location by its real nickname needs ClientLoreManager/
// ClientLocationManager, which only exist client-side. All three forms (coordinates, x/z-only,
// name) resolve down to one final BlockPos + message here, then a single GoToPacket carries the
// result to the server, which just applies it - no client-only lookups needed there.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class GotoCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("goto")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> goTo(ctx, null))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> goTo(ctx, StringArgumentType.getString(ctx, "message")))))
                // x/z only - an NPC walking somewhere doesn't need the player to know or care what
                // the ground height is there; resolved via heightmap below.
                .then(Commands.argument("columnPos", ColumnPosArgument.columnPos())
                    .executes(ctx -> goToColumn(ctx, null))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> goToColumn(ctx, StringArgumentType.getString(ctx, "message")))))
                // A single word, matched against the target NPC's own known locations (its home
                // and mcaichat_nearby_civs) by their real nickname - not a general lookup, so the
                // command fails cleanly rather than falling back to some other, unintended place.
                .then(Commands.argument("location", StringArgumentType.word())
                    .executes(ctx -> goToNamed(ctx, null))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> goToNamed(ctx, StringArgumentType.getString(ctx, "message")))))
        );
    }

    private static int goTo(CommandContext<CommandSourceStack> ctx, String message) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return 0;

        Entity target = requireTarget(mc, player);
        if (target == null) return 0;

        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        String defaultMessage = "Go to the " + cardinalDirection(player.blockPosition(), pos) + "!";
        send(target, pos, message != null ? message : defaultMessage);
        return 1;
    }

    // x/z only, ground height resolved via heightmap - the player asking an NPC to go somewhere
    // usually cares about "over there", not the exact Y, and getting Y wrong (e.g. defaulting to
    // sea level) can send the NPC pathing somewhere nonsensical.
    private static int goToColumn(CommandContext<CommandSourceStack> ctx, String message) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return 0;

        Entity target = requireTarget(mc, player);
        if (target == null) return 0;

        ColumnPos columnPos = ColumnPosArgument.getColumnPos(ctx, "columnPos");
        int y = target.level().getHeight(Heightmap.Types.MOTION_BLOCKING, columnPos.x(), columnPos.z());
        BlockPos pos = new BlockPos(columnPos.x(), y, columnPos.z());

        String defaultMessage = "Go to the " + cardinalDirection(player.blockPosition(), pos) + "!";
        send(target, pos, message != null ? message : defaultMessage);
        return 1;
    }

    private static int goToNamed(CommandContext<CommandSourceStack> ctx, String message) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return 0;

        Entity target = requireTarget(mc, player);
        if (target == null) return 0;

        String name = StringArgumentType.getString(ctx, "location");
        LocationMatch match = findKnownLocation(target, name);
        if (match == null) {
            player.sendSystemMessage(Component.literal("§c[" + npcDisplayName(target) + "]: I don't know of a '" + name + "' nearby."));
            return 0;
        }

        String defaultMessage = "Go check out " + match.name + "!";
        send(target, match.pos, message != null ? message : defaultMessage);
        return 1;
    }

    private static void send(Entity target, BlockPos pos, String message) {
        NetworkHandler.INSTANCE.sendToServer(new GoToPacket(target.getId(), pos, message));
    }

    private record LocationMatch(String name, BlockPos pos) {}

    // Matches against the real nickname a location is currently known by - ClientLoreManager's
    // generated realm name for vanilla structures, ClientLocationManager's (possibly still-hidden)
    // name for player-created ones - never the raw structure type. Only searches what this
    // specific NPC already knows about (its own home, plus mcaichat_nearby_civs); no broader/
    // global fallback, so an unfamiliar or not-yet-discovered name fails the command instead of
    // guessing.
    private static LocationMatch findKnownLocation(Entity target, String name) {
        CompoundTag data = target.getPersistentData();
        BlockPos from = target.blockPosition();
        String viewerHomeId = data.getString("mcaichat_home_id");

        LocationMatch best = null;
        double bestDistSqr = Double.MAX_VALUE;

        if (!viewerHomeId.isEmpty() && !viewerHomeId.equals("none") && data.contains("mcaichat_home_x")) {
            String homeName = knownName(viewerHomeId, data.getString("mcaichat_home_type"), viewerHomeId);
            if (homeName != null && homeName.equalsIgnoreCase(name)) {
                BlockPos pos = new BlockPos(
                        data.getInt("mcaichat_home_x"),
                        data.getInt("mcaichat_home_y"),
                        data.getInt("mcaichat_home_z"));
                double distSqr = from.distSqr(pos);
                if (distSqr < bestDistSqr) {
                    best = new LocationMatch(homeName, pos);
                    bestDistSqr = distSqr;
                }
            }
        }

        if (data.contains("mcaichat_nearby_civs", 9)) {
            ListTag civList = data.getList("mcaichat_nearby_civs", 8);
            for (int i = 0; i < civList.size(); i++) {
                String[] parts = civList.getString(i).split("\\|");
                if (parts.length != 5) continue;

                String civName = knownName(parts[0], parts[1], viewerHomeId);
                if (civName == null || !civName.equalsIgnoreCase(name)) continue;

                int x = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);
                // mcaichat_nearby_civs only stores x/z, not y - resolve a usable ground height here.
                int y = target.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos pos = new BlockPos(x, y, z);

                double distSqr = from.distSqr(pos);
                if (distSqr < bestDistSqr) {
                    best = new LocationMatch(civName, pos);
                    bestDistSqr = distSqr;
                }
            }
        }

        return best;
    }

    // The name this NPC would actually reference this location by right now - null if nothing's
    // known yet (a vanilla structure whose lore was never generated, i.e. never visited by anyone)
    // so it can never match.
    private static String knownName(String id, String rawType, String viewerHomeId) {
        if (rawType.equals("player_created")) {
            ClientLocationManager.LocationInfo info = ClientLocationManager.get(id);
            return info != null ? info.displayName(viewerHomeId) : null;
        }
        ClientLoreManager.StructureLore lore = ClientLoreManager.getLore(id);
        return lore != null ? lore.name : null;
    }

    private static String npcDisplayName(Entity npc) {
        String name = npc.getPersistentData().getString("mcaichat_name");
        return name.isEmpty() ? npc.getDisplayName().getString() : name;
    }

    private static Entity requireTarget(Minecraft mc, Player player) {
        Entity target = ChatInterceptor.getTargetEntity(mc, player);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§7(No one is around to hear you...)"));
        }
        return target;
    }

    private static String cardinalDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? "East" : "West";
        }
        return dz >= 0 ? "South" : "North";
    }
}
