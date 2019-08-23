package net.robinfriedli.botify.exceptions.handlers;

import net.robinfriedli.botify.Botify;

public class LoggingExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Botify.LOGGER.error("Uncaught exception in thread " + t, e);
    }
}
