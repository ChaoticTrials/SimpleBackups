package de.melanx.simplebackups;

import de.melanx.simplebackups.commands.BackupCommand;
import de.melanx.simplebackups.commands.MergeCommand;
import de.melanx.simplebackups.commands.PauseCommand;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import de.melanx.simplebackups.network.Pause;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public class EventListener {

    private boolean doBackup;

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SimpleBackups.MODID)
                .requires(stack -> ServerConfig.commandsCheatsDisabled() || stack.hasPermission(2))
                .then(BackupCommand.register())
                .then(PauseCommand.register())
                .then(MergeCommand.register()));
    }

    @SubscribeEvent
    public void onServerTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide
                && level.getGameTime() % 20 == 0 && level == level.getServer().overworld()) {
            EventListener.checkForTickCounterConfigUpdate(event.getLevel().getServer());

            if (!level.getServer().getPlayerList().getPlayers().isEmpty() || this.doBackup || CommonConfig.doNoPlayerBackups()) {
                this.doBackup = false;

                boolean done = BackupThread.tryCreateBackup(level.getServer());
                if (done) {
                    SimpleBackups.LOGGER.info("Backup done.");
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerConnect(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        //noinspection UnstableApiUsage
        if (CommonConfig.isEnabled() && event.getEntity().getServer() != null && NetworkRegistry.hasChannel(player.connection.connection, null, Pause.ID)) {
            PacketDistributor.sendToPlayer(player, new Pause(BackupData.get(event.getEntity().getServer()).isPaused()));
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

    private static void checkForTickCounterConfigUpdate(MinecraftServer server) {
        BackupData backupData = BackupData.get(server);
        boolean usesTickCounter = CommonConfig.useTickCounter();

        if (usesTickCounter != backupData.usesTickCounter()) {
            SimpleBackups.LOGGER.info("Tick counter config updated, usesTickCounter: {}", usesTickCounter);
            backupData.setUsesTickCounter(usesTickCounter);

            long lastTimeSaved = backupData.getLastSaved();
            int commonConfigTimer = CommonConfig.getTimer();

            SimpleBackups.LOGGER.info("Initial lastTimeSaved: {}", lastTimeSaved);
            SimpleBackups.LOGGER.info("Config timer in minutes: {}", commonConfigTimer);

            if (usesTickCounter) {
                long millisecondsTimeDifference = System.currentTimeMillis() - lastTimeSaved;
                long tickTimeDifference = millisecondsTimeDifference / 50L;
                lastTimeSaved = server.overworld().getGameTime() - tickTimeDifference;
                long timerInTicks = commonConfigTimer * 60L * 20L;

                SimpleBackups.LOGGER.info("Milliseconds difference: {}, Tick difference: {}", millisecondsTimeDifference, tickTimeDifference);
                SimpleBackups.LOGGER.info("Updated lastTimeSaved in ticks: {}", lastTimeSaved);
                SimpleBackups.LOGGER.info("Timer value in ticks: {}", timerInTicks);

                lastTimeSaved = Math.max(lastTimeSaved, timerInTicks);
                SimpleBackups.LOGGER.info("Final lastTimeSaved after max comparison (ticks): {}", lastTimeSaved);
            } else {
                long tickTimeDifference = server.overworld().getGameTime() - lastTimeSaved;
                long millisecondsTimeDifference = tickTimeDifference * 50L;
                lastTimeSaved = System.currentTimeMillis() - millisecondsTimeDifference;
                long timerInMilliseconds = commonConfigTimer * 60L * 1000L;

                SimpleBackups.LOGGER.info("Tick difference: {}, Milliseconds difference: {}", tickTimeDifference, millisecondsTimeDifference);
                SimpleBackups.LOGGER.info("Updated lastTimeSaved in milliseconds: {}", lastTimeSaved);
                SimpleBackups.LOGGER.info("Timer value in milliseconds: {}", timerInMilliseconds);

                lastTimeSaved = Math.max(lastTimeSaved, timerInMilliseconds);
                SimpleBackups.LOGGER.info("Final lastTimeSaved after max comparison (milliseconds): {}", lastTimeSaved);
            }
        }
    }
}
