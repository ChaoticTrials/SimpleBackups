package de.melanx.simplebackups;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

public class BackupData extends SavedData {

    private long lastSaved;
    private long lastFullBackup;
    private boolean paused;
    private boolean merging;
    private boolean usesTickCounter;

    private BackupData() {
        // use BackupData.get
    }

    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag nbt, @Nonnull HolderLookup.Provider provider) {
        nbt.putLong("lastSaved", this.lastSaved);
        nbt.putLong("lastFullBackup", this.lastFullBackup);
        nbt.putBoolean("paused", this.paused);
        nbt.putBoolean("merging", this.merging);
        nbt.putBoolean("usesTickCounter", this.usesTickCounter);
        return nbt;
    }

    public static BackupData get(ServerLevel level) {
        return BackupData.get(level.getServer());
    }

    public static BackupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BackupData.factory(), "simplebackups");
    }

    public BackupData load(@Nonnull CompoundTag nbt, @Nonnull HolderLookup.Provider provider) {
        this.lastSaved = nbt.getLong("lastSaved");
        this.lastFullBackup = nbt.getLong("lastFullBackup");
        this.paused = nbt.getBoolean("paused");
        this.merging = nbt.getBoolean("merging");
        this.usesTickCounter = nbt.getBoolean("usesTickCounter");
        return this;
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

    private static SavedData.Factory<BackupData> factory() {
        return new SavedData.Factory<>(BackupData::new, (nbt, provider) -> new BackupData().load(nbt, provider));
    }
}
