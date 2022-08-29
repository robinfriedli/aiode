package net.robinfriedli.aiode.command.commands.playlistmanagement;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.exec.BlockingTrackLoadingExecutor;
import net.robinfriedli.aiode.audio.playables.PlayableContainer;
import net.robinfriedli.aiode.audio.playables.PlayableContainerManager;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.commands.AbstractPlayableLoadingCommand;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import net.robinfriedli.aiode.util.SearchEngine;
import org.hibernate.Session;

public class AddCommand extends AbstractPlayableLoadingCommand {

    private final String definingArgument;
    private Playlist playlist;

    public AddCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        this(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category, "to");
    }

    public AddCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category, String definingArgument) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category, new BlockingTrackLoadingExecutor());
        this.definingArgument = definingArgument;
    }

    @Override
    public void doRun() throws Exception {
        Session session = getContext().getSession();
        if (argumentSet("queue")) {
            AudioQueue queue = Aiode.get().getAudioManager().getQueue(getContext().getGuild());
            if (queue.isEmpty()) {
                throw new InvalidCommandException("Queue is empty");
            }

            Playlist playlist = SearchEngine.searchLocalList(session, getToAddString());
            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", getToAddString()));
            }

            List<Playable> tracks = queue.getTracks();
            addPlayables(playlist, tracks);
        } else {
            if (!argumentSet(definingArgument)) {
                throw new InvalidCommandException(String.format("Expected argument '%s', defining the target playlist. " +
                    "Hint: if you meant to add tracks to the queue, use the queue command instead.", definingArgument));
            }

            String playlistName = getArgumentValue(definingArgument);
            playlist = SearchEngine.searchLocalList(session, playlistName);

            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", playlistName));
            }

            super.doRun();
        }
    }

    @Override
    protected void handleResult(PlayableContainer<?> playableContainer, PlayableFactory playableFactory) {
        addPlayables(playlist, playableContainer.loadPlayables(playableFactory));
    }

    @Override
    protected boolean shouldRedirectSpotify() {
        return false;
    }

    protected void addToList(Playlist playlist, List<PlaylistItem> items) {
        if (items.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        Session session = getContext().getSession();
        invoke(() -> items.forEach(item -> {
            item.add();
            session.persist(item);
        }));
    }

    private void addPlayables(Playlist playlist, List<Playable> playables) {
        if ((getTask() != null && getTask().isTerminated()) || Thread.currentThread().isInterrupted()) {
            return;
        }
        Session session = getContext().getSession();
        List<PlaylistItem> items = Lists.newArrayList();
        invoke(() -> {
            playables.forEach(playable -> {
                if (playable instanceof HollowYouTubeVideo && ((HollowYouTubeVideo) playable).isCanceled()) {
                    return;
                }

                items.add(playable.export(playlist, getContext().getUser(), session));
            });
            addToList(playlist, items);
        });
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }

    @Override
    public void withUserResponse(Object option) {
        Session session = getContext().getSession();
        String playlistName = getArgumentValue(definingArgument);

        Playlist playlist = SearchEngine.searchLocalList(session, playlistName);
        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local list found for '%s'", getToAddString()));
        }

        Aiode aiode = Aiode.get();
        PlayableContainerManager playableContainerManager = aiode.getPlayableContainerManager();
        PlayableFactory playableFactory = aiode.getAudioManager().createPlayableFactory(getSpotifyService(), new BlockingTrackLoadingExecutor(), false);

        List<PlayableContainer<?>> playableContainers;
        if (option instanceof Collection collection) {
            playableContainers = Lists.newArrayList();
            for (Object o : collection) {
                playableContainers.add(playableContainerManager.requirePlayableContainer(o));
            }
        } else {
            playableContainers = Collections.singletonList(playableContainerManager.requirePlayableContainer(option));
        }

        addPlayables(playlist, playableFactory.loadAll(playableContainers));
    }

    protected String getToAddString() {
        return getCommandInput();
    }

}
