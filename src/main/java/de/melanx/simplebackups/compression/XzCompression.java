package de.melanx.simplebackups.compression;

import de.melanx.simplebackups.config.CommonConfig;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;


public class XzCompression extends CompressionBase {

    @Override
    public long makeBackup(Path outputFile, Path levelName, Path levelPath, boolean fullBackup, long lastSaved) throws IOException {
        int compressionLevel = CommonConfig.getCompressionLevel();
        try (XZCompressorOutputStream xzStream = new XZCompressorOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)), compressionLevel)) {

            Files.walkFileTree(levelPath, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.endsWith("session.lock")) {
                        long lastModified = file.toFile().lastModified();
                        if (fullBackup || lastModified - lastSaved > 0) {
                            String completePath = levelName.resolve(levelPath.relativize(file)).toString().replace('\\', '/');
                            xzStream.write(completePath.getBytes());
                            com.google.common.io.Files.asByteSource(file.toFile()).copyTo(xzStream);
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
        return "xz";
    }
}
