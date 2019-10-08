package net.robinfriedli.botify.audio;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widgets.NowPlayingWidget;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Provides access to all {@link AudioPlayback} for all guilds and methods to retrieve all audio related factories and
 * services, such as the {@link PlayableFactory} or {@link YouTubeService}, or methods to start a playback or
 * join / leave voice channels. Also manages the playback history and creates the {@link NowPlayingWidget}
 */
public class AudioManager {

    private final AudioPlayerManager playerManager;
    private final YouTubeService youTubeService;
    private final Logger logger;
    private final SessionFactory sessionFactory;
    private final GuildManager guildManager;
    private final ExecutorService executorService;
    private final UrlAudioLoader urlAudioLoader;

    public AudioManager(YouTubeService youTubeService, SessionFactory sessionFactory, GuildManager guildManager) {
        playerManager = new DefaultAudioPlayerManager();
        this.youTubeService = youTubeService;
        this.guildManager = guildManager;
        this.logger = LoggerFactory.getLogger(getClass());
        this.sessionFactory = sessionFactory;
        executorService = Executors.newFixedThreadPool(5, r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        urlAudioLoader = new UrlAudioLoader(playerManager);
        AudioSourceManagers.registerRemoteSources(playerManager);
        guildManager.setAudioManager(this);
    }

    public void startPlayback(Guild guild, @Nullable VoiceChannel channel) {
        playTrack(guild, channel, false);
    }

    public void startOrResumePlayback(Guild guild, @Nullable VoiceChannel channel) {
        playTrack(guild, channel, true);
    }

    public void playTrack(Guild guild, @Nullable VoiceChannel channel, boolean resumePaused) {
        AudioPlayback playback = getPlaybackForGuild(guild);

        if (channel != null) {
            setChannel(playback, channel);
        } else if (playback.getVoiceChannel() == null) {
            throw new InvalidCommandException("Not in a voice channel");
        }

        if (CommandContext.Current.isSet()) {
            playback.setCommunicationChannel(CommandContext.Current.require().getChannel());
        }

        if (playback.isPaused() && resumePaused) {
            playback.unpause();
        } else {
            QueueIterator queueIterator = new QueueIterator(playback, this);
            playback.setCurrentQueueIterator(queueIterator);
            queueIterator.playNext();
        }
    }

    public AudioPlayback getPlaybackForGuild(Guild guild) {
        return guildManager.getContextForGuild(guild).getPlayback();
    }

    public AudioQueue getQueue(Guild guild) {
        return getPlaybackForGuild(guild).getAudioQueue();
    }

    public YouTubeService getYouTubeService() {
        return youTubeService;
    }

    public AudioPlayerManager getPlayerManager() {
        return playerManager;
    }

    public PlayableFactory createPlayableFactory(Guild guild, SpotifyService spotifyService) {
        return new PlayableFactory(spotifyService, urlAudioLoader, youTubeService, guildManager.getContextForGuild(guild).getTrackLoadingExecutor());
    }

    void createHistoryEntry(Playable playable, Guild guild) {
        executorService.execute(() -> {
            try (Session session = sessionFactory.openSession()) {
                PlaybackHistory playbackHistory = new PlaybackHistory(LocalDateTime.now(), playable, guild, session);
                session.beginTransaction();
                session.persist(playbackHistory);
                session.getTransaction().commit();
            } catch (Throwable e) {
                logger.error("Exception while creating playback history entry", e);
            }
        });
    }

    void createNowPlayingWidget(CompletableFuture<Message> futureMessage, AudioPlayback playback) {
        WidgetManager widgetManager = guildManager.getContextForGuild(playback.getGuild()).getWidgetManager();
        futureMessage.thenAccept(message -> widgetManager.registerWidget(new NowPlayingWidget(widgetManager, message)));
    }

    private void setChannel(AudioPlayback audioPlayback, VoiceChannel channel) {
        audioPlayback.setVoiceChannel(channel);
        Guild guild = audioPlayback.getGuild();
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(audioPlayback.getAudioPlayer()));
        try {
            guild.getAudioManager().openAudioConnection(channel);
        } catch (InsufficientPermissionException e) {
            throw new InvalidCommandException("I do not have permission to join this voice channel!");
        }
    }

}
