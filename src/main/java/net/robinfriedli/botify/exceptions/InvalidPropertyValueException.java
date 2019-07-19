package net.robinfriedli.botify.exceptions;

public class InvalidPropertyValueException extends UserException {

    public InvalidPropertyValueException() {
        super();
    }

    public InvalidPropertyValueException(String message) {
        super(message);
    }

    public InvalidPropertyValueException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidPropertyValueException(Throwable cause) {
        super(cause);
    }
}
