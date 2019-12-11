package net.robinfriedli.botify.command.commands;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.QueueWidget;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;

public class QueueCommand extends AbstractQueueLoadingCommand {

    public QueueCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution,
            context,
            commandManager,
            commandString,
            identifier,
            description,
            Category.PLAYBACK,
            false,
            context.getGuildContext().getPooledTrackLoadingExecutor());
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
    protected void handleResults(List<Playable> playables) {
        if (playables.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        playback.getAudioQueue().add(playables);
    }

    private void listQueue() throws Exception {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = Botify.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue audioQueue = playback.getAudioQueue();

        CompletableFuture<Message> futureMessage = sendMessage(audioQueue.buildMessageEmbed(playback, guild));
        WidgetManager widgetManager = getContext().getGuildContext().getWidgetManager();
        widgetManager.registerWidget(new QueueWidget(widgetManager, futureMessage.get(), audioManager, playback));
    }

    @Override
    public void onSuccess() {
        if (loadedTrack != null) {
            sendSuccess("Queued " + loadedTrack.display());
        }
        if (loadedLocalList != null) {
            sendSuccess("Queued playlist " + loadedLocalList.getName());
        }
        if (loadedSpotifyPlaylist != null) {
            sendSuccess("Queued playlist " + loadedSpotifyPlaylist.getName());
        }
        if (loadedYouTubePlaylist != null) {
            sendSuccess("Queued playlist " + loadedYouTubePlaylist.getTitle());
        }
        if (loadedAlbum != null) {
            sendSuccess("Queued album " + loadedAlbum.getName());
        }
        if (loadedAmount > 0) {
            String loadedString = loadedAmount > 1 ? "items" : "item";
            sendSuccess("Queued " + loadedAmount + " " + loadedString);
        }
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        AudioManager audioManager = Botify.get().getAudioManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), getTrackLoadingExecutor());
        AudioQueue queue = audioManager.getQueue(getContext().getGuild());

        List<Playable> playables;
        if (chosenOption instanceof Collection) {
            playables = playableFactory.createPlayables(!argumentSet("preview"), chosenOption);
            loadedAmount = playables.size();
        } else {
            playables = getPlayablesForOption(chosenOption, playableFactory);
        }

        queue.add(playables);
    }

    private List<Playable> getPlayablesForOption(Object chosenOption, PlayableFactory playableFactory) throws Exception {
        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo) {
            Playable track = playableFactory.createPlayable(!argumentSet("preview"), chosenOption);
            loadedTrack = track;
            return Collections.singletonList(track);
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks(playlist));
            List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), tracks);
            loadedSpotifyPlaylist = playlist;
            return playables;
        } else if (chosenOption instanceof YouTubePlaylist) {
            YouTubePlaylist youTubePlaylist = (YouTubePlaylist) chosenOption;
            List<Playable> playables = playableFactory.createPlayables(youTubePlaylist);
            loadedYouTubePlaylist = youTubePlaylist;
            return playables;
        } else if (chosenOption instanceof AlbumSimplified) {
            AlbumSimplified album = (AlbumSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(album.getId()));
            List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), tracks);
            loadedAlbum = album;
            return playables;
        }

        throw new UnsupportedOperationException("Unsupported chosen option type: " + chosenOption.getClass());
    }

}
