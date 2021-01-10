package net.robinfriedli.botify.exceptions;

import javax.annotation.Nonnull;

import net.robinfriedli.botify.exceptions.handler.handlers.CommandUncaughtExceptionHandler;

/**
 * Exception class to wrap checked exceptions thrown during command execution. Used by {@link CommandUncaughtExceptionHandler}
 * to handle the cause checked exception directly.
 */
public class CommandRuntimeException extends RuntimeException {

    public CommandRuntimeException(@Nonnull Throwable cause) {
        super(cause);
    }

    /**
     * Throws the exception and wraps it into a CommandRuntimeException if it is not already a RuntimeException or Error.
     *
     * @param e the exception to throw and potentially wrap
     */
    public static void throwRuntimeException(Throwable e) {
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else if (e instanceof Error) {
            throw (Error) e;
        } else {
            throw new CommandRuntimeException(e);
        }
    }

}
