package com.github.tartaricacid.bakadanmaku.event.post;

import com.github.tartaricacid.bakadanmaku.hud.DanmakuHud;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

public interface SendDanmakuEvent {
    Event<SendDanmakuEvent> EVENT = EventFactory.createArrayBacked(SendDanmakuEvent.class,
            listeners -> message -> {
                for (SendDanmakuEvent listener : listeners) {
                    InteractionResult result = listener.register(message);
                    if (result != InteractionResult.PASS) {
                        return result;
                    }
                }
                return InteractionResult.PASS;
            });

    static void register() {
        SendDanmakuEvent.EVENT.register(message -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (client.level != null) {
                    if (DanmakuHud.isHudMode()) {
                        DanmakuHud.addMessage(message);
                    } else {
                        client.gui.getChat().addClientSystemMessage(Component.literal(message.text()));
                    }
                }
            });
            return InteractionResult.SUCCESS;
        });
    }

    static void send(String message) {
        EVENT.invoker().register(DanmakuMessage.text(message));
    }

    InteractionResult register(DanmakuMessage message);
}
