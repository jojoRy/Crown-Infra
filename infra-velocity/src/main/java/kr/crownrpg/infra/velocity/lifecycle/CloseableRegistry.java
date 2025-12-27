package kr.crownrpg.infra.velocity.lifecycle;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CloseableRegistry {

    private static final String LOG_PREFIX = "[CrownInfra-Velocity] ";

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
                    logger.log(Level.WARNING, LOG_PREFIX + "자원 종료에 실패했습니다: " + c.getClass().getName(), t);
                }
            }
        }
    }
}
