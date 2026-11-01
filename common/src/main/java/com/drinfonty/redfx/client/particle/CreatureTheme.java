package com.drinfonty.redfx.client.particle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;

public enum CreatureTheme {
    NORMAL,
    ENDER,
    NETHER_WITHER,
    SCULK,
    ARTHROPOD;

    public static CreatureTheme fromEntity(LivingEntity entity) {
        if (entity == null) return NORMAL;
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

        if (key.contains("enderman") || key.contains("ender_dragon") || key.contains("endermite") || key.contains("shulker")) {
            return ENDER;
        }
        if (key.contains("wither") || key.contains("blaze") || key.contains("magma_cube")) {
            return NETHER_WITHER;
        }
        if (key.contains("warden") || key.contains("sculk")) {
            return SCULK;
        }
        if (entity.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD)) {
            return ARTHROPOD;
        }
        return NORMAL;
    }
}
