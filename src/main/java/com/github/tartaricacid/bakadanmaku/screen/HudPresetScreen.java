package com.github.tartaricacid.bakadanmaku.screen;

import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.config.ConfigManger;
import com.github.tartaricacid.bakadanmaku.hud.DanmakuHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HudPresetScreen extends Screen {
    private static final int BUTTON_WIDTH = 260;

    private final Screen parent;
    private final BilibiliConfig config;
    private final BilibiliConfig.Display display;

    public HudPresetScreen(Screen parent, BilibiliConfig config) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.translatable("screen.bakadanmaku.presets"));
        this.parent = parent;
        this.config = config;
        this.display = config.getDisplay();
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        addRenderableWidget(Button.builder(Component.translatable("setting.bakadanmaku.preset_sky_note"),
                        button -> display.applySkyNotePreset())
                .bounds(x, 72, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(x, height - 44, 126, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("setting.bakadanmaku.exit_save"),
                        button -> saveAndExit())
                .bounds(x + 134, height - 44, 126, 20).build());
    }

    private void saveAndExit() {
        ConfigManger.saveBilibiliConfig(config);
        DanmakuHud.applyConfig(config);
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DanmakuHud.extractPreview(graphics, display);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
    }
}
