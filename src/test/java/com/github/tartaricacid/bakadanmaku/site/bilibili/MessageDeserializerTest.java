package com.github.tartaricacid.bakadanmaku.site.bilibili;

import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.event.post.DanmakuMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDeserializerTest {
    @Test
    void danmakuCarriesBilibiliUidForAvatarLookup() {
        BilibiliConfig config = new BilibiliConfig().deco();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(DanmakuMessage.class, new MessageDeserializer(config))
                .create();

        DanmakuMessage message = gson.fromJson(
                "{\"cmd\":\"DANMU_MSG\",\"info\":[[],\"hello\",[12345,\"测试用户\",0,0,0,0,0,\"\"]]}",
                DanmakuMessage.class);

        assertEquals(12345L, message.uid());
        assertTrue(message.text().contains("测试用户"));
        assertTrue(message.text().contains("hello"));
    }

    @Test
    void blockedDanmakuDoesNotStartAvatarLookup() {
        BilibiliConfig config = new BilibiliConfig().deco();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(DanmakuMessage.class, new MessageDeserializer(config))
                .create();

        DanmakuMessage message = gson.fromJson(
                "{\"cmd\":\"DANMU_MSG\",\"info\":[[],\"小鬼\",[12345,\"测试用户\",0,0,0,0,0,\"\"]]}",
                DanmakuMessage.class);

        assertNull(message);
    }

    @Test
    void readsAvatarEmbeddedInModernDanmakuMetadata() {
        BilibiliConfig config = new BilibiliConfig().deco();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(DanmakuMessage.class, new MessageDeserializer(config))
                .create();

        DanmakuMessage message = gson.fromJson(
                "{\"cmd\":\"DANMU_MSG:4:0:2:2:2:0\",\"info\":[[\"{\\\"user\\\":{\\\"base\\\":{\\\"face\\\":\\\"//i0.hdslb.com/avatar.jpg\\\"}}}\"],\"hello\",[12345,\"测试用户\",0,0,0,0,0,\"\"]]}",
                DanmakuMessage.class);

        assertEquals("https://i0.hdslb.com/avatar.jpg", message.avatarUrl());
    }
}
