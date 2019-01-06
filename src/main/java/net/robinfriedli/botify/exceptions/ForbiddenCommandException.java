package net.robinfriedli.botify.exceptions;

import java.util.List;

import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.stringlist.StringListImpl;


/**
 * Exception thrown when a user tries to use a command that requires a certain role the user does not have
 */
public class ForbiddenCommandException extends RuntimeException {

    public ForbiddenCommandException(User user, String commandIdentifier, List<Role> roles) {
        super(String.format("User %s is not allowed to use command %s. %s.",
            user.getName(),
            commandIdentifier,
            roles.isEmpty()
                ? "Only available to guild owner"
                : "Required roles: " + StringListImpl.create(roles, Role::getName).toSeparatedString(", ")));
    }

}
