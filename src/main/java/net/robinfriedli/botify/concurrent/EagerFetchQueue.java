package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EagerFetchQueue {

    public static final ExecutorService FETCH_POOL = Executors.newFixedThreadPool(5, new LoggingThreadFactory("eager-fetch-pool"));

    public static void submitFetch(Runnable fetchTask) {
        FETCH_POOL.execute(fetchTask);
    }

}
