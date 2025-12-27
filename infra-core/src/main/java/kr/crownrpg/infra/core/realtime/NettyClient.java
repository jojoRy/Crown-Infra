package kr.crownrpg.infra.core.realtime;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static kr.crownrpg.infra.core.realtime.HandshakeHandler.Protocol;

/**
 * Netty client endpoint used by Paper servers to connect to the Velocity realtime server.
 */
public final class NettyClient {

    private final String host;
    private final int port;
    private final String environment;
    private final String serverId;
    private final RealtimeMessageHandler messageHandler;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private EventLoopGroup workerGroup;
    private Channel channel;

    public NettyClient(String host,
                       int port,
                       String environment,
                       String serverId,
                       RealtimeMessageHandler messageHandler) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(0, 0, 120, TimeUnit.SECONDS))
                                    .addLast(new LengthFieldBasedFrameDecoder(1_048_576, 0, 4, 0, 4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new HandshakeHandler(false, environment, serverId, null, messageHandler));
                        }
                    });
            ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port)).sync();
            channel = future.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting Netty client", e);
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Failed to start Netty realtime client", e);
        }
    }

    public boolean isStarted() {
        return started.get() && channel != null && channel.isActive();
    }

    public void send(String targetServerId, byte[] payload) {
        if (!isStarted()) {
            throw new IllegalStateException("Realtime client is not connected");
        }
        Channel ch = channel;
        ByteBuf buffer = Protocol.encodeData(ch.alloc(), targetServerId, serverId, payload);
        ch.writeAndFlush(buffer);
    }

    public void stop() {
        if (!started.get()) {
            return;
        }
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        started.set(false);
    }
}
