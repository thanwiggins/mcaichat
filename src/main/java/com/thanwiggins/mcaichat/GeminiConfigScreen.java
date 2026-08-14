package com.thanwiggins.mcaichat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

public class GeminiConfigScreen extends Screen {
    private final Screen previous;
    private EditBox apiKeyBox;
    private EditBox displayNameBox;
    private EditBox descriptionBox;

    public GeminiConfigScreen(Screen previous) {
        super(Component.literal("MC-AI Chat Configuration"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 40;

        // API Key box
        this.apiKeyBox = new EditBox(this.font, x, y, 200, 20, Component.literal("API Key"));
        this.apiKeyBox.setMaxLength(200);
        this.apiKeyBox.setValue(Config.API_KEY.get());
        this.addRenderableWidget(this.apiKeyBox);

        // Player display name / description boxes
        this.displayNameBox = new EditBox(this.font, x, y + 38, 200, 20, Component.literal("Display Name"));
        this.displayNameBox.setMaxLength(64);
        this.displayNameBox.setValue(Config.PLAYER_DISPLAY_NAME.get());
        this.addRenderableWidget(this.displayNameBox);

        this.descriptionBox = new EditBox(this.font, x, y + 76, 200, 20, Component.literal("Description"));
        this.descriptionBox.setMaxLength(150);
        this.descriptionBox.setValue(Config.PLAYER_DESCRIPTION.get());
        this.addRenderableWidget(this.descriptionBox);

        // Creature Category Config Button
        this.addRenderableWidget(Button.builder(Component.literal("Creature Config"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.PLAYER_DISPLAY_NAME.set(this.displayNameBox.getValue());
            Config.PLAYER_DESCRIPTION.set(this.descriptionBox.getValue());
            this.minecraft.setScreen(new EntityConfigScreen(this));
        }).bounds(x - 20, y + 112, 115, 20).build());

        // Structure Category Config Button
        this.addRenderableWidget(Button.builder(Component.literal("Structure Config"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.PLAYER_DISPLAY_NAME.set(this.displayNameBox.getValue());
            Config.PLAYER_DESCRIPTION.set(this.descriptionBox.getValue());
            this.minecraft.setScreen(new StructureConfigScreen(this));
        }).bounds(x + 105, y + 112, 115, 20).build());

        // Save button
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), button -> {
            Config.API_KEY.set(this.apiKeyBox.getValue());
            Config.PLAYER_DISPLAY_NAME.set(this.displayNameBox.getValue());
            Config.PLAYER_DESCRIPTION.set(this.descriptionBox.getValue());
            this.minecraft.setScreen(this.previous);
        }).bounds(x, y + 147, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Anchored above the "Paste your Gemini API Key" label (not the title) so it never
        // collides with the label regardless of window height.
        int labelY = this.height / 2 - 55;
        int warnY = labelY - 33;
        int warnColor = 0xFFAA00;
        guiGraphics.drawCenteredString(this.font, Component.literal("This mod cannot guarantee your API key or messages are kept secure."), this.width / 2, warnY, warnColor);
        guiGraphics.drawCenteredString(this.font, Component.literal("Do not share sensitive or personal information in chat."), this.width / 2, warnY + 10, warnColor);
        guiGraphics.drawCenteredString(this.font, Component.literal("Review your API key's usage terms, quotas, and limits."), this.width / 2, warnY + 20, warnColor);

        guiGraphics.drawString(this.font, Component.literal("Paste your Gemini API Key:"), this.width / 2 - 100, labelY, 0xA0A0A0);

        int fieldY = this.height / 2 - 40;
        guiGraphics.drawString(this.font, Component.literal("Name NPCs know you by (optional):"), this.width / 2 - 100, fieldY + 26, 0xA0A0A0);
        guiGraphics.drawString(this.font, Component.literal("Description NPCs can reference (optional):"), this.width / 2 - 100, fieldY + 64, 0xA0A0A0);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}