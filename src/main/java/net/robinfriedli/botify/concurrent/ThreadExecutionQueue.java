package net.robinfriedli.botify.concurrent;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Nullable;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import net.robinfriedli.botify.exceptions.RateLimitException;

/**
 * Thread queue that allows a certain amount of threads to run concurrently based on the size parameter
 */
public class ThreadExecutionQueue {

    private static final Object DUMMY = new Object();
    @Nullable
    private final RateLimiterConfig rateLimiterConfig;
    @Nullable
    private final RateLimiter rateLimiter;
    // the timeout raised when the rate limit is hit
    @Nullable
    private final Duration violationTimeout;

    private final BlockingQueue<QueuedThread> queue;
    private final ConcurrentHashMap<QueuedThread, Object> currentPool;
    private final int size;

    private volatile boolean closed;
    private volatile long timeoutNanosTimeStamp;

    public ThreadExecutionQueue(int size) {
        this(size, true, null, 0, null, null);
    }

    public ThreadExecutionQueue(
        int size,
        boolean unboundedQueue,
        @Nullable String rateLimiterIdentifier,
        int limitForPeriod,
        @Nullable Duration period,
        @Nullable Duration violationTimeout
    ) {
        if (unboundedQueue) {
            queue = new LinkedBlockingQueue<>();
        } else {
            queue = new LinkedBlockingQueue<>(size);
        }

        if (rateLimiterIdentifier != null && limitForPeriod > 0 && period != null && violationTimeout != null) {
            this.rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(period)
                .timeoutDuration(Duration.ofSeconds(1))
                .build();

            RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
            this.rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterIdentifier);
            this.violationTimeout = violationTimeout;
        } else if (rateLimiterIdentifier != null || limitForPeriod > 0 || period != null || violationTimeout != null) {
            throw new IllegalArgumentException("Incomplete RateLimiter configuration");
        } else {
            this.rateLimiterConfig = null;
            this.rateLimiter = null;
            this.violationTimeout = null;
        }

        currentPool = new ConcurrentHashMap<>(size);
        this.size = size;
    }

    /**
     * @param thread the {@link QueuedThread} to queue
     * @return true if the currentPool has free space, false if the thread was queued instead
     * @throws RateLimitException if the configured rate limit is exceeded
     */
    public synchronized boolean add(QueuedThread thread) throws RateLimitException {
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

            if (thread.isPrivileged()) {
                currentPool.put(thread, DUMMY);
                thread.start();
                return true;
            } else {
                if (!queue.offer(thread)) {
                    if (violationTimeout != null) {
                        timeoutNanosTimeStamp = System.nanoTime() + violationTimeout.toNanos();
                    }
                    throw new RateLimitException(
                        false,
                        String.format(
                            "Execution queue of size %d is full. You can not submit commands until one is done.%s",
                            size,
                            violationTimeout != null
                                ? String.format(" Additionally, a %d second timeout has been raised. Task submissions during the timeout reset the timeout.", violationTimeout.toSeconds())
                                : ""
                        )
                    );
                }

                if (currentPool.size() < size) {
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
        currentPool.keySet().forEach(Thread::interrupt);
        currentPool.clear();
    }

    public void join() throws InterruptedException {
        if (!currentPool.isEmpty()) {
            QueuedThread[] queuedThreads;
            synchronized (this) {
                queuedThreads = currentPool.keySet().toArray(new QueuedThread[0]);
            }
            for (QueuedThread queuedThread : queuedThreads) {
                queuedThread.join();
            }
        }
    }

    /**
     * @return true if this queue does not contain any running or queued threads
     */
    public boolean isIdle() {
        return currentPool.isEmpty() && queue.isEmpty();
    }

    synchronized void freeSlot(QueuedThread thread) {
        Object removed = currentPool.remove(thread);
        if (!closed && removed != null) {
            runNext();
        }
    }

    private void runNext() {
        QueuedThread poll = queue.poll();
        if (poll != null) {
            currentPool.put(poll, DUMMY);
            poll.start();
        }
    }
}
