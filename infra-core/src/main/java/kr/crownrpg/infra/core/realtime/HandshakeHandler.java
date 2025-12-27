package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Handles initial handshake to validate environment, token, and allowed peers.
 * <p>
 * Protocol frames:
 * - HELLO   : {protocolVersion, environment, serverId, token}
 * - WELCOME : {protocolVersion, environment, serverId}
 * - REJECT  : {reason}
 * - DATA    : {targetServerId, sourceServerId, payload}
 */
class HandshakeHandler extends ChannelInboundHandlerAdapter {

    static final byte TYPE_HELLO = 0x01;
    static final byte TYPE_WELCOME = 0x02;
    static final byte TYPE_REJECT = 0x03;
    static final byte TYPE_DATA = 0x04;

    static final int PROTOCOL_VERSION = 1;

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB safety
    private static final Logger LOGGER = LoggerFactory.getLogger(HandshakeHandler.class);

    private final boolean serverSide;
    private final String environment;
    private final String selfServerId;
    private final String token;
    private final RealtimeChannelSettings settings;
    private final ChannelRegistry registry;
    private final RealtimeMessageHandler messageHandler;
    private final HandshakeCallback callback;

    HandshakeHandler(boolean serverSide,
                     String environment,
                     String selfServerId,
                     String token,
                     RealtimeChannelSettings settings,
                     ChannelRegistry registry,
                     RealtimeMessageHandler messageHandler,
                     HandshakeCallback callback) {
        this.serverSide = serverSide;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.selfServerId = Objects.requireNonNull(selfServerId, "selfServerId");
        this.token = Objects.requireNonNull(token, "token");
        this.settings = Objects.requireNonNull(settings, "settings");
        if (serverSide && registry == null) {
            throw new IllegalArgumentException("registry is required on server side");
        }
        if (serverSide && messageHandler == null) {
            throw new IllegalArgumentException("messageHandler is required on server side");
        }
        this.registry = registry;
        this.messageHandler = messageHandler;
        this.callback = callback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!serverSide) {
            ByteBuf hello = Protocol.encodeHello(ctx.alloc(), PROTOCOL_VERSION, environment, selfServerId, token);
            ctx.writeAndFlush(hello);
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf buffer)) {
            ctx.fireChannelRead(msg);
            return;
        }
        byte type = buffer.readByte();
        switch (type) {
            case TYPE_HELLO -> handleHello(ctx, buffer);
            case TYPE_WELCOME -> handleWelcome(ctx, buffer);
            case TYPE_REJECT -> handleReject(ctx, buffer);
            default -> {
                buffer.release();
                LOGGER.warn("핸드셰이크 완료 전 예상치 못한 프레임을 드롭합니다: type={}", type);
                ctx.close();
            }
        }
    }

    private void handleHello(ChannelHandlerContext ctx, ByteBuf buffer) {
        if (!serverSide) {
            buffer.release();
            LOGGER.warn("클라이언트가 예상치 못한 HELLO를 수신하여 연결을 종료합니다");
            ctx.close();
            return;
        }
        Protocol.HelloFrame frame = Protocol.decodeHello(buffer, MAX_FRAME_LENGTH);
        buffer.release();
        if (frame.protocolVersion() != PROTOCOL_VERSION) {
            sendReject(ctx, "지원되지 않는 프로토콜 버전: " + frame.protocolVersion());
            return;
        }
        if (!environment.equals(frame.environment())) {
            sendReject(ctx, "환경 불일치 - 요청 환경: " + frame.environment());
            return;
        }
        if (!token.equals(frame.token())) {
            sendReject(ctx, "토큰 불일치");
            return;
        }
        if (!settings.isAllowedPeer(frame.serverId())) {
            sendReject(ctx, "허용되지 않은 peer: " + frame.serverId());
            return;
        }
        if (selfServerId.equals(frame.serverId())) {
            sendReject(ctx, "자기 자신으로의 연결은 허용되지 않습니다");
            return;
        }
        Channel existing = registry.register(frame.serverId(), ctx.channel());
        if (existing != null && existing != ctx.channel()) {
            existing.close();
        }
        ByteBuf welcome = Protocol.encodeWelcome(ctx.alloc(), PROTOCOL_VERSION, environment, selfServerId);
        ctx.writeAndFlush(welcome);
        ctx.pipeline().replace(this, "realtime-server", new RealtimeServerHandler(selfServerId, frame.serverId(), registry, messageHandler));
        if (callback != null) {
            callback.onAccepted(frame.serverId(), ctx.channel());
        }
    }

    private void handleWelcome(ChannelHandlerContext ctx, ByteBuf buffer) {
        if (serverSide) {
            buffer.release();
            LOGGER.warn("서버가 예상치 못한 WELCOME 프레임을 수신하여 연결을 종료합니다");
            ctx.close();
            return;
        }
        Protocol.WelcomeFrame frame = Protocol.decodeWelcome(buffer, MAX_FRAME_LENGTH);
        buffer.release();
        if (frame.protocolVersion() != PROTOCOL_VERSION) {
            LOGGER.warn("프로토콜 버전 불일치로 연결을 종료합니다 (수신 {} vs 기대 {})", frame.protocolVersion(), PROTOCOL_VERSION);
            ctx.close();
            return;
        }
        if (!environment.equals(frame.environment())) {
            LOGGER.warn("환경 불일치로 연결을 종료합니다 (수신 {} vs 기대 {})", frame.environment(), environment);
            ctx.close();
            return;
        }
        if (!settings.isAllowedPeer(frame.serverId())) {
            LOGGER.warn("허용되지 않은 서버 {}에서 연결 시도", frame.serverId());
            ctx.close();
            return;
        }
        ctx.pipeline().replace(this, "realtime-client", new RealtimeClientHandler(selfServerId, frame.serverId(), messageHandler));
        if (callback != null) {
            callback.onAccepted(frame.serverId(), ctx.channel());
        }
    }

    private void handleReject(ChannelHandlerContext ctx, ByteBuf buffer) {
        Protocol.RejectFrame frame = Protocol.decodeReject(buffer, MAX_FRAME_LENGTH);
        buffer.release();
        LOGGER.warn("핸드셰이크 거부: {}", frame.reason());
        if (callback != null) {
            callback.onRejected(frame.reason());
        }
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.warn("핸드셰이크 처리 중 오류", cause);
        ctx.close();
    }

    private void sendReject(ChannelHandlerContext ctx, String reason) {
        ByteBuf reject = Protocol.encodeReject(ctx.alloc(), reason);
        ctx.writeAndFlush(reject);
        if (callback != null) {
            callback.onRejected(reason);
        }
        ctx.close();
    }

    interface HandshakeCallback {
        void onAccepted(String remoteServerId, Channel channel);

        default void onRejected(String reason) {
        }
    }

    static final class Protocol {
        private Protocol() {
        }

        static ByteBuf encodeHello(io.netty.buffer.ByteBufAllocator alloc,
                                   int protocolVersion,
                                   String environment,
                                   String serverId,
                                   String token) {
            byte[] envBytes = environment.getBytes(StandardCharsets.UTF_8);
            byte[] idBytes = serverId.getBytes(StandardCharsets.UTF_8);
            byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
            ByteBuf buffer = alloc.buffer(1 + 2 + envBytes.length + 2 + idBytes.length + 2 + tokenBytes.length + 4);
            buffer.writeByte(TYPE_HELLO);
            buffer.writeInt(protocolVersion);
            writeSizedBytes(buffer, envBytes);
            writeSizedBytes(buffer, idBytes);
            writeSizedBytes(buffer, tokenBytes);
            return buffer;
        }

        static HelloFrame decodeHello(ByteBuf buffer, int maxLength) {
            int protocolVersion = buffer.readInt();
            String env = readSizedString(buffer, maxLength);
            String serverId = readSizedString(buffer, maxLength);
            String token = readSizedString(buffer, maxLength);
            return new HelloFrame(protocolVersion, env, serverId, token);
        }

        static ByteBuf encodeWelcome(io.netty.buffer.ByteBufAllocator alloc,
                                     int protocolVersion,
                                     String environment,
                                     String serverId) {
            byte[] envBytes = environment.getBytes(StandardCharsets.UTF_8);
            byte[] idBytes = serverId.getBytes(StandardCharsets.UTF_8);
            ByteBuf buffer = alloc.buffer(1 + 4 + 2 + envBytes.length + 2 + idBytes.length);
            buffer.writeByte(TYPE_WELCOME);
            buffer.writeInt(protocolVersion);
            writeSizedBytes(buffer, envBytes);
            writeSizedBytes(buffer, idBytes);
            return buffer;
        }

        static WelcomeFrame decodeWelcome(ByteBuf buffer, int maxLength) {
            int protocolVersion = buffer.readInt();
            String env = readSizedString(buffer, maxLength);
            String serverId = readSizedString(buffer, maxLength);
            return new WelcomeFrame(protocolVersion, env, serverId);
        }

        static ByteBuf encodeReject(io.netty.buffer.ByteBufAllocator alloc, String reason) {
            byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
            ByteBuf buffer = alloc.buffer(1 + 2 + reasonBytes.length);
            buffer.writeByte(TYPE_REJECT);
            writeSizedBytes(buffer, reasonBytes);
            return buffer;
        }

        static RejectFrame decodeReject(ByteBuf buffer, int maxLength) {
            String reason = readSizedString(buffer, maxLength);
            return new RejectFrame(reason);
        }

        static ByteBuf encodeData(io.netty.buffer.ByteBufAllocator alloc, String targetServerId, String sourceServerId, byte[] payload) {
            byte[] targetBytes = targetServerId.getBytes(StandardCharsets.UTF_8);
            byte[] sourceBytes = sourceServerId.getBytes(StandardCharsets.UTF_8);
            ByteBuf buffer = alloc.buffer(1 + 2 + targetBytes.length + 2 + sourceBytes.length + payload.length);
            buffer.writeByte(TYPE_DATA);
            writeSizedBytes(buffer, targetBytes);
            writeSizedBytes(buffer, sourceBytes);
            buffer.writeBytes(payload);
            return buffer;
        }

        static DataFrame decodeData(ByteBuf buffer, int maxLength) {
            String target = readSizedString(buffer, maxLength);
            String source = readSizedString(buffer, maxLength);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new DataFrame(target, source, data);
        }

        private static String readSizedString(ByteBuf buffer, int maxLength) {
            int length = buffer.readUnsignedShort();
            if (length < 0 || length > maxLength) {
                throw new IllegalStateException("Invalid field length: " + length);
            }
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private static void writeSizedBytes(ByteBuf buffer, byte[] data) {
            if (data.length > 65535) {
                throw new IllegalArgumentException("Field length exceeds 65535");
            }
            buffer.writeShort(data.length);
            buffer.writeBytes(data);
        }

        record HelloFrame(int protocolVersion, String environment, String serverId, String token) {
        }

        record WelcomeFrame(int protocolVersion, String environment, String serverId) {
        }

        record RejectFrame(String reason) {
        }

        record DataFrame(String targetServerId, String sourceServerId, byte[] payload) {
        }
    }
}
