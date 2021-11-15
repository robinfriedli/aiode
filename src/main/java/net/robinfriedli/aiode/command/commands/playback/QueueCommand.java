package net.robinfriedli.aiode.command.commands.playback;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.base.Strings;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.AudioQueue;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.PlayableFactory;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.audio.youtube.YouTubePlaylist;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.commands.AbstractQueueLoadingCommand;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.command.widget.widgets.QueueWidget;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class QueueCommand extends AbstractQueueLoadingCommand {

    public QueueCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution,
            context,
            commandManager,
            commandString,
            requiresInput,
            identifier,
            description,
            category,
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

    private void listQueue() {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = Aiode.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue audioQueue = playback.getAudioQueue();

        CompletableFuture<Message> futureMessage = sendMessage(audioQueue.buildMessageEmbed(playback, guild));
        WidgetRegistry widgetRegistry = getContext().getGuildContext().getWidgetRegistry();
        futureMessage.thenAccept(message -> new QueueWidget(widgetRegistry, guild, message, playback).initialise());
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
        AudioManager audioManager = Aiode.get().getAudioManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), getTrackLoadingExecutor());
        AudioQueue queue = audioManager.getQueue(getContext().getGuild());

        List<Playable> playables;
        if (chosenOption instanceof Collection) {
            playables = playableFactory.createPlayables(shouldRedirectSpotify(), chosenOption);
            loadedAmount = playables.size();
        } else {
            playables = getPlayablesForOption(chosenOption, playableFactory);
        }

        queue.add(playables);
    }

    private List<Playable> getPlayablesForOption(Object chosenOption, PlayableFactory playableFactory) throws Exception {
        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo || chosenOption instanceof Episode) {
            Playable track = playableFactory.createPlayable(shouldRedirectSpotify(), chosenOption);
            loadedTrack = track;
            return Collections.singletonList(track);
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<SpotifyTrack> tracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks(playlist));
            List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), tracks);
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
            List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), tracks);
            loadedAlbum = album;
            return playables;
        } else if (chosenOption instanceof AudioTrack) {
            Playable playable = playableFactory.createPlayable(shouldRedirectSpotify(), chosenOption);
            loadedAudioTrack = (AudioTrack) chosenOption;
            return Collections.singletonList(playable);
        } else if (chosenOption instanceof AudioPlaylist) {
            List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), chosenOption);
            loadedAudioPlaylist = (AudioPlaylist) chosenOption;
            return playables;
        } else if (chosenOption instanceof ShowSimplified) {
            ShowSimplified show = (ShowSimplified) chosenOption;
            List<Episode> episodes = runWithCredentials(() -> getSpotifyService().getShowEpisodes(show.getId()));
            List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), episodes);
            loadedShow = show;
            return playables;
        }

        throw new UnsupportedOperationException("Unsupported chosen option type: " + chosenOption.getClass());
    }

}
