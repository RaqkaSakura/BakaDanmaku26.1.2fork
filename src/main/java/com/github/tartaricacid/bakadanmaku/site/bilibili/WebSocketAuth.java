package com.github.tartaricacid.bakadanmaku.site.bilibili;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.event.post.SendDanmakuEvent;
import com.github.tartaricacid.bakadanmaku.hud.AvatarManager;
import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSocketAuth {
    private static final String INIT_URL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo";
    private static final String USER_INFO_URL = "https://api.bilibili.com/x/web-interface/nav";
    private static final Gson GSON = new Gson();
    private static final String AUTH_FORMAT = "{\"uid\":%d,\"roomid\":%d,\"protover\":3,\"buvid\":\"%s\",\"platform\":\"web\",\"type\":2,\"key\":\"%s\"}";

    public static byte[] newAuth(BilibiliConfig.Room room) {
        RoomInfo roomInfo = RoomInfo.getRoomInfo(room.getId());
        if (roomInfo == null) {
            return null;
        }
        AvatarManager.configureAnchor(roomInfo.getOwnerId());

        if (room.isManualAuth()) {
            return room
                    .getAuth()
                    .replace("${roomId}", String.valueOf(roomInfo.getRoomId()))
                    .getBytes(StandardCharsets.UTF_8);
        }

        Map<String, String> configuredCookies = room.getCookie();
        String buvid3 = getCookie(configuredCookies, "buvid3");
        String buvid4 = getCookie(configuredCookies, "buvid4");
        if (isBlank(buvid3) || isBlank(buvid4)) {
            Buvid generatedBuvid = Buvid.newBuvid(roomInfo.getRoomId());
            if (generatedBuvid != null) {
                if (isBlank(buvid3)) {
                    buvid3 = generatedBuvid.getBuvid3();
                }
                if (isBlank(buvid4)) {
                    buvid4 = generatedBuvid.getBuvid4();
                }
            }
        }

        if (isBlank(buvid3)) {
            return null;
        }

        String cookie = buildCookieHeader(configuredCookies, buvid3, buvid4);
        long uid = 0;
        if (isBlank(getCookie(configuredCookies, "SESSDATA"))) {
            BakaDanmaku.LOGGER.info("[BakaDanmaku] Login as guest; Bilibili may mask usernames");
            sendAuthWarning("当前使用 B 站游客鉴权，部分用户昵称会被 B 站显示为 ***。"
                    + "请在 config/bakadanmaku/bilibili.json 的 room.cookie 中填写登录 Cookie，然后按 B 重载。");
        } else {
            uid = getAuthenticatedUid(cookie);
            if (uid > 0) {
                BakaDanmaku.LOGGER.info("[BakaDanmaku] Login as authenticated Bilibili user");
            } else {
                BakaDanmaku.LOGGER.warn("[BakaDanmaku] Bilibili Cookie is invalid or expired; falling back to guest authentication");
                sendAuthWarning("B 站登录 Cookie 无效或已过期，已退回游客鉴权；部分用户昵称会显示为 ***。");
            }
        }

        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("id", String.valueOf(roomInfo.getRoomId()));
            params.put("type", "0");

            HttpURLConnection conn = BilibiliHttpClient.openConnection(INIT_URL + "?" + WbiSigner.wbiSign(params));
            try {
                conn.addRequestProperty(HttpHeaders.COOKIE, cookie);
                conn.setRequestMethod("GET");
                conn.addRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0");
                conn.addRequestProperty(HttpHeaders.REFERER, "https://www.bilibili.com/");
                String data = IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8);
                JsonObject response = GSON.fromJson(data, JsonObject.class);
                String token = response.getAsJsonObject("data").get("token").getAsString();
                String auth = String.format(
                        AUTH_FORMAT, uid, roomInfo.getRoomId(), buvid3, token
                );
                return auth.getBytes(StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            BakaDanmaku.LOGGER.error(e);
        }
        return null;
    }

    private static void sendAuthWarning(String message) {
        SendDanmakuEvent.send("[BakaDanmaku] " + message);
    }

    static String buildCookieHeader(Map<String, String> configuredCookies, String buvid3, String buvid4) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (configuredCookies != null) {
            configuredCookies.forEach((key, value) -> {
                if (!isBlank(key) && !isBlank(value)) {
                    cookies.put(key, value);
                }
            });
        }
        if (!isBlank(buvid3)) {
            cookies.putIfAbsent("buvid3", buvid3);
        }
        if (!isBlank(buvid4)) {
            cookies.putIfAbsent("buvid4", buvid4);
        }
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    static long parseAuthenticatedUid(String responseBody) {
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            if (response == null || !response.has("code") || response.get("code").getAsInt() != 0) {
                return 0;
            }
            JsonObject data = response.getAsJsonObject("data");
            if (data == null || !data.has("isLogin") || !data.get("isLogin").getAsBoolean() || !data.has("mid")) {
                return 0;
            }
            long uid = data.get("mid").getAsLong();
            return uid > 0 ? uid : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long getAuthenticatedUid(String cookie) {
        HttpURLConnection conn = null;
        try {
            conn = BilibiliHttpClient.openConnection(USER_INFO_URL);
            conn.addRequestProperty(HttpHeaders.COOKIE, cookie);
            conn.addRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0");
            conn.addRequestProperty(HttpHeaders.REFERER, "https://www.bilibili.com/");
            conn.setRequestMethod("GET");
            String response = IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8);
            return parseAuthenticatedUid(response);
        } catch (Exception e) {
            BakaDanmaku.LOGGER.warn("[BakaDanmaku] Failed to validate the Bilibili login Cookie", e);
            return 0;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String getCookie(Map<String, String> cookies, String name) {
        return cookies == null ? null : cookies.get(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class Buvid {
        static final String INIT_URL = "https://api.bilibili.com/x/frontend/finger/spi";
        static final Gson GSON = new Gson();

        private final String buvid3;
        private final String buvid4;

        private Buvid(String buvid3, String buvid4) {
            this.buvid3 = buvid3;
            this.buvid4 = buvid4;
        }

        public static Buvid newBuvid(long roomId) {
            try {
                String data = BilibiliHttpClient.get(INIT_URL + "?id=" + roomId);
                JsonObject response = GSON.fromJson(data, JsonObject.class);
                JsonObject obj = response.getAsJsonObject("data");
                return new Buvid(
                        obj.get("b_3").getAsString(),
                        obj.get("b_4").getAsString()
                );
            } catch (Exception e) {
                BakaDanmaku.LOGGER.error(e);
            }
            return null;
        }

        public String getBuvid3() {
            return buvid3;
        }

        public String getBuvid4() {
            return buvid4;
        }
    }
}
