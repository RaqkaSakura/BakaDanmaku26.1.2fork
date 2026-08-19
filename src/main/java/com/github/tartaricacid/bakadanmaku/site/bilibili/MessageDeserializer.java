package com.github.tartaricacid.bakadanmaku.site.bilibili;

import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.event.post.DanmakuMessage;
import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.Map;

public class MessageDeserializer implements JsonDeserializer<DanmakuMessage> {
    private final BilibiliConfig config;

    public MessageDeserializer(BilibiliConfig config) {
        this.config = config;
    }

    @Override
    public DanmakuMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (!json.isJsonObject()) {
            return null;
        }
        JsonObject data = json.getAsJsonObject();
        if (!data.has("cmd")) {
            return null;
        }
        String type = data.get("cmd").getAsString();
        if (type.startsWith("DANMU_MSG")) {
            type = "DANMU_MSG";
        }
        return switch (type) {
            case "DANMU_MSG" -> config.getDanmaku().isShow() ? handDanmaku(data) : null;
            case "SEND_GIFT" -> config.getGift().isShow() ? handGift(data) : null;
            case "COMBO_SEND" -> config.getGift().isShow() ? handComboGift(data) : null;
            case "INTERACT_WORD" -> config.getEnter().isShowNormal() ? handNormalEnter(data) : null;
            case "WELCOME" -> config.getEnter().isShowNormal() ? handWelcome(data) : null;
            case "WELCOME_GUARD" -> config.getEnter().isShowGuard() ? handGuardWelcome(data) : null;
            case "GUARD_BUY" -> config.getGuard().isShow() ? handBuyGuard(data) : null;
            case "SUPER_CHAT_MESSAGE" -> config.getSc().isShow() ? handSuperChat(data) : null;
            default -> null;
        };
    }

    private DanmakuMessage handDanmaku(JsonObject dataIn) {
        JsonArray info = dataIn.getAsJsonArray("info");
        if (info == null || info.size() < 3 || !info.get(2).isJsonArray()) {
            return null;
        }
        JsonArray user = info.get(2).getAsJsonArray();
        String userName = stringAt(user, 1);
        String danmaku = stringAt(info, 1);
        long uid = longAt(user, 0);
        boolean isAdmin = intAt(user, 2) == 1;
        boolean isGuard = StringUtils.isNotBlank(stringAt(user, 7));

        for (String block : config.getDanmaku().getBlockWord()) {
            if (danmaku.contains(block)) {
                return null;
            }
        }

        String formatted;
        if (isAdmin) {
            formatted = String.format(config.getDanmaku().getAdminStyleFormatted(), userName, danmaku);
        } else if (isGuard) {
            formatted = String.format(config.getDanmaku().getGuardStyleFormatted(), userName, danmaku);
        } else {
            formatted = String.format(config.getDanmaku().getNormalStyleFormatted(), userName, danmaku);
        }
        return new DanmakuMessage(formatted, uid, findAvatarUrl(info.get(0)), userName);
    }

    private DanmakuMessage handGift(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        String userName = value(data, "uname");
        String action = value(data, "action");
        String giftName = value(data, "giftName");
        int num = intValue(data, "num");

        for (String block : config.getGift().getBlockGift()) {
            if (giftName.equals(block)) {
                return null;
            }
        }

        String message = String.format(config.getGift().getStyleFormatted(), userName, action, giftName, num);
        return new DanmakuMessage(message, longValue(data, "uid"), findAvatarUrl(data), userName);
    }

    private DanmakuMessage handComboGift(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        String userName = value(data, "uname");
        String action = value(data, "action");
        String giftName = value(data, "gift_name");
        int num = intValue(data, "total_num");

        for (String block : config.getGift().getBlockGift()) {
            if (giftName.equals(block)) {
                return null;
            }
        }

        String message = String.format(config.getGift().getStyleFormatted(), userName, action, giftName, num);
        return new DanmakuMessage(message, longValue(data, "uid"), findAvatarUrl(data), userName);
    }

    private DanmakuMessage handNormalEnter(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        String userName = value(data, "uname");
        return new DanmakuMessage(String.format(config.getEnter().getNormalStyleFormatted(), userName),
                longValue(data, "uid"), findAvatarUrl(data), userName);
    }

    private DanmakuMessage handWelcome(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        String userName = value(data, "uname");
        return new DanmakuMessage(String.format(config.getEnter().getNormalStyleFormatted(), userName),
                longValue(data, "uid"), findAvatarUrl(data), userName);
    }

    private DanmakuMessage handGuardWelcome(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        String userName = value(data, "username");
        int level = intValue(data, "guard_level");
        String message = switch (level) {
            case 1 -> String.format(config.getEnter().getGuardStyle1Formatted(), userName);
            case 2 -> String.format(config.getEnter().getGuardStyle2Formatted(), userName);
            case 3 -> String.format(config.getEnter().getGuardStyle3Formatted(), userName);
            default -> null;
        };
        return message == null ? null : new DanmakuMessage(message, longValue(data, "uid"), findAvatarUrl(data),
                userName);
    }

    private DanmakuMessage handBuyGuard(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        String userName = value(data, "username");
        int level = intValue(data, "guard_level");
        String message = switch (level) {
            case 1 -> String.format(config.getGuard().getGuardStyle1Formatted(), userName);
            case 2 -> String.format(config.getGuard().getGuardStyle2Formatted(), userName);
            case 3 -> String.format(config.getGuard().getGuardStyle3Formatted(), userName);
            default -> null;
        };
        return message == null ? null : new DanmakuMessage(message, longValue(data, "uid"), findAvatarUrl(data),
                userName);
    }

    private DanmakuMessage handSuperChat(JsonObject dataIn) {
        JsonObject data = dataIn.getAsJsonObject("data");
        JsonObject userInfo = data.getAsJsonObject("user_info");
        String userName = value(userInfo, "uname");
        String message = value(data, "message");
        int price = intValue(data, "price");
        long uid = longValue(data, "uid");
        if (uid <= 0) {
            uid = longValue(userInfo, "uid");
        }
        return new DanmakuMessage(String.format(config.getSc().getStyleFormatted(), userName, message, price), uid,
                findAvatarUrl(data), userName);
    }

    static String findAvatarUrl(JsonElement element) {
        return findAvatarUrl(element, 0);
    }

    private static String findAvatarUrl(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > 10) {
            return "";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("face") && object.get("face").isJsonPrimitive()) {
                String face = object.get("face").getAsString();
                if (isAvatarUrl(face)) {
                    return normalizeAvatarUrl(face);
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String result = findAvatarUrl(entry.getValue(), depth + 1);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String result = findAvatarUrl(child, depth + 1);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String nested = element.getAsString().trim();
            if ((nested.startsWith("{") || nested.startsWith("[")) && nested.contains("\"face\"")) {
                try {
                    return findAvatarUrl(JsonParser.parseString(nested), depth + 1);
                } catch (JsonParseException ignored) {
                }
            }
        }
        return "";
    }

    private static boolean isAvatarUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://")
                || value.startsWith("//"));
    }

    private static String normalizeAvatarUrl(String value) {
        return value.startsWith("//") ? "https:" + value : value;
    }

    private static String value(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static int intValue(JsonObject object, String key) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsInt() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long longValue(JsonObject object, String key) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsLong() : -1L;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static String stringAt(JsonArray array, int index) {
        return array != null && array.size() > index && !array.get(index).isJsonNull()
                ? array.get(index).getAsString() : "";
    }

    private static int intAt(JsonArray array, int index) {
        try {
            return array != null && array.size() > index ? array.get(index).getAsInt() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long longAt(JsonArray array, int index) {
        try {
            return array != null && array.size() > index ? array.get(index).getAsLong() : -1L;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
