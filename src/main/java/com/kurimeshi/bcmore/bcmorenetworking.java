package com.kurimeshi.bcmore;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = bcmore.MODID)
public final class bcmorenetworking {

    // ---- 調整用の定数 ----
    private static final double PARRY_BOUNCE = 0.4;        // 跳ねる高さ(0.3 ~ 0.5)
    private static final double ENEMY_KNOCKBACK = 0.6;     // 相手のノックバック
    private static final double SELF_KNOCKBACK = 0.15;     // 自分のノックバック(ほんの少し)
    private static final float COUNTER_MULTIPLIER = 1.5f;  // カウンターのダメージ倍率
    private static final float PARRY_DAMAGE_MIN = 2.0f;    // パリィ反撃ダメージの最小値(倍率・上限は属性)

    // 反撃ダメージ自身が、パリィ判定やカウンター判定を再び起動しないようにする
    private static boolean parryDamaging = false;

    private static final long ROLL_IFRAME_MS = 400;
    private static final long STEP_IFRAME_MS = 200;
    private static final long TELEPORT_IFRAME_MS = 150;

    private static final double AIR_ROLL_DISTANCE_MULT = 0.15;  // 空中ロールの移動距離(15%)
    private static final double TUMBLE_MIN_FALL = 3.0;          // 受け身が働く最低落下距離

    // 火花が出る条件: 剣・ツールで、耐久が石以上(石=131。木=59 / 金=32 は対象外)
    private static final int SPARK_MIN_DURABILITY = 131;

    // 空中ロールの方向(受け身の自動ロール用)
    private static final Map<UUID, Vec3> AIR_ROLL_DIR = new HashMap<>();

    // パリィの回数(サーバー側)
    private static final Map<UUID, Integer> PARRY_CHARGE_COUNT = new HashMap<>();
    private static final Map<UUID, Long> PARRY_RECHARGE_START = new HashMap<>();
    private static final long PARRY_SYNC_TOLERANCE_MS = 50;   // クライアントとの時間ずれの許容

    // ---- パケット定義 ----
    public record parryc2s() implements CustomPacketPayload {
        public static final Type<parryc2s> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, parryc2s> CODEC =
                StreamCodec.unit(new parryc2s());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record dodgec2s(float forward, float sideways) implements CustomPacketPayload {
        public static final Type<dodgec2s> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "dodge_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, dodgec2s> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, dodgec2s::forward,
                ByteBufCodecs.FLOAT, dodgec2s::sideways,
                dodgec2s::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record parryanims2c(UUID player) implements CustomPacketPayload {
        public static final Type<parryanims2c> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_anim_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, parryanims2c> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, parryanims2c::player,
                parryanims2c::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 回避アニメの再生指示: roll=true ならロール、false ならステップ/テレポート */
    public record dodgeanims2c(UUID player, boolean roll) implements CustomPacketPayload {
        public static final Type<dodgeanims2c> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "dodge_anim_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, dodgeanims2c> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, dodgeanims2c::player,
                ByteBufCodecs.BOOL, dodgeanims2c::roll,
                dodgeanims2c::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** パリィ成功の演出指示: weapon=true なら武器ありの演出(放射状の火花) */
    public record parryshakes2c(boolean weapon) implements CustomPacketPayload {
        public static final Type<parryshakes2c> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_shake_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, parryshakes2c> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, parryshakes2c::weapon,
                parryshakes2c::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 回避の移動指示: 方向(x,z)・移動距離・2段階加速か(ロール=true) */
    public record dodgemoves2c(float dx, float dz, float distance, boolean ramp) implements CustomPacketPayload {
        public static final Type<dodgemoves2c> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "dodge_move_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, dodgemoves2c> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, dodgemoves2c::dx,
                ByteBufCodecs.FLOAT, dodgemoves2c::dz,
                ByteBufCodecs.FLOAT, dodgemoves2c::distance,
                ByteBufCodecs.BOOL, dodgemoves2c::ramp,
                dodgemoves2c::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 回避の回数の同期: 現在の回数 / 今の1回ぶんの回復開始からの経過ms */
    public record dodgesyncs2c(int charges, int elapsedMs) implements CustomPacketPayload {
        public static final Type<dodgesyncs2c> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(bcmore.MODID, "dodge_sync_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, dodgesyncs2c> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, dodgesyncs2c::charges,
                ByteBufCodecs.VAR_INT, dodgesyncs2c::elapsedMs,
                dodgesyncs2c::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---- 登録(MODバス) ----
    @EventBusSubscriber(modid = bcmore.MODID)
    public static final class registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar reg = event.registrar("1");

            // パリィ開始(回数と回復時間は属性から取得)
            reg.playToServer(parryc2s.TYPE, parryc2s.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer player)) return;
                if (!bcmoreconfig.ENABLE_PARRY.get()) return;
                int charges = refreshParryCharges(player);
                if (charges <= 0) return;
                if (!combatstate.tryStartParry(player.getUUID(), 0L)) return;   // クールダウンは回数側で管理
                PARRY_CHARGE_COUNT.put(player.getUUID(), charges - 1);
                PacketDistributor.sendToPlayersTrackingEntity(player, new parryanims2c(player.getUUID()));
            }));

            // 回避
            reg.playToServer(dodgec2s.TYPE, dodgec2s.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer player)) return;
                handleDodge(player, payload.forward(), payload.sideways());
            }));

            // 回避の種類の切り替え
            reg.playToClient(parryanims2c.TYPE, parryanims2c.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> com.kurimeshi.bcmore.client.bcmoreclient.onRemoteParry(payload.player())));

            reg.playToClient(parryshakes2c.TYPE, parryshakes2c.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> com.kurimeshi.bcmore.client.bcmoreclient.onParryShake(payload.weapon())));

            reg.playToClient(dodgemoves2c.TYPE, dodgemoves2c.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> com.kurimeshi.bcmore.client.bcmoreclient.onDodgeMove(
                            payload.dx(), payload.dz(), payload.distance(), payload.ramp())));

            reg.playToClient(dodgesyncs2c.TYPE, dodgesyncs2c.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> com.kurimeshi.bcmore.client.bcmoreclient.onDodgeSync(
                            payload.charges(), payload.elapsedMs())));

            reg.playToClient(dodgeanims2c.TYPE, dodgeanims2c.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> com.kurimeshi.bcmore.client.bcmoreclient.onDodgeAnim(
                            payload.player(), payload.roll())));
        }
    }

    // ---- パリィの回数まわり ----
    /** 経過時間ぶんの回復を反映して、現在の回数を返す */
    private static int refreshParryCharges(ServerPlayer player) {
        UUID id = player.getUUID();
        int max = Math.max(1, (int) Math.round(player.getAttributeValue(bcmoreattributes.PARRY_CHARGES)));
        long cd = (long) (player.getAttributeValue(bcmoreattributes.PARRY_COOLDOWN) * 1000);
        long now = combatstate.now();

        int charges = PARRY_CHARGE_COUNT.getOrDefault(id, max);
        long start = PARRY_RECHARGE_START.getOrDefault(id, now);

        if (charges >= max || cd <= 0) {
            charges = max;
            start = now;   // 満タンの間は、回復の開始時刻を現在に保つ
        } else {
            long gained = (now - start + PARRY_SYNC_TOLERANCE_MS) / cd;
            if (gained > 0) {
                charges = (int) Math.min(max, charges + gained);
                start += gained * cd;
            }
        }
        PARRY_CHARGE_COUNT.put(id, charges);
        PARRY_RECHARGE_START.put(id, start);
        return charges;
    }

    // ---- 回避の回数まわり ----
    private static int maxCharges(ServerPlayer player) {
        return Math.max(1, (int) Math.round(player.getAttributeValue(bcmoreattributes.DODGE_CHARGES)));
    }

    private static long rechargeMs(ServerPlayer player) {
        return (long) (player.getAttributeValue(bcmoreattributes.DODGE_COOLDOWN) * 1000);
    }

    /** 現在の回数と回復の進み具合を、本人のクライアントへ送る */
    private static void sendDodgeSync(ServerPlayer player) {
        UUID id = player.getUUID();
        int charges = combatstate.getCharges(id, maxCharges(player), rechargeMs(player));
        int elapsed = (int) Math.min(Integer.MAX_VALUE, combatstate.rechargeElapsed(id));
        PacketDistributor.sendToPlayer(player, new dodgesyncs2c(charges, elapsed));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendDodgeSync(player);
    }

    // ---- 回避 ----
    private static void handleDodge(ServerPlayer player, float forward, float sideways) {
        if (!bcmoreconfig.ENABLE_ROLL.get()) return;
        forward = Mth.clamp(forward, -1f, 1f);
        sideways = Mth.clamp(sideways, -1f, 1f);
        if (player.isSpectator() || player.isPassenger()) return;

        UUID id = player.getUUID();
        int mode = combatstate.getDodgeMode(id);

        long iframeMs = mode == combatstate.DODGE_STEP ? STEP_IFRAME_MS
                : mode == combatstate.DODGE_TELEPORT ? TELEPORT_IFRAME_MS : ROLL_IFRAME_MS;
        boolean ok = combatstate.tryStartDodge(id, maxCharges(player), rechargeMs(player), iframeMs);
        sendDodgeSync(player);   // 成功しても失敗しても、クライアントの表示を合わせる
        if (!ok) return;

        // 操作なしは前へ
        if (Math.abs(forward) < 0.01f && Math.abs(sideways) < 0.01f) forward = 1f;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 dir = fwd.scale(forward).add(left.scale(sideways)).normalize();

        // 空中で出したロールだけを受け身の判定に使う(着地の直前に出すと成立)
        if (mode == combatstate.DODGE_ROLL && !player.onGround()) {
            combatstate.markRoll(id);
            AIR_ROLL_DIR.put(id, dir);
        }

        double distance = player.getAttributeValue(bcmoreattributes.DODGE_DISTANCE);

        if (mode == combatstate.DODGE_TELEPORT) {
            doTeleport(player, dir, distance * 1.5);
        } else if (mode == combatstate.DODGE_ROLL) {
            double total = player.onGround() ? distance : distance * AIR_ROLL_DISTANCE_MULT;
            PacketDistributor.sendToPlayer(player,
                    new dodgemoves2c((float) dir.x, (float) dir.z, (float) total, true));
        } else {
            PacketDistributor.sendToPlayer(player,
                    new dodgemoves2c((float) dir.x, (float) dir.z, (float) (distance * 0.5), false));
        }

        player.causeFoodExhaustion(0.3f);
        if (mode == combatstate.DODGE_TELEPORT) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.6f, 0.8f);
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.6f, 1.4f);
        }

        // 回避アニメ(ロール = roll / ステップ・テレポート = step)を自分と周りに送る
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new dodgeanims2c(id, mode == combatstate.DODGE_ROLL));
    }

    /** 壁の手前まで進んで瞬間移動する。元の位置に白い残像を残す */
    private static void doTeleport(ServerPlayer player, Vec3 dir, double distance) {
        Vec3 start = player.position();
        Vec3 end = start;
        for (double d = 0.25; d <= distance; d += 0.25) {
            Vec3 cand = start.add(dir.scale(d));
            AABB box = player.getBoundingBox().move(cand.subtract(start));
            if (!player.level().noCollision(player, box)) break;
            end = cand;
        }
        if (end.distanceToSqr(start) < 0.01) return;

        if (player.level() instanceof ServerLevel level) {
            spawnAfterimage(level, start);
        }
        player.randomTeleport(end.x, end.y, end.z, false);
        player.resetFallDistance();
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL, end.x, end.y + 1.0, end.z, 20, 0.3, 0.5, 0.3, 0.1);
        }
    }

    /** 人型のシルエットに、白い粒子を密に並べる */
    private static void spawnAfterimage(ServerLevel level, Vec3 pos) {
        DustParticleOptions white = new DustParticleOptions(0xFFFFFF, 1.2f);
        for (double y = 0.0; y <= 1.8; y += 0.12) {
            double width = y > 1.45 ? 0.15 : (y > 0.7 ? 0.28 : 0.14);   // 頭 / 胴 / 脚
            level.sendParticles(white, pos.x, pos.y + y, pos.z, 4, width, 0.03, width, 0.0);
        }
    }

    // ---- 受け身(着地の直前に空中ロールで落下ダメージ無効 + 自動ロール) ----
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getDistance() <= TUMBLE_MIN_FALL) return;

        UUID id = player.getUUID();
        if (!combatstate.consumeTumble(id)) return;

        event.setDamageMultiplier(0f);   // 落下ダメージ無効
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        player.sendSystemMessage(Component.literal("受け身!"), true);

        // 着地と同時に、地上ロールを自動で出す(回数は消費しない)
        Vec3 dir = AIR_ROLL_DIR.remove(id);
        if (dir == null) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        }
        double distance = player.getAttributeValue(bcmoreattributes.DODGE_DISTANCE);
        combatstate.grantIframes(id, ROLL_IFRAME_MS);
        PacketDistributor.sendToPlayer(player,
                new dodgemoves2c((float) dir.x, (float) dir.z, (float) distance, true));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.6f, 1.2f);

        // 自動ロールのアニメ
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new dodgeanims2c(id, true));
    }

    // ---- ダメージ判定(パリィ / 回避無敵) ----
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (parryDamaging) return;   // 反撃ダメージ中は判定しない
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        if (source.getEntity() == null && source.getDirectEntity() == null) return; // 環境ダメージは対象外

        if (combatstate.isDodging(player.getUUID())) {
            event.setCanceled(true);
            return;
        }

        long windowMs = (long) (player.getAttributeValue(bcmoreattributes.PARRY_WINDOW) * 1000);
        if (combatstate.isInParryWindow(player.getUUID(), windowMs)) {
            combatstate.consumeParry(player.getUUID());
            onParrySuccess(player, source.getEntity(), event.getAmount());
            event.setCanceled(true);
        }
    }

    // ---- 攻撃制御 ----
    // パリィ中は攻撃を無効化 / 攻撃時刻を記録
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (combatstate.isParrying(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        combatstate.markAttack(player.getUUID());
    }

    // カウンター: パリィ成功後の最初の近接攻撃を 1.5 倍 + クリティカル演出
    @SubscribeEvent
    public static void onCounterDamage(LivingIncomingDamageEvent event) {
        if (parryDamaging) return;   // 反撃ダメージ自身はカウンター扱いにしない
        var source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        if (source.getDirectEntity() != attacker) return;   // 近接のみ(矢などは対象外)

        UUID id = attacker.getUUID();
        if (combatstate.isParrying(id)) {
            event.setCanceled(true);
            return;
        }
        combatstate.markAttack(id);

        if (combatstate.consumeCounter(id)) {
            event.setAmount(event.getAmount() * COUNTER_MULTIPLIER);
            LivingEntity target = event.getEntity();
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY(0.6), target.getZ(),
                        24, 0.4, 0.4, 0.4, 0.3);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        combatstate.clear(id);
        AIR_ROLL_DIR.remove(id);
        PARRY_CHARGE_COUNT.remove(id);
        PARRY_RECHARGE_START.remove(id);
    }

    // ---- パリィ成功時の演出・反動・反撃ダメージ ----

    /** 反撃ダメージ = 受けるはずだったダメージ × 倍率(属性)。最小 2.0、上限は属性 */
    private static float parryDamage(ServerPlayer player, float incoming) {
        float ratio = (float) player.getAttributeValue(bcmoreattributes.PARRY_DAMAGE_RATIO);
        float max   = (float) player.getAttributeValue(bcmoreattributes.PARRY_DAMAGE_MAX);
        return Math.min(max, Math.max(PARRY_DAMAGE_MIN, incoming * ratio));
    }

    /** 剣・ツール(ピッケル/斧/シャベル/クワ)で、石以上の耐久なら火花が出る */
    private static boolean canSpark(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean tool = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
        return tool && stack.getMaxDamage() >= SPARK_MIN_DURABILITY;
    }

    private static void onParrySuccess(ServerPlayer player, Entity attacker, float incoming) {
        if (!(player.level() instanceof ServerLevel level)) return;

        combatstate.startCounter(player.getUUID());

        // メインハンドが空でなければ「武器あり」
        boolean weapon = !player.getMainHandItem().isEmpty();

        level.playSound(null, player.blockPosition(),
                (weapon ? bcmoresounds.PARRY_WEAPON : bcmoresounds.PARRY_HAND).get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        // クライアントの放射状の火花(streak)は、石以上の剣・ツールのときだけ
        PacketDistributor.sendToPlayer(player, new parryshakes2c(canSpark(player.getMainHandItem())));

        // 火花(胸の高さ、視線方向に少し前から放射)
        Vec3 look = player.getLookAngle();
        spawnRadialSparks(level,
                player.getX() + look.x * 0.8,
                player.getY(0.7),
                player.getZ() + look.z * 0.8,
                40, 0.35);

        // 相手: 反撃ダメージ + ノックバック + 跳ねる
        if (attacker instanceof LivingEntity living) {
            float dmg = parryDamage(player, incoming);
            parryDamaging = true;
            try {
                living.hurtServer(level, player.damageSources().playerAttack(player), dmg);
            } finally {
                parryDamaging = false;
            }
            living.invulnerableTime = 0;   // 直後のカウンター攻撃が無敵時間で弾かれないように

            living.knockback(ENEMY_KNOCKBACK, player.getX() - living.getX(), player.getZ() - living.getZ());
            Vec3 av = living.getDeltaMovement();
            living.setDeltaMovement(av.x, PARRY_BOUNCE, av.z);
            living.hurtMarked = true;
        }

        // 自分: ほんの少しノックバック + 跳ねる(ノックバックの後に上方向を上書き)
        if (attacker != null) {
            player.knockback(SELF_KNOCKBACK, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
        }
        Vec3 pv = player.getDeltaMovement();
        player.setDeltaMovement(pv.x, PARRY_BOUNCE, pv.z);
        player.hurtMarked = true;
    }

    /** 中心から球状に均等な方向へ放射する(count=0 の sendParticles は 方向×speed で飛ぶ) */
    private static void spawnRadialSparks(ServerLevel level, double x, double y, double z, int amount, double speed) {
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < amount; i++) {
            double dy = 1.0 - (i / (double) (amount - 1)) * 2.0;
            double r = Math.sqrt(1.0 - dy * dy);
            double theta = golden * i;
            level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 0,
                    Math.cos(theta) * r, dy, Math.sin(theta) * r, speed);
        }
    }
}