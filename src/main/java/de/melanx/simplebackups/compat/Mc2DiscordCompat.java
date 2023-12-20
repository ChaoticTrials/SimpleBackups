package de.melanx.simplebackups.compat;

import de.melanx.simplebackups.SimpleBackups;
import fr.denisd3d.mc2discord.core.MessageManager;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

public class Mc2DiscordCompat {

    public static void announce(Component text) {
        MessageManager.sendInfoMessage(SimpleBackups.MODID, text.getString());
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("mc2discord");
    }
}
