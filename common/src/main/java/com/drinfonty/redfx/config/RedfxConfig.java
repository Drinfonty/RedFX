package com.drinfonty.redfx.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class RedfxConfig {
    private static final File CONFIG_FILE = new File("config", "redfx.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean bloodEnabled = true;
    public String particleAmount = "High"; // Low, Medium, High, Ultra
    public String particleType = "RedWool"; // RedWool, TNT, RedPoof
    public int particleLifetimeSeconds = 5; // Range: 1 to 30 seconds
    public boolean enableSplatDust = true; // Spawn falling dust particle on landing
    public String waterParticleType = "Smoke"; // CampfireSmoke or Smoke
    public float particleSizeScale = 1.0f; // Range: 0.5 to 2.0
    public float splatSizeScale = 1.0f; // Range: 0.5 to 2.0
    public float colorSaturation = 1.0f; // Range: 0.0 to 2.0
    public float bloodOpacity = 1.0f; // Range: 0.1 to 1.0 (10% to 100%)
    public boolean translucentEdges = false; // Translucent gradient falloff on splatter edges
    public boolean dripOverEdges = true; // Wrap and drip splatters over block edges into open air
    public boolean wallDripping = true; // Splatters on vertical walls form natural dripping rivulets running down the block
    public boolean gradualSplatter = true; // Splatters blossom outward gradually (core -> sub-perimeter -> edge)
    public int splatterGrowthDelayTicks = 2; // Delay in ticks between growth stages (1 = 50ms, 2 = 100ms, 3 = 150ms)
    public java.util.Map<String, String> entityBloodColors = new java.util.LinkedHashMap<>();

    public RedfxConfig() {
        initDefaultEntityColors();
    }

    public void initDefaultEntityColors() {
        entityBloodColors.clear();
        entityBloodColors.put("default", "#FF0D0D");
        entityBloodColors.put("minecraft:creeper", "#33E633");
        entityBloodColors.put("minecraft:slime", "#33E633");
        entityBloodColors.put("minecraft:blaze", "#E6B21A");
        entityBloodColors.put("minecraft:magma_cube", "#E6B21A");
        entityBloodColors.put("minecraft:enderman", "#991ACC");
        entityBloodColors.put("minecraft:ender_dragon", "#991ACC");
        entityBloodColors.put("minecraft:endermite", "#991ACC");
        entityBloodColors.put("minecraft:skeleton", "#85522E");
        entityBloodColors.put("minecraft:skeleton_horse", "#85522E");
        entityBloodColors.put("minecraft:stray", "#6B8A99");
        entityBloodColors.put("minecraft:bogged", "#4D6633");
        entityBloodColors.put("minecraft:wither_skeleton", "#262626");
        entityBloodColors.put("minecraft:warden", "#0D4DB2");
        entityBloodColors.put("minecraft:sulfur_cube", "#EBEBEB");
    }

    private static RedfxConfig instance;

    public static RedfxConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static RedfxConfig load() {
        RedfxConfig config = new RedfxConfig();
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                com.google.gson.JsonElement element = com.google.gson.JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    com.google.gson.JsonObject obj = element.getAsJsonObject();
                    if (obj.has("bloodEnabled")) config.bloodEnabled = obj.get("bloodEnabled").getAsBoolean();
                    if (obj.has("particleAmount")) config.particleAmount = obj.get("particleAmount").getAsString();
                    if (obj.has("particleType")) config.particleType = obj.get("particleType").getAsString();
                    if (obj.has("particleLifetimeSeconds")) config.particleLifetimeSeconds = obj.get("particleLifetimeSeconds").getAsInt();
                    if (obj.has("enableSplatDust")) config.enableSplatDust = obj.get("enableSplatDust").getAsBoolean();
                    if (obj.has("waterParticleType")) config.waterParticleType = obj.get("waterParticleType").getAsString();
                    if (obj.has("particleSizeScale")) config.particleSizeScale = obj.get("particleSizeScale").getAsFloat();
                    if (obj.has("splatSizeScale")) config.splatSizeScale = obj.get("splatSizeScale").getAsFloat();
                    if (obj.has("colorSaturation")) config.colorSaturation = obj.get("colorSaturation").getAsFloat();
                    if (obj.has("bloodOpacity")) config.bloodOpacity = obj.get("bloodOpacity").getAsFloat();
                    if (obj.has("translucentEdges")) config.translucentEdges = obj.get("translucentEdges").getAsBoolean();
                    if (obj.has("dripOverEdges")) config.dripOverEdges = obj.get("dripOverEdges").getAsBoolean();
                    if (obj.has("wallDripping")) config.wallDripping = obj.get("wallDripping").getAsBoolean();
                    if (obj.has("gradualSplatter")) config.gradualSplatter = obj.get("gradualSplatter").getAsBoolean();
                    if (obj.has("splatterGrowthDelayTicks")) config.splatterGrowthDelayTicks = obj.get("splatterGrowthDelayTicks").getAsInt();
                    if (obj.has("entityBloodColors") && obj.get("entityBloodColors").isJsonObject()) {
                        config.entityBloodColors.clear();
                        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.getAsJsonObject("entityBloodColors").entrySet()) {
                            if (entry.getValue().isJsonPrimitive()) {
                                config.entityBloodColors.put(entry.getKey(), entry.getValue().getAsString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[RedFX] Failed to load config: " + e.getMessage());
            }
        }
        // Fallbacks & validation
        if (config.particleAmount == null) {
            config.particleAmount = "High";
        }
        if (config.particleLifetimeSeconds <= 0) {
            config.particleLifetimeSeconds = 5;
        }
        if (config.particleType == null || 
            config.particleType.equals("RedstoneBlock") || 
            config.particleType.equals("RedstoneWire")) {
            config.particleType = "RedWool";
        }
        if (config.waterParticleType == null) {
            config.waterParticleType = "Smoke";
        }
        if (config.particleSizeScale < 0.1f) {
            config.particleSizeScale = 1.0f;
        }
        if (config.splatSizeScale < 0.1f) {
            config.splatSizeScale = 1.0f;
        }
        if (config.colorSaturation < 0.0f) {
            config.colorSaturation = 1.0f;
        }
        if (config.bloodOpacity < 0.1f || config.bloodOpacity > 1.0f) {
            config.bloodOpacity = Math.max(0.1f, Math.min(1.0f, config.bloodOpacity));
        }
        if (config.splatterGrowthDelayTicks <= 0) {
            config.splatterGrowthDelayTicks = 2;
        }
        // Save back so any newly introduced properties are persisted into user's config file
        config.save();
        return config;
    }

    public void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[RedFX] Failed to save config: " + e.getMessage());
        }
    }

    public void resetToDefaults() {
        this.bloodEnabled = true;
        this.particleAmount = "High";
        this.particleType = "RedWool";
        this.particleLifetimeSeconds = 5;
        this.enableSplatDust = true;
        this.waterParticleType = "Smoke";
        this.particleSizeScale = 1.0f;
        this.splatSizeScale = 1.0f;
        this.colorSaturation = 1.0f;
        this.bloodOpacity = 1.0f;
        this.translucentEdges = false;
        this.dripOverEdges = true;
        this.wallDripping = true;
        this.gradualSplatter = true;
        this.splatterGrowthDelayTicks = 2;
        initDefaultEntityColors();
    }

    public float getMultiplier() {
        return switch (particleAmount) {
            case "Low" -> 0.4f;
            case "Medium" -> 1.0f;
            case "High" -> 2.0f;
            case "Ultra" -> 4.0f;
            default -> 1.0f;
        };
    }

    public boolean isBloodEnabledFor(String entityId) {
        if (!bloodEnabled) return false;
        if (entityId != null) {
            String val = entityBloodColors.get(entityId);
            if (val != null) {
                return !isNone(val);
            }
            int colon = entityId.indexOf(':');
            if (colon >= 0) {
                String shortKey = entityId.substring(colon + 1);
                val = entityBloodColors.get(shortKey);
                if (val != null) {
                    return !isNone(val);
                }
            }
        }
        String def = entityBloodColors.get("default");
        if (def != null && isNone(def)) return false;
        return true;
    }

    public static boolean isNone(String val) {
        if (val == null) return false;
        String s = val.trim().toLowerCase();
        return s.equals("none") || s.equals("off") || s.equals("disabled") || s.equals("false");
    }

    public float[] getBloodColorForEntity(String entityId) {
        if (entityId != null) {
            String hex = entityBloodColors.get(entityId);
            if (hex != null) {
                if (isNone(hex)) return null;
                float[] rgb = parseColor(hex);
                if (rgb != null) return rgb;
            }
            int colon = entityId.indexOf(':');
            if (colon >= 0) {
                String shortKey = entityId.substring(colon + 1);
                hex = entityBloodColors.get(shortKey);
                if (hex != null) {
                    if (isNone(hex)) return null;
                    float[] rgb = parseColor(hex);
                    if (rgb != null) return rgb;
                }
            }
        }
        return null;
    }

    public float[] getDefaultBloodColor() {
        String hex = entityBloodColors.get("default");
        if (hex != null) {
            float[] rgb = parseColor(hex);
            if (rgb != null) return rgb;
        }
        return new float[]{1.0F, 0.05F, 0.05F};
    }

    public static float[] parseColor(String hex) {
        if (hex == null || hex.isBlank()) return null;
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(s.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(s.substring(2, 3), 16) * 17;
                return new float[]{r / 255.0f, g / 255.0f, b / 255.0f};
            } else if (s.length() == 6 || s.length() == 8) {
                if (s.length() == 8) {
                    s = s.substring(2);
                }
                int val = Integer.parseInt(s, 16);
                float r = ((val >> 16) & 0xFF) / 255.0f;
                float g = ((val >> 8) & 0xFF) / 255.0f;
                float b = (val & 0xFF) / 255.0f;
                return new float[]{r, g, b};
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public static int parseColorInt(String hex, int fallback) {
        float[] rgb = parseColor(hex);
        if (rgb == null) return fallback;
        int r = Math.round(rgb[0] * 255.0f);
        int g = Math.round(rgb[1] * 255.0f);
        int b = Math.round(rgb[2] * 255.0f);
        return (r << 16) | (g << 8) | b;
    }

    public static String toHex(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return String.format("#%02X%02X%02X", ri, gi, bi);
    }
}
