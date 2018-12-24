package net.robinfriedli.botify.audio;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
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
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class AudioManager extends AudioEventAdapter {

    private static final Set<Class> SUPPORTED_PLAYABLES = ImmutableSet.of(Track.class, YouTubeVideo.class);

    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private final YouTubeService youTubeService;
    private List<AudioPlayback> audioPlaybacks = Lists.newArrayList();

    public AudioManager(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
        AudioSourceManagers.registerRemoteSources(playerManager);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason reason) {
        if (reason.mayStartNext) {
            AudioPlayback playback = getPlaybackForPlayer(player);
            AudioQueue audioQueue = playback.getAudioQueue();
            if (audioQueue.hasNext()) {
                audioQueue.next();
                playTrack(playback.getGuild(), playback.getCommunicationChannel(), playback.getVoiceChannel());
            } else {
                audioQueue.reset();
                leaveChannel(playback);
            }
        }
    }

    public void playTrack(Guild guild, MessageChannel communicationChannel, @Nullable VoiceChannel channel) {
        AudioPlayback audioPlayback = getPlaybackForGuild(guild);
        if (channel != null) {
            setChannel(audioPlayback, channel);
        } else if (audioPlayback.getVoiceChannel() == null) {
            throw new InvalidCommandException("Not in a voice channel");
        }

        audioPlayback.setCommunicationChannel(communicationChannel);

        Playable track = audioPlayback.getAudioQueue().getCurrent();
        playerManager.loadItem(track.getPlaybackUrl(), new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                play(audioTrack, channel);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                System.out.println("what");
            }

            @Override
            public void noMatches() {
                System.out.println("No matches");
            }

            @Override
            public void loadFailed(FriendlyException e) {
                e.printStackTrace();
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
            throw new IllegalStateException("No AudioPlayback for guild" + guild);
        }
    }

    public Playable createPlayable(boolean redirectSpotify, Object objectToWrap) {
        if (!SUPPORTED_PLAYABLES.contains(objectToWrap.getClass())) {
            throw new UnsupportedOperationException("Unsupported playable: " + objectToWrap.getClass().getSimpleName());
        }

        if (redirectSpotify && objectToWrap instanceof Track) {
            objectToWrap = youTubeService.redirectSpotify((Track) objectToWrap);
        }

        return new Playable(this, objectToWrap);
    }

    public List<Playable> createPlayables(boolean redirectSpotify, Object... objects) {
        return createPlayables(redirectSpotify, Lists.newArrayList(objects));
    }

    public List<Playable> createPlayables(boolean redirectSpotify, List<?> objects) {
        return objects.stream().map(o -> createPlayable(redirectSpotify, o)).collect(Collectors.toList());
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

    private void play(AudioTrack track, VoiceChannel channel) {
        Guild guild = channel.getGuild();
        AudioPlayback playback = getPlaybackForGuild(guild);
        AudioPlayer audioPlayer = playback.getAudioPlayer();
        audioPlayer.playTrack(track);

        Playable currentTrack = playback.getAudioQueue().getCurrent();
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Now playing ").append(currentTrack.getDisplay());
        if (playback.isPaused()) {
            messageBuilder.append(" (paused)");
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
