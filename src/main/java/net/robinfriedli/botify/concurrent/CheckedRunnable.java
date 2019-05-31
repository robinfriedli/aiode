package net.robinfriedli.botify.concurrent;

/**
 * Runnable that allows checked exceptions like a Callable
 */
public interface CheckedRunnable {

    void run() throws Exception;

}
