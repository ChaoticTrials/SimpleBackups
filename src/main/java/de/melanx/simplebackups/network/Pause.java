package de.melanx.simplebackups.network;

import de.melanx.simplebackups.client.ClientEventHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record Pause(boolean pause) {

    public static void handle(Pause msg, Supplier<NetworkEvent.Context> context) {
        ClientEventHandler.setPaused(msg.pause);
    }
}
