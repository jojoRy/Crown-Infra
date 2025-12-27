package kr.crownrpg.infra.core.database;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadFactories {

    private ThreadFactories() {}

    public static ExecutorService fixed(int threads, String prefix) {
        return Executors.newFixedThreadPool(threads, named(prefix));
    }

    public static ThreadFactory named(String prefix) {
        AtomicInteger idx = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r);
            t.setName(prefix + "-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }
}
