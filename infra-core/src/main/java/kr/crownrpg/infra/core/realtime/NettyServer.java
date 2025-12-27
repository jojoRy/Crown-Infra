package kr.crownrpg.infra.core.realtime;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty server endpoint used by the Velocity proxy to route realtime payloads.
 */
public final class NettyServer {

    private final String bindHost;
    private final int port;
    private final String environment;
    private final String serverId;
    private final ChannelRegistry registry;
    private final RealtimeMessageHandler messageHandler;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServer(String bindHost,
                       int port,
                       String environment,
                       String serverId,
                       ChannelRegistry registry,
                       RealtimeMessageHandler messageHandler) {
        this.bindHost = Objects.requireNonNull(bindHost, "bindHost");
        this.port = port;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(0, 0, 120, TimeUnit.SECONDS))
                                    .addLast(new LengthFieldBasedFrameDecoder(1_048_576, 0, 4, 0, 4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new HandshakeHandler(true, environment, serverId, registry, messageHandler));
                        }
                    });
            ChannelFuture future = bootstrap.bind(new InetSocketAddress(bindHost, port)).sync();
            serverChannel = future.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting Netty server", e);
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Failed to start Netty realtime server", e);
        }
    }

    public boolean isStarted() {
        return started.get() && serverChannel != null && serverChannel.isActive();
    }

    public void stop() {
        if (!started.get()) {
            return;
        }
        registry.closeAll();
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        started.set(false);
    }
}
