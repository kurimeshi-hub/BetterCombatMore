package com.kurimeshi.bcmore.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class ParrySparkParticle extends SingleQuadParticle {

    private final SpriteSet spriteSet;

    protected ParrySparkParticle(ClientLevel level, double x, double y, double z,
                                 double dx, double dy, double dz, SpriteSet spriteSet) {
        super(level, x, y, z, spriteSet.first());
        this.spriteSet = spriteSet;

        this.lifetime = 7;        // 13フレーム = 13tick で1周(1フレーム1tick)
        this.quadSize = 1f;      // 大きさ(16x16画像なので大きめ推奨)
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;

        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public void tick() {
        super.tick();
        int a = Math.min(this.age, this.lifetime);
        this.setSprite(this.spriteSet.get(a, this.lifetime));
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    // 暗所でも光らせる。不要なら削除
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;



        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz,
                                       RandomSource random) {
            try {
                return new ParrySparkParticle(level, x, y, z, dx, dy, dz, spriteSet);
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        }
    }
}