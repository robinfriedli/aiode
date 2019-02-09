package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.discord.AlertService;

public class TrackLoadingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;
    private final MessageChannel channel;
    @Nullable
    private final YouTubePlaylist playlist;

    public TrackLoadingExceptionHandler(Logger logger, MessageChannel channel, @Nullable YouTubePlaylist playlist) {
        this.logger = logger;
        this.channel = channel;
        this.playlist = playlist;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        StringBuilder titleBuilder = new StringBuilder();
        titleBuilder.append("There has been an API error while loading ");
        if (playlist != null) {
            titleBuilder.append("playlist ").append(playlist.getTitle());
        } else {
            titleBuilder.append("some tracks");
        }
        titleBuilder.append(". Please try again.");
        embedBuilder.setColor(Color.RED);
        embedBuilder.setDescription(titleBuilder.toString());
        appendException(embedBuilder, e, false);
        recursiveCause(embedBuilder, e);

        if (playlist != null) {
            logger.error("Exception while loading playlist " + playlist.getId(), e);
        } else {
            logger.error("Exception while loading tracks", e);
        }

        AlertService alertService = new AlertService(logger);
        alertService.send(embedBuilder.build(), channel);
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
