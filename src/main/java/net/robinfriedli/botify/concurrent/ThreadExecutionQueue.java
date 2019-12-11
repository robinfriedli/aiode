package net.robinfriedli.botify.concurrent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread queue that allows a certain amount of threads to run concurrently based on the size parameter
 */
public class ThreadExecutionQueue {

    private final String name;
    private final AtomicInteger threadNumber;
    private final ConcurrentLinkedQueue<QueuedThread> queue;
    private final Set<QueuedThread> currentPool;
    private final int size;
    private volatile boolean closed;

    public ThreadExecutionQueue(String name, int size) {
        this.name = name;
        this.threadNumber = new AtomicInteger(1);
        this.queue = new ConcurrentLinkedQueue<>();
        currentPool = new HashSet<>(size);
        this.size = size;
    }

    /**
     * Overload for {@link #add(QueuedThread, boolean)} with <code>true</code> as default value for param generateName.
     */
    public boolean add(QueuedThread thread) {
        return add(thread, true);
    }

    /**
     * @param thread       the {@link QueuedThread} to queue
     * @param generateName whether to generate and set a name for the provided thread
     * @return true if the currentPool has free space, false if the thread was queued instead
     */
    public synchronized boolean add(QueuedThread thread, boolean generateName) {
        if (!closed) {
            if (generateName) {
                thread.setName(name + "-thread-" + threadNumber.getAndIncrement());
            } else {
                threadNumber.incrementAndGet();
            }
            if (thread.isPrivileged()) {
                currentPool.add(thread);
                thread.start();
                return true;
            } else {
                queue.add(thread);
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
        currentPool.forEach(QueuedThread::terminate);
        currentPool.clear();
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

    synchronized void freeSlot(QueuedThread thread) {
        boolean removed = currentPool.remove(thread);
        if (!closed && removed && !thread.isPrivileged()) {
            runNext();
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
