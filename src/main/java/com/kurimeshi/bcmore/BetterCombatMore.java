package com.kurimeshi.bcmore;

import com.kurimeshi.bcmore.network.ParryPacketHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(BetterCombatMore.MOD_ID)
public class BetterCombatMore {
    public static final String MOD_ID = "bcmore";

    public BetterCombatMore(IEventBus modEventBus) {
        modEventBus.addListener(ParryPacketHandler::register);
    }
}