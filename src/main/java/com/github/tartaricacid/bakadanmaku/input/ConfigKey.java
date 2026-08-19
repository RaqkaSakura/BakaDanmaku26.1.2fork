package com.github.tartaricacid.bakadanmaku.input;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.github.tartaricacid.bakadanmaku.config.ConfigManger;
import com.github.tartaricacid.bakadanmaku.screen.DanmakuSettingsScreen;
import com.github.tartaricacid.bakadanmaku.utils.OpenCloseDanmaku;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ConfigKey {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(BakaDanmaku.MOD_ID, "main")
    );

    public static final KeyMapping CONFIG_KEY = new KeyMapping(
            "key.bakadanmaku.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    public static final KeyMapping SETTINGS_KEY = new KeyMapping(
            "key.bakadanmaku.settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    );

    public static void registerKeyboardInput() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CONFIG_KEY.consumeClick()) {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.translatable("message.bakadanmaku.reloading"));
                    OpenCloseDanmaku.reloadDanmaku();
                }
            }
            while (SETTINGS_KEY.consumeClick()) {
                client.setScreen(new DanmakuSettingsScreen(client.screen, ConfigManger.getBilibiliConfig()));
            }
        });
    }
}
