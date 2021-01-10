package net.robinfriedli.botify.concurrent;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.handler.ExceptionHandlerExecutor;

/**
 * Thread type that may be added to a {@link ThreadExecutionQueue} that frees up its slot after completion
 */
public class QueuedTask implements Runnable {

    private final Runnable task;
    private final ThreadExecutionQueue queue;
    private volatile boolean complete;
    private volatile boolean terminated;
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
        } catch (Throwable e) {
            ExceptionHandlerExecutor exceptionHandlerExecutor = createExceptionHandlerExecutor();

            try {
                exceptionHandlerExecutor.handleException(e);
            } catch (Throwable propagate) {
                CommandRuntimeException.throwRuntimeException(propagate);
            }
        } finally {
            complete = true;

            synchronized (this) {
                notifyAll();
            }

            ThreadContext.Current.clear();
            queue.removeFromPool(this);

            if (oldName != null) {
                thread.setName(oldName);
            }
        }
    }

    public void await() throws InterruptedException {
        await(0);
    }

    public void await(long millis) throws InterruptedException {
        // no locking required if task is already complete - fast return
        if (isDone()) {
            return;
        }

        synchronized (this) {
            long millisToWait = millis;
            while (!(isDone() || (millis > 0 && millisToWait <= 0))) {
                // recheck condition after acquiring lock in case of a race condition
                // wait in a loop to guard against spurious wakeups, keep waiting until command is done or until the
                // specified amount of milliseconds has passed, if amount to wait is greater than 0 (0 means indefinite wait)
                long currentTime = System.currentTimeMillis();
                wait(millisToWait);
                millisToWait -= (System.currentTimeMillis() - currentTime);
            }
        }
    }

    public boolean isComplete() {
        return complete;
    }

    /**
     * Use separate terminated flag because a thread's interrupted status might be cleared
     */
    public void terminate() {
        terminated = true;
        if (thread != null) {
            // if the thread was created just after this check had failed the terminated flag is already set, so the
            // task will return either way
            thread.interrupt();
        }
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

    protected ExceptionHandlerExecutor createExceptionHandlerExecutor() {
        return ExceptionHandlerExecutor.PropagatingExecutor.INSTANCE;
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
