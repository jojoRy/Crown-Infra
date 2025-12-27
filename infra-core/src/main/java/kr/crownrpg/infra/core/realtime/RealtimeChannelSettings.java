package kr.crownrpg.infra.core.realtime;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

/**
 * Netty 실시간 채널 정책을 외부 설정으로 전달하기 위한 옵션.
 */
public final class RealtimeChannelSettings {

    private final CopyOnWriteArraySet<String> allowedPeerIds;
    private final int outboundQueueCapacity;
    private final int maxReconnectAttempts;
    private final long initialReconnectDelayMillis;
    private final long maxReconnectDelayMillis;
    private final int dropWarnThreshold;

    public RealtimeChannelSettings(Set<String> allowedPeerIds,
                                   int outboundQueueCapacity,
                                   int maxReconnectAttempts,
                                   long initialReconnectDelayMillis,
                                   long maxReconnectDelayMillis,
                                   int dropWarnThreshold) {
        this.allowedPeerIds = new CopyOnWriteArraySet<>(Objects.requireNonNull(allowedPeerIds, "allowedPeerIds"));
        this.outboundQueueCapacity = Math.max(1, outboundQueueCapacity);
        this.maxReconnectAttempts = Math.max(1, maxReconnectAttempts);
        this.initialReconnectDelayMillis = Math.max(0L, initialReconnectDelayMillis);
        this.maxReconnectDelayMillis = Math.max(initialReconnectDelayMillis, maxReconnectDelayMillis);
        this.dropWarnThreshold = Math.max(1, dropWarnThreshold);
    }

    public static RealtimeChannelSettings defaults(Set<String> allowedPeerIds) {
        return new RealtimeChannelSettings(allowedPeerIds, 512, 10, TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(30), 10);
    }

    public Set<String> allowedPeerIdsSnapshot() {
        return Collections.unmodifiableSet(allowedPeerIds);
    }

    public void updateAllowedPeers(Set<String> newPeers) {
        Objects.requireNonNull(newPeers, "newPeers");
        allowedPeerIds.clear();
        allowedPeerIds.addAll(newPeers);
    }

    public boolean isAllowedPeer(String peerId) {
        return allowedPeerIds.contains(peerId);
    }

    public int outboundQueueCapacity() {
        return outboundQueueCapacity;
    }

    public int maxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public long initialReconnectDelayMillis() {
        return initialReconnectDelayMillis;
    }

    public long maxReconnectDelayMillis() {
        return maxReconnectDelayMillis;
    }

    public int dropWarnThreshold() {
        return dropWarnThreshold;
    }
}
