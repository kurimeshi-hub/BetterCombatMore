package com.kurimeshi.bcmore.client;

import com.kurimeshi.bcmore.bcmoreparticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class ParryStreakParticle extends SingleQuadParticle {

    private static final int LIFETIME = 13;
    private static final float START_SIZE = 0.02f;
    private static final float FRICTION = 0.80f;
    private static final float GRAVITY = 0.0f;          // 落ちる強さ(大きいほどよく落ちる)
    private static final double MAX_SPEED = 0.30;
    private static final double SPACING_JITTER = 0.8;   // 間隔のばらつき(0 = 等間隔 / 1 = 隣の粒と重なるくらい)

    // ---- 色(テクスチャは白前提。色はコードで掛ける) ----
    private static final float[] ORANGE = {1.0f, 0.55f, 0.10f};
    private static final float[] YELLOW = {1.0f, 0.90f, 0.20f};
    private static final float[] WHITE  = {1.0f, 1.00f, 1.00f};
    private static final float[][][] PALETTES = {
            {ORANGE, YELLOW},   // オレンジ → 黄色
            {YELLOW, WHITE},    // 黄色 → 白
            {YELLOW, YELLOW}    // 黄色のみ
    };

    // burst() が「この線の色」を渡すための一時置き場(addParticle は同期でここを呼ぶ)
    private static int pendingPalette = -1;

    private final float[] colorFrom;
    private final float[] colorTo;

    protected ParryStreakParticle(ClientLevel level, double x, double y, double z,
                                  double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.lifetime = LIFETIME;
        this.gravity = GRAVITY;
        this.hasPhysics = true;          // 壁・床に当たる
        this.setSize(0.02f, 0.02f);      // 当たり判定を小さくして、壁ぎりぎりまで進めるように
        this.friction = FRICTION;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;

        int idx = pendingPalette >= 0 ? pendingPalette : this.random.nextInt(PALETTES.length);
        this.colorFrom = PALETTES[idx][0];
        this.colorTo = PALETTES[idx][1];
        this.setColor(colorFrom[0], colorFrom[1], colorFrom[2]);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float p = (this.age + partialTicks) / this.lifetime;
        return START_SIZE * (1.0f - p);
    }

    @Override
    public void tick() {
        double beforeX = this.xd;
        double beforeZ = this.zd;

        super.tick();

        // 壁に当たったら横方向の動きを完全に止める(あとは重力で落ちる)
        if ((beforeX != 0 && this.xd == 0) || (beforeZ != 0 && this.zd == 0)) {
            this.xd = 0;
            this.zd = 0;
        }

        // 時間とともに色を変える
        float p = Mth.clamp(this.age / (float) this.lifetime, 0f, 1f);
        this.setColor(
                Mth.lerp(p, colorFrom[0], colorTo[0]),
                Mth.lerp(p, colorFrom[1], colorTo[1]),
                Mth.lerp(p, colorFrom[2], colorTo[2]));
        this.setAlpha(1.0f);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.bySprite(this.sprite);
    }

    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    /** parryspark と完全に同じ位置（pos）を基準にして放射する */
    public static void burst(ClientLevel level, Vec3 pos, int count) {
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0; // 黄金比
        RandomSource rnd = level.getRandom();

        try {
            for (int i = 0; i < count; i++) {
                double y = 1.0 - (i / (double)(count - 1)) * 2.0;
                double radiusAtY = Math.sqrt(1.0 - y * y);
                double theta = 2.0 * Math.PI * i / phi;

                Vec3 dir = new Vec3(
                        Math.cos(theta) * radiusAtY,
                        y,
                        Math.sin(theta) * radiusAtY
                );

                pendingPalette = rnd.nextInt(PALETTES.length);   // この1本の色を決める

                int trailLength = 80;
                double totalLength = 0.9;

                for (int j = 0; j < trailLength; j++) {
                    // 粒どうしの間隔を少しランダムにする(全体の長さは変えない)
                    double jitter = (rnd.nextDouble() - 0.5) * SPACING_JITTER;
                    double ratio = Mth.clamp((j + jitter) / (trailLength - 1), 0.0, 1.0);

                    double offset = ratio * totalLength;
                    double px = pos.x + dir.x * offset;
                    double py = pos.y + dir.y * offset;
                    double pz = pos.z + dir.z * offset;

                    double speed = MAX_SPEED * ratio;

                    level.addParticle(bcmoreparticles.PARRY_STREAK.get(),
                            px, py, pz,
                            dir.x * speed, dir.y * speed, dir.z * speed);
                }
            }
        } finally {
            pendingPalette = -1;
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz,
                                       RandomSource random) {
            return new ParryStreakParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}