package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    // Bumped for the 1.0.2 multiplayer-support rework - the packet set and several existing
    // packets' meaning changed enough that an old client/server pairing shouldn't be allowed to
    // connect at all, rather than silently misbehaving.
    private static final String PROTOCOL_VERSION = "2";
    
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(GeminiMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    // Registration order fixes each packet's numeric ID for this mod version - add new packets
    // at the end rather than reordering these, so old and new clients/servers never disagree on IDs.
    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, StructurePacket.class,
                StructurePacket::encode,
                StructurePacket::new,
                StructurePacket::handle);

        INSTANCE.registerMessage(id++, SyncNPCPacket.class,
                SyncNPCPacket::encode,
                SyncNPCPacket::new,
                SyncNPCPacket::handle);

        INSTANCE.registerMessage(id++, ConversationStatePacket.class,
                ConversationStatePacket::encode,
                ConversationStatePacket::new,
                ConversationStatePacket::handle);

        INSTANCE.registerMessage(id++, LocationSyncPacket.class,
                LocationSyncPacket::encode,
                LocationSyncPacket::new,
                LocationSyncPacket::handle);

        INSTANCE.registerMessage(id++, LocationRevealPacket.class,
                LocationRevealPacket::encode,
                LocationRevealPacket::new,
                LocationRevealPacket::handle);

        INSTANCE.registerMessage(id++, TriggerChatPacket.class,
                TriggerChatPacket::encode,
                TriggerChatPacket::new,
                TriggerChatPacket::handle);

        INSTANCE.registerMessage(id++, GoToPacket.class,
                GoToPacket::encode,
                GoToPacket::new,
                GoToPacket::handle);

        INSTANCE.registerMessage(id++, PlayerNameRevealPacket.class,
                PlayerNameRevealPacket::encode,
                PlayerNameRevealPacket::new,
                PlayerNameRevealPacket::handle);

        INSTANCE.registerMessage(id++, LoreSyncPacket.class,
                LoreSyncPacket::encode,
                LoreSyncPacket::new,
                LoreSyncPacket::handle);

        INSTANCE.registerMessage(id++, LoreReportPacket.class,
                LoreReportPacket::encode,
                LoreReportPacket::new,
                LoreReportPacket::handle);

        INSTANCE.registerMessage(id++, EffectiveConfigSyncPacket.class,
                EffectiveConfigSyncPacket::encode,
                EffectiveConfigSyncPacket::new,
                EffectiveConfigSyncPacket::handle);

        INSTANCE.registerMessage(id++, ConversationClaimRequestPacket.class,
                ConversationClaimRequestPacket::encode,
                ConversationClaimRequestPacket::new,
                ConversationClaimRequestPacket::handle);

        INSTANCE.registerMessage(id++, ConversationClaimResponsePacket.class,
                ConversationClaimResponsePacket::encode,
                ConversationClaimResponsePacket::new,
                ConversationClaimResponsePacket::handle);

        INSTANCE.registerMessage(id++, MemoryUpdatePacket.class,
                MemoryUpdatePacket::encode,
                MemoryUpdatePacket::new,
                MemoryUpdatePacket::handle);

        INSTANCE.registerMessage(id++, SocialRosterSyncPacket.class,
                SocialRosterSyncPacket::encode,
                SocialRosterSyncPacket::new,
                SocialRosterSyncPacket::handle);

        INSTANCE.registerMessage(id++, PlayerIdentityUpdatePacket.class,
                PlayerIdentityUpdatePacket::encode,
                PlayerIdentityUpdatePacket::new,
                PlayerIdentityUpdatePacket::handle);

        INSTANCE.registerMessage(id++, PlayerIdentitySyncPacket.class,
                PlayerIdentitySyncPacket::encode,
                PlayerIdentitySyncPacket::new,
                PlayerIdentitySyncPacket::handle);

        INSTANCE.registerMessage(id++, CivNameReportPacket.class,
                CivNameReportPacket::encode,
                CivNameReportPacket::new,
                CivNameReportPacket::handle);

        INSTANCE.registerMessage(id++, CivWaypointSyncPacket.class,
                CivWaypointSyncPacket::encode,
                CivWaypointSyncPacket::new,
                CivWaypointSyncPacket::handle);
    }

    // Sent to a newly-joining player: every currently-online player's identity, read straight off
    // each ServerPlayer's own persistent data. The new joiner's own identity (if they'd set one on
    // a prior visit) reaches everyone else the same way, individually, right after.
    public static void sendPlayerIdentitiesTo(ServerPlayer player) {
        java.util.List<PlayerIdentitySyncPacket.Entry> entries = new java.util.ArrayList<>();
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            String name = online.getPersistentData().getString("mcaichat_player_display_name");
            String description = online.getPersistentData().getString("mcaichat_player_description");
            if (!name.isEmpty() || !description.isEmpty()) {
                entries.add(new PlayerIdentitySyncPacket.Entry(online.getUUID(), name, description));
            }
        }
        if (!entries.isEmpty()) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PlayerIdentitySyncPacket(entries));
        }

        if (player.getPersistentData().contains("mcaichat_player_display_name")
                || player.getPersistentData().contains("mcaichat_player_description")) {
            String name = player.getPersistentData().getString("mcaichat_player_display_name");
            String description = player.getPersistentData().getString("mcaichat_player_description");
            INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new PlayerIdentitySyncPacket(java.util.List.of(new PlayerIdentitySyncPacket.Entry(player.getUUID(), name, description))));
        }
    }

    // Single-home broadcast, used right after IdentityHandler settles a home or a resident dies.
    public static void broadcastSocialRoster(ServerLevel level, String homeId) {
        SocialRosterData data = SocialRosterData.get(level);
        java.util.Map<String, java.util.Map<java.util.UUID, SocialRosterData.CitizenProfile>> single = new java.util.HashMap<>();
        single.put(homeId, data.getCitizens(homeId));

        CompoundTag tag = new CompoundTag();
        tag.put("roster", SocialRosterData.rosterTag(single));
        INSTANCE.send(PacketDistributor.ALL.noArg(), new SocialRosterSyncPacket(tag));
    }

    // Full dump sent on login, mirroring sendLocationsTo/sendLoreTo.
    public static void sendSocialRosterTo(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.put("roster", SocialRosterData.rosterTag(SocialRosterData.get(player.serverLevel()).all()));
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SocialRosterSyncPacket(tag));
    }

    private static CompoundTag effectiveConfigTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("whitelistEntities", Config.getEffectiveList(Config.WHITELIST_ENTITIES));
        tag.putString("blacklistEntities", Config.getEffectiveList(Config.BLACKLIST_ENTITIES));
        tag.putString("wandererEntities", Config.getEffectiveList(Config.WANDERER_ENTITIES));
        tag.putString("customMonsters", Config.getEffectiveList(Config.CUSTOM_MONSTERS));
        tag.putString("customCreatures", Config.getEffectiveList(Config.CUSTOM_CREATURES));
        tag.putString("customWildlife", Config.getEffectiveList(Config.CUSTOM_WILDLIFE));
        tag.putString("civStructures", Config.getEffectiveList(Config.CIV_STRUCTURES));
        tag.putString("nomadStructures", Config.getEffectiveList(Config.NOMAD_STRUCTURES));
        tag.putString("adventureStructures", Config.getEffectiveList(Config.ADVENTURE_STRUCTURES));
        tag.putString("ignoredStructures", Config.getEffectiveList(Config.IGNORED_STRUCTURES));
        return tag;
    }

    // Re-sent to everyone whenever the host saves a change in the config-edit screens
    // (EntityConfigScreen/StructureConfigScreen) - the resolved value is the same for every
    // player, so this is a plain broadcast, not per-player.
    public static void broadcastEffectiveConfig() {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new EffectiveConfigSyncPacket(effectiveConfigTag()));
    }

    public static void sendEffectiveConfigTo(ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new EffectiveConfigSyncPacket(effectiveConfigTag()));
    }

    public static void broadcastLocations(ServerLevel level) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new LocationSyncPacket(PlayerLocationData.get(level).save(new CompoundTag())));
    }

    public static void sendLocationsTo(ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new LocationSyncPacket(PlayerLocationData.get(player.serverLevel()).save(new CompoundTag())));
    }

    // Single-entry broadcast used right after a client's LoreReportPacket is accepted as the
    // winning report for a structure - see LoreReportPacket.handle.
    public static void broadcastNewLore(ServerLevel level, String structureId, StructureLoreData.LoreEntry entry) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        list.add(loreEntryTag(structureId, entry));
        tag.put("lore", list);
        INSTANCE.send(PacketDistributor.ALL.noArg(), new LoreSyncPacket(tag));
    }

    // Full dump sent on login, mirroring sendLocationsTo, so a player who wasn't online for any
    // of the reports so far still starts with every structure's already-known lore.
    public static void sendLoreTo(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (java.util.Map.Entry<String, StructureLoreData.LoreEntry> entry : StructureLoreData.get(player.serverLevel()).all().entrySet()) {
            list.add(loreEntryTag(entry.getKey(), entry.getValue()));
        }
        tag.put("lore", list);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new LoreSyncPacket(tag));
    }

    private static CompoundTag loreEntryTag(String structureId, StructureLoreData.LoreEntry entry) {
        CompoundTag entryTag = new CompoundTag();
        entryTag.putString("id", structureId);
        entryTag.putString("name", entry.name());
        entryTag.putString("background", entry.background());
        entryTag.putString("type", entry.type());
        entryTag.putString("fullKey", entry.fullKey());
        return entryTag;
    }

    private static CompoundTag civWaypointEntryTag(String structureId, PlayerCivWaypointData.CivEntry entry, ServerLevel dimensionLevel) {
        CompoundTag entryTag = new CompoundTag();
        entryTag.putString("id", structureId);
        entryTag.putString("name", entry.name());
        entryTag.putString("dimension", dimensionLevel.dimension().location().toString());
        entryTag.putInt("x", entry.pos().getX());
        entryTag.putInt("y", entry.pos().getY());
        entryTag.putInt("z", entry.pos().getZ());
        return entryTag;
    }

    // Single-entry push right after ServerStructureTracker credits a player with a brand-new
    // visit - see PlayerCivWaypointData.markVisited.
    public static void sendCivWaypointTo(ServerPlayer player, ServerLevel level, String structureId, PlayerCivWaypointData.CivEntry entry) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        list.add(civWaypointEntryTag(structureId, entry, level));
        tag.put("waypoints", list);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CivWaypointSyncPacket(tag));
    }

    // Full dump sent on login, mirroring sendLoreTo/sendLocationsTo - only this player's own
    // visited set, scoped to whichever dimension's PlayerCivWaypointData they're currently in
    // (same per-dimension-storage simplification StructureLoreData already accepts).
    public static void sendCivWaypointsTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        PlayerCivWaypointData data = PlayerCivWaypointData.get(level);
        java.util.Set<String> visitedIds = data.getVisited(player.getUUID());
        if (visitedIds.isEmpty()) return;

        ListTag list = new ListTag();
        for (String structureId : visitedIds) {
            PlayerCivWaypointData.CivEntry entry = data.get(structureId);
            if (entry == null || entry.name() == null || entry.pos() == null) continue;
            list.add(civWaypointEntryTag(structureId, entry, level));
        }
        if (list.isEmpty()) return;

        CompoundTag tag = new CompoundTag();
        tag.put("waypoints", list);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CivWaypointSyncPacket(tag));
    }
}