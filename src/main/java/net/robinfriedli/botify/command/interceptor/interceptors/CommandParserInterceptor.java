package net.robinfriedli.botify.command.interceptor.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.command.parser.CommandParser;
import net.robinfriedli.botify.discord.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;

public class CommandParserInterceptor extends AbstractChainableCommandInterceptor {

    public CommandParserInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        super(contribution, next);
    }

    @Override
    public void performChained(AbstractCommand command) {
        CommandParser commandParser = new CommandParser(command, ArgumentPrefixProperty.getForCurrentContext());
        commandParser.parse();
    }
}
