package com.thanwiggins.mcaichat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Client-side mirror of SocialRosterData - which NPCs share a home structure (their "social
// circle"), and whether one of them has since died. Populated only by SocialRosterSyncPacket;
// the server is the sole source of truth (see SocialRosterData), so this class never accretes
// citizens from local observations the way it used to.
public class ClientSocialManager {
    private static Map<String, Map<UUID, CitizenProfile>> socialMap = new HashMap<>();

    public static class CitizenProfile {
        public String name;
        public String type;
        public String personality;
        public String capabilities;
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

    // Replaces whichever homes appear in this payload wholesale - either one home (a
    // broadcast-on-change) or every home (the full login dump) - matching whatever
    // SocialRosterData.rosterTag actually serialized.
    public static void merge(CompoundTag data) {
        ListTag homesList = data.getList("roster", 10); // 10 = CompoundTag
        for (int i = 0; i < homesList.size(); i++) {
            CompoundTag homeTag = homesList.getCompound(i);
            String homeId = homeTag.getString("homeId");

            Map<UUID, CitizenProfile> citizens = new HashMap<>();
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
            socialMap.put(homeId, citizens);
        }
    }

    public static Map<UUID, CitizenProfile> getCitizens(String homeId) {
        return socialMap.getOrDefault(homeId, new HashMap<>());
    }
}
