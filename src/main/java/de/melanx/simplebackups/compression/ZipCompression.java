package de.melanx.simplebackups.compression;

import de.melanx.simplebackups.config.LocalConfig;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipCompression extends CompressionBase {

    public ZipCompression(FileStore fileStore, boolean doFullBackup, long lastSaved) {
        super(fileStore, doFullBackup, lastSaved);
    }

    @Override
    public void makeBackup(Path levelName, Path levelPath, Path outputFile) throws IOException {
        try (ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
            zipStream.setLevel(LocalConfig.getCompressionLevel());

            Files.walkFileTree(levelPath, new CompressionFileVisitor(levelPath) {

                @Nonnull
                public FileVisitResult visitFile_(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException {
                    String completePath = this.getNormalizedPath(levelName.resolve(levelPath.relativize(file)));
                    ZipEntry zipentry = new ZipEntry(completePath);
                    try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(file))) {
                        zipStream.putNextEntry(zipentry);
                        inputStream.transferTo(zipStream);
                        zipStream.closeEntry();
                    } catch (IOException e) {
                        this.visitFileFailed(file, e);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Override
    public String getExtension() {
        return BackupFormat.ZIP.getExtension();
    }
}
