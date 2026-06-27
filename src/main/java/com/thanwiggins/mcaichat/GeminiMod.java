package com.thanwiggins.mcaichat;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.client.ConfigScreenHandler;

@Mod(GeminiMod.MODID)
public class GeminiMod {
    public static final String MODID = "mcaichat";

    public GeminiMod() {
        // Register the Client Config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        
        // Register the Config GUI to the Forge Mods Menu
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new GeminiConfigScreen(screen)));
        
        NetworkHandler.register();
    }
}