package net.robinfriedli.botify.exceptions;

/**
 * Special exception class that propagates exceptions thrown during command execution that have already been handled
 */
public class CommandFailure extends RuntimeException {

    public CommandFailure(Throwable cause) {
        super(cause);
    }

}
