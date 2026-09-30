package de.melanx.simplebackups.compression;

import de.melanx.simplebackups.ToolsLoader;
import de.melanx.simplebackups.config.LocalConfig;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class ZstdCompression extends CompressionBase {

    public static boolean isAvailable() {
        return ToolsLoader.isZstdAvailable();
    }

    public ZstdCompression(FileStore fileStore, boolean doFullBackup, long lastSaved) {
        super(fileStore, doFullBackup, lastSaved);
    }

    @Override
    public void makeBackup(Path levelName, Path levelPath, Path outputFile) throws IOException {
        // Map config level (0–9, -1 = default) to ZSTD levels (1–19, -1 = default 3)
        int cfgLevel = LocalConfig.getCompressionLevel();
        int zstdLevel = cfgLevel < 0 ? -1 : Math.max(1, cfgLevel * 2 + 1);

        try (OutputStream zstdOut = ToolsLoader.wrapWithZstd(new BufferedOutputStream(Files.newOutputStream(outputFile)), zstdLevel);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(zstdOut)) {

            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tarOut.setAddPaxHeadersForNonAsciiNames(true);

            Files.walkFileTree(levelPath, new CompressionFileVisitor(levelPath) {

                @Nonnull
                @Override
                protected FileVisitResult visitFile_(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException {
                    String entryName = this.getNormalizedPath(levelName.resolve(levelPath.relativize(file)));
                    TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), entryName);
                    entry.setSize(attrs.size());

                    tarOut.putArchiveEntry(entry);
                    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                        in.transferTo(tarOut);
                    } catch (IOException e) {
                        this.visitFileFailed(file, e);
                    } finally {
                        tarOut.closeArchiveEntry();
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            tarOut.finish();
        }
    }

    @Override
    public String getExtension() {
        return BackupFormat.ZSTD.getExtension();
    }
}
