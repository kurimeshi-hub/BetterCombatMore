package com.kurimeshi.bcmore;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class bcmoreconfig {
    public enum dodgemode { ROLL, STEP, TELEPORT }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<dodgemode> DEFAULT_DODGE_MODE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("dodge");
        DEFAULT_DODGE_MODE = b
                .comment("Initial evasion types (ROLL / STEP / TELEPORT)")
                .defineEnum("defaultDodgeMode", dodgemode.ROLL);
        b.pop();
        SPEC = b.build();
    }

    private bcmoreconfig() {}
}