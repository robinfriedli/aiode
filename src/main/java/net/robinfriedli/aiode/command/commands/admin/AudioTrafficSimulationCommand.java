package net.robinfriedli.aiode.command.commands.admin;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioTrackLoader;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.exec.BlockingTrackLoadingExecutor;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.boot.configurations.JdaComponent;
import net.robinfriedli.aiode.command.AbstractAdminCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.concurrent.QueuedTask;
import net.robinfriedli.aiode.concurrent.ThreadExecutionQueue;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AudioTrafficSimulationCommand extends AbstractAdminCommand {

    private static final int DEFAULT_STREAM_COUNT = 200;
    private static final int DEFAULT_DURATION = 300;
    private static final int DEFAULT_DELAY = 3;
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
        Aiode aiode = Aiode.get();
        AudioManager audioManager = aiode.getAudioManager();
        AudioPlayerManager playerManager = audioManager.getPlayerManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), new BlockingTrackLoadingExecutor(), true);
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        ThreadExecutionQueue threadExecutionQueue = aiode.getExecutionQueueManager().getForGuild(guild);
        AudioTrackLoader audioTrackLoader = new AudioTrackLoader(playerManager);

        int streams = getArgumentValueOrElse("streams", DEFAULT_STREAM_COUNT);
        int durationSecs = getArgumentValueOrElse("duration", DEFAULT_DURATION);
        String playbackUrl = getArgumentValueOrElse("url", DEFAULT_PLAYBACK_URL);
        int delay = getArgumentValueOrElse("delay", DEFAULT_DELAY);
        Integer nativeBuffer = getArgumentValueWithTypeOrElse("nativeBuffer", Integer.class, null);

        if (nativeBuffer != null && !JdaComponent.platformSupportsJdaNas()) {
            throw new InvalidCommandException("Current platform does not support jda-nas, invoke without nativeBuffer argument");
        }

        List<Playable> playables = playableFactory.createPlayableContainerForUrl(playbackUrl).loadPlayables(playableFactory);

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
        List<AudioPlayer> players = Lists.newArrayListWithCapacity(streams);
        List<Thread> playbackThreads = nativeBuffer != null ? null : Lists.newArrayListWithCapacity(streams);
        List<IAudioSendSystem> audioSendSystems = nativeBuffer != null ? Lists.newArrayListWithCapacity(streams) : null;
        NativeAudioSendFactory nativeAudioSendFactory = nativeBuffer != null ? new NativeAudioSendFactory(nativeBuffer) : null;
        LoggingUncaughtExceptionHandler eh = new LoggingUncaughtExceptionHandler();

        for (int i = 0; i < streams; i++) {
            AudioPlayer player = playerManager.createPlayer();
            player.addListener(loopTrackListener);
            players.add(player);

            if (nativeAudioSendFactory != null) {
                IAudioSendSystem sendSystem = nativeAudioSendFactory.createSendSystem(new FakePacketProvider(player));
                audioSendSystems.add(sendSystem);
            } else {
                Thread playbackThread = new Thread(new PlayerPollingRunnable(player));

                playbackThread.setDaemon(true);
                playbackThread.setUncaughtExceptionHandler(eh);
                playbackThread.setName("simulated-playback-thread-" + i);
                playbackThreads.add(playbackThread);
            }
        }

        QueuedTask playbackThreadsManagementTask = new QueuedTask(threadExecutionQueue, new FakePlayerManagementTask(playbackThreads, audioSendSystems, track, players, durationSecs, delay)) {
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

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Nullable
        private final List<Thread> playbackThreads;
        @Nullable
        private final List<IAudioSendSystem> audioSendSystems;

        private final AudioTrack track;
        private final List<AudioPlayer> players;
        private final int durationSecs;
        private final int delay;

        private FakePlayerManagementTask(
            @Nullable List<Thread> playbackThreads,
            @Nullable List<IAudioSendSystem> audioSendSystems,
            AudioTrack track,
            List<AudioPlayer> players,
            int durationSecs,
            int delay
        ) {
            this.playbackThreads = playbackThreads;
            this.audioSendSystems = audioSendSystems;
            this.track = track;
            this.players = players;
            this.durationSecs = durationSecs;
            this.delay = delay;
        }

        @Override
        public void run() {
            if (Thread.interrupted()) {
                return;
            }

            try {
                startPlayers(playbackThreads, Thread::start);
                startPlayers(audioSendSystems, IAudioSendSystem::start);

                Thread.sleep(durationSecs * 1000L);
            } catch (InterruptedException e) {
                // continue to interrupt threads
            } catch (Exception e) {
                logger.error("Unexpected exception while managing fake players", e);
            }

            if (playbackThreads != null) {
                for (Thread playbackThread : playbackThreads) {
                    playbackThread.interrupt();
                }
            }

            if (audioSendSystems != null) {
                for (int i = 0; i < audioSendSystems.size(); i++) {
                    audioSendSystems.get(i).shutdown();
                    players.get(i).stopTrack();
                }
            }
        }

        private <T> void startPlayers(@Nullable List<T> playerSystems, Consumer<T> startAction) throws InterruptedException {
            if (playerSystems != null) {
                for (int i = 0; i < playerSystems.size(); i++) {
                    if (delay > 0) {
                        Thread.sleep(delay * 1000L);
                    }
                    startAction.accept(playerSystems.get(i));
                    AudioTrack track = this.track.makeClone();
                    track.setUserData(i);
                    players.get(i).playTrack(track);
                    logger.info(String.format("Started fake audio player %d", i));
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
