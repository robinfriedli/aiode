package net.robinfriedli.botify.command.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class CommandVerificationInterceptor implements CommandInterceptor {

    @Override
    public void intercept(AbstractCommand command) {
        command.verify();

        if (command.getCommandBody().length() > 1000) {
            throw new InvalidCommandException("Command input exceeds maximum length of 1000 characters.");
        }
    }
}
