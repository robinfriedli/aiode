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
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackResultHandler;
import net.robinfriedli.botify.audio.spotify.SpotifyUri;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.exceptions.NoSpotifyResultsFoundException;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;

public abstract class AbstractPlayableLoadingCommand extends AbstractSourceDecidingCommand {

    private final boolean mayInterrupt;
    int loadedAmount;
    Playlist loadedLocalList;
    YouTubePlaylist loadedYouTubePlaylist;
    PlaylistSimplified loadedSpotifyPlaylist;
    Playable loadedTrack;
    AlbumSimplified loadedAlbum;

    public AbstractPlayableLoadingCommand(CommandContribution commandContribution,
                                          CommandContext context,
                                          CommandManager commandManager,
                                          String commandString,
                                          boolean requiresInput,
                                          String identifier,
                                          String description,
                                          Category category,
                                          boolean mayInterrupt) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
        this.mayInterrupt = mayInterrupt;
    }

    @Override
    public void doRun() throws Exception {
        AudioManager audioManager = Botify.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
        playback.setCommunicationChannel(getContext().getChannel());

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

    private void loadUrlItems(AudioManager audioManager, AudioPlayback playback) throws IOException {
        PlayableFactory playableFactory = audioManager.createPlayableFactory(playback.getGuild(), getSpotifyService());
        List<Playable> playables = playableFactory.createPlayables(getCommandInput(), getContext().getSpotifyApi(), !argumentSet("preview"), mayInterrupt);
        if (playables.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        handleResults(playables);
        loadedAmount = playables.size();
    }

    private void loadSpotifyUri(AudioManager audioManager) throws Exception {
        SpotifyUri spotifyUri = SpotifyUri.parse(getCommandInput());
        SpotifyService spotifyService = getContext().getSpotifyService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
        List<Playable> playables = spotifyUri.loadPlayables(playableFactory, spotifyService, !argumentSet("preview"), mayInterrupt);
        handleResults(playables);
        loadedAmount = playables.size();
    }

    private void loadLocalList(AudioManager audioManager) throws Exception {
        Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandInput(), isPartitioned(), getContext().getGuild().getId());
        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local playlist found for '%s'", getCommandInput()));
        }

        List<Object> items = runWithCredentials(() -> playlist.getTracks(getContext().getSpotifyApi()));

        if (items.isEmpty()) {
            throw new NoResultsFoundException("Playlist is empty");
        }

        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
        List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), items, mayInterrupt);
        handleResults(playables);
        loadedLocalList = playlist;
    }

    private void loadYouTubeList(AudioManager audioManager) throws IOException {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());

        if (argumentSet("select")) {
            int limit = getArgumentValue("select", Integer.class, 10);
            if (!(limit > 0 && limit <= 20)) {
                throw new InvalidCommandException("Limit must be between 1 and 20");
            }

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandInput());
            if (playlists.size() == 1) {
                YouTubePlaylist playlist = playlists.get(0);
                List<Playable> playables = playableFactory.createPlayables(playlist, mayInterrupt);
                handleResults(playables);
                loadedYouTubePlaylist = playlist;
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube playlist found for '%s'", getCommandInput()));
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandInput());
            List<Playable> playables = playableFactory.createPlayables(youTubePlaylist, mayInterrupt);
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
                PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
                List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), playlistTracks, mayInterrupt);
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
            PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
            List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), tracks, mayInterrupt);
            handleResults(playables);
            loadedAlbum = album;
        } else if (albums.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No albums found for '%s'", getCommandInput()));
        } else {
            askQuestion(albums, AlbumSimplified::getName, album -> StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "));
        }
    }

    private void loadTrack(AudioManager audioManager) throws Exception {
        int limit = argumentSet("select") ? getArgumentValue("select", Integer.class, 20) : 20;
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

    private void createPlayableForTrack(Track track, AudioManager audioManager) throws IOException {
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
        Playable playable = playableFactory.createPlayable(!argumentSet("preview"), track);
        handleResults(Lists.newArrayList(playable));
        loadedTrack = playable;
    }

    private void loadYouTubeVideo(AudioManager audioManager) throws IOException {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        if (argumentSet("select")) {
            int limit = getArgumentValue("select", Integer.class, 10);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

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
                        return youTubeVideo.getTitle();
                    } catch (UnavailableResourceException e) {
                        // Unreachable since only HollowYouTubeVideos might get interrupted
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandInput());
            audioManager.getQueue(getContext().getGuild()).add(youTubeVideo);
            handleResults(Lists.newArrayList(youTubeVideo));
            loadedTrack = youTubeVideo;
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("list").setRequiresInput(true)
            .setDescription("Search for a youtube, spotify or botify playlist. Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("preview").setRequiresInput(true)
            .setDescription("Load the short preview mp3 directly from Spotify instead of the full track from YouTube.")
            .addRule(ac -> getSource().isSpotify(), "Argument 'preview' may only be used with Spotify.");
        argumentContribution.map("spotify").setRequiresInput(true).excludesArguments("youtube")
            .setDescription("Search for a Spotify track, list or album. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("youtube").setRequiresInput(true).excludesArguments("spotify")
            .setDescription("Play a YouTube video or playlist. Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("own").setRequiresInput(true)
            .setDescription("Limit search to Spotify tracks, lists or albums that are in the current user's library. This requires a Spotify login.")
            .addRule(ac -> getSource().isSpotify(), "Argument 'own' may only be used with Spotify.");
        argumentContribution.map("local").needsArguments("list").excludesArguments("spotify", "youtube")
            .setDescription("Search for a local botify playlist.");
        argumentContribution.map("album").excludesArguments("list").setRequiresInput(true)
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.")
            .addRule(ac -> getSource().isSpotify(), "Argument 'album' may only be used with Spotify.");
        argumentContribution.map("select").excludesArguments("album").setRequiresInput(true)
            .setDescription("Show a selection of YouTube playlists / videos or Spotify tracks to chose from. May be assigned a value from 1 to 20: $select=5")
            .addRule(ac -> {
                Source source = getSource();
                if (ac.argumentSet("list")) {
                    return source.isYouTube();
                }

                return source.isYouTube() || source.isSpotify();
            }, "Argument 'select' may only be used with YouTube videos / playlists or Spotify tracks.");
        return argumentContribution;
    }

}
