package com.kurimeshi.bcmore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class bcmoresounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, bcmore.MODID);

    /** 旧パリィ音(現在は使っていないが、互換のため残してある) */
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY =
            SOUNDS.register("parry", () ->
                    SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(bcmore.MODID, "parry")));

    /** パリィ成功音: 武器あり */
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_WEAPON =
            SOUNDS.register("parry_weapon", () ->
                    SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_weapon")));

    /** パリィ成功音: 素手 */
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_HAND =
            SOUNDS.register("parry_hand", () ->
                    SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(bcmore.MODID, "parry_hand")));

    private bcmoresounds() {}
}