package net.robinfriedli.botify.concurrent;

/**
 * Thread type that may be added to a {@link ThreadExecutionQueue} that frees up its slot after completion
 */
public class QueuedThread extends Thread {

    private final ThreadExecutionQueue queue;

    public QueuedThread(ThreadExecutionQueue queue, Runnable runnable) {
        super(runnable);
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            super.run();
        } finally {
            queue.freeSlot(this);
        }
    }

}
