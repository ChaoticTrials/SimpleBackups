package de.melanx.simplebackups;

import de.melanx.simplebackups.client.ClientInit;
import de.melanx.simplebackups.config.LocalConfig;
import de.melanx.simplebackups.config.SyncedConfig;
import de.melanx.simplebackups.network.Pause;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SimpleBackups.MODID)
public class SimpleBackups {

    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleBackups.class);
    public static final String MODID = "simplebackups";

    public SimpleBackups(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        ToolsLoader.init();
        modContainer.registerConfig(ModConfig.Type.LOCAL, LocalConfig.CONFIG, SimpleBackups.MODID + "/common.toml"); // todo 26.4 - rename to local.toml
        modContainer.registerConfig(ModConfig.Type.SYNCED, SyncedConfig.CONFIG, SimpleBackups.MODID + "/server.toml"); // todo 26.4 - rename to synced.toml
        NeoForge.EVENT_BUS.register(new EventListener());
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::onRegisterPayloadHandler);

        if (LocalConfig.backupsDisabledByJvmArg()) {
            LOGGER.info("##########################################");
            LOGGER.info("#  Backups are disabled by JVM argument  #");
            LOGGER.info("##########################################");
        }

        if (dist.isClient()) {
            ClientInit.init(modEventBus, modContainer);
        }
    }

    private void onRegisterPayloadHandler(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SimpleBackups.MODID)
                .versioned("1.0")
                .optional();

        registrar.playToClient(Pause.TYPE, Pause.CODEC, Pause::handle);
    }

    private void setup(FMLCommonSetupEvent event) {
        // NO-OP
    }
}
