package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static kr.crownrpg.infra.core.realtime.HandshakeHandler.Protocol;
import static kr.crownrpg.infra.core.realtime.HandshakeHandler.TYPE_DATA;

class RealtimeServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private final String selfServerId;
    private final String remoteServerId;
    private final ChannelRegistry registry;
    private final RealtimeMessageHandler messageHandler;
    private final Logger logger = Logger.getLogger(RealtimeServerHandler.class.getName());

    RealtimeServerHandler(String selfServerId,
                          String remoteServerId,
                          ChannelRegistry registry,
                          RealtimeMessageHandler messageHandler) {
        this.selfServerId = Objects.requireNonNull(selfServerId, "selfServerId");
        this.remoteServerId = Objects.requireNonNull(remoteServerId, "remoteServerId");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (!ctx.channel().isActive() || registry.find(remoteServerId) != ctx.channel()) {
            logger.warning("Dropping message from unregistered or stale peer: " + remoteServerId);
            return;
        }
        byte type = msg.readByte();
        if (type != TYPE_DATA) {
            return;
        }
        Protocol.DataFrame frame = Protocol.decodeData(msg, MAX_FRAME_LENGTH);
        if (!remoteServerId.equals(frame.sourceServerId())) {
            logger.warning("Dropping spoofed source from peer " + remoteServerId + " claiming " + frame.sourceServerId());
            return;
        }
        if (selfServerId.equals(frame.targetServerId())) {
            messageHandler.onMessage(remoteServerId, frame.payload());
            return;
        }
        Channel targetChannel = registry.find(frame.targetServerId());
        if (targetChannel != null && targetChannel.isActive()) {
            ByteBuf forward = Protocol.encodeData(targetChannel.alloc(), frame.targetServerId(), remoteServerId, frame.payload());
            targetChannel.writeAndFlush(forward);
        } else {
            logger.warning("Dropping realtime forward to unavailable target: " + frame.targetServerId());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        registry.remove(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            registry.remove(ctx.channel());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        registry.remove(ctx.channel());
        logger.log(Level.WARNING, "Realtime server handler error", cause);
        ctx.close();
    }
}
