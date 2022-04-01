package de.melanx.simplebackups;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
                        )
                );
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        boolean quiet = false;
        try {
            quiet = BoolArgumentType.getBool(context, "quiet");
        } catch (IllegalArgumentException e) {
            // do nothing
        }

        MinecraftServer server = context.getSource().getServer();
        BackupThread.createBackup(server, quiet);
        return 1;
    }
}
