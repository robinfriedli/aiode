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
    private TrackLoadingThread trackLoading;

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

    public void registerTrackLoading(TrackLoadingThread trackLoading) {
        if (this.trackLoading != null) {
            if (this.trackLoading.mayInterrupt()) {
                interruptTrackLoading();
            }
        }
        this.trackLoading = trackLoading;
    }

    public void interruptTrackLoading() {
        if (trackLoading != null && trackLoading.isAlive()) {
            trackLoading.interrupt();
        }
    }

    public void awaitLoaded() throws InterruptedException {
        if (trackLoading != null) {
            trackLoading.join();
        } else {
            System.out.println("bla");
        }
    }

    public static class TrackLoadingThread {

        private final Thread thread;
        private final boolean mayInterrupt;

        public TrackLoadingThread(Thread thread, boolean mayInterrupt) {
            this.thread = thread;
            this.mayInterrupt = mayInterrupt;
        }

        public void start() {
            thread.start();
        }

        public boolean isAlive() {
            return thread.isAlive();
        }

        public void interrupt() {
            if (mayInterrupt) {
                thread.interrupt();
            }
        }

        public void join() throws InterruptedException {
            thread.join();
        }

        public boolean mayInterrupt() {
            return mayInterrupt;
        }

        public Thread getThread() {
            return thread;
        }
    }

}
