package de.melanx.simplebackups;

import de.melanx.simplebackups.compression.CompressionBase;
import de.melanx.simplebackups.config.LocalConfig;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BackupChainManager {

    private static final Map<String, BackupChainManager> INSTANCES = new HashMap<>();
    private final List<BackupChain> chains = new ArrayList<>();
    private final String levelId;

    private BackupChainManager(String levelId) {
        this.levelId = levelId;
    }

    public static BackupChainManager get(String levelId) {
        return INSTANCES.computeIfAbsent(levelId, str -> {
            BackupChainManager manager = new BackupChainManager(str);
            manager.reloadAllChains();
            return manager;
        });
    }

    public void addChain(BackupChain chain) {
        try {
            Files.createDirectories(chain.getParentFolder());
            chain.writeMetadata();
            this.chains.add(chain);
            this.chains.sort(Comparator.comparingLong(BackupChain::getLastUpdated));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create new backup chain", e);
        }
    }

    public BackupChain createChain(String baseName) {
        return this.createChain(baseName, LocalConfig.getBackupFormat());
    }

    public BackupChain createChain(String baseName, CompressionBase.BackupFormat format) {
        Path chainDir = LocalConfig.getOutputPath(this.levelId).resolve(baseName);
        Path backupFilePath = Paths.get("full" + format.getExtension());
        BackupChain backupChain = new BackupChain(chainDir, backupFilePath, LocalConfig.backupType(), format);

        this.addChain(backupChain);
        return backupChain;
    }

    public void removeChain(BackupChain chain) {
        this.chains.remove(chain);
        chain.deleteFiles();
    }

    public List<BackupChain> getChains() {
        return new LinkedList<>(this.chains);
    }

    public BackupChain getFirstChain() {
        return this.chains.getFirst();
    }

    @Nullable
    public BackupChain getLatestChain() {
        if (this.chains.isEmpty()) {
            return null;
        }

        return this.chains.getLast();
    }

    public long getFileSize() {
        return this.chains.stream().mapToLong(BackupChain::getFileSize).sum();
    }

    public void reloadAllChains() {
        List<BackupChain> chains = new ArrayList<>();
        try {
            Path outputPath = LocalConfig.getOutputPath(this.levelId);
            if (!Files.exists(outputPath)) {
                this.chains.clear();
                return;
            }

            Files.walk(outputPath).filter(Files::isDirectory).forEach(dir -> {
                if (dir.resolve(BackupChain.METADATA_FILE).toFile().exists()) {
                    BackupChain backupChain = BackupChain.readMetadata(dir);
                    if (backupChain != null) {
                        chains.add(backupChain);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload backup chains for " + this.levelId, e);
        }

        this.chains.clear();
        this.chains.addAll(chains);
        this.chains.sort(Comparator.comparingLong(BackupChain::getLastUpdated));
    }
}
