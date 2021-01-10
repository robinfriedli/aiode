package net.robinfriedli.botify.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import net.robinfriedli.threadpool.ThreadPool;

/**
 * The implementation of the ThreadPoolExecutor only starts creating additional threads up to the maxPoolSize once
 * all core pool threads are busy AND the queue is full. Yet the idea of this thread pool is to create new threads
 * immediately when the core threads are busy, yet only allow a fixed maxPoolSize (unlike {@link Executors#newCachedThreadPool()})
 * and queue all additional tasks when hitting the maxPoolSize.
 * To do this we use a SynchronousQueue, see {@link Executors#newCachedThreadPool()}, that causes new threads to spawn
 * for each additional task up to the maxPoolSize and then implementing a {@link RejectedExecutionHandler} that
 * adds each additional task to a secondary queue that attempts to offer tasks to the main queue until there are
 * idle threads.
 *
 * @deprecated This {@link ThreadPoolExecutor} has been replaced with an independent threadpool implementation {@link ThreadPool} and its
 * wrapper {@link ForkTaskTreadPool}.
 */
@Deprecated
public class EagerlyScalingThreadPoolExecutor extends ThreadPoolExecutor {

    public EagerlyScalingThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit keepAliveUnit) {
        super(corePoolSize, maximumPoolSize,
            keepAliveTime, keepAliveUnit,
            new SynchronousQueue<>(),
            new LoggingThreadFactory(poolName),
            new SecondaryQueueRejectionHandler(poolName));

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

    public static class SecondaryQueueRejectionHandler implements RejectedExecutionHandler {

        private final AtomicLong workerId = new AtomicLong(1);
        private final AtomicReference<Thread> currentWorker = new AtomicReference<>();
        private final LinkedBlockingQueue<QueuedExecution> secondaryQueue = new LinkedBlockingQueue<>();
        private final Logger logger;
        private final Object lock = new Object();
        private final String mainPoolName;

        private volatile boolean shutdown;

        public SecondaryQueueRejectionHandler(String mainPoolName) {
            this.logger = LoggerFactory.getLogger(getClass());
            this.mainPoolName = mainPoolName;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (shutdown) {
                return;
            }

            secondaryQueue.add(new QueuedExecution(r, executor));

            synchronized (lock) {
                Thread currentWorker = this.currentWorker.getAcquire();
                if (currentWorker == null) {
                    Thread newWorkerThread = new Thread(() -> {
                        try {
                            QueuedExecution queuedExecution = secondaryQueue.poll();

                            while (queuedExecution != null) {
                                ThreadPoolExecutor poolExecutor = queuedExecution.getExecutor();

                                if (!poolExecutor.isShutdown()) {
                                    Runnable task = queuedExecution.getTask();

                                    BlockingQueue<Runnable> mainQueue = poolExecutor.getQueue();
                                    boolean offer = mainQueue.offer(task, 10, TimeUnit.MINUTES);
                                    int i = 1;

                                    while (!offer) {
                                        logger.warn(String.format("possible stale task or overloaded pool, failing to submit task from secondary queue after trying for %s minutes, target pool: %s %s", i * 10, mainPoolName, poolExecutor.toString()));
                                        i++;
                                        offer = mainQueue.offer(task, 10, TimeUnit.MINUTES);
                                    }
                                }

                                queuedExecution = secondaryQueue.poll(10, TimeUnit.SECONDS);
                            }

                            this.currentWorker.compareAndSet(Thread.currentThread(), null);
                        } catch (InterruptedException e) {
                            this.currentWorker.compareAndSet(Thread.currentThread(), null);
                        }
                    });

                    newWorkerThread.setName(mainPoolName + "-secondary-queue-handler-thread-" + workerId.getAndIncrement());
                    newWorkerThread.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
                    newWorkerThread.start();
                    this.currentWorker.setRelease(newWorkerThread);
                }
            }
        }

        public void shutdown() {
            shutdown = true;
            Thread thread = currentWorker.getAcquire();

            if (thread != null) {
                thread.interrupt();
            }
        }

        private static class QueuedExecution {

            private final Runnable task;
            private final ThreadPoolExecutor executor;

            private QueuedExecution(Runnable task, ThreadPoolExecutor executor) {
                this.task = task;
                this.executor = executor;
            }

            public Runnable getTask() {
                return task;
            }

            public ThreadPoolExecutor getExecutor() {
                return executor;
            }
        }

    }

}
