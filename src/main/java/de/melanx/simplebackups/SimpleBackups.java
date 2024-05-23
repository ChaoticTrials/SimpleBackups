package de.melanx.simplebackups;

import de.melanx.simplebackups.client.ClientEventHandler;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import de.melanx.simplebackups.network.Pause;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

@Mod(SimpleBackups.MODID)
public class SimpleBackups {

    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleBackups.class);
    public static final String MODID = "simplebackups";

    public SimpleBackups(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.CONFIG);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.CONFIG);
        NeoForge.EVENT_BUS.register(new EventListener());
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::onRegisterPayloadHandler);

        if (dist.isClient()) {
            NeoForge.EVENT_BUS.register(new ClientEventHandler());
        }
    }

    private void onRegisterPayloadHandler(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SimpleBackups.MODID)
                .versioned("1.0")
                .optional();

        registrar.playToClient(Pause.TYPE, Pause.CODEC, Pause::handle);
    }

    private void setup(FMLCommonSetupEvent event) {
    }
}
