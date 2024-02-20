package de.melanx.simplebackups.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import de.melanx.simplebackups.BackupData;
import de.melanx.simplebackups.config.BackupType;
import de.melanx.simplebackups.config.CommonConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MergeCommand implements Command<CommandSourceStack> {


    public static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("mergeBackups")
                .executes(new MergeCommand());
    }

    @Override
    public int run(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
        // Check if only modified files should be backed up
        if (CommonConfig.backupType() == BackupType.FULL_BACKUPS) {
            throw new SimpleCommandExceptionType(Component.translatable("simplebackups.commands.only_modified")).create();
        }

        BackupData data = BackupData.get(commandContext.getSource().getServer());

        // Check if a merge operation is already in progress
        if (data.isMerging()) {
            throw new SimpleCommandExceptionType(Component.translatable("simplebackups.commands.is_merging")).create();
        }

        MergingThread mergingThread = new MergingThread(commandContext);
        try {
            data.startMerging();
            mergingThread.start();
        } catch (Exception e) {
            e.printStackTrace();
            data.stopMerging();
            return 0;
        }

        data.stopMerging();
        return 1;
    }

    private static class MergingThread extends Thread {

        private final CommandContext<CommandSourceStack> commandContext;

        public MergingThread(CommandContext<CommandSourceStack> commandContext) {
            this.commandContext = commandContext;
        }

        @Override
        public void run() {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("merged_backup-" + UUID.randomUUID() + ".zip"))) {
                Map<String, Path> zipFiles = new HashMap<>();

                // Walk the file tree of the output path
                Files.walkFileTree(CommonConfig.getOutputPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        MergingThread.this.processFile(file, zipFiles);
                        return FileVisitResult.CONTINUE;
                    }
                });

                // Write the merged zip file
                this.writeMergedZipFile(zos, zipFiles);
            } catch (IOException e) {
                throw new IllegalStateException("Error while processing backups", e);
            } finally {
                commandContext.getSource().sendSuccess(() -> Component.translatable("simplebackups.commands.finished"), false);
            }
        }

        private void processFile(Path file, Map<String, Path> zipFiles) throws IOException {
            if (file.toString().endsWith(".zip")) {
                try (ZipFile zipFile = new ZipFile(file.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();

                        zipFiles.merge(name, file, this::getLatestModifiedFile);
                    }
                }
            }
        }

        private Path getLatestModifiedFile(Path existingFile, Path newFile) {
            try {
                FileTime existingFileTime = Files.getLastModifiedTime(existingFile);
                FileTime newFileTime = Files.getLastModifiedTime(newFile);
                return existingFileTime.compareTo(newFileTime) > 0 ? existingFile : newFile;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void writeMergedZipFile(ZipOutputStream zos, Map<String, Path> zipFiles) throws IOException {
            for (Map.Entry<String, Path> entry : zipFiles.entrySet()) {
                String fileName = entry.getKey();
                Path zipFilePath = entry.getValue();

                try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
                    ZipEntry zipEntry = zipFile.getEntry(fileName);
                    if (zipEntry != null) {
                        zos.putNextEntry(new ZipEntry(fileName));

                        try (InputStream is = zipFile.getInputStream(zipEntry)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }

                        zos.closeEntry();
                    }
                }
            }
        }
    }
}
