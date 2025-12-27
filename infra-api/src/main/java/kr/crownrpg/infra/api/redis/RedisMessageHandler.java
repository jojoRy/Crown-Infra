package kr.crownrpg.infra.api.redis;

import kr.crownrpg.infra.api.message.InfraMessage;

@FunctionalInterface
public interface RedisMessageHandler {

    void onMessage(String channel, InfraMessage message);
}
