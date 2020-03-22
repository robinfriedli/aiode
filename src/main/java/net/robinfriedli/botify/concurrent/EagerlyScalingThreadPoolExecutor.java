package net.robinfriedli.botify.concurrent;

import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

/**
 * The default implementation of the ThreadPoolExecutor only starts creating additional threads up to the maxPoolSize once
 * all core pool threads are busy AND the queue is full. The idea of this thread pool is to create new threads
 * immediately when the core threads are busy, yet only allow a fixed maxPoolSize (unlike {@link Executors#newCachedThreadPool()})
 * and queue all additional tasks when hitting the maxPoolSize.
 * To achieve this this pool uses an {@link OfferRejectingQueue} that extends an unbound {@link LinkedBlockingQueue} and returns
 * false for all {@link Queue#offer(Object)} attempts, resulting in the pool always creating a new thread, then, in case
 * the thread pool is full, the {@link DirectQueueOfferRejectionHandler} is called which then uses
 * {@link OfferRejectingQueue#offerEmphatically(Runnable)} to actually queue the task when no additional threads may be created,
 * if the offer still fails a {@link RejectedExecutionException} is thrown, although that does not happen unless the
 * capacity of the {@link OfferRejectingQueue} is changed or the size reaches {@link Integer#MAX_VALUE}.
 */
public class EagerlyScalingThreadPoolExecutor extends ThreadPoolExecutor {

    public EagerlyScalingThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit keepAliveUnit) {
        super(corePoolSize, maximumPoolSize,
            keepAliveTime, keepAliveUnit,
            new OfferRejectingQueue(),
            new LoggingThreadFactory(poolName),
            new DirectQueueOfferRejectionHandler());

        if (corePoolSize == 0 && keepAliveUnit.toMillis(keepAliveTime) < 5) {
            throw new IllegalArgumentException("Pool needs either a core thread pool or a keep alive time since idle threads are needed to offer tasks from the secondary queue.");
        }
    }

    @Override
    public void execute(Runnable command) {
        super.execute(() -> {
            try {
                command.run();
            } finally {
                ThreadContext.Current.clear();
            }
        });
    }

    private static class OfferRejectingQueue extends LinkedBlockingQueue<Runnable> {

        @Override
        public boolean offer(@NotNull Runnable runnable) {
            return false;
        }

        public boolean offerEmphatically(Runnable task) {
            return super.offer(task);
        }
    }

    private static class DirectQueueOfferRejectionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!((OfferRejectingQueue) executor.getQueue()).offerEmphatically(r)) {
                throw new RejectedExecutionException(String.format("Pool %s rejected execution of %s indicating that both pool and queue size are full.", executor.toString(), r.toString()));
            }
        }

    }

}
