package de.melanx.simplebackups.config;

import de.melanx.simplebackups.StorageSize;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CommonConfig {

    public static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        init(BUILDER);
        CONFIG = BUILDER.build();
    }

    private static ForgeConfigSpec.BooleanValue enabled;
    private static ForgeConfigSpec.BooleanValue onlyModified;
    private static ForgeConfigSpec.IntValue backupsToKeep;
    private static ForgeConfigSpec.IntValue timer;
    private static ForgeConfigSpec.BooleanValue sendMessages;
    private static ForgeConfigSpec.ConfigValue<String> maxDiskSize;
    private static ForgeConfigSpec.ConfigValue<String> outputPath;

    private static ForgeConfigSpec.BooleanValue mc2discord;

    public static void init(ForgeConfigSpec.Builder builder) {
        enabled = builder.comment("If set true, no backups are being made.")
                .define("enabled", true);
        onlyModified = builder.comment("Should only changed files be backed up? Useful for large worlds. Keep in mind that old backups are required for a complete backup. Alternatively, you could run the command to create a new complete backup.")
                .define("onlyModified", false);
        backupsToKeep = builder.comment("The max amount of backup files to keep.")
                .defineInRange("backupsToKeep", 10, 1, Short.MAX_VALUE);
        timer = builder.comment("The time between two backups in minutes", "5 = each 5 minutes", "60 = each hour", "1440 = each day")
                .defineInRange("timer", 120, 1, Short.MAX_VALUE);
        sendMessages = builder.comment("Should message be sent when backup is in the making?")
                .define("sendMessages", true);
        maxDiskSize = builder.comment("The max size of storage the backup folder. If it takes more storage, old files will be deleted.",
                        "Needs to be written as <number><space><storage type>",
                        "Valid storage types: B, KB, MB, GB, TB")
                .define("maxDiskSize", "25 GB");
        outputPath = builder.comment("Used to define the output path.")
                .define("outputPath", "simplebackups");

        builder.push("mod_compat");
        mc2discord = builder.comment("Should backup notifications be sent to Discord by using mc2discord? (needs to be installed)")
                .define("mc2discord", true);
        builder.pop();
    }

    public static boolean isEnabled() {
        return enabled.get();
    }

    public static int getBackupsToKeep() {
        return backupsToKeep.get();
    }

    // converts config value from milliseconds to minutes
    public static int getTimer() {
        return timer.get() * 60 * 1000;
    }

    public static long getMaxDiskSize() {
        String s = maxDiskSize.get();
        if (s.split(" ").length != 2) {
            s = "25 GB";
        }

        return StorageSize.getBytes(s);
    }

    public static Path getOutputPath() {
        try {
            return Paths.get(outputPath.get()).toRealPath();
        } catch (IOException e) {
            return Paths.get(outputPath.get());
        }
    }

    public static boolean onlyModified() {
        return onlyModified.get();
    }

    public static boolean sendMessages() {
        return sendMessages.get();
    }

    public static boolean mc2discord() {
        return mc2discord.get();
    }
}
