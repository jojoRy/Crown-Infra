package kr.crownrpg.infra.paper.pubsub;

import kr.crownrpg.infra.api.Preconditions;
import kr.crownrpg.infra.api.message.InfraMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe dispatcher that routes {@link InfraMessage} instances to registered handlers by type.
 */
public final class MessageDispatcher {

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
                logger.log(Level.SEVERE, "Error while handling message type " + message.type(), e);
            }
        }
    }
}

