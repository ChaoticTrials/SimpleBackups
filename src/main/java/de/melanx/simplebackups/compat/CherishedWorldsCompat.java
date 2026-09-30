package de.melanx.simplebackups.compat;

import com.illusivesoulworks.cherishedworlds.client.favorites.FavoritesList;
import de.melanx.simplebackups.config.LocalConfig;
import net.neoforged.fml.ModList;

public class CherishedWorldsCompat {

    public static boolean isFavorite(String worldName) {
        return !LocalConfig.onlyFavorites() || FavoritesList.contains(worldName);
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("cherishedworlds");
    }
}
