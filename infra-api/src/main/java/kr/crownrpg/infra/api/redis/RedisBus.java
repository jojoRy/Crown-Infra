package kr.crownrpg.infra.api.redis;

import kr.crownrpg.infra.api.message.InfraMessage;

/**
 * 서버 간 메시징을 위한 최소 계약.
 *
 * - publish: 채널로 메시지 발행
 * - subscribe: 채널 구독
 *
 * 주의:
 * - infra-api는 구현(lettuce 등)을 모른다.
 * - 재시도/ACK/큐잉은 v2 기능으로 미룸.
 */
public interface RedisBus {

    void publish(String channel, InfraMessage message);

    RedisSubscription subscribe(String channel, RedisMessageHandler handler);
}
