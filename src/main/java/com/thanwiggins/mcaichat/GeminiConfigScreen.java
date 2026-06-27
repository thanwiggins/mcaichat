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
    private EditBox blacklistBox;

    public GeminiConfigScreen(Screen previous) {
        super(Component.literal("Gemini API Configuration"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 60; // Shifted up more to make room for three boxes

        // Create the API Key box
        this.apiKeyBox = new EditBox(this.font, x, y, 200, 20, Component.literal("API Key"));
        this.apiKeyBox.setMaxLength(200);
        this.apiKeyBox.setValue(Config.API_KEY.get());
        this.addRenderableWidget(this.apiKeyBox);

        // Create the Whitelist box
        this.whitelistBox = new EditBox(this.font, x, y + 40, 200, 20, Component.literal("Whitelist"));
        this.whitelistBox.setMaxLength(500);
        this.whitelistBox.setValue(Config.WHITELIST_ENTITIES.get());
        this.addRenderableWidget(this.whitelistBox);
        
        // Create the Blacklist box
        this.blacklistBox = new EditBox(this.font, x, y + 80, 200, 20, Component.literal("Blacklist"));
        this.blacklistBox.setMaxLength(500);
        this.blacklistBox.setValue(Config.BLACKLIST_ENTITIES.get());
        this.addRenderableWidget(this.blacklistBox);

        // Create the Save button
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.WHITELIST_ENTITIES.set(this.whitelistBox.getValue());
            Config.BLACKLIST_ENTITIES.set(this.blacklistBox.getValue());
            this.minecraft.setScreen(this.previous);
        }).bounds(x, y + 115, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        guiGraphics.drawString(this.font, Component.literal("Paste your Gemini API Key:"), this.width / 2 - 100, this.height / 2 - 75, 0xA0A0A0);
        guiGraphics.drawString(this.font, Component.literal("Whitelisted Entities (comma separated):"), this.width / 2 - 100, this.height / 2 - 35, 0xA0A0A0);
        guiGraphics.drawString(this.font, Component.literal("Blacklisted Entities (comma separated):"), this.width / 2 - 100, this.height / 2 + 5, 0xA0A0A0);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}