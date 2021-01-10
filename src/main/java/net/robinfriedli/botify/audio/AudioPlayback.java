package net.robinfriedli.botify.audio;

import java.time.LocalDateTime;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.discord.DiscordEntity;

/**
 * There is exactly one AudioPlayback per guild instantiated when initializing the guild. This class holds all information
 * about a guilds playback and its {@link AudioPlayer} and {@link AudioQueue} and is used to pause / stop the playback.
 */
public class AudioPlayback {

    private final DiscordEntity.Guild guild;
    private final AudioPlayer audioPlayer;
    private final AudioQueue audioQueue;
    private final Logger logger;
    private DiscordEntity.VoiceChannel voiceChannel;
    private DiscordEntity.MessageChannel communicationChannel;
    private DiscordEntity.Message lastPlaybackNotification;
    private QueueIterator currentQueueIterator;

    // time the bot has been alone in the voice channel since
    @Nullable
    private LocalDateTime aloneSince;

    public AudioPlayback(AudioPlayer player, Guild guild) {
        this.guild = new DiscordEntity.Guild(guild);
        audioPlayer = player;
        this.logger = LoggerFactory.getLogger(getClass());
        SpringPropertiesConfig springPropertiesConfig = Botify.get().getSpringPropertiesConfig();
        Integer queueSizeMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.queue_size_max");
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

    public VoiceChannel getVoiceChannel() {
        if (voiceChannel != null) {
            return voiceChannel.get();
        } else {
            return null;
        }
    }

    public void setVoiceChannel(VoiceChannel voiceChannel) {
        if (voiceChannel != null) {
            this.voiceChannel = new DiscordEntity.VoiceChannel(voiceChannel);
        } else {
            this.voiceChannel = null;
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
        return audioQueue.isRepeatOne();
    }

    public void setRepeatOne(boolean repeatOne) {
        audioQueue.setRepeatOne(repeatOne);
    }

    public boolean isRepeatAll() {
        return audioQueue.isRepeatAll();
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
        voiceChannel = null;
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
        if (getVolume() != 100) {
            setVolume(100);
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
        if (voiceChannel != null) {
            guild.get().getAudioManager().closeAudioConnection();
            voiceChannel = null;
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
            MessageChannel messageChannel = lastPlaybackNotification.getChannel().retrieve();
            if (messageChannel != null) {
                try {
                    messageChannel.deleteMessageById(lastPlaybackNotification.getId()).queue();
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
}
