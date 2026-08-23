package com.drinfonty.redfx.neoforge;

import com.drinfonty.redfx.RedfxMod;
import com.drinfonty.redfx.client.gui.RedfxConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod("redfx")
public class RedfxNeoForge {
    public RedfxNeoForge(ModContainer container) {
        RedfxMod.init();
        // NeoForge <=21.8 exposes the dist as a public field; getDist() arrived later.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new RedfxConfigScreen(parent));
        }
    }
}
