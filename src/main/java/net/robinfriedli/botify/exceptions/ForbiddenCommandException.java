package net.robinfriedli.botify.exceptions;

import java.util.List;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.stringlist.StringList;


/**
 * Exception thrown when a user tries to use a command that requires a certain role the user does not have
 */
public class ForbiddenCommandException extends UserException {

    public ForbiddenCommandException(User user, PermissionTarget permissionTarget, List<Role> roles) {
        super(
            String.format(
                "User %s is not allowed to use %s %s. %s.",
                user.getAsMention(),
                permissionTarget.getPermissionTargetType().getName(),
                permissionTarget.getFullPermissionTargetIdentifier(),
                roles.isEmpty()
                    ? "Only available to guild owner and administrator roles"
                    : "Requires any of these roles: " + StringList.create(roles, Role::getName).toSeparatedString(", ")
            )
        );
    }

    public ForbiddenCommandException(User user, PermissionTarget permissionTarget, String availableTo) {
        super(
            String.format(
                "User %s is not allowed to use %s %s. Only available to %s.",
                user.getAsMention(),
                permissionTarget.getPermissionTargetType().getName(),
                permissionTarget.getFullPermissionTargetIdentifier(),
                availableTo
            )
        );
    }

}
