package de.melanx.simplebackups.merging;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import de.melanx.simplebackups.BackupChain;
import de.melanx.simplebackups.SimpleBackups;
import de.melanx.simplebackups.compression.CompressionBase;
import de.melanx.simplebackups.config.LocalConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public abstract class MergerBase {

    protected final BackupChain chain;
    protected final CommandContext<CommandSourceStack> commandContext;
    private final List<Path> archiveFiles = new ArrayList<>();

    public MergerBase(BackupChain chain, CommandContext<CommandSourceStack> commandContext) {
        this.chain = chain;
        this.commandContext = commandContext;
    }

    public void merge() throws CommandSyntaxException {
        this.collectFiles();

        new MergingThread(this.commandContext).start();
    }

    protected abstract void mergeFiles(Function<Exception, IllegalStateException> onError);

    protected abstract CompressionBase.BackupFormat getFormat();

    protected List<Path> getArchiveFiles() {
        return ImmutableList.copyOf(this.archiveFiles);
    }

    protected Path mergedBackupPath() {
        return MergerBase.mainBackupsDir().resolve("merged_backup-" + UUID.randomUUID() + this.chain.getFormat().getExtension());
    }

    protected static Path mainBackupsDir() {
        return LocalConfig.getOutputPath("ignore").getParent();
    }

    public void collectFiles() throws CommandSyntaxException {
        switch(this.chain.getBackupType()) {
            case FULL_BACKUPS -> {
                throw new SimpleCommandExceptionType(Component.translatable("simplebackups.commands.only_modified")).create();
            }
            case INCREMENTAL -> {
                this.archiveFiles.add(this.chain.getFullBackup());
                this.archiveFiles.addAll(this.chain.getChildren());
            }
            case DIFFERENTIAL -> {
                this.archiveFiles.add(this.chain.getFullBackup());
                this.archiveFiles.add(this.chain.getChildren().getLast());
            }
        }
    }

    protected Path getLatestModifiedFile(Path existingFile, Path newFile) {
        try {
            FileTime existingFileTime = Files.getLastModifiedTime(existingFile);
            FileTime newFileTime = Files.getLastModifiedTime(newFile);
            return existingFileTime.compareTo(newFileTime) > 0 ? existingFile : newFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class MergingThread extends Thread {

        private final CommandContext<CommandSourceStack> commandContext;

        public MergingThread(CommandContext<CommandSourceStack> commandContext) {
            this.commandContext = commandContext;
        }

        @Override
        public void run() {
            Path mainBackupsDir = MergerBase.mainBackupsDir();
            Path mergedBackupPath = MergerBase.this.mergedBackupPath();
            try {
                MergerBase.this.mergeFiles(e -> new IllegalStateException("Error while processing backups", e));
            } catch (Exception e) {
                SimpleBackups.LOGGER.error("Error merging backups", e);
                this.commandContext.getSource().sendFailure(Component.translatable("simplebackups.commands.error", e.getMessage()));
                return;
            }

            this.commandContext.getSource().sendSuccess(() -> Component.translatable("simplebackups.commands.finished", mainBackupsDir.getParent().relativize(mergedBackupPath).toString()), false);
        }
    }
}
