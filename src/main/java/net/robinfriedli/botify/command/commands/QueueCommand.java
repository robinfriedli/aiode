package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.validator.routines.UrlValidator;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.QueueWidget;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;

public class QueueCommand extends AbstractCommand {

    private Playable queuedTrack;
    private Playlist queuedLocalList;
    private PlaylistSimplified queuedSpotifyPlaylist;
    private YouTubePlaylist queuedYouTubePlaylist;
    private AlbumSimplified queuedAlbum;
    private int queuedAmount;

    public QueueCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() throws Exception {
        if (getCommandBody().isBlank()) {
            listQueue();
        } else {
            AudioManager audioManager = getManager().getAudioManager();
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            playback.setCommunicationChannel(getContext().getChannel());

            if (UrlValidator.getInstance().isValid(getCommandBody())) {
                queueUrlItems(audioManager, playback);
            } else if (argumentSet("list")) {
                if (argumentSet("spotify")) {
                    queueSpotifyList(audioManager, playback);
                } else if (argumentSet("youtube")) {
                    queueYouTubeList(audioManager, playback);
                } else {
                    queueLocalList(audioManager, playback);
                }
            } else {
                if (argumentSet("youtube")) {
                    queueYouTubeVideo(audioManager, playback);
                } else if (argumentSet("album")) {
                    queueSpotifyAlbum(audioManager, playback);
                } else if (UrlValidator.getInstance().isValid(getCommandBody())) {
                    queueUrlItems(audioManager, playback);
                } else {
                    queueTrack(audioManager, playback);
                }
            }
        }
    }

    private void queueUrlItems(AudioManager audioManager, AudioPlayback playback) {
        List<Playable> playables = audioManager.createPlayables(getCommandBody(), playback, getContext().getSpotifyApi(), !argumentSet("preview"), false);
        playback.getAudioQueue().add(playables);
        queuedAmount = playables.size();
    }

    private void queueLocalList(AudioManager audioManager, AudioPlayback playback) throws Exception {
        Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandBody(), isPartitioned(), getContext().getGuild().getId());
        if (playlist == null) {
            throw new NoResultsFoundException("No local playlist found for " + getCommandBody());
        }

        List<Object> items = runWithCredentials(() -> playlist.getItems(getContext().getSpotifyApi()));

        if (items.isEmpty()) {
            throw new NoResultsFoundException("Playlist is empty");
        }

        List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), items, playback, false);
        playback.getAudioQueue().add(playables);
        queuedLocalList = playlist;
    }

    private void queueYouTubeList(AudioManager audioManager, AudioPlayback playback) {
        YouTubeService youTubeService = audioManager.getYouTubeService();

        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandBody());
            if (playlists.size() == 1) {
                YouTubePlaylist playlist = playlists.get(0);
                List<Playable> playables = audioManager.createPlayables(playlist, playback, false);
                playback.getAudioQueue().add(playables);
                queuedYouTubePlaylist = playlist;
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No playlist found for " + getCommandBody());
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandBody());
            List<Playable> playables = audioManager.createPlayables(youTubePlaylist, playback, false);
            playback.getAudioQueue().add(playables);
            queuedYouTubePlaylist = youTubePlaylist;
        }
    }

    private void queueSpotifyList(AudioManager audioManager, AudioPlayback playback) throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        Callable<Void> callable = () -> {
            List<PlaylistSimplified> found;
            if (argumentSet("own")) {
                found = SearchEngine.searchOwnPlaylist(spotifyApi, getCommandBody());
            } else {
                found = SearchEngine.searchSpotifyPlaylist(spotifyApi, getCommandBody());
            }

            if (found.size() == 1) {
                PlaylistSimplified playlist = found.get(0);
                List<Track> playlistTracks = SearchEngine.getPlaylistTracks(spotifyApi, playlist);
                List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), playlistTracks, playback, false);
                playback.getAudioQueue().add(playables);
                queuedSpotifyPlaylist = playlist;
            } else if (found.isEmpty()) {
                throw new NoResultsFoundException("No playlist found for " + getCommandBody());
            } else {
                askQuestion(found, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
            }

            return null;
        };

        if (argumentSet("own")) {
            runWithLogin(getContext().getUser(), callable);
        } else {
            runWithCredentials(callable);
        }
    }

    private void queueSpotifyAlbum(AudioManager audioManager, AudioPlayback audioPlayback) throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        List<AlbumSimplified> albums = runWithCredentials(() -> SearchEngine.searchSpotifyAlbum(spotifyApi, getCommandBody()));

        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getAlbumTracks(spotifyApi, album.getId()));
            List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), tracks, audioPlayback, false);
            audioPlayback.getAudioQueue().add(playables);
            queuedAlbum = album;
        } else if (albums.isEmpty()) {
            throw new NoResultsFoundException("No albums found for " + getCommandBody());
        } else {
            askQuestion(albums, AlbumSimplified::getName, album -> StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "));
        }
    }

    private void queueTrack(AudioManager audioManager, AudioPlayback playback) throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, getCommandBody()));
        } else {
            found = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, getCommandBody()));
        }

        if (found.size() == 1) {
            Playable track = audioManager.createPlayable(!argumentSet("preview"), found.get(0));
            playback.getAudioQueue().add(track);
            queuedTrack = track;
        } else if (found.isEmpty()) {
            throw new NoResultsFoundException("No results found for " + getCommandBody());
        } else {
            askQuestion(found, track -> {
                String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                return String.format("%s by %s", track.getName(), artistString);
            }, track -> track.getAlbum().getName());
        }
    }

    private void queueYouTubeVideo(AudioManager audioManager, AudioPlayback playback) {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandBody());
            if (youTubeVideos.size() == 1) {
                Playable playable = audioManager.createPlayable(false, youTubeVideos.get(0));
                playback.getAudioQueue().add(playable);
                queuedTrack = playable;
            } else if (youTubeVideos.isEmpty()) {
                throw new NoResultsFoundException("No YouTube video found for " + getCommandBody());
            } else {
                askQuestion(youTubeVideos, youTubeVideo -> {
                    try {
                        return youTubeVideo.getTitle();
                    } catch (InterruptedException e) {
                        // Unreachable since only HollowYouTubeVideos might get interrupted
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandBody());
            Playable video = audioManager.createPlayable(false, youTubeVideo);
            audioManager.getQueue(getContext().getGuild()).add(video);
            queuedTrack = video;
        }
    }

    private void listQueue() throws Exception {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = getManager().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue audioQueue = playback.getAudioQueue();

        CompletableFuture<Message> futureMessage = sendWithLogo(getContext().getChannel(), audioQueue.buildMessageEmbed(playback, guild));
        getManager().registerWidget(new QueueWidget(getManager(), futureMessage.get(), audioManager, playback));
    }

    @Override
    public void onSuccess() {
        if (queuedTrack != null) {
            sendSuccess(getContext().getChannel(), "Queued " + queuedTrack.getDisplayInterruptible());
        }
        if (queuedLocalList != null) {
            sendSuccess(getContext().getChannel(), "Queued playlist " + queuedLocalList.getName());
        }
        if (queuedSpotifyPlaylist != null) {
            sendSuccess(getContext().getChannel(), "Queued playlist " + queuedSpotifyPlaylist.getName());
        }
        if (queuedYouTubePlaylist != null) {
            sendSuccess(getContext().getChannel(), "Queued playlist " + queuedYouTubePlaylist.getTitle());
        }
        if (queuedAlbum != null) {
            sendSuccess(getContext().getChannel(), "Queued album " + queuedAlbum.getName());
        }
        if (queuedAmount > 0) {
            String queuedString = queuedAmount > 1 ? "items" : "item";
            sendSuccess(getContext().getChannel(), "Queued " + queuedAmount + " " + queuedString);
        }
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        AudioManager audioManager = getManager().getAudioManager();
        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo) {
            Playable track = audioManager.createPlayable(!argumentSet("preview"), chosenOption);
            audioManager.getQueue(getContext().getGuild()).add(track);
            queuedTrack = track;
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(getContext().getSpotifyApi(), playlist));
            AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(getContext().getGuild());
            List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), tracks, playbackForGuild, false);
            playbackForGuild.getAudioQueue().add(playables);
            queuedSpotifyPlaylist = playlist;
        } else if (chosenOption instanceof YouTubePlaylist) {
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            YouTubePlaylist youTubePlaylist = (YouTubePlaylist) chosenOption;
            List<Playable> playables = audioManager.createPlayables(youTubePlaylist, playback, false);
            playback.getAudioQueue().add(playables);
            queuedYouTubePlaylist = youTubePlaylist;
        } else if (chosenOption instanceof AlbumSimplified) {
            AlbumSimplified album = (AlbumSimplified) chosenOption;
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getAlbumTracks(getContext().getSpotifyApi(), album.getId()));
            List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), tracks, playback, false);
            playback.getAudioQueue().add(playables);
            queuedAlbum = album;
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
            .setDescription("Limit search to Spotify tracks and playlists in the current users library. This requires a Spotify login. Spotify search filters (\"artist:\", \"album:\" etc.) are not supported with this argument.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Queue local playlist.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of YouTube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        argumentContribution.map("album").needsArguments("spotify").excludesArguments("own").setRequiresInput(true)
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
        return argumentContribution;
    }

}
