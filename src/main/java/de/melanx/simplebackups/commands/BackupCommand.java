package de.melanx.simplebackups.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.melanx.simplebackups.BackupThread;
import de.melanx.simplebackups.compression.CompressionBase;
import de.melanx.simplebackups.config.LocalConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;

import java.util.stream.Stream;

public class BackupCommand implements Command<CommandSourceStack> {

    public static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("backup")
                .then(Commands.literal("start")
                        .executes(new BackupCommand())
                        .then(Commands.argument("quiet", BoolArgumentType.bool())
                                .executes(new BackupCommand())
                                .then(Commands.argument("format", StringArgumentType.word())
                                        .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                                Stream.of(CompressionBase.BackupFormat.values()).map(Enum::name), builder))
                                        .executes(new BackupCommand()))
                        )
                );
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        boolean quiet = false;
        CompressionBase.BackupFormat format;
        try {
            quiet = BoolArgumentType.getBool(context, "quiet");
        } catch (IllegalArgumentException e) {
            // do nothing
        }
        try {
            format = CompressionBase.BackupFormat.valueOf(StringArgumentType.getString(context, "format"));
        } catch (IllegalArgumentException e) {
            format = LocalConfig.getBackupFormat();
        }

        MinecraftServer server = context.getSource().getServer();
        BackupThread.createBackup(server, quiet, format);
        return 1;
    }
}
