package com.github.tartaricacid.bakadanmaku.screen;

import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.hud.DanmakuHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudLayoutScreen extends Screen {
    private static final int RESIZE_HANDLE = 14;

    private final Screen parent;
    private final BilibiliConfig.Display display;
    private boolean dragging;
    private boolean resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private double resizeStartX;
    private double resizeStartY;
    private int resizeStartWidth;
    private int resizeStartHeight;

    public HudLayoutScreen(Screen parent, BilibiliConfig.Display display) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.translatable("screen.bakadanmaku.layout"));
        this.parent = parent;
        this.display = display;
    }

    @Override
    protected void init() {
        clampLayout();
        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("setting.bakadanmaku.reset_layout"),
                        button -> resetLayout())
                .bounds(width / 2 - 104, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 4, buttonY, 100, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }

        int x = display.getWindowX();
        int y = display.getWindowY();
        int windowWidth = display.getWindowWidth();
        int windowHeight = display.getWindowHeight();
        if (inside(event.x(), event.y(), x + windowWidth - RESIZE_HANDLE,
                y + windowHeight - RESIZE_HANDLE, RESIZE_HANDLE, RESIZE_HANDLE)) {
            resizing = true;
            resizeStartX = event.x();
            resizeStartY = event.y();
            resizeStartWidth = windowWidth;
            resizeStartHeight = windowHeight;
            return true;
        }
        if (inside(event.x(), event.y(), x, y, windowWidth, DanmakuHud.HEADER_HEIGHT)) {
            dragging = true;
            dragOffsetX = (int) event.x() - x;
            dragOffsetY = (int) event.y() - y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            display.setWindowX(clamp((int) event.x() - dragOffsetX, 0,
                    Math.max(0, width - display.getWindowWidth())));
            display.setWindowY(clamp((int) event.y() - dragOffsetY, 0,
                    Math.max(0, height - display.getWindowHeight())));
            return true;
        }
        if (resizing) {
            int maxWidth = Math.max(DanmakuHud.MIN_WINDOW_WIDTH, width - display.getWindowX());
            int maxHeight = Math.max(DanmakuHud.MIN_WINDOW_HEIGHT, height - display.getWindowY());
            display.setWindowWidth(clamp(resizeStartWidth + (int) (event.x() - resizeStartX),
                    DanmakuHud.MIN_WINDOW_WIDTH, maxWidth));
            display.setWindowHeight(clamp(resizeStartHeight + (int) (event.y() - resizeStartY),
                    DanmakuHud.MIN_WINDOW_HEIGHT, maxHeight));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging || resizing) {
            dragging = false;
            resizing = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DanmakuHud.extractPreview(graphics, display);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    private void resetLayout() {
        display.setWindowWidth(Math.max(DanmakuHud.MIN_WINDOW_WIDTH, Math.min(360, width - 40)));
        display.setWindowHeight(Math.max(DanmakuHud.MIN_WINDOW_HEIGHT, Math.min(180, height - 40)));
        display.setWindowX(Math.max(0, (width - display.getWindowWidth()) / 2));
        display.setWindowY(Math.max(0, (height - display.getWindowHeight()) / 2));
        clampLayout();
    }

    private void clampLayout() {
        display.setWindowWidth(Math.min(display.getWindowWidth(), Math.max(DanmakuHud.MIN_WINDOW_WIDTH, width)));
        display.setWindowHeight(Math.min(display.getWindowHeight(), Math.max(DanmakuHud.MIN_WINDOW_HEIGHT, height)));
        display.setWindowX(clamp(display.getWindowX(), 0, Math.max(0, width - display.getWindowWidth())));
        display.setWindowY(clamp(display.getWindowY(), 0, Math.max(0, height - display.getWindowHeight())));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
