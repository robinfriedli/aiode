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

    public ThreadExecutionQueue(int size) {
        this.queue = new ConcurrentLinkedQueue<>();
        currentPool = new HashSet<>();
        this.size = size;
    }

    /**
     * @param thread the {@link QueuedThread} to queue
     * @return true if the currentPool has free space, false if the thread was queued instead
     */
    public boolean add(QueuedThread thread) {
        queue.add(thread);
        if (currentPool.size() < size) {
            runNext();
            return true;
        }

        return false;
    }

    public synchronized void freeSlot(QueuedThread thread) {
        currentPool.remove(thread);
        runNext();
    }

    private synchronized void runNext() {
        QueuedThread poll = queue.poll();
        if (poll != null) {
            currentPool.add(poll);
            poll.start();
        }
    }
}
