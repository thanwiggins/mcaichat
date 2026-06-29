package com.thanwiggins.mcaichat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.stream.Collectors;

// Structurify imports
import com.faboslav.structurify.common.config.data.WorldgenDataProvider;
import com.faboslav.structurify.common.config.data.StructureData;

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
        structSet.addAll(Arrays.asList(Config.ADVENTURE_STRUCTURES.get().split(",")));
        structSet.addAll(Arrays.asList(Config.IGNORED_STRUCTURES.get().split(",")));
        structSet.remove("");
        
        // Pull the universal master list from Structurify
        Map<String, StructureData> masterStructureList = WorldgenDataProvider.getStructures();
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

        // Widened layout bounds
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
            else if (Config.isInList(Config.ADVENTURE_STRUCTURES, structId)) currentCat = "Adventure";
            else {
                boolean isCiv = structId.contains("village") || structId.contains("city") || 
                                structId.contains("bastion") || structId.contains("fortress") ||
                                structId.contains("towns_and_towers") || structId.contains("valarian_conquest");
                currentCat = "Default (" + (isCiv ? "Civ." : "Adv.") + ")";
            }

            // Widened cycle button to 160px
            Button cycleBtn = Button.builder(Component.literal(currentCat), b -> {
                cycleStructureCategory(structId);
                this.init(); 
            }).bounds(centerX + 10, yPos, 160, 20).build();
            
            this.addRenderableWidget(cycleBtn);
        }

        // Widened back button to match search bar width
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.minecraft.setScreen(this.previous);
        }).bounds(centerX - 150, this.height - 30, 300, 20).build());
    }

    private void cycleStructureCategory(String id) {
        boolean isIgnored = Config.isInList(Config.IGNORED_STRUCTURES, id);
        boolean isCiv = Config.isInList(Config.CIV_STRUCTURES, id);
        boolean isAdv = Config.isInList(Config.ADVENTURE_STRUCTURES, id);

        Config.setCategory(Config.IGNORED_STRUCTURES, id, false);
        Config.setCategory(Config.CIV_STRUCTURES, id, false);
        Config.setCategory(Config.ADVENTURE_STRUCTURES, id, false);

        if (!isIgnored && !isCiv && !isAdv) {
            Config.setCategory(Config.CIV_STRUCTURES, id, true);
        } else if (isCiv) {
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
            
            String displayStr = structId;
            // Increased text width limit to 180px and shifted left
            if (this.font.width(displayStr) > 180) {
                displayStr = this.font.plainSubstrByWidth(displayStr, 170) + "...";
            }
            gui.drawString(this.font, displayStr, centerX - 180, yPos, 0xFFFFFF);
        }

        int maxPages = Math.max(1, (int) Math.ceil(filteredStructures.size() / (double) itemsPerPage));
        gui.drawCenteredString(this.font, "Page " + (currentPage + 1) + " of " + maxPages, centerX, 5, 0xAAAAAA);
    }
}