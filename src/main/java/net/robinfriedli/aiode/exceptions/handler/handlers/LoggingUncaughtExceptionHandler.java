package net.robinfriedli.aiode.exceptions.handler.handlers;

import net.robinfriedli.aiode.Aiode;

public class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Aiode.LOGGER.error("Uncaught exception in thread " + t, e);
    }
}
