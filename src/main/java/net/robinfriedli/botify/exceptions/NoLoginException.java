package net.robinfriedli.botify.exceptions;

import net.dv8tion.jda.core.entities.User;

/**
 * Thrown by CommandExecutor when a login is required but none is found for the corresponding user.
 */
public class NoLoginException extends RuntimeException {

    public NoLoginException(User user) {
        super(String.format("User %s is not logged in.", user.getName()));
    }

}
