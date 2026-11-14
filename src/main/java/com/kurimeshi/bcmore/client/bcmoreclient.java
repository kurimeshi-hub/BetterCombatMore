package com.kurimeshi.bcmore.client;

import com.kurimeshi.bcmore.bcmore;
import com.kurimeshi.bcmore.bcmoreattributes;
import com.kurimeshi.bcmore.bcmoreconfig;
import com.kurimeshi.bcmore.bcmorenetworking;
import com.kurimeshi.bcmore.bcmoreparticles;
import com.kurimeshi.bcmore.combatstate;
import com.mojang.blaze3d.platform.InputConstants;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.UUID;

@EventBusSubscriber(modid = bcmore.MODID, value = Dist.CLIENT)
public class bcmoreclient {
    private static final Identifier LAYER_ID =
            Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_layer");
    private static final Identifier PARRY_ANIM_ID =
            Identifier.fromNamespaceAndPath(bcmore.MODID, "animation.parry");
    private static final Identifier ROLL_ANIM_ID =
            Identifier.fromNamespaceAndPath(bcmore.MODID, "animation.roll");
    private static final Identifier STEP_ANIM_ID =
            Identifier.fromNamespaceAndPath(bcmore.MODID, "animation.step");
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(bcmore.MODID, "main"));

    public static final KeyMapping PARRY_KEY =
            new KeyMapping("key.bcmore.parry", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping DODGE_KEY =
            new KeyMapping("key.bcmore.dodge", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    // ---- 画面揺れの設定 ----
    private static long shakeStartMs = 0;
    private static final long SHAKE_DURATION_MS = 200;   // 揺れる時間
    private static final float SHAKE_STRENGTH = 1.2f;    // 揺れの大きさ(度)

    // ---- パリィ成功のパーティクル ----
    private static final double PARRY_PARTICLE_DISTANCE = 2.0;   // 目線から何ブロック先に出すか
    private static final int PARRY_STREAK_COUNT = 38;            // 武器ありのときの放射線の本数

    // ---- パリィ成功の画面の白フラッシュ ----
    private static final long SCREEN_FLASH_MS = 250;             // 白くなって消えるまでの時間
    private static long screenFlashStartMs = -100000;

    // ---- ロールの2段階加速(1tick目 45% + 2tick目 55%) ----
    private static final double ROLL_FIRST_RATIO = 0.45;
    private static final double ROLL_SECOND_RATIO = 0.55;

    // ---- 空中でも地上と同じ減速にする ----
    private static final double GROUND_FRICTION = 0.546;
    private static final double AIR_FRICTION = 0.91;
    private static final double AIR_EXTRA_DRAG = GROUND_FRICTION / AIR_FRICTION;   // ≒ 0.6
    private static int airDecayTicks = 0;
    private static boolean airDecaySkipFirst = false;

    // ---- パリィ・攻撃の状態(クライアント側) ----
    private static long parryStartMs = 0;
    private static long lastAttackClickMs = 0;
    private static boolean parryLockCleared = false;   // 成功で攻撃ロックを解除したか

    // ---- ロールの2段目の加速 ----
    private static boolean rollSecondPending = false;
    private static double rollDx = 0;
    private static double rollDz = 0;
    private static double rollSecondSpeed = 0;

    // ---- ロール中の視点回転(一人称のみ) ----
    private static final long ROLL_SPIN_MS = 450;        // 1回転にかける時間
    private static final float ROLL_SPIN_DEGREES = 360f; // 回転量
    private static long rollSpinStartMs = -100000;
    private static float rollSpinFwd = 0f;               // 前後の成分(+1 前転 / -1 後転)
    private static float rollSpinSide = 0f;              // 左右の成分

    // ---- 回避の回数(サーバーの値を同期して、時間経過はこちらで進める) ----
    private static int clientCharges = -1;          // -1 = まだ同期されていない(満タン扱い)
    private static long clientRechargeStart = 0;

    // ---- パリィの回数(クライアント側で予測して進める) ----
    private static int parryCharges = -1;
    private static long parryRechargeStart = 0;
    private static boolean parryKeyHeld = false;    // 押しっぱなしでの連続発動を防ぐ

    // ---- 「1ゲージ溜まった」ときの光と音(ゲージごとに管理) ----
    private static final long FLASH_MS = 350;
    private static final int FLASH_SLOTS = 10;      // 属性の上限(10)に合わせる
    private static final long[] rollFlashMs = new long[FLASH_SLOTS];
    private static final long[] parryFlashMs = new long[FLASH_SLOTS];
    private static int rollLastCharges = -1;        // -1 = 起動直後(鳴らさない)
    private static int parryLastCharges = -1;
    static {
        Arrays.fill(rollFlashMs, -100000L);
        Arrays.fill(parryFlashMs, -100000L);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(PARRY_KEY);
        event.register(DODGE_KEY);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(bcmoreparticles.PARRY_SPARK.get(), ParrySparkParticle.Provider::new);
        event.registerSpriteSet(bcmoreparticles.PARRY_STREAK.get(), ParryStreakParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1500,
                        player -> {
                            PlayerAnimationController controller = new PlayerAnimationController(player,
                                    (c, state, animSetter) -> PlayState.STOP);
                            controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
                            controller.setFirstPersonConfiguration(new FirstPersonConfiguration()
                                    .setShowRightArm(true)
                                    .setShowLeftArm(true)
                                    .setShowRightItem(true)
                                    .setShowLeftItem(true)
                                    .setShowArmor(false));
                            controller.setFirstPersonFollowsCamera(true);
                            controller.setFirstPersonTransitionLength(2);
                            return controller;
                        }));
    }

    /** パリィ中(攻撃できない)か。成功したら解除される */
    private static boolean parryLocked() {
        return !parryLockCleared && combatstate.now() - parryStartMs < combatstate.PARRY_LOCK_MS;
    }

    // tick の開始時: パリィ中の攻撃入力を捨てる / 回避の減速補正 / ロールの2段目の加速
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // パリィ中は攻撃クリックを捨てる(Better Combat の攻撃アニメも出さない)
        if (parryLocked()) {
            while (mc.options.keyAttack.consumeClick()) { /* 捨てる */ }
            mc.options.keyAttack.setDown(false);
        }

        // 空中: 地上と同じ減速になるよう、余分な抵抗を足す
        if (airDecayTicks > 0) {
            if (airDecaySkipFirst) {
                airDecaySkipFirst = false;
            } else if (!mc.player.onGround()) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x * AIR_EXTRA_DRAG, v.y, v.z * AIR_EXTRA_DRAG);
            }
            airDecayTicks--;
        }

        // ロール: 2tick目に残りの加速を足す
        if (rollSecondPending) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(
                    v.x + rollDx * rollSecondSpeed,
                    v.y,
                    v.z + rollDz * rollSecondSpeed);
            rollSecondPending = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long nowMs = combatstate.now();

        // ロール: 1ゲージ溜まるごとに、そのゲージを光らせる
        int rollCharges = chargesNow();
        if (rollLastCharges >= 0 && rollCharges > rollLastCharges) {
            for (int i = rollLastCharges; i < rollCharges && i < FLASH_SLOTS; i++) rollFlashMs[i] = nowMs;
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
        }
        rollLastCharges = rollCharges;

        // パリィ: 同じく1ゲージごと
        int parryNow = parryChargesNow();
        if (parryLastCharges >= 0 && parryNow > parryLastCharges) {
            for (int i = parryLastCharges; i < parryNow && i < FLASH_SLOTS; i++) parryFlashMs[i] = nowMs;
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
        }
        parryLastCharges = parryNow;

        // 長押し対策: キーを離したら、次のパリィを受け付ける
        if (!PARRY_KEY.isDown()) parryKeyHeld = false;

        while (PARRY_KEY.consumeClick()) {
            if (!bcmoreconfig.ENABLE_PARRY.get()) continue;                       // コンフィグでOFF
            if (parryKeyHeld) continue;                                           // 押しっぱなしは不可
            long now = combatstate.now();
            if (now - lastAttackClickMs < combatstate.ATTACK_LOCK_MS) continue;   // 攻撃直後は不可
            if (parryChargesNow() <= 0) continue;                                 // 回数切れ
            parryKeyHeld = true;
            parryCharges--;
            parryStartMs = now;
            parryLockCleared = false;   // 新しいパリィが始まったので、ロックを有効に戻す
            playParry(mc.player);
            ClientPacketDistributor.sendToServer(new bcmorenetworking.parryc2s());
        }
        while (DODGE_KEY.consumeClick()) {
            if (!bcmoreconfig.ENABLE_ROLL.get()) continue;                        // コンフィグでOFF
            var move = mc.player.input.getMoveVector(); // x=左右, y=前後
            ClientPacketDistributor.sendToServer(new bcmorenetworking.dodgec2s(move.y, move.x));
        }
    }

    // 攻撃クリックの時刻を記録 / パリィ中は攻撃を出さない
    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        long now = combatstate.now();
        if (parryLocked()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        lastAttackClickMs = now;
    }

    // ================= 回避の回数(ゲージ表示用) =================

    /** 最大回数(属性) */
    public static int maxCharges() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1;
        return Math.max(1, (int) Math.round(mc.player.getAttributeValue(bcmoreattributes.DODGE_CHARGES)));
    }

    /** 1回ぶんの回復時間(ms、属性) */
    private static long rechargeMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1000;
        return (long) (mc.player.getAttributeValue(bcmoreattributes.DODGE_COOLDOWN) * 1000);
    }

    /** サーバーからの同期(S2C パケットから呼ばれる) */
    public static void onDodgeSync(int charges, int elapsedMs) {
        clientCharges = charges;
        clientRechargeStart = combatstate.now() - elapsedMs;
    }

    /** 経過時間ぶんの回復を反映する */
    private static void advanceCharges() {
        int max = maxCharges();
        long cd = rechargeMs();
        long n = combatstate.now();

        if (clientCharges < 0 || clientCharges >= max || cd <= 0) {
            clientCharges = max;
            clientRechargeStart = n;   // 満タンの間は、回復の開始時刻を現在に保つ
            return;
        }
        long gained = (n - clientRechargeStart) / cd;
        if (gained > 0) {
            clientCharges = (int) Math.min(max, clientCharges + gained);
            clientRechargeStart += gained * cd;
        }
    }

    /** 現在使える回数 */
    public static int chargesNow() {
        advanceCharges();
        return clientCharges;
    }

    /** 回復中の1回ぶんの進み具合(0.0 〜 1.0)。満タンなら 1.0 */
    public static float rechargeProgress() {
        advanceCharges();
        if (clientCharges >= maxCharges()) return 1f;
        long cd = rechargeMs();
        if (cd <= 0) return 1f;
        return Math.max(0f, Math.min(1f, (combatstate.now() - clientRechargeStart) / (float) cd));
    }

    // ================= パリィのゲージ用 =================

    /** パリィの最大回数(属性) */
    public static int parryMaxCharges() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1;
        return Math.max(1, (int) Math.round(mc.player.getAttributeValue(bcmoreattributes.PARRY_CHARGES)));
    }

    /** パリィ1回ぶんの回復時間(ms、属性) */
    private static long parryRechargeMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1000;
        return (long) (mc.player.getAttributeValue(bcmoreattributes.PARRY_COOLDOWN) * 1000);
    }

    /** 経過時間ぶんの回復を反映する */
    private static void advanceParry() {
        int max = parryMaxCharges();
        long cd = parryRechargeMs();
        long n = combatstate.now();

        if (parryCharges < 0 || parryCharges >= max || cd <= 0) {
            parryCharges = max;
            parryRechargeStart = n;   // 満タンの間は、回復の開始時刻を現在に保つ
            return;
        }
        long gained = (n - parryRechargeStart) / cd;
        if (gained > 0) {
            parryCharges = (int) Math.min(max, parryCharges + gained);
            parryRechargeStart += gained * cd;
        }
    }

    /** 現在使えるパリィの回数 */
    public static int parryChargesNow() {
        advanceParry();
        return parryCharges;
    }

    /** 回復中の1回ぶんの進み具合(0.0 〜 1.0)。満タンなら 1.0 */
    public static float parryRechargeProgress() {
        advanceParry();
        if (parryCharges >= parryMaxCharges()) return 1f;
        long cd = parryRechargeMs();
        if (cd <= 0) return 1f;
        return Math.max(0f, Math.min(1f, (combatstate.now() - parryRechargeStart) / (float) cd));
    }

    // ================= 光の演出 =================

    /** index 番目のロールゲージが光っているか(そのゲージが溜まってから FLASH_MS のあいだ true) */
    public static boolean rollFlashing(int index) {
        return index >= 0 && index < FLASH_SLOTS && combatstate.now() - rollFlashMs[index] < FLASH_MS;
    }

    /** index 番目のパリィゲージが光っているか */
    public static boolean parryFlashing(int index) {
        return index >= 0 && index < FLASH_SLOTS && combatstate.now() - parryFlashMs[index] < FLASH_MS;
    }

    /** どれかのパリィゲージが光っているか */
    public static boolean parryAnyFlashing() {
        for (int i = 0; i < FLASH_SLOTS; i++) if (parryFlashing(i)) return true;
        return false;
    }

    /** 画面の白フラッシュの強さ(1.0 → 0.0 に消えていく。光っていないときは 0) */
    public static float parryScreenFlash() {
        long t = combatstate.now() - screenFlashStartMs;
        if (t < 0 || t >= SCREEN_FLASH_MS) return 0f;
        float p = 1f - t / (float) SCREEN_FLASH_MS;
        return p * p;   // 最初に強く、すっと消える
    }

    // ================= 回避の移動 =================

    /**
     * 回避の移動(S2C パケットから呼ばれる)。
     * ステップ: 初速を1回で与える。ロール: 2段階で加速する。
     * 初速は空中でも地上の摩擦で計算し、空中では余分な抵抗で減速を合わせる。
     */
    public static void onDodgeMove(float dx, float dz, float distance, boolean ramp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double speed = distance * (1.0 - GROUND_FRICTION);

        Vec3 v = mc.player.getDeltaMovement();
        if (ramp) {
            mc.player.setDeltaMovement(dx * speed * ROLL_FIRST_RATIO, v.y, dz * speed * ROLL_FIRST_RATIO);
            rollDx = dx;
            rollDz = dz;
            rollSecondSpeed = speed * ROLL_SECOND_RATIO;
            rollSecondPending = true;

            // 視点回転: 回避方向を、向いている方向を基準に前後・左右へ分解する
            float yawRad = mc.player.getYRot() * (float) (Math.PI / 180.0);
            rollSpinFwd = dx * -(float) Math.sin(yawRad) + dz * (float) Math.cos(yawRad);
            rollSpinSide = dx * (float) Math.cos(yawRad) + dz * (float) Math.sin(yawRad);
            rollSpinStartMs = combatstate.now();
        } else {
            rollSecondPending = false;
            mc.player.setDeltaMovement(dx * speed, v.y, dz * speed);
        }

        airDecayTicks = 14;          // 約0.7秒間、空中での減速を補正する
        airDecaySkipFirst = true;
    }

    /**
     * パリィ成功(S2C パケットから呼ばれる):
     * 画面揺れ + 攻撃ロック解除(クールダウンはそのまま) + パーティクル。
     * weapon=true(メインハンドに何か持っている)なら、放射状の火花の線も出す。
     */
    public static void onParryShake(boolean weapon) {
        shakeStartMs = System.nanoTime() / 1_000_000L;
        screenFlashStartMs = combatstate.now();
        parryLockCleared = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 目線の少し先に出す
        Vec3 pos = mc.player.getEyePosition()
                .add(mc.player.getLookAngle().scale(PARRY_PARTICLE_DISTANCE))
                .add(0, -0.2, 0);

        mc.level.addParticle(bcmoreparticles.PARRY_SPARK.get(), pos.x, pos.y, pos.z, 0, 0, 0);

        if (weapon) {
            ParryStreakParticle.burst(mc.level, pos, PARRY_STREAK_COUNT);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();

        // ---- パリィ成功の画面揺れ ----
        long elapsed = System.nanoTime() / 1_000_000L - shakeStartMs;
        if (elapsed >= 0 && elapsed <= SHAKE_DURATION_MS) {
            float progress = elapsed / (float) SHAKE_DURATION_MS;   // 0 → 1
            float decay = (1.0f - progress) * (1.0f - progress);    // 徐々に収まる
            float t = elapsed * 0.06f;

            event.setPitch(event.getPitch() + (float) Math.sin(t * 2.3) * SHAKE_STRENGTH * decay);
            event.setYaw(event.getYaw() + (float) Math.cos(t * 1.9) * SHAKE_STRENGTH * 0.6f * decay);
            event.setRoll(event.getRoll() + (float) Math.sin(t * 3.1) * SHAKE_STRENGTH * 0.8f * decay);
        }

        // ---- ロール中の視点回転(一人称のみ) ----
        if (!mc.options.getCameraType().isFirstPerson()) return;
        long spin = combatstate.now() - rollSpinStartMs;
        if (spin < 0 || spin >= ROLL_SPIN_MS) return;

        float p = spin / (float) ROLL_SPIN_MS;                        // 0 → 1
        float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * p);    // ゆっくり始まり、ゆっくり終わる
        float angle = ROLL_SPIN_DEGREES * eased;

        event.setPitch(event.getPitch() + angle * rollSpinFwd);       // 前転・後転
        event.setRoll(event.getRoll() - angle * rollSpinSide);        // 横回転
    }

    /** 他プレイヤーのパリィアニメ(S2C パケットから呼ばれる) */
    public static void onRemoteParry(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer p) playParry(p);
    }

    /** 回避アニメ(S2C パケットから呼ばれる)。roll=true ならロール、false ならステップ/テレポート */
    public static void onDodgeAnim(UUID id, boolean roll) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer p) {
            playAnim(p, roll ? ROLL_ANIM_ID : STEP_ANIM_ID);
        }
    }

    private static void playParry(AbstractClientPlayer player) {
        playAnim(player, PARRY_ANIM_ID);
    }

    private static void playAnim(AbstractClientPlayer player, Identifier animId) {
        var layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID);
        if (layer instanceof PlayerAnimationController controller) {
            controller.triggerAnimation(animId);
        }
    }
}