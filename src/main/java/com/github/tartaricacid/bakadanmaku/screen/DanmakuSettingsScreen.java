package com.github.tartaricacid.bakadanmaku.screen;

import com.github.tartaricacid.bakadanmaku.config.BilibiliConfig;
import com.github.tartaricacid.bakadanmaku.config.ConfigManger;
import com.github.tartaricacid.bakadanmaku.hud.DanmakuHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

public final class DanmakuSettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 260;
    private static final List<BilibiliConfig.DisplayMode> MODES = Arrays.asList(BilibiliConfig.DisplayMode.CHAT,
            BilibiliConfig.DisplayMode.HUD);
    private static final List<Integer> COLORS = java.util.stream.IntStream.range(0, 16).boxed().toList();
    private static final List<Integer> MATERIALS = List.of(0, 16);

    private final Screen parent;
    private final BilibiliConfig config;
    private final BilibiliConfig.Display display;
    private CycleButton<BilibiliConfig.DisplayMode> modeButton;
    private CycleButton<Integer> backgroundMaterialButton;
    private CycleButton<Integer> backgroundColorButton;
    private OpacitySlider hudOpacitySlider;
    private OpacitySlider dialogOpacitySlider;
    private CycleButton<Integer> dialogMaterialButton;
    private CycleButton<Integer> dialogColorButton;
    private CycleButton<Boolean> avatarButton;
    private CycleButton<Boolean> fontButton;
    private TextSizeSlider textSizeSlider;
    private CycleButton<Integer> textColorButton;
    private Button layoutButton;
    private Button presetButton;

    public DanmakuSettingsScreen(Screen parent, BilibiliConfig config) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.translatable("screen.bakadanmaku.settings"));
        this.parent = parent;
        this.config = config;
        this.display = config.getDisplay();
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        modeButton = addRenderableWidget(CycleButton.builder(this::modeLabel, display.getMode())
                .withValues(MODES)
                .create(x, 44, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.display_mode"),
                        (button, value) -> {
                            display.setMode(value);
                            updateHudControls();
                        }));
        addRenderableWidget(new net.minecraft.client.gui.components.StringWidget(x, 70, BUTTON_WIDTH, 16,
                Component.translatable("setting.bakadanmaku.group_hud"), font));
        backgroundMaterialButton = addRenderableWidget(CycleButton.builder(this::backgroundMaterialLabel,
                        materialValue(display.getBackgroundColor(), DanmakuHud.CUSTOM_BACKGROUND_STYLE))
                .withValues(MATERIALS)
                .create(x, 88, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.window_material"),
                        (button, value) -> {
                            if (value == DanmakuHud.CUSTOM_BACKGROUND_STYLE) {
                                display.setBackgroundColor(DanmakuHud.CUSTOM_BACKGROUND_STYLE);
                            } else if (display.getBackgroundColor() == DanmakuHud.CUSTOM_BACKGROUND_STYLE) {
                                display.setBackgroundColor(15);
                            }
                            updateHudControls();
                        }));
        backgroundColorButton = addRenderableWidget(CycleButton.builder(this::terracottaLabel,
                        baseColorValue(display.getBackgroundColor()))
                .withValues(COLORS)
                .create(x, 110, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.window_color"),
                        (button, value) -> display.setBackgroundColor(value)));
        hudOpacitySlider = addRenderableWidget(new OpacitySlider(x, 132, BUTTON_WIDTH, 20,
                display.getBackgroundOpacity(), display::setBackgroundOpacity,
                "setting.bakadanmaku.hud_opacity"));
        dialogOpacitySlider = addRenderableWidget(new OpacitySlider(x, 154, BUTTON_WIDTH, 20,
                display.getDialogOpacity(), display::setDialogOpacity,
                "setting.bakadanmaku.dialog_opacity"));
        dialogMaterialButton = addRenderableWidget(CycleButton.builder(this::dialogMaterialLabel,
                        materialValue(display.getDialogStyle(), DanmakuHud.CUSTOM_DIALOG_STYLE))
                .withValues(MATERIALS)
                .create(x, 176, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.dialog_material"),
                        (button, value) -> {
                            if (value == DanmakuHud.CUSTOM_DIALOG_STYLE) {
                                display.setDialogStyle(DanmakuHud.CUSTOM_DIALOG_STYLE);
                            } else if (display.getDialogStyle() == DanmakuHud.CUSTOM_DIALOG_STYLE) {
                                display.setDialogStyle(0);
                            }
                            updateHudControls();
                        }));
        dialogColorButton = addRenderableWidget(CycleButton.builder(this::woolLabel,
                        baseColorValue(display.getDialogStyle()))
                .withValues(COLORS)
                .create(x, 198, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.dialog_color"),
                        (button, value) -> display.setDialogStyle(value)));
        avatarButton = addRenderableWidget(CycleButton.onOffBuilder(
                display.isShowAvatars())
                .create(x, 220, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.avatars"),
                        (button, value) -> display.setShowAvatars(value)));
        addRenderableWidget(new net.minecraft.client.gui.components.StringWidget(x, 246, BUTTON_WIDTH, 16,
                Component.translatable("setting.bakadanmaku.group_text"), font));
        fontButton = addRenderableWidget(CycleButton.onOffBuilder(
                display.isCustomFont())
                .create(x, 264, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.custom_font"),
                        (button, value) -> display.setCustomFont(value)));
        textSizeSlider = addRenderableWidget(new TextSizeSlider(x, 286, BUTTON_WIDTH, 20,
                display.getTextSize(), display::setTextSize));
        textColorButton = addRenderableWidget(CycleButton.builder(this::woolLabel, display.getTextColor())
                .withValues(COLORS)
                .create(x, 308, BUTTON_WIDTH, 20, Component.translatable("setting.bakadanmaku.text_color"),
                        (button, value) -> display.setTextColor(value)));
        layoutButton = addRenderableWidget(Button.builder(Component.translatable("setting.bakadanmaku.edit_layout"),
                        button -> minecraft.setScreen(new HudLayoutScreen(this, display)))
                .bounds(x, 334, BUTTON_WIDTH, 20).build());
        presetButton = addRenderableWidget(Button.builder(Component.translatable("setting.bakadanmaku.presets"),
                        button -> minecraft.setScreen(new HudPresetScreen(this, config)))
                .bounds(x, 358, BUTTON_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(x, height - 44, 126, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(x + 134, height - 44, 126, 20).build());
        updateHudControls();
    }

    private Component modeLabel(BilibiliConfig.DisplayMode mode) {
        return Component.translatable(mode == BilibiliConfig.DisplayMode.HUD
                ? "setting.bakadanmaku.mode_hud"
                : "setting.bakadanmaku.mode_chat");
    }

    private Component woolLabel(int color) {
        return DanmakuHud.WoolPalette.name(baseColorValue(color));
    }

    private Component terracottaLabel(int color) {
        return DanmakuHud.TerracottaPalette.name(baseColorValue(color));
    }

    private Component backgroundMaterialLabel(int value) {
        return Component.translatable(value == DanmakuHud.CUSTOM_BACKGROUND_STYLE
                ? "setting.bakadanmaku.background_custom" : "setting.bakadanmaku.material_terracotta");
    }

    private Component dialogMaterialLabel(int value) {
        return Component.translatable(value == DanmakuHud.CUSTOM_DIALOG_STYLE
                ? "setting.bakadanmaku.dialog_style_custom" : "setting.bakadanmaku.material_wool");
    }

    private static int materialValue(int value, int customValue) {
        return value == customValue ? customValue : 0;
    }

    private static int baseColorValue(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private void updateHudControls() {
        boolean active = display.getMode() == BilibiliConfig.DisplayMode.HUD;
        backgroundMaterialButton.active = active;
        backgroundColorButton.active = active;
        backgroundColorButton.visible = display.getBackgroundColor() != DanmakuHud.CUSTOM_BACKGROUND_STYLE;
        hudOpacitySlider.active = active;
        dialogOpacitySlider.active = active;
        dialogMaterialButton.active = active;
        dialogColorButton.active = active;
        dialogColorButton.visible = display.getDialogStyle() != DanmakuHud.CUSTOM_DIALOG_STYLE;
        avatarButton.active = active;
        fontButton.active = active;
        textSizeSlider.active = active;
        textColorButton.active = active;
        layoutButton.active = active;
        presetButton.active = active;
    }

    private void saveAndClose() {
        ConfigManger.saveBilibiliConfig(config);
        DanmakuHud.applyConfig(config);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        private final IntConsumer onChanged;
        private final String labelKey;

        private OpacitySlider(int x, int y, int width, int height, int initial, IntConsumer onChanged,
                              String labelKey) {
            super(x, y, width, height, Component.empty(), Math.max(0, Math.min(100, initial)) / 100.0D);
            this.onChanged = onChanged;
            this.labelKey = labelKey;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(labelKey, Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            onChanged.accept((int) Math.round(value * 100));
        }
    }

    private static final class TextSizeSlider extends AbstractSliderButton {
        private final IntConsumer onChanged;

        private TextSizeSlider(int x, int y, int width, int height, int initial, IntConsumer onChanged) {
            super(x, y, width, height, Component.empty(),
                    (Math.max(50, Math.min(200, initial)) - 50) / 150.0D);
            this.onChanged = onChanged;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("setting.bakadanmaku.text_size", 50 + Math.round((float) value * 150)));
        }

        @Override
        protected void applyValue() {
            onChanged.accept(50 + Math.round((float) value * 150));
        }
    }
}
