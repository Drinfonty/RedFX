package com.drinfonty.redfx.client.gui;

import com.drinfonty.redfx.config.RedfxConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RedfxConfigScreen extends Screen {
    private final Screen parent;

    public RedfxConfigScreen(Screen parent) {
        super(Component.literal("RedFX Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RedfxConfig config = RedfxConfig.get();

        int buttonWidth = 220;
        int buttonHeight = 20;
        int x = (this.width - buttonWidth) / 2;
        int startY = this.height / 2 - 50;

        // Button 1: Toggle Blood Enabled
        Button bloodToggle = Button.builder(
            getBloodButtonMessage(config),
            btn -> {
                config.bloodEnabled = !config.bloodEnabled;
                btn.setMessage(getBloodButtonMessage(config));
            }
        ).bounds(x, startY, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(bloodToggle);

        // Button 2: Toggle Particle Amount Multiplier
        Button amountToggle = Button.builder(
            getAmountButtonMessage(config),
            btn -> {
                config.particleAmount = switch (config.particleAmount) {
                    case "Low" -> "Medium";
                    case "Medium" -> "High";
                    case "High" -> "Ultra";
                    default -> "Low";
                };
                btn.setMessage(getAmountButtonMessage(config));
            }
        ).bounds(x, startY + 25, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(amountToggle);

        // Button 3: Toggle Particle Style (Redstone Block vs Red Poof)
        Button styleToggle = Button.builder(
            getStyleButtonMessage(config),
            btn -> {
                config.particleType = config.particleType.equals("RedstoneBlock") ? "RedPoof" : "RedstoneBlock";
                btn.setMessage(getStyleButtonMessage(config));
            }
        ).bounds(x, startY + 50, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(styleToggle);

        // Button 4: Done / Close
        Button doneButton = Button.builder(
            Component.literal("Done"),
            btn -> {
                config.save();
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parent);
                }
            }
        ).bounds(x, startY + 85, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(doneButton);
    }

    private Component getBloodButtonMessage(RedfxConfig config) {
        return Component.literal("Blood Effects: " + (config.bloodEnabled ? "ON" : "OFF"));
    }

    private Component getAmountButtonMessage(RedfxConfig config) {
        return Component.literal("Particle Amount: " + config.particleAmount);
    }

    private Component getStyleButtonMessage(RedfxConfig config) {
        String displayName = config.particleType.equals("RedstoneBlock") ? "Redstone Block" : "Red Poof";
        return Component.literal("Particle Style: " + displayName);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Draw background
        this.extractTransparentBackground(extractor);
        
        // Draw title
        extractor.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw widgets (calls super to render buttons)
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        RedfxConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
