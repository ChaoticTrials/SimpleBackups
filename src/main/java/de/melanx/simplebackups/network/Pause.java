package de.melanx.simplebackups.network;

import de.melanx.simplebackups.SimpleBackups;
import de.melanx.simplebackups.client.ClientEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nonnull;

public record Pause(boolean pause) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleBackups.MODID, "pause");
    public static final CustomPacketPayload.Type<Pause> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, Pause> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Pause::pause, Pause::new
    );

    @Nonnull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return Pause.TYPE;
    }

    public void handle(IPayloadContext context) {
        ClientEventHandler.setPaused(this.pause);
    }
}
