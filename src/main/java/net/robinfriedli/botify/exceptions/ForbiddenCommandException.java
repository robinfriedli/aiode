package net.robinfriedli.botify.exceptions;

import java.util.List;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.stringlist.StringListImpl;


/**
 * Exception thrown when a user tries to use a command that requires a certain role the user does not have
 */
public class ForbiddenCommandException extends UserException {

    public ForbiddenCommandException(User user, String commandIdentifier, List<Role> roles) {
        super(String.format("User %s is not allowed to use command %s. %s.",
            user.getName(),
            commandIdentifier,
            roles.isEmpty()
                ? "Only available to guild owner and administrator roles"
                : "Requires any of these roles: " + StringListImpl.create(roles, Role::getName).toSeparatedString(", ")));
    }

    public ForbiddenCommandException(User user, String commandIdentifier, String availableTo) {
        super(String.format("User %s is not allowed to use command %s. Only available to %s.",
            user.getName(), commandIdentifier, availableTo));
    }

}
