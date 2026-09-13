package de.melanx.simplebackups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

public enum StorageSize {
    B(0),
    KB(1),
    MB(2),
    GB(3),
    TB(4);

    private final long sizeInBytes;
    private final String postfix;

    StorageSize(int factor) {
        this.sizeInBytes = (long) Math.pow(1024, factor);
        this.postfix = this.name().toUpperCase(Locale.ROOT);
    }

    public static StorageSize getSizeFor(double bytes) {
        for (StorageSize value : StorageSize.values()) {
            if (bytes < value.sizeInBytes) {
                return value.getLower();
            } else if (value == TB) {
                return value;
            }
        }

        return B;
    }

    public static long getBytes(String s) {
        String[] splits = s.split(" ");
        int amount = Integer.parseInt(splits[0]);
        StorageSize size = StorageSize.valueOf(splits[1].toUpperCase(Locale.ROOT));
        return amount * size.sizeInBytes;
    }

    public static String getFormattedSize(double bytes) {
        StorageSize size = StorageSize.getSizeFor(bytes);
        double small = bytes / size.sizeInBytes;
        return String.format("%.1f %s", small, size.postfix);
    }

    public static long getFolderSize(Path folderPath) {
        if (!Files.exists(folderPath)) {
            return 0;
        }

        try (Stream<Path> stream = Files.walk(folderPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            SimpleBackups.LOGGER.warn("Failed to get size of {}", path, e);
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            SimpleBackups.LOGGER.warn("Failed to get size of {}", folderPath, e);
            return 0L;
        }
    }

    public StorageSize getLower() {
        return this.ordinal() == 0 ? this : StorageSize.values()[this.ordinal() - 1];
    }
}
