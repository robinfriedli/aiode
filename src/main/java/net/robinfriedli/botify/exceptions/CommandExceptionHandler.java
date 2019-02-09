package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import org.slf4j.Logger;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.AlertService;

public class CommandExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final CommandContext commandContext;
    private final Logger logger;

    public CommandExceptionHandler(CommandContext commandContext, Logger logger) {
        this.commandContext = commandContext;
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        MessageChannel channel = commandContext.getChannel();
        String command = commandContext.getMessage().getContentDisplay();
        AlertService alertService = new AlertService(logger);

        Throwable exception = e instanceof CommandRuntimeException ? e.getCause() : e;
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.RED);
        embedBuilder.addField("Error", String.format("%s: %s", exception.getClass().getSimpleName(), exception.getMessage()), false);
        recursiveCause(embedBuilder, exception);
        alertService.send(embedBuilder.build(), channel);
        logger.error(String.format("Exception while handling command %s on guild %s", command, commandContext.getGuild().getName()), exception);
    }

    private void recursiveCause(EmbedBuilder embedBuilder, Throwable exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            embedBuilder.addField("Caused by", String.format("%s: %s", cause.getClass().getSimpleName(), cause.getMessage()), false);
            recursiveCause(embedBuilder, cause);
        }
    }
}
