package kr.crownrpg.infra.api.message;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Standard metadata for messages.
 */
public record MessageMeta(String messageId, long createdAtEpochMillis, Map<String, String> headers) {

    public MessageMeta {
        String id = messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId;
        long timestamp = createdAtEpochMillis <= 0 ? Instant.now().toEpochMilli() : createdAtEpochMillis;
        this.messageId = id;
        this.createdAtEpochMillis = timestamp;
        this.headers = toUnmodifiableHeaders(headers);
    }

    public static MessageMeta create() {
        return new MessageMeta(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), Collections.emptyMap());
    }

    public MessageMeta withHeaders(Map<String, String> newHeaders) {
        return new MessageMeta(messageId, createdAtEpochMillis, newHeaders);
    }

    private static Map<String, String> toUnmodifiableHeaders(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(Map.copyOf(source));
    }
}
