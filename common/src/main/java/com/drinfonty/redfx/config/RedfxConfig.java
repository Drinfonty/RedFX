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
    public boolean translucentEdges = true; // Translucent gradient falloff on splatter edges
    public boolean dripOverEdges = true; // Wrap and drip splatters over block edges into open air
    public boolean wallDripping = true; // Splatters on vertical walls form natural dripping rivulets running down the block
    public boolean gradualSplatter = true; // Splatters blossom outward gradually (core -> sub-perimeter -> edge)
    public int splatterGrowthDelayTicks = 2; // Delay in ticks between growth stages (1 = 50ms, 2 = 100ms, 3 = 150ms)

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
                    if (obj.has("translucentEdges")) config.translucentEdges = obj.get("translucentEdges").getAsBoolean();
                    if (obj.has("dripOverEdges")) config.dripOverEdges = obj.get("dripOverEdges").getAsBoolean();
                    if (obj.has("wallDripping")) config.wallDripping = obj.get("wallDripping").getAsBoolean();
                    if (obj.has("gradualSplatter")) config.gradualSplatter = obj.get("gradualSplatter").getAsBoolean();
                    if (obj.has("splatterGrowthDelayTicks")) config.splatterGrowthDelayTicks = obj.get("splatterGrowthDelayTicks").getAsInt();
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
        this.translucentEdges = true;
        this.dripOverEdges = true;
        this.wallDripping = true;
        this.gradualSplatter = true;
        this.splatterGrowthDelayTicks = 2;
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
}
