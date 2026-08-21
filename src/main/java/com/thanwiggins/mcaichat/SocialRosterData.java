package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Server-authoritative "who lives here" roster, one per dimension - the server-side counterpart
// to ClientSocialManager, which is now just a synced read-only mirror (see SocialRosterSyncPacket)
// rather than something each client independently accretes from its own observations. Populated
// the moment IdentityHandler settles an NPC's final home, and updated when a resident dies -
// see those call sites for exactly when.
public class SocialRosterData extends SavedData {
    private static final String STORAGE_ID = "mcaichat_social_roster";

    public static class CitizenProfile {
        public final String name;
        public final String type;
        public final String personality;
        public final String capabilities;
        public boolean isDeceased;
        public String causeOfDeath;

        public CitizenProfile(String name, String type, String personality, String capabilities) {
            this.name = name;
            this.type = type;
            this.personality = personality;
            this.capabilities = capabilities;
            this.isDeceased = false;
            this.causeOfDeath = "";
        }
    }

    private final Map<String, Map<UUID, CitizenProfile>> roster = new HashMap<>();

    public static SocialRosterData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SocialRosterData::load, SocialRosterData::new, STORAGE_ID);
    }

    public Map<UUID, CitizenProfile> getCitizens(String homeId) {
        return roster.getOrDefault(homeId, Map.of());
    }

    public Map<String, Map<UUID, CitizenProfile>> all() {
        return roster;
    }

    // Only adds if this citizen doesn't already exist anywhere in the roster, so a home never
    // accidentally clobbers an existing entry's death status (or gains a duplicate under a
    // different home if an NPC's home is ever reassigned).
    public boolean addCitizen(String homeId, UUID uuid, String name, String type, String personality, String capabilities) {
        if (homeId == null || homeId.isEmpty() || homeId.equals("none")) return false;
        for (Map<UUID, CitizenProfile> citizens : roster.values()) {
            if (citizens.containsKey(uuid)) return false;
        }

        roster.computeIfAbsent(homeId, k -> new HashMap<>()).put(uuid, new CitizenProfile(name, type, personality, capabilities));
        setDirty();
        return true;
    }

    public boolean markDeceased(UUID uuid, String cause) {
        boolean changed = false;
        for (Map<UUID, CitizenProfile> citizens : roster.values()) {
            CitizenProfile profile = citizens.get(uuid);
            if (profile != null && !profile.isDeceased) {
                profile.isDeceased = true;
                profile.causeOfDeath = cause;
                changed = true;
            }
        }
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("roster", rosterTag(roster));
        return tag;
    }

    // Shared by the full save tag and SocialRosterSyncPacket's single-home broadcast payload.
    public static ListTag rosterTag(Map<String, Map<UUID, CitizenProfile>> roster) {
        ListTag homesList = new ListTag();
        for (Map.Entry<String, Map<UUID, CitizenProfile>> homeEntry : roster.entrySet()) {
            CompoundTag homeTag = new CompoundTag();
            homeTag.putString("homeId", homeEntry.getKey());

            ListTag citizensList = new ListTag();
            for (Map.Entry<UUID, CitizenProfile> citizenEntry : homeEntry.getValue().entrySet()) {
                CitizenProfile profile = citizenEntry.getValue();
                CompoundTag citizenTag = new CompoundTag();
                citizenTag.putUUID("uuid", citizenEntry.getKey());
                citizenTag.putString("name", profile.name);
                citizenTag.putString("type", profile.type);
                citizenTag.putString("personality", profile.personality);
                citizenTag.putString("capabilities", profile.capabilities);
                citizenTag.putBoolean("isDeceased", profile.isDeceased);
                citizenTag.putString("causeOfDeath", profile.causeOfDeath);
                citizensList.add(citizenTag);
            }
            homeTag.put("citizens", citizensList);
            homesList.add(homeTag);
        }
        return homesList;
    }

    private static SocialRosterData load(CompoundTag tag) {
        SocialRosterData data = new SocialRosterData();
        ListTag homesList = tag.getList("roster", 10); // 10 = CompoundTag
        for (int i = 0; i < homesList.size(); i++) {
            CompoundTag homeTag = homesList.getCompound(i);
            String homeId = homeTag.getString("homeId");
            Map<UUID, CitizenProfile> citizens = data.roster.computeIfAbsent(homeId, k -> new HashMap<>());

            ListTag citizensList = homeTag.getList("citizens", 10);
            for (int j = 0; j < citizensList.size(); j++) {
                CompoundTag citizenTag = citizensList.getCompound(j);
                CitizenProfile profile = new CitizenProfile(
                        citizenTag.getString("name"),
                        citizenTag.getString("type"),
                        citizenTag.getString("personality"),
                        citizenTag.getString("capabilities"));
                profile.isDeceased = citizenTag.getBoolean("isDeceased");
                profile.causeOfDeath = citizenTag.getString("causeOfDeath");
                citizens.put(citizenTag.getUUID("uuid"), profile);
            }
        }
        return data;
    }
}
