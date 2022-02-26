package de.melanx.simplebackups;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EventListener {

    private int remainingBackups = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.WorldTickEvent event) {
        //noinspection ConstantConditions
        if (event.phase == TickEvent.Phase.END && !event.world.isClientSide
                && event.world.getGameTime() % 20 == 0 && event.world == event.world.getServer().overworld()) {
            if (!event.world.getServer().getPlayerList().getPlayers().isEmpty() || this.remainingBackups > 0) {
                if (this.remainingBackups > 0) {
                    this.remainingBackups--;
                }

                boolean done = BackupThread.tryCreateBackup(event.world.getServer());
                if (done) {
                    SimpleBackups.LOGGER.info("Backup done.");
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !SimpleBackups.getNetwork().isRemotePresent(player)) {
            DefaultTranslator.PLAYERS_WITHOUT_MOD.add(player.getGameProfile().getId());
        }
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            DefaultTranslator.PLAYERS_WITHOUT_MOD.remove(event.getPlayer().getGameProfile().getId());
            //noinspection ConstantConditions
            if (player.getServer().getPlayerList().getPlayers().isEmpty()) {
                this.remainingBackups = 1;
            }
        }
    }
}
