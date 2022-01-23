package net.robinfriedli.aiode.concurrent;

import java.time.Duration;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import net.robinfriedli.aiode.exceptions.RateLimitException;
import net.robinfriedli.aiode.function.RateLimitInvoker;

/**
 * Thread queue that allows a certain amount of threads to run concurrently based on the size parameter
 */
public class ThreadExecutionQueue {

    private final AtomicInteger threadNumber;
    private final BlockingQueue<Object> slotStack;
    private final BlockingQueue<QueuedTask> queue;
    private final int queueSize;
    private final String name;
    private final ThreadPoolExecutor threadPool;
    private final Vector<QueuedTask> currentPool;

    @Nullable
    private final RateLimiterConfig rateLimiterConfig;
    @Nullable
    private final RateLimiter rateLimiter;
    // the timeout raised when the rate limit is hit
    @Nullable
    private final Duration violationTimeout;

    private volatile boolean closed;
    private volatile long timeoutNanosTimeStamp;

    public ThreadExecutionQueue(String name, int concurrentSize, ThreadPoolExecutor threadPool) {
        this(name, concurrentSize, 0, threadPool, null, 0, null, null);
    }

    public ThreadExecutionQueue(
        String name,
        int concurrentSize,
        int queueSize,
        ThreadPoolExecutor threadPool,
        @Nullable String rateLimiterIdentifier,
        int limitForPeriod,
        @Nullable Duration period,
        @Nullable Duration violationTimeout
    ) {
        threadNumber = new AtomicInteger(1);
        slotStack = new LinkedBlockingQueue<>(concurrentSize);
        this.name = name;
        this.threadPool = threadPool;
        currentPool = new Vector<>(concurrentSize);
        for (int i = 0; i < concurrentSize; i++) {
            slotStack.add(new Object());
        }

        this.queueSize = queueSize;
        if (queueSize == 0) {
            queue = new LinkedBlockingQueue<>();
        } else {
            queue = new LinkedBlockingQueue<>(queueSize);
        }

        if (rateLimiterIdentifier != null && limitForPeriod > 0 && period != null && violationTimeout != null) {
            this.rateLimiterConfig = RateLimiterConfig
                .custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(period)
                .timeoutDuration(Duration.ofSeconds(1))
                .build();

            this.rateLimiter = RateLimitInvoker.RATE_LIMITER_REGISTRY.rateLimiter(rateLimiterIdentifier, this.rateLimiterConfig);
            this.violationTimeout = violationTimeout;
        } else if (rateLimiterIdentifier != null || limitForPeriod > 0 || period != null || violationTimeout != null) {
            throw new IllegalArgumentException("Incomplete RateLimiter configuration");
        } else {
            this.rateLimiterConfig = null;
            this.rateLimiter = null;
            this.violationTimeout = null;
        }
    }

    /**
     * Overload for {@link #add(QueuedTask, boolean)} with <code>true</code> as default value for param generateName.
     */
    public boolean add(QueuedTask thread) {
        return add(thread, true);
    }

    /**
     * @param task         the {@link QueuedTask} to queue
     * @param generateName whether to generate and set a name for the provided thread
     * @return true if the currentPool has free space, false if the thread was queued instead
     */
    public synchronized boolean add(QueuedTask task, boolean generateName) {
        if (!closed) {
            if (timeoutNanosTimeStamp > 0) {
                long currentNanoTime = System.nanoTime();
                if (currentNanoTime < timeoutNanosTimeStamp) {
                    // violationTimeout is not null if RateLimiter is not null
                    //noinspection ConstantConditions
                    timeoutNanosTimeStamp = System.nanoTime() + violationTimeout.toNanos();
                    throw new RateLimitException(true);
                } else {
                    timeoutNanosTimeStamp = 0;
                }
            }

            if (rateLimiter != null && !rateLimiter.acquirePermission()) {
                // violationTimeout is not null if RateLimiter is not null
                //noinspection ConstantConditions
                timeoutNanosTimeStamp = System.nanoTime() + violationTimeout.toNanos();
                // config is not null if RateLimiter is not null
                //noinspection ConstantConditions
                throw new RateLimitException(
                    false,
                    String.format(
                        "Hit rate limit for submitting tasks of %d per %d seconds. You may not enter any commands for %d seconds. " +
                            "For each attempt to submit additional tasks during the timeout, the timeout is reset.",
                        rateLimiterConfig.getLimitForPeriod(),
                        rateLimiterConfig.getLimitRefreshPeriod().getSeconds(),
                        violationTimeout.toSeconds()
                    )
                );
            }

            if (generateName) {
                task.setName(name + "-thread-" + threadNumber.getAndIncrement());
            } else {
                threadNumber.incrementAndGet();
            }

            if (task.isPrivileged()) {
                currentPool.add(task);
                threadPool.execute(task);
                return true;
            } else {
                if (!queue.offer(task)) {
                    if (violationTimeout != null) {
                        timeoutNanosTimeStamp = System.nanoTime() + violationTimeout.toNanos();
                    }
                    throw new RateLimitException(
                        false,
                        String.format(
                            "Execution queue of size %d is full. You can not submit commands until one is done.%s",
                            queueSize,
                            violationTimeout != null
                                ? String.format(" Additionally, a %d second timeout has been raised. Task submissions during the timeout reset the timeout.", violationTimeout.toSeconds())
                                : ""
                        )
                    );
                }

                if (!slotStack.isEmpty()) {
                    runNext();
                    return true;
                }
            }
            return false;
        } else {
            throw new IllegalStateException("This " + getClass().getSimpleName() + " has been closed");
        }
    }

    public void close() {
        closed = true;
    }

    /**
     * Sends an interrupt signal to current threads and clears the queue
     */
    public synchronized void abortAll() {
        queue.clear();
        currentPool.forEach(QueuedTask::terminate);
        currentPool.clear();
    }

    /**
     * Clears all queued tasks submitted to this queue
     */
    public void cancelEnqueued() {
        queue.clear();
    }

    /**
     * Awaits all submitted tasks running at the moment of calling this method to finish. This waits for the countdown
     * latch of all tasks currently in the pool. Enqueued tasks that are started after this method is called are not
     * considered and it is generally expected that the caller has made sure that no additional tasks are submitted to
     * this queue while this method runs.
     */
    public void join(long millis) throws InterruptedException {
        if (!currentPool.isEmpty()) {
            QueuedTask[] queuedTasks = currentPool.toArray(new QueuedTask[0]);

            long millisLeft = millis;
            for (QueuedTask queuedTask : queuedTasks) {
                if (millisLeft < 0) {
                    return;
                }

                long currentMillis = System.currentTimeMillis();
                queuedTask.await(millisLeft);
                millisLeft -= (System.currentTimeMillis() - currentMillis);
            }
        }
    }

    /**
     * @return true if this queue does not contain any running or queued threads
     */
    public boolean isIdle() {
        return currentPool.isEmpty() && queue.isEmpty();
    }

    Object takeSlot() throws InterruptedException {
        return slotStack.take();
    }

    void removeFromPool(QueuedTask queuedTask) {
        currentPool.remove(queuedTask);
    }

    synchronized void freeSlot(Object slot) {
        slotStack.add(slot);
        if (!closed) {
            runNext();
        }
    }

    private void runNext() {
        QueuedTask poll = queue.poll();
        if (poll != null) {
            currentPool.add(poll);
            threadPool.execute(poll);
        }
    }
}
