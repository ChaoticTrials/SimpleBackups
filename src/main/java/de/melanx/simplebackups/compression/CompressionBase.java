package de.melanx.simplebackups.compression;

import de.melanx.simplebackups.config.CommonConfig;
import net.minecraft.FileUtil;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;

public abstract class CompressionBase {

    public static long makeBackup(LevelStorageSource.LevelStorageAccess storageSource, boolean fullBackup, long lastSaved) throws IOException {
        storageSource.checkLock();
        String fileName = storageSource.levelId + "_" + LocalDateTime.now().format(FORMATTER);
        Path outputPath = CommonConfig.getOutputPath();

        try {
            Files.createDirectories(Files.exists(outputPath) ? outputPath.toRealPath() : outputPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        CompressionBase compressor = switch (CommonConfig.backupFormat()) {
            case LZ4 -> new Lz4Compression();
            case XZ -> new XzCompression();
            case ZIP -> new ZipCompression();
        };
        Path outputFile = outputPath.resolve(FileUtil.findAvailableName(outputPath, fileName, "." + compressor.getFileExtension()));
        Path levelName = Paths.get(storageSource.levelId);
        Path levelPath = storageSource.getWorldDir().resolve(storageSource.levelId);

        return compressor.makeBackup(outputFile, levelName, levelPath, fullBackup, lastSaved);
    }

    protected static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('_')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral('-')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral('-')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();

    public abstract long makeBackup(Path outputFile, Path levelName, Path levelPath, boolean fullBackup, long lastSaved) throws IOException;
    public abstract String getFileExtension();

    public enum BackupFormat {
        LZ4,
        XZ,
        ZIP
    }
}
