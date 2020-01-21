package net.robinfriedli.botify.command.commands;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.validator.routines.UrlValidator;

import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackResultHandler;
import net.robinfriedli.botify.audio.spotify.SpotifyUri;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.exceptions.NoSpotifyResultsFoundException;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;

public abstract class AbstractPlayableLoadingCommand extends AbstractSourceDecidingCommand {

    private final boolean mayInterrupt;
    private final TrackLoadingExecutor trackLoadingExecutor;

    protected int loadedAmount;
    protected Playlist loadedLocalList;
    protected YouTubePlaylist loadedYouTubePlaylist;
    protected PlaylistSimplified loadedSpotifyPlaylist;
    protected Playable loadedTrack;
    protected AlbumSimplified loadedAlbum;

    public AbstractPlayableLoadingCommand(CommandContribution commandContribution,
                                          CommandContext context,
                                          CommandManager commandManager,
                                          String commandString,
                                          boolean requiresInput,
                                          String identifier,
                                          String description,
                                          Category category,
                                          boolean mayInterrupt,
                                          TrackLoadingExecutor trackLoadingExecutor) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
        this.mayInterrupt = mayInterrupt;
        this.trackLoadingExecutor = trackLoadingExecutor;
    }

    @Override
    public void doRun() throws Exception {
        AudioManager audioManager = Botify.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());

        if (UrlValidator.getInstance().isValid(getCommandInput())) {
            loadUrlItems(audioManager, playback);
        } else if (SpotifyUri.isSpotifyUri(getCommandInput())) {
            loadSpotifyUri(audioManager);
        } else if (argumentSet("list")) {
            Source source = getSource();
            if (source.isSpotify()) {
                loadSpotifyList(audioManager);
            } else if (source.isYouTube()) {
                loadYouTubeList(audioManager);
            } else {
                loadLocalList(audioManager);
            }
        } else {
            Source source = getSource();
            if (source.isYouTube()) {
                loadYouTubeVideo(audioManager);
            } else if (argumentSet("album")) {
                loadSpotifyAlbum(audioManager);
            } else {
                loadTrack(audioManager);
            }
        }
    }

    protected abstract void handleResults(List<Playable> playables);

    protected abstract boolean shouldRedirectSpotify();

    protected TrackLoadingExecutor getTrackLoadingExecutor() {
        return trackLoadingExecutor;
    }

    private void loadUrlItems(AudioManager audioManager, AudioPlayback playback) throws IOException {
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);
        List<Playable> playables = playableFactory.createPlayables(getCommandInput(), getContext().getSpotifyApi(), shouldRedirectSpotify());
        if (playables.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        handleResults(playables);
        loadedAmount = playables.size();
    }

    private void loadSpotifyUri(AudioManager audioManager) throws Exception {
        SpotifyUri spotifyUri = SpotifyUri.parse(getCommandInput());
        SpotifyService spotifyService = getContext().getSpotifyService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);
        List<Playable> playables = spotifyUri.loadPlayables(playableFactory, spotifyService, shouldRedirectSpotify(), mayInterrupt);
        handleResults(playables);
        loadedAmount = playables.size();
    }

    private void loadLocalList(AudioManager audioManager) throws Exception {
        Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandInput());
        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local playlist found for '%s'", getCommandInput()));
        }

        List<Object> items = runWithCredentials(() -> playlist.getTracks(getContext().getSpotifyApi()));

        if (items.isEmpty()) {
            throw new NoResultsFoundException("Playlist is empty");
        }

        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);
        List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), items);
        handleResults(playables);
        loadedLocalList = playlist;
    }

    private void loadYouTubeList(AudioManager audioManager) throws IOException {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);

        if (argumentSet("select")) {
            int limit = getArgumentValue("select", Integer.class, 10);

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandInput());
            if (playlists.size() == 1) {
                YouTubePlaylist playlist = playlists.get(0);
                List<Playable> playables = playableFactory.createPlayables(playlist);
                handleResults(playables);
                loadedYouTubePlaylist = playlist;
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube playlist found for '%s'", getCommandInput()));
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandInput());
            List<Playable> playables = playableFactory.createPlayables(youTubePlaylist);
            handleResults(playables);
            loadedYouTubePlaylist = youTubePlaylist;
        }
    }

    private void loadSpotifyList(AudioManager audioManager) throws Exception {
        Callable<Void> callable = () -> {
            List<PlaylistSimplified> found;
            if (argumentSet("own")) {
                found = getSpotifyService().searchOwnPlaylist(getCommandInput());
            } else {
                found = getSpotifyService().searchPlaylist(getCommandInput());
            }

            if (found.size() == 1) {
                PlaylistSimplified playlist = found.get(0);
                List<Track> playlistTracks = getSpotifyService().getPlaylistTracks(playlist);
                PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);
                List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), playlistTracks);
                handleResults(playables);
                loadedSpotifyPlaylist = playlist;
            } else if (found.isEmpty()) {
                throw new NoSpotifyResultsFoundException(String.format("No Spotify playlist found for '%s'", getCommandInput()));
            } else {
                askQuestion(found, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
            }

            return null;
        };

        if (argumentSet("own")) {
            runWithLogin(callable);
        } else {
            runWithCredentials(callable);
        }
    }

    private void loadSpotifyAlbum(AudioManager audioManager) throws Exception {
        Callable<List<AlbumSimplified>> albumLoadCallable = () -> getSpotifyService().searchAlbum(getCommandInput(), argumentSet("own"));
        List<AlbumSimplified> albums;
        if (argumentSet("own")) {
            albums = runWithLogin(albumLoadCallable);
        } else {
            albums = runWithCredentials(albumLoadCallable);
        }

        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(album.getId()));
            PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);
            List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), tracks);
            handleResults(playables);
            loadedAlbum = album;
        } else if (albums.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No albums found for '%s'", getCommandInput()));
        } else {
            askQuestion(albums, AlbumSimplified::getName, album -> StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "));
        }
    }

    private void loadTrack(AudioManager audioManager) throws Exception {
        int limit = getArgumentValue("select", Integer.class, 20);
        Callable<List<Track>> loadTrackCallable = () -> getSpotifyService().searchTrack(getCommandInput(), argumentSet("own"), limit);
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(loadTrackCallable);
        } else {
            found = runWithCredentials(loadTrackCallable);
        }

        if (found.size() == 1) {
            createPlayableForTrack(found.get(0), audioManager);
        } else if (found.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No Spotify track found for '%s'", getCommandInput()));
        } else {
            if (argumentSet("select")) {
                askQuestion(found, track -> {
                    String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                    return String.format("%s by %s", track.getName(), artistString);
                }, track -> track.getAlbum().getName());
            } else {
                SpotifyTrackResultHandler resultHandler = new SpotifyTrackResultHandler(getContext().getGuild(), getContext().getSession());
                createPlayableForTrack(resultHandler.getBestResult(getCommandInput(), found), audioManager);
            }
        }
    }

    private void createPlayableForTrack(Track track, AudioManager audioManager) {
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor);
        Playable playable = playableFactory.createPlayable(shouldRedirectSpotify(), track);
        handleResults(Lists.newArrayList(playable));
        loadedTrack = playable;
    }

    private void loadYouTubeVideo(AudioManager audioManager) throws IOException {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        if (argumentSet("select")) {
            int limit = getArgumentValue("select", Integer.class, 10);

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandInput());
            if (youTubeVideos.size() == 1) {
                Playable playable = youTubeVideos.get(0);
                handleResults(Lists.newArrayList(playable));
                loadedTrack = playable;
            } else if (youTubeVideos.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube video found for '%s'", getCommandInput()));
            } else {
                askQuestion(youTubeVideos, youTubeVideo -> {
                    try {
                        return youTubeVideo.getDisplay();
                    } catch (UnavailableResourceException e) {
                        // Unreachable since only HollowYouTubeVideos might get interrupted
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandInput());
            handleResults(Lists.newArrayList(youTubeVideo));
            loadedTrack = youTubeVideo;
        }
    }

}
