package com.drinfonty.redfx.client.gui;

import com.drinfonty.redfx.config.RedfxConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
        int startY = this.height / 2 - 70;

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

        // Button 3: Toggle Particle Style
        Button styleToggle = Button.builder(
            getStyleButtonMessage(config),
            btn -> {
                config.particleType = switch (config.particleType) {
                    case "RedWool" -> "TNT";
                    case "TNT" -> "RedPoof";
                    default -> "RedWool";
                };
                btn.setMessage(getStyleButtonMessage(config));
            }
        ).bounds(x, startY + 50, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(styleToggle);

        // Button 4: Toggle Splat Decal Texture
        Button splatToggle = Button.builder(
            getSplatButtonMessage(config),
            btn -> {
                config.useSplatTexture = !config.useSplatTexture;
                btn.setMessage(getSplatButtonMessage(config));
            }
        ).bounds(x, startY + 75, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(splatToggle);

        // Button 5: Slider for Particle Lifetime
        AbstractSliderButton lifetimeSlider = new AbstractSliderButton(
            x, startY + 100, buttonWidth, buttonHeight,
            Component.empty(),
            (double) (config.particleLifetimeSeconds - 1) / 29.0
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Landed Lifetime: " + config.particleLifetimeSeconds + "s"));
            }

            @Override
            protected void applyValue() {
                config.particleLifetimeSeconds = 1 + (int) Math.round(this.value * 29.0);
            }
        };
        this.addRenderableWidget(lifetimeSlider);

        // Button 6: Done / Close
        Button doneButton = Button.builder(
            Component.literal("Done"),
            btn -> {
                config.save();
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parent);
                }
            }
        ).bounds(x, startY + 130, buttonWidth, buttonHeight).build();
        this.addRenderableWidget(doneButton);
    }

    private Component getBloodButtonMessage(RedfxConfig config) {
        return Component.literal("Blood Effects: " + (config.bloodEnabled ? "ON" : "OFF"));
    }

    private Component getAmountButtonMessage(RedfxConfig config) {
        return Component.literal("Particle Amount: " + config.particleAmount);
    }

    private Component getStyleButtonMessage(RedfxConfig config) {
        String displayName = switch (config.particleType) {
            case "TNT" -> "TNT Block";
            case "RedPoof" -> "Red Poof";
            case "RedWool" -> "Red Wool";
            default -> "Red Wool";
        };
        return Component.literal("Particle Style: " + displayName);
    }

    private Component getSplatButtonMessage(RedfxConfig config) {
        return Component.literal("Splat Texture: " + (config.useSplatTexture ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Draw background
        this.extractTransparentBackground(extractor);
        
        // Draw title
        extractor.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw widgets (calls super to render buttons and slider)
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
