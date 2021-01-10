package net.robinfriedli.botify.exceptions.handler.handlers;

import org.slf4j.Logger;

import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class CommandUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;

    public CommandUncaughtExceptionHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Command command = ThreadContext.Current.get(Command.class);
        if (command != null) {
            try {
                ExceptionUtils.handleCommandException(e, command, logger);
                return;
            } catch (Exception e1) {
                logger.error("Exception while calling ExceptionUtils#handleCommandException, falling back to logging error", e1);
            }
        }
        logger.error("Exception in command handler thread", e);
    }

}
