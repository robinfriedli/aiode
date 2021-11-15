package net.robinfriedli.aiode.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.ShutdownableExecutorService;
import net.robinfriedli.threadpool.ThreadPool;

public class EventHandlerPool {

    public static final ExecutorService POOL = ThreadPool.Builder.create()
        .setCoreSize(3)
        .setMaxSize(50)
        .setKeepAlive(5L, TimeUnit.MINUTES)
        .setThreadFactory(new LoggingThreadFactory("event-handler-pool")).build();

    static {
        Aiode.SHUTDOWNABLES.add(new ShutdownableExecutorService(POOL));
    }

    private EventHandlerPool() {
    }

    public static void execute(Runnable command) {
        POOL.execute(new ThreadContextClosingRunnableDelegate(command));
    }

}
