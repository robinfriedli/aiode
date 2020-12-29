package net.robinfriedli.botify.exceptions;

/**
 * Exception thrown when a discord entity stored by id could not be loaded.
 */
public class DiscordEntityInitialisationException extends RuntimeException {

    public DiscordEntityInitialisationException() {
        super();
    }

    public DiscordEntityInitialisationException(String message) {
        super(message);
    }

    public DiscordEntityInitialisationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DiscordEntityInitialisationException(Throwable cause) {
        super(cause);
    }
}
