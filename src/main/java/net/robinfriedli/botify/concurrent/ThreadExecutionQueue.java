package net.robinfriedli.botify.concurrent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread queue that allows a certain amount of threads to run concurrently based on the size parameter
 */
public class ThreadExecutionQueue {

    private final ConcurrentLinkedQueue<QueuedThread> queue;
    private final Set<QueuedThread> currentPool;
    private final int size;
    private final Object synchroniseLock;
    private boolean closed;

    public ThreadExecutionQueue(int size) {
        this.queue = new ConcurrentLinkedQueue<>();
        currentPool = new HashSet<>();
        this.size = size;
        this.synchroniseLock = new Object();
    }

    /**
     * @param thread the {@link QueuedThread} to queue
     * @return true if the currentPool has free space, false if the thread was queued instead
     */
    public boolean add(QueuedThread thread) {
        synchronized (synchroniseLock) {
            if (!closed) {
                queue.add(thread);
                if (currentPool.size() < size) {
                    runNext();
                    return true;
                }

                return false;
            } else {
                throw new IllegalStateException("This " + getClass().getSimpleName() + " has been closed");
            }
        }
    }

    public void close() {
        closed = true;
    }

    /**
     * Sends an interrupt signal to current threads and clears the queue
     */
    public void abortAll() {
        synchronized (synchroniseLock) {
            queue.clear();
            currentPool.forEach(Thread::interrupt);
            currentPool.clear();
        }
    }

    public void join() throws InterruptedException {
        if (!currentPool.isEmpty()) {
            QueuedThread[] queuedThreads = currentPool.toArray(new QueuedThread[0]);
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

    void freeSlot(QueuedThread thread) {
        synchronized (synchroniseLock) {
            boolean removed = currentPool.remove(thread);
            if (!closed && removed) {
                runNext();
            }
        }
    }

    private void runNext() {
        QueuedThread poll = queue.poll();
        if (poll != null) {
            currentPool.add(poll);
            poll.start();
        }
    }
}
