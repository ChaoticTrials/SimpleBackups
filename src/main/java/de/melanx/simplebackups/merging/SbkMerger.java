package de.melanx.simplebackups.merging;

import com.mojang.brigadier.context.CommandContext;
import de.melanx.simplebackups.BackupChain;
import de.melanx.simplebackups.SimpleBackups;
import de.melanx.simplebackups.compression.CompressionBase;
import de.melanx.simplebackups.config.LocalConfig;
import de.melanx.simplebackups.sbk.*;
import net.minecraft.commands.CommandSourceStack;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;

public class SbkMerger extends MergerBase {

    public SbkMerger(BackupChain chain, CommandContext<CommandSourceStack> commandContext) {
        super(chain, commandContext);
    }

    @Override
    protected CompressionBase.BackupFormat getFormat() {
        return CompressionBase.BackupFormat.SBK;
    }

    @Override
    protected void mergeFiles(Function<Exception, IllegalStateException> onError) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("simplebackups-sbkmerge-");

            // Extract all archives in chain order; later extractions overwrite older files
            Map<String, Path> dataFiles = new HashMap<>();
            for (Path archive : this.getArchiveFiles()) {
                SbkInfo info = SbkReader.info(archive);
                info.entries().forEach(entry -> dataFiles.put(entry.path(), archive));
            }

            Map<Path, Set<String>> paths = new HashMap<>();
            dataFiles.forEach((path, archive) -> {
                paths.computeIfAbsent(archive, p -> new HashSet<>()).add(path);
            });

            for (Map.Entry<Path, Set<String>> entry : paths.entrySet()) {
                SbkReader.extract(entry.getKey(), tempDir, entry.getValue(), SbkProgress.SILENT);
            }

            String levelName = deriveLevelName(this.getArchiveFiles().getFirst());
            Path sourceDir = tempDir.resolve(levelName);

            List<Path> files = new ArrayList<>();
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Nonnull
                @Override
                public FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });

            int lvl = LocalConfig.getCompressionLevel();
            int lzmaPreset = lvl < 0 ? SbkWriteOptions.DEFAULT_PRESET : Math.clamp(lvl, 0, 9);
            SbkWriteOptions options = SbkWriteOptions.builder()
                    .lzmaPreset(lzmaPreset)
                    .algorithm(LocalConfig.sbkAlgorithm())
                    .build();

            SbkWriter.compress(sourceDir, files, Path.of(levelName), this.mergedBackupPath(), options, SbkProgress.SILENT);
        } catch (IOException e) {
            throw onError.apply(e);
        } finally {
            if (tempDir != null) {
                try {
                    deleteTempDir(tempDir);
                } catch (IOException e) {
                    SimpleBackups.LOGGER.error("Failed to delete SBK merge temp dir {}", tempDir, e);
                }
            }
        }
    }

    private static String deriveLevelName(Path archive) throws IOException {
        SbkInfo info = SbkReader.info(archive);
        if (info.entries().isEmpty()) {
            throw new SbkException("Archive has no entries: " + archive);
        }

        String firstPath = info.entries().getFirst().path();
        int sep = firstPath.indexOf('/');

        return sep > 0 ? firstPath.substring(0, sep) : firstPath;
    }

    private static void deleteTempDir(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {

            @Nonnull
            @Override
            public FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Nonnull
            @Override
            public FileVisitResult postVisitDirectory(@Nonnull Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
