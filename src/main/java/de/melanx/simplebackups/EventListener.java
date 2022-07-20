package de.melanx.simplebackups;

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
                .then(BackupCommand.register()));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        //noinspection ConstantConditions
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide
                && event.level.getGameTime() % 20 == 0 && event.level == event.level.getServer().overworld()) {
            if (!event.level.getServer().getPlayerList().getPlayers().isEmpty() || this.doBackup) {
                this.doBackup = false;

                boolean done = BackupThread.tryCreateBackup(event.level.getServer());
                if (done) {
                    SimpleBackups.LOGGER.info("Backup done.");
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            //noinspection ConstantConditions
            if (player.getServer().getPlayerList().getPlayers().isEmpty()) {
                this.doBackup = true;
            }
        }
    }
}
