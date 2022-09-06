package de.melanx.simplebackups.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {

    public static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        init(BUILDER);
        CONFIG = BUILDER.build();
    }

    private static ForgeConfigSpec.BooleanValue commandsCheatsDisabled;

    public static void init(ForgeConfigSpec.Builder builder) {
        commandsCheatsDisabled = builder.comment("Should commands work without cheats enabled? Mainly recommended for single player, otherwise all users on servers can trigger commands.")
                .define("commandsCheatsDisabled", false);
    }

    public static boolean commandsCheatsDisabled() {
        return commandsCheatsDisabled.get();
    }
}
