package net.robinfriedli.botify.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;

public class AudioPlayback {

    private final Guild guild;
    private final AudioPlayer audioPlayer;
    private final AudioQueue audioQueue;
    private VoiceChannel voiceChannel;
    private MessageChannel communicationChannel;
    private boolean repeatOne;
    private boolean repeatAll;
    // register Track loading Threads here so that they can be interrupted when a different playlist is being played
    private Thread trackLoadingThread;

    public AudioPlayback(AudioPlayer player, Guild guild) {
        this.guild = guild;
        audioPlayer = player;
        audioQueue = new AudioQueue();
    }

    public void pause() {
        audioPlayer.setPaused(true);
    }

    public void unpause() {
        audioPlayer.setPaused(false);
    }

    public boolean isPaused() {
        return audioPlayer.isPaused();
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
        return repeatOne;
    }

    public void setRepeatOne(boolean repeatOne) {
        this.repeatOne = repeatOne;
    }

    public boolean isRepeatAll() {
        return repeatAll;
    }

    public void setRepeatAll(boolean repeatAll) {
        this.repeatAll = repeatAll;
    }

    public boolean isShuffle() {
        return audioQueue.isShuffle();
    }

    public void setShuffle(boolean isShuffle) {
        audioQueue.setShuffle(isShuffle);
    }

    public void registerTrackLoading(Thread thread) {
        if (this.trackLoadingThread != null) {
            interruptTrackLoading();
        }
        this.trackLoadingThread = thread;
    }

    public void interruptTrackLoading() {
        if (trackLoadingThread != null && trackLoadingThread.isAlive()) {
            trackLoadingThread.interrupt();
        }
    }

}
