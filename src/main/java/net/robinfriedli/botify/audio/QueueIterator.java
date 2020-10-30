package net.robinfriedli.botify.audio;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.botify.util.EmojiConstants;

/**
 * Iterator to iterate the queue automatically when a track ends or fails loading. The current QueueIterator is registered
 * on the guilds {@link AudioPlayback} but is replaced each time a track is started manually. This makes it easy to
 * manage when the thread calling the AudioEventAdapter is recursively skipping unavailable tracks while the user
 * explicitly starts playing a new playback.
 */
public class QueueIterator extends AudioEventAdapter {

    private final AudioPlayback playback;
    private final AudioQueue queue;
    private final AudioManager audioManager;
    private final MessageService messageService;
    private final AudioTrackLoader audioTrackLoader;
    private Playable currentlyPlaying;

    private volatile boolean isReplaced;
    // Incremented when attempting to play the next track and reset when a track ends successfully.
    //
    // For the perspective of QueueIterator instances tracks are either finished completely or fail
    // due to an error. If the user skips a track a new QueueIterator instance is created.
    //
    // Value is not atomic because it is not expected to be updated concurrently but only by the audio connection thread.
    private int attemptCount;

    QueueIterator(AudioPlayback playback, AudioManager audioManager) {
        this.playback = playback;
        this.queue = playback.getAudioQueue();
        this.audioManager = audioManager;
        messageService = Botify.get().getMessageService();
        audioTrackLoader = new AudioTrackLoader(audioManager.getPlayerManager());
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        if (playback.isPaused()) {
            playback.unpause();
        }

        Playable current = track.getUserData(Playable.class);
        if (current != null) {
            audioManager.createHistoryEntry(current, playback.getGuild(), playback.getVoiceChannel());
            if (shouldSendPlaybackNotification()) {
                sendCurrentTrackNotification(current);
            }
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason reason) {
        if (reason.mayStartNext) {
            if (reason == AudioTrackEndReason.LOAD_FAILED) {
                iterateQueue(playback, queue, true);
            } else {
                // only reset the retryCount once a track has ended successfully, as tracks can fail after they started
                // and tracks that fail immediately after they start, e.g. a soundcloud track throwing a 401, would still
                // spam the chat
                //
                // hint: for the perspective of QueueIterator instances tracks are either finished completely or fail
                // due to an error. If the user skips a track a new QueueIterator instance is created
                resetAttemptCount();
                iterateQueue(playback, queue);
            }
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        Throwable e = exception;
        while (e.getCause() != null) {
            e = e.getCause();
        }
        sendError(track.getUserData(Playable.class), e);
    }

    void setReplaced() {
        isReplaced = true;
    }

    void playNext() {
        if (isReplaced) {
            return;
        }

        // don't skip over more than 10 items to avoid a frozen queue
        if (++attemptCount > 10) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(Color.RED);
            embedBuilder.setDescription("Queue contains too many unplayable tracks subsequently for automatic skipping. You can skip to the next valid track manually.");
            messageService.sendTemporary(embedBuilder.build(), playback.getCommunicationChannel());
            playback.stop();
            // reset just in case, even though the same QueueIterator instance will currently never be used again as the
            // user now either has to skip manually or start a new playback, creating a new iterator
            resetAttemptCount();
            return;
        }

        Playable track = queue.getCurrent();
        AudioItem result = null;
        AudioTrack cachedTracked = track.getCached();
        if (cachedTracked != null) {
            result = cachedTracked.makeClone();
        }

        if (result == null) {
            String playbackUrl;
            try {
                playbackUrl = track.getPlaybackUrl();
            } catch (UnavailableResourceException e) {
                iterateQueue(playback, queue, true);
                return;
            }

            try {
                result = audioTrackLoader.loadByIdentifier(playbackUrl);
            } catch (FriendlyException e) {
                sendError(track, e);

                iterateQueue(playback, queue, true);
                return;
            }
        }
        if (result != null) {
            if (result instanceof AudioTrack) {
                AudioTrack audioTrack = (AudioTrack) result;
                track.setCached(audioTrack);
                audioTrack.setUserData(track);
                playback.getAudioPlayer().playTrack(audioTrack);
                currentlyPlaying = track;
            } else {
                throw new UnsupportedOperationException("Expected an AudioTrack or Throwable but got " + result.getClass());
            }
        } else {
            iterateQueue(playback, queue);
        }
    }

    private void iterateQueue(AudioPlayback playback, AudioQueue queue) {
        iterateQueue(playback, queue, false);
    }

    private void iterateQueue(AudioPlayback playback, AudioQueue queue, boolean ignoreRepeat) {
        if (isReplaced) {
            // another exit point in case a different thread already started a new iterator making sure this one does not
            // iterate the queue one too many times resulting in the queue starting at 1 instead of 0
            return;
        }

        if (!queue.isRepeatOne() || ignoreRepeat) {
            if (queue.hasNext(ignoreRepeat)) {
                queue.iterate();
                if (!queue.hasNext(true) && queue.isRepeatAll() && queue.isShuffle()) {
                    queue.randomize();
                }
                playNext();
            } else {
                queue.reset();
                playback.leaveChannel();
            }
        } else {
            playNext();
        }
    }

    private void sendError(Playable track, Throwable e) {
        if (attemptCount == 1) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (track != null) {
                embedBuilder.setTitle("Could not load track " + track.display());
            } else {
                embedBuilder.setTitle("Could not load current track");
            }

            if (e.getMessage() != null) {
                embedBuilder.setDescription("Message returned by source: " + e.getMessage());
            }

            embedBuilder.setColor(Color.RED);

            if (queue.hasNext(true)) {
                embedBuilder.addField("", "Skipping to the next playable track...", false);
            }

            messageService.sendTemporary(embedBuilder.build(), playback.getCommunicationChannel());
        }
    }

    private void sendCurrentTrackNotification(Playable currentTrack) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Now playing", currentTrack.display(), false);

        if (queue.hasNext()) {
            embedBuilder.addField("Next", queue.getNext().fetch().display(2, TimeUnit.SECONDS), false);
        }

        StringBuilder footerBuilder = new StringBuilder();
        appendIfTrue(footerBuilder, EmojiConstants.SHUFFLE, playback.isShuffle());
        appendIfTrue(footerBuilder, EmojiConstants.REPEAT, playback.isRepeatAll());
        appendIfTrue(footerBuilder, EmojiConstants.REPEAT_ONE, playback.isRepeatOne());
        footerBuilder.append(" | ").append("View the queue using the queue command");

        String albumCoverUrl = currentTrack.getAlbumCoverUrl();
        if (albumCoverUrl == null) {
            SpringPropertiesConfig springPropertiesConfig = Botify.get().getSpringPropertiesConfig();
            String baseUri = springPropertiesConfig.requireApplicationProperty("botify.server.base_uri");
            albumCoverUrl = baseUri + "/resources-public/img/botify-logo-small.png";
        }
        embedBuilder.setFooter(footerBuilder.toString(), albumCoverUrl);

        Guild guild = playback.getGuild();
        Color color = StaticSessionProvider.invokeWithSession(session -> {
            GuildSpecification specification = Botify.get().getGuildManager().getContextForGuild(guild).getSpecification(session);
            return ColorSchemeProperty.getColor(specification);
        });
        embedBuilder.setColor(color);

        CompletableFuture<Message> futureMessage = messageService.send(embedBuilder.build(), playback.getCommunicationChannel());
        futureMessage.thenAccept(playback::setLastPlaybackNotification);
        audioManager.createNowPlayingWidget(futureMessage, playback);
    }

    private boolean shouldSendPlaybackNotification() {
        return StaticSessionProvider.invokeWithSession(session -> {
            Guild guild = playback.getGuild();
            GuildSpecification specification = Botify.get().getGuildManager().getContextForGuild(guild).getSpecification(session);
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            return guildPropertyManager
                .getPropertyValueOptional("sendPlaybackNotification", Boolean.class, specification)
                .orElse(true);
        });
    }

    private void appendIfTrue(StringBuilder builder, String s, boolean b) {
        if (b) {
            builder.append(s);
        }
    }

    private void resetAttemptCount() {
        attemptCount = 0;
    }

    public Playable getCurrentlyPlaying() {
        return currentlyPlaying;
    }
}
