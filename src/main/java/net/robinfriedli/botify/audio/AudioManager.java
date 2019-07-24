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
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.NowPlayingWidget;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class AudioManager {

    private final AudioPlayerManager playerManager;
    private final YouTubeService youTubeService;
    private final Logger logger;
    private final SessionFactory sessionFactory;
    private final CommandManager commandManager;
    private final GuildManager guildManager;
    private final ExecutorService executorService;
    private final UrlAudioLoader urlAudioLoader;

    public AudioManager(YouTubeService youTubeService, SessionFactory sessionFactory, CommandManager commandManager, GuildManager guildManager) {
        playerManager = new DefaultAudioPlayerManager();
        this.youTubeService = youTubeService;
        this.guildManager = guildManager;
        this.logger = LoggerFactory.getLogger(getClass());
        this.sessionFactory = sessionFactory;
        this.commandManager = commandManager;
        executorService = Executors.newFixedThreadPool(5);
        urlAudioLoader = new UrlAudioLoader(playerManager);
        AudioSourceManagers.registerRemoteSources(playerManager);
        guildManager.setAudioManager(this);
    }

    public void playTrack(Guild guild, @Nullable VoiceChannel channel) {
        AudioPlayback playback = getPlaybackForGuild(guild);

        if (channel != null) {
            setChannel(playback, channel);
        } else if (playback.getVoiceChannel() == null) {
            throw new InvalidCommandException("Not in a voice channel");
        }

        QueueIterator queueIterator = new QueueIterator(playback, this);
        playback.setCurrentQueueIterator(queueIterator);
        queueIterator.playNext();
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

    public void leaveChannel(AudioPlayback playback) {
        playback.getGuild().getAudioManager().closeAudioConnection();
        playback.setVoiceChannel(null);
    }

    public AudioPlayerManager getPlayerManager() {
        return playerManager;
    }

    public PlayableFactory createPlayableFactory(Guild guild) {
        return new PlayableFactory(urlAudioLoader, youTubeService, guildManager.getContextForGuild(guild).getTrackLoadingExecutor());
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
        futureMessage.thenAccept(message -> commandManager.registerWidget(new NowPlayingWidget(commandManager, message, playback, this)));
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
