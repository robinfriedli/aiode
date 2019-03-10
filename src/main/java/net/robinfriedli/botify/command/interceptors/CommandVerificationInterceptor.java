package net.robinfriedli.botify.command.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class CommandVerificationInterceptor implements CommandInterceptor {

    @Override
    public void intercept(AbstractCommand command) {
        if (command.requiresInput() && command.getCommandBody().isBlank()) {
            throw new InvalidCommandException("That command requires more input!");
        }

        command.getArgumentContribution().complete();
    }
}
