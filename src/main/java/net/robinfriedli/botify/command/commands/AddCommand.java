package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.validator.routines.UrlValidator;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableImpl;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class AddCommand extends AbstractCommand {

    public AddCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        if (argumentSet("queue")) {
            AudioQueue queue = getManager().getAudioManager().getQueue(getContext().getGuild());
            if (queue.isEmpty()) {
                throw new InvalidCommandException("Queue is empty");
            }

            Playlist playlist = SearchEngine.searchLocalList(session, getCommandBody(), isPartitioned(), getContext().getGuild().getId());
            if (playlist == null) {
                throw new NoResultsFoundException("No local list found for " + getCommandBody());
            }

            List<Playable> tracks = queue.getTracks();
            checkSize(playlist, tracks.size());
            invoke(() -> {
                for (Playable track : tracks) {
                    session.persist(track.export(playlist, getContext().getUser(), session));
                }
            });
        } else {
            Pair<String, String> pair = splitInlineArgument("to");
            Playlist playlist = SearchEngine.searchLocalList(session, pair.getRight(), isPartitioned(), getContext().getGuild().getId());

            if (playlist == null) {
                throw new NoResultsFoundException("No local list found for " + pair.getRight());
            }

            invoke(() -> {
                if (UrlValidator.getInstance().isValid(pair.getLeft())) {
                    addUrl(playlist, pair.getLeft());
                } else {
                    if (argumentSet("list")) {
                        addList(playlist, pair.getLeft());
                    } else if (argumentSet("album")) {
                        addSpotifyAlbum(playlist, pair.getLeft());
                    } else {
                        addSpecificTrack(playlist, pair.getLeft());
                    }
                }
            });
        }
    }

    private void addUrl(Playlist playlist, String url) {
        AudioManager audioManager = getManager().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
        List<Playable> playables = audioManager.createPlayables(url, playback, getContext().getSpotifyApi(), false, false);
        Session session = getContext().getSession();

        checkSize(playlist, playables.size());
        invoke(() -> playables.forEach(playable -> {
            if (playable instanceof PlayableImpl && ((PlayableImpl) playable).delegate() instanceof HollowYouTubeVideo) {
                HollowYouTubeVideo video = (HollowYouTubeVideo) ((PlayableImpl) playable).delegate();
                video.awaitCompletion();
                if (video.isCanceled()) {
                    return;
                }
            }

            session.persist(playable.export(playlist, getContext().getUser(), session));
        }));
    }

    private void addList(Playlist playlist, String searchTerm) throws Exception {
        if (argumentSet("spotify")) {
            SpotifyApi spotifyApi = getContext().getSpotifyApi();
            Callable<Void> callable = () -> {
                List<PlaylistSimplified> playlists;
                if (argumentSet("own")) {
                    playlists = SearchEngine.searchOwnPlaylist(spotifyApi, searchTerm);
                } else {
                    playlists = SearchEngine.searchSpotifyPlaylist(spotifyApi, searchTerm);
                }

                if (playlists.size() == 1) {
                    List<Track> playlistTracks = SearchEngine.getPlaylistTracks(spotifyApi, playlists.get(0));
                    List<PlaylistItem> songs = playlistTracks
                        .stream()
                        .map(t -> new Song(t, getContext().getUser(), playlist, getContext().getSession()))
                        .collect(Collectors.toList());
                    checkSize(playlist, songs.size());
                    songs.forEach(item -> getContext().getSession().persist(item));
                } else if (playlists.isEmpty()) {
                    throw new NoResultsFoundException("No Spotify playlists found for " + searchTerm);
                } else {
                    askQuestion(playlists, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
                }

                return null;
            };

            if (argumentSet("own")) {
                runWithLogin(getContext().getUser(), callable);
            } else {
                runWithCredentials(callable);
            }
        } else if (argumentSet("youtube")) {
            YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
            if (argumentSet("limit")) {
                int limit = getArgumentValue("limit", Integer.class);
                if (!(limit > 0 && limit <= 10)) {
                    throw new InvalidCommandException("Limit must be between 1 and 10");
                }

                List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, searchTerm);
                if (playlists.size() == 1) {
                    YouTubePlaylist youTubePlaylist = playlists.get(0);
                    addYouTubeList(youTubePlaylist, playlist, youTubeService, getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild()));
                } else if (playlists.isEmpty()) {
                    throw new NoResultsFoundException("No YouTube playlists found for " + searchTerm);
                } else {
                    askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
                }
            } else {
                YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(searchTerm);
                addYouTubeList(youTubePlaylist, playlist, youTubeService, getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild()));
            }
        } else {
            Playlist targetList = SearchEngine.searchLocalList(getContext().getSession(), searchTerm, isPartitioned(), getContext().getGuild().getId());

            if (targetList == null) {
                throw new InvalidCommandException("No local playlist found for " + searchTerm);
            }

            List<PlaylistItem> items = targetList.getPlaylistItems().stream().map(item -> item.copy(playlist)).collect(Collectors.toList());
            checkSize(playlist, items.size());
            items.forEach(item -> getContext().getSession().persist(item));
        }
    }

    private void addYouTubeList(YouTubePlaylist youTubePlaylist, Playlist playlist, YouTubeService youTubeService, AudioPlayback playback) {
        playback.load(() -> youTubeService.populateList(youTubePlaylist), false);
        List<HollowYouTubeVideo> videos = youTubePlaylist.getVideos();
        checkSize(playlist, videos.size());
        for (HollowYouTubeVideo video : videos) {
            video.awaitCompletion();
            if (!video.isCanceled()) {
                getContext().getSession().persist(new Video(video, getContext().getUser(), playlist));
            }
        }

    }

    private void addSpotifyAlbum(Playlist playlist, String searchTerm) throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        List<AlbumSimplified> albums = runWithCredentials(() -> SearchEngine.searchSpotifyAlbum(spotifyApi, searchTerm));

        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getAlbumTracks(spotifyApi, album.getId()));
            List<Song> songs = tracks
                .stream()
                .map(t -> new Song(t, getContext().getUser(), playlist, getContext().getSession()))
                .collect(Collectors.toList());
            checkSize(playlist, songs.size());
            songs.forEach(s -> getContext().getSession().persist(s));
        } else if (albums.isEmpty()) {
            throw new NoResultsFoundException("No Spotify album found for " + searchTerm);
        } else {
            askQuestion(
                albums,
                AlbumSimplified::getName,
                album -> String.valueOf(StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "))
            );
        }
    }

    private void addSpecificTrack(Playlist playlist, String searchTerm) throws Exception {
        if (argumentSet("youtube")) {
            YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
            if (argumentSet("limit")) {
                int limit = getArgumentValue("limit", Integer.class);
                if (!(limit > 0 && limit <= 10)) {
                    throw new InvalidCommandException("Limit must be between 1 and 10");
                }
                List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, searchTerm);
                if (youTubeVideos.size() == 1) {
                    checkSize(playlist, 1);
                    getContext().getSession().persist(new Video(youTubeVideos.get(0), getContext().getUser(), playlist));
                } else if (youTubeVideos.isEmpty()) {
                    throw new NoResultsFoundException("No YouTube videos found for " + searchTerm);
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
                YouTubeVideo youTubeVideo = youTubeService.searchVideo(searchTerm);
                checkSize(playlist, 1);
                getContext().getSession().persist(new Video(youTubeVideo, getContext().getUser(), playlist));
            }
        } else {
            SpotifyApi spotifyApi = getContext().getSpotifyApi();
            List<Track> tracks;
            if (argumentSet("own")) {
                tracks = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, searchTerm));
            } else {
                tracks = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, searchTerm));
            }

            if (tracks.size() == 1) {
                checkSize(playlist, 1);
                getContext().getSession().persist(new Song(tracks.get(0), getContext().getUser(), playlist, getContext().getSession()));
            } else if (tracks.isEmpty()) {
                throw new NoResultsFoundException("No spotify track found for " + searchTerm);
            } else {
                askQuestion(tracks, track -> {
                    String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                    return String.format("%s by %s", track.getName(), artistString);
                }, track -> track.getAlbum().getName());
            }
        }
    }

    private void checkSize(Playlist playlist, int toAddSize) {
        String playlistSizeMax = PropertiesLoadingService.loadProperty("PLAYLIST_SIZE_MAX");
        if (playlistSizeMax != null) {
            int maxSize = Integer.parseInt(playlistSizeMax);
            if (playlist.getSongCount() + toAddSize > maxSize) {
                throw new InvalidCommandException("List exceeds maximum size of " + maxSize + " items!");
            }
        }
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }

    @Override
    public void withUserResponse(Object option) {
        Session session = getContext().getSession();
        Pair<String, String> pair = splitInlineArgument("to");

        Playlist playlist = SearchEngine.searchLocalList(session, pair.getRight(), isPartitioned(), getContext().getGuild().getId());
        if (playlist == null) {
            throw new NoResultsFoundException("No local list found for " + getCommandBody());
        }

        invoke(() -> {
            if (option instanceof Track) {
                checkSize(playlist, 1);
                session.persist(new Song((Track) option, getContext().getUser(), playlist, session));
            } else if (option instanceof YouTubeVideo) {
                checkSize(playlist, 1);
                session.persist(new Video((YouTubeVideo) option, getContext().getUser(), playlist));
            } else if (option instanceof PlaylistSimplified) {
                SpotifyApi spotifyApi = getContext().getSpotifyApi();
                List<Track> playlistTracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, (PlaylistSimplified) option));
                List<PlaylistItem> songs = playlistTracks.stream().map(t -> new Song(t, getContext().getUser(), playlist, session)).collect(Collectors.toList());
                checkSize(playlist, songs.size());
                songs.forEach(session::persist);
            } else if (option instanceof YouTubePlaylist) {
                YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
                addYouTubeList((YouTubePlaylist) option, playlist, youTubeService, getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild()));
            } else if (option instanceof AlbumSimplified) {
                SpotifyApi spotifyApi = getContext().getSpotifyApi();
                List<Track> tracks = runWithCredentials(() -> SearchEngine.getAlbumTracks(spotifyApi, ((AlbumSimplified) option).getId()));
                List<Song> songs = tracks.stream().map(t -> new Song(t, getContext().getUser(), playlist, session)).collect(Collectors.toList());
                checkSize(playlist, songs.size());
                songs.forEach(session::persist);
            }
        });
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("youtube").excludesArguments("spotify")
            .setDescription("Add specific video from YouTube. Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("spotify").excludesArguments("youtube")
            .setDescription("Add specific Spotify track. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("queue").excludesArguments("youtube", "spotify")
            .setDescription("Add items from the current queue.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to tracks in your library. This requires a Spotify login. Spotify search filters (\"artist:\", \"album:\" etc.) are not supported with this argument.");
        argumentContribution.map("list")
            .setDescription("Add tracks from a Spotify, YouTube or local list to a list.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Add items from a local list. This is the default option when adding lists.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of youtube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        argumentContribution.map("album").needsArguments("spotify").excludesArguments("own")
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
        return argumentContribution;
    }
}
