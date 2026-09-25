package com.drinfonty.redfx.client.gui;

import com.drinfonty.redfx.config.RedfxConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class EntityColorsScreen extends Screen {
    private final Screen parent;
    private int page = 0;
    private static final int ENTRIES_PER_PAGE = 5;

    private EditBox searchBox;
    private final List<AbstractWidget> entryWidgets = new ArrayList<>();
    private final List<String> registeredEntityIds = new ArrayList<>();
    private final List<String> currentMatches = new ArrayList<>();
    private int suggestionIndex = 0;

    public record ColorPreset(String name, String hex, int colorInt) {
        public ColorPreset(String name, String hex) {
            this(name, hex, RedfxConfig.parseColorInt(hex, 0xFF0000));
        }
    }

    public static final List<ColorPreset> PRESETS = List.of(
        new ColorPreset("Blood Red", "#FF0D0D"),
        new ColorPreset("Slime Green", "#33E633"),
        new ColorPreset("Blaze Orange", "#E6B21A"),
        new ColorPreset("Ender Purple", "#991ACC"),
        new ColorPreset("Sculk Cyan", "#0D4DB2"),
        new ColorPreset("Bone White", "#E6E6E6"),
        new ColorPreset("Skeleton Brown", "#85522E"),
        new ColorPreset("Bogged Olive", "#4D6633"),
        new ColorPreset("Wither Dark", "#262626"),
        new ColorPreset("Bright Yellow", "#FFE600"),
        new ColorPreset("Sky Blue", "#1E90FF"),
        new ColorPreset("Hot Pink", "#FF69B4")
    );

    public EntityColorsScreen(Screen parent) {
        super(Component.literal("Entity Blood Colors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (registeredEntityIds.isEmpty()) {
            BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(Object::toString)
                .sorted()
                .forEach(registeredEntityIds::add);
        }

        entryWidgets.clear();

        int rowWidth = 290;
        int leftX = (this.width - rowWidth) / 2;
        int startY = Math.max(12, (this.height - 225) / 2);

        // Top Row: Search/Add box (width 192), Clear button (width 20), Add button (width 72)
        searchBox = new EditBox(this.font, leftX, startY + 16, 192, 20, Component.literal("Search or Add"));
        searchBox.setHint(Component.literal("Search / add (e.g. spider)..."));
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);

        Button clearBtn = Button.builder(
            Component.literal("x"),
            btn -> {
                searchBox.setValue("");
            }
        ).bounds(leftX + 195, startY + 16, 20, 20).tooltip(
            Tooltip.create(Component.literal("Clear search / suggestions"))
        ).build();
        this.addRenderableWidget(clearBtn);

        Button addBtn = Button.builder(
            Component.literal("+ Add"),
            btn -> {
                addCurrentEntity();
            }
        ).bounds(leftX + 218, startY + 16, 72, 20).build();
        this.addRenderableWidget(addBtn);

        // Bottom action buttons: Done and Reset All (fixed)
        int bottomY = startY + 192;
        Button doneBtn = Button.builder(
            Component.literal("Done"),
            btn -> {
                RedfxConfig.get().save();
                this.onClose();
            }
        ).bounds(leftX + 30, bottomY, 110, 20).build();
        this.addRenderableWidget(doneBtn);

        Button resetBtn = Button.builder(
            Component.literal("Reset All"),
            btn -> {
                RedfxConfig.get().initDefaultEntityColors();
                page = 0;
                searchBox.setValue("");
                refreshEntries();
            }
        ).bounds(leftX + 150, bottomY, 110, 20).build();
        this.addRenderableWidget(resetBtn);

        refreshEntries();
    }

    private void onSearchChanged(String text) {
        String query = text.trim().toLowerCase();
        currentMatches.clear();
        suggestionIndex = 0;
        page = 0;

        if (query.isEmpty()) {
            searchBox.setSuggestion(null);
        } else {
            List<String> prefixMatches = new ArrayList<>();
            List<String> containsMatches = new ArrayList<>();

            for (String id : registeredEntityIds) {
                String shortName = id.startsWith("minecraft:") ? id.substring(10) : id;
                if (shortName.startsWith(query) || id.startsWith(query)) {
                    prefixMatches.add(id);
                } else if (shortName.contains(query) || id.contains(query)) {
                    containsMatches.add(id);
                }
            }
            currentMatches.addAll(prefixMatches);
            currentMatches.addAll(containsMatches);

            // In-line ghost text completion for top prefix match
            if (!prefixMatches.isEmpty()) {
                String top = prefixMatches.get(0);
                String display = top.startsWith("minecraft:") && !query.startsWith("minecraft:") ? top.substring(10) : top;
                if (display.startsWith(query) && display.length() > query.length()) {
                    searchBox.setSuggestion(display.substring(query.length()));
                } else {
                    searchBox.setSuggestion(null);
                }
            } else {
                searchBox.setSuggestion(null);
            }
        }

        refreshEntries();
    }

    private void clearEntryWidgets() {
        for (AbstractWidget w : entryWidgets) {
            this.removeWidget(w);
        }
        entryWidgets.clear();
    }

    private <T extends AbstractWidget> T addEntryWidget(T widget) {
        entryWidgets.add(widget);
        return this.addRenderableWidget(widget);
    }

    private void refreshEntries() {
        clearEntryWidgets();
        RedfxConfig config = RedfxConfig.get();

        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        boolean isSearching = !query.isEmpty();

        List<String> displayItems;
        if (isSearching) {
            displayItems = new ArrayList<>(currentMatches);
            String normalizedQuery = query.contains(":") ? query : "minecraft:" + query;
            if (!displayItems.contains(normalizedQuery)) {
                displayItems.add(0, normalizedQuery);
            }
        } else {
            displayItems = new ArrayList<>(config.entityBloodColors.keySet());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) displayItems.size() / ENTRIES_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int rowWidth = 290;
        int leftX = (this.width - rowWidth) / 2;
        int startY = Math.max(12, (this.height - 225) / 2);

        int startIndex = page * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, displayItems.size());
        int entryY = startY + 42;

        for (int i = startIndex; i < endIndex; i++) {
            final String entityId = displayItems.get(i);
            boolean isConfigured = config.entityBloodColors.containsKey(entityId);
            String currentHex = config.entityBloodColors.getOrDefault(
                entityId, config.entityBloodColors.getOrDefault("default", "#FF0D0D")
            );

            // 1. Entity label
            String displayName = entityId.startsWith("minecraft:") ? entityId.substring(10) : entityId;
            StringWidget nameWidget = new StringWidget(leftX, entryY, 105, 20, Component.literal(displayName), this.font);
            nameWidget.setTooltip(Tooltip.create(Component.literal(entityId)));
            addEntryWidget(nameWidget);

            if (isConfigured) {
                // 2. Hex EditBox
                EditBox hexBox = new EditBox(this.font, leftX + 109, entryY, 55, 20, Component.literal("Hex"));
                hexBox.setValue(currentHex);
                hexBox.setMaxLength(7);
                addEntryWidget(hexBox);

                // 3. Preset cycle button
                final Button[] presetBtnRef = new Button[1];
                Button presetBtn = Button.builder(
                    getPresetButtonMessage(currentHex),
                    btn -> {
                        String hexNow = config.entityBloodColors.getOrDefault(entityId, "#FF0D0D");
                        String nextHex = getNextPresetHex(hexNow);
                        config.entityBloodColors.put(entityId, nextHex);
                        hexBox.setValue(nextHex);
                        btn.setMessage(getPresetButtonMessage(nextHex));
                    }
                ).bounds(leftX + 168, entryY, 100, 20).build();
                presetBtnRef[0] = presetBtn;
                addEntryWidget(presetBtn);

                hexBox.setResponder(val -> {
                    float[] rgb = RedfxConfig.parseColor(val);
                    if (rgb != null) {
                        hexBox.setTextColor(0xFFFFFF);
                        String validHex = val.startsWith("#") ? val.toUpperCase() : "#" + val.toUpperCase();
                        config.entityBloodColors.put(entityId, validHex);
                        if (presetBtnRef[0] != null) {
                            presetBtnRef[0].setMessage(getPresetButtonMessage(validHex));
                        }
                    } else {
                        hexBox.setTextColor(0xFF5555);
                    }
                });

                // 4. Remove / Reset button
                boolean isDefault = entityId.equals("default");
                Button actionBtn = Button.builder(
                    Component.literal(isDefault ? "R" : "X"),
                    btn -> {
                        if (isDefault) {
                            config.entityBloodColors.put("default", "#FF0D0D");
                        } else {
                            config.entityBloodColors.remove(entityId);
                        }
                        refreshEntries();
                    }
                ).bounds(leftX + 272, entryY, 18, 20).tooltip(
                    Tooltip.create(Component.literal(isDefault ? "Reset default to #FF0D0D" : "Remove " + entityId))
                ).build();
                addEntryWidget(actionBtn);
            } else {
                // Not yet configured in custom list: show suggested color preview & "+ Add" button
                Button previewPresetBtn = Button.builder(
                    getPresetButtonMessage(currentHex),
                    btn -> {
                        String nextHex = getNextPresetHex(currentHex);
                        config.entityBloodColors.put(entityId, nextHex);
                        refreshEntries();
                    }
                ).bounds(leftX + 109, entryY, 100, 20).tooltip(
                    Tooltip.create(Component.literal("Click to customize color and add"))
                ).build();
                addEntryWidget(previewPresetBtn);

                Button addThisBtn = Button.builder(
                    Component.literal("+ Add"),
                    btn -> {
                        config.entityBloodColors.put(entityId, currentHex);
                        if (searchBox != null) searchBox.setValue("");
                    }
                ).bounds(leftX + 213, entryY, 77, 20).tooltip(
                    Tooltip.create(Component.literal("Add " + entityId + " to custom colors"))
                ).build();
                addEntryWidget(addThisBtn);
            }

            entryY += 24;
        }

        // Pagination row
        int pageY = startY + 166;
        Button prevBtn = Button.builder(
            Component.literal("< Prev"),
            btn -> {
                if (page > 0) {
                    page--;
                    refreshEntries();
                }
            }
        ).bounds(leftX + 30, pageY, 65, 20).build();
        prevBtn.active = (page > 0);
        addEntryWidget(prevBtn);

        StringWidget pageInfo = new StringWidget(
            leftX + 100, pageY, 90, 20,
            Component.literal("Page " + (page + 1) + " / " + totalPages),
            this.font
        );
        addEntryWidget(pageInfo);

        Button nextBtn = Button.builder(
            Component.literal("Next >"),
            btn -> {
                if (page < totalPages - 1) {
                    page++;
                    refreshEntries();
                }
            }
        ).bounds(leftX + 195, pageY, 65, 20).build();
        nextBtn.active = (page < totalPages - 1);
        addEntryWidget(nextBtn);
    }

    private void addCurrentEntity() {
        RedfxConfig config = RedfxConfig.get();
        if (searchBox == null) return;
        String val = searchBox.getValue().trim().toLowerCase();
        if (val.isEmpty()) return;

        String toAdd;
        if (!currentMatches.isEmpty()) {
            int idx = Math.max(0, Math.min(suggestionIndex, currentMatches.size() - 1));
            toAdd = currentMatches.get(idx);
        } else {
            toAdd = val.contains(":") ? val : "minecraft:" + val;
        }

        if (!config.entityBloodColors.containsKey(toAdd)) {
            config.entityBloodColors.put(toAdd, config.entityBloodColors.getOrDefault("default", "#FF0D0D"));
        }
        searchBox.setValue("");

        List<String> keys = new ArrayList<>(config.entityBloodColors.keySet());
        int idx = keys.indexOf(toAdd);
        if (idx >= 0) {
            page = idx / ENTRIES_PER_PAGE;
        }
        refreshEntries();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            if (event.key() == 258) { // GLFW_KEY_TAB
                if (!currentMatches.isEmpty()) {
                    if (event.hasShiftDown()) {
                        suggestionIndex = (suggestionIndex - 1 + currentMatches.size()) % currentMatches.size();
                    } else {
                        suggestionIndex = (suggestionIndex + 1) % currentMatches.size();
                    }
                    String chosen = currentMatches.get(suggestionIndex);
                    String shortName = chosen.startsWith("minecraft:") ? chosen.substring(10) : chosen;
                    String currentVal = searchBox.getValue().trim();
                    if (currentVal.startsWith("minecraft:")) {
                        searchBox.setValue(chosen);
                    } else {
                        searchBox.setValue(shortName);
                    }
                    searchBox.setCursorPosition(searchBox.getValue().length());
                    searchBox.setHighlightPos(searchBox.getValue().length());
                    return true;
                }
            } else if (event.key() == 257 || event.key() == 335) { // GLFW_KEY_ENTER or NUMPAD_ENTER
                addCurrentEntity();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private Component getPresetButtonMessage(String hex) {
        int colorInt = RedfxConfig.parseColorInt(hex, 0xFF0000);
        String name = "Custom";
        for (ColorPreset p : PRESETS) {
            if (p.hex.equalsIgnoreCase(hex)) {
                name = p.name;
                break;
            }
        }
        return Component.literal("■ ").withColor(colorInt).append(Component.literal(name));
    }

    private String getNextPresetHex(String currentHex) {
        for (int i = 0; i < PRESETS.size(); i++) {
            if (PRESETS.get(i).hex.equalsIgnoreCase(currentHex)) {
                return PRESETS.get((i + 1) % PRESETS.size()).hex;
            }
        }
        return PRESETS.get(0).hex;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(extractor);
        int startY = Math.max(12, (this.height - 225) / 2);
        extractor.centeredText(this.font, this.title, this.width / 2, Math.max(4, startY - 2), 0xFFFFFF);
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
