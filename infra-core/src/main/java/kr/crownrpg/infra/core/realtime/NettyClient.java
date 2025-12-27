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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private final RealtimeMessageHandler messageHandler;
    private final BlockingDeque<NettyRealtimeChannel.OutboundMessage> outboundQueue;
    private final RealtimeChannelSettings settings;
    private final AtomicLong reconnectAttempts = new AtomicLong();
    private final AtomicLong reconnectFailures = new AtomicLong();
    private final AtomicLong outboundDropCounter;
    private final Logger logger = LoggerFactory.getLogger(NettyClient.class);

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
                       RealtimeChannelSettings settings,
                       RealtimeMessageHandler messageHandler,
                       BlockingDeque<NettyRealtimeChannel.OutboundMessage> outboundQueue,
                       AtomicLong outboundDropCounter) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.token = Objects.requireNonNull(token, "token");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.outboundQueue = Objects.requireNonNull(outboundQueue, "outboundQueue");
        this.outboundDropCounter = Objects.requireNonNull(outboundDropCounter, "outboundDropCounter");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        stopping.set(false);
        reconnectAttempts.set(0);
        reconnectFailures.set(0);
        workerGroup = new NioEventLoopGroup();
        scheduleReconnect(true);
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

    private void connectWithBackoff(boolean immediate) {
        if (stopping.get()) {
            return;
        }
        long attemptNumber = reconnectAttempts.incrementAndGet();
        if (attemptNumber > settings.maxReconnectAttempts()) {
            logger.warn("재연결 최대 시도({})를 초과하여 중단합니다", settings.maxReconnectAttempts());
            return;
        }
        long drops = outboundDropCounter.get();
        if (drops > 0 && drops % settings.dropWarnThreshold() == 0) {
            logger.warn("실시간 outbound 큐 누적 드롭 {}회 - 재연결 대기 중 메시지 손실 여부를 확인하세요", drops);
        }
        long delayMillis = immediate ? 0L : Math.min(settings.maxReconnectDelayMillis(),
                settings.initialReconnectDelayMillis() * (long) Math.pow(2, Math.max(0, attemptNumber - 1)));
        if (delayMillis > 0) {
            logger.info("실시간 클라이언트 재연결 대기 {}ms (시도 {}/{})", delayMillis, attemptNumber, settings.maxReconnectAttempts());
        }
        workerGroup.schedule(() -> doConnect(attemptNumber), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void doConnect(long attemptNumber) {
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
                                    .addLast(new HandshakeHandler(false, environment, serverId, token, settings, null, messageHandler, new HandshakeHandler.HandshakeCallback() {
                                        @Override
                                        public void onAccepted(String remoteServerId, Channel channel) {
                                            handshakeComplete.set(true);
                                            reconnectAttempts.set(0);
                                            reconnectFailures.set(0);
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
                    scheduleReconnect(false);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting Netty client", e);
        } catch (Exception e) {
            long failures = reconnectFailures.incrementAndGet();
            logger.warn("실시간 클라이언트 연결 실패 (시도 {} / 실패 누적 {} / 허용 {})", attemptNumber, failures, settings.maxReconnectAttempts(), e);
            if (!stopping.get()) {
                scheduleReconnect(false);
            }
        }
    }

    private void scheduleReconnect(boolean immediate) {
        connectWithBackoff(immediate);
    }
}
