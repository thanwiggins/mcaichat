package com.thanwiggins.mcaichat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Client-side cache of other online players' chosen display name/description, fed only by
// PlayerIdentitySyncPacket. See PromptBuilder.getPlayerDisplayName/buildPlayerSocialLine, which
// consult this for any player other than the local one (the local player's own values are read
// straight from Config for zero-latency self-edits).
public class PlayerIdentityCache {
    public record Entry(String displayName, String description) {}

    private static final Map<UUID, Entry> cache = new HashMap<>();

    public static void put(UUID playerId, String displayName, String description) {
        cache.put(playerId, new Entry(displayName, description));
    }

    public static Entry get(UUID playerId) {
        return cache.get(playerId);
    }
}
