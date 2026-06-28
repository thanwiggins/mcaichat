package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

public class GeminiConfigScreen extends Screen {
    private final Screen previous;
    private EditBox apiKeyBox;
    private EditBox whitelistBox;

    public GeminiConfigScreen(Screen previous) {
        super(Component.literal("MC-AI Chat Configuration"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 60; 

        // API Key box
        this.apiKeyBox = new EditBox(this.font, x, y, 200, 20, Component.literal("API Key"));
        this.apiKeyBox.setMaxLength(200);
        this.apiKeyBox.setValue(Config.API_KEY.get());
        this.addRenderableWidget(this.apiKeyBox);

        // Whitelist box (NPCs you can talk to)
        this.whitelistBox = new EditBox(this.font, x, y + 40, 200, 20, Component.literal("Whitelist"));
        this.whitelistBox.setMaxLength(500);
        this.whitelistBox.setValue(Config.WHITELIST_ENTITIES.get());
        this.addRenderableWidget(this.whitelistBox);

        // NEW: Creature Category Config Button
        this.addRenderableWidget(Button.builder(Component.literal("Creature Categories"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.WHITELIST_ENTITIES.set(this.whitelistBox.getValue());
            this.minecraft.setScreen(new EntityConfigScreen(this));
        }).bounds(x, y + 80, 95, 20).build());

        // NEW: Structure Category Config Button
        this.addRenderableWidget(Button.builder(Component.literal("Structure Categories"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.WHITELIST_ENTITIES.set(this.whitelistBox.getValue());
            this.minecraft.setScreen(new StructureConfigScreen(this));
        }).bounds(x + 105, y + 80, 95, 20).build());

        // Save button
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.WHITELIST_ENTITIES.set(this.whitelistBox.getValue());
            this.minecraft.setScreen(this.previous);
        }).bounds(x, y + 115, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        guiGraphics.drawString(this.font, Component.literal("Paste your Gemini API Key:"), this.width / 2 - 100, this.height / 2 - 75, 0xA0A0A0);
        guiGraphics.drawString(this.font, Component.literal("NPC Chat Whitelist (comma separated):"), this.width / 2 - 100, this.height / 2 - 35, 0xA0A0A0);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}