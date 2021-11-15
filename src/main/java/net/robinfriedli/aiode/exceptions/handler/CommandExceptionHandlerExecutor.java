package net.robinfriedli.aiode.exceptions.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.exceptions.ExceptionUtils;

public class CommandExceptionHandlerExecutor extends ExceptionHandlerExecutor {

    private final Command command;

    public CommandExceptionHandlerExecutor(Command command) {
        this.command = command;
    }

    @Override
    protected void handleUnhandled(Throwable e) {
        Logger logger = LoggerFactory.getLogger(getClass());
        try {
            ExceptionUtils.handleCommandException(e, command, logger);
        } catch (Exception e1) {
            logger.error("Exception while calling ExceptionUtils#handleCommandException, falling back to logging error", e1);
            logger.error("Exception in command handler thread", e);
        }
    }
}
