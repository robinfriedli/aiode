package net.robinfriedli.aiode.exceptions.handler.handlers;

import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.exceptions.handler.ExceptionHandler;
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
