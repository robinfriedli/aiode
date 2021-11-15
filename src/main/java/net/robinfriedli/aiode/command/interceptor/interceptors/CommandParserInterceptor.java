package net.robinfriedli.aiode.command.interceptor.interceptors;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.aiode.command.interceptor.CommandInterceptor;
import net.robinfriedli.aiode.command.parser.CommandParser;
import net.robinfriedli.aiode.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.exceptions.CommandParseException;
import net.robinfriedli.aiode.exceptions.UnexpectedCommandSetupException;

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
