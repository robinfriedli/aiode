package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;

/**
 * Thread pool commonly used to store playback and command history entries.
 */
public class HistoryPool {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, new LoggingThreadFactory("history-pool"));

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(POOL));
    }

    public static void execute(Runnable runnable) {
        POOL.execute(runnable);
    }

}
