package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;

public class EagerFetchQueue {

    public static final ExecutorService FETCH_POOL = Executors.newFixedThreadPool(5, new LoggingThreadFactory("eager-fetch-pool"));

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(FETCH_POOL));
    }

    public static void submitFetch(Runnable fetchTask) {
        FETCH_POOL.execute(fetchTask);
    }

}
