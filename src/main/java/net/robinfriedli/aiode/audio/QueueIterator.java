package net.robinfriedli.aiode.audio;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudM3uAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import dev.lavalink.youtube.track.YoutubeAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackRedirect;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.exceptions.ExceptionUtils;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import net.robinfriedli.aiode.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.aiode.util.EmojiConstants;
import net.robinfriedli.threadpool.ThreadPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Iterator to iterate the queue automatically when a track ends or fails loading. The current QueueIterator is registered
 * on the guilds {@link AudioPlayback} but is replaced each time a track is started manually. This makes it easy to
 * manage when the thread calling the AudioEventAdapter is recursively skipping unavailable tracks while the user
 * explicitly starts playing a new playback.
 */
public class QueueIterator extends AudioEventAdapter {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final ThreadPool AUDIO_EVENT_POOL = ThreadPool.Builder.create()
        .setCoreSize(3)
        .setMaxSize(50)
        .setKeepAlive(5L, TimeUnit.MINUTES)
        .setThreadFactory(new ThreadFactory() {

            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r);
                t.setName("audio-event-pool-thread-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
                return t;
            }
        })
        .build();

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
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    private volatile boolean isYouTubeBanned = false;
    private volatile boolean retryCurrent = false;

    QueueIterator(AudioPlayback playback, AudioManager audioManager) {
        this.playback = playback;
        this.queue = playback.getAudioQueue();
        this.audioManager = audioManager;
        messageService = Aiode.get().getMessageService();
        audioTrackLoader = new AudioTrackLoader(audioManager.getPlayerManager());
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        handleAudioEvent(() -> {
            if (playback.isPaused()) {
                playback.unpause();
            }

            Playable current = track.getUserData(Playable.class);
            if (current != null) {
                audioManager.createHistoryEntry(current, playback.getGuild(), playback.getAudioChannel());
                if (shouldSendPlaybackNotification()) {
                    sendCurrentTrackNotification(current, track);
                }
            }
        });
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason reason) {
        if (reason.mayStartNext) {
            handleAudioEvent(() -> {
                if (retryCurrent) {
                    playNext();
                } else if (reason == AudioTrackEndReason.LOAD_FAILED) {
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
            });
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        Throwable e = ExceptionUtils.getRootCause(exception);
        Playable playable = track.getUserData(Playable.class);
        if (!isYouTubeBanned && isYouTubeBanError(playable, e)) {
            isYouTubeBanned = true;
            if (playable instanceof SpotifyTrackRedirect spotifyTrackRedirect) {
                // don't send error if the yt redirect failed and a soundcloud track is present because the track will get retried
                if (spotifyTrackRedirect.isYouTube() && spotifyTrackRedirect.getCompletedSoundCloudTrack() != null) {
                    logger.warn("Failed to play YouTube video for redirected Spotify track, trying SoundCloud instead");
                    retryCurrent = true;
                    return;
                }
            }
        }
        if (exception.severity == FriendlyException.Severity.COMMON) {
            logger.warn("Common lavaplayer track error: " + exception.getMessage());
        } else {
            logger.error("Lavaplayer track exception", exception);
        }
        sendError(playable, e);
    }

    void setReplaced() {
        isReplaced = true;
    }

    void playNext() {
        if (isReplaced) {
            return;
        }
        boolean ignoreCache;
        if (retryCurrent) {
            retryCurrent = false;
            ignoreCache = true;
        } else {
            ignoreCache = false;
        }

        // don't skip over more than 3 items to avoid a frozen queue
        if (attemptCount.incrementAndGet() > 3) {
            MessageChannel communicationChannel = playback.getCommunicationChannel();
            if (communicationChannel != null) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setColor(Color.RED);
                embedBuilder.setDescription("Queue contains too many unplayable tracks subsequently for automatic skipping. You can skip to the next valid track manually.");
                messageService.sendTemporary(embedBuilder.build(), communicationChannel);
            }
            playback.stop();
            // reset just in case, even though the same QueueIterator instance will currently never be used again as the
            // user now either has to skip manually or start a new playback, creating a new iterator
            resetAttemptCount();
            return;
        }

        Playable track = queue.getCurrent();
        AudioItem result = null;
        if (!ignoreCache) {
            AudioTrack cachedTracked = track.getCached();
            if (cachedTracked != null) {
                result = cachedTracked.makeClone();
            }
        }

        if (result == null) {
            String playbackUrl;
            try {
                // for SpotifyTrackRedirect, prioritise YouTube last when banned
                if (isYouTubeBanned && track instanceof SpotifyTrackRedirect spotifyTrackRedirect) {
                    FilebrokerPlayableWrapper completedFilebrokerPost = spotifyTrackRedirect.getCompletedFilebrokerPost();
                    if (completedFilebrokerPost != null) {
                        playbackUrl = completedFilebrokerPost.getPlaybackUrl();
                    } else {
                        UrlPlayable completedSoundCloudTrack = spotifyTrackRedirect.getCompletedSoundCloudTrack();
                        if (completedSoundCloudTrack != null) {
                            playbackUrl = completedSoundCloudTrack.getPlaybackUrl();
                        } else {
                            playbackUrl = track.getPlaybackUrl();
                        }
                    }
                } else {
                    playbackUrl = track.getPlaybackUrl();
                }
            } catch (UnavailableResourceException e) {
                iterateQueue(playback, queue, true);
                return;
            }

            try {
                result = audioTrackLoader.loadByIdentifier(playbackUrl);
            } catch (FriendlyException e) {
                if (!isYouTubeBanned && isYouTubeBanError(track, e)) {
                    isYouTubeBanned = true;
                    if (track instanceof SpotifyTrackRedirect spotifyTrackRedirect && spotifyTrackRedirect.getCompletedSoundCloudTrack() != null) {
                        // retry redirect using soundcloud on yt ban
                        retryCurrent = true;
                        logger.warn("Failed to play YouTube video for redirected Spotify track, trying SoundCloud instead");
                        playNext();
                        return;
                    }
                }

                if (e.severity == FriendlyException.Severity.COMMON) {
                    logger.warn("Common lavaplayer track error: " + e.getMessage());
                } else {
                    logger.error("Lavaplayer track exception", e);
                }

                sendError(track, e);

                iterateQueue(playback, queue, true);
                return;
            }
        }
        if (result != null) {
            if (result instanceof AudioTrack audioTrack) {
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

        if (!queue.getRepeatOne() || ignoreRepeat) {
            if (queue.hasNext(ignoreRepeat)) {
                queue.iterate();
                playNext();
            } else {
                queue.reset();
                playback.leaveChannel();
            }
        } else {
            playNext();
        }
    }

    private boolean isYouTubeBanError(@Nullable Playable track, Throwable e) {
        return e.getMessage() != null
            && (track instanceof YouTubeVideo || (track instanceof SpotifyTrackRedirect spotifyTrackRedirect && spotifyTrackRedirect.isYouTube()))
            && (e.getMessage().contains("not a bot") || e.getMessage().contains("403"));
    }

    private void sendError(@Nullable Playable track, Throwable e) {
        if (attemptCount.get() == 1) {
            MessageChannel communicationChannel = playback.getCommunicationChannel();

            if (communicationChannel == null) {
                return;
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (track != null) {
                embedBuilder.setTitle("Could not load track " + track.display());
            } else {
                embedBuilder.setTitle("Could not load current track");
            }

            if (isYouTubeBanError(track, e)) {
                embedBuilder.setDescription("YouTube blocked playback due to bot detection. Note that Spotify tracks are looked up on YouTube, unless they are found on [filebroker](https://filebroker.io/) or SoundCloud. " +
                    "[Supporters](https://ko-fi.com/R5R0XAC5J) may circumvent YouTube bot detection by inviting a limited private bot using the invite command. " +
                    "Larger bots generating too much traffic will eventually get banned, thus stable YouTube support for the public bot is no longer possible. " +
                    "To continue playing Spotify content on the public bot, you can help by uploading content to [filebroker](https://filebroker.io/register).");
            } else if (e.getMessage() != null && e.getMessage().length() <= 4096) {
                embedBuilder.setDescription("Message returned by source: " + e.getMessage());
            }

            embedBuilder.setColor(Color.RED);

            if (queue.hasNext(true)) {
                embedBuilder.addField("", "Skipping to the next playable track...", false);
            }

            messageService.sendTemporary(embedBuilder.build(), communicationChannel);
        }
    }

    private void sendCurrentTrackNotification(Playable currentTrack, AudioTrack track) {
        MessageChannel communicationChannel = playback.getCommunicationChannel();

        if (communicationChannel == null) {
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Now playing", currentTrack.display(), false);

        Playable next = queue.peekNext();
        if (next != null) {
            embedBuilder.addField("Next", next.display(), false);
        }

        StringBuilder footerBuilder = new StringBuilder();
        appendIfTrue(footerBuilder, EmojiConstants.SHUFFLE, playback.isShuffle());
        appendIfTrue(footerBuilder, EmojiConstants.REPEAT, playback.isRepeatAll());
        appendIfTrue(footerBuilder, EmojiConstants.REPEAT_ONE, playback.isRepeatOne());
        boolean isFilebroker = currentTrack instanceof FilebrokerPlayableWrapper
            || (currentTrack instanceof SpotifyTrackRedirect spotifyTrackRedirect && spotifyTrackRedirect.isRedirectedToFilebroker());
        if (isFilebroker) {
            footerBuilder.append(" | ").append("Powered by filebroker.io");
        } else if (currentTrack instanceof SpotifyTrackRedirect && (track instanceof YoutubeAudioTrack || track instanceof com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack)) {
            footerBuilder.append(" | ").append("Powered by YouTube");
        } else if (currentTrack instanceof SpotifyTrackRedirect && (track instanceof SoundCloudAudioTrack || track instanceof SoundCloudM3uAudioTrack)) {
            footerBuilder.append(" | ").append("Powered by SoundCloud");
        } else {
            footerBuilder.append(" | ").append("View the queue using the queue command");
        }

        String albumCoverUrl = currentTrack.getAlbumCoverUrl();
        SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
        String baseUri = springPropertiesConfig.requireApplicationProperty("aiode.server.base_uri");
        if (albumCoverUrl == null) {
            albumCoverUrl = baseUri + "/resources-public/img/aiode-logo.png";
        }
        embedBuilder.setFooter(footerBuilder.toString(), baseUri + (isFilebroker ? "/resources-public/img/filebroker-logo-small.png" : "/resources-public/img/aiode-logo-small.png"));
        embedBuilder.setThumbnail(albumCoverUrl);
        embedBuilder.setAuthor("Support aiode", "https://ko-fi.com/R5R0XAC5J", "https://storage.ko-fi.com/cdn/brandasset/kofi_s_logo_nolabel.png");

        Guild guild = playback.getGuild();
        Color color = StaticSessionProvider.invokeWithSession(session -> {
            GuildSpecification specification = Aiode.get().getGuildManager().getContextForGuild(guild).getSpecification(session);
            return ColorSchemeProperty.getColor(specification);
        });
        embedBuilder.setColor(color);

        CompletableFuture<Message> futureMessage = messageService.send(embedBuilder.build(), communicationChannel);
        futureMessage.thenAccept(playback::setLastPlaybackNotification);
        audioManager.createNowPlayingWidget(futureMessage, playback);
    }

    private void handleAudioEvent(Runnable runnable) {
        AUDIO_EVENT_POOL.execute(() -> {
            if (isReplaced) {
                return;
            }

            synchronized (this) {
                runnable.run();
            }
        });
    }

    private boolean shouldSendPlaybackNotification() {
        return StaticSessionProvider.invokeWithSession(session -> {
            Guild guild = playback.getGuild();
            GuildSpecification specification = Aiode.get().getGuildManager().getContextForGuild(guild).getSpecification(session);
            GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();
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
        attemptCount.set(0);
    }

    public Playable getCurrentlyPlaying() {
        return currentlyPlaying;
    }
}
