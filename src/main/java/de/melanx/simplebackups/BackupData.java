package de.melanx.simplebackups;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

public class BackupData extends SavedData {

    private long lastSaved;

    public static BackupData get(ServerLevel level) {
        return BackupData.get(level.getServer());
    }

    public static BackupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(nbt -> new BackupData().load(nbt), BackupData::new, "simplebackups");
    }

    public BackupData load(@Nonnull CompoundTag nbt) {
        this.lastSaved = nbt.getLong("lastSaved");
        return this;
    }

    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag nbt) {
        nbt.putLong("lastSaved", this.lastSaved);
        return nbt;
    }

    public long getLastSaved() {
        return this.lastSaved;
    }

    public void updateSaveTime(long time) {
        this.lastSaved = time;
        this.setDirty();
    }
}
