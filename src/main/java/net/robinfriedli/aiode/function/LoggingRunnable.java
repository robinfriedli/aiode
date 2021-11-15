package net.robinfriedli.aiode.function;

import net.robinfriedli.aiode.Aiode;

@FunctionalInterface
public interface LoggingRunnable extends CheckedRunnable {

    @Override
    default void run() {
        try {
            doRun();
        } catch (Exception e) {
            Aiode.LOGGER.error("Uncaught exception in thread " + Thread.currentThread(), e);
        }
    }
}
