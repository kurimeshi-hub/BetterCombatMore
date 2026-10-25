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

/** ホットバーの左に、回避の回数ぶんの矢印ゲージを左方向へ並べる */
@EventBusSubscriber(modid = bcmore.MODID, value = Dist.CLIENT)
public final class rollmeter {
    private static final int SIZE = 15;              // 画像サイズ(元の11px の3倍)
    private static final int GAP = -5;                // 矢印どうしのすき間
    private static final int MARGIN = 6;             // ホットバーとの間隔
    private static final int OFFHAND_WIDTH = 29;     // オフハンド枠の幅

    private static final Identifier[] STAGES = new Identifier[9];
    private static final Identifier FULL = tex("roll_meter_full");
    private static final Identifier FLASH = tex("roll_meter_flash");
    static {
        for (int i = 0; i < STAGES.length; i++) STAGES[i] = tex("roll_meter_" + i);
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(bcmore.MODID, "textures/gui/" + name + ".png");
    }

    private rollmeter() {}

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(bcmore.MODID, "roll_meter"),
                rollmeter::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int max = bcmoreclient.maxCharges();
        int charges = bcmoreclient.chargesNow();
        float progress = bcmoreclient.rechargeProgress();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // ホットバー(幅182)の左端
        int hotbarLeft = screenW / 2 - 91;
        int margin = MARGIN;
        if (!mc.player.getOffhandItem().isEmpty() && mc.player.getMainArm() == HumanoidArm.RIGHT) {
            margin += OFFHAND_WIDTH;   // 右利きのとき、オフハンド枠が左に出る
        }
        int y = screenH - 11 - SIZE / 2;   // ホットバーの縦の中心に合わせる

        // 0番目がホットバーに一番近く、そこから左へ伸びる
        for (int i = 0; i < max; i++) {
            Identifier tex;
            if (i < charges) {
                tex = bcmoreclient.rollFlashing() ? FLASH : FULL;   // 使える(最大になった直後は光る)
            } else if (i == charges) {
                tex = STAGES[Math.min(8, (int) (progress * 9f))];   // 回復中
            } else {
                tex = STAGES[0];                                    // まだ使えない
            }
            int x = hotbarLeft - margin - (i + 1) * SIZE - i * GAP;
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        }
    }
}