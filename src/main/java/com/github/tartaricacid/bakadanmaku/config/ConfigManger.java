package com.github.tartaricacid.bakadanmaku.config;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManger {
    private static final Path CONFIG_FOLDER = FabricLoader.getInstance().getConfigDir().resolve(BakaDanmaku.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static BilibiliConfig getBilibiliConfig() {
        BilibiliConfig config = new BilibiliConfig();

        if (!CONFIG_FOLDER.toFile().isDirectory()) {
            try {
                Files.createDirectories(CONFIG_FOLDER);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Path configPath = CONFIG_FOLDER.resolve(config.getConfigName() + ".json");
        if (configPath.toFile().isFile()) {
            try {
                String json = FileUtils.readFileToString(configPath.toFile(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonObject room = root.has("room") && root.get("room").isJsonObject()
                        ? root.getAsJsonObject("room")
                        : null;
                boolean missingCookieConfig = room == null || !room.has("cookie");
                JsonObject display = root.has("display") && root.get("display").isJsonObject()
                        ? root.getAsJsonObject("display")
                        : null;
                boolean missingAvatarConfig = display == null || !display.has("show_avatars");
                boolean missingFontConfig = display == null || !display.has("custom_font");
                boolean missingTextSizeConfig = display == null || !display.has("text_size");
                boolean missingTextColorConfig = display == null || !display.has("text_color");

                config = GSON.fromJson(root, BilibiliConfig.class);
                if (missingCookieConfig || missingAvatarConfig || missingFontConfig || missingTextSizeConfig
                        || missingTextColorConfig || config.getRoom().ensureCookieTemplate()) {
                    FileUtils.write(configPath.toFile(), GSON.toJson(config), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                FileUtils.write(configPath.toFile(), GSON.toJson(config), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return config.deco();
    }

    public static void saveBilibiliConfig(BilibiliConfig config) {
        config.getRoom().ensureCookieTemplate();
        if (!CONFIG_FOLDER.toFile().isDirectory()) {
            try {
                Files.createDirectories(CONFIG_FOLDER);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Path configPath = CONFIG_FOLDER.resolve(config.getConfigName() + ".json");
        try {
            FileUtils.write(configPath.toFile(), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
