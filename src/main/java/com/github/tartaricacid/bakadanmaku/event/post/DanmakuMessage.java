package com.github.tartaricacid.bakadanmaku.event.post;

public record DanmakuMessage(String text, long uid, String avatarUrl, String userName) {
    public DanmakuMessage {
        text = text == null ? "" : text;
        avatarUrl = avatarUrl == null ? "" : avatarUrl;
        userName = userName == null ? "" : userName;
    }

    public DanmakuMessage(String text, long uid, String avatarUrl) {
        this(text, uid, avatarUrl, "");
    }

    public DanmakuMessage(String text, long uid) {
        this(text, uid, "");
    }

    public static DanmakuMessage text(String text) {
        return new DanmakuMessage(text, -1L, "");
    }

    public boolean hasUser() {
        return uid > 0;
    }
}
