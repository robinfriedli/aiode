package net.robinfriedli.botify.audio;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.exceptions.TrackLoadingExceptionHandler;

public class AudioManager extends AudioEventAdapter {

    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private final YouTubeService youTubeService;
    private final Logger logger;
    private List<AudioPlayback> audioPlaybacks = Lists.newArrayList();

    public AudioManager(YouTubeService youTubeService, Logger logger) {
        this.youTubeService = youTubeService;
        this.logger = logger;
        AudioSourceManagers.registerRemoteSources(playerManager);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason reason) {
        if (reason.mayStartNext) {
            AudioPlayback playback = getPlaybackForPlayer(player);
            AudioQueue audioQueue = playback.getAudioQueue();
            iterateQueue(playback, audioQueue);
        }
    }

    public void playTrack(Guild guild, @Nullable VoiceChannel channel) {
        AudioPlayback audioPlayback = getPlaybackForGuild(guild);
        if (channel != null) {
            setChannel(audioPlayback, channel);
        } else if (audioPlayback.getVoiceChannel() == null) {
            throw new InvalidCommandException("Not in a voice channel");
        }

        AudioQueue audioQueue = audioPlayback.getAudioQueue();
        Playable track = audioQueue.getCurrent();
        String playbackUrl;
        try {
            playbackUrl = track.getPlaybackUrl();
        } catch (InterruptedException e) {
            iterateQueue(audioPlayback, audioQueue);
            return;
        }

        Object audioTrack = loadUrl(playbackUrl);
        if (audioTrack != null) {
            if (audioTrack instanceof AudioTrack) {
                play((AudioTrack) audioTrack, audioPlayback);
            } else {
                throw new UnsupportedOperationException("Expected an AudioTrack");
            }
        } else {
            iterateQueue(audioPlayback, audioQueue);
        }
    }

    public AudioPlayback getPlaybackForGuild(Guild guild) {
        List<AudioPlayback> found = audioPlaybacks.stream().filter(p -> p.getGuild().equals(guild)).collect(Collectors.toList());

        if (found.size() == 1) {
            return found.get(0);
        } else if (found.size() > 1) {
            throw new IllegalStateException("Several playbacks for guild " + guild);
        } else {
            throw new IllegalStateException("No AudioPlayback for guild " + guild);
        }
    }

    public Playable createPlayable(boolean redirectSpotify, Object objectToWrap) {
        if (objectToWrap instanceof Playable) {
            return (Playable) objectToWrap;
        }

        if (redirectSpotify && objectToWrap instanceof Track) {
            HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, (Track) objectToWrap);
            youTubeService.redirectSpotify(youTubeVideo);
            return new PlayableImpl(youTubeVideo);
        }

        if (objectToWrap instanceof UrlTrack) {
            return ((UrlTrack) objectToWrap).asPlayable();
        }

        return new PlayableImpl(objectToWrap);
    }

    public List<Playable> createPlayables(boolean redirectSpotify, List<?> objects, AudioPlayback audioPlayback) {
        return createPlayables(redirectSpotify, objects, audioPlayback, true);
    }

    public List<Playable> createPlayables(boolean redirectSpotify, List<?> objects, AudioPlayback audioPlayback, boolean mayInterrupt) {
        List<Playable> createdPlayables = Lists.newArrayList();
        List<HollowYouTubeVideo> tracksToRedirect = Lists.newArrayList();

        for (Object object : objects) {
            if (object instanceof Playable) {
                createdPlayables.add((Playable) object);
            } else if (redirectSpotify && object instanceof Track) {
                HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, (Track) object);
                tracksToRedirect.add(youTubeVideo);
                createdPlayables.add(new PlayableImpl(youTubeVideo));
            } else if (object instanceof UrlTrack) {
                createdPlayables.add(((UrlTrack) object).asPlayable());
            } else {
                createdPlayables.add(new PlayableImpl(object));
            }
        }

        if (!tracksToRedirect.isEmpty()) {
            Thread trackRedirectingThread = new Thread(() -> {
                for (HollowYouTubeVideo hollowYouTubeVideo : tracksToRedirect) {
                    if (Thread.currentThread().isInterrupted()) {
                        tracksToRedirect.stream().filter(HollowYouTubeVideo::isHollow).forEach(HollowYouTubeVideo::cancel);
                        break;
                    }
                    youTubeService.redirectSpotify(hollowYouTubeVideo);
                }
            });
            trackRedirectingThread.setName("Botify Redirecting Spotify thread: " + objects.size() + " tracks");
            TrackLoadingExceptionHandler eh = new TrackLoadingExceptionHandler(logger, audioPlayback.getCommunicationChannel(), null);
            trackRedirectingThread.setUncaughtExceptionHandler(eh);

            AudioPlayback.TrackLoadingThread loadingThread = new AudioPlayback.TrackLoadingThread(trackRedirectingThread, mayInterrupt);
            audioPlayback.registerTrackLoading(loadingThread);
            loadingThread.start();
        }
        return createdPlayables;
    }

    public List<Playable> createPlayables(YouTubePlaylist youTubePlaylist, AudioPlayback audioPlayback) {
        return createPlayables(youTubePlaylist, audioPlayback, true);
    }

    public List<Playable> createPlayables(YouTubePlaylist youTubePlaylist, AudioPlayback audioPlayback, boolean mayInterrupt) {
        List<Playable> playables = Lists.newArrayList();

        for (HollowYouTubeVideo video : youTubePlaylist.getVideos()) {
            playables.add(new PlayableImpl(video));
        }

        Thread videoLoadingThread = new Thread(() -> youTubeService.populateList(youTubePlaylist));
        videoLoadingThread.setName("Botify Video Loading " + youTubePlaylist.toString());
        TrackLoadingExceptionHandler eh = new TrackLoadingExceptionHandler(logger, audioPlayback.getCommunicationChannel(), youTubePlaylist);
        videoLoadingThread.setUncaughtExceptionHandler(eh);

        AudioPlayback.TrackLoadingThread loadingThread = new AudioPlayback.TrackLoadingThread(videoLoadingThread, mayInterrupt);
        audioPlayback.registerTrackLoading(loadingThread);
        loadingThread.start();

        return playables;
    }

    public List<Playable> createPlayables(String url, AudioPlayback playback) {
        return createPlayables(url, playback, true);
    }

    public List<Playable> createPlayables(String url, AudioPlayback playback, boolean mayInterrupt) {
        List<Playable> playables;

        URI uri = URI.create(url);
        if (uri.getHost().contains("youtube.com")) {
            List<NameValuePair> parameters = URLEncodedUtils.parse(uri, Charset.forName("UTF-8"));
            Map<String, String> parameterMap = new HashMap<>();
            parameters.forEach(param -> parameterMap.put(param.getName(), param.getValue()));
            String videoId = parameterMap.get("v");
            String playlistId = parameterMap.get("list");
            if (videoId != null) {
                YouTubeVideo youTubeVideo = youTubeService.videoForId(videoId);
                playables = Lists.newArrayList(createPlayable(false, youTubeVideo));
            } else if (playlistId != null) {
                YouTubePlaylist youTubePlaylist = youTubeService.playlistForId(playlistId);
                playables = createPlayables(youTubePlaylist, playback, mayInterrupt);
            } else {
                throw new InvalidCommandException("Detected YouTube URL but no video or playlist id provided.");
            }
        } else {
            Object result = loadUrl(uri.toString());

            if (result == null) {
                throw new NoResultsFoundException("Could not load audio for provided URL");
            }

            if (result instanceof AudioTrack) {
                playables = Lists.newArrayList(new UrlPlayable((AudioTrack) result));
            } else if (result instanceof AudioPlaylist) {
                AudioPlaylist playlist = (AudioPlaylist) result;
                playables = Lists.newArrayList();
                for (AudioTrack track : playlist.getTracks()) {
                    playables.add(new UrlPlayable(track));
                }
            } else {
                throw new UnsupportedOperationException("Expected an AudioTrack or AudioPlaylist but got " + result.getClass().getSimpleName());
            }
        }

        return playables;
    }

    public AudioQueue getQueue(Guild guild) {
        return getPlaybackForGuild(guild).getAudioQueue();
    }

    public void addGuild(Guild guild) {
        initializeGuild(guild);
    }

    public void removeGuild(Guild guild) {
        AudioPlayback playbackForGuild = getPlaybackForGuild(guild);
        audioPlaybacks.remove(playbackForGuild);
    }

    public YouTubeService getYouTubeService() {
        return youTubeService;
    }

    public void leaveChannel(AudioPlayback playback) {
        playback.getGuild().getAudioManager().closeAudioConnection();
        playback.setVoiceChannel(null);
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

    private void play(AudioTrack track, AudioPlayback playback) {
        AudioPlayer audioPlayer = playback.getAudioPlayer();
        audioPlayer.playTrack(track);

        Playable currentTrack = playback.getAudioQueue().getCurrent();
        StringBuilder messageBuilder = new StringBuilder();
        try {
            messageBuilder.append("Now playing ").append(currentTrack.getDisplay());
        } catch (InterruptedException e) {
            // never reached since the track has been loaded, this it had been completed
            return;
        }
        if (playback.isPaused()) {
            messageBuilder.append(" (paused)");
        }
        if (playback.isRepeatAll()) {
            messageBuilder.append(" (repeat all)");
        }
        if (playback.isRepeatOne()) {
            messageBuilder.append(" (repeat one)");
        }
        if (playback.isShuffle()) {
            messageBuilder.append(" (shuffle)");
        }

        AlertService alertService = new AlertService(logger);
        alertService.send(messageBuilder.toString(), playback.getCommunicationChannel());
    }

    private void iterateQueue(AudioPlayback playback, AudioQueue audioQueue) {
        if (!playback.isRepeatOne()) {
            if (audioQueue.hasNext()) {
                audioQueue.next();
                playTrack(playback.getGuild(), playback.getVoiceChannel());
            } else {
                audioQueue.reset();
                if (!playback.isRepeatAll()) {
                    leaveChannel(playback);
                } else {
                    playTrack(playback.getGuild(), playback.getVoiceChannel());
                }
            }
        } else {
            playTrack(playback.getGuild(), playback.getVoiceChannel());
        }
    }

    @Nullable
    private Object loadUrl(String playbackUrl) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        playerManager.loadItem(playbackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                result.complete(audioTrack);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                result.complete(audioPlaylist);
            }

            @Override
            public void noMatches() {
                result.cancel(false);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                result.cancel(false);
                if (e.severity != FriendlyException.Severity.COMMON) {
                    logger.error("lavaplayer threw an exception while loading track " + playbackUrl, e);
                }
            }
        });

        try {
            return result.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException | CancellationException e) {
            return null;
        }
    }

    private void initializeGuild(Guild guild) {
        AudioPlayer player = playerManager.createPlayer();
        player.addListener(this);
        audioPlaybacks.add(new AudioPlayback(player, guild));
    }

    private AudioPlayback getPlaybackForPlayer(AudioPlayer player) {
        List<AudioPlayback> found = audioPlaybacks.stream().filter(p -> p.getAudioPlayer().equals(player)).collect(Collectors.toList());

        if (found.size() == 1) {
            return found.get(0);
        } else if (found.size() > 1) {
            throw new IllegalStateException("Several playbacks for player " + player);
        } else {
            throw new IllegalStateException("No AudioPlayback for player" + player);
        }
    }

}
