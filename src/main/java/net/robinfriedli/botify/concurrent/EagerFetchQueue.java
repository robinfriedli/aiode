package net.robinfriedli.botify.concurrent;

import java.util.concurrent.TimeUnit;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.threadpool.ThreadPool;

/**
 * Thread pool commonly used to submit requests to fetch data ahead of time or in parallel. This pool utilizes
 * {@link ThreadPool} to enable creating creating additional threads before enqueueing tasks
 * while only allowing a maximum number of threads.
 */
public class EagerFetchQueue {

    public static final ForkTaskTreadPool FETCH_POOL = new ForkTaskTreadPool(
        ThreadPool.Builder.create()
            .setCoreSize(3)
            .setMaxSize(20)
            .setKeepAlive(1L, TimeUnit.MINUTES)
            .setThreadFactory(new LoggingThreadFactory("eager-fetch-pool")).build()
    );

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(FETCH_POOL));
    }

    public static void submitFetch(Runnable fetchTask) {
        FETCH_POOL.execute(fetchTask);
    }

}
