package net.robinfriedli.botify.rest.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotations for handler methods that states that the client must be connected to an active session to use this endpoint.
 * If {@link #requiredPermissions()} is not empty this further checks whether the member connected with this session
 * has the required permissions.
 */
@Target(ElementType.METHOD)
public @interface AuthenticationRequired {

    /**
     * @return a string array containing the accessConfiguration permissionIdentifiers required to access this endpoint.
     */
    String[] requiredPermissions() default {};

}
