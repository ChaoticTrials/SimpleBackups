package de.melanx.simplebackups.compat;

import ml.denisd3d.mc2discord.core.Mc2Discord;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

public class Mc2DiscordCompat {

    public static void announce(Component text) {
        Mc2Discord.INSTANCE.messageManager.sendInfoMessage(text.getString());
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("mc2discord");
    }
}
