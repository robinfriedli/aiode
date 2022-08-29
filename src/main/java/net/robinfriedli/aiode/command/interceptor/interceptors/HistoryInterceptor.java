package net.robinfriedli.aiode.command.interceptor.interceptors;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.aiode.command.interceptor.CommandInterceptor;
import net.robinfriedli.aiode.command.widget.AbstractWidgetAction;
import net.robinfriedli.aiode.entities.CommandHistory;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;

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
        history.setInput(command instanceof AbstractCommand ? context.getMessage() : command.getCommandBody());
        history.setGuild(context.getGuild().getName());
        history.setGuildId(context.getGuild().getId());
        history.setUser(context.getUser().getName());
        history.setUserId(context.getUser().getId());
        context.setCommandHistory(history);
    }
}
