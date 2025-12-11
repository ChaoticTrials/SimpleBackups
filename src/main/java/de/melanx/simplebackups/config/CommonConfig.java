package de.melanx.simplebackups.config;

import de.melanx.simplebackups.StorageSize;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

public class CommonConfig {

    public static final ModConfigSpec CONFIG;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final String DEFAULT_DISK_SIZE = "25 GB";
    private static final String EXPERIMENTAL_NOTE = "Experimental setting available, look at 'simplebackups-common-experimental.toml' to help testing";

    static {
        init(BUILDER);
        CONFIG = BUILDER.build();
    }

    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.EnumValue<BackupType> backupType;
    private static ModConfigSpec.BooleanValue saveAll;
    private static ModConfigSpec.IntValue fullBackupTimer;
    private static ModConfigSpec.IntValue backupsToKeep;
    private static ModConfigSpec.IntValue timer;
    private static ModConfigSpec.IntValue compressionLevel;
    private static ModConfigSpec.BooleanValue sendMessages;
    private static ModConfigSpec.ConfigValue<String> maxDiskSize;
    private static ModConfigSpec.ConfigValue<String> outputPath;
    private static ModConfigSpec.BooleanValue noPlayerBackups;
    private static ModConfigSpec.IntValue noPlayerBackupCount;
    private static ModConfigSpec.IntValue noPlayerBackupTimer;
    private static ModConfigSpec.BooleanValue createSubDirs;
    private static ModConfigSpec.BooleanValue useTickCounter;
    private static ModConfigSpec.BooleanValue collectErrors;
    private static ModConfigSpec.BooleanValue deleteUnfinishedBackup;
    private static ModConfigSpec.ConfigValue<List<? extends String>> ignoredPaths;
    private static ModConfigSpec.ConfigValue<List<? extends String>> ignoredFiles;
    private static ModConfigSpec.ConfigValue<String> ignoredFilesRegex;

    private static ModConfigSpec.BooleanValue mc2discord;
    private static ModConfigSpec.BooleanValue onlyFavorites;

    public static void init(ModConfigSpec.Builder builder) {
        enabled = builder.comment("If set false, no backups are being made.")
                .define("enabled", true);
        backupType = builder.comment(EXPERIMENTAL_NOTE,
                        "Defines the backup type.",
                        "- FULL_BACKUPS - always creates full backups",
                        "- MODIFIED_SINCE_LAST - only saves the files which changed since last (partial) backup",
                        "- MODIFIED_SINCE_FULL - saves all files which changed after the last full backup")
                .defineEnum("backupType", BackupType.FULL_BACKUPS);
        saveAll = builder.comment("Should a save-all be forced before backup?")
                .define("saveAll", true);
        fullBackupTimer = builder.comment("How often should a full backup be created if only modified files should be saved? This creates a full backup when x minutes are over and the next backup needs to be done. Once a year is default.")
                .defineInRange("fullBackupTimer", 525960, 1, 5259600);
        backupsToKeep = builder.comment(EXPERIMENTAL_NOTE, "The max amount of backup files to keep.")
                .defineInRange("backupsToKeep", 10, 1, Short.MAX_VALUE);
        timer = builder.comment("The time between two backups in minutes", "5 = each 5 minutes", "60 = each hour", "1440 = each day")
                .defineInRange("timer", 120, 1, Short.MAX_VALUE);
        compressionLevel = builder.comment("Compression level:",
                        "  0  = no compression (low CPU usage, larger files)",
                        "  9  = maximum compression (high CPU usage, smaller files)",
                        "  -1 = default; balances speed and compression (recommended)")
                .defineInRange("compressionLevel", Deflater.DEFAULT_COMPRESSION, Math.min(Deflater.DEFAULT_COMPRESSION, Deflater.NO_COMPRESSION), Deflater.BEST_COMPRESSION);
        sendMessages = builder.comment("Should message be sent when backup is in the making?")
                .define("sendMessages", true);
        maxDiskSize = builder.comment("The max size of storage the backup folder. If it takes more storage, old files will be deleted.",
                        "Needs to be written as <number><space><storage type>",
                        "Valid storage types: B, KB, MB, GB, TB")
                .define("maxDiskSize", DEFAULT_DISK_SIZE);
        outputPath = builder.comment("Used to define the output path.")
                .define("outputPath", "simplebackups");
        noPlayerBackups = builder.comment("Create backups, even if nobody is online")
                .define("noPlayerBackups", false);
        noPlayerBackupCount = builder.comment("How many backups should be created after the last player left? Set to 0 to disable.")
                .defineInRange("noPlayerBackupCount", 1, 0, Integer.MAX_VALUE);
        noPlayerBackupTimer = builder.comment("The time between two backups when no player is online. 0 means the same as the \"timer\" setting.")
                .defineInRange("noPlayerBackupTimer", 0, 0, Integer.MAX_VALUE);
        createSubDirs = builder.comment("Should sub-directories be generated for each world?",
                        "Keep in mind that all configs above, including backupsToKeep and maxDiskSize, will be calculated for each sub directory.")
                .define("createSubDirs", true);
        useTickCounter = builder.comment("Use an internal tick counter instead of the real world time. The value of the timer will be converted to ticks. When the timer is over, the backup will be created.",
                        "Keep in mind that lagging servers will result in larger gaps between two backups, e.g. 10 FPS in average will result in double the time set between backups.")
                .define("useTickCounter", false);

        builder.comment("WARNING This configuration should stay as default if backups are not monitored properly.")
                .push("error_handling");
        collectErrors = builder.comment("I/O Errors will be collected and print into the chat. The mod tries to complete the backup with as many files as possible. Otherwise, it will abort after the first error.")
                .define("collectErrors", true);
        deleteUnfinishedBackup = builder.comment("If collectErrors is 'false', backups with errors will be deleted immediately to prevent deleting valid backups.")
                .define("deleteUnfinishedBackup", true);
        builder.pop();

        builder.comment("WARNING Please check your configuration before using permanently.",
                        "The backup system will ignore these paths and files.")
                .push("to_ignore");
        ignoredPaths = builder.comment("All directories that should be excluded from backups",
                        "Format: Enter paths relative to the world directory (e.g., 'logs', 'data/cache')",
                        "All files within these directories will also be excluded")
                .defineList("ignored_paths", List.of(), () -> "", obj -> obj instanceof String);
        ignoredFiles = builder.comment("Specific files that should be excluded from backups",
                        "Format: Enter complete file paths relative to the world directory (e.g., 'level.dat_old', 'stats/player.json')",
                        "Use this for individual files rather than entire directories")
                .defineList("ignored_files", List.of(), () -> "", obj -> obj instanceof String);
        ignoredFilesRegex = builder.comment("Regular expression pattern to exclude matching files from backups",
                        "All files with paths matching this pattern will be skipped",
                        "Example: '.*\\.temp$' excludes all files ending with .temp",
                        "Leave empty to disable regex-based file exclusion")
                .define("ignored_files_regex", "");
        builder.pop();

        builder.push("mod_compat");
        mc2discord = builder.comment("Should backup notifications be sent to Discord by using mc2discord? (needs to be installed)")
                .define("mc2discord", true);
        onlyFavorites = builder.comment("Should only worlds be backed up that are marked as favorite by Cherished Worlds mod?")
                .define("onlyFavorites", false);
        builder.pop();
    }

    public static boolean isEnabled() {
        return enabled.get();
    }

    public static int getBackupsToKeep() {
        return ExperimentalConfig.isEnabled() ? ExperimentalConfig.backupChainsToKeep() : backupsToKeep.get();
    }

    @Deprecated
    // converts config value from milliseconds to minutes
    public static long getTimer() {
        return CommonConfig.getTimer(true);
    }

    public static long getTimer(boolean arePlayersOnline) {
        int i = noPlayerBackupTimer.get();
        if (i == 0) {
            i = timer.get();
        }

        return (long) (arePlayersOnline ? timer.get() : i) * 60 * 1000;
    }

    // converts config value from milliseconds to minutes
    public static long getFullBackupTimer() {
        return (long) fullBackupTimer.get() * 60 * 1000;
    }

    public static int getCompressionLevel() {
        return compressionLevel.get();
    }

    public static long getMaxDiskSize() {
        String s = maxDiskSize.get();
        if (s.split(" ").length != 2) {
            s = DEFAULT_DISK_SIZE;
        }

        return StorageSize.getBytes(s);
    }

    public static Path getOutputPath(@Nullable String levelId) {
        Path base = Paths.get(outputPath.get());
        boolean withSubDir = levelId != null && !levelId.isEmpty() && createSubDirs.get();
        try {
            return withSubDir ? base.toRealPath().resolve(levelId) : base.toRealPath();
        } catch (IOException e) {
            return withSubDir ? base.resolve(levelId) : base;
        }
    }

    public static boolean doNoPlayerBackups() {
        return noPlayerBackups.get();
    }

    public static int noPlayerBackupCount() {
        return noPlayerBackupCount.get();
    }

    public static BackupType backupType() {
        return ExperimentalConfig.isEnabled() ? ExperimentalConfig.backupType().asLegacyType() : backupType.get();
    }

    public static boolean saveAll() {
        return saveAll.get();
    }

    public static boolean sendMessages() {
        return sendMessages.get();
    }

    public static boolean collectErrors() {
        return collectErrors.get();
    }

    public static boolean deleteUnfinishedBackup() {
        return deleteUnfinishedBackup.get();
    }

    public static List<Path> getIgnoredPaths() {
        List<Path> paths = new ArrayList<>();
        for (String path : ignoredPaths.get()) {
            paths.add(Path.of(path));
        }

        return paths;
    }

    public static List<Path> getIgnoredFiles() {
        List<Path> paths = new ArrayList<>();
        for (String path : ignoredFiles.get()) {
            paths.add(Path.of(path));
        }

        return paths;
    }

    public static String getIgnoredFilesRegex() {
        return ignoredFilesRegex.get();
    }

    public static boolean useTickCounter() {
        return useTickCounter.get();
    }

    public static boolean mc2discord() {
        return mc2discord.get();
    }

    public static boolean onlyFavorites() {
        return onlyFavorites.get();
    }
}
