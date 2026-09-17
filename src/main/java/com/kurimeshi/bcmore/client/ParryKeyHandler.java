package com.kurimeshi.bcmore.client;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class WeaponQuery {

    /** メインハンドの武器がtwo_handed指定かどうか */
    public static boolean isMainHandTwoHanded(Player player) {
        WeaponAttributes attrs = getAttributes(player.getMainHandItem());
        return attrs != null && attrs.isTwoHanded();
    }

    /** 両手とも武器が登録されていて、どちらもtwo_handedでない = Better Combatが二刀流として扱っている状態 */
    public static boolean isDualWielding(Player player) {
        WeaponAttributes main = getAttributes(player.getMainHandItem());
        WeaponAttributes off = getAttributes(player.getOffhandItem());
        return main != null && off != null
                && !main.isTwoHanded() && !off.isTwoHanded();
    }

    private static WeaponAttributes getAttributes(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem());
        return WeaponRegistry.registrations.get(itemId); // 要確認: publicなgetterがあるならそちらを優先
    }
}