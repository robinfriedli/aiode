package net.robinfriedli.aiode.command.commands.playback;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
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
        Aiode aiode = Aiode.get();
        AudioManager audioManager = aiode.getAudioManager();

        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        AudioPlayer audioPlayer = playback.getAudioPlayer();

        AudioQueue audioQueue = playback.getAudioQueue();

        QueueFragment queueFragment = playableContainer.createQueueFragment(playableFactory, audioQueue);
        if (queueFragment == null) {
            throw new NoResultsFoundException("Result is empty!");
        }

        GuildPropertyManager guildPropertyManager = aiode.getGuildPropertyManager();
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        int autoQueueMode = guildPropertyManager.getPropertyValueOptional("autoQueueMode", Integer.class, specification).orElse(1);

        if (autoQueueMode == 1 && !audioQueue.isEmpty() && !playback.isShuffle()) {
            if (!playback.isPlaying()) {
                int position = audioQueue.getPosition();
                if (playback.isPaused()) {
                    audioQueue.insertNext(queueFragment);
                    audioQueue.iterate();
                } else {
                    audioQueue.insertRelative(-1, queueFragment);
                    // inserting over the current index increments it, reset back to the original index to play the inserted tracks instead
                    audioQueue.setPosition(position);
                }
                audioManager.startPlayback(guild, channel);
            } else {
                audioQueue.insertNext(queueFragment);
            }
            sendSuccessMessage(true);
        } else if ((autoQueueMode == 2 || autoQueueMode == 1) && !audioQueue.isEmpty()) {
            audioQueue.add(queueFragment);
            if (!playback.isPlaying()) {
                audioQueue.iterate();
                audioManager.startPlayback(guild, channel);
            }
            sendSuccessMessage(false);
        } else {
            if (audioPlayer.getPlayingTrack() != null) {
                audioPlayer.stopTrack();
            }
            audioQueue.set(queueFragment);
            audioManager.startPlayback(guild, channel);
        }
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
        AudioChannel channel = getContext().getAudioChannel();

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

        GuildPropertyManager guildPropertyManager = aiode.getGuildPropertyManager();
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        int autoQueueMode = guildPropertyManager.getPropertyValueOptional("autoQueueMode", Integer.class, specification).orElse(1);

        if (autoQueueMode == 1 && !queue.isEmpty() && !playback.isShuffle()) {
            if (!playback.isPlaying()) {
                int position = queue.getPosition();
                if (playback.isPaused()) {
                    loadedAmount = queue.addContainers(playableContainers, playableFactory, false, 0);
                    queue.iterate();
                } else {
                    loadedAmount = queue.addContainers(playableContainers, playableFactory, false, -1);
                    // inserting over the current index increments it, reset back to the original index to play the inserted tracks instead
                    queue.setPosition(position);
                }
                audioManager.startPlayback(guild, channel);
            } else {
                loadedAmount = queue.addContainers(playableContainers, playableFactory, false, 0);
            }
            sendSuccessMessage(true);
        } else if ((autoQueueMode == 2 || autoQueueMode == 1) && !queue.isEmpty()) {
            loadedAmount = queue.addContainers(playableContainers, playableFactory, false, null);
            if (!playback.isPlaying()) {
                queue.iterate();
                audioManager.startPlayback(guild, channel);
            }
            sendSuccessMessage(false);
        } else {
            if (audioPlayer.getPlayingTrack() != null) {
                audioPlayer.stopTrack();
            }
            loadedAmount = queue.addContainers(playableContainers, playableFactory, true, null);
            audioManager.startPlayback(guild, getContext().getAudioChannel());
        }
    }

}
