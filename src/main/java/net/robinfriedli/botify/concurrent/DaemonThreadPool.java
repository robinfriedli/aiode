package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Thread pool spawning daemon threads used for command monitoring. This pool uses a 0 capacity SynchronousQueue to ensure
 * that tasks are never queued but executed instantly.
 */
public class DaemonThreadPool {

    public static final ExecutorService POOL = new ThreadPoolExecutor(3, Integer.MAX_VALUE,
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ThreadFactory() {
            private final AtomicLong threadId = new AtomicLong(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("daemon-pool-thread-" + threadId.getAndIncrement());
                thread.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
                thread.setDaemon(true);
                return thread;
            }
        });

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(POOL));
    }

    public static void execute(Runnable r) {
        POOL.execute(r);
    }

    public static Future<?> submit(Runnable r) {
        return POOL.submit(r);
    }

}
