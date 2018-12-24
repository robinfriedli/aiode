package net.robinfriedli.botify.exceptions;

/**
 * Type of command thrown when the cause is user error. This exception typically gets caught and its message sent to
 * Discord.
 */
public class InvalidCommandException extends RuntimeException {

    public InvalidCommandException() {
        super();
    }

    public InvalidCommandException(String errorMessage) {
        super(errorMessage);
    }

    public InvalidCommandException(Throwable cause) {
        super(cause);
    }

    public InvalidCommandException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
    }

}
