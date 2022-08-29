package net.robinfriedli.aiode.command.commands.playback;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.base.Strings;
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
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;

public class QueueCommand extends AbstractQueueLoadingCommand {

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

        audioQueue.add(queueFragment);
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
        if (loadedTrack != null) {
            sendSuccess("Queued " + loadedTrack.display());
        }
        if (loadedLocalList != null) {
            sendSuccess(String.format("Queued playlist '%s'", loadedLocalList.getName()));
        }
        if (loadedSpotifyPlaylist != null) {
            sendSuccess(String.format("Queued playlist '%s'", loadedSpotifyPlaylist.getName()));
        }
        if (loadedYouTubePlaylist != null) {
            sendSuccess(String.format("Queued playlist '%s'", loadedYouTubePlaylist.getTitle()));
        }
        if (loadedAlbum != null) {
            sendSuccess(String.format("Queued album '%s'", loadedAlbum.getName()));
        }
        if (loadedAmount > 0) {
            sendSuccess(String.format("Queued %d item%s", loadedAmount, loadedAmount == 1 ? "" : "s"));
        }
        if (loadedAudioTrack != null) {
            sendSuccess("Queued track " + loadedAudioTrack.getInfo().title);
        }
        if (loadedAudioPlaylist != null) {
            String name = loadedAudioPlaylist.getName();
            if (!Strings.isNullOrEmpty(name)) {
                sendSuccess("Queued playlist " + name);
            } else {
                int size = loadedAudioPlaylist.getTracks().size();
                sendSuccess(String.format("Queued %d item%s", size, size == 1 ? "" : "s"));
            }
        }
        if (loadedShow != null) {
            String name = loadedShow.getName();
            sendSuccess("Queued podcast " + name);
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

        int prevSize = queue.getSize();
        queue.addContainers(playableContainers, playableFactory, false);
        loadedAmount = queue.getSize() - prevSize;
    }

}
