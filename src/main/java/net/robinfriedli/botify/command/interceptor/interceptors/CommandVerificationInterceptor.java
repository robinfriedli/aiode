package net.robinfriedli.botify.command.interceptor.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class CommandVerificationInterceptor extends AbstractChainableCommandInterceptor {

    public CommandVerificationInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        super(contribution, next);
    }

    @Override
    public void performChained(AbstractCommand command) {
        command.verify();

        if (command.getCommandBody().length() > 1000) {
            throw new InvalidCommandException("Command input exceeds maximum length of 1000 characters.");
        }
    }
}
