package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;

/**
 * Thread pool commonly used to submit requests to fetch data ahead of time or in parallel. This pool utilizes
 * {@link EagerlyScalingThreadPoolExecutor} to enable creating creating additional threads before enqueueing tasks
 * while only allowing a maximum number of threads.
 */
public class EagerFetchQueue {

    public static final ExecutorService FETCH_POOL = new EagerlyScalingThreadPoolExecutor("eager-fetch-pool", 5, 20, 1, TimeUnit.MINUTES);

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(FETCH_POOL));
    }

    public static void submitFetch(Runnable fetchTask) {
        FETCH_POOL.execute(fetchTask);
    }

}
