package com.kurimeshi.bcmore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class bcmoresounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, bcmore.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY =
            SOUNDS.register("parry", () ->
                    SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(bcmore.MODID, "parry")));

    private bcmoresounds() {}
}