package com.github.tartaricacid.bakadanmaku.site.bilibili;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class BilibiliHttpClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;

    private BilibiliHttpClient() {
    }

    static HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        return connection;
    }

    static String get(String url) throws IOException {
        HttpURLConnection connection = openConnection(url);
        try {
            return IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
