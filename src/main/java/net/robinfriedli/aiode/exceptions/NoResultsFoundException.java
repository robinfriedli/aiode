package net.robinfriedli.aiode.exceptions;

public class NoResultsFoundException extends UserException {

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
