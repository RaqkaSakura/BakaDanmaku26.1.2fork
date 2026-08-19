package com.github.tartaricacid.bakadanmaku.hud;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AvatarManager {
    public static final int AVATAR_SIZE = 24;
    public static final int AVATAR_GAP = 5;

    private static final String CARD_API = "https://api.bilibili.com/x/web-interface/card?mid=";
    private static final String LIVE_USER_API = "https://api.live.bilibili.com/live_user/v1/Master/info?uid=";
    private static final int MAX_CACHE_SIZE = 128;
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 2048;
    private static final int CACHED_IMAGE_SIZE = 64;
    private static final long RETRY_DELAY_MS = 60_000L;

    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "BakaDanmaku-Avatar");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Long, CachedAvatar> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static final AtomicBoolean REPORTED_SUCCESS = new AtomicBoolean();
    private static volatile boolean enabled;
    private static volatile String cookieHeader = "";
    private static volatile long anchorUid = -1L;
    private static volatile String anchorName = "";
    private static volatile String anchorAvatarUrl = "";
    private static volatile boolean anchorLoading;
    private static volatile long anchorRetryAt;

    private AvatarManager() {
    }

    public static void configure(boolean value, Map<String, String> cookies) {
        if (!value && enabled) {
            clearCache();
        }
        enabled = value;
        cookieHeader = cookies == null ? "" : cookies.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank()
                        && entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    public static void configureAnchor(long uid) {
        if (anchorUid != uid) {
            anchorName = "";
            anchorAvatarUrl = "";
            anchorLoading = false;
            anchorRetryAt = 0L;
        }
        anchorUid = uid;
        if (uid > 0 && !anchorLoading && System.currentTimeMillis() >= anchorRetryAt
                && (anchorName.isBlank() || anchorAvatarUrl.isBlank())) {
            anchorLoading = true;
            DOWNLOAD_EXECUTOR.execute(() -> loadAnchorInfo(uid));
        }
    }

    public static long getAnchorUid() {
        return anchorUid;
    }

    public static String getAnchorName() {
        return anchorName;
    }

    public static String getAnchorAvatarUrl() {
        return anchorAvatarUrl;
    }

    public static Texture getTexture(long uid, String avatarUrl) {
        if (!enabled || uid <= 0) {
            return null;
        }

        CachedAvatar cached;
        synchronized (CACHE) {
            cached = CACHE.get(uid);
            if (cached == null) {
                cached = new CachedAvatar();
                CACHE.put(uid, cached);
                trimCacheLocked();
            }
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                cached.avatarUrl = normalizeAvatarUrl(avatarUrl);
            }
            if (cached.texture == null && !cached.loading && System.currentTimeMillis() >= cached.retryAt) {
                cached.loading = true;
                loadAsync(uid, cached, cached.avatarUrl);
            }
            return cached.texture;
        }
    }

    public static void render(GuiGraphicsExtractor graphics, long uid, String avatarUrl,
                              int x, int y, int size, int tint) {
        Texture texture = getTexture(uid, avatarUrl);
        if (texture == null) {
            renderDefault(graphics, x, y, size, tint);
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, 0, 0,
                size, size, texture.width(), texture.height(), texture.width(), texture.height(), tint);
    }

    public static void shutdown() {
        DOWNLOAD_EXECUTOR.shutdownNow();
        Minecraft client = Minecraft.getInstance();
        synchronized (CACHE) {
            for (CachedAvatar cached : CACHE.values()) {
                if (cached.texture != null) {
                    Identifier id = cached.texture.id();
                    client.execute(() -> client.getTextureManager().release(id));
                }
            }
            CACHE.clear();
        }
        anchorUid = -1L;
        anchorName = "";
        anchorAvatarUrl = "";
        anchorLoading = false;
    }

    private static void loadAsync(long uid, CachedAvatar expected, String directAvatarUrl) {
        DOWNLOAD_EXECUTOR.execute(() -> {
            NativeImage image = null;
            try {
                String avatarUrl = directAvatarUrl;
                if (avatarUrl == null || avatarUrl.isBlank()) {
                    avatarUrl = fetchAvatarUrl(uid);
                }
                if (avatarUrl == null || avatarUrl.isBlank()) {
                    throw new IOException("Bilibili avatar URL is empty");
                }
                try {
                    image = downloadImage(avatarUrl);
                } catch (IOException directFailure) {
                    if (directAvatarUrl == null || directAvatarUrl.isBlank()) {
                        throw directFailure;
                    }
                    avatarUrl = fetchAvatarUrl(uid);
                    image = downloadImage(avatarUrl);
                }
                NativeImage loadedImage = image;
                Minecraft.getInstance().execute(() -> registerTexture(uid, expected, loadedImage));
                image = null;
            } catch (Exception exception) {
                BakaDanmaku.LOGGER.warn("[BakaDanmaku] Failed to load avatar for uid {}: {}",
                        uid, exception.toString());
                BakaDanmaku.LOGGER.debug("Bilibili avatar failure details for uid " + uid, exception);
                synchronized (CACHE) {
                    if (CACHE.get(uid) == expected) {
                        expected.loading = false;
                        expected.retryAt = System.currentTimeMillis() + RETRY_DELAY_MS;
                    }
                }
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        });
    }

    private static void loadAnchorInfo(long uid) {
        try {
            HttpURLConnection connection = openConnection(LIVE_USER_API + uid, "application/json");
            String name = "";
            String face = "";
            try {
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Bilibili anchor API returned HTTP " + status);
                }
                byte[] body;
                try (InputStream input = connection.getInputStream()) {
                    body = input.readNBytes(MAX_JSON_BYTES + 1);
                }
                if (body.length > MAX_JSON_BYTES) {
                    throw new IOException("Bilibili anchor API response is too large");
                }
                JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject data = root.has("data") && root.get("data").isJsonObject()
                        ? root.getAsJsonObject("data") : null;
                JsonObject info = data != null && data.has("info") && data.get("info").isJsonObject()
                        ? data.getAsJsonObject("info") : null;
                if (info != null) {
                    name = info.has("uname") ? info.get("uname").getAsString() : "";
                    face = info.has("face") ? normalizeAvatarUrl(info.get("face").getAsString()) : "";
                }
            } finally {
                connection.disconnect();
            }
            if (anchorUid == uid) {
                anchorName = name == null ? "" : name;
                anchorAvatarUrl = face == null ? "" : face;
                anchorLoading = false;
                anchorRetryAt = 0L;
            }
        } catch (Exception exception) {
            BakaDanmaku.LOGGER.debug("Bilibili anchor info lookup failed for uid " + uid, exception);
            if (anchorUid == uid) {
                anchorLoading = false;
                anchorRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS;
            }
        }
    }

    private static String fetchAvatarUrl(long uid) throws IOException {
        try {
            String cardFace = fetchFace(CARD_API + uid, true);
            if (!cardFace.isBlank()) {
                return cardFace;
            }
        } catch (IOException exception) {
            BakaDanmaku.LOGGER.debug("Bilibili card API failed for uid " + uid, exception);
        }
        return fetchFace(LIVE_USER_API + uid, false);
    }

    private static String fetchFace(String url, boolean cardResponse) throws IOException {
        HttpURLConnection connection = openConnection(url, "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Bilibili user API returned HTTP " + status);
            }
            byte[] body;
            try (InputStream input = connection.getInputStream()) {
                body = input.readNBytes(MAX_JSON_BYTES + 1);
            }
            if (body.length > MAX_JSON_BYTES) {
                throw new IOException("Bilibili user API response is too large");
            }
            JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("code") || root.get("code").getAsInt() != 0 || !root.has("data")) {
                return "";
            }
            JsonObject data = root.getAsJsonObject("data");
            JsonObject user = cardResponse
                    ? data.getAsJsonObject("card")
                    : data.getAsJsonObject("info");
            if (user == null || !user.has("face")) {
                return "";
            }
            return normalizeAvatarUrl(user.get("face").getAsString());
        } finally {
            connection.disconnect();
        }
    }

    private static NativeImage downloadImage(String avatarUrl) throws IOException {
        String normalizedUrl = normalizeAvatarUrl(avatarUrl);
        URI uri;
        try {
            uri = URI.create(normalizedUrl);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Bilibili avatar URL is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !isAllowedAvatarHost(uri.getHost())) {
            throw new IOException("Bilibili avatar URL is not from an allowed CDN");
        }

        // Avatar URLs come from live-room payloads. Never send the user's login cookie to them.
        HttpURLConnection connection = openConnection(normalizedUrl, "image/*", false);
        byte[] body;
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Bilibili avatar CDN returned HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                body = input.readNBytes(MAX_IMAGE_BYTES + 1);
            }
        } finally {
            connection.disconnect();
        }
        if (body.length == 0 || body.length > MAX_IMAGE_BYTES) {
            throw new IOException("Bilibili avatar image has an invalid size");
        }
        NativeImage image = decodeImage(body);
        if (image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
            image.close();
            throw new IOException("Bilibili avatar has invalid dimensions");
        }
        return image;
    }

    static NativeImage decodeImage(byte[] body) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(body));
        if (source == null) {
            throw new IOException("Unsupported Bilibili avatar image format");
        }
        if (source.getWidth() <= 0 || source.getHeight() <= 0
                || source.getWidth() > MAX_IMAGE_DIMENSION || source.getHeight() > MAX_IMAGE_DIMENSION) {
            throw new IOException("Bilibili avatar has invalid dimensions");
        }

        BufferedImage scaled = new BufferedImage(CACHED_IMAGE_SIZE, CACHED_IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, CACHED_IMAGE_SIZE, CACHED_IMAGE_SIZE, null);
        } finally {
            graphics.dispose();
        }

        NativeImage image = new NativeImage(CACHED_IMAGE_SIZE, CACHED_IMAGE_SIZE, false);
        for (int y = 0; y < CACHED_IMAGE_SIZE; y++) {
            for (int x = 0; x < CACHED_IMAGE_SIZE; x++) {
                image.setPixel(x, y, scaled.getRGB(x, y));
            }
        }
        return image;
    }

    private static HttpURLConnection openConnection(String url, String accept) throws IOException {
        return openConnection(url, accept, true);
    }

    private static HttpURLConnection openConnection(String url, String accept, boolean includeCookie)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Referer", "https://www.bilibili.com/");
        if (includeCookie) {
            String cookie = cookieHeader;
            if (!cookie.isBlank()) {
                connection.setRequestProperty("Cookie", cookie);
            }
        }
        return connection;
    }

    private static boolean isAllowedAvatarHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.endsWith(".hdslb.com") || normalizedHost.endsWith(".biliimg.com")
                || normalizedHost.equals("hdslb.com") || normalizedHost.equals("biliimg.com");
    }

    private static String normalizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            return "";
        }
        return avatarUrl.startsWith("//") ? "https:" + avatarUrl : avatarUrl;
    }

    private static void registerTexture(long uid, CachedAvatar expected, NativeImage image) {
        synchronized (CACHE) {
            if (CACHE.get(uid) != expected) {
                image.close();
                return;
            }
            Identifier id = Identifier.fromNamespaceAndPath(BakaDanmaku.MOD_ID, "avatars/" + uid);
            DynamicTexture dynamicTexture = new DynamicTexture(() -> "BakaDanmaku avatar " + uid, image);
            Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
            expected.texture = new Texture(id, image.getWidth(), image.getHeight());
            expected.loading = false;
            expected.retryAt = 0L;
            if (REPORTED_SUCCESS.compareAndSet(false, true)) {
                BakaDanmaku.LOGGER.info("[BakaDanmaku] Avatar loading is working; first loaded uid is {}", uid);
            } else {
                BakaDanmaku.LOGGER.debug("[BakaDanmaku] Loaded avatar for uid {}", uid);
            }
        }
    }

    private static void trimCacheLocked() {
        if (CACHE.size() <= MAX_CACHE_SIZE) {
            return;
        }
        Iterator<Map.Entry<Long, CachedAvatar>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHE_SIZE && iterator.hasNext()) {
            Map.Entry<Long, CachedAvatar> entry = iterator.next();
            CachedAvatar cached = entry.getValue();
            if (cached.loading) {
                continue;
            }
            iterator.remove();
            if (cached.texture != null) {
                Identifier id = cached.texture.id();
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(id));
            }
        }
    }

    private static void clearCache() {
        List<Identifier> textureIds = new ArrayList<>();
        synchronized (CACHE) {
            for (CachedAvatar cached : CACHE.values()) {
                if (cached.texture != null) {
                    textureIds.add(cached.texture.id());
                }
            }
            CACHE.clear();
        }
        if (!textureIds.isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> textureIds.forEach(client.getTextureManager()::release));
        }
    }

    private static void renderDefault(GuiGraphicsExtractor graphics, int x, int y, int size, int tint) {
        graphics.fill(x, y, x + size, y + size, 0xCC30343A);
        int headSize = Math.max(6, size / 3);
        int headX = x + (size - headSize) / 2;
        int headY = y + Math.max(3, size / 6);
        graphics.fill(headX, headY, headX + headSize, headY + headSize, 0xFF9AA0A8);
        graphics.fill(x + size / 4, y + size * 3 / 5, x + size * 3 / 4, y + size - 3, 0xFF9AA0A8);
        graphics.outline(x, y, size, size, 0xFF606872);
    }

    public record Texture(Identifier id, int width, int height) {
    }

    private static final class CachedAvatar {
        private Texture texture;
        private String avatarUrl = "";
        private boolean loading;
        private long retryAt;
    }
}
