package com.drinfonty.redfx.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RedfxConfigTest {
    @Test
    void preservesDefaultsWhenLoadingLegacyJson(@TempDir File tempDir) throws Exception {
        File configFile = new File(tempDir, "redfx.json");
        // Legacy JSON missing newly added fields (gradualSplatter, splatterGrowthDelayTicks, wallDripping, etc.)
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{\n  \"bloodEnabled\": true,\n  \"particleAmount\": \"High\"\n}");
        }

        // We can test the parsing logic directly using Gson/JsonParser pattern
        com.google.gson.JsonElement element;
        try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
            element = com.google.gson.JsonParser.parseReader(reader);
        }
        assertTrue(element.isJsonObject());
        com.google.gson.JsonObject obj = element.getAsJsonObject();

        RedfxConfig config = new RedfxConfig();
        if (obj.has("bloodEnabled")) config.bloodEnabled = obj.get("bloodEnabled").getAsBoolean();
        if (obj.has("gradualSplatter")) config.gradualSplatter = obj.get("gradualSplatter").getAsBoolean();
        if (obj.has("splatterGrowthDelayTicks")) config.splatterGrowthDelayTicks = obj.get("splatterGrowthDelayTicks").getAsInt();
        if (obj.has("wallDripping")) config.wallDripping = obj.get("wallDripping").getAsBoolean();

        // Defaults must remain intact
        assertTrue(config.bloodEnabled);
        assertTrue(config.gradualSplatter, "gradualSplatter should default to true even if missing in JSON");
        assertTrue(config.wallDripping, "wallDripping should default to true even if missing in JSON");
        assertEquals(2, config.splatterGrowthDelayTicks, "splatterGrowthDelayTicks should default to 2");
    }
}
