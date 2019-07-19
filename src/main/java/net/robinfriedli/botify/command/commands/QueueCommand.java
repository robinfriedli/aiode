package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.QueueWidget;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class QueueCommand extends AbstractQueueLoadingCommand {

    public QueueCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK, false);
    }

    @Override
    public void doRun() throws Exception {
        if (getCommandBody().isBlank()) {
            listQueue();
        } else {
            super.doRun();
        }
    }

    @Override
    protected void handleResults(List<Playable> playables) {
        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        playback.getAudioQueue().add(playables);
    }

    private void listQueue() throws Exception {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = Botify.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue audioQueue = playback.getAudioQueue();

        CompletableFuture<Message> futureMessage = sendWithLogo(audioQueue.buildMessageEmbed(playback, guild));
        getManager().registerWidget(new QueueWidget(getManager(), futureMessage.get(), audioManager, playback));
    }

    @Override
    public void onSuccess() {
        if (loadedTrack != null) {
            sendSuccess("Queued " + loadedTrack.getDisplayInterruptible());
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
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild());
        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo) {
            Playable track = playableFactory.createPlayable(!argumentSet("preview"), chosenOption);
            audioManager.getQueue(getContext().getGuild()).add(track);
            loadedTrack = track;
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks(playlist));
            AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(getContext().getGuild());
            List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), tracks, false);
            playbackForGuild.getAudioQueue().add(playables);
            loadedSpotifyPlaylist = playlist;
        } else if (chosenOption instanceof YouTubePlaylist) {
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            YouTubePlaylist youTubePlaylist = (YouTubePlaylist) chosenOption;
            List<Playable> playables = playableFactory.createPlayables(youTubePlaylist, false);
            playback.getAudioQueue().add(playables);
            loadedYouTubePlaylist = youTubePlaylist;
        } else if (chosenOption instanceof AlbumSimplified) {
            AlbumSimplified album = (AlbumSimplified) chosenOption;
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(album.getId()));
            List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), tracks, false);
            playback.getAudioQueue().add(playables);
            loadedAlbum = album;
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("preview").needsArguments("spotify").excludesArguments("youtube")
            .setDescription("Queue the preview mp3 directly from Spotify rather than the full track from YouTube");
        argumentContribution.map("youtube").setRequiresInput(true).excludesArguments("preview")
            .setDescription("Queue a YouTube video. Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("list").setRequiresInput(true)
            .setDescription("Add the elements from a Spotify, YouTube or local Playlist to the current queue (local is default).");
        argumentContribution.map("spotify").setRequiresInput(true).excludesArguments("youtube")
            .setDescription("Queue Spotify track, playlist or album. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to Spotify tracks and playlists in the current users library. This requires a Spotify login.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Queue local playlist.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of YouTube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        argumentContribution.map("album").needsArguments("spotify").excludesArguments("list").setRequiresInput(true)
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
        return argumentContribution;
    }

}
