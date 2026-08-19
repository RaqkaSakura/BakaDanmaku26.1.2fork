package com.github.tartaricacid.bakadanmaku.site.bilibili;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoomId {
    private static final String INIT_URL = "https://api.live.bilibili.com/room/v1/Room/room_init";
    private static final Pattern EXTRACT_ROOM_ID = Pattern.compile("\"room_id\":(\\d+),");

    public static int getRealRoomId(int roomId) {
        String realRoomId = null;
        try {
            String data = BilibiliHttpClient.get(INIT_URL + "?id=" + roomId);
            Matcher matcher = EXTRACT_ROOM_ID.matcher(data);
            if (matcher.find()) {
                realRoomId = matcher.group(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (realRoomId == null) {
            return -1;
        }
        return Integer.parseInt(realRoomId);
    }
}
