package com.github.tartaricacid.bakadanmaku;

import com.github.tartaricacid.bakadanmaku.event.post.SendDanmakuEvent;
import com.github.tartaricacid.bakadanmaku.hud.DanmakuHud;
import com.github.tartaricacid.bakadanmaku.hud.AvatarManager;
import com.github.tartaricacid.bakadanmaku.input.ConfigKey;
import com.github.tartaricacid.bakadanmaku.utils.OpenCloseDanmaku;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BakaDanmaku implements ClientModInitializer {
    public static final String MOD_ID = "bakadanmaku";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        DanmakuHud.register();
        KeyMappingHelper.registerKeyMapping(ConfigKey.CONFIG_KEY);
        KeyMappingHelper.registerKeyMapping(ConfigKey.SETTINGS_KEY);
        ConfigKey.registerKeyboardInput();
        SendDanmakuEvent.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> OpenCloseDanmaku.openDanmaku());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OpenCloseDanmaku.closeDanmaku());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            OpenCloseDanmaku.shutdown();
            AvatarManager.shutdown();
        });
    }
}
