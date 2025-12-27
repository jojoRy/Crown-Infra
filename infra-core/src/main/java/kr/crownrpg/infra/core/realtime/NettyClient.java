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
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static kr.crownrpg.infra.core.realtime.HandshakeHandler.Protocol;

/**
 * Netty client endpoint used by Paper servers to connect to the Velocity realtime server.
 */
public final class NettyClient {

    private final String host;
    private final int port;
    private final String environment;
    private final String serverId;
    private final String token;
    private final Set<String> allowedPeerIds;
    private final RealtimeMessageHandler messageHandler;
    private final BlockingDeque<NettyRealtimeChannel.OutboundMessage> outboundQueue;
    private final Logger logger = Logger.getLogger(NettyClient.class.getName());

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean handshakeComplete = new AtomicBoolean(false);

    private EventLoopGroup workerGroup;
    private volatile Channel channel;

    public NettyClient(String host,
                       int port,
                       String environment,
                       String serverId,
                       String token,
                       Set<String> allowedPeerIds,
                       RealtimeMessageHandler messageHandler,
                       BlockingDeque<NettyRealtimeChannel.OutboundMessage> outboundQueue) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.token = Objects.requireNonNull(token, "token");
        this.allowedPeerIds = Objects.requireNonNull(allowedPeerIds, "allowedPeerIds");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.outboundQueue = Objects.requireNonNull(outboundQueue, "outboundQueue");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        stopping.set(false);
        workerGroup = new NioEventLoopGroup();
        connectWithBackoff(0);
    }

    public boolean isStarted() {
        return started.get() && handshakeComplete.get() && channel != null && channel.isActive();
    }

    public void drainQueue() {
        Channel ch = channel;
        if (!handshakeComplete.get() || ch == null || !ch.isActive()) {
            return;
        }
        NettyRealtimeChannel.OutboundMessage message;
        while ((message = outboundQueue.poll()) != null) {
            ByteBuf buffer = Protocol.encodeData(ch.alloc(), message.targetNodeId(), serverId, message.payload());
            ch.write(buffer);
        }
        ch.flush();
    }

    public void stop() {
        stopping.set(true);
        if (!started.get()) {
            return;
        }
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        handshakeComplete.set(false);
        started.set(false);
    }

    private void connectWithBackoff(int attempt) {
        if (stopping.get()) {
            return;
        }
        long delaySeconds = attempt == 0 ? 0 : Math.min(30, (long) Math.pow(2, attempt));
        if (delaySeconds > 0) {
            logger.info("Realtime client reconnecting in " + delaySeconds + "s (attempt " + attempt + ")");
        }
        workerGroup.schedule(() -> doConnect(attempt), delaySeconds, TimeUnit.SECONDS);
    }

    private void doConnect(int attempt) {
        if (stopping.get()) {
            return;
        }
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
                                    .addLast(new HandshakeHandler(false, environment, serverId, token, allowedPeerIds, null, messageHandler, new HandshakeHandler.HandshakeCallback() {
                                        @Override
                                        public void onAccepted(String remoteServerId, Channel channel) {
                                            handshakeComplete.set(true);
                                            drainQueue();
                                        }
                                    }));
                        }
                    });
            ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port)).sync();
            channel = future.channel();
            channel.closeFuture().addListener(f -> {
                handshakeComplete.set(false);
                if (!stopping.get()) {
                    connectWithBackoff(1);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting Netty client", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Realtime client connection failed (attempt " + attempt + ")", e);
            if (!stopping.get()) {
                connectWithBackoff(Math.min(attempt + 1, 5));
            }
        }
    }
}
