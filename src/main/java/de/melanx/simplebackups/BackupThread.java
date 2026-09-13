package de.melanx.simplebackups;

import de.melanx.simplebackups.compat.CherishedWorldsCompat;
import de.melanx.simplebackups.compat.Mc2DiscordCompat;
import de.melanx.simplebackups.compression.CompressionBase;
import de.melanx.simplebackups.config.BackupType;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import de.melanx.simplebackups.exception.NotEnoughDiskSpaceException;
import de.melanx.simplebackups.network.Pause;
import de.melanx.simplebackups.sbk.SbkException;
import net.minecraft.ChatFormatting;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BackupThread extends Thread {

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('_')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral('-')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral('-')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();
    private static final int LOG_BUFFER_SIZE = 8192;
    public static final Logger LOGGER = LoggerFactory.getLogger(BackupThread.class);
    private final MinecraftServer server;
    private final boolean quiet;
    private final CompressionBase.BackupFormat format;
    private final long lastSaved;
    private final boolean fullBackup;
    private final LevelStorageSource.LevelStorageAccess storageSource;
    private final Path backupPath;
    private final BackupChainManager manager;
    private boolean forceFullBackup = false;

    private BackupThread(@Nonnull MinecraftServer server, boolean quiet, BackupData backupData) {
        this(server, quiet, backupData, CommonConfig.getBackupFormat());
    }

    private BackupThread(@Nonnull MinecraftServer server, boolean quiet, BackupData backupData, CompressionBase.BackupFormat format) {
        this.server = server;
        this.storageSource = server.storageSource;
        this.quiet = quiet;
        this.format = format;
        if (backupData == null) {
            this.lastSaved = 0;
            this.fullBackup = true;
        } else {
            long now = CommonConfig.useTickCounter() ? server.overworld().getGameTime() : System.currentTimeMillis();
            this.lastSaved = CommonConfig.backupType() == BackupType.INCREMENTAL ? backupData.getLastSaved() : backupData.getLastFullBackup();
            this.fullBackup = CommonConfig.backupType() == BackupType.FULL_BACKUPS || (now - CommonConfig.getFullBackupTimer()) > backupData.getLastFullBackup();
        }
        this.setName("SimpleBackups");
        this.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        String levelId = this.storageSource.getLevelId();
        this.backupPath = CommonConfig.getOutputPath(levelId);
        this.manager = BackupChainManager.get(levelId);
    }

    public static boolean tryCreateBackup(MinecraftServer server) {
        BackupData backupData = BackupData.get(server);
        if (BackupThread.shouldRunBackup(server)) {
            BackupThread thread = new BackupThread(server, false, backupData);
            thread.start();
            long currentTime = CommonConfig.useTickCounter() ? server.overworld().getGameTime() : System.currentTimeMillis();
            backupData.updateSaveTime(currentTime);
            if (thread.createFullBackup()) {
                backupData.updateFullBackupTime(currentTime);
            }

            return true;
        }

        return false;
    }

    public static boolean shouldRunBackup(MinecraftServer server) {
        BackupData backupData = BackupData.get(server);
        if (!CommonConfig.isEnabled() || CommonConfig.backupsDisabledByJvmArg() || backupData.isPaused()) {
            return false;
        }

        if (CherishedWorldsCompat.isLoaded() && !CherishedWorldsCompat.isFavorite(server.storageSource.getLevelId())) {
            return false;
        }

        boolean arePlayersOnline = !server.getPlayerList().getPlayers().isEmpty();

        if (CommonConfig.useTickCounter()) {
            long gameTime = server.overworld().getGameTime();
            long lastSaved = backupData.getLastSaved();
            // convert timer from minutes into ticks
            long timer = CommonConfig.getTimer(arePlayersOnline) * 20 * 60;
            return gameTime - lastSaved >= timer;
        }

        return System.currentTimeMillis() - CommonConfig.getTimer(arePlayersOnline) > backupData.getLastSaved();
    }

    public static void createBackup(MinecraftServer server, boolean quiet, CompressionBase.BackupFormat format) {
        BackupThread thread = new BackupThread(server, quiet, null, format);
        thread.start();
    }

    public void deleteFiles() {
        if (this.manager.getChains().isEmpty()) {
            return;
        }

        int maxChains = CommonConfig.getBackupsToKeep();
        while (this.manager.getChains().size() > maxChains) {
            BackupChain chain = this.manager.getFirstChain();
            LOGGER.info("Deleting backup chain directory \"{}\"", chain.getParentFolder());
            this.manager.removeChain(chain);
        }
    }

    public void saveStorageSize() {
        try {
            while (this.manager.getFileSize() > CommonConfig.getMaxDiskSize()) {
                List<BackupChain> chains = this.manager.getChains();
                if (chains.size() <= 1) {
                    LOGGER.error("Cannot delete old chains to save disk space. Only one chain directory left!");
                    return;
                }

                BackupChain victim = chains.getFirst();
                LOGGER.info("Deleting backup chain directory \"{}\" to save disk space", victim.getParentFolder());
                this.manager.removeChain(victim);
            }
        } catch (NullPointerException e) {
            LOGGER.error("Cannot delete old files to save disk space", e);
        }
    }

    @Override
    public void run() {
        try {
            Path backupFilePath = this.getChainBackupFilePath();
            Path latestLogPath = this.server.getFile("logs/latest.log");
            LogSnapshot latestLogSnapshot = this.getLogSnapshot(latestLogPath);
            BackupResult backupResult = null;
            String time = null;

            try {
                this.deleteFiles();

                Files.createDirectories(this.backupPath);
                long start = System.currentTimeMillis();
                this.broadcast("simplebackups.backup_started", Style.EMPTY.withColor(ChatFormatting.GOLD));
                backupResult = CompressionBase.makeBackup(this.server, this.storageSource, this.backupPath, backupFilePath, this.createFullBackup(), this.format, this.lastSaved);
                long end = System.currentTimeMillis();
                time = Timer.getTimer(end - start);

            } catch (NotEnoughDiskSpaceException e) {
                BackupThread.this.broadcast("simplebackups.not_enough_space", Style.EMPTY.withColor(ChatFormatting.RED));
                Files.deleteIfExists(backupFilePath);
            } catch (IOException | SbkException e) {
                if (CommonConfig.deleteUnfinishedBackup()) {
                    this.broadcast("simplebackups.backup_failed_delete", Style.EMPTY.withColor(ChatFormatting.RED));
                    Files.deleteIfExists(backupFilePath);
                } else {
                    this.broadcast("simplebackups.backup_failed_continue", Style.EMPTY.withColor(ChatFormatting.RED));
                }

                SimpleBackups.LOGGER.error("Error backing up", e);
            }

            if (backupResult != null) {
                this.addBackupLog(backupFilePath, latestLogPath, latestLogSnapshot);
                long backupFileSize = backupResult.fileSize();
                this.saveStorageSize();

                this.broadcast("simplebackups.backup_finished", Style.EMPTY.withColor(backupResult.hasErrors() ? ChatFormatting.YELLOW : ChatFormatting.GOLD),
                        time, StorageSize.getFormattedSize(backupFileSize), StorageSize.getFormattedSize(StorageSize.getFolderSize(this.backupPath)));

                if (backupResult.hasErrors()) {
                    MutableComponent erroredFiles = Component.literal(backupResult.errors().stream()
                            .map(file -> "- " + file.toString())
                            .collect(Collectors.joining("\n"))
                    );

                    this.broadcast("simplebackups.backup_errors", Style.EMPTY.withColor(ChatFormatting.RED)
                            .withHoverEvent(new HoverEvent.ShowText(erroredFiles)), backupResult.errors().size());
                    BackupThread.LOGGER.error("Skipped {} files during backup because of errors:", backupResult.errors().size());
                    for (Path failedFile : backupResult.errors()) {
                        BackupThread.LOGGER.error(" - {}", failedFile);
                    }
                }
            }

            // Overwrite existing for the new log lines like errors and finished
            this.addBackupLog(backupFilePath, latestLogPath, latestLogSnapshot);
            this.saveStorageSize();
        } catch (IOException e) {
            SimpleBackups.LOGGER.error("Error backing up", e);
        }
    }

    private Path getChainBackupFilePath() throws IOException {
        Path worldBackupDir = this.backupPath;
        Files.createDirectories(Files.exists(worldBackupDir) ? worldBackupDir.toRealPath() : worldBackupDir);
        String baseName = LocalDateTime.now().format(FORMATTER);
        BackupChain latestChain = this.manager.getLatestChain();

        if (this.fullBackup || latestChain == null || latestChain.getFormat() != this.format) {
            this.forceFullBackup = true;
            BackupChain chain = this.manager.createChain(baseName, this.format);
            return chain.getFullBackup();
        }

        return latestChain.createChild();
    }

    private void broadcast(String message, Style style, Object... parameters) {
        //noinspection UnstableApiUsage,StringConcatenationArgumentToLogCall
        SimpleBackups.LOGGER.info(String.format(FMLTranslations.getPattern(message, () -> message), parameters));
        if (CommonConfig.sendMessages() && !this.quiet) {
            this.server.execute(() -> {
                this.server.getPlayerList().getPlayers().forEach(player -> {
                    if (ServerConfig.messagesForEveryone() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                        player.sendSystemMessage(BackupThread.component(player, message, parameters).withStyle(style));
                    }
                });
            });

            if (Mc2DiscordCompat.isLoaded() && CommonConfig.mc2discord()) {
                Mc2DiscordCompat.announce(BackupThread.component(null, message, parameters));
            }
        }
    }

    public static MutableComponent component(@Nullable ServerPlayer player, String key, Object... parameters) {
        if (player != null) {
            //noinspection UnstableApiUsage
            if (NetworkRegistry.hasChannel(player.connection.connection, null, Pause.ID)) {
                return Component.translatable(key, parameters);
            }
        }

        //noinspection UnstableApiUsage
        return Component.literal(String.format(FMLTranslations.getPattern(key, () -> key), parameters));
    }

    private LogSnapshot getLogSnapshot(Path latestLogPath) {
        if (!CommonConfig.captureLatestLog()) {
            return LogSnapshot.disabled();
        }

        try {
            if (!Files.exists(latestLogPath)) {
                return new LogSnapshot(0L, null);
            }

            BasicFileAttributes attributes = Files.readAttributes(latestLogPath, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                return LogSnapshot.disabled();
            }

            return new LogSnapshot(attributes.size(), attributes.fileKey());
        } catch (IOException e) {
            LOGGER.warn("Failed to read latest.log metadata from {}", latestLogPath, e);
            return LogSnapshot.disabled();
        }
    }

    private void addBackupLog(Path outputFile, Path latestLogPath, LogSnapshot logSnapshot) {
        if (!logSnapshot.isEnabled() || !Files.isRegularFile(latestLogPath)) {
            return;
        }

        try {
            BasicFileAttributes attributes = Files.readAttributes(latestLogPath, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                return;
            }

            long startOffset = logSnapshot.hasRolledOver(attributes) ? 0L : logSnapshot.offset();
            long bytesToCopy = attributes.size() - startOffset;
            if (bytesToCopy <= 0L) {
                return;
            }

            Path logFile = this.getLogPath(outputFile);
            try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(latestLogPath));
                 OutputStream outputStream = new BufferedOutputStream(
                         Files.newOutputStream(
                                 logFile,
                                 StandardOpenOption.CREATE,
                                 StandardOpenOption.TRUNCATE_EXISTING,
                                 StandardOpenOption.WRITE
                         )
                 )) {
                this.skip(inputStream, startOffset);
                this.copy(inputStream, outputStream, bytesToCopy);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write log file for backup", e);
        }
    }

    private Path getLogPath(Path outputFile) {
        String filename = outputFile.getFileName().toString();
        String ext = this.format.getExtension();
        String stem = filename.endsWith(ext) ? filename.substring(0, filename.length() - ext.length()) : filename;
        Path parent = outputFile.getParent();

        return parent != null ? parent.resolve(stem + ".log") : Path.of(stem + ".log");
    }

    private void copy(InputStream inputStream, OutputStream outputStream, long bytesToCopy) throws IOException {
        long remaining = bytesToCopy;
        byte[] buffer = new byte[LOG_BUFFER_SIZE];
        while (remaining > 0L) {
            int length = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (length < 0) {
                return;
            }

            outputStream.write(buffer, 0, length);
            remaining -= length;
        }
    }

    private void skip(InputStream inputStream, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0L) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }

            if (inputStream.read() < 0) {
                return;
            }

            remaining--;
        }
    }

    private boolean createFullBackup() {
        return this.fullBackup || this.forceFullBackup;
    }

    private record LogSnapshot(long offset, @Nullable Object fileKey) {

        private static LogSnapshot disabled() {
            return new LogSnapshot(-1L, null);
        }

        private boolean isEnabled() {
            return this.offset >= 0L;
        }

        private boolean hasRolledOver(BasicFileAttributes currentAttributes) {
            Object currentFileKey = currentAttributes.fileKey();
            return currentAttributes.size() < this.offset
                    || ((this.fileKey != null || currentFileKey != null) && !Objects.equals(this.fileKey, currentFileKey));
        }
    }

    private static class Timer {

        public static String getTimer(long milliseconds) {
            long seconds = milliseconds / 1000;
            long ms = milliseconds % 1000;

            if (seconds < 60) {
                return String.format("%d.%03ds", seconds, ms);
            }

            if (seconds < 3600) {
                return String.format("%02d:%02dmin", seconds / 60, seconds % 60);
            }

            return String.format("%d:%02dh", seconds / 3600, (seconds % 3600) / 60);
        }
    }
}
