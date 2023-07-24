package net.robinfriedli.aiode.audio;

import java.time.Duration;
import java.time.LocalDateTime;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.discord.DiscordEntity;
import net.robinfriedli.aiode.function.RateLimitInvoker;
import net.robinfriedli.exec.Mode;

/**
 * There is exactly one AudioPlayback per guild instantiated when initializing the guild. This class holds all information
 * about a guilds playback and its {@link AudioPlayer} and {@link AudioQueue} and is used to pause / stop the playback.
 */
public class AudioPlayback {

    private static final RateLimitInvoker MESSAGE_DELETION_RATE_LIMITED = new RateLimitInvoker("audio_playback_message_deletion", 2, Duration.ofSeconds(1));

    private final DiscordEntity.Guild guild;
    private final AudioPlayer audioPlayer;
    private final AudioQueue audioQueue;
    private final Logger logger;

    private DiscordEntity<? extends AudioChannel> audioChannel;
    private DiscordEntity.MessageChannel communicationChannel;
    private DiscordEntity.Message lastPlaybackNotification;
    private QueueIterator currentQueueIterator;
    private int defaultVolume = 100;

    // time the bot has been alone in the voice channel since
    @Nullable
    private LocalDateTime aloneSince;

    public AudioPlayback(AudioPlayer player, Guild guild) {
        this.guild = new DiscordEntity.Guild(guild);
        audioPlayer = player;
        this.logger = LoggerFactory.getLogger(getClass());
        SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
        Integer queueSizeMax = springPropertiesConfig.getApplicationProperty(Integer.class, "aiode.preferences.queue_size_max");
        audioQueue = new AudioQueue(queueSizeMax);
    }

    public boolean isPlaying() {
        return !isPaused() && audioPlayer.getPlayingTrack() != null;
    }

    public void pause() {
        audioPlayer.setPaused(true);
    }

    public void unpause() {
        audioPlayer.setPaused(false);
    }

    public boolean isPaused() {
        return audioPlayer.isPaused() && audioPlayer.getPlayingTrack() != null;
    }

    public void stop() {
        audioPlayer.stopTrack();
        setLastPlaybackNotification(null);
        leaveChannel();
    }

    public Guild getGuild() {
        return guild.get();
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public AudioQueue getAudioQueue() {
        return audioQueue;
    }

    public AudioChannel getAudioChannel() {
        if (audioChannel != null) {
            return audioChannel.get();
        } else {
            return null;
        }
    }

    public void setVoiceChannel(AudioChannel audioChannel) {
        if (audioChannel instanceof VoiceChannel voiceChannel) {
            this.audioChannel = new DiscordEntity.VoiceChannel(voiceChannel);
        } else if (audioChannel instanceof StageChannel stageChannel) {
            this.audioChannel = new DiscordEntity.StageChannel(stageChannel);
        } else if (audioChannel != null) {
            throw new IllegalArgumentException("Illegal audioChannel type " + audioChannel.getClass());
        } else {
            this.audioChannel = null;
        }
    }

    public MessageChannel getCommunicationChannel() {
        if (communicationChannel != null) {
            return communicationChannel.retrieve();
        } else {
            return null;
        }
    }

    public void setCommunicationChannel(MessageChannel communicationChannel) {
        if (communicationChannel != null) {
            this.communicationChannel = DiscordEntity.MessageChannel.createForMessageChannel(communicationChannel);
        } else {
            this.communicationChannel = null;
        }
    }

    public boolean isRepeatOne() {
        return audioQueue.getRepeatOne();
    }

    public void setRepeatOne(boolean repeatOne) {
        audioQueue.setRepeatOne(repeatOne);
    }

    public boolean isRepeatAll() {
        return audioQueue.getRepeatAll();
    }

    public void setRepeatAll(boolean repeatAll) {
        audioQueue.setRepeatAll(repeatAll);
    }

    public boolean isShuffle() {
        return audioQueue.isShuffle();
    }

    public void setShuffle(boolean isShuffle) {
        audioQueue.setShuffle(isShuffle);
    }

    public long getCurrentPositionMs() {
        AudioTrack playingTrack = audioPlayer.getPlayingTrack();
        return playingTrack != null ? playingTrack.getPosition() : 0;
    }

    public void setPosition(long ms) {
        audioPlayer.getPlayingTrack().setPosition(ms);
    }

    public int getVolume() {
        return audioPlayer.getVolume();
    }

    public void setVolume(int volume) {
        audioPlayer.setVolume(volume);
    }

    public void leaveChannel() {
        guild.get().getAudioManager().closeAudioConnection();
        audioChannel = null;
    }

    /**
     * Clear the queue and reset all options
     *
     * @return true if anything changed
     */
    public boolean clear() {
        boolean changedAnything = false;
        if (!audioQueue.isEmpty()) {
            audioQueue.clear();
            changedAnything = true;
        }
        if (isPaused()) {
            audioPlayer.stopTrack();
            changedAnything = true;
        }
        if (getVolume() != defaultVolume) {
            setVolume(defaultVolume);
            changedAnything = true;
        }
        if (isShuffle()) {
            setShuffle(false);
            changedAnything = true;
        }
        if (isRepeatAll()) {
            setRepeatAll(false);
            changedAnything = true;
        }
        if (isRepeatOne()) {
            setRepeatOne(false);
            changedAnything = true;
        }
        if (audioChannel != null) {
            guild.get().getAudioManager().closeAudioConnection();
            audioChannel = null;
            changedAnything = true;
        }
        if (communicationChannel != null) {
            communicationChannel = null;
            changedAnything = true;
        }


        setLastPlaybackNotification(null);
        return changedAnything;
    }

    public void setLastPlaybackNotification(Message message) {
        if (lastPlaybackNotification != null) {
            DiscordEntity.Message messageToDelete = lastPlaybackNotification;
            MESSAGE_DELETION_RATE_LIMITED.invokeLimited(Mode.create(), () -> {
                MessageChannel messageChannel = messageToDelete.getChannel().retrieve();
                if (messageChannel != null) {
                    try {
                        messageChannel.deleteMessageById(messageToDelete.getId()).queue();
                    } catch (Exception e) {
                        logger.warn(
                            String.format(
                                "Cannot delete playback notification message for channel %s on guild %s",
                                message,
                                guild.getId()
                            ),
                            e
                        );
                    }
                }
            });
        }
        if (message != null) {
            this.lastPlaybackNotification = new DiscordEntity.Message(message);
        } else {
            this.lastPlaybackNotification = null;
        }
    }

    public QueueIterator getCurrentQueueIterator() {
        return currentQueueIterator;
    }

    public void setCurrentQueueIterator(QueueIterator queueIterator) {
        if (currentQueueIterator != null) {
            audioPlayer.removeListener(currentQueueIterator);
            currentQueueIterator.setReplaced();
        }

        currentQueueIterator = queueIterator;
        audioPlayer.addListener(queueIterator);
    }

    @Nullable
    public LocalDateTime getAloneSince() {
        return aloneSince;
    }

    public void setAloneSince(@Nullable LocalDateTime aloneSince) {
        this.aloneSince = aloneSince;
    }

    public int getDefaultVolume() {
        return defaultVolume;
    }

    public void setDefaultVolume(int defaultVolume) {
        // if the current volume matches the default, change current volume as well
        if (this.defaultVolume == getVolume()) {
            setVolume(defaultVolume);
        }
        this.defaultVolume = defaultVolume;
    }
}
