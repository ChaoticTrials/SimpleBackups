package de.melanx.simplebackups.compression;

import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Lz4Compression extends CompressionBase {

    @Override
    public long makeBackup(Path outputFile, Path levelName, Path levelPath, boolean fullBackup, long lastSaved) throws IOException {
        try (FramedLZ4CompressorOutputStream lz4Stream = new FramedLZ4CompressorOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {

            Files.walkFileTree(levelPath, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.endsWith("session.lock")) {
                        long lastModified = file.toFile().lastModified();
                        if (fullBackup || lastModified - lastSaved > 0) {
                            String completePath = levelName.resolve(levelPath.relativize(file)).toString().replace('\\', '/');
                            lz4Stream.write(completePath.getBytes());
                            com.google.common.io.Files.asByteSource(file.toFile()).copyTo(lz4Stream);
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
        return "lz4";
    }
}
