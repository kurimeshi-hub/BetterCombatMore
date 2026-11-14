package com.kurimeshi.bcmore.client;

import com.kurimeshi.bcmore.bcmore;
import com.kurimeshi.bcmore.bcmoreconfig;
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

/** ホットバーの右に、パリィの回数ぶんの盾ゲージを右方向へ並べる */
@EventBusSubscriber(modid = bcmore.MODID, value = Dist.CLIENT)
public final class parrymeter {
    private static final int SIZE = 11;              // 画像サイズ
    private static final int MARGIN = 6;             // ホットバーとの間隔
    private static final int GAP = 0;                // 盾どうしのすき間
    private static final int OFFHAND_WIDTH = 29;     // オフハンド枠の幅
    private static final boolean HIDE_WHEN_READY = false;   // true: 全部使えるときは非表示

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
        if (mc.player == null || mc.options.hideGui || !bcmoreconfig.ENABLE_PARRY.get()) return;

        int max = bcmoreclient.parryMaxCharges();
        int charges = bcmoreclient.parryChargesNow();
        float progress = bcmoreclient.parryRechargeProgress();
        if (HIDE_WHEN_READY && charges >= max && !bcmoreclient.parryAnyFlashing()) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // ホットバー(幅182)の右端
        int hotbarRight = screenW / 2 + 91;
        int margin = MARGIN;
        if (!mc.player.getOffhandItem().isEmpty() && mc.player.getMainArm() == HumanoidArm.LEFT) {
            margin += OFFHAND_WIDTH;   // 左利きのとき、オフハンド枠が右に出る
        }
        int y = screenH - 11 - SIZE / 2;   // ホットバーの縦の中心に合わせる

        // 0番目がホットバーに一番近く、そこから右へ伸びる
        for (int i = 0; i < max; i++) {
            Identifier tex;
            if (i < charges) {
                tex = bcmoreclient.parryFlashing(i) ? FLASH : FULL;   // 使える(溜まった直後はそのゲージが光る)
            } else if (i == charges) {
                tex = STAGES[Math.min(8, (int) (progress * 9f))];     // 回復中
            } else {
                tex = STAGES[0];                                      // まだ使えない
            }
            int x = hotbarRight + margin + i * (SIZE + GAP);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        }
    }
}