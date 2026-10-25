package com.kurimeshi.bcmore;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = bcmore.MODID)
public final class bcmoreattributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, bcmore.MODID);

    public static final DeferredHolder<Attribute, Attribute> DODGE_DISTANCE = ATTRIBUTES.register(
            "dodge_distance",
            () -> new RangedAttribute("attribute.name.bettercombatmore.dodge_distance", 2.5, 0.0, 20.0).setSyncable(true));

    /** 1回ぶんの回避が回復するまでの時間(秒) */
    public static final DeferredHolder<Attribute, Attribute> DODGE_COOLDOWN = ATTRIBUTES.register(
            "dodge_cooldown",
            () -> new RangedAttribute("attribute.name.bettercombatmore.dodge_cooldown", 1.0, 0.0, 60.0).setSyncable(true));

    /** 連続で回避できる回数 */
    public static final DeferredHolder<Attribute, Attribute> DODGE_CHARGES = ATTRIBUTES.register(
            "dodge_charges",
            () -> new RangedAttribute("attribute.name.bettercombatmore.dodge_charges", 2.0, 1.0, 10.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> PARRY_WINDOW = ATTRIBUTES.register(
            "parry_window",
            () -> new RangedAttribute("attribute.name.bettercombatmore.parry_window", 0.13, 0.0, 2.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> PARRY_COOLDOWN = ATTRIBUTES.register(
            "parry_cooldown",
            () -> new RangedAttribute("attribute.name.bettercombatmore.parry_cooldown", 0.8, 0.0, 60.0).setSyncable(true));

    private bcmoreattributes() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, DODGE_DISTANCE);
        event.add(EntityType.PLAYER, DODGE_COOLDOWN);
        event.add(EntityType.PLAYER, DODGE_CHARGES);
        event.add(EntityType.PLAYER, PARRY_WINDOW);
        event.add(EntityType.PLAYER, PARRY_COOLDOWN);
    }
}