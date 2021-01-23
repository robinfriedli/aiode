package net.robinfriedli.botify.command.commands.admin;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.wrapper.spotify.SpotifyApi;
import groovy.lang.Tuple2;
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioTrackLoader;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.exec.BlockingTrackLoadingExecutor;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.QueuedTask;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AudioTrafficSimulationCommand extends AbstractAdminCommand {

    private static final int DEFAULT_STREAM_COUNT = 200;
    private static final int DEFAULT_DURATION = 300;
    private static final String DEFAULT_PLAYBACK_URL = "https://www.youtube.com/watch?v=kXYiU_JCYtU";

    public AudioTrafficSimulationCommand(
        CommandContribution commandContribution,
        CommandContext context,
        CommandManager commandManager,
        String commandString,
        boolean requiresInput,
        String identifier,
        String description,
        Category category
    ) {
        super(
            commandContribution,
            context,
            commandManager,
            commandString,
            requiresInput,
            identifier,
            description,
            category
        );
    }

    @Override
    public void runAdmin() throws Exception {
        Botify botify = Botify.get();
        AudioManager audioManager = botify.getAudioManager();
        AudioPlayerManager playerManager = audioManager.getPlayerManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), new BlockingTrackLoadingExecutor());
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        ThreadExecutionQueue threadExecutionQueue = botify.getExecutionQueueManager().getForGuild(guild);
        SpotifyApi spotifyApi = context.getSpotifyApi();
        AudioTrackLoader audioTrackLoader = new AudioTrackLoader(playerManager);

        int streams = getArgumentValueOrElse("streams", DEFAULT_STREAM_COUNT);
        int durationSecs = getArgumentValueOrElse("duration", DEFAULT_DURATION);
        String playbackUrl = getArgumentValueOrElse("url", DEFAULT_PLAYBACK_URL);
        Integer nativeBuffer = getArgumentValueWithTypeOrElse("nativeBuffer", Integer.class, null);

        List<Playable> playables = playableFactory.createPlayables(playbackUrl, spotifyApi, true);

        if (playables.isEmpty()) {
            throw new InvalidCommandException("No playables found for url " + playbackUrl);
        }

        Playable playable = playables.get(0);
        AudioItem audioItem = audioTrackLoader.loadByIdentifier(playable.getPlaybackUrl());
        AudioTrack track;
        if (audioItem instanceof AudioTrack) {
            track = (AudioTrack) audioItem;
        } else {
            throw new IllegalStateException("Could not get AudioTrack for Playable " + playable);
        }

        LoopTrackListener loopTrackListener = new LoopTrackListener(track);
        List<Thread> playbackThreads = nativeBuffer != null ? null : Lists.newArrayListWithCapacity(streams);
        List<Tuple2<IAudioSendSystem, AudioPlayer>> audioSendSystems = nativeBuffer != null ? Lists.newArrayListWithCapacity(streams) : null;
        NativeAudioSendFactory nativeAudioSendFactory = nativeBuffer != null ? new NativeAudioSendFactory(nativeBuffer) : null;
        LoggingUncaughtExceptionHandler eh = new LoggingUncaughtExceptionHandler();

        for (int i = 0; i < streams; i++) {
            AudioPlayer player = playerManager.createPlayer();
            player.addListener(loopTrackListener);
            player.playTrack(track.makeClone());

            if (nativeAudioSendFactory != null) {
                IAudioSendSystem sendSystem = nativeAudioSendFactory.createSendSystem(new FakePacketProvider(player));
                audioSendSystems.add(new Tuple2<>(sendSystem, player));
            } else {
                Thread playbackThread = new Thread(new PlayerPollingRunnable(player));

                playbackThread.setDaemon(true);
                playbackThread.setUncaughtExceptionHandler(eh);
                playbackThread.setName("simulated-playback-thread-" + i);
                playbackThreads.add(playbackThread);
            }
        }

        QueuedTask playbackThreadsManagementTask = new QueuedTask(threadExecutionQueue, new FakePlayerManagementTask(playbackThreads, audioSendSystems, durationSecs)) {
            @Override
            protected boolean isPrivileged() {
                return true;
            }
        };

        playbackThreadsManagementTask.setName("simulated-playback-management-task");

        threadExecutionQueue.add(playbackThreadsManagementTask, false);
    }

    @Override
    public void onSuccess() {
        sendSuccess("Starting fake playbacks");
    }

    private static class LoopTrackListener extends AudioEventAdapter {

        private final AudioTrack audioTrack;

        private LoopTrackListener(AudioTrack audioTrack) {
            this.audioTrack = audioTrack;
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            if (endReason.mayStartNext) {
                player.playTrack(audioTrack.makeClone());
            }
        }
    }

    private static class FakePlayerManagementTask implements Runnable {

        @Nullable
        private final List<Thread> playbackThreads;
        @Nullable
        private final List<Tuple2<IAudioSendSystem, AudioPlayer>> audioSendSystems;

        private final int durationSecs;

        private FakePlayerManagementTask(@Nullable List<Thread> playbackThreads, @Nullable List<Tuple2<IAudioSendSystem, AudioPlayer>> audioSendSystems, int durationSecs) {
            this.playbackThreads = playbackThreads;
            this.audioSendSystems = audioSendSystems;
            this.durationSecs = durationSecs;
        }

        @Override
        public void run() {
            if (Thread.interrupted()) {
                return;
            }

            if (playbackThreads != null) {
                for (Thread playbackThread : playbackThreads) {
                    playbackThread.start();
                }
            }

            if (audioSendSystems != null) {
                for (Tuple2<IAudioSendSystem, AudioPlayer> audioSendSystemTuple : audioSendSystems) {
                    audioSendSystemTuple.getFirst().start();
                }
            }

            try {
                Thread.sleep(durationSecs * 1000L);
            } catch (InterruptedException e) {
                // continue to interrupt threads
            }

            if (playbackThreads != null) {
                for (Thread playbackThread : playbackThreads) {
                    playbackThread.interrupt();
                }
            }

            if (audioSendSystems != null) {
                for (Tuple2<IAudioSendSystem, AudioPlayer> audioSendSystemTuple : audioSendSystems) {
                    audioSendSystemTuple.getFirst().shutdown();
                    audioSendSystemTuple.getSecond().stopTrack();
                }
            }
        }
    }

    private static class PlayerPollingRunnable implements Runnable {

        private final AudioPlayer player;

        private PlayerPollingRunnable(AudioPlayer player) {
            this.player = player;
        }

        @Override
        public void run() {
            while (true) {
                if (Thread.interrupted()) {
                    player.stopTrack();
                    return;
                }

                player.provide();

                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    player.stopTrack();
                    return;
                }
            }
        }
    }

    // only implement methods required by jda-nas
    private static class FakePacketProvider implements IPacketProvider {

        private final AudioPlayer audioPlayer;
        private final InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        private FakePacketProvider(AudioPlayer audioPlayer) {
            this.audioPlayer = audioPlayer;
        }

        @NotNull
        @Override
        public String getIdentifier() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public VoiceChannel getConnectedChannel() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public DatagramSocket getUdpSocket() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public InetSocketAddress getSocketAddress() {
            return socketAddress;
        }

        @Nullable
        @Override
        public ByteBuffer getNextPacketRaw(boolean changeTalking) {
            AudioFrame frame = audioPlayer.provide();

            if (frame != null) {
                return ByteBuffer.wrap(frame.getData());
            }

            return null;
        }

        @Nullable
        @Override
        public DatagramPacket getNextPacket(boolean changeTalking) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onConnectionError(@NotNull ConnectionStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onConnectionLost() {
            throw new UnsupportedOperationException();
        }
    }

}
