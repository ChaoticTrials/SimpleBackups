package de.melanx.simplebackups;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.melanx.simplebackups.compression.CompressionBase;
import de.melanx.simplebackups.config.BackupType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BackupChain {

    public static final String METADATA_FILE = "metadata.json";
    private final Path parentFolder;
    private final Path fullBackup;
    private final List<Path> children;
    private final BackupType backupType;
    private final CompressionBase.BackupFormat format;
    private long lastUpdated;

    public BackupChain(Path parentFolder, Path fullBackup, BackupType backupType, CompressionBase.BackupFormat format) {
        this(parentFolder, fullBackup, new ArrayList<>(), backupType, format, System.currentTimeMillis());
    }

    public BackupChain(Path parentFolder, Path fullBackup, List<Path> children, BackupType backupType, CompressionBase.BackupFormat format, long lastUpdated) {
        this.parentFolder = parentFolder;
        this.fullBackup = fullBackup;
        this.children = children;
        this.backupType = backupType;
        this.format = format;
        this.lastUpdated = lastUpdated;
    }

    public Path getParentFolder() {
        return this.parentFolder;
    }

    public Path getFullBackup() {
        return this.parentFolder.resolve(this.fullBackup);
    }

    public List<Path> getChildren() {
        return this.children.stream().map(this.parentFolder::resolve).toList();
    }

    public BackupType getBackupType() {
        return this.backupType;
    }

    public CompressionBase.BackupFormat getFormat() {
        return this.format;
    }

    public void addChild(Path child) {
        this.children.add(child);
    }

    public Path createChild() {
        int index = this.children.size() + 1;
        Path child = Path.of("child-" + String.format("%04d", index) + this.format.getExtension());
        this.children.add(child);
        this.lastUpdated = System.currentTimeMillis();
        this.writeMetadata();

        return this.parentFolder.resolve(child);
    }

    public long getLastUpdated() {
        return this.lastUpdated;
    }

    public long getFileSize() {
        return StorageSize.getFolderSize(this.parentFolder);
    }

    public void deleteFiles() {
        if (!Files.exists(this.parentFolder)) return;

        try {
            Files.walk(this.parentFolder)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            SimpleBackups.LOGGER.warn("Failed to delete \"{}\"", path, e);
                        }
                    });
            Files.deleteIfExists(this.parentFolder);
        } catch (IOException e) {
            SimpleBackups.LOGGER.warn("Failed to delete directory tree \"{}\"", this.parentFolder, e);
        }
    }

    public int getSize() {
        return this.children.size() + 1;
    }

    public void writeMetadata() {
        Path meta = this.parentFolder.resolve(METADATA_FILE);
        JsonObject json = new JsonObject();
        json.addProperty("backupType", this.getBackupType().name());
        json.addProperty("fullBackup", this.fullBackup.toString());
        JsonArray children = new JsonArray();
        for (Path child : this.children) {
            children.add(child.toString());
        }
        json.add("children", children);
        json.addProperty("lastUpdated", this.lastUpdated);
        json.addProperty("format", this.format.name());
        try {
            Files.writeString(meta, json.toString());
        } catch (IOException e) {
            SimpleBackups.LOGGER.warn("Failed to write metadata to {}", meta, e);
        }
    }

    @Nullable
    public static BackupChain readMetadata(Path chainDir) {
        Path meta = chainDir.resolve(METADATA_FILE);

        try {
            JsonObject json = new Gson().fromJson(Files.newBufferedReader(meta), JsonObject.class);

            Path fullBackup = Path.of(json.get("fullBackup").getAsString());
            List<Path> children = new ArrayList<>();
            for (JsonElement child : json.getAsJsonArray("children")) {
                children.add(Path.of(child.getAsString()));
            }
            BackupType backupType = BackupType.valueOf(json.get("backupType").getAsString());
            long lastUpdated = json.get("lastUpdated").getAsLong();

            CompressionBase.BackupFormat format;
            JsonElement formatElement = json.get("format");
            if (formatElement == null) {
                format = CompressionBase.BackupFormat.ZIP;
            } else {
                try {
                    format = CompressionBase.BackupFormat.valueOf(formatElement.getAsString().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    format = CompressionBase.BackupFormat.ZIP;
                }
            }

            return new BackupChain(chainDir, fullBackup, children, backupType, format, lastUpdated);
        } catch (IOException e) {
            SimpleBackups.LOGGER.warn("Failed to read metadata from {}", meta, e);
            return null;
        }
    }
}
