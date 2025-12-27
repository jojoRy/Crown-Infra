package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Handles initial handshake to validate environment and bind serverIds to channels.
 */
class HandshakeHandler extends ChannelInboundHandlerAdapter {

    static final byte TYPE_HANDSHAKE = 0x01;
    static final byte TYPE_DATA = 0x02;

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB safety

    private final boolean serverSide;
    private final String environment;
    private final String selfServerId;
    private final ChannelRegistry registry;
    private final RealtimeMessageHandler messageHandler;

    HandshakeHandler(boolean serverSide,
                     String environment,
                     String selfServerId,
                     ChannelRegistry registry,
                     RealtimeMessageHandler messageHandler) {
        this.serverSide = serverSide;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.selfServerId = Objects.requireNonNull(selfServerId, "selfServerId");
        if (serverSide && registry == null) {
            throw new IllegalArgumentException("registry is required on server side");
        }
        if (serverSide && messageHandler == null) {
            throw new IllegalArgumentException("messageHandler is required on server side");
        }
        this.registry = registry;
        this.messageHandler = messageHandler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!serverSide) {
            ByteBuf handshakeFrame = Protocol.encodeHandshake(ctx.alloc(), environment, selfServerId);
            ctx.writeAndFlush(handshakeFrame);
            ctx.pipeline().replace(this, "realtime-client", new RealtimeClientHandler(selfServerId, messageHandler));
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
        if (type != TYPE_HANDSHAKE) {
            buffer.release();
            ctx.close();
            return;
        }
        Protocol.HandshakeFrame frame = Protocol.decodeHandshake(buffer, MAX_FRAME_LENGTH);
        buffer.release();
        if (!environment.equals(frame.environment())) {
            ctx.close();
            return;
        }
        Channel registered = registry.register(frame.serverId(), ctx.channel());
        if (registered != null && registered != ctx.channel()) {
            registered.close();
        }
        ctx.pipeline().replace(this, "realtime-server", new RealtimeServerHandler(selfServerId, frame.serverId(), registry, messageHandler));
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
        ctx.close();
    }

    static final class Protocol {
        private Protocol() {
        }

        static ByteBuf encodeHandshake(io.netty.buffer.ByteBufAllocator alloc, String environment, String serverId) {
            byte[] envBytes = environment.getBytes(StandardCharsets.UTF_8);
            byte[] idBytes = serverId.getBytes(StandardCharsets.UTF_8);
            ByteBuf buffer = alloc.buffer(1 + 2 + envBytes.length + 2 + idBytes.length);
            buffer.writeByte(TYPE_HANDSHAKE);
            writeSizedBytes(buffer, envBytes);
            writeSizedBytes(buffer, idBytes);
            return buffer;
        }

        static HandshakeFrame decodeHandshake(ByteBuf buffer, int maxLength) {
            String env = readSizedString(buffer, maxLength);
            String serverId = readSizedString(buffer, maxLength);
            return new HandshakeFrame(env, serverId);
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

        record HandshakeFrame(String environment, String serverId) {
        }

        record DataFrame(String targetServerId, String sourceServerId, byte[] payload) {
        }
    }
}
