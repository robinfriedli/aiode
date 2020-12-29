package net.robinfriedli.botify.command.interceptor.interceptors;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.command.widget.AbstractWidgetAction;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;

/**
 * Interceptor that creates a commands {@link CommandHistory} entry
 */
public class HistoryInterceptor extends AbstractChainableCommandInterceptor {

    public HistoryInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        super(contribution, next);
    }

    @Override
    public void performChained(Command command) {
        CommandContext context = command.getContext();
        CommandHistory history = new CommandHistory();
        long currentTimeMillis = System.currentTimeMillis();
        history.setStartMillis(currentTimeMillis);
        history.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault()));
        history.setCommandContextId(command.getContext().getId());
        history.setCommandIdentifier(command.getIdentifier());
        history.setWidget(command instanceof AbstractWidgetAction);
        history.setCommandBody(command instanceof AbstractCommand ? ((AbstractCommand) command).getCommandInput() : command.getCommandBody());
        history.setInput(command instanceof AbstractCommand ? context.getMessage().getContentDisplay() : command.getCommandBody());
        history.setGuild(context.getGuild().getName());
        history.setGuildId(context.getGuild().getId());
        history.setUser(context.getUser().getName());
        history.setUserId(context.getUser().getId());
        context.setCommandHistory(history);
    }
}
