package com.kurimeshi.bcmore;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class bcmoreparticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, bcmore.MODID);

    public static final Supplier<SimpleParticleType> PARRY_SPARK =
            PARTICLE_TYPES.register("parry_spark", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> PARRY_STREAK =
            PARTICLE_TYPES.register("parry_streak", () -> new SimpleParticleType(false));
}