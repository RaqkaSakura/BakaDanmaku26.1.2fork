package com.github.tartaricacid.bakadanmaku.site.bilibili;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketAuthTest {
    @Test
    void preservesCookieValuesWithoutDoubleEncoding() {
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("SESSDATA", "abc%2Cdef");
        cookies.put("DedeUserID", "12345");

        String header = WebSocketAuth.buildCookieHeader(cookies, "generated-buvid3", "generated-buvid4");

        assertEquals(
                "SESSDATA=abc%2Cdef; DedeUserID=12345; buvid3=generated-buvid3; buvid4=generated-buvid4",
                header
        );
    }

    @Test
    void keepsConfiguredBuvidValues() {
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("buvid3", "configured-buvid3");
        cookies.put("buvid4", "configured-buvid4");

        String header = WebSocketAuth.buildCookieHeader(cookies, "generated-buvid3", "generated-buvid4");

        assertEquals("buvid3=configured-buvid3; buvid4=configured-buvid4", header);
    }

    @Test
    void parsesAuthenticatedUid() {
        long uid = WebSocketAuth.parseAuthenticatedUid(
                "{\"code\":0,\"data\":{\"isLogin\":true,\"mid\":12345}}"
        );

        assertEquals(12345, uid);
    }

    @Test
    void rejectsGuestOrMalformedResponses() {
        assertEquals(0, WebSocketAuth.parseAuthenticatedUid(
                "{\"code\":0,\"data\":{\"isLogin\":false,\"mid\":0}}"
        ));
        assertEquals(0, WebSocketAuth.parseAuthenticatedUid("not-json"));
    }
}
