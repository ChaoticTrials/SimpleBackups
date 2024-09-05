package de.melanx.simplebackups;

import de.melanx.simplebackups.compat.Mc2DiscordCompat;
import de.melanx.simplebackups.config.BackupType;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.FileUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.common.ForgeI18n;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupThread extends Thread {

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('_')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral('-')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral('-')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();
    public static final Logger LOGGER = LoggerFactory.getLogger(BackupThread.class);
    private final MinecraftServer server;
    private final boolean quiet;
    private final long lastSaved;
    private final boolean fullBackup;
    private final LevelStorageSource.LevelStorageAccess storageSource;
    private final Path backupPath;

    private BackupThread(@Nonnull MinecraftServer server, boolean quiet, BackupData backupData) {
        this.server = server;
        this.storageSource = server.storageSource;
        this.quiet = quiet;
        if (backupData == null) {
            this.lastSaved = 0;
            this.fullBackup = true;
        } else {
            this.lastSaved = CommonConfig.backupType() == BackupType.MODIFIED_SINCE_LAST ? backupData.getLastSaved() : backupData.getLastFullBackup();
            this.fullBackup = CommonConfig.backupType() == BackupType.FULL_BACKUPS || (CommonConfig.useTickCounter() ? server.overworld().getGameTime() : System.currentTimeMillis()) - CommonConfig.getFullBackupTimer() > backupData.getLastFullBackup();
        }
        this.setName("SimpleBackups");
        this.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        this.backupPath = CommonConfig.getOutputPath(this.storageSource.levelId);
    }

    public static boolean tryCreateBackup(MinecraftServer server) {
        BackupData backupData = BackupData.get(server);
        if (BackupThread.shouldRunBackup(server)) {
            BackupThread thread = new BackupThread(server, false, backupData);
            thread.start();
            long currentTime = CommonConfig.useTickCounter() ? server.overworld().getGameTime() : System.currentTimeMillis();
            backupData.updateSaveTime(currentTime);
            if (thread.fullBackup) {
                backupData.updateFullBackupTime(currentTime);
            }

            return true;
        }

        return false;
    }

    public static boolean shouldRunBackup(MinecraftServer server) {
        BackupData backupData = BackupData.get(server);
        if (!CommonConfig.isEnabled() || backupData.isPaused()) {
            return false;
        }

        if (CommonConfig.useTickCounter()) {
            long gameTime = server.overworld().getGameTime();
            long lastSaved = backupData.getLastSaved();
            // convert timer from minutes into ticks
            int timer = CommonConfig.getTimer() * 20 * 60;
            return gameTime - lastSaved >= timer;
        }

        return System.currentTimeMillis() - CommonConfig.getTimer() > backupData.getLastSaved();
    }

    public static void createBackup(MinecraftServer server, boolean quiet) {
        BackupThread thread = new BackupThread(server, quiet, null);
        thread.start();
    }

    public void deleteFiles() {
        File backups = this.backupPath.toFile();
        if (backups.isDirectory()) {
            List<File> files = new ArrayList<>(Arrays.stream(Objects.requireNonNull(backups.listFiles())).filter(File::isFile).toList());
            if (files.size() >= CommonConfig.getBackupsToKeep()) {
                files.sort(Comparator.comparingLong(File::lastModified));
                while (files.size() >= CommonConfig.getBackupsToKeep()) {
                    boolean deleted = files.get(0).delete();
                    String name = files.get(0).getName();
                    if (deleted) {
                        files.remove(0);
                        LOGGER.info("Successfully deleted \"{}\"", name);
                    }
                }
            }
        }
    }

    public void saveStorageSize() {
        try {
            while (this.getOutputFolderSize() > CommonConfig.getMaxDiskSize()) {
                File[] files = this.backupPath.toFile().listFiles();
                if (Objects.requireNonNull(files).length == 1) {
                    LOGGER.error("Cannot delete old files to save disk space. Only one backup file left!");
                    return;
                }

                Arrays.sort(Objects.requireNonNull(files), Comparator.comparingLong(File::lastModified));
                File file = files[0];
                String name = file.getName();
                if (file.delete()) {
                    LOGGER.info("Successfully deleted \"{}\"", name);
                }
            }
        } catch (NullPointerException | IOException e) {
            LOGGER.error("Cannot delete old files to save disk space", e);
        }
    }

    @Override
    public void run() {
        try {
            this.deleteFiles();

            Files.createDirectories(this.backupPath);
            long start = System.currentTimeMillis();
            this.broadcast("simplebackups.backup_started", Style.EMPTY.withColor(ChatFormatting.GOLD));
            long size = this.makeWorldBackup();
            long end = System.currentTimeMillis();
            String time = Timer.getTimer(end - start);
            this.saveStorageSize();
            this.broadcast("simplebackups.backup_finished", Style.EMPTY.withColor(ChatFormatting.GOLD), time, StorageSize.getFormattedSize(size), StorageSize.getFormattedSize(this.getOutputFolderSize()));
        } catch (IOException e) {
            SimpleBackups.LOGGER.error("Error backing up", e);
        }
    }

    private long getOutputFolderSize() throws IOException {
        File[] files = this.backupPath.toFile().listFiles();
        long size = 0;
        try {
            for (File file : Objects.requireNonNull(files)) {
                size += Files.size(file.toPath());
            }
        } catch (NullPointerException e) {
            return 0;
        }

        return size;
    }

    private void broadcast(String message, Style style, Object... parameters) {
        if (CommonConfig.sendMessages() && !this.quiet) {
            this.server.execute(() -> {
                this.server.getPlayerList().getPlayers().forEach(player -> {
                    if (ServerConfig.messagesForEveryone() || player.hasPermissions(2)) {
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
            ConnectionData data = NetworkHooks.getConnectionData(player.connection.connection);
            if (data != null && data.getModList().contains(SimpleBackups.MODID)) {
                return Component.translatable(key, parameters);
            }
        }

        return Component.literal(String.format(ForgeI18n.getPattern(key), parameters));
    }

    // vanilla copy with modifications
    private long makeWorldBackup() throws IOException {
        this.storageSource.checkLock();
        String fileName = this.storageSource.levelId + "_" + LocalDateTime.now().format(FORMATTER);
        Path path = CommonConfig.getOutputPath(this.storageSource.levelId);

        try {
            Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
        } catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        }

        Path outputFile = path.resolve(FileUtil.findAvailableName(path, fileName, ".zip"));
        final ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)));
        zipStream.setLevel(CommonConfig.getCompressionLevel());

        try {
            Path levelName = Paths.get(this.storageSource.levelId);
            Path levelPath = this.storageSource.getWorldDir().resolve(this.storageSource.levelId).toRealPath();
            Files.walkFileTree(levelPath, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.endsWith("session.lock")) {
                        long lastModified = file.toFile().lastModified();
                        if (BackupThread.this.fullBackup || lastModified - BackupThread.this.lastSaved > 0) {
                            String completePath = levelName.resolve(levelPath.relativize(file)).toString().replace('\\', '/');
                            ZipEntry zipentry = new ZipEntry(completePath);
                            zipStream.putNextEntry(zipentry);
                            com.google.common.io.Files.asByteSource(file.toFile()).copyTo(zipStream);
                            zipStream.closeEntry();
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            try {
                zipStream.close();
            } catch (IOException e1) {
                e.addSuppressed(e1);
            }

            throw e;
        }

        zipStream.close();
        return Files.size(outputFile);
    }

    private static class Timer {

        private static final SimpleDateFormat SECONDS = new SimpleDateFormat("s.SSS");
        private static final SimpleDateFormat MINUTES = new SimpleDateFormat("mm:ss");
        private static final SimpleDateFormat HOURS = new SimpleDateFormat("HH:mm");

        public static String getTimer(long milliseconds) {
            Date date = new Date(milliseconds);
            double seconds = milliseconds / 1000d;
            if (seconds < 60) {
                return SECONDS.format(date) + "s";
            } else if (seconds < 3600) {
                return MINUTES.format(date) + "min";
            } else {
                return HOURS.format(date) + "h";
            }
        }
    }
}
