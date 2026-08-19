package com.github.tartaricacid.bakadanmaku.hud;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.event.post.DanmakuMessage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class DanmakuHud {
    public static final int MIN_WINDOW_WIDTH = 220;
    public static final int MIN_WINDOW_HEIGHT = 120;
    public static final int HEADER_HEIGHT = 18;
    public static final int CUSTOM_BACKGROUND_STYLE = 16;
    public static final int CUSTOM_DIALOG_STYLE = 16;
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath(BakaDanmaku.MOD_ID, "danmaku_hud");
    private static final FontDescription HUD_FONT_DESCRIPTION = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(BakaDanmaku.MOD_ID, "hud"));
    private static final int MAX_MESSAGES = 50;
    private static final long MESSAGE_LIFETIME_MS = 12_000L;
    private static final int CARD_GAP = 4;
    private static final int TILE_SIZE = 16;
    private static final int LIGHT_BLUE_FRAME_TEXTURE_WIDTH = 256;
    private static final int LIGHT_BLUE_FRAME_TEXTURE_HEIGHT = 128;
    private static final int BEIGE_NOTE_TEXTURE_WIDTH = 256;
    private static final int BEIGE_NOTE_TEXTURE_HEIGHT = 128;
    private static final Identifier LIGHT_BLUE_FRAME_TEXTURE = Identifier.fromNamespaceAndPath(
            BakaDanmaku.MOD_ID, "textures/hud/light_blue_frame.png");
    private static final Identifier BEIGE_NOTE_TEXTURE = Identifier.fromNamespaceAndPath(
            BakaDanmaku.MOD_ID, "textures/hud/beige_note.png");
    private static final Deque<Notice> NOTICES = new ArrayDeque<>();
    private static volatile BilibiliConfig.DisplayMode mode = BilibiliConfig.DisplayMode.CHAT;
    private static volatile int backgroundStyle = 15;
    private static volatile int backgroundOpacity = 72;
    private static volatile int dialogOpacity = 92;
    private static volatile int dialogStyle = 0;
    private static volatile boolean showAvatars = true;
    private static volatile boolean customFont = true;
    private static volatile int textSize = 100;
    private static volatile int textColor = 0;
    private static volatile int windowX = 20;
    private static volatile int windowY = 20;
    private static volatile int windowWidth = 360;
    private static volatile int windowHeight = 180;

    private DanmakuHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(ELEMENT_ID, DanmakuHud::extractRenderState);
    }

    public static void applyConfig(BilibiliConfig config) {
        BilibiliConfig.Display display = config.getDisplay();
        mode = display.getMode();
        backgroundStyle = display.getBackgroundColor();
        backgroundOpacity = display.getBackgroundOpacity();
        dialogOpacity = display.getDialogOpacity();
        dialogStyle = display.getDialogStyle();
        showAvatars = display.getMode() == BilibiliConfig.DisplayMode.HUD && display.isShowAvatars();
        customFont = display.isCustomFont();
        textSize = display.getTextSize();
        textColor = display.getTextColor();
        AvatarManager.configure(display.getMode() == BilibiliConfig.DisplayMode.HUD, config.getRoom().getCookie());
        windowX = display.getWindowX();
        windowY = display.getWindowY();
        windowWidth = display.getWindowWidth();
        windowHeight = display.getWindowHeight();
    }

    public static boolean isHudMode() {
        return mode == BilibiliConfig.DisplayMode.HUD;
    }

    public static void addMessage(String message) {
        addMessage(DanmakuMessage.text(message));
    }

    public static void addMessage(DanmakuMessage message) {
        if (!isHudMode() || message == null || message.text().isBlank()) {
            return;
        }
        synchronized (NOTICES) {
            while (NOTICES.size() >= MAX_MESSAGES) {
                NOTICES.removeFirst();
            }
            NOTICES.addLast(new Notice(message.text(), message.uid(), message.avatarUrl(),
                    System.currentTimeMillis() + MESSAGE_LIFETIME_MS));
        }
    }

    public static void extractPreview(GuiGraphicsExtractor graphics, BilibiliConfig.Display display) {
        List<Notice> preview = List.of(
                new Notice("§b<夏花海晴> §f独立 HUD 弹幕预览", -1L, "", Long.MAX_VALUE),
                new Notice("§b<一只蝙蝠丶> §f较长的弹幕会根据窗口宽度自动换行，调整窗口大小即可查看效果。",
                        -1L, "", Long.MAX_VALUE),
                new Notice("§6[舰] §2<预览用户> §f十六色羊毛材质弹幕框", -1L, "", Long.MAX_VALUE)
        );
        renderWindow(graphics, display.getWindowX(), display.getWindowY(), display.getWindowWidth(),
                display.getWindowHeight(), display.getBackgroundColor(), display.getBackgroundOpacity(),
                display.getDialogOpacity(), display.getDialogStyle(), display.getTextSize(),
                display.getTextColor(), preview, true);
    }

    private static void extractRenderState(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!isHudMode() || client.level == null || client.options.hideGui) {
            return;
        }

        List<Notice> messages = new ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (NOTICES) {
            NOTICES.removeIf(notice -> notice.expiresAt < now);
            messages.addAll(NOTICES);
        }
        renderWindow(graphics, windowX, windowY, windowWidth, windowHeight, backgroundStyle,
                backgroundOpacity, dialogOpacity, dialogStyle, textSize, textColor, messages, false);
    }

    private static void renderWindow(GuiGraphicsExtractor graphics, int configuredX, int configuredY,
                                     int configuredWidth, int configuredHeight, int terracottaStyle,
                                     int opacity, int cardOpacity, int woolStyle, int configuredTextSize,
                                     int configuredTextColor,
                                     List<Notice> messages, boolean editing) {
        int width = Math.min(Math.max(Math.min(MIN_WINDOW_WIDTH, graphics.guiWidth()), configuredWidth),
                graphics.guiWidth());
        int height = Math.min(Math.max(Math.min(MIN_WINDOW_HEIGHT, graphics.guiHeight()), configuredHeight),
                graphics.guiHeight());
        int x = Math.max(0, Math.min(configuredX, graphics.guiWidth() - width));
        int y = Math.max(0, Math.min(configuredY, graphics.guiHeight() - height));
        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        float textScale = Math.max(0.5F, Math.min(2.0F, configuredTextSize / 100.0F));
        int lineHeight = Math.max(1, Math.round(Minecraft.getInstance().font.lineHeight * textScale));
        int textColorValue = WoolPalette.color(configuredTextColor);

        graphics.enableScissor(x, y, x + width, y + height);
        if (terracottaStyle == CUSTOM_BACKGROUND_STYLE) {
            stretchTexture(graphics, LIGHT_BLUE_FRAME_TEXTURE, x, y, width, height,
                    LIGHT_BLUE_FRAME_TEXTURE_WIDTH, LIGHT_BLUE_FRAME_TEXTURE_HEIGHT, alpha);
        } else {
            tileTexture(graphics, TerracottaPalette.texture(terracottaStyle), x, y, width, height, alpha);
        }
        int shadeAlpha = alpha * 72 / 255;
        graphics.fill(x, y, x + width, y + height, shadeAlpha << 24);
        graphics.fill(x, y, x + width, y + HEADER_HEIGHT,
                terracottaStyle == CUSTOM_BACKGROUND_STYLE ? 0x33105C8A : 0x88000000);
        Font hudFont = Minecraft.getInstance().font;
        long anchorUid = AvatarManager.getAnchorUid();
        String anchorName = AvatarManager.getAnchorName();
        if (anchorUid > 0) {
            AvatarManager.render(graphics, anchorUid, AvatarManager.getAnchorAvatarUrl(),
                    x + 4, y + Math.max(0, (HEADER_HEIGHT - 18) / 2), 18, 0xFFFFFFFF);
            if (!anchorName.isBlank()) {
                graphics.text(hudFont, Component.literal(anchorName), x + 27, y + 5, 0xFFFFFFFF, true);
            }
        }

        int contentX = x + 6;
        int contentY = y + HEADER_HEIGHT + 5;
        int contentWidth = Math.max(40, width - 12);
        int contentHeight = Math.max(0, height - HEADER_HEIGHT - 10);
        List<Card> cards = layoutCards(Minecraft.getInstance().font, messages, contentWidth - 16,
                textScale, lineHeight);
        Deque<Card> visibleCards = selectVisibleCards(cards, contentHeight);
        int cardY = contentY;
        for (Card card : visibleCards) {
            int cardAlpha = Math.max(0, Math.min(100, cardOpacity)) * 255 / 100;
            if (woolStyle == CUSTOM_DIALOG_STYLE) {
                stretchTexture(graphics, BEIGE_NOTE_TEXTURE, contentX, cardY, contentWidth, card.height,
                        BEIGE_NOTE_TEXTURE_WIDTH, BEIGE_NOTE_TEXTURE_HEIGHT, cardAlpha);
                graphics.outline(contentX, cardY, contentWidth, card.height, 0xFFD0A95A);
            } else {
                tileTexture(graphics, WoolPalette.texture(woolStyle), contentX, cardY, contentWidth, card.height,
                        cardAlpha);
                int overlayAlpha = cardAlpha * (WoolPalette.isDark(woolStyle) ? 68 : 51) / 255;
                graphics.fill(contentX, cardY, contentX + contentWidth, cardY + card.height,
                        overlayAlpha << 24);
                graphics.outline(contentX, cardY, contentWidth, card.height, WoolPalette.color(woolStyle));
            }
            int textX = contentX + 8;
            if (!editing && showAvatars && card.uid > 0) {
                AvatarManager.render(graphics, card.uid, card.avatarUrl, contentX + 6,
                        cardY + (card.height - AvatarManager.AVATAR_SIZE) / 2,
                        AvatarManager.AVATAR_SIZE, 0xFFFFFFFF);
                textX += AvatarManager.AVATAR_SIZE + AvatarManager.AVATAR_GAP;
            }
            int lineY = cardY + 5;
            for (FormattedCharSequence line : card.lines) {
                drawText(graphics, hudFont, line, textX, lineY, textColorValue, textScale);
                lineY += lineHeight;
            }
            cardY += card.height + CARD_GAP;
        }

        if (editing) {
            graphics.outline(x, y, width, height, 0xFFFFFFFF);
            graphics.fill(x + width - 12, y + height - 3, x + width, y + height, 0xFFFFFFFF);
            graphics.fill(x + width - 3, y + height - 12, x + width, y + height, 0xFFFFFFFF);
        }
        graphics.disableScissor();
    }

    private static List<Card> layoutCards(Font font, List<Notice> notices, int textWidth,
                                          float textScale, int lineHeight) {
        List<Card> cards = new ArrayList<>();
        for (Notice notice : notices) {
            boolean withAvatar = showAvatars && notice.uid > 0;
            Component message = styledText(Component.literal(notice.message));
            List<FormattedCharSequence> lines = font.split(message,
                    Math.max(24, Math.round((textWidth - (withAvatar ? AvatarManager.AVATAR_SIZE + AvatarManager.AVATAR_GAP : 0))
                            / textScale)));
            if (!lines.isEmpty()) {
                int messageHeight = lines.size() * lineHeight + 10;
                int cardHeight = Math.max(withAvatar ? AvatarManager.AVATAR_SIZE + 10 : Math.round(20 * textScale),
                        messageHeight);
                cards.add(new Card(notice.uid, notice.avatarUrl, lines, cardHeight));
            }
        }
        return cards;
    }

    private static MutableComponent styledText(MutableComponent component) {
        if (!customFont) {
            return component;
        }
        return component.withStyle(style -> style.withFont(HUD_FONT_DESCRIPTION).withBold(true));
    }

    private static void drawText(GuiGraphicsExtractor graphics, Font font, Component text,
                                 int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x * (1.0F - scale), y * (1.0F - scale));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, x, y, color, true);
        graphics.pose().popMatrix();
    }

    private static void drawText(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text,
                                 int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x * (1.0F - scale), y * (1.0F - scale));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, x, y, color, true);
        graphics.pose().popMatrix();
    }

    private static Deque<Card> selectVisibleCards(List<Card> cards, int availableHeight) {
        Deque<Card> visible = new ArrayDeque<>();
        int usedHeight = 0;
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card card = cards.get(i);
            int required = card.height + (visible.isEmpty() ? 0 : CARD_GAP);
            if (!visible.isEmpty() && usedHeight + required > availableHeight) {
                break;
            }
            visible.addFirst(card);
            usedHeight += required;
            if (usedHeight >= availableHeight) {
                break;
            }
        }
        return visible;
    }

    private static void tileTexture(GuiGraphicsExtractor graphics, Identifier texture,
                                    int x, int y, int width, int height, int alpha) {
        int tint = Math.max(0, Math.min(255, alpha)) << 24 | 0x00FFFFFF;
        for (int offsetY = 0; offsetY < height; offsetY += TILE_SIZE) {
            int tileHeight = Math.min(TILE_SIZE, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += TILE_SIZE) {
                int tileWidth = Math.min(TILE_SIZE, width - offsetX);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + offsetX, y + offsetY,
                        0, 0, tileWidth, tileHeight, TILE_SIZE, TILE_SIZE, tint);
            }
        }
    }

    private static void stretchTexture(GuiGraphicsExtractor graphics, Identifier texture,
                                       int x, int y, int width, int height,
                                       int textureWidth, int textureHeight, int alpha) {
        int tint = Math.max(0, Math.min(255, alpha)) << 24 | 0x00FFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height,
                textureWidth, textureHeight, textureWidth, textureHeight, tint);
    }

    private record Notice(String message, long uid, String avatarUrl, long expiresAt) {
    }

    private record Card(long uid, String avatarUrl, List<FormattedCharSequence> lines, int height) {
    }

    public static final class WoolPalette {
        private static final int[] COLORS = {
                0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA,
                0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
                0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA,
                0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21
        };

        private WoolPalette() {
        }

        public static Component name(int index) {
            return ColorPalette.name(index);
        }

        public static Identifier texture(int index) {
            return blockTexture(ColorPalette.IDS[Math.min(15, clampStyle(index))] + "_wool");
        }

        public static int color(int index) {
            return COLORS[clampStyle(index)];
        }

        public static boolean isDark(int index) {
            int color = color(index);
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            return (r * 299 + g * 587 + b * 114) / 1000 < 150;
        }
    }

    public static final class TerracottaPalette {
        private TerracottaPalette() {
        }

        public static Component name(int index) {
            return ColorPalette.name(index);
        }

        public static Identifier texture(int index) {
            return blockTexture(ColorPalette.IDS[clampStyle(index)] + "_terracotta");
        }
    }

    private static final class ColorPalette {
        private static final String[] IDS = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };

        private ColorPalette() {
        }

        private static Component name(int index) {
            return Component.translatable("color.minecraft." + IDS[clampStyle(index)]);
        }
    }

    private static int clampStyle(int index) {
        return Math.max(0, Math.min(15, index));
    }

    private static Identifier blockTexture(String name) {
        return Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + name + ".png");
    }
}
