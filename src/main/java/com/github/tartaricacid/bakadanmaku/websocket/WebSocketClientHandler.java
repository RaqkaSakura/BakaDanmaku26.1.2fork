package com.github.tartaricacid.bakadanmaku.websocket;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.github.tartaricacid.bakadanmaku.site.ISite;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;

public final class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private final ISite site;
    private final Runnable disconnectCallback;
    private ChannelPromise handshakeFuture;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker, ISite site, Runnable disconnectCallback) {
        this.handshaker = handshaker;
        this.site = site;
        this.disconnectCallback = disconnectCallback;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext context) {
        handshakeFuture = context.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        handshaker.handshake(context.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        disconnectCallback.run();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object message) throws Exception {
        Channel channel = context.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(channel, (FullHttpResponse) message);
            BakaDanmaku.LOGGER.info("WebSocket client connected");
            handshakeFuture.trySuccess();
            return;
        }

        if (message instanceof FullHttpResponse response) {
            throw new IllegalStateException("Unexpected HTTP response (status=" + response.status() + ", content="
                    + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) message;
        if (frame instanceof CloseWebSocketFrame) {
            channel.close();
        } else if (frame instanceof PingWebSocketFrame) {
            channel.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else {
            site.handMessage(frame);
        }
    }

    public ChannelPromise handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        BakaDanmaku.LOGGER.error("WebSocket client failure", cause);
        if (handshakeFuture != null && !handshakeFuture.isDone()) {
            handshakeFuture.tryFailure(cause);
        }
        context.close();
    }
}
