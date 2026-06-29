package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import java.util.*;
import java.util.stream.Collectors;

public class EntityConfigScreen extends Screen {
    private final Screen previous;
    private EditBox searchBox;
    private int currentPage = 0;
    private final int itemsPerPage = 6;
    private List<String> allEntities;
    private List<String> filteredEntities;
    
    public EntityConfigScreen(Screen previous) {
        super(Component.literal("Configure Creatures & Whitelist"));
        this.previous = previous;
        this.allEntities = ForgeRegistries.ENTITY_TYPES.getKeys().stream()
            .map(ResourceLocation::toString)
            .sorted()
            .collect(Collectors.toList());
        this.filteredEntities = new ArrayList<>(allEntities);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;
        int yStart = 40;

        this.searchBox = new EditBox(this.font, centerX - 100, 15, 200, 20, Component.literal("Search"));
        this.searchBox.setResponder(text -> {
            this.filteredEntities = allEntities.stream()
                .filter(e -> e.toLowerCase().contains(text.toLowerCase()))
                .collect(Collectors.toList());
            this.currentPage = 0;
            this.init(); 
        });
        this.addRenderableWidget(this.searchBox);

        int maxPages = Math.max(1, (int) Math.ceil(filteredEntities.size() / (double) itemsPerPage));
        
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (currentPage > 0) { currentPage--; this.init(); }
        }).bounds(centerX - 130, 15, 20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (currentPage < maxPages - 1) { currentPage++; this.init(); }
        }).bounds(centerX + 110, 15, 20, 20).build());

        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredEntities.size());

        for (int i = startIdx; i < endIdx; i++) {
            String entityId = filteredEntities.get(i);
            int yPos = yStart + ((i - startIdx) * 25);
            
            // --- WHITELIST TOGGLE BUTTON ---
            boolean isWhitelisted = Config.isInList(Config.WHITELIST_ENTITIES, entityId);
            Button whitelistBtn = Button.builder(Component.literal(isWhitelisted ? "Chat: ON" : "Chat: OFF"), b -> {
                Config.setCategory(Config.WHITELIST_ENTITIES, entityId, !isWhitelisted);
                this.init();
            }).bounds(centerX - 35, yPos, 65, 20).build();
            this.addRenderableWidget(whitelistBtn);

            // --- AI CATEGORY CYCLE BUTTON ---
            String currentCat = "";
            if (Config.isInList(Config.BLACKLIST_ENTITIES, entityId)) currentCat = "Ignored";
            else if (Config.isInList(Config.CUSTOM_MONSTERS, entityId)) currentCat = "Monster";
            else if (Config.isInList(Config.CUSTOM_WILDLIFE, entityId)) currentCat = "Wildlife";
            else {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
                boolean isMonster = type != null && (type.getCategory() == MobCategory.MONSTER || Enemy.class.isAssignableFrom(type.getBaseClass()));
                currentCat = "Def. (" + (isMonster ? "Monster" : "Wild.") + ")";
            }

            Button cycleBtn = Button.builder(Component.literal(currentCat), b -> {
                cycleEntityCategory(entityId);
                this.init(); 
            }).bounds(centerX + 35, yPos, 105, 20).build();
            this.addRenderableWidget(cycleBtn);
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.minecraft.setScreen(this.previous);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    private void cycleEntityCategory(String id) {
        boolean isIgnored = Config.isInList(Config.BLACKLIST_ENTITIES, id);
        boolean isMonster = Config.isInList(Config.CUSTOM_MONSTERS, id);
        boolean isWildlife = Config.isInList(Config.CUSTOM_WILDLIFE, id);

        Config.setCategory(Config.BLACKLIST_ENTITIES, id, false);
        Config.setCategory(Config.CUSTOM_MONSTERS, id, false);
        Config.setCategory(Config.CUSTOM_WILDLIFE, id, false);

        if (!isIgnored && !isMonster && !isWildlife) {
            Config.setCategory(Config.CUSTOM_MONSTERS, id, true);
        } else if (isMonster) {
            Config.setCategory(Config.CUSTOM_WILDLIFE, id, true);
        } else if (isWildlife) {
            Config.setCategory(Config.BLACKLIST_ENTITIES, id, true);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int yStart = 40;
        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredEntities.size());

        for (int i = startIdx; i < endIdx; i++) {
            String entityId = filteredEntities.get(i);
            int yPos = yStart + ((i - startIdx) * 25) + 6;
            
            String displayStr = entityId;
            if (this.font.width(displayStr) > 105) {
                displayStr = this.font.plainSubstrByWidth(displayStr, 95) + "...";
            }
            gui.drawString(this.font, displayStr, centerX - 145, yPos, 0xFFFFFF);
        }

        int maxPages = Math.max(1, (int) Math.ceil(filteredEntities.size() / (double) itemsPerPage));
        gui.drawCenteredString(this.font, "Page " + (currentPage + 1) + " of " + maxPages, centerX, 5, 0xAAAAAA);
    }
}