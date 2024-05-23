package de.melanx.simplebackups.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {

    public static final ModConfigSpec CONFIG;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        init(BUILDER);
        CONFIG = BUILDER.build();
    }

    private static ModConfigSpec.BooleanValue commandsCheatsDisabled;
    private static ModConfigSpec.BooleanValue messagesForEveryone;

    public static void init(ModConfigSpec.Builder builder) {
        commandsCheatsDisabled = builder.comment("Should commands work without cheats enabled? Mainly recommended for single player, otherwise all users on servers can trigger commands.")
                .define("commandsCheatsDisabled", false);
        messagesForEveryone = builder.comment("Should all users receive the backup message? Disable to only have users with permission level 2 and higher receive them.")
                .define("messagesForEveryone", true);
    }

    public static boolean commandsCheatsDisabled() {
        return commandsCheatsDisabled.get();
    }

    public static boolean messagesForEveryone() {
        return messagesForEveryone.get();
    }
}
