package com.github.tartaricacid.bakadanmaku.site.bilibili;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoomInfo {
    private static final String INIT_URL = "https://api.live.bilibili.com/room/v1/Room/room_init";
    private static final Pattern EXTRACT_ROOM_ID = Pattern.compile("\"room_id\":(\\d+),");
    private static final Pattern OWNER_ID = Pattern.compile("\"uid\":(\\d+),");

    private final long roomId;
    private final long ownerId;

    private RoomInfo(long roomId, long ownerId) {
        this.roomId = roomId;
        this.ownerId = ownerId;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public long getRoomId() {
        return roomId;
    }

    public static RoomInfo getRoomInfo(long roomId) {
        String extractRoomId = null;
        String ownerId = null;
        try {
            String data = BilibiliHttpClient.get(INIT_URL + "?id=" + roomId);
            Matcher matcher = EXTRACT_ROOM_ID.matcher(data);
            if (matcher.find()) {
                extractRoomId = matcher.group(1);
            }
            matcher = OWNER_ID.matcher(data);
            if (matcher.find()) {
                ownerId = matcher.group(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (extractRoomId == null) {
            return null;
        }
        return new RoomInfo(Long.parseLong(extractRoomId), Long.parseLong(ownerId == null ? "0" : ownerId));
    }
}
