package com.kurimeshi.bcmore;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(bcmore.MODID)
public class bcmore {
    public static final String MODID = "bettercombatmore";

    public bcmore(IEventBus modEventBus, ModContainer modContainer) {
        bcmoresounds.SOUNDS.register(modEventBus);
        bcmoreattributes.ATTRIBUTES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, bcmoreconfig.SPEC);
    }
}