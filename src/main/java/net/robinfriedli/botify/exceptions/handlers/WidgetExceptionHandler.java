package net.robinfriedli.botify.exceptions.handlers;

import org.slf4j.Logger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

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
        Botify.get().getMessageService().send(embedBuilder.build(), textChannel);
        logger.error("Exception in widget", e);
    }
}
