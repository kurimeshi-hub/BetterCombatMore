package com.kurimeshi.bcmore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class combatstate {
    public static final long PARRY_LOCK_MS = 417;       // パリィ中に攻撃できない時間(アニメの長さ)
    public static final long ATTACK_LOCK_MS = 450;      // 攻撃後にパリィできない時間
    public static final long COUNTER_WINDOW_MS = 1200;  // パリィ成功後のカウンター有効時間
    public static final long TUMBLE_WINDOW_MS = 250;    // 着地の何ms前までの空中ロールが受け身になるか

    // 回避の種類
    public static final int DODGE_ROLL = 0;
    public static final int DODGE_STEP = 1;
    public static final int DODGE_TELEPORT = 2;
    public static final String[] DODGE_NAMES = {"ロール", "ステップ", "テレポート"};

    private static final Map<UUID, Long> PARRY_START = new HashMap<>();
    private static final Map<UUID, Long> PARRY_CD = new HashMap<>();
    private static final Map<UUID, Long> LAST_ATTACK = new HashMap<>();
    private static final Map<UUID, Long> COUNTER_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> DODGE_IFRAME_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> DODGE_MODE = new HashMap<>();
    private static final Map<UUID, Long> LAST_ROLL = new HashMap<>();

    // 回避の回数制(現在の回数 / 今の1回ぶんの回復を開始した時刻)
    private static final Map<UUID, Integer> DODGE_CHARGES = new HashMap<>();
    private static final Map<UUID, Long> DODGE_RECHARGE_START = new HashMap<>();

    private combatstate() {}

    public static long now() { return System.nanoTime() / 1_000_000L; }

    // ---- パリィ ----
    public static boolean tryStartParry(UUID id, long cooldownMs) {
        long n = now();
        if (PARRY_CD.getOrDefault(id, 0L) > n) return false;
        if (isAttacking(id)) return false;
        PARRY_START.put(id, n);
        PARRY_CD.put(id, n + cooldownMs);
        return true;
    }

    public static boolean isInParryWindow(UUID id, long windowMs) {
        Long start = PARRY_START.get(id);
        if (start == null) return false;
        long elapsed = now() - start;
        return elapsed >= 0 && elapsed <= windowMs;
    }

    public static boolean isParrying(UUID id) {
        Long start = PARRY_START.get(id);
        return start != null && now() - start < PARRY_LOCK_MS;
    }

    public static void consumeParry(UUID id) {
        PARRY_START.remove(id);   // 攻撃ロックは解除するが、パリィのクールダウンは残す
    }
    // ---- 攻撃 ----
    public static void markAttack(UUID id) { LAST_ATTACK.put(id, now()); }

    public static boolean isAttacking(UUID id) {
        return now() - LAST_ATTACK.getOrDefault(id, 0L) < ATTACK_LOCK_MS;
    }

    // ---- カウンター ----
    public static void startCounter(UUID id) { COUNTER_UNTIL.put(id, now() + COUNTER_WINDOW_MS); }

    public static boolean consumeCounter(UUID id) {
        Long until = COUNTER_UNTIL.remove(id);
        return until != null && until > now();
    }

    // ---- 受け身(空中ロール) ----
    public static void markRoll(UUID id) { LAST_ROLL.put(id, now()); }

    /** 受け身が成立していれば true を返して消費する */
    public static boolean consumeTumble(UUID id) {
        Long t = LAST_ROLL.remove(id);
        return t != null && now() - t <= TUMBLE_WINDOW_MS;
    }

    /** 最後にロールしてからの経過ms(記録がなければ -1) */
    public static long msSinceRoll(UUID id) {
        Long t = LAST_ROLL.get(id);
        return t == null ? -1 : now() - t;
    }

    // ---- 回避(回数制) ----

    /** 経過時間ぶんの回復を反映する */
    private static void refill(UUID id, int max, long cdMs) {
        int c = Math.min(DODGE_CHARGES.getOrDefault(id, max), max);
        long n = now();

        if (c >= max || cdMs <= 0) {
            c = max;
            DODGE_RECHARGE_START.remove(id);
        } else {
            long start = DODGE_RECHARGE_START.getOrDefault(id, n);
            long gained = (n - start) / cdMs;
            if (gained > 0) {
                c = (int) Math.min(max, c + gained);
                start += gained * cdMs;
            }
            if (c >= max) DODGE_RECHARGE_START.remove(id);
            else DODGE_RECHARGE_START.put(id, start);
        }
        DODGE_CHARGES.put(id, c);
    }

    public static boolean tryStartDodge(UUID id, int max, long cdMs, long iframeMs) {
        refill(id, max, cdMs);
        int c = DODGE_CHARGES.get(id);
        if (c <= 0) return false;

        long n = now();
        if (c >= max) DODGE_RECHARGE_START.put(id, n);   // 満タンからの消費で、回復が始まる
        DODGE_CHARGES.put(id, c - 1);
        DODGE_IFRAME_UNTIL.put(id, n + iframeMs);
        return true;
    }

    public static int getCharges(UUID id, int max, long cdMs) {
        refill(id, max, cdMs);
        return DODGE_CHARGES.get(id);
    }

    /** 今の1回ぶんの回復が始まってからの経過ms(満タンなら 0) */
    public static long rechargeElapsed(UUID id) {
        Long s = DODGE_RECHARGE_START.get(id);
        return s == null ? 0 : now() - s;
    }

    public static boolean isDodging(UUID id) {
        return DODGE_IFRAME_UNTIL.getOrDefault(id, 0L) > now();
    }

    /** 回数を消費せずに無敵時間だけ付与する(受け身の自動ロール用) */
    public static void grantIframes(UUID id, long ms) {
        DODGE_IFRAME_UNTIL.put(id, now() + ms);
    }

    public static int getDodgeMode(UUID id) {
        Integer m = DODGE_MODE.get(id);
        return m != null ? m : defaultDodgeMode();
    }

    /** config の初期値(config が未ロードならロール) */
    private static int defaultDodgeMode() {
        try {
            return bcmoreconfig.DEFAULT_DODGE_MODE.get().ordinal();
        } catch (IllegalStateException e) {
            return DODGE_ROLL;
        }
    }

    /** 別 MOD などから回避の種類を切り替えるための口(DODGE_ROLL / DODGE_STEP / DODGE_TELEPORT) */
    public static void setDodgeMode(UUID id, int mode) {
        if (mode < 0 || mode >= DODGE_NAMES.length) return;
        DODGE_MODE.put(id, mode);
    }

    public static int cycleDodgeMode(UUID id) {
        int next = (getDodgeMode(id) + 1) % DODGE_NAMES.length;
        DODGE_MODE.put(id, next);
        return next;
    }

    public static void clear(UUID id) {
        PARRY_START.remove(id);
        PARRY_CD.remove(id);
        LAST_ATTACK.remove(id);
        COUNTER_UNTIL.remove(id);
        DODGE_IFRAME_UNTIL.remove(id);
        DODGE_MODE.remove(id);
        LAST_ROLL.remove(id);
        DODGE_CHARGES.remove(id);
        DODGE_RECHARGE_START.remove(id);
    }
}