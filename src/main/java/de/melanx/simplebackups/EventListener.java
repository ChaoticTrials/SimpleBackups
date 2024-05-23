package de.melanx.simplebackups;

import de.melanx.simplebackups.commands.BackupCommand;
import de.melanx.simplebackups.commands.MergeCommand;
import de.melanx.simplebackups.commands.PauseCommand;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import de.melanx.simplebackups.network.Pause;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
        //noinspection ConstantConditions
        Level level = event.getLevel();
        if (!level.isClientSide
                && level.getGameTime() % 20 == 0 && level == level.getServer().overworld()) {
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
}
