package kr.crownrpg.infra.core.realtime;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Velocity 프록시에서 실시간 페이로드를 중계하기 위해 동작하는 Netty 서버 엔드포인트.
 * <p>
 * 채널 인증과 프레이밍, 유휴 감지를 처리하며 {@link RealtimeMessageHandler}로 메시지를 넘겨준다.
 */
public final class NettyServer {

    private final String bindHost;
    private final int port;
    private final String environment;
    private final String serverId;
    private final String token;
    private final RealtimeChannelSettings settings;
    private final ChannelRegistry registry;
    private final RealtimeMessageHandler messageHandler;
    private final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private final AtomicBoolean started = new AtomicBoolean(false);
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServer(String bindHost,
                       int port,
                       String environment,
                       String serverId,
                       String token,
                       RealtimeChannelSettings settings,
                       ChannelRegistry registry,
                       RealtimeMessageHandler messageHandler) {
        this.bindHost = Objects.requireNonNull(bindHost, "bindHost");
        this.port = port;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.token = Objects.requireNonNull(token, "token");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    }

    /**
     * 실시간 서버를 비동기로 부트스트랩한다.
     * 이미 시작된 경우 중복 실행을 막고, 이벤트 루프와 파이프라인 구성을 담당한다.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(0, 0, 120, TimeUnit.SECONDS))
                                    .addLast(new LengthFieldBasedFrameDecoder(1_048_576, 0, 4, 0, 4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new HandshakeHandler(true, environment, serverId, token, settings, registry, messageHandler, null));
                        }
                    });
            ChannelFuture future = bootstrap.bind(new InetSocketAddress(bindHost, port)).sync();
            serverChannel = future.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting Netty server", e);
        } catch (Exception e) {
            logger.error("Netty 실시간 서버 시작 실패", e);
            stop();
            throw new IllegalStateException("Failed to start Netty realtime server", e);
        }
    }

    public boolean isStarted() {
        return started.get() && serverChannel != null && serverChannel.isActive();
    }

    /**
     * 바인딩된 서버와 모든 연결을 안전하게 종료한다.
     * 레지스트리의 채널을 먼저 닫은 뒤 이벤트 루프를 순서대로 해제한다.
     */
    public void stop() {
        if (!started.get()) {
            return;
        }
        registry.closeAll();
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        started.set(false);
    }
}
