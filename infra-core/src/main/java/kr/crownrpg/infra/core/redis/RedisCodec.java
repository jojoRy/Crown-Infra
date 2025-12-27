package kr.crownrpg.infra.core.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.crownrpg.infra.api.message.InfraMessage;

/**
 * InfraMessage <-> JSON 변환만 담당.
 * (bus에서 try/catch 덜어내기 위해 분리)
 */
public final class RedisCodec {

    private final ObjectMapper mapper;

    public RedisCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(InfraMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode InfraMessage", e);
        }
    }

    public InfraMessage decode(String json) {
        try {
            return mapper.readValue(json, InfraMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode InfraMessage: " + safe(json), e);
        }
    }

    private static String safe(String s) {
        if (s == null) return "null";
        if (s.length() <= 200) return s;
        return s.substring(0, 200) + "...(truncated)";
    }
}
