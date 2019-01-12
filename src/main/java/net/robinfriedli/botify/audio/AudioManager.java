package net.robinfriedli.botify.audio;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;

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
import net.robinfriedli.botify.exceptions.InvalidCommandException;
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

        playerManager.loadItem(playbackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                play(audioTrack, audioPlayback);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                throw new UnsupportedOperationException("Expected a track, not a playlist");
            }

            @Override
            public void noMatches() {
                try {
                    audioPlayback.getCommunicationChannel().sendMessage("Audio player could not load " + track.getDisplay()).queue();
                } catch (InterruptedException ignored) {
                }
            }

            @Override
            public void loadFailed(FriendlyException e) {
                String trackDisplay;
                try {
                    trackDisplay = track.getDisplay();
                } catch (InterruptedException e1) {
                    trackDisplay = "";
                }
                audioPlayback
                    .getCommunicationChannel()
                    .sendMessage("Exception while loading " + trackDisplay + ". Message: " + e.getMessage()).queue();
                logger.error("lavaplayer threw an exception while loading track " + trackDisplay, e);
            }
        });
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
        if (redirectSpotify && objectToWrap instanceof Track) {
            HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, (Track) objectToWrap);
            youTubeService.redirectSpotify(youTubeVideo);
            return new Playable(this, youTubeVideo);
        }

        return new Playable(this, objectToWrap);
    }

    public List<Playable> createPlayables(boolean redirectSpotify, List<?> objects, AudioPlayback audioPlayback) {
        List<Playable> createdPlayables = Lists.newArrayList();
        List<HollowYouTubeVideo> tracksToRedirect = Lists.newArrayList();

        for (Object object : objects) {
            if (redirectSpotify && object instanceof Track) {
                HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, (Track) object);
                tracksToRedirect.add(youTubeVideo);
                createdPlayables.add(new Playable(this, youTubeVideo));
            } else {
                createdPlayables.add(new Playable(this, object));
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
            audioPlayback.registerTrackLoading(trackRedirectingThread);
            trackRedirectingThread.start();
        }
        return createdPlayables;
    }

    public List<Playable> createPlayables(YouTubePlaylist youTubePlaylist, AudioPlayback audioPlayback) {
        List<Playable> playables = Lists.newArrayList();

        for (HollowYouTubeVideo video : youTubePlaylist.getVideos()) {
            playables.add(new Playable(this, video));
        }

        Thread videoLoadingThread = new Thread(() -> youTubeService.populateList(youTubePlaylist));
        videoLoadingThread.setName("Botify Video Loading " + youTubePlaylist.toString());
        TrackLoadingExceptionHandler eh = new TrackLoadingExceptionHandler(logger, audioPlayback.getCommunicationChannel(), youTubePlaylist);
        videoLoadingThread.setUncaughtExceptionHandler(eh);
        audioPlayback.registerTrackLoading(videoLoadingThread);
        videoLoadingThread.start();

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
        guild.getAudioManager().openAudioConnection(channel);
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
        playback.getCommunicationChannel().sendMessage(messageBuilder.toString()).queue();
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
