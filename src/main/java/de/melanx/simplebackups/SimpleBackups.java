package de.melanx.simplebackups;

import de.melanx.simplebackups.client.ClientEventHandler;
import de.melanx.simplebackups.config.CommonConfig;
import de.melanx.simplebackups.config.ServerConfig;
import de.melanx.simplebackups.network.SimpleNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SimpleBackups.MODID)
public class SimpleBackups {

    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleBackups.class);
    public static final String MODID = "simplebackups";
    private static final SimpleNetwork network = new SimpleNetwork();

    public SimpleBackups() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.CONFIG);
        MinecraftForge.EVENT_BUS.register(new EventListener());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ClientEventHandler()));
    }

    private void setup(FMLCommonSetupEvent event) {
        network.registerPackets();
    }

    public static SimpleNetwork network() {
        return network;
    }
}
