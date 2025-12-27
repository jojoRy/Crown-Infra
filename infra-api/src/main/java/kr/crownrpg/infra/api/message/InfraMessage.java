package kr.crownrpg.infra.api.message;

import kr.crownrpg.infra.api.Preconditions;

import java.util.Map;

/**
 * Immutable envelope for cross-node messaging.
 */
public record InfraMessage(
        String environment,
        String fromServerId,
        String type,
        String payload,
        String payloadFormat,
        MessageMeta meta
) {

    public InfraMessage {
        Preconditions.checkNotBlank(environment, "environment");
        Preconditions.checkNotBlank(fromServerId, "fromServerId");
        Preconditions.checkNotBlank(type, "type");

        String normalizedPayload = payload == null ? "" : payload;
        String normalizedFormat;
        if (payloadFormat == null || payloadFormat.trim().isEmpty()) {
            normalizedFormat = PayloadFormat.JSON.wireName();
        } else {
            normalizedFormat = payloadFormat.trim().toLowerCase();
        }

        this.payload = normalizedPayload;
        this.payloadFormat = normalizedFormat;
        this.meta = meta == null ? MessageMeta.create() : meta;
    }

    public static InfraMessage json(String environment, String fromServerId, String type, String payloadJson) {
        return new InfraMessage(environment, fromServerId, type, payloadJson, PayloadFormat.JSON.wireName(), MessageMeta.create());
    }

    public static InfraMessage text(String environment, String fromServerId, String type, String textPayload) {
        return new InfraMessage(environment, fromServerId, type, textPayload, PayloadFormat.TEXT.wireName(), MessageMeta.create());
    }

    public InfraMessage withHeaders(Map<String, String> headers) {
        return new InfraMessage(environment, fromServerId, type, payload, payloadFormat, meta.withHeaders(headers));
    }
}
