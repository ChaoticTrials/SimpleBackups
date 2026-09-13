package de.melanx.simplebackups.compression;

import de.melanx.simplebackups.BackupResult;
import de.melanx.simplebackups.SimpleBackups;
import de.melanx.simplebackups.StorageSize;
import de.melanx.simplebackups.ToolsLoader;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.exception.NotEnoughDiskSpaceException;
import de.melanx.simplebackups.sbk.SbkException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public abstract class CompressionBase {

    private static final Pattern TEMP_FILE_PATTERN = Pattern.compile(".+\\d{10}\\.neoforge-tmp$");   // NeoForge copy - net.neoforged.neoforge.common.IOUtilities
    public static final long BACKUP_BUFFER_SIZE = 128L * 1024 * 1024; // 128 MB
    protected final List<Path> errors = new ArrayList<>();
    protected final FileStore fileStore;
    protected final boolean doFullBackup;
    protected final long lastSaved;

    public CompressionBase(FileStore fileStore, boolean doFullBackup, long lastSaved) {
        this.fileStore = fileStore;
        this.doFullBackup = doFullBackup;
        this.lastSaved = lastSaved;
    }

    public static BackupResult makeBackup(MinecraftServer server, LevelStorageSource.LevelStorageAccess storageAccess, Path backupPath, Path backupFilePath, boolean doFullBackup, BackupFormat format, long lastSaved) throws IOException {
        storageAccess.checkLock();
        if (CommonConfig.saveAll()) {
            server.executeBlocking(() -> server.saveEverything(true, false, true));
        }

        FileStore fileStore = Files.getFileStore(backupPath);
        CompressionBase compressor = switch(format) {
            case ZIP -> new ZipCompression(fileStore, doFullBackup, lastSaved);
            case ZSTD -> {
                if (!ZstdCompression.isAvailable()) {
                    throw new IOException("ZSTD compression is selected but zstd-jni is not installed. Download it and place it in " + ToolsLoader.RELATIVE_TOOLS_DIR + ".");
                }
                yield new ZstdCompression(fileStore, doFullBackup, lastSaved);
            }
            case SBK -> new SbkCompression(fileStore, doFullBackup, lastSaved);
        };

        Path levelName = Paths.get(storageAccess.getLevelId());
        Path levelPath = storageAccess.getWorldDir().resolve(storageAccess.getLevelId()).toRealPath();

        Path sourceDir = levelPath;
        Path tempDir = null;
        try {
            boolean doPreCopy = CommonConfig.preCopy();

            if (!doPreCopy && format != BackupFormat.ZIP) {
                SimpleBackups.LOGGER.info("Pre-copy disabled for {} compression. It's highly recommended to enable it to avoid problems!", format.name());
            }

            if (doPreCopy) {
                tempDir = Files.createTempDirectory(backupPath, "simplebackups-precopy-");
                compressor.copyToTemp(levelPath, tempDir);
                sourceDir = tempDir;
            }

            compressor.makeBackup(levelName, sourceDir, backupFilePath);
        } catch (IOException | SbkException e) {
            SimpleBackups.LOGGER.error("Failed to create backup", e);
        } finally {
            if (tempDir != null) {
                compressor.deleteTempDir(tempDir);
            }
        }

        return new BackupResult(backupFilePath, Files.size(backupFilePath), compressor.errors);
    }

    public abstract void makeBackup(Path levelName, Path levelPath, Path outputFile) throws IOException;

    public abstract String getExtension();

    protected final void copyToTemp(Path source, Path dest) throws IOException {
        long start = System.currentTimeMillis();

        SimpleBackups.LOGGER.info("Pre-copying {} to {}", source, dest);

        Files.walkFileTree(source, new PreCopyFileVisitor(source, dest));
        long end = System.currentTimeMillis() - start;

        SimpleBackups.LOGGER.info("Pre-copy took {}ms for {} Bytes", end, StorageSize.getFolderSize(source));
    }

    protected final void deleteTempDir(Path tempDir) {
        try {
            Files.walk(tempDir).sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            SimpleBackups.LOGGER.warn("Failed to delete pre-copy temp directory: {}", tempDir, e);
        }
    }

    private class PreCopyFileVisitor extends CompressionFileVisitor {

        private final Path source;
        private final Path dest;

        public PreCopyFileVisitor(Path source, Path dest) {
            super(source);
            this.source = source;
            this.dest = dest;
        }

        @Override
        protected FileVisitResult visitFile_(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException {
            Path target = this.dest.resolve(this.source.relativize(file));

            try {
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                this.visitFileFailed(file, e);
            }

            return FileVisitResult.CONTINUE;
        }
    }

    protected abstract class CompressionFileVisitor extends SimpleFileVisitor<Path> {

        protected final List<Path> ignoredPaths = CommonConfig.getIgnoredPaths();
        protected final List<Path> ignoredFiles = CommonConfig.getIgnoredFiles();
        protected final String ignoredFilesRegex = CommonConfig.getIgnoredFilesRegex();
        protected final boolean ignoreSomething = !this.ignoredPaths.isEmpty() || !this.ignoredFiles.isEmpty() || !this.ignoredFilesRegex.isEmpty();
        private final Path levelPath;

        public CompressionFileVisitor(Path levelPath) {
            this.levelPath = levelPath;
        }

        @Nonnull
        @Override
        public FileVisitResult preVisitDirectory(@Nonnull Path dir, @Nonnull BasicFileAttributes attrs) {
            if (dir.equals(this.levelPath)) return FileVisitResult.CONTINUE;

            Path rel = this.levelPath.relativize(dir);

            // skip the whole directory subtree if configured
            if (this.ignoreSomething && this.shouldSkipDirectory(rel)) {
                SimpleBackups.LOGGER.debug("Skipping directory: {}", dir);
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Nonnull
        @Override
        public final FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException {
            if (file.endsWith("session.lock")) {
                return FileVisitResult.CONTINUE;
            }

            if (file.endsWith("biomancy.spatial.db")) {
                SimpleBackups.LOGGER.info("Skipping \"{}\" - see https://github.com/Elenterius/Biomancy/issues/175", this.levelPath.relativize(file));
                return FileVisitResult.CONTINUE;
            }

            if (CommonConfig.ignoreTempFiles() && TEMP_FILE_PATTERN.matcher(file.getFileName().toString()).matches()) {
                SimpleBackups.LOGGER.debug("Skipping temporary file: {}", file);
                return FileVisitResult.CONTINUE;
            }

            if (this.ignoreSomething && this.shouldSkipFile(this.levelPath.relativize(file))) {
                SimpleBackups.LOGGER.debug("Skipping file: {}", file);
                return FileVisitResult.CONTINUE;
            }

            long lastModified = attrs.lastModifiedTime().toMillis();
            if (CompressionBase.this.doFullBackup || lastModified - CompressionBase.this.lastSaved > 0) {
                if (CompressionBase.this.fileStore.getUsableSpace() - attrs.size() - BACKUP_BUFFER_SIZE < 0L) {
                    throw new NotEnoughDiskSpaceException("Not enough space on disk to create backup");
                }

                return this.visitFile_(file, attrs);
            }

            return FileVisitResult.CONTINUE;
        }

        protected abstract FileVisitResult visitFile_(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException;

        @Nonnull
        @Override
        public FileVisitResult visitFileFailed(@Nonnull Path file, @Nonnull IOException exc) throws IOException {
            if (exc instanceof NoSuchFileException || exc instanceof FileNotFoundException) {
                SimpleBackups.LOGGER.debug("Skipped vanished file: {}", file);
                return FileVisitResult.CONTINUE;
            }

            if (CommonConfig.collectErrors()) {
                SimpleBackups.LOGGER.error("Failed to backup file: {}", file, exc);
                CompressionBase.this.errors.add(this.levelPath.relativize(file));
                return FileVisitResult.CONTINUE;
            }

            IOException detailedException = new IOException("Failed to backup file: " + file, exc);
            return super.visitFileFailed(file, detailedException);
        }

        protected boolean shouldSkipFile(Path relativePath) {
            return this.ignoredPaths.contains(relativePath.getParent())
                    || this.ignoredFiles.contains(relativePath)
                    || (!this.ignoredFilesRegex.isEmpty() && this.getNormalizedPath(relativePath).matches(this.ignoredFilesRegex));
        }

        private boolean shouldSkipDirectory(Path relativeDir) {
            // If the directory itself is in ignoredPaths, skip its subtree
            return this.ignoredPaths.contains(relativeDir);
        }

        protected String getNormalizedPath(Path path) {
            return path.toString().replace('\\', '/');
        }
    }

    public enum BackupFormat {
        ZIP(".zip"),
        ZSTD(".tar.zst"),
        SBK(".sbk");

        private final String extension;

        BackupFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return this.extension;
        }
    }
}
