package net.robinfriedli.botify.command.interceptor.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.UnexpectedCommandSetupException;
import net.robinfriedli.botify.exceptions.UserException;

/**
 * Interceptor that verifies a command by checking all argument rules and command input
 */
public class CommandVerificationInterceptor extends AbstractChainableCommandInterceptor {

    public CommandVerificationInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        super(contribution, next);
    }

    @Override
    public void performChained(Command command) {
        if (command instanceof AbstractCommand) {
            AbstractCommand textBasedCommand = (AbstractCommand) command;
            try {
                textBasedCommand.verify();
            } catch (UserException e) {
                throw e;
            } catch (Exception e) {
                throw new UnexpectedCommandSetupException("Unexpected exception while verifying command", e);
            }

            if (textBasedCommand.getCommandInput().length() > 1000) {
                throw new InvalidCommandException("Command input exceeds maximum length of 1000 characters.");
            }
        }
    }
}
