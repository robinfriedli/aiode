package net.robinfriedli.aiode.command.commands;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.validator.routines.UrlValidator;

import com.google.common.base.Strings;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioTrackLoader;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.aiode.audio.playables.PlayableContainer;
import net.robinfriedli.aiode.audio.playables.PlayableContainerManager;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.playables.containers.AudioTrackPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.EpisodePlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.FilebrokerPostCollectionPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.FilebrokerPostPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.PlaylistPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SinglePlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SpotifyAlbumSimplifiedPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SpotifyPlaylistSimplifiedPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SpotifyShowSimplifiedPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.TrackPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.YouTubePlaylistPlayableContainer;
import net.robinfriedli.aiode.audio.spotify.SpotifyService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackResultHandler;
import net.robinfriedli.aiode.audio.spotify.SpotifyUri;
import net.robinfriedli.aiode.audio.youtube.YouTubePlaylist;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import net.robinfriedli.aiode.exceptions.NoSpotifyResultsFoundException;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.aiode.util.SearchEngine;
import net.robinfriedli.filebroker.FilebrokerApi;
import net.robinfriedli.stringlist.StringList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

public abstract class AbstractPlayableLoadingCommand extends AbstractSourceDecidingCommand {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TrackLoadingExecutor trackLoadingExecutor;

    protected int loadedAmount;
    protected Playlist loadedLocalList;
    protected YouTubePlaylist loadedYouTubePlaylist;
    protected PlaylistSimplified loadedSpotifyPlaylist;
    protected Playable loadedTrack;
    protected AlbumSimplified loadedAlbum;
    protected AudioTrack loadedAudioTrack;
    protected AudioPlaylist loadedAudioPlaylist;
    protected ShowSimplified loadedShow;
    protected FilebrokerApi.PostCollection loadedFilebrokerCollection;

    public AbstractPlayableLoadingCommand(CommandContribution commandContribution,
                                          CommandContext context,
                                          CommandManager commandManager,
                                          String commandString,
                                          boolean requiresInput,
                                          String identifier,
                                          String description,
                                          Category category,
                                          TrackLoadingExecutor trackLoadingExecutor) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
        this.trackLoadingExecutor = trackLoadingExecutor;
    }

    @Override
    public void doRun() throws Exception {
        AudioManager audioManager = Aiode.get().getAudioManager();

        if (UrlValidator.getInstance().isValid(getCommandInput())) {
            loadUrlItems(audioManager);
        } else if (SpotifyUri.isSpotifyUri(getCommandInput())) {
            loadSpotifyUri(audioManager);
        } else if (argumentSet("list")) {
            Source source = getSource();
            if (source.isSpotify()) {
                loadSpotifyList(audioManager);
            } else if (source.isYouTube()) {
                loadYouTubeList(audioManager);
            } else if (source.isFilebroker()) {
                loadFilebrokerCollections(audioManager);
            } else {
                loadLocalList(audioManager);
            }
        } else if (argumentSet("episode")) {
            loadSpotifyEpisode(audioManager);
        } else if (argumentSet("podcast")) {
            loadSpotifyShow(audioManager);
        } else {
            Source source = getSource();
            if (source.isYouTube()) {
                loadYouTubeVideo(audioManager);
            } else if (source.isSoundCloud()) {
                loadSoundCloudTrack(audioManager);
            } else if (source.isFilebroker()) {
                if (argumentSet("album")) {
                    loadFilebrokerCollections(audioManager);
                } else {
                    loadFilebrokerPosts(audioManager);
                }
            } else if (argumentSet("album")) {
                loadSpotifyAlbum(audioManager);
            } else {
                loadTrack(audioManager);
            }
        }
    }

    protected abstract void handleResult(PlayableContainer<?> playableContainer, net.robinfriedli.aiode.audio.playables.PlayableFactory playableFactory);

    protected abstract boolean shouldRedirectSpotify();

    protected TrackLoadingExecutor getTrackLoadingExecutor() {
        return trackLoadingExecutor;
    }

    protected void sendSuccessMessage(boolean playingNext) {
        if (loadedTrack != null) {
            sendSuccess("Queued " + loadedTrack.display() + (playingNext ? " to play next" : ""));
        }
        if (loadedLocalList != null) {
            sendSuccess(String.format("Queued playlist '%s'%s", loadedLocalList.getName(), (playingNext ? " to play next" : "")));
        }
        if (loadedSpotifyPlaylist != null) {
            sendSuccess(String.format("Queued playlist '%s'%s", loadedSpotifyPlaylist.getName(), (playingNext ? " to play next" : "")));
        }
        if (loadedYouTubePlaylist != null) {
            sendSuccess(String.format("Queued playlist '%s'%s", loadedYouTubePlaylist.getTitle(), (playingNext ? " to play next" : "")));
        }
        if (loadedAlbum != null) {
            sendSuccess(String.format("Queued album '%s'%s", loadedAlbum.getName(), (playingNext ? " to play next" : "")));
        }
        if (loadedAmount > 0) {
            sendSuccess(String.format("Queued %d item%s%s", loadedAmount, loadedAmount == 1 ? "" : "s", (playingNext ? " to play next" : "")));
        }
        if (loadedAudioTrack != null) {
            sendSuccess("Queued track " + loadedAudioTrack.getInfo().title + (playingNext ? " to play next" : ""));
        }
        if (loadedAudioPlaylist != null) {
            String name = loadedAudioPlaylist.getName();
            if (!Strings.isNullOrEmpty(name)) {
                sendSuccess("Queued playlist " + name + (playingNext ? " to play next" : ""));
            } else {
                int size = loadedAudioPlaylist.getTracks().size();
                sendSuccess(String.format("Queued %d item%s%s", size, size == 1 ? "" : "s", (playingNext ? " to play next" : "")));
            }
        }
        if (loadedShow != null) {
            String name = loadedShow.getName();
            sendSuccess("Queued podcast " + name + (playingNext ? " to play next" : ""));
        }
        if (loadedFilebrokerCollection != null) {
            sendSuccess("Queued filebroker collection " + loadedFilebrokerCollection.getTitle() + (playingNext ? " to play next" : ""));
        }
    }

    private void loadUrlItems(AudioManager audioManager) throws IOException {
        net.robinfriedli.aiode.audio.playables.PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        PlayableContainer<?> playableContainerForUrl = playableFactory.createPlayableContainerForUrl(getCommandInput());
        List<Playable> playables = playableContainerForUrl.loadPlayables(playableFactory);
        if (playables.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        loadedAmount = playables.size();
        handleResult(playableContainerForUrl, playableFactory);
    }

    private void loadSpotifyUri(AudioManager audioManager) throws Exception {
        SpotifyUri spotifyUri = SpotifyUri.parse(getCommandInput());
        SpotifyService spotifyService = getContext().getSpotifyService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        PlayableContainer<?> playableContainer = spotifyUri.createPlayableContainer(playableFactory, spotifyService);
        List<Playable> playables = playableContainer.loadPlayables(playableFactory);
        loadedAmount = playables.size();
        handleResult(playableContainer, playableFactory);
    }

    private void loadLocalList(AudioManager audioManager) {
        Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandInput());
        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local playlist found for '%s'", getCommandInput()));
        }

        if (playlist.isEmpty()) {
            throw new NoResultsFoundException("Playlist is empty");
        }

        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        PlayableContainerManager playableContainerManager = Aiode.get().getPlayableContainerManager();
        PlaylistPlayableContainer playlistPlayableContainer = new PlaylistPlayableContainer(playlist, playableContainerManager);
        loadedLocalList = playlist;
        handleResult(playlistPlayableContainer, playableFactory);
    }

    private void loadYouTubeList(AudioManager audioManager) throws IOException {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());

        if (argumentSet("select")) {
            int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 10);

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandInput());
            if (playlists.size() == 1) {
                YouTubePlaylist playlist = playlists.get(0);
                loadedYouTubePlaylist = playlist;
                handleResult(new YouTubePlaylistPlayableContainer(playlist), playableFactory);
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube playlist found for '%s'", getCommandInput()));
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandInput());
            loadedYouTubePlaylist = youTubePlaylist;
            handleResult(new YouTubePlaylistPlayableContainer(youTubePlaylist), playableFactory);
        }
    }

    private void loadSpotifyList(AudioManager audioManager) throws Exception {
        Callable<Void> callable = () -> {
            List<PlaylistSimplified> found;
            int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
            if (argumentSet("own")) {
                found = getSpotifyService().searchOwnPlaylist(getCommandInput(), limit);
            } else {
                found = getSpotifyService().searchPlaylist(getCommandInput(), limit);
            }

            if (found.size() == 1) {
                PlaylistSimplified playlist = found.get(0);
                PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
                loadedSpotifyPlaylist = playlist;
                handleResult(new SpotifyPlaylistSimplifiedPlayableContainer(playlist), playableFactory);
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
        int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
        Callable<List<AlbumSimplified>> albumLoadCallable = () -> getSpotifyService().searchAlbum(getCommandInput(), argumentSet("own"), limit);
        List<AlbumSimplified> albums;
        if (argumentSet("own")) {
            albums = runWithLogin(albumLoadCallable);
        } else {
            albums = runWithCredentials(albumLoadCallable);
        }

        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
            loadedAlbum = album;
            handleResult(new SpotifyAlbumSimplifiedPlayableContainer(album), playableFactory);
        } else if (albums.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No albums found for '%s'", getCommandInput()));
        } else {
            askQuestion(albums, AlbumSimplified::getName, album -> StringList.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "));
        }
    }

    private void loadTrack(AudioManager audioManager) throws Exception {
        int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
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
                    String artistString = StringList.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                    return String.format("%s by %s", track.getName(), artistString);
                }, track -> track.getAlbum().getName());
            } else {
                SpotifyTrackResultHandler resultHandler = new SpotifyTrackResultHandler(getContext().getGuild(), getContext().getSession());
                createPlayableForTrack(resultHandler.getBestResult(getCommandInput(), found), audioManager);
            }
        }
    }

    private void loadSoundCloudTrack(AudioManager audioManager) {
        AudioTrackLoader audioTrackLoader = new AudioTrackLoader(audioManager.getPlayerManager());
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        String commandInput = getCommandInput();
        AudioItem audioItem = audioTrackLoader.loadByIdentifier("scsearch:" + commandInput);
        switch (audioItem) {
            case AudioTrack audioTrack -> {
                this.loadedAudioTrack = audioTrack;
                handleResult(new AudioTrackPlayableContainer(audioTrack), playableFactory);
            }
            case null ->
                throw new NoResultsFoundException(String.format("No soundcloud track found for '%s'", commandInput));
            case AudioPlaylist audioPlaylist -> {
                int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
                List<AudioTrack> tracks = audioPlaylist.getTracks();

                if (tracks.isEmpty()) {
                    throw new NoResultsFoundException(String.format("No soundcloud track found for '%s'", commandInput));
                }

                if (tracks.size() > limit) {
                    tracks = tracks.subList(0, limit);
                }

                askQuestion(tracks, audioTrack -> audioTrack.getInfo().title, audioTrack -> audioTrack.getInfo().author);
            }
            default -> {
            }
        }
    }

    private void loadSpotifyEpisode(AudioManager audioManager) throws Exception {
        int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
        Callable<List<Episode>> loadTrackCallable = () -> getSpotifyService().searchEpisode(getCommandInput(), argumentSet("own"), limit);
        List<Episode> found;
        if (argumentSet("own")) {
            found = runWithLogin(loadTrackCallable);
        } else {
            found = runWithCredentials(loadTrackCallable);
        }

        if (found.size() == 1) {
            createPlayableForEpisode(found.get(0), audioManager);
        } else if (found.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No Spotify episode found for '%s'", getCommandInput()));
        } else {
            askQuestion(found, episode -> String.format("%s by %s", episode.getName(), episode.getShow().getName()));
        }
    }

    private void loadSpotifyShow(AudioManager audioManager) throws Exception {
        int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
        Callable<List<ShowSimplified>> albumLoadCallable = () -> getSpotifyService().searchShow(getCommandInput(), argumentSet("own"), limit);
        List<ShowSimplified> shows;
        if (argumentSet("own")) {
            shows = runWithLogin(albumLoadCallable);
        } else {
            shows = runWithCredentials(albumLoadCallable);
        }

        if (shows.size() == 1) {
            ShowSimplified show = shows.get(0);
            PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
            loadedShow = show;
            handleResult(new SpotifyShowSimplifiedPlayableContainer(show), playableFactory);
        } else if (shows.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No shows found for '%s'", getCommandInput()));
        } else {
            askQuestion(shows, ShowSimplified::getName, ShowSimplified::getPublisher);
        }
    }

    private void createPlayableForTrack(Track track, AudioManager audioManager) {
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        TrackPlayableContainer playableContainer = new TrackPlayableContainer(track);
        loadedTrack = playableContainer.loadPlayable(playableFactory);
        handleResult(playableContainer, playableFactory);
    }

    private void createPlayableForEpisode(Episode episode, AudioManager audioManager) {
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        EpisodePlayableContainer episodePlayableContainer = new EpisodePlayableContainer(episode);
        loadedTrack = episodePlayableContainer.loadPlayable(playableFactory);
        handleResult(episodePlayableContainer, playableFactory);
    }

    private void loadYouTubeVideo(AudioManager audioManager) throws IOException {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());

        if (argumentSet("select")) {
            int limit = getArgumentValueWithTypeOrElse("select", Integer.class, 10);

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandInput());
            if (youTubeVideos.size() == 1) {
                Playable playable = youTubeVideos.get(0);
                loadedTrack = playable;
                handleResult(new SinglePlayableContainer(playable), playableFactory);
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
            loadedTrack = youTubeVideo;
            handleResult(new SinglePlayableContainer(youTubeVideo), playableFactory);
        }
    }

    private void loadFilebrokerPosts(AudioManager audioManager) {
        FilebrokerApi filebrokerApi = Aiode.get().getFilebrokerApi();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        CompletableFuture<FilebrokerApi.SearchResult> searchResultCompletableFuture;
        String query = getCommandInput();
        if (argumentSet("select")) {
            Integer select = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
            searchResultCompletableFuture = filebrokerApi.searchPostsAsync(query, null, select);
        } else {
            searchResultCompletableFuture = filebrokerApi.searchPostsAsync(query, null, 1);
        }

        try {
            FilebrokerApi.SearchResult searchResult = searchResultCompletableFuture.get(10, TimeUnit.SECONDS);
            List<FilebrokerApi.Post> posts = searchResult.getPosts();
            if (posts == null || posts.isEmpty()) {
                throw new NoResultsFoundException("No filebroker posts found for query, check [the documentation](https://github.com/filebroker/filebroker-server/wiki/filebroker-query-cheat-sheet).");
            } else {
                if (posts.size() > 1 && argumentSet("select")) {
                    askQuestion(
                        posts,
                        post -> new FilebrokerPlayableWrapper(post).getDisplay(),
                        post -> String.valueOf(post.getPk())
                    );
                } else {
                    FilebrokerPostPlayableContainer playableContainer = new FilebrokerPostPlayableContainer(posts.getFirst());
                    loadedTrack = playableContainer.loadPlayable(playableFactory);
                    handleResult(playableContainer, playableFactory);
                }
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FilebrokerApi.InvalidHttpResponseException filebrokerApiException) {
                handleFilebrokerQueryException(query, filebrokerApiException);
            }
            logger.error("Failed to execute filebroker search", e);
            throw new NoResultsFoundException("Failed to execute filebroker search", e);
        } catch (TimeoutException e) {
            throw new NoResultsFoundException("Filebroker search timed out", e);
        } catch (InterruptedException e) {
            throw new CommandRuntimeException(e);
        }
    }

    private void loadFilebrokerCollections(AudioManager audioManager) {
        FilebrokerApi filebrokerApi = Aiode.get().getFilebrokerApi();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), trackLoadingExecutor, shouldRedirectSpotify());
        CompletableFuture<FilebrokerApi.SearchResult> searchResultCompletableFuture;
        String query = getCommandInput();
        if (argumentSet("select")) {
            Integer select = getArgumentValueWithTypeOrElse("select", Integer.class, 20);
            searchResultCompletableFuture = filebrokerApi.searchPostCollectionsAsync(query, null, select);
        } else {
            searchResultCompletableFuture = filebrokerApi.searchPostCollectionsAsync(query, null, 1);
        }

        try {
            FilebrokerApi.SearchResult searchResult = searchResultCompletableFuture.get(10, TimeUnit.SECONDS);
            List<FilebrokerApi.PostCollection> collections = searchResult.getCollections();
            if (collections == null || collections.isEmpty()) {
                throw new NoResultsFoundException("No filebroker collections found for query, check [the documentation](https://github.com/filebroker/filebroker-server/wiki/filebroker-query-cheat-sheet).");
            } else {
                if (collections.size() > 1 && argumentSet("select")) {
                    askQuestion(
                        collections,
                        FilebrokerApi.PostCollection::getTitle,
                        collection -> String.valueOf(collection.getPk())
                    );
                } else {
                    FilebrokerApi.PostCollection collection = collections.getFirst();
                    FilebrokerPostCollectionPlayableContainer playableContainer = new FilebrokerPostCollectionPlayableContainer(collection);
                    loadedFilebrokerCollection = collection;
                    handleResult(playableContainer, playableFactory);
                }
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FilebrokerApi.InvalidHttpResponseException filebrokerApiException) {
                handleFilebrokerQueryException(query, filebrokerApiException);
            }
            logger.error("Failed to execute filebroker search", e);
            throw new NoResultsFoundException("Failed to execute filebroker search", e);
        } catch (TimeoutException e) {
            throw new NoResultsFoundException("Filebroker search timed out", e);
        } catch (InterruptedException e) {
            throw new CommandRuntimeException(e);
        }
    }

    private void handleFilebrokerQueryException(String query, FilebrokerApi.InvalidHttpResponseException filebrokerApiException) {
        try {
            JSONObject errorObject = new JSONObject(filebrokerApiException.getBody());
            if (errorObject.getInt("error_code") == 400_010 && errorObject.has("compilation_errors")) {
                StringBuilder errorMsgBuilder = new StringBuilder("Invalid filebroker query, check [the documentation](https://github.com/filebroker/filebroker-server/wiki/filebroker-query-cheat-sheet).");
                JSONArray compilationErrors = errorObject.getJSONArray("compilation_errors");
                if (!compilationErrors.isEmpty()) {
                    errorMsgBuilder.append(System.lineSeparator()).append("```").append(System.lineSeparator());
                    JSONObject compilationError = (JSONObject) compilationErrors.get(0);
                    JSONObject location = compilationError.getJSONObject("location");
                    int start = location.getInt("start");
                    int end = location.getInt("end");
                    int startIdx = Math.max(0, start - 25);
                    int endIdx = Math.min(query.length(), end + 25);
                    String queryPart = query.substring(startIdx, endIdx);
                    String marker;
                    if (end > start) {
                        marker = " ".repeat(start - startIdx) + "^" + "-".repeat(Math.max(0, end - start - 1)) + "^";
                    } else {
                        marker = " ".repeat(start - startIdx) + "^";
                    }
                    errorMsgBuilder.append(queryPart).append(System.lineSeparator()).append(marker);
                    String msg = compilationError.getString("msg");
                    errorMsgBuilder.append(System.lineSeparator()).append(msg);
                    errorMsgBuilder.append(System.lineSeparator()).append("```").append(System.lineSeparator());
                }
                throw new InvalidCommandException(errorMsgBuilder.toString());
            } else {
                throw new InvalidCommandException(errorObject.getString("message"));
            }
        } catch (JSONException | ClassCastException jsonException) {
            logger.error("Failed to deserialize error response body", jsonException);
        }
    }

}
