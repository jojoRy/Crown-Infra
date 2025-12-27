package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import kr.crownrpg.infra.api.redis.RealtimeChannel;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty-backed realtime channel supporting client (Paper) and server (Velocity) roles.
 */
public class NettyRealtimeChannel implements RealtimeChannel {

    private enum Mode {SERVER, CLIENT}

    private final String environment;
    private final String serverId;
    private final String token;
    private final Set<String> allowedPeerIds;
    private final String host;
    private final int port;
    private final Mode mode;
    private final RealtimeMessageHandler messageHandler;
    private final ChannelRegistry registry;
    private final BlockingDeque<OutboundMessage> outboundQueue;
    private final Logger logger = Logger.getLogger(NettyRealtimeChannel.class.getName());

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private NettyServer server;
    private NettyClient client;

    public NettyRealtimeChannel(String environment,
                                String serverId,
                                String token,
                                Set<String> allowedPeerIds,
                                String host,
                                int port,
                                boolean serverMode,
                                RealtimeMessageHandler messageHandler) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.token = Objects.requireNonNull(token, "token");
        this.allowedPeerIds = Set.copyOf(Objects.requireNonNull(allowedPeerIds, "allowedPeerIds"));
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.mode = serverMode ? Mode.SERVER : Mode.CLIENT;
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.registry = new ChannelRegistry();
        this.outboundQueue = new LinkedBlockingDeque<>(512);
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
            server = new NettyServer(host, port, environment, serverId, token, allowedPeerIds, registry, messageHandler);
            server.start();
        } else {
            client = new NettyClient(host, port, environment, serverId, token, allowedPeerIds, messageHandler, outboundQueue);
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
            logger.log(Level.WARNING, "Dropping realtime send to unregistered or inactive peer: " + targetNodeId);
        }
    }

    private void queueAndSendFromClient(String targetNodeId, byte[] payload) {
        OutboundMessage message = new OutboundMessage(targetNodeId, payload);
        if (!outboundQueue.offer(message)) {
            // Drop oldest to make room; this enforces the bounded queue policy.
            outboundQueue.poll();
            if (!outboundQueue.offer(message)) {
                logger.log(Level.WARNING, "Dropping realtime send due to full outbound queue: " + targetNodeId);
                return;
            }
        }
        if (client != null && client.isStarted()) {
            client.drainQueue();
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
