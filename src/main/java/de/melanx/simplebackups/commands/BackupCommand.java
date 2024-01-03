package de.melanx.simplebackups.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.melanx.simplebackups.BackupThread;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

public class BackupCommand implements Command<CommandSourceStack> {

    public static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("backup")
                .then(Commands.literal("start")
                        .executes(new BackupCommand())
                        .then(Commands.argument("quiet", BoolArgumentType.bool())
                                .executes(new BackupCommand())
                                .then(Commands.argument("full", BoolArgumentType.bool())
                                        .executes(new BackupCommand()))
                        )
                );
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        boolean quiet = false;
        boolean full = true;
        try {
            quiet = BoolArgumentType.getBool(context, "quiet");
        } catch (IllegalArgumentException e) {
            // do nothing
        }

        try {
            full = BoolArgumentType.getBool(context, "full");
        } catch (IllegalArgumentException e) {
            // do nothing
        }

        MinecraftServer server = context.getSource().getServer();
        if (full) {
            BackupThread.createFullBackup(server, quiet);
        } else {
            BackupThread.createNormalBackup(server, quiet);
        }
        return 1;
    }
}
