package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.stream.Collectors;

import com.faboslav.structurify.common.config.data.WorldgenDataProvider;
import com.faboslav.structurify.common.config.data.StructureData;

// Lets the player override how a structure is categorized (civilization/nomad/adventure/ignored),
// which controls whether NPCs treat it as a "home", generate lore for it, or ignore it entirely.
// The searchable list is seeded from three sources: structures already discovered in this world,
// anything already present in the config lists, and Structurify's full registry of known worldgen
// structures (so players can pre-configure structures they haven't found yet).
public class StructureConfigScreen extends Screen {
    private final Screen previous;
    private EditBox searchBox;
    private int currentPage = 0;
    private final int itemsPerPage = 6;
    private List<String> allStructures;
    private List<String> filteredStructures;
    
    public StructureConfigScreen(Screen previous) {
        super(Component.literal("Configure Structure Categories"));
        this.previous = previous;
        
        Set<String> structSet = new HashSet<>();
        structSet.addAll(ClientLoreManager.getKnownStructureKeys());
        structSet.addAll(Arrays.asList(Config.CIV_STRUCTURES.get().split(",")));
        structSet.addAll(Arrays.asList(Config.NOMAD_STRUCTURES.get().split(",")));
        structSet.addAll(Arrays.asList(Config.ADVENTURE_STRUCTURES.get().split(",")));
        structSet.addAll(Arrays.asList(Config.IGNORED_STRUCTURES.get().split(",")));
        structSet.remove("");
        
        Map<String, StructureData> masterStructureList = WorldgenDataProvider.getStructures();

        // Structurify only builds this list lazily; force it now if this screen is opened
        // before anything else has triggered that load.
        if (masterStructureList == null || masterStructureList.isEmpty()) {
            WorldgenDataProvider.loadWorldgenData();
            masterStructureList = WorldgenDataProvider.getStructures();
        }
        
        if (masterStructureList != null) {
            masterStructureList.keySet().forEach(key -> structSet.add(key));
        }
        
        this.allStructures = new ArrayList<>(structSet);
        Collections.sort(this.allStructures);
        this.filteredStructures = new ArrayList<>(this.allStructures);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;
        int yStart = 40;

        // Only the world host's config is ever actually enforced (see EffectiveConfig) - a
        // joining player's own edits here would silently do nothing, so editing is disabled
        // entirely for anyone who isn't running the integrated/dedicated server themselves.
        boolean canEdit = net.minecraft.client.Minecraft.getInstance().isLocalServer();

        this.searchBox = new EditBox(this.font, centerX - 150, 15, 300, 20, Component.literal("Search or Add ID"));
        this.searchBox.setResponder(text -> {
            this.filteredStructures = allStructures.stream()
                .filter(e -> e.toLowerCase().contains(text.toLowerCase()))
                .collect(Collectors.toList());
                
            if (!text.isEmpty() && !allStructures.contains(text)) {
                this.filteredStructures.add(0, text);
            }
            this.currentPage = 0;
            this.init(); 
        });
        this.addRenderableWidget(this.searchBox);

        int maxPages = Math.max(1, (int) Math.ceil(filteredStructures.size() / (double) itemsPerPage));
        
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (currentPage > 0) { currentPage--; this.init(); }
        }).bounds(centerX - 180, 15, 20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (currentPage < maxPages - 1) { currentPage++; this.init(); }
        }).bounds(centerX + 160, 15, 20, 20).build());

        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredStructures.size());

        for (int i = startIdx; i < endIdx; i++) {
            String structId = filteredStructures.get(i);
            int yPos = yStart + ((i - startIdx) * 25);
            
            String currentCat = "";
            if (Config.isInList(Config.IGNORED_STRUCTURES, structId)) currentCat = "Ignored";
            else if (Config.isInList(Config.CIV_STRUCTURES, structId)) currentCat = "Civilization";
            else if (Config.isInList(Config.NOMAD_STRUCTURES, structId)) currentCat = "Standard";
            else if (Config.isInList(Config.ADVENTURE_STRUCTURES, structId)) currentCat = "Hidden";
            else {
                boolean isCiv = structId.contains("village") || structId.contains("city") ||
                                structId.contains("bastion") || structId.contains("fortress") ||
                                structId.contains("towns_and_towers") || structId.contains("valarian_conquest");
                currentCat = "Default (" + (isCiv ? "Civ." : "Hid.") + ")";
            }

            Button cycleBtn = Button.builder(Component.literal(currentCat), b -> {
                cycleStructureCategory(structId);
                NetworkHandler.broadcastEffectiveConfig();
                this.init();
            }).bounds(centerX + 10, yPos, 160, 20).build();
            cycleBtn.active = canEdit;

            this.addRenderableWidget(cycleBtn);
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.minecraft.setScreen(this.previous);
        }).bounds(centerX - 150, this.height - 30, 300, 20).build());
    }

    // Cycles a structure through Civilization -> Standard -> Hidden -> Ignored -> Civilization,
    // clearing whichever list it was previously in before adding it to the next one.
    private void cycleStructureCategory(String id) {
        boolean isIgnored = Config.isInList(Config.IGNORED_STRUCTURES, id);
        boolean isCiv = Config.isInList(Config.CIV_STRUCTURES, id);
        boolean isNomad = Config.isInList(Config.NOMAD_STRUCTURES, id);
        boolean isAdv = Config.isInList(Config.ADVENTURE_STRUCTURES, id);

        Config.setCategory(Config.IGNORED_STRUCTURES, id, false);
        Config.setCategory(Config.CIV_STRUCTURES, id, false);
        Config.setCategory(Config.NOMAD_STRUCTURES, id, false);
        Config.setCategory(Config.ADVENTURE_STRUCTURES, id, false);

        if (!isIgnored && !isCiv && !isNomad && !isAdv) {
            Config.setCategory(Config.CIV_STRUCTURES, id, true);
        } else if (isCiv) {
            Config.setCategory(Config.NOMAD_STRUCTURES, id, true);
        } else if (isNomad) {
            Config.setCategory(Config.ADVENTURE_STRUCTURES, id, true);
        } else if (isAdv) {
            Config.setCategory(Config.IGNORED_STRUCTURES, id, true);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int yStart = 40;
        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredStructures.size());

        for (int i = startIdx; i < endIdx; i++) {
            String structId = filteredStructures.get(i);
            int yPos = yStart + ((i - startIdx) * 25) + 5;
            
            int maxWidth = 180;
            int textX = centerX - 180;
            int textWidth = this.font.width(structId);
            
            if (textWidth > maxWidth) {
                // Long structure IDs get a marquee scroll: pause at the start, scroll left to
                // reveal the end, then wrap back to the start on a loop.
                gui.enableScissor(textX, yPos, textX + maxWidth, yPos + this.font.lineHeight);
                long time = net.minecraft.Util.getMillis();
                int scrollRange = textWidth - maxWidth + 20;
                int offset = (int) ((time / 30L) % (scrollRange + 40));
                if (offset > scrollRange) offset = scrollRange;
                if (offset < 20) offset = 0;
                else offset -= 20;

                gui.drawString(this.font, structId, textX - offset, yPos, 0xFFFFFF);
                gui.disableScissor();
            } else {
                gui.drawString(this.font, structId, textX, yPos, 0xFFFFFF);
            }
        }

        int maxPages = Math.max(1, (int) Math.ceil(filteredStructures.size() / (double) itemsPerPage));
        gui.drawCenteredString(this.font, "Page " + (currentPage + 1) + " of " + maxPages, centerX, 5, 0xAAAAAA);
    }
}