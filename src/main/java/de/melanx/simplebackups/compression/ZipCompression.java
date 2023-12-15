package de.melanx.simplebackups.compression;

import de.melanx.simplebackups.BackupThread;
import de.melanx.simplebackups.config.CommonConfig;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipCompression extends CompressionBase{

    @Override
    public long makeBackup(Path outputFile, Path levelName, Path levelPath, boolean fullBackup, long lastSaved) throws IOException {
        try (ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
            zipStream.setLevel(CommonConfig.getCompressionLevel());

            Files.walkFileTree(levelPath, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.endsWith("session.lock")) {
                        long lastModified = file.toFile().lastModified();
                        if (fullBackup || lastModified - lastSaved > 0) {
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
        }

        return Files.size(outputFile);
    }

    @Override
    public String getFileExtension() {
        return "zip";
    }
}
