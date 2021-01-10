package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import net.robinfriedli.botify.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import org.jetbrains.annotations.NotNull;

public class LoggingThreadFactory implements ThreadFactory {

    private final String poolName;
    private final AtomicLong threadNumber = new AtomicLong(1);

    public LoggingThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(poolName + "-thread-" + threadNumber.getAndIncrement());
        thread.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
        return thread;
    }
}
