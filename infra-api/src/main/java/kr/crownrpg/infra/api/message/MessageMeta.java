package kr.crownrpg.infra.api.message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 메시지 메타데이터.
 * - traceId: 추적용 (없으면 자동 생성)
 * - timestamp: 생성 시각 (없으면 자동 생성)
 * - headers: 확장 메타 (문자열 키/값)
 */
public record MessageMeta(
        String traceId,
        Instant timestamp,
        Map<String, String> headers
) {
    public MessageMeta {
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (headers == null) headers = Map.of();
    }

    public static MessageMeta create() {
        return new MessageMeta(null, null, null);
    }

    public static MessageMeta withHeaders(Map<String, String> headers) {
        return new MessageMeta(null, null, headers);
    }
}
