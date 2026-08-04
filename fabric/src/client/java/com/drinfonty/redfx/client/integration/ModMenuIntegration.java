package com.drinfonty.redfx.client.integration;

import com.drinfonty.redfx.client.gui.RedfxConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new RedfxConfigScreen(parent);
    }
}
