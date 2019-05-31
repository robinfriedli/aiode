package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;

public class TrackLoadingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;
    private final MessageChannel channel;
    @Nullable
    private final CommandContext commandContext;

    public TrackLoadingExceptionHandler(Logger logger, MessageChannel channel, @Nullable CommandContext commandContext) {
        this.logger = logger;
        this.channel = channel;
        this.commandContext = commandContext;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.error("Exception while loading tracks", e);
        if (channel != null) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(Color.RED);
            embedBuilder.setDescription("There has been an API error while loading some tracks. Please try again.");
            appendException(embedBuilder, e, false);
            recursiveCause(embedBuilder, e);

            if (commandContext != null) {
                embedBuilder.addField("CommandContext ID", commandContext.getId(), false);
            }

            MessageService messageService = new MessageService();
            messageService.send(embedBuilder.build(), channel);
        }
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
