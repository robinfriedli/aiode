package net.robinfriedli.aiode.command.commands.playback;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
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
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.command.widget.widgets.QueueWidget;
import net.robinfriedli.aiode.concurrent.CompletableFutures;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;

public class QueueCommand extends AbstractQueueLoadingCommand {

    private int removedTracks;

    public QueueCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(
            commandContribution,
            context,
            commandManager,
            commandString,
            requiresInput,
            identifier,
            description,
            category,
            context.getGuildContext().getPooledTrackLoadingExecutor()
        );
    }

    @Override
    public void doRun() throws Exception {
        if (getCommandInput().isBlank()) {
            listQueue();
        } else if (argumentSet("remove")) {
            AudioQueue audioQueue = getContext().getGuildContext().getPlayback().getAudioQueue();
            String commandInput = getCommandInput();
            int fromIdx;
            int endIdx;
            try {
                if (commandInput.contains("-")) {
                    String[] split = commandInput.split("\\s*-\\s*");
                    if (split.length != 2) {
                        throw new InvalidCommandException("Index range is invalid");
                    }
                    fromIdx = Integer.parseInt(split[0]);
                    endIdx = Integer.parseInt(split[1]);
                } else {
                    fromIdx = Integer.parseInt(commandInput);
                    endIdx = fromIdx;
                }
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("Index range is invalid");
            }
            if (endIdx < fromIdx) {
                throw new InvalidCommandException("End index must be greater than or equal to from index");
            }
            try {
                // convert from 1 to 0 based indexing, endIdx can be left as is because it is converted from an including to excluding index
                removedTracks = audioQueue.removeRelative(fromIdx - 1, endIdx);
            } catch (IndexOutOfBoundsException e) {
                throw new InvalidCommandException(e.getMessage(), e);
            }
        } else {
            super.doRun();
        }
    }

    @Override
    protected void handleResult(PlayableContainer<?> playableContainer, PlayableFactory playableFactory) {
        AudioQueue audioQueue = getContext().getGuildContext().getPlayback().getAudioQueue();
        QueueFragment queueFragment = playableContainer.createQueueFragment(playableFactory, audioQueue);
        if (queueFragment == null) {
            throw new NoResultsFoundException("Result is empty!");
        }

        if (argumentSet("insert")) {
            Integer idx = getArgumentValueWithTypeOrElse("at", Integer.class, null);
            try {
                if (idx == null || argumentSet("next")) {
                    audioQueue.insertNext(queueFragment);
                } else {
                    audioQueue.insertRelative(idx - 1, queueFragment);
                }
            } catch (IndexOutOfBoundsException e) {
                throw new InvalidCommandException(e.getMessage(), e);
            }
        } else {
            audioQueue.add(queueFragment);
        }
    }

    private void listQueue() {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = Aiode.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue audioQueue = playback.getAudioQueue();

        CompletableFuture<Message> futureMessage = sendMessage(audioQueue.buildMessageEmbed(playback, guild));
        WidgetRegistry widgetRegistry = getContext().getGuildContext().getWidgetRegistry();
        CompletableFutures.thenAccept(futureMessage, message -> new QueueWidget(widgetRegistry, guild, message, playback).initialise());
    }

    @Override
    public void onSuccess() {
        sendSuccessMessage(false);
    }

    @Override
    protected void sendSuccessMessage(boolean playingNext) {
        super.sendSuccessMessage(playingNext);
        if (removedTracks > 0) {
            sendSuccess(String.format("Removed %d tracks from queue%s", removedTracks, (playingNext ? " to play next" : "")));
        }
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        Aiode aiode = Aiode.get();
        AudioManager audioManager = aiode.getAudioManager();
        PlayableContainerManager playableContainerManager = aiode.getPlayableContainerManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), getTrackLoadingExecutor(), shouldRedirectSpotify());
        AudioQueue queue = audioManager.getQueue(getContext().getGuild());

        List<PlayableContainer<?>> playableContainers;
        if (chosenOption instanceof Collection collection) {
            playableContainers = Lists.newArrayList();
            for (Object o : collection) {
                playableContainers.add(playableContainerManager.requirePlayableContainer(o));
            }
        } else {
            playableContainers = Collections.singletonList(playableContainerManager.requirePlayableContainer(chosenOption));
        }

        Lock writeLock = queue.getLock().writeLock();
        writeLock.lock();
        try {
            Integer insertionIdx;
            if (argumentSet("insert")) {
                Integer idx = getArgumentValueWithTypeOrElse("at", Integer.class, null);
                try {
                    if (idx == null || argumentSet("next")) {
                        insertionIdx = 0;
                    } else {
                        insertionIdx = idx - 1;
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new InvalidCommandException(e.getMessage(), e);
                }
            } else {
                insertionIdx = null;
            }

            int prevSize = queue.getSize();
            loadedAmount = queue.addContainersLocked(playableContainers, playableFactory, false, insertionIdx);
        } finally {
            writeLock.unlock();
        }
    }

}
