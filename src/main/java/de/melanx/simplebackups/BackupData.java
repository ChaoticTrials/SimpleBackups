package de.melanx.simplebackups;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.melanx.simplebackups.config.LocalConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class BackupData extends SavedData {

    private long lastSaved;
    private long lastFullBackup;
    private boolean paused;
    private boolean merging;
    private boolean usesTickCounter;
    private int backupsSinceLastPlayerJoined;

    public static final Codec<BackupData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.fieldOf("lastSaved").forGetter(BackupData::getLastSaved),
                    Codec.LONG.fieldOf("lastFullBackup").forGetter(BackupData::getLastFullBackup),
                    Codec.BOOL.fieldOf("paused").forGetter(BackupData::isPaused),
                    Codec.BOOL.fieldOf("merging").forGetter(BackupData::isMerging),
                    Codec.BOOL.fieldOf("usesTickCounter").forGetter(BackupData::usesTickCounter),
                    Codec.INT.fieldOf("backupsSinceLastPlayerJoined").forGetter(BackupData::backupsSinceLastPlayerJoined)
            )
            .apply(instance, BackupData::new));

    public static SavedDataType<BackupData> type() {
        return new SavedDataType<>(Identifier.fromNamespaceAndPath("simplebackups", "data"), context -> new BackupData(0, 0, false, false, LocalConfig.useTickCounter(), 0), context -> CODEC);
    }

    private BackupData(long lastSaved, long lastFullBackup, boolean paused, boolean merging, boolean usesTickCounter, int backupsSinceLastPlayerJoined) {
        this.lastSaved = lastSaved;
        this.lastFullBackup = lastFullBackup;
        this.paused = paused;
        this.merging = merging;
        this.usesTickCounter = usesTickCounter;
        this.backupsSinceLastPlayerJoined = backupsSinceLastPlayerJoined;
    }

    public static BackupData get(ServerLevel level) {
        return BackupData.get(level.getServer());
    }

    public static BackupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BackupData.type());
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        this.setDirty();
    }

    public boolean isPaused() {
        return this.paused;
    }

    public long getLastSaved() {
        return this.lastSaved;
    }

    public void updateSaveTime(long time) {
        this.lastSaved = time;
        this.setDirty();
    }

    public long getLastFullBackup() {
        return this.lastFullBackup;
    }

    public void updateFullBackupTime(long time) {
        this.lastFullBackup = time;
        this.setDirty();
    }

    public int backupsSinceLastPlayerJoined() {
        return this.backupsSinceLastPlayerJoined;
    }

    public void incrementBackupsSinceLastPlayerJoined() {
        this.backupsSinceLastPlayerJoined++;
        this.setDirty();
    }

    public void resetBackupsSinceLastPlayerJoined() {
        if (this.backupsSinceLastPlayerJoined == 0) {
            return;
        }

        this.backupsSinceLastPlayerJoined = 0;
        this.setDirty();
    }

    public boolean isMerging() {
        return this.merging;
    }

    public void startMerging() {
        this.merging = true;
    }

    public void stopMerging() {
        this.merging = false;
    }

    public boolean usesTickCounter() {
        return this.usesTickCounter;
    }

    public void setUsesTickCounter(boolean usesTickCounter) {
        this.usesTickCounter = usesTickCounter;
        this.setDirty();
    }
}
