package kr.crownrpg.infra.api.message;

import java.util.Map;

/**
 * Redis로 오갈 표준 메시지 Envelope (API 계약).
 *
 * payload는 infra-api에서 JSON 라이브러리를 의존하지 않기 위해
 * "문자열 JSON" 또는 "문자열"로 취급한다.
 *
 * payloadFormat:
 *  - "json": payload가 JSON 문자열임
 *  - "text": payload가 일반 문자열임
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
        if (environment == null || environment.isBlank()) throw new IllegalArgumentException("environment is blank");
        if (fromServerId == null || fromServerId.isBlank()) throw new IllegalArgumentException("fromServerId is blank");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type is blank");
        if (payload == null) payload = "";
        if (payloadFormat == null || payloadFormat.isBlank()) payloadFormat = "json";
        if (meta == null) meta = MessageMeta.create();
    }

    public static InfraMessage json(String environment, String fromServerId, String type, String payloadJson) {
        return new InfraMessage(environment, fromServerId, type, payloadJson, "json", MessageMeta.create());
    }

    public static InfraMessage text(String environment, String fromServerId, String type, String text) {
        return new InfraMessage(environment, fromServerId, type, text, "text", MessageMeta.create());
    }

    public InfraMessage withHeaders(Map<String, String> headers) {
        return new InfraMessage(environment, fromServerId, type, payload, payloadFormat, MessageMeta.withHeaders(headers));
    }
}
