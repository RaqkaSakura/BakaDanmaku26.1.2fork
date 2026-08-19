package com.github.tartaricacid.bakadanmaku.utils;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.github.tartaricacid.bakadanmaku.event.post.SendDanmakuEvent;
import com.github.tartaricacid.bakadanmaku.hud.DanmakuHud;
import com.github.tartaricacid.bakadanmaku.site.bilibili.BilibiliSite;
import com.github.tartaricacid.bakadanmaku.websocket.WebSocketClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static com.github.tartaricacid.bakadanmaku.config.ConfigManger.getBilibiliConfig;

public final class OpenCloseDanmaku {
    private static final ExecutorService CONNECTION_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BakaDanmaku-Connection");
        thread.setDaemon(true);
        return thread;
    });

    private static WebSocketClient webSocketClient;
    private static volatile boolean shuttingDown;

    private OpenCloseDanmaku() {
    }

    public static void openDanmaku() {
        submit(OpenCloseDanmaku::openNow);
    }

    public static void reloadDanmaku() {
        submit(() -> {
            closeNow();
            openNow();
        });
    }

    public static void closeDanmaku() {
        submit(OpenCloseDanmaku::closeNow);
    }

    public static void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        try {
            CONNECTION_EXECUTOR.execute(OpenCloseDanmaku::closeNow);
        } catch (RejectedExecutionException ignored) {
            BakaDanmaku.LOGGER.debug("Danmaku connection executor is already stopped");
        }
        CONNECTION_EXECUTOR.shutdown();
    }

    private static void submit(Runnable task) {
        if (shuttingDown) {
            return;
        }
        try {
            CONNECTION_EXECUTOR.execute(task);
        } catch (RejectedExecutionException ignored) {
            BakaDanmaku.LOGGER.debug("Danmaku connection executor is already stopped");
        }
    }

    private static void openNow() {
        if (shuttingDown) {
            return;
        }

        closeNow();
        BilibiliSite site = new BilibiliSite(getBilibiliConfig());
        DanmakuHud.applyConfig(site.getConfig());
        if (!site.getConfig().getRoom().isEnable()) {
            BakaDanmaku.LOGGER.info("Bilibili danmaku is disabled in the config");
            return;
        }

        WebSocketClient client = new WebSocketClient(site);
        webSocketClient = client;
        try {
            client.open();
        } catch (Exception exception) {
            if (webSocketClient == client) {
                webSocketClient = null;
            }
            client.close();
            BakaDanmaku.LOGGER.error("Failed to connect to Bilibili danmaku", exception);
            SendDanmakuEvent.send("弹幕连接失败，请检查配置和网络");
        }
    }

    private static void closeNow() {
        WebSocketClient client = webSocketClient;
        webSocketClient = null;
        if (client != null) {
            client.close();
        }
    }
}
