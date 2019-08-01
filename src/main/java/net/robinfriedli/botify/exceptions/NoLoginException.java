package net.robinfriedli.botify.exceptions;

import net.dv8tion.jda.core.entities.User;

/**
 * Thrown when a Spotify login is required for an action but none is found for the corresponding user.
 */
public class NoLoginException extends UserException {

    public NoLoginException(User user) {
        super(String.format("User %s is not logged in.", user.getName()));
    }

}
