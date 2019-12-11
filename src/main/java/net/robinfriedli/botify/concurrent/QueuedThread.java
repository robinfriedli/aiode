package net.robinfriedli.botify.concurrent;

/**
 * Thread type that may be added to a {@link ThreadExecutionQueue} that frees up its slot after completion
 */
public class QueuedThread extends Thread {

    private final ThreadExecutionQueue queue;
    private boolean terminated;

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

    /**
     * Use separate terminated flag because a thread's interrupted status might be cleared
     */
    public void terminate() {
        interrupt();
        terminated = true;
    }

    public boolean isTerminated() {
        return terminated;
    }

    protected boolean isPrivileged() {
        return false;
    }

}
