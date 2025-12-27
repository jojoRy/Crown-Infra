package kr.crownrpg.infra.core.realtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static kr.crownrpg.infra.core.realtime.HandshakeHandler.Protocol;
import static kr.crownrpg.infra.core.realtime.HandshakeHandler.TYPE_DATA;

class RealtimeClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private final String selfServerId;
    private final String remoteServerId;
    private final RealtimeMessageHandler messageHandler;
    private final Logger logger = LoggerFactory.getLogger(RealtimeClientHandler.class);

    RealtimeClientHandler(String selfServerId,
                          String remoteServerId,
                          RealtimeMessageHandler messageHandler) {
        this.selfServerId = Objects.requireNonNull(selfServerId, "selfServerId");
        this.remoteServerId = Objects.requireNonNull(remoteServerId, "remoteServerId");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte type = msg.readByte();
        if (type != TYPE_DATA) {
            return;
        }
        Protocol.DataFrame frame = Protocol.decodeData(msg, MAX_FRAME_LENGTH);
        if (!selfServerId.equals(frame.targetServerId())) {
            return;
        }
        if (!remoteServerId.equals(frame.sourceServerId())) {
            logger.warn("예상치 못한 피어 {} 로부터의 데이터를 드롭합니다", frame.sourceServerId());
            return;
        }
        messageHandler.onMessage(frame.sourceServerId(), frame.payload());
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
        logger.warn("실시간 클라이언트 처리 중 오류", cause);
        ctx.close();
    }
}
