package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import kr.crownrpg.infra.api.redis.RealtimeChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty-backed realtime channel supporting client (Paper) and server (Velocity) roles.
 */
public class NettyRealtimeChannel implements RealtimeChannel {

    private enum Mode {SERVER, CLIENT}

    private final String environment;
    private final String serverId;
    private final String token;
    private final String host;
    private final int port;
    private final Mode mode;
    private final RealtimeMessageHandler messageHandler;
    private final ChannelRegistry registry;
    private final BlockingDeque<OutboundMessage> outboundQueue;
    private final RealtimeChannelSettings settings;
    private final AtomicLong droppedOutboundCount = new AtomicLong(0);
    private final Logger logger = LoggerFactory.getLogger(NettyRealtimeChannel.class);

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private NettyServer server;
    private NettyClient client;

    public NettyRealtimeChannel(String environment,
                                String serverId,
                                String token,
                                java.util.Set<String> allowedPeerIds,
                                String host,
                                int port,
                                boolean serverMode,
                                RealtimeMessageHandler messageHandler) {
        this(environment, serverId, token, RealtimeChannelSettings.defaults(allowedPeerIds), host, port, serverMode, messageHandler);
    }

    public NettyRealtimeChannel(String environment,
                                String serverId,
                                String token,
                                RealtimeChannelSettings settings,
                                String host,
                                int port,
                                boolean serverMode,
                                RealtimeMessageHandler messageHandler) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.token = Objects.requireNonNull(token, "token");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.mode = serverMode ? Mode.SERVER : Mode.CLIENT;
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.registry = new ChannelRegistry();
        this.outboundQueue = new LinkedBlockingDeque<>(settings.outboundQueueCapacity());
    }

    @Override
    public void start() {
        if (stopped.get()) {
            throw new IllegalStateException("Realtime channel has been stopped and cannot be restarted");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (mode == Mode.SERVER) {
            server = new NettyServer(host, port, environment, serverId, token, settings, registry, messageHandler);
            server.start();
        } else {
            client = new NettyClient(host, port, environment, serverId, token, settings, messageHandler, outboundQueue, droppedOutboundCount);
            client.start();
        }
    }

    @Override
    public void stop() {
        if (!started.get() || stopped.get()) {
            return;
        }
        if (mode == Mode.SERVER) {
            if (server != null) {
                server.stop();
            }
        } else {
            if (client != null) {
                client.stop();
            }
        }
        registry.closeAll();
        stopped.set(true);
    }

    @Override
    public boolean isAvailable() {
        if (!started.get() || stopped.get()) {
            return false;
        }
        return mode == Mode.SERVER ? (server != null && server.isStarted()) : (client != null && client.isStarted());
    }

    @Override
    public void send(String targetNodeId, byte[] payload) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("targetNodeId must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (mode == Mode.SERVER) {
            if (!isAvailable()) {
                throw new IllegalStateException("Realtime channel is not available");
            }
            sendFromServer(targetNodeId, payload);
        } else {
            if (stopped.get() || !started.get()) {
                throw new IllegalStateException("Realtime channel has not been started");
            }
            queueAndSendFromClient(targetNodeId, payload);
        }
    }

    private void sendFromServer(String targetNodeId, byte[] payload) {
        if (serverId.equals(targetNodeId)) {
            messageHandler.onMessage(serverId, payload);
            return;
        }
        Channel target = registry.find(targetNodeId);
        if (target != null && target.isActive()) {
            ByteBuf buffer = HandshakeHandler.Protocol.encodeData(target.alloc(), targetNodeId, serverId, payload);
            target.writeAndFlush(buffer);
        } else {
            logger.warn("실시간 대상 '{}'이(가) 미등록/비활성 상태여서 메시지를 드롭합니다", targetNodeId);
        }
    }

    private void queueAndSendFromClient(String targetNodeId, byte[] payload) {
        OutboundMessage message = new OutboundMessage(targetNodeId, payload);
        if (!outboundQueue.offer(message)) {
            OutboundMessage dropped = outboundQueue.poll();
            if (dropped != null) {
                logOutboundDrop("outbound 큐 포화로 가장 오래된 메시지 드롭");
            }
            if (!outboundQueue.offer(message)) {
                logOutboundDrop("outbound 큐 포화로 신규 메시지 드롭");
                return;
            }
        }
        if (client != null && client.isStarted()) {
            client.drainQueue();
        }
    }

    private void logOutboundDrop(String reason) {
        long totalDrops = droppedOutboundCount.incrementAndGet();
        if (totalDrops % settings.dropWarnThreshold() == 0) {
            logger.warn("실시간 outbound 큐 드롭 {}회 발생 ({}).", totalDrops, reason);
        } else {
            logger.debug("실시간 outbound 큐 드롭 {}회 발생 ({}).", totalDrops, reason);
        }
    }

    static final class OutboundMessage {
        private final String targetNodeId;
        private final byte[] payload;

        OutboundMessage(String targetNodeId, byte[] payload) {
            this.targetNodeId = targetNodeId;
            this.payload = payload;
        }

        String targetNodeId() {
            return targetNodeId;
        }

        byte[] payload() {
            return payload;
        }
    }
}
