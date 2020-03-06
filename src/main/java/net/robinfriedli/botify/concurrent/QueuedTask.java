package net.robinfriedli.botify.concurrent;

import java.util.concurrent.CountDownLatch;

/**
 * Thread type that may be added to a {@link ThreadExecutionQueue} that frees up its slot after completion
 */
public class QueuedTask implements Runnable {

    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final Runnable task;
    private final ThreadExecutionQueue queue;
    private boolean complete;
    private boolean terminated;
    private String name;
    private Thread thread;

    public QueuedTask(ThreadExecutionQueue queue, Runnable task) {
        this.task = task;
        this.queue = queue;
    }

    @Override
    public void run() {
        if (terminated) {
            return;
        }

        thread = Thread.currentThread();
        String oldName = null;
        if (name != null) {
            oldName = thread.getName();
            thread.setName(name);
        }

        try {
            if (isPrivileged()) {
                task.run();
            } else {
                runWithSlot();
            }
        } finally {
            complete = true;
            countDownLatch.countDown();
            ThreadContext.Current.clear();
            queue.removeFromPool(this);

            if (oldName != null) {
                thread.setName(oldName);
            }
        }
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    public boolean isComplete() {
        return complete;
    }

    /**
     * Use separate terminated flag because a thread's interrupted status might be cleared
     */
    public void terminate() {
        if (thread != null) {
            thread.interrupt();
        }
        terminated = true;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public boolean isDone() {
        return isComplete() || isTerminated();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Thread getThread() {
        return thread;
    }

    public void join(long millis) throws InterruptedException {
        if (thread != null) {
            thread.join(millis);
        }
    }

    protected boolean isPrivileged() {
        return false;
    }

    private void runWithSlot() {
        Object slot;
        try {
            slot = queue.takeSlot();
        } catch (InterruptedException e) {
            return;
        }

        try {
            task.run();
        } finally {
            queue.freeSlot(slot);
        }
    }
}
