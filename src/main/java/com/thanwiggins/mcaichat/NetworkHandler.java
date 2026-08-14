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
    private static final String PROTOCOL_VERSION = "1";
    
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

        INSTANCE.registerMessage(id++, NpcDeathPacket.class,
                NpcDeathPacket::encode,
                NpcDeathPacket::new,
                NpcDeathPacket::handle);

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
}