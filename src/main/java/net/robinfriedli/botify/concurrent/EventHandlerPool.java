package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;

public class EventHandlerPool {

    public static final ExecutorService POOL = new ThreadPoolExecutor(5, 50,
        5L, TimeUnit.MINUTES,
        new SynchronousQueue<>(),
        new LoggingThreadFactory("event-handler-pool"));

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(POOL));
    }

    private EventHandlerPool() {
    }

}
