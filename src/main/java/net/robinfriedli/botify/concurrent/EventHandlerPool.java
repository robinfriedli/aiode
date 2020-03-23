package net.robinfriedli.botify.concurrent;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.robinfriedli.botify.Botify;

public class EventHandlerPool {

    public static final ThreadPoolExecutor POOL = new EagerlyScalingThreadPoolExecutor("event-handler-pool", 3, 50, 5L, TimeUnit.MINUTES);

    static {
        Botify.SHUTDOWNABLES.add(delayMs -> {
            POOL.shutdown();
            ((EagerlyScalingThreadPoolExecutor.SecondaryQueueRejectionHandler) POOL.getRejectedExecutionHandler()).shutdown();
        });
    }

    private EventHandlerPool() {
    }

    public static void execute(Runnable command) {
        POOL.execute(command);
    }

}
