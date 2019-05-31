package net.robinfriedli.botify.concurrent;

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
