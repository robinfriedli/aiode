package net.robinfriedli.botify.command.interceptor.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.command.parser.CommandParser;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.CommandParseException;
import net.robinfriedli.botify.exceptions.UnexpectedCommandSetupException;

/**
 * Interceptor that parses the used arguments and command input for text based commands by calling the {@link CommandParser}
 */
public class CommandParserInterceptor extends AbstractChainableCommandInterceptor {

    public CommandParserInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        super(contribution, next);
    }

    @Override
    public void performChained(Command command) {
        if (command instanceof AbstractCommand) {
            AbstractCommand textBasedCommand = (AbstractCommand) command;
            CommandParser commandParser = new CommandParser(textBasedCommand, ArgumentPrefixProperty.getForCurrentContext());

            try {
                commandParser.parse();
            } catch (CommandParseException e) {
                throw e;
            } catch (Exception e) {
                throw new UnexpectedCommandSetupException(e);
            }
        }
    }
}
