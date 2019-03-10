package net.robinfriedli.botify.util;

/**
 * Runnable that allows checked exceptions like a Callable
 */
public interface CheckedRunnable {

    void run() throws Exception;

}
