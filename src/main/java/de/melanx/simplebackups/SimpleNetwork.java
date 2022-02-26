package de.melanx.simplebackups;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SimpleNetwork {

    public final SimpleChannel channel;

    public SimpleNetwork() {
        this.channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(SimpleBackups.MODID, "netchannel"),
                () -> "1",
                s -> true,
                s -> true
        );
    }

    public boolean isRemotePresent(ServerPlayer player) {
        return this.channel.isRemotePresent(player.connection.getConnection());
    }
}
