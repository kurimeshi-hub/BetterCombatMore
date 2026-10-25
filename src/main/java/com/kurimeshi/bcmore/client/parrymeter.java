package com.kurimeshi.bcmore.client;

import com.kurimeshi.bcmore.bcmore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** ホットバーの右に、パリィのクールダウンを盾のゲージで表示する */
@EventBusSubscriber(modid = bcmore.MODID, value = Dist.CLIENT)
public final class parrymeter {
    private static final int SIZE = 15;              // 画像サイズ
    private static final int MARGIN = 6;             // ホットバーとの間隔
    private static final int OFFHAND_WIDTH = 29;     // オフハンド枠の幅
    private static final boolean HIDE_WHEN_READY = false;   // true: 使えるときは非表示

    private static final Identifier[] STAGES = new Identifier[9];
    private static final Identifier FULL = tex("parry_meter_full");
    private static final Identifier FLASH = tex("parry_meter_flash");
    static {
        for (int i = 0; i < STAGES.length; i++) STAGES[i] = tex("parry_meter_" + i);
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(bcmore.MODID, "textures/gui/" + name + ".png");
    }

    private parrymeter() {}

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_meter"),
                parrymeter::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float progress = bcmoreclient.parryProgress();
        if (HIDE_WHEN_READY && progress >= 1f && !bcmoreclient.parryFlashing()) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // ホットバー(幅182)の右端
        int hotbarRight = screenW / 2 + 91;
        int margin = MARGIN;
        if (!mc.player.getOffhandItem().isEmpty() && mc.player.getMainArm() == HumanoidArm.LEFT) {
            margin += OFFHAND_WIDTH;   // 左利きのとき、オフハンド枠が右に出る
        }

        int x = hotbarRight + margin;
        int y = screenH - 11 - SIZE / 2;   // ホットバーの縦の中心に合わせる

        Identifier tex = progress >= 1f
                ? (bcmoreclient.parryFlashing() ? FLASH : FULL)
                : STAGES[Math.min(8, (int) (progress * 9f))];
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
    }
}