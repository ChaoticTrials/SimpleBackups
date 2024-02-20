package de.melanx.simplebackups;

import de.melanx.simplebackups.commands.BackupCommand;
import de.melanx.simplebackups.commands.MergeCommand;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EventListener {

    private boolean doBackup;

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SimpleBackups.MODID)
                .requires(stack -> stack.hasPermission(2))
                .then(BackupCommand.register())
                .then(MergeCommand.register()));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.WorldTickEvent event) {
        //noinspection ConstantConditions
        if (event.phase == TickEvent.Phase.END && !event.world.isClientSide
                && event.world.getGameTime() % 20 == 0 && event.world == event.world.getServer().overworld()) {
            if (!event.world.getServer().getPlayerList().getPlayers().isEmpty() || this.doBackup) {
                this.doBackup = false;

                boolean done = BackupThread.tryCreateBackup(event.world.getServer());
                if (done) {
                    SimpleBackups.LOGGER.info("Backup done.");
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            //noinspection ConstantConditions
            if (player.getServer().getPlayerList().getPlayers().isEmpty()) {
                this.doBackup = true;
            }
        }
    }
}
