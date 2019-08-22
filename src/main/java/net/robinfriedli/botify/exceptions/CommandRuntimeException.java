package net.robinfriedli.botify.exceptions;

import javax.annotation.Nonnull;

import net.robinfriedli.botify.exceptions.handlers.CommandExceptionHandler;

/**
 * Exception class to wrap checked exceptions thrown during command execution. Used by {@link CommandExceptionHandler}
 * to handle the cause checked exception directly.
 */
public class CommandRuntimeException extends RuntimeException {

    public CommandRuntimeException(@Nonnull Throwable cause) {
        super(cause);
    }

}
