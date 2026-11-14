package com.kurimeshi.bcmore.client;

import com.kurimeshi.bcmore.bcmore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** パリィ成功の瞬間、画面全体をうっすら白くして、すぐ消す */
@EventBusSubscriber(modid = bcmore.MODID, value = Dist.CLIENT)
public final class parryflash {
    private static final float MAX_ALPHA = 0.22f;   // いちばん白い瞬間の濃さ(0.0 〜 1.0)

    private parryflash() {}

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_flash"),
                parryflash::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float k = bcmoreclient.parryScreenFlash();
        if (k <= 0f) return;

        int alpha = (int) (MAX_ALPHA * k * 255f);
        if (alpha <= 0) return;

        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                (alpha << 24) | 0xFFFFFF);
    }
}