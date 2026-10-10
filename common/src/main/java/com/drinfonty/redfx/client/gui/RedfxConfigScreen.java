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

        int buttonWidth = 230;
        int buttonHeight = 20;
        int x = (this.width - buttonWidth) / 2;
        int startY = this.height / 2 - 105;
        int colWidth = 112;
        int leftX = x;
        int rightX = x + 118;

        // Button 1: Toggle Blood Enabled
        Button bloodToggle = Button.builder(
            getBloodButtonMessage(config),
            btn -> {
                config.bloodEnabled = !config.bloodEnabled;
                btn.setMessage(getBloodButtonMessage(config));
            }
        ).bounds(leftX, startY, colWidth, buttonHeight).build();
        this.addRenderableWidget(bloodToggle);

        // Button 1b: Toggle Directional Blood Spray
        Button directionalToggle = Button.builder(
            getDirectionalButtonMessage(config),
            btn -> {
                config.directionalBlood = !config.directionalBlood;
                btn.setMessage(getDirectionalButtonMessage(config));
            }
        ).bounds(rightX, startY, colWidth, buttonHeight).build();
        this.addRenderableWidget(directionalToggle);

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
        ).bounds(leftX, startY + 25, colWidth, buttonHeight).build();
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
        ).bounds(rightX, startY + 25, colWidth, buttonHeight).build();
        this.addRenderableWidget(styleToggle);

        // Button 4: Toggle Splat Dust Particle
        Button splatDustToggle = Button.builder(
            getSplatDustButtonMessage(config),
            btn -> {
                config.enableSplatDust = !config.enableSplatDust;
                btn.setMessage(getSplatDustButtonMessage(config));
            }
        ).bounds(leftX, startY + 50, colWidth, buttonHeight).build();
        this.addRenderableWidget(splatDustToggle);

        // Button 5: Toggle Underwater Particle Type
        Button waterParticleToggle = Button.builder(
            getWaterParticleButtonMessage(config),
            btn -> {
                config.waterParticleType = config.waterParticleType.equals("CampfireSmoke") ? "Smoke" : "CampfireSmoke";
                btn.setMessage(getWaterParticleButtonMessage(config));
            }
        ).bounds(rightX, startY + 50, colWidth, buttonHeight).build();
        this.addRenderableWidget(waterParticleToggle);

        // Slider for Particle Size Scale
        AbstractSliderButton particleSizeSlider = new AbstractSliderButton(
            leftX, startY + 75, colWidth, buttonHeight,
            Component.empty(),
            (double) (config.particleSizeScale - 0.5f) / 1.5f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Drop Size: " + (Math.round(config.particleSizeScale * 10.0f) / 10.0f) + "x"));
            }

            @Override
            protected void applyValue() {
                config.particleSizeScale = 0.5f + (float) (this.value * 1.5f);
            }
        };
        this.addRenderableWidget(particleSizeSlider);

        // Slider for Splat Size Scale
        AbstractSliderButton splatSizeSlider = new AbstractSliderButton(
            rightX, startY + 75, colWidth, buttonHeight,
            Component.empty(),
            (double) (config.splatSizeScale - 0.5f) / 1.5f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Splat Size: " + (Math.round(config.splatSizeScale * 10.0f) / 10.0f) + "x"));
            }

            @Override
            protected void applyValue() {
                config.splatSizeScale = 0.5f + (float) (this.value * 1.5f);
            }
        };
        this.addRenderableWidget(splatSizeSlider);

        // Slider for Particle Lifetime
        AbstractSliderButton lifetimeSlider = new AbstractSliderButton(
            leftX, startY + 100, colWidth, buttonHeight,
            Component.empty(),
            (double) (config.particleLifetimeSeconds - 1) / 29.0
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Lifetime: " + config.particleLifetimeSeconds + "s"));
            }

            @Override
            protected void applyValue() {
                config.particleLifetimeSeconds = 1 + (int) Math.round(this.value * 29.0);
            }
        };
        this.addRenderableWidget(lifetimeSlider);

        // Slider for Color Saturation
        AbstractSliderButton saturationSlider = new AbstractSliderButton(
            rightX, startY + 100, colWidth, buttonHeight,
            Component.empty(),
            (double) (config.colorSaturation) / 2.0f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Saturation: " + (Math.round(config.colorSaturation * 10.0f) / 10.0f) + "x"));
            }

            @Override
            protected void applyValue() {
                config.colorSaturation = (float) (this.value * 2.0f);
            }
        };
        this.addRenderableWidget(saturationSlider);

        // Button: Toggle Soft Edges
        Button softEdgesToggle = Button.builder(
            getSoftEdgesButtonMessage(config),
            btn -> {
                config.translucentEdges = !config.translucentEdges;
                btn.setMessage(getSoftEdgesButtonMessage(config));
            }
        ).bounds(leftX, startY + 125, colWidth, buttonHeight).build();
        this.addRenderableWidget(softEdgesToggle);

        // Button: Toggle Edge Dripping
        Button edgeDripToggle = Button.builder(
            getEdgeDripButtonMessage(config),
            btn -> {
                config.dripOverEdges = !config.dripOverEdges;
                btn.setMessage(getEdgeDripButtonMessage(config));
            }
        ).bounds(rightX, startY + 125, colWidth, buttonHeight).build();
        this.addRenderableWidget(edgeDripToggle);

        // Button: Toggle Wall Dripping
        Button wallDripToggle = Button.builder(
            getWallDripsButtonMessage(config),
            btn -> {
                config.wallDripping = !config.wallDripping;
                btn.setMessage(getWallDripsButtonMessage(config));
            }
        ).bounds(leftX, startY + 150, colWidth, buttonHeight).build();
        this.addRenderableWidget(wallDripToggle);

        // Button: Toggle Gradual Splatter Bloom
        Button gradualToggle = Button.builder(
            getGradualSplatterButtonMessage(config),
            btn -> {
                config.gradualSplatter = !config.gradualSplatter;
                btn.setMessage(getGradualSplatterButtonMessage(config));
            }
        ).bounds(rightX, startY + 150, colWidth, buttonHeight).build();
        this.addRenderableWidget(gradualToggle);

        // Slider for Blood Opacity (10% to 100%)
        AbstractSliderButton opacitySlider = new AbstractSliderButton(
            leftX, startY + 175, colWidth, buttonHeight,
            Component.empty(),
            (double) (config.bloodOpacity - 0.1f) / 0.9f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Opacity: " + Math.round(config.bloodOpacity * 100.0f) + "%"));
            }

            @Override
            protected void applyValue() {
                config.bloodOpacity = 0.1f + (float) (this.value * 0.9f);
            }
        };
        this.addRenderableWidget(opacitySlider);

        // Button: Entity Blood Colors
        Button entityColorsButton = Button.builder(
            Component.literal("Entity Colors..."),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new EntityColorsScreen(this));
                }
            }
        ).bounds(rightX, startY + 175, colWidth, buttonHeight).build();
        this.addRenderableWidget(entityColorsButton);

        // Button: Done / Close
        Button doneButton = Button.builder(
            Component.literal("Done"),
            btn -> {
                config.save();
                this.onClose();
            }
        ).bounds(leftX, startY + 200, colWidth, buttonHeight).build();
        this.addRenderableWidget(doneButton);

        // Button: Reset Defaults
        Button resetButton = Button.builder(
            Component.literal("Reset"),
            btn -> {
                config.resetToDefaults();
                this.rebuildWidgets();
            }
        ).bounds(rightX, startY + 200, colWidth, buttonHeight).build();
        this.addRenderableWidget(resetButton);
    }

    private Component getWallDripsButtonMessage(RedfxConfig config) {
        return Component.literal("Wall Drips: " + (config.wallDripping ? "ON" : "OFF"));
    }

    private Component getGradualSplatterButtonMessage(RedfxConfig config) {
        return Component.literal("Bloom: " + (config.gradualSplatter ? "ON" : "OFF"));
    }


    private Component getSoftEdgesButtonMessage(RedfxConfig config) {
        return Component.literal("Soft Edges: " + (config.translucentEdges ? "ON" : "OFF"));
    }

    private Component getEdgeDripButtonMessage(RedfxConfig config) {
        return Component.literal("Edge Dripping: " + (config.dripOverEdges ? "ON" : "OFF"));
    }

    private Component getBloodButtonMessage(RedfxConfig config) {
        return Component.literal("Blood: " + (config.bloodEnabled ? "ON" : "OFF"));
    }

    private Component getDirectionalButtonMessage(RedfxConfig config) {
        return Component.literal("Directional: " + (config.directionalBlood ? "ON" : "OFF"));
    }

    private Component getAmountButtonMessage(RedfxConfig config) {
        return Component.literal("Amount: " + config.particleAmount);
    }

    private Component getStyleButtonMessage(RedfxConfig config) {
        String displayName = switch (config.particleType) {
            case "TNT" -> "Shred";
            case "RedPoof" -> "Spray";
            case "RedWool" -> "Default";
            default -> "Default";
        };
        return Component.literal("Style: " + displayName);
    }

    private Component getSplatDustButtonMessage(RedfxConfig config) {
        return Component.literal("Splat Dust: " + (config.enableSplatDust ? "ON" : "OFF"));
    }

    private Component getWaterParticleButtonMessage(RedfxConfig config) {
        String displayName = config.waterParticleType.equals("CampfireSmoke") ? "Big" : "Small";
        return Component.literal("Underwater: " + displayName);
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
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
