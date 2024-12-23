package de.melanx.simplebackups;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MetaData {

    public static final Logger LOGGER = LoggerFactory.getLogger(MetaData.class);
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path backupPath;
    private final Path metadataPath;
    private final JsonObject data;
    private final LinkedHashMap<String, MetaBackupType> backupMap;
    private String lastFullBackup;

    public MetaData(Path backupPath) {
        this.backupPath = backupPath;
        this.metadataPath = backupPath.resolve("metadata.json");
        this.data = this.load(this.metadataPath);
        this.backupMap = new LinkedHashMap<>();

        // Load existing backups into backupMap
        JsonObject fullBackups = this.data.getAsJsonObject("full_backups");
        for (Map.Entry<String, JsonElement> entry : fullBackups.entrySet()) {
            String fullBackupName = entry.getKey();
            this.backupMap.put(fullBackupName, MetaBackupType.FULL_BACKUP);

            JsonArray deltaBackups = entry.getValue().getAsJsonArray();
            for (JsonElement deltaBackup : deltaBackups) {
                this.backupMap.put(deltaBackup.getAsString(), MetaBackupType.DELTA_BACKUP);
            }
        }
    }

    public LinkedHashMap<String, MetaBackupType> getBackupMap() {
        return this.backupMap;
    }

    public JsonObject load(@Nonnull Path path) {
        if (!Files.exists(path)) {
            return MetaData.createDefault();
        }

        try {
            String content = Files.readString(path);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            this.lastFullBackup = json.get("last_full_backup").getAsString();
            return json;
        } catch (IOException e) {
            LOGGER.error("Unable to read existing metadata file, creating new one and backup previous file", e);
            try {
                Files.copy(path, path.resolve(".bak"));
            } catch (IOException ex) {
                LOGGER.error("Unable to backup existing metadata file");
                throw new RuntimeException(ex);
            }

            return MetaData.createDefault();
        }
    }

    private void save() {
        String content = GSON.toJson(this.data);
        try {
            Files.writeString(this.metadataPath, content);
        } catch (IOException e) {
            LOGGER.error("Unable to write metadata file");
            throw new RuntimeException(e);
        }
    }

    public void addBackup(String backupName, boolean fullBackup) {
        JsonObject fullBackups = this.data.getAsJsonObject("full_backups");
        if (fullBackup) {
            this.backupMap.put(backupName, MetaBackupType.FULL_BACKUP);
            JsonArray deltaBackups = new JsonArray();
            this.data.getAsJsonObject("full_backups").add(backupName, deltaBackups);
            this.data.addProperty("last_full_backup", backupName);

            this.save();
            return;
        }

        this.backupMap.put(backupName, MetaBackupType.DELTA_BACKUP);
        String lastFullBackup = this.data.get("last_full_backup").getAsString();
        fullBackups.getAsJsonArray(lastFullBackup).add(backupName);

        this.save();
    }

    public void pruneOldBackups(int maxBackups) {
        // Temporary collection to hold items to be removed
        List<Map.Entry<String, MetaBackupType>> itemsToRemove = new ArrayList<>();

        // Collect items to be removed
        Iterator<Map.Entry<String, MetaBackupType>> iterator = this.backupMap.entrySet().iterator();
        while (iterator.hasNext() && this.backupMap.size() - itemsToRemove.size() >= maxBackups) {
            Map.Entry<String, MetaBackupType> entry = iterator.next();
            itemsToRemove.add(entry);
        }

        // Remove collected items
        for (Map.Entry<String, MetaBackupType> entryToRemove : itemsToRemove) {
            String backupToRemove = entryToRemove.getKey();
            MetaBackupType backupTypeToRemove = entryToRemove.getValue();

            if (backupTypeToRemove == MetaBackupType.DELTA_BACKUP) {
                String lastFullBackup = this.data.get("last_full_backup").getAsString();
                JsonArray lastFullBackupArray = this.data.getAsJsonObject("full_backups").getAsJsonArray(lastFullBackup);

                if (lastFullBackupArray != null) {
                    lastFullBackupArray.remove(new JsonPrimitive(backupToRemove));
                } else {
                    LOGGER.warn("Tried to remove delta backup from a null JsonArray for: {}", lastFullBackup);
                }
            } else {
                JsonArray deltaBackups = this.data.getAsJsonObject("full_backups").getAsJsonArray(backupToRemove);
                if (deltaBackups != null && !deltaBackups.isEmpty()) {
                    // Remove first delta backup if present
                    String deltaBackupToRemove = deltaBackups.get(0).getAsString();
                    deltaBackups.remove(0);

                    // Update backupMap
                    this.backupMap.remove(deltaBackupToRemove);
                    this.deleteBackupFile(deltaBackupToRemove);
                } else {
                    // No delta backups present, remove the full backup
                    this.data.getAsJsonObject("full_backups").remove(backupToRemove);
                    this.backupMap.remove(backupToRemove);
                    this.deleteBackupFile(backupToRemove);
                }
            }
        }

        this.save();
    }

    private void deleteBackupFile(String backupName) {
        boolean deleted = this.backupPath.resolve(backupName).toFile().delete();
        if (deleted) {
            LOGGER.info("Successfully deleted \"{}\"", backupName);
        }
    }

    @Nullable
    public Path removeBackup(String backupName) {
        JsonObject fullBackups = this.data.getAsJsonObject("full_backups");
        if (fullBackups.has(backupName)) {
            return null;
        }

        for (Map.Entry<String, JsonElement> entry : fullBackups.entrySet()) {
            String fullBackupName = entry.getKey();
            JsonArray fullBackupChildren = entry.getValue().getAsJsonArray();
            if (fullBackupChildren.contains(new JsonPrimitive(backupName))) {
                return fullBackups.getAsJsonArray(fullBackupName).isEmpty() ? this.backupPath.resolve(fullBackupName) : null;
            }
        }

        return null;
    }

    public Set<Path> onlyChildren(Set<Path> allBackups) {
        this.data.getAsJsonObject("full_backups").entrySet().forEach(entry -> {
            String fullBackupName = entry.getKey();
            JsonArray fullBackupChildren = entry.getValue().getAsJsonArray();
            allBackups.removeIf(path -> path.getFileName().toString().equals(fullBackupName) && !fullBackupChildren.contains(new JsonPrimitive(path.getFileName().toString())));
        });

        return allBackups;
    }

    @Nullable
    public String getLastFullBackup() {
        return this.lastFullBackup;
    }

    public Set<Path> getChildrenFromFull(String fullBackupName) {
        JsonArray childrenOfFullBackup = this.data.getAsJsonObject("full_backups").getAsJsonArray(fullBackupName);

        Set<Path> children = new HashSet<>();
        for (JsonElement child : childrenOfFullBackup) {
            children.add(this.backupPath.resolve(child.getAsString()));
        }

        return children;
    }

    private static JsonObject createDefault() {
        JsonObject jsonObject = new JsonObject();
        JsonObject fullBackups = new JsonObject();

        JsonObject fullBackup = new JsonObject();
        JsonArray fullBackupChildren = new JsonArray();

        fullBackup.add("children", fullBackupChildren);
        jsonObject.add("full_backups", fullBackups);
        jsonObject.add("last_full_backup", null);
        jsonObject.addProperty("version", MetaData.VERSION);
        return jsonObject;
    }

    public enum MetaBackupType {
        FULL_BACKUP,
        DELTA_BACKUP
    }
}
