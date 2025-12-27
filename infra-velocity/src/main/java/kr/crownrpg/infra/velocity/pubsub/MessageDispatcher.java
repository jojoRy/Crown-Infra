package kr.crownrpg.infra.velocity.pubsub;

import kr.crownrpg.infra.api.Preconditions;
import kr.crownrpg.infra.api.message.InfraMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Thread-safe dispatcher that routes {@link InfraMessage} instances to registered handlers by type.
 */
public final class MessageDispatcher {

    private static final String LOG_PREFIX = "[CrownInfra-Velocity] ";

    private final Logger logger;
    private final Map<String, List<Consumer<InfraMessage>>> handlers = new ConcurrentHashMap<>();

    public MessageDispatcher(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(String type, Consumer<InfraMessage> handler) {
        Preconditions.checkNotBlank(type, "type");
        Preconditions.checkNotNull(handler, "handler");
        handlers.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void dispatch(InfraMessage message) {
        if (message == null) {
            return;
        }
        List<Consumer<InfraMessage>> consumers = handlers.get(message.type());
        if (consumers == null || consumers.isEmpty()) {
            return;
        }
        for (Consumer<InfraMessage> consumer : consumers) {
            try {
                consumer.accept(message);
            } catch (Exception e) {
                logger.error(LOG_PREFIX + "메시지 처리 중 오류가 발생했습니다: {}", message.type(), e);
            }
        }
    }
}

