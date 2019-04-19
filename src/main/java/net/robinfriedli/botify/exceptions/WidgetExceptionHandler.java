package net.robinfriedli.botify.exceptions;

import org.slf4j.Logger;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.TextChannel;
import net.robinfriedli.botify.discord.MessageService;

public class WidgetExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final TextChannel textChannel;
    private final Logger logger;

    public WidgetExceptionHandler(TextChannel textChannel, Logger logger) {
        this.textChannel = textChannel;
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
        new MessageService().send(embedBuilder.build(), textChannel);
        logger.error("Exception in widget", e);
    }
}
