package de.melanx.simplebackups;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SimpleBackups.MODID)
public class SimpleBackups {

    public static final Logger LOGGER = LogManager.getLogger(SimpleBackups.class);
    public static final String MODID = "simplebackups";
    private static SimpleNetwork network;

    public SimpleBackups() {
        network = new SimpleNetwork();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHandler.COMMON_CONFIG);
        MinecraftForge.EVENT_BUS.register(new EventListener());
    }

    public static SimpleNetwork getNetwork() {
        return network;
    }
}
