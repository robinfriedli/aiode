package net.robinfriedli.botify.exceptions.handler.handlers;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.handler.ExceptionHandler;
import org.springframework.stereotype.Component;

@Component
public class CommandRuntimeExceptionHandler implements ExceptionHandler<CommandRuntimeException> {

    @Override
    public Class<CommandRuntimeException> getType() {
        return CommandRuntimeException.class;
    }

    @Override
    public Result handleException(Throwable uncaughtException, CommandRuntimeException exceptionToHandle) {
        return Result.SKIP_TO_CAUSE;
    }
}
