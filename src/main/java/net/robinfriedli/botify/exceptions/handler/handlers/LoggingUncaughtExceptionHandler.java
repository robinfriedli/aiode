package net.robinfriedli.botify.exceptions.handler.handlers;

import net.robinfriedli.botify.Botify;

public class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Botify.LOGGER.error("Uncaught exception in thread " + t, e);
    }
}
