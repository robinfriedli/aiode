package net.robinfriedli.botify.concurrent;

import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread queue that allows a certain amount of threads to run concurrently based on the size parameter
 */
public class ThreadExecutionQueue {

    private final AtomicInteger threadNumber;
    private final BlockingQueue<Object> slotStack;
    private final ConcurrentLinkedQueue<QueuedTask> queue;
    private final String name;
    private final ThreadPoolExecutor threadPool;
    private final Vector<QueuedTask> currentPool;

    private volatile boolean closed;

    public ThreadExecutionQueue(String name, int size, ThreadPoolExecutor threadPool) {
        threadNumber = new AtomicInteger(1);
        slotStack = new LinkedBlockingQueue<>(size);
        queue = new ConcurrentLinkedQueue<>();
        this.name = name;
        this.threadPool = threadPool;
        currentPool = new Vector<>(size);
        for (int i = 0; i < size; i++) {
            slotStack.add(new Object());
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
                queue.add(task);
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

            // if millis = 0 wait indefinitely, as that is the expected behavior for a join but not how CountdownLatch#await is implemented
            long millisLeft = millis == 0 ? Long.MAX_VALUE : millis;
            for (QueuedTask queuedTask : queuedTasks) {
                if (millisLeft < 0) {
                    return;
                }

                long currentMillis = System.currentTimeMillis();
                queuedTask.getCountDownLatch().await(millisLeft, TimeUnit.MILLISECONDS);
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
