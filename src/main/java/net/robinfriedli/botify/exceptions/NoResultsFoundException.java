package net.robinfriedli.botify.exceptions;

public class NoResultsFoundException extends RuntimeException {

    public NoResultsFoundException() {
        super();
    }

    public NoResultsFoundException(String errorMessage) {
        super(errorMessage);
    }

    public NoResultsFoundException(Throwable cause) {
        super(cause);
    }

    public NoResultsFoundException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
    }

}
