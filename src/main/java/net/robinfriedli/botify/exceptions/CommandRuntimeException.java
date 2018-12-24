package net.robinfriedli.botify.exceptions;

/**
 * Exception class to wrap checked exceptions thrown during command execution. Used by {@link CommandExceptionHandler}
 * to handle the cause checked exception directly.
 */
public class CommandRuntimeException extends RuntimeException {

    public CommandRuntimeException(Throwable cause) {
        super(cause);
    }

}
