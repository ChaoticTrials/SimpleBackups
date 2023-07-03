package de.melanx.simplebackups.network;

import de.melanx.simplebackups.SimpleBackups;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class SimpleNetwork {

    private static final String NET_VERSION = "1.0";

    private final SimpleChannel channel;
    private int id = 0;

    public SimpleNetwork() {
        this.channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(SimpleBackups.MODID, "netchannel"),
                () -> NET_VERSION,
                // allow joining if mod is not installed on client/server
                s -> NET_VERSION.equals(s) || NetworkRegistry.ABSENT.version().equals(s) || NetworkRegistry.ACCEPTVANILLA.equals(s),
                s -> NET_VERSION.equals(s) || NetworkRegistry.ABSENT.version().equals(s) || NetworkRegistry.ACCEPTVANILLA.equals(s)
        );
    }

    public void pause(boolean pause) {
        this.channel.send(PacketDistributor.ALL.noArg(), new Pause(pause));
    }

    public void pause(Player player, boolean pause) {
        if (player instanceof ServerPlayer) {
            this.channel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new Pause(pause));
        }
    }

    public void registerPackets() {
        this.channel.registerMessage(this.id++, Pause.class, (msg, buf) -> buf.writeBoolean(msg.pause()), buf -> new Pause(buf.readBoolean()), Pause::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
