package kr.crownrpg.infra.paper.lifecycle;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CloseableRegistry {

    private final Deque<Closeable> stack = new ArrayDeque<>();

    public void register(Closeable closeable) {
        Objects.requireNonNull(closeable, "closeable");
        stack.push(closeable);
    }

    public void closeAllQuietly(Logger logger) {
        while (!stack.isEmpty()) {
            Closeable c = stack.pop();
            try {
                c.close();
            } catch (Throwable t) {
                if (logger != null) {
                    logger.log(Level.WARNING, "[CrownInfra] Failed to close resource: " + c.getClass().getName(), t);
                }
            }
        }
    }
}
