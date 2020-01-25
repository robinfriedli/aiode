package net.robinfriedli.botify.exceptions.handlers;

import org.slf4j.Logger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.Abort;
import net.robinfriedli.botify.exceptions.CommandFailure;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.botify.exceptions.UserException;

public class CommandExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Command command;
    private final Logger logger;

    public CommandExceptionHandler(Command command, Logger logger) {
        this.command = command;
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (e instanceof CommandFailure || e instanceof Abort) {
            return;
        }

        CommandContext commandContext = command.getContext();

        if (Botify.isShuttingDown()) {
            logger.warn(String.format("Suppressed error from command %s because it happened during shutdown: %s", commandContext.getId(), e));
            return;
        }

        MessageChannel channel = commandContext.getChannel();
        String commandDisplay = command.display();
        MessageService messageService = Botify.get().getMessageService();

        if (e instanceof UserException) {
            EmbedBuilder embedBuilder = ((UserException) e).buildEmbed();
            messageService.sendTemporary(embedBuilder.build(), channel);
        } else {
            EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
            embedBuilder.addField("CommandContext ID", commandContext.getId(), false);
            messageService.send(embedBuilder.build(), channel);
            logger.error(String.format("Exception while handling command %s on guild %s", commandDisplay, commandContext.getGuild().getName()), e);
        }
    }

}
