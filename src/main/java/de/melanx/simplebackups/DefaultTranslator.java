package de.melanx.simplebackups;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultTranslator {

    public static final List<UUID> PLAYERS_WITHOUT_MOD = Lists.newArrayList();
    private static final Gson GSON = new Gson();
    private static final Map<String, String> MOD_TRANSLATIONS = Maps.newHashMap();
    static {
        InputStream inputStream = DefaultTranslator.class.getResourceAsStream("/assets/" + SimpleBackups.MODID + "/lang/en_us.json");
        if (inputStream == null) {
            throw new IllegalStateException("Could not load default language for clients without Simple Backups");
        }

        JsonObject json = GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            MOD_TRANSLATIONS.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    private DefaultTranslator(){}

    public static String parseKey(String key, Object... parameters) {
        if (MOD_TRANSLATIONS.containsKey(key)) {
            return String.format(MOD_TRANSLATIONS.get(key), parameters);
        }

        return "Format error: " + key;
    }
}
