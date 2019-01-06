package net.robinfriedli.botify.exceptions;

import javax.annotation.Nullable;

import org.slf4j.Logger;

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
        StringBuilder sb = new StringBuilder();
        sb.append("There has been an API error while loading ");
        if (playlist != null) {
            sb.append("playlist ").append(playlist.getTitle());
        } else {
            sb.append("some tracks");
        }
        sb.append(". Please try again.");
        appendException(sb, e);
        recursiveCause(sb, e);

        if (playlist != null) {
            logger.error("Exception while loading playlist " + playlist.getId(), e);
        } else {
            logger.error("Exception while loading tracks", e);
        }

        AlertService alertService = new AlertService();
        alertService.send(sb.toString(), channel);
    }

    private void recursiveCause(StringBuilder sb, Throwable e) {
        Throwable cause = e.getCause();
        if (cause != null) {
            sb.append(System.lineSeparator()).append("Caused by:");
            appendException(sb, cause);
            recursiveCause(sb, cause);
        }
    }

    private void appendException(StringBuilder sb, Throwable e) {
        sb.append(System.lineSeparator()).append("Exception: ").append(e.getClass().getSimpleName());
        if (e.getMessage() != null) {
            sb.append(System.lineSeparator()).append("Message: ").append(e.getMessage());
        }
    }
}
