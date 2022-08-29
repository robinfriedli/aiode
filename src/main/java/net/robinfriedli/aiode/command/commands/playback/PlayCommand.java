package net.robinfriedli.aiode.command.commands.playback;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.playables.PlayableContainer;
import net.robinfriedli.aiode.audio.playables.PlayableContainerManager;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.audio.queue.QueueFragment;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.commands.AbstractQueueLoadingCommand;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;

public class PlayCommand extends AbstractQueueLoadingCommand {

    public PlayCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(
            commandContribution,
            context,
            commandManager,
            commandString,
            requiresInput,
            identifier,
            description,
            category,
            context.getGuildContext().getReplaceableTrackLoadingExecutor()
        );
    }

    @Override
    public void doRun() throws Exception {
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        AudioChannel channel = context.getAudioChannel();
        AudioManager audioManager = Aiode.get().getAudioManager();
        MessageChannel messageChannel = getContext().getChannel();
        AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(guild);
        playbackForGuild.setCommunicationChannel(messageChannel);

        if (getCommandInput().isBlank()) {
            if (playbackForGuild.isPaused() || !audioManager.getQueue(guild).isEmpty()) {
                audioManager.startOrResumePlayback(guild, channel);
            } else {
                throw new InvalidCommandException("Queue is empty. Specify a song you want to play.");
            }
        } else {
            super.doRun();
        }
    }

    @Override
    protected void handleResult(PlayableContainer<?> playableContainer, PlayableFactory playableFactory) {
        Guild guild = getContext().getGuild();
        AudioChannel channel = getContext().getAudioChannel();
        AudioManager audioManager = Aiode.get().getAudioManager();

        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        AudioPlayer audioPlayer = playback.getAudioPlayer();
        if (audioPlayer.getPlayingTrack() != null) {
            audioPlayer.stopTrack();
        }

        AudioQueue audioQueue = playback.getAudioQueue();

        QueueFragment queueFragment = playableContainer.createQueueFragment(playableFactory, audioQueue);
        if (queueFragment == null) {
            throw new NoResultsFoundException("Result is empty!");
        }

        audioQueue.set(queueFragment);
        audioManager.startPlayback(guild, channel);
    }

    @Override
    public void onSuccess() {
        // current track notification sent by AudioManager
    }

    @Override
    public void withUserResponse(Object option) {
        Aiode aiode = Aiode.get();
        AudioManager audioManager = aiode.getAudioManager();
        PlayableContainerManager playableContainerManager = aiode.getPlayableContainerManager();
        Guild guild = getContext().getGuild();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), getTrackLoadingExecutor(), shouldRedirectSpotify());
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();

        List<PlayableContainer<?>> playableContainers;

        if (option instanceof Collection collection) {
            playableContainers = Lists.newArrayList();
            for (Object o : collection) {
                playableContainers.add(playableContainerManager.requirePlayableContainer(o));
            }
        } else {
            playableContainers = Collections.singletonList(playableContainerManager.requirePlayableContainer(option));
        }

        AudioPlayer audioPlayer = playback.getAudioPlayer();
        if (audioPlayer.getPlayingTrack() != null) {
            audioPlayer.stopTrack();
        }

        queue.addContainers(playableContainers, playableFactory, true);

        audioManager.startPlayback(guild, getContext().getAudioChannel());
    }

}
