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
        if (obj.has("directionalBlood")) config.directionalBlood = obj.get("directionalBlood").getAsBoolean();

        // Defaults must remain intact
        assertTrue(config.bloodEnabled);
        assertTrue(config.directionalBlood, "directionalBlood should default to true even if missing in JSON");
        assertTrue(config.gradualSplatter, "gradualSplatter should default to true even if missing in JSON");
        assertTrue(config.wallDripping, "wallDripping should default to true even if missing in JSON");
        assertEquals(2, config.splatterGrowthDelayTicks, "splatterGrowthDelayTicks should default to 2");
        assertTrue(config.entityBloodColors.containsKey("minecraft:creeper"));
        assertEquals("#33E633", config.entityBloodColors.get("minecraft:creeper"));
    }

    @Test
    void parsesHexColorsCorrectly() {
        float[] red = RedfxConfig.parseColor("#FF0000");
        org.junit.jupiter.api.Assertions.assertNotNull(red);
        assertEquals(1.0f, red[0], 0.001f);
        assertEquals(0.0f, red[1], 0.001f);
        assertEquals(0.0f, red[2], 0.001f);

        float[] greenShort = RedfxConfig.parseColor("#0F0");
        org.junit.jupiter.api.Assertions.assertNotNull(greenShort);
        assertEquals(0.0f, greenShort[0], 0.001f);
        assertEquals(1.0f, greenShort[1], 0.001f);
        assertEquals(0.0f, greenShort[2], 0.001f);

        org.junit.jupiter.api.Assertions.assertNull(RedfxConfig.parseColor("invalid"));
        org.junit.jupiter.api.Assertions.assertNull(RedfxConfig.parseColor(""));
        org.junit.jupiter.api.Assertions.assertNull(RedfxConfig.parseColor(null));
    }

    @Test
    void resolvesEntityBloodColors() {
        RedfxConfig config = new RedfxConfig();
        config.entityBloodColors.put("minecraft:custom_mob", "#00FF00");

        float[] colorFull = config.getBloodColorForEntity("minecraft:custom_mob");
        org.junit.jupiter.api.Assertions.assertNotNull(colorFull);
        assertEquals(0.0f, colorFull[0], 0.001f);
        assertEquals(1.0f, colorFull[1], 0.001f);
        assertEquals(0.0f, colorFull[2], 0.001f);

        // Lookup by short name
        config.entityBloodColors.put("short_mob", "#0000FF");
        float[] colorShort = config.getBloodColorForEntity("minecraft:short_mob");
        org.junit.jupiter.api.Assertions.assertNotNull(colorShort);
        assertEquals(1.0f, colorShort[2], 0.001f);

        // Unknown entity returns null
        org.junit.jupiter.api.Assertions.assertNull(config.getBloodColorForEntity("minecraft:unknown_mob_xyz"));

        // Entity disabled
        config.entityBloodColors.put("minecraft:iron_golem", "none");
        org.junit.jupiter.api.Assertions.assertFalse(config.isBloodEnabledFor("minecraft:iron_golem"));
        org.junit.jupiter.api.Assertions.assertNull(config.getBloodColorForEntity("minecraft:iron_golem"));
        org.junit.jupiter.api.Assertions.assertTrue(config.isBloodEnabledFor("minecraft:creeper"));
    }
}
