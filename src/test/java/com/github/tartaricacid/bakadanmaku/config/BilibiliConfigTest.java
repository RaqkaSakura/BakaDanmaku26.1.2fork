package com.github.tartaricacid.bakadanmaku.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilibiliConfigTest {
    @Test
    void newConfigContainsLoginCookieTemplate() {
        Map<String, String> cookie = new BilibiliConfig().getRoom().getCookie();

        assertTrue(cookie.containsKey("SESSDATA"));
        assertTrue(cookie.containsKey("DedeUserID"));
        assertTrue(cookie.containsKey("DedeUserID__ckMd5"));
        assertTrue(cookie.containsKey("bili_jct"));
        assertTrue(cookie.containsKey("buvid3"));
        assertTrue(cookie.containsKey("buvid4"));
    }

    @Test
    void migrationPreservesConfiguredCookieValues() {
        BilibiliConfig.Room room = new BilibiliConfig.Room();
        room.setCookie(new LinkedHashMap<>(Map.of("SESSDATA", "configured-value")));

        assertTrue(room.ensureCookieTemplate());
        assertEquals("configured-value", room.getCookie().get("SESSDATA"));
        assertTrue(room.getCookie().containsKey("DedeUserID"));
        assertFalse(room.ensureCookieTemplate());
    }

    @Test
    void oldDisplayConfigEnablesAvatarsByDefault() {
        BilibiliConfig config = new Gson().fromJson("{\"display\":{\"mode\":\"HUD\"}}", BilibiliConfig.class);

        assertTrue(config.getDisplay().isShowAvatars());
        config.getDisplay().setShowAvatars(false);
        assertFalse(config.getDisplay().isShowAvatars());
    }
}
