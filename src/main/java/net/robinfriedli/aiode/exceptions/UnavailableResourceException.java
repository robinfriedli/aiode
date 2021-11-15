package net.robinfriedli.aiode.exceptions;

/**
 * Exception thrown to signal an item could not be loaded. E.g. if the user attempted to play a private, copyright
 * claimed or otherwise unavailable YouTube video or if loading the item was cancelled
 */
@SuppressWarnings("unused")
public class UnavailableResourceException extends Exception {

    public UnavailableResourceException() {
        super();
    }

    public UnavailableResourceException(String message) {
        super(message);
    }

    public UnavailableResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnavailableResourceException(Throwable cause) {
        super(cause);
    }
}
