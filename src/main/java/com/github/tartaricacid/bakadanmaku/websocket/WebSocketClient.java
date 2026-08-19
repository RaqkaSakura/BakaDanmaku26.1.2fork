package com.github.tartaricacid.bakadanmaku.websocket;

import com.github.tartaricacid.bakadanmaku.BakaDanmaku;
import com.github.tartaricacid.bakadanmaku.site.ISite;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebSocketClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 15;

    private final ISite site;
    private final URI uri;
    private final AtomicBoolean closed = new AtomicBoolean();

    private EventLoopGroup group;
    private Channel channel;
    private ScheduledFuture<?> heartbeatTask;

    public WebSocketClient(ISite site) {
        this.site = site;
        this.uri = URI.create(site.getUri());
    }

    public void open() throws Exception {
        SslContext sslContext = SslContextBuilder.forClient()
                .endpointIdentificationAlgorithm("HTTPS")
                .build();
        group = new MultiThreadIoEventLoopGroup(
                1,
                new DefaultThreadFactory("bakadanmaku-websocket", true),
                NioIoHandler.newFactory()
        );

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, false, EmptyHttpHeaders.INSTANCE
        );
        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker, site, this::handleDisconnected);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(
                                sslContext.newHandler(socketChannel.alloc(), uri.getHost(), effectivePort()),
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192),
                                handler
                        );
                    }
                });

        channel = bootstrap.connect(uri.getHost(), effectivePort()).sync().channel();
        if (!handler.handshakeFuture().await(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new TimeoutException("Timed out waiting for the WebSocket handshake");
        }
        if (!handler.handshakeFuture().isSuccess()) {
            throw new IllegalStateException("WebSocket handshake failed", handler.handshakeFuture().cause());
        }
        if (!site.initMessage(this)) {
            throw new IllegalStateException("Bilibili authentication could not be created");
        }

        heartbeatTask = channel.eventLoop().scheduleAtFixedRate(
                () -> sendMessage(site.getHeartBeat()),
                site.getHeartBeatInterval(),
                site.getHeartBeatInterval(),
                TimeUnit.MILLISECONDS
        );
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        cancelHeartbeat();
        Channel currentChannel = channel;
        if (currentChannel != null) {
            if (currentChannel.isActive()) {
                currentChannel.writeAndFlush(new CloseWebSocketFrame()).awaitUninterruptibly(2, TimeUnit.SECONDS);
            }
            currentChannel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        }

        EventLoopGroup currentGroup = group;
        if (currentGroup != null) {
            currentGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(3, TimeUnit.SECONDS);
        }
    }

    public void sendMessage(ByteBuf binaryData) {
        Channel currentChannel = channel;
        if (closed.get() || currentChannel == null || !currentChannel.isActive()) {
            ReferenceCountUtil.safeRelease(binaryData);
            return;
        }
        currentChannel.writeAndFlush(new BinaryWebSocketFrame(binaryData));
    }

    private int effectivePort() {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }

    private void handleDisconnected() {
        cancelHeartbeat();
        if (closed.compareAndSet(false, true)) {
            BakaDanmaku.LOGGER.info("WebSocket client disconnected");
        }
        EventLoopGroup currentGroup = group;
        if (currentGroup != null && !currentGroup.isShuttingDown()) {
            currentGroup.shutdownGracefully();
        }
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        heartbeatTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }
}
