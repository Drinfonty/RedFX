package com.drinfonty.redfx.neoforge;

import com.drinfonty.redfx.RedfxMod;
import com.drinfonty.redfx.client.gui.RedfxConfigScreen;
import com.drinfonty.redfx.neoforge.client.RedfxNeoForgeClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("redfx")
public class RedfxNeoForge {
    public RedfxNeoForge() {
        RedfxMod.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new RedfxConfigScreen(parent))
            );
            RedfxNeoForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
