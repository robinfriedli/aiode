package net.robinfriedli.botify.concurrent;

public class ThreadContextClosingRunnableDelegate implements Runnable {

    private final Runnable delegate;

    public ThreadContextClosingRunnableDelegate(Runnable delegate) {
        this.delegate = delegate;
    }

    @Override
    public void run() {
        try {
            delegate.run();
        } finally {
            ThreadContext.Current.clear();
        }
    }
}
