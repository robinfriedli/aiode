package net.robinfriedli.botify.exceptions;

/**
 * Exception denoting errors caused by an invalid command configuration or other unexpected issues when setting up a command,
 * e.g. errors when running the groovy script of an argument rule or when parsing the command fails due to an unexpected
 * exception.
 */
public class UnexpectedCommandSetupException extends RuntimeException {

    public UnexpectedCommandSetupException() {
        super();
    }

    public UnexpectedCommandSetupException(String message) {
        super(message);
    }

    public UnexpectedCommandSetupException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnexpectedCommandSetupException(Throwable cause) {
        super(cause);
    }
}
