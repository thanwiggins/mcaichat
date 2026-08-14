package com.thanwiggins.mcaichat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

// Server-side commands letting a player found (/base new) and later update (/base edit) a named,
// described location that NPCs perceive the same way they perceive vanilla civ structures - see
// IdentityHandler.generateWorldKnowledge/considerNewLocation. Follows NpcDirectiveCommands' own
// RegisterCommandsEvent pattern, the mod's only other server-command precedent.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID)
public class PlayerLocationCommands {
    private static final int NEW_COST = 25;
    private static final int EDIT_COST = 10;
    private static final int CLAIM_COST = 25;
    private static final double CLAIM_SEARCH_RADIUS_BLOCKS = 256.0;

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
                .then(Commands.literal("claim")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                            .executes(PlayerLocationCommands::baseClaim))))
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

        int minDist = Config.MIN_LOCATION_DISTANCE.get();
        BlockPos pos = player.blockPosition();
        if (minDist > 0 && (!data.withinRadius(pos, minDist).isEmpty() || isNearExistingStructure(level, pos, minDist))) {
            player.sendSystemMessage(Component.literal("§cToo close to an existing structure, civilization, or player-founded location. Must be at least " + minDist + " blocks away."));
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

    // Claims an existing civ/nomad structure (or Ice&Fire dragon roost) as a player base once its
    // defenders are dealt with, migrating its surviving residents over to the new player-owned
    // location rather than leaving them orphaned under a home id that no longer resolves to anything.
    private static int baseClaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        String description = StringArgumentType.getString(ctx, "description");
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = player.blockPosition();

        PlayerLocationData data = PlayerLocationData.get(level);
        if (data.exists(name)) {
            player.sendSystemMessage(Component.literal("§cA location named '" + name + "' already exists."));
            return 0;
        }

        ClaimTarget target = findClaimTargetAt(level, pos);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cYou must be standing inside a conquerable structure to claim it."));
            return 0;
        }
        if (hasLivingDefenders(level, pos, target.homeId())) {
            player.sendSystemMessage(Component.literal("§cThis structure still has defenders alive. Defeat them first."));
            return 0;
        }
        if (!spendGold(player, CLAIM_COST)) {
            player.sendSystemMessage(Component.literal("§cClaiming this structure costs " + CLAIM_COST + " gold ingots."));
            return 0;
        }

        String finalDescription = description + " Formerly known as a " + target.structureType().replace("_", " ") + ".";
        PlayerLocationData.Location location = data.create(name, finalDescription, player.getUUID(), pos);
        migrateResidents(level, pos, target.homeId(), location);
        IdentityHandler.forgetOldLocation(level, pos, target.homeId());
        IdentityHandler.considerNewLocation(level, location);
        NetworkHandler.broadcastLocations(level);

        player.sendSystemMessage(Component.literal("§aClaimed '" + name + "'."));
        return 1;
    }

    // Same civ/nomad classification IdentityHandler uses when first assigning NPC homes, so a
    // claimed structure's id matches what its residents already have stored as mcaichat_home_id.
    private static ClaimTarget findClaimTargetAt(ServerLevel level, BlockPos pos) {
        ChunkPos centerChunk = new ChunkPos(pos);
        int radiusChunks = 16;

        for (int x = -radiusChunks; x <= radiusChunks; x++) {
            for (int z = -radiusChunks; z <= radiusChunks; z++) {
                ChunkAccess chunk = level.getChunk(centerChunk.x + x, centerChunk.z + z, ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;

                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start == null || !start.isValid() || !start.getBoundingBox().isInside(pos)) continue;

                    ResourceLocation key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                    if (key == null) continue;

                    String fullKey = key.toString();
                    if (Config.isInList(Config.IGNORED_STRUCTURES, fullKey)) continue;

                    boolean isCiv = Config.isInList(Config.CIV_STRUCTURES, fullKey);
                    boolean isNomad = !isCiv && Config.isInList(Config.NOMAD_STRUCTURES, fullKey);
                    if (!isCiv && !isNomad && !Config.isInList(Config.ADVENTURE_STRUCTURES, fullKey)) {
                        isCiv = fullKey.contains("village") || fullKey.contains("city")
                                || fullKey.contains("bastion") || fullKey.contains("fortress")
                                || fullKey.contains("towns_and_towers") || fullKey.contains("valarian_conquest");
                    }

                    if (isCiv || isNomad) {
                        String structId = fullKey + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                        return new ClaimTarget(structId, key.getPath());
                    }
                }
            }
        }

        // Dragon roosts are Features, not Structures, so the scan above never finds them.
        // Only claimable while standing exactly on the roost's registered position (distSqr 0) -
        // there's no bounding box to test "inside" against like a real Structure has.
        DragonRoostFinder.Roost roost = DragonRoostFinder.findNearest(level, pos, 32.0);
        if (roost != null && roost.distSqr() == 0.0 && !Config.isInList(Config.IGNORED_STRUCTURES, roost.type())) {
            return new ClaimTarget(roost.id(), roost.type().substring(roost.type().indexOf(':') + 1));
        }
        return null;
    }

    private static boolean hasLivingDefenders(ServerLevel level, BlockPos origin, String homeId) {
        AABB searchBox = new AABB(origin).inflate(CLAIM_SEARCH_RADIUS_BLOCKS);
        List<Entity> defenders = level.getEntities((Entity) null, searchBox, e ->
                Config.isWhitelisted(e) && e.getPersistentData().getString("mcaichat_home_id").equals(homeId) && Config.isCapableFighter(e));
        return !defenders.isEmpty();
    }

    // Re-tags every surviving resident of the claimed structure so they belong to the new
    // player-owned location, and re-tethers PathfinderMobs to it the same way IdentityHandler
    // does when it first assigns a home.
    private static void migrateResidents(ServerLevel level, BlockPos origin, String oldHomeId, PlayerLocationData.Location newLocation) {
        AABB searchBox = new AABB(origin).inflate(CLAIM_SEARCH_RADIUS_BLOCKS);
        List<Entity> residents = level.getEntities((Entity) null, searchBox, e ->
                Config.isWhitelisted(e) && e.getPersistentData().getString("mcaichat_home_id").equals(oldHomeId));

        // Residents already lived here before the claim - they already know their own home's
        // real name, same exception IdentityHandler applies when a home is first assigned.
        PlayerLocationData.get(level).reveal(newLocation.id(), newLocation.id());

        for (Entity resident : residents) {
            CompoundTag data = resident.getPersistentData();
            data.putString("mcaichat_home_id", newLocation.id());
            data.putString("mcaichat_home_type", "player_created");

            if (resident instanceof PathfinderMob pathfinderMob) {
                BlockPos residentPos = resident.blockPosition();
                data.putInt("mcaichat_home_x", residentPos.getX());
                data.putInt("mcaichat_home_y", residentPos.getY());
                data.putInt("mcaichat_home_z", residentPos.getZ());
                pathfinderMob.restrictTo(residentPos, Config.HOME_RADIUS.get());
            }
        }
    }

    // Same chunk-scan and civ/nomad classification as findClaimTargetAt, but only asking "is
    // anything nearby" rather than "what's directly underfoot" - used by baseNew's
    // minimum-distance guard. Adventure structures (and anything ignored) don't compete for
    // home the way civs/nomad camps do, so they don't block founding a location near them either.
    private static boolean isNearExistingStructure(ServerLevel level, BlockPos pos, double maxDistBlocks) {
        double maxDistSqr = maxDistBlocks * maxDistBlocks;
        int radiusChunks = (int) Math.ceil(maxDistBlocks / 16.0) + 1;
        ChunkPos centerChunk = new ChunkPos(pos);

        for (int x = -radiusChunks; x <= radiusChunks; x++) {
            for (int z = -radiusChunks; z <= radiusChunks; z++) {
                ChunkAccess chunk = level.getChunk(centerChunk.x + x, centerChunk.z + z, ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;

                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start == null || !start.isValid()) continue;

                    ResourceLocation key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                    if (key == null) continue;

                    String fullKey = key.toString();
                    if (Config.isInList(Config.IGNORED_STRUCTURES, fullKey)) continue;

                    boolean isCiv = Config.isInList(Config.CIV_STRUCTURES, fullKey);
                    boolean isNomad = !isCiv && Config.isInList(Config.NOMAD_STRUCTURES, fullKey);
                    if (!isCiv && !isNomad && !Config.isInList(Config.ADVENTURE_STRUCTURES, fullKey)) {
                        isCiv = fullKey.contains("village") || fullKey.contains("city")
                                || fullKey.contains("bastion") || fullKey.contains("fortress")
                                || fullKey.contains("towns_and_towers") || fullKey.contains("valarian_conquest");
                    }
                    if (!isCiv && !isNomad) continue;

                    BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                    double actualDistSqr = start.getBoundingBox().isInside(pos) ? 0.0 : pos.distSqr(startPos);
                    if (actualDistSqr <= maxDistSqr) return true;
                }
            }
        }

        // Dragon roosts always compete for "home" the same way civs/nomad camps do (see
        // IdentityHandler), so they're treated as civ-equivalent here rather than adventure.
        DragonRoostFinder.Roost roost = DragonRoostFinder.findNearest(level, pos, maxDistBlocks);
        return roost != null && roost.distSqr() <= maxDistSqr && !Config.isInList(Config.IGNORED_STRUCTURES, roost.type());
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

    private record ClaimTarget(String homeId, String structureType) {}
}
