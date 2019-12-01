package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import org.jetbrains.annotations.NotNull;

public class LoggingThreadFactory implements ThreadFactory {

    private final String poolName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public LoggingThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(poolName + "-thread-" + threadNumber.getAndIncrement());
        thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
        return thread;
    }
}
