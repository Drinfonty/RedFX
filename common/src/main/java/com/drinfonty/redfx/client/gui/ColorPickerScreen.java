package com.drinfonty.redfx.client.gui;

import com.drinfonty.redfx.config.RedfxConfig;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final String entityId;
    private final String initialHex;
    private final int initialColorInt;
    private final Consumer<String> onApply;

    private int red;
    private int green;
    private int blue;
    private String currentHex;
    private boolean isUpdating = false;

    private ColorSlider redSlider;
    private ColorSlider greenSlider;
    private ColorSlider blueSlider;
    private EditBox hexBox;

    public record PresetSwatch(String name, String hex) {
        public int colorInt() {
            return RedfxConfig.parseColorInt(hex, 0xFF0000);
        }
    }

    public static final List<PresetSwatch> SWATCHES = List.of(
        new PresetSwatch("Pure Red", "#FF0000"),
        new PresetSwatch("Blood Red", "#FF0D0D"),
        new PresetSwatch("Crimson", "#8B0000"),
        new PresetSwatch("Rust Brown", "#85522E"),
        new PresetSwatch("Blaze Orange", "#E6B21A"),
        new PresetSwatch("Bright Orange", "#FF7700"),

        new PresetSwatch("Gold", "#FFD700"),
        new PresetSwatch("Yellow", "#FFE600"),
        new PresetSwatch("Slime Green", "#33E633"),
        new PresetSwatch("Acid Lime", "#00FF00"),
        new PresetSwatch("Forest Green", "#1B4D1B"),
        new PresetSwatch("Bogged Olive", "#4D6633"),

        new PresetSwatch("Turquoise", "#00CED1"),
        new PresetSwatch("Sculk Cyan", "#0D4DB2"),
        new PresetSwatch("Sky Blue", "#1E90FF"),
        new PresetSwatch("Royal Blue", "#0033CC"),
        new PresetSwatch("Ender Purple", "#991ACC"),
        new PresetSwatch("Deep Violet", "#660099"),

        new PresetSwatch("Hot Pink", "#FF69B4"),
        new PresetSwatch("Magenta", "#FF00FF"),
        new PresetSwatch("Bone White", "#E6E6E6"),
        new PresetSwatch("Light Gray", "#A0A0A0"),
        new PresetSwatch("Dark Gray", "#555555"),
        new PresetSwatch("Wither Dark", "#262626")
    );

    public ColorPickerScreen(Screen parent, String entityId, String currentHex, Consumer<String> onApply) {
        super(Component.literal("Blood Color Picker"));
        this.parent = parent;
        this.entityId = entityId;
        this.initialHex = currentHex.startsWith("#") ? currentHex.toUpperCase() : "#" + currentHex.toUpperCase();
        this.initialColorInt = RedfxConfig.parseColorInt(this.initialHex, 0xFF0000);
        this.onApply = onApply;

        int c = this.initialColorInt;
        this.red = (c >> 16) & 0xFF;
        this.green = (c >> 8) & 0xFF;
        this.blue = c & 0xFF;
        this.currentHex = this.initialHex;
    }

    @Override
    protected void init() {
        int modalWidth = 280;
        int leftX = (this.width - modalWidth) / 2;
        int startY = Math.max(8, (this.height - 232) / 2);

        // Top Preview row: Hex EditBox
        int previewY = startY + 28;
        hexBox = new EditBox(this.font, leftX + 198, previewY, 82, 22, Component.literal("Hex"));
        hexBox.setValue(this.currentHex);
        hexBox.setMaxLength(7);
        hexBox.setResponder(val -> {
            if (isUpdating) return;
            float[] rgb = RedfxConfig.parseColor(val);
            if (rgb != null) {
                hexBox.setTextColor(0xFFFFFF);
                int color = RedfxConfig.parseColorInt(val, 0xFF0000);
                this.red = (color >> 16) & 0xFF;
                this.green = (color >> 8) & 0xFF;
                this.blue = color & 0xFF;
                this.currentHex = val.startsWith("#") ? val.toUpperCase() : "#" + val.toUpperCase();
                updateSlidersFromRgb();
            } else {
                hexBox.setTextColor(0xFF5555);
            }
        });
        this.addRenderableWidget(hexBox);

        // RGB Sliders
        redSlider = new ColorSlider(leftX, startY + 56, modalWidth, 18, "Red", 0xFF6666, this.red, val -> {
            this.red = val;
            onRgbChanged();
        });
        this.addRenderableWidget(redSlider);

        greenSlider = new ColorSlider(leftX, startY + 76, modalWidth, 18, "Green", 0x66FF66, this.green, val -> {
            this.green = val;
            onRgbChanged();
        });
        this.addRenderableWidget(greenSlider);

        blueSlider = new ColorSlider(leftX, startY + 96, modalWidth, 18, "Blue", 0x6699FF, this.blue, val -> {
            this.blue = val;
            onRgbChanged();
        });
        this.addRenderableWidget(blueSlider);

        // Quick Swatches Grid (6 cols x 4 rows)
        int swatchW = 44;
        int swatchH = 15;
        int spacing = 3;
        int gridStartY = startY + 130;

        for (int i = 0; i < SWATCHES.size(); i++) {
            PresetSwatch swatch = SWATCHES.get(i);
            int col = i % 6;
            int row = i / 6;
            int bx = leftX + col * (swatchW + spacing);
            int by = gridStartY + row * (swatchH + spacing);

            Button swatchBtn = Button.builder(
                Component.literal("■").withColor(swatch.colorInt()),
                btn -> {
                    setColor(swatch.hex());
                }
            ).bounds(bx, by, swatchW, swatchH).tooltip(
                Tooltip.create(Component.literal(swatch.name() + " (" + swatch.hex() + ")"))
            ).build();
            this.addRenderableWidget(swatchBtn);
        }

        // Bottom action buttons: Apply, Reset, Cancel
        int btnY = startY + 206;
        Button applyBtn = Button.builder(
            Component.literal("Apply"),
            btn -> {
                if (onApply != null) {
                    onApply.accept(this.currentHex);
                }
                this.onClose();
            }
        ).bounds(leftX, btnY, 88, 20).build();
        this.addRenderableWidget(applyBtn);

        Button resetBtn = Button.builder(
            Component.literal("Reset"),
            btn -> {
                setColor(this.initialHex);
            }
        ).bounds(leftX + 94, btnY, 88, 20).tooltip(
            Tooltip.create(Component.literal("Revert to initial color: " + this.initialHex))
        ).build();
        this.addRenderableWidget(resetBtn);

        Button cancelBtn = Button.builder(
            Component.literal("Cancel"),
            btn -> {
                this.onClose();
            }
        ).bounds(leftX + 188, btnY, 92, 20).build();
        this.addRenderableWidget(cancelBtn);
    }

    private void onRgbChanged() {
        this.currentHex = String.format("#%02X%02X%02X", this.red, this.green, this.blue);
        if (hexBox != null && !isUpdating) {
            isUpdating = true;
            hexBox.setValue(this.currentHex);
            hexBox.setTextColor(0xFFFFFF);
            isUpdating = false;
        }
    }

    private void updateSlidersFromRgb() {
        isUpdating = true;
        if (redSlider != null) redSlider.setSliderValue(this.red);
        if (greenSlider != null) greenSlider.setSliderValue(this.green);
        if (blueSlider != null) blueSlider.setSliderValue(this.blue);
        isUpdating = false;
    }

    public void setColor(String hex) {
        int color = RedfxConfig.parseColorInt(hex, 0xFF0000);
        this.red = (color >> 16) & 0xFF;
        this.green = (color >> 8) & 0xFF;
        this.blue = color & 0xFF;
        this.currentHex = hex.startsWith("#") ? hex.toUpperCase() : "#" + hex.toUpperCase();
        updateSlidersFromRgb();
        if (hexBox != null) {
            isUpdating = true;
            hexBox.setValue(this.currentHex);
            hexBox.setTextColor(0xFFFFFF);
            isUpdating = false;
        }
    }

    private int getCurrentColorInt() {
        return 0xFF000000 | (this.red << 16) | (this.green << 8) | this.blue;
    }

    private int getContrastColor(int colorInt) {
        int r = (colorInt >> 16) & 0xFF;
        int g = (colorInt >> 8) & 0xFF;
        int b = colorInt & 0xFF;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return lum > 0.6 ? 0x000000 : 0xFFFFFF;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);

        int modalWidth = 280;
        int leftX = (this.width - modalWidth) / 2;
        int startY = Math.max(8, (this.height - 232) / 2);

        // Title and entity subtitle
        graphics.drawCenteredString(this.font, this.title, this.width / 2, startY + 2, 0xFFFFFF);
        String sub = entityId.startsWith("minecraft:") ? entityId.substring(10) : entityId;
        graphics.drawCenteredString(this.font, Component.literal("(" + sub + ")"), this.width / 2, startY + 14, 0xAAAAAA);

        // Preview Swatches
        int previewY = startY + 28;

        // Old color box
        int oldX = leftX + 4;
        int oldW = 60;
        int oldH = 22;
        graphics.fill(oldX - 1, previewY - 1, oldX + oldW + 1, previewY + oldH + 1, 0xFF888888);
        graphics.fill(oldX, previewY, oldX + oldW, previewY + oldH, 0xFF000000 | this.initialColorInt);
        graphics.drawCenteredString(this.font, "Old", oldX + oldW / 2, previewY + 7, getContrastColor(this.initialColorInt));

        // Arrow
        graphics.drawCenteredString(this.font, "➔", leftX + 75, previewY + 7, 0xCCCCCC);

        // New color box
        int newX = leftX + 88;
        int newW = 100;
        int newH = 22;
        int currentInt = getCurrentColorInt();
        graphics.fill(newX - 1, previewY - 1, newX + newW + 1, previewY + newH + 1, 0xFFFFFFFF);
        graphics.fill(newX, previewY, newX + newW, previewY + newH, currentInt);
        graphics.drawCenteredString(this.font, this.currentHex, newX + newW / 2, previewY + 7, getContrastColor(currentInt));

        // Presets header
        graphics.drawString(this.font, "Quick Presets:", leftX, startY + 118, 0xDDDDDD);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private class ColorSlider extends AbstractSliderButton {
        private final String label;
        private final int textColor;
        private final IntConsumer onValueChange;

        public ColorSlider(int x, int y, int width, int height, String label, int textColor, int initialValue, IntConsumer onValueChange) {
            super(x, y, width, height, Component.empty(), initialValue / 255.0);
            this.label = label;
            this.textColor = textColor;
            this.onValueChange = onValueChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int val = (int) Math.round(this.value * 255.0);
            this.setMessage(Component.literal(label + ": " + val).withColor(textColor));
        }

        @Override
        protected void applyValue() {
            int val = (int) Math.round(this.value * 255.0);
            onValueChange.accept(val);
        }

        public void setSliderValue(int intVal) {
            this.value = Math.max(0.0, Math.min(1.0, intVal / 255.0));
            this.updateMessage();
        }
    }
}
