package com.drinfonty.redfx.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class RedfxConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "redfx.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean bloodEnabled = true;
    public String particleAmount = "High"; // Low, Medium, High, Ultra
    public String particleType = "RedWool"; // RedWool, TNT, RedPoof
    public int particleLifetimeSeconds = 5; // Range: 1 to 15 seconds
    public boolean useSplatTexture = true; // Use custom splatter texture on landing

    private static RedfxConfig instance;

    public static RedfxConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static RedfxConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                RedfxConfig config = GSON.fromJson(reader, RedfxConfig.class);
                if (config != null) {
                    if (config.particleAmount == null) {
                        config.particleAmount = "High";
                    }
                    if (config.particleLifetimeSeconds <= 0) {
                        config.particleLifetimeSeconds = 5;
                    }
                    // Fallback to default if loaded particleType is one of the removed ones
                    if (config.particleType == null || 
                        config.particleType.equals("RedstoneBlock") || 
                        config.particleType.equals("RedstoneWire")) {
                        config.particleType = "RedWool";
                    }
                    return config;
                }
            } catch (Exception e) {
                System.err.println("[RedFX] Failed to load config: " + e.getMessage());
            }
        }
        RedfxConfig config = new RedfxConfig();
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
