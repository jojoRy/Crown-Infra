package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import kr.crownrpg.infra.api.redis.RealtimeChannel;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty-backed realtime channel supporting client (Paper) and server (Velocity) roles.
 */
public class NettyRealtimeChannel implements RealtimeChannel {

    private enum Mode {SERVER, CLIENT}

    private final String environment;
    private final String serverId;
    private final String host;
    private final int port;
    private final Mode mode;
    private final RealtimeMessageHandler messageHandler;
    private final ChannelRegistry registry;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private NettyServer server;
    private NettyClient client;

    public NettyRealtimeChannel(String environment,
                                String serverId,
                                String host,
                                int port,
                                boolean serverMode,
                                RealtimeMessageHandler messageHandler) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.mode = serverMode ? Mode.SERVER : Mode.CLIENT;
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.registry = new ChannelRegistry();
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
            server = new NettyServer(host, port, environment, serverId, registry, messageHandler);
            server.start();
        } else {
            client = new NettyClient(host, port, environment, serverId, messageHandler);
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
        if (!isAvailable()) {
            throw new IllegalStateException("Realtime channel is not available");
        }
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("targetNodeId must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (mode == Mode.SERVER) {
            sendFromServer(targetNodeId, payload);
        } else {
            client.send(targetNodeId, payload);
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
        }
    }
}
