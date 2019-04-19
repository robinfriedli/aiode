package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import org.slf4j.Logger;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.discord.MessageService;

public class TrackLoadingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;
    private final MessageChannel channel;

    public TrackLoadingExceptionHandler(Logger logger, MessageChannel channel) {
        this.logger = logger;
        this.channel = channel;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.RED);
        embedBuilder.setDescription("There has been an API error while loading some tracks. Please try again.");
        appendException(embedBuilder, e, false);
        recursiveCause(embedBuilder, e);
        logger.error("Exception while loading tracks", e);

        MessageService messageService = new MessageService();
        messageService.send(embedBuilder.build(), channel);
    }

    private void recursiveCause(EmbedBuilder embedBuilder, Throwable e) {
        Throwable cause = e.getCause();
        if (cause != null) {
            appendException(embedBuilder, cause, true);
            recursiveCause(embedBuilder, cause);
        }
    }

    private void appendException(EmbedBuilder embedBuilder, Throwable e, boolean isCause) {
        String message = e instanceof GoogleJsonResponseException
            ? ((GoogleJsonResponseException) e).getDetails().getMessage()
            : e.getMessage();
        embedBuilder.addField(isCause ? "Caused by" : "Error", String.format("%s: %s", e.getClass().getSimpleName(), message), false);
    }
}
