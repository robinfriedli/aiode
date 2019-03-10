package net.robinfriedli.botify.command.interceptors;

import java.util.Date;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.botify.entities.CommandHistory;

public class HistoryInterceptor implements CommandInterceptor {

    @Override
    public void intercept(AbstractCommand command) {
        CommandContext context = command.getContext();
        CommandHistory history = new CommandHistory();
        long currentTimeMillis = System.currentTimeMillis();
        history.setStartMillis(currentTimeMillis);
        history.setTimestamp(new Date(currentTimeMillis));
        history.setCommandIdentifier(command.getIdentifier());
        history.setCommandBody(command.getCommandBody());
        history.setInput(context.getMessage().getContentDisplay());
        history.setGuild(context.getGuild().getName());
        history.setGuildId(context.getGuild().getId());
        history.setUser(context.getUser().getName());
        history.setUserId(context.getUser().getId());
        context.setCommandHistory(history);
    }
}
