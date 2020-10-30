package net.robinfriedli.botify.function;

import net.robinfriedli.botify.Botify;

@FunctionalInterface
public interface LoggingRunnable extends CheckedRunnable {

    @Override
    default void run() {
        try {
            doRun();
        } catch (Exception e) {
            Botify.LOGGER.error("Uncaught exception in thread " + Thread.currentThread(), e);
        }
    }
}
