package net.robinfriedli.botify.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

/**
 * There is exactly one AudioPlayback per guild instantiated when initializing the guild. This class holds all information
 * about a guilds playback and its {@link AudioPlayer} and {@link AudioQueue} and is used to pause / stop the playback.
 */
public class AudioPlayback {

    private final Guild guild;
    private final AudioPlayer audioPlayer;
    private final AudioQueue audioQueue;
    private final Logger logger;
    private VoiceChannel voiceChannel;
    private MessageChannel communicationChannel;
    private Message lastPlaybackNotification;
    private QueueIterator currentQueueIterator;

    public AudioPlayback(AudioPlayer player, Guild guild) {
        this.guild = guild;
        audioPlayer = player;
        this.logger = LoggerFactory.getLogger(getClass());
        audioQueue = new AudioQueue();
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
    }

    public Guild getGuild() {
        return guild;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public AudioQueue getAudioQueue() {
        return audioQueue;
    }

    public VoiceChannel getVoiceChannel() {
        return voiceChannel;
    }

    public void setVoiceChannel(VoiceChannel voiceChannel) {
        this.voiceChannel = voiceChannel;
    }

    public MessageChannel getCommunicationChannel() {
        return communicationChannel;
    }

    public void setCommunicationChannel(MessageChannel communicationChannel) {
        this.communicationChannel = communicationChannel;
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

    public void setLastPlaybackNotification(Message message) {
        if (lastPlaybackNotification != null) {
            try {
                lastPlaybackNotification.delete().queue();
            } catch (Throwable e) {
                logger.warn("Cannot delete playback notification message for channel " + communicationChannel, e);
            }
        }
        this.lastPlaybackNotification = message;
    }

    public QueueIterator getCurrentQueueIterator() {
        return currentQueueIterator;
    }

    public void setCurrentQueueIterator(QueueIterator queueIterator) {
        if (currentQueueIterator != null) {
            audioPlayer.removeListener(currentQueueIterator);
            currentQueueIterator.setReplaced(true);
        }

        currentQueueIterator = queueIterator;
        audioPlayer.addListener(queueIterator);
    }
}
