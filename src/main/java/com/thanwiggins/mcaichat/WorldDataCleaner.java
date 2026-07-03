package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Vanilla has no event for "a world was deleted" - the "Delete World" button just wipes the save
// folder and drops the player back on the (reloaded) singleplayer world list. So instead of hooking
// that button, this watches the world list itself: once it finishes (re)loading, any lore/memory/
// social file whose world no longer appears in it is stale and gets pruned. This also cleans up
// after worlds removed outside the game entirely (e.g. deleted from the filesystem directly).
@Mod.EventBusSubscriber(modid = GeminiMod.MODID, value = Dist.CLIENT)
public class WorldDataCleaner {

    // WorldSelectionList#currentlyDisplayedLevels is null until the async world scan first
    // completes, then holds the full (unfiltered) result - including an empty list if there
    // truly are no worlds left. That null/non-null split is what lets us tell "still loading"
    // apart from "confirmed empty" without racing the list's own render-driven polling.
    private static final Field DISPLAYED_LEVELS_FIELD;
    static {
        Field field = null;
        try {
            field = WorldSelectionList.class.getDeclaredField("currentlyDisplayedLevels");
            field.setAccessible(true);
        } catch (Exception e) {
            System.err.println("[MC-AI Chat] Could not hook into the world list - data for deleted worlds won't be auto-pruned.");
        }
        DISPLAYED_LEVELS_FIELD = field;
    }

    private static boolean prunedThisVisit = false;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof SelectWorldScreen) {
            prunedThisVisit = false;
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (prunedThisVisit || DISPLAYED_LEVELS_FIELD == null || !(event.getScreen() instanceof SelectWorldScreen screen)) {
            return;
        }

        for (Object child : screen.children()) {
            if (!(child instanceof WorldSelectionList list)) continue;

            List<LevelSummary> levels;
            try {
                levels = (List<LevelSummary>) DISPLAYED_LEVELS_FIELD.get(list);
            } catch (Exception e) {
                return;
            }

            if (levels == null) return; // still loading

            Set<String> existingWorldIds = new HashSet<>();
            for (LevelSummary summary : levels) {
                existingWorldIds.add("sp_" + summary.getLevelName().replaceAll("[^a-zA-Z0-9.-]", "_"));
            }

            ClientLoreManager.pruneDeletedWorlds(existingWorldIds);
            ClientMemoryManager.pruneDeletedWorlds(existingWorldIds);
            ClientSocialManager.pruneDeletedWorlds(existingWorldIds);
            prunedThisVisit = true;
            return;
        }
    }
}
