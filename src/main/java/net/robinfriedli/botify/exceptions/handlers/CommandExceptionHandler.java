package net.robinfriedli.botify.exceptions.handlers;

import org.slf4j.Logger;

import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class CommandExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;

    public CommandExceptionHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Command command = ThreadContext.Current.get(Command.class);
        if (command != null) {
            ExceptionUtils.handleCommandException(e, command, logger);
        } else {
            logger.error("Exception in command handler thread", e);
        }
    }

}
