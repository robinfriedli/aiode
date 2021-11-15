package net.robinfriedli.aiode.rest.exceptions;

/**
 * Thrown when the bot cannot connect to the guild or user specified by the session of the web client, i.e. when getGuild
 * or getMember return null.
 */
public class MissingAccessException extends RuntimeException {

    public MissingAccessException() {
        super();
    }

    public MissingAccessException(String message) {
        super(message);
    }
}
