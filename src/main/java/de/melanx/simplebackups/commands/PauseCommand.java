package de.melanx.simplebackups.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.melanx.simplebackups.BackupData;
import de.melanx.simplebackups.SimpleBackups;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class PauseCommand implements Command<CommandSourceStack> {

    private final boolean paused;

    private PauseCommand(boolean paused) {
        this.paused = paused;
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("backup")
                .then(Commands.literal("pause")
                        .executes(new PauseCommand(true)))
                .then(Commands.literal("unpause")
                        .executes(new PauseCommand(false)));
    }


    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        BackupData data = BackupData.get(context.getSource().getServer());
        data.setPaused(this.paused);
//        SimpleBackups.network().pause(this.paused);
        return this.paused ? 1 : 0;
    }
}
