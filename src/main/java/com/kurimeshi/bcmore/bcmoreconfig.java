package com.kurimeshi.bcmore;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class bcmoreconfig {
    public enum dodgemode { ROLL, STEP, TELEPORT }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<dodgemode> DEFAULT_DODGE_MODE;
    public static final ModConfigSpec.BooleanValue ENABLE_PARRY;
    public static final ModConfigSpec.BooleanValue ENABLE_ROLL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("dodge");
        DEFAULT_DODGE_MODE = b
                .comment("Initial evasion types (ROLL / STEP / TELEPORT)")
                .defineEnum("defaultDodgeMode", dodgemode.ROLL);
        b.pop();

        b.push("features");
        ENABLE_PARRY = b
                .comment("Enable parry (false: key, gauge and effects are all disabled)")
                .define("enableParry", true);
        ENABLE_ROLL = b
                .comment("Enable dodge (roll / step / teleport) (false: key and gauge are disabled)")
                .define("enableRoll", true);
        b.pop();
        SPEC = b.build();
    }

    private bcmoreconfig() {}
}