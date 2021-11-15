package net.robinfriedli.aiode.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.ShutdownableExecutorService;

/**
 * Thread pool commonly used to store playback and command history entries.
 */
public class HistoryPool {

    public static final ExecutorService POOL = Executors.newFixedThreadPool(3, new LoggingThreadFactory("history-pool"));

    static {
        Aiode.SHUTDOWNABLES.add(new ShutdownableExecutorService(POOL));
    }

    public static void execute(Runnable runnable) {
        POOL.execute(runnable);
    }

}
