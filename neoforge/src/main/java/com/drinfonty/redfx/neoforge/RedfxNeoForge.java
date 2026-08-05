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
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new RedfxConfigScreen(parent));
        }
    }
}
