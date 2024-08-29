package net.robinfriedli.aiode.audio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup;
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingNanoIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.IpBlock;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.TvHtml5Embedded;
import dev.lavalink.youtube.clients.Web;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.aiode.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.audio.spotify.SpotifyService;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.boot.AbstractShutdownable;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.command.widget.widgets.NowPlayingWidget;
import net.robinfriedli.aiode.concurrent.CompletableFutures;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.concurrent.HistoryPool;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.entities.PlaybackHistory;
import net.robinfriedli.aiode.entities.UserPlaybackHistory;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.filebroker.FilebrokerApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides access to all {@link AudioPlayback} for all guilds and methods to retrieve all audio related factories and
 * services, such as the {@link PlayableFactory} or {@link YouTubeService}, or methods to start a playback or
 * join / leave voice channels. Also manages the playback history and creates the {@link NowPlayingWidget}
 */
@Component
public class AudioManager extends AbstractShutdownable {

    private final AudioPlayerManager playerManager;
    private final AudioTrackLoader audioTrackLoader;
    private final FilebrokerApi filebrokerApi;
    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final Logger logger;
    private final YouTubeService youTubeService;

    public AudioManager(
        FilebrokerApi filebrokerApi,
        GuildManager guildManager,
        HibernateComponent hibernateComponent,
        YouTubeService youTubeService,
        @Value("${aiode.preferences.ipv6_blocks:#{null}}") String ipv6Blocks,
        @Value("${aiode.tokens.yt-oauth-refresh-token:#{null}}") String ytOauthRefreshToken,
        @Value("${aiode.tokens.yt-po-token:#{null}}") String ytPoToken,
        @Value("${aiode.tokens.yt-po-visitor-data:#{null}}") String ytPoVisitorData
    ) {
        playerManager = new DefaultAudioPlayerManager();
        audioTrackLoader = new AudioTrackLoader(playerManager);

        this.filebrokerApi = filebrokerApi;
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.logger = LoggerFactory.getLogger(getClass());
        this.youTubeService = youTubeService;

        playerManager.registerSourceManager(new YoutubeAudioSourceManager(new Music(), new Web(), new TvHtml5Embedded()));
        playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        playerManager.registerSourceManager(new BandcampAudioSourceManager());
        playerManager.registerSourceManager(new VimeoAudioSourceManager());
        playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
        playerManager.registerSourceManager(new BeamAudioSourceManager());
        playerManager.registerSourceManager(new GetyarnAudioSourceManager());
        playerManager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
        guildManager.setAudioManager(this);
        YoutubeAudioSourceManager youtubeAudioSourceManager = playerManager.source(YoutubeAudioSourceManager.class);
        // there is 100 videos per page and the maximum playlist size is 5000
        youtubeAudioSourceManager.setPlaylistPageCount(50);
        if (!Strings.isNullOrEmpty(ytOauthRefreshToken)) {
            if ("init".equalsIgnoreCase(ytOauthRefreshToken)) {
                youtubeAudioSourceManager.useOauth2(null, false);
            } else {
                youtubeAudioSourceManager.useOauth2(ytOauthRefreshToken, true);
            }
        }
        if (!Strings.isNullOrEmpty(ytPoToken) && !Strings.isNullOrEmpty(ytPoVisitorData)) {
            Web.setPoTokenAndVisitorData(ytPoToken, ytPoVisitorData);
        }

        if (!Strings.isNullOrEmpty(ipv6Blocks)) {
            // NanoIpRoutePlanner uses raw type
            @SuppressWarnings("rawtypes")
            List<IpBlock> ipv6BlockList = Splitter.on(",")
                .trimResults()
                .omitEmptyStrings()
                .splitToList(ipv6Blocks)
                .stream()
                .map(Ipv6Block::new)
                .collect(Collectors.toList());

            YoutubeIpRotatorSetup youtubeIpRotatorSetup = new YoutubeIpRotatorSetup(new RotatingNanoIpRoutePlanner(ipv6BlockList, ip -> true, true));
            youtubeIpRotatorSetup.forConfiguration(youtubeAudioSourceManager.getHttpInterfaceManager(), false)
                .withMainDelegateFilter(null)
                .setup();
            logger.info("YouTubeIpRotator set up with block: " + ipv6Blocks);
        }
    }

    public void startPlayback(Guild guild, @Nullable AudioChannel channel) {
        playTrack(guild, channel, false);
    }

    public void startOrResumePlayback(Guild guild, @Nullable AudioChannel channel) {
        playTrack(guild, channel, true);
    }

    public void playTrack(Guild guild, @Nullable AudioChannel channel, boolean resumePaused) {
        AudioPlayback playback = getPlaybackForGuild(guild);

        if (!resumePaused && playback.isPaused()) {
            playback.getAudioPlayer().stopTrack();
            playback.unpause();
        }

        if (channel != null) {
            setChannel(playback, channel);
        } else if (playback.getAudioChannel() == null) {
            throw new InvalidCommandException("Not in a voice channel");
        }

        if (ExecutionContext.Current.isSet()) {
            playback.setCommunicationChannel(ExecutionContext.Current.require().getChannel());
        }

        if (playback.isPaused() && resumePaused) {
            playback.unpause();
        } else {
            if (playback.getAudioQueue().isEmpty()) {
                throw new InvalidCommandException("Queue is empty");
            }
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

    public PlayableFactory createPlayableFactory(SpotifyService spotifyService, TrackLoadingExecutor trackLoadingExecutor, boolean shouldRedirectSpotify) {
        return new PlayableFactory(audioTrackLoader, spotifyService, trackLoadingExecutor, youTubeService, shouldRedirectSpotify, filebrokerApi);
    }

    public void setChannel(AudioPlayback audioPlayback, AudioChannel channel) {
        audioPlayback.setVoiceChannel(channel);
        Guild guild = audioPlayback.getGuild();
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(audioPlayback.getAudioPlayer()));
        try {
            guild.getAudioManager().openAudioConnection(channel);
        } catch (InsufficientPermissionException e) {
            throw new InvalidCommandException("I do not have permission to join this voice channel!");
        }
    }

    void createHistoryEntry(Playable playable, Guild guild, AudioChannel audioChannel) {
        HistoryPool.execute(() -> {
            try {
                hibernateComponent.consumeSession(session -> {
                    PlaybackHistory playbackHistory = new PlaybackHistory(LocalDateTime.now(), playable, guild, session);

                    session.persist(playbackHistory);
                    if (audioChannel != null) {
                        Member selfMember = guild.getSelfMember();
                        for (Member member : audioChannel.getMembers()) {
                            if (!member.equals(selfMember)) {
                                UserPlaybackHistory userPlaybackHistory = new UserPlaybackHistory(member.getUser(), playbackHistory);
                                session.persist(userPlaybackHistory);
                            }
                        }
                    }

                });
            } catch (Exception e) {
                logger.error("Exception while creating playback history entry", e);
            }
        });
    }

    void createNowPlayingWidget(CompletableFuture<Message> futureMessage, AudioPlayback playback) {
        Guild guild = playback.getGuild();
        WidgetRegistry widgetRegistry = guildManager.getContextForGuild(guild).getWidgetRegistry();
        CompletableFutures.thenAccept(futureMessage, message -> new NowPlayingWidget(widgetRegistry, guild, message).initialise());
    }

    @Override
    public void shutdown(int delayMs) {
        for (GuildContext guildContext : guildManager.getGuildContexts()) {
            guildContext.getPlayback().stop();
        }
        playerManager.shutdown();
    }
}
