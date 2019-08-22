package net.robinfriedli.botify.command.commands;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.commons.validator.routines.UrlValidator;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.spotify.SpotifyUri;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.exceptions.NoSpotifyResultsFoundException;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class AddCommand extends AbstractSourceDecidingCommand {

    public AddCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() throws Exception {
        Session session = getContext().getSession();
        if (argumentSet("queue")) {
            AudioQueue queue = Botify.get().getAudioManager().getQueue(getContext().getGuild());
            if (queue.isEmpty()) {
                throw new InvalidCommandException("Queue is empty");
            }

            Playlist playlist = SearchEngine.searchLocalList(session, getToAddString(), isPartitioned(), getContext().getGuild().getId());
            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", getToAddString()));
            }

            List<Playable> tracks = queue.getTracks();
            addPlayables(playlist, tracks);
        } else {
            String playlistName = getArgumentValue("to");
            Playlist playlist = SearchEngine.searchLocalList(session, playlistName, isPartitioned(), getContext().getGuild().getId());

            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", playlistName));
            }

            if (UrlValidator.getInstance().isValid(getCommandInput())) {
                addUrl(playlist, getCommandInput());
            } else if (SpotifyUri.isSpotifyUri(getCommandInput())) {
                addSpotifyUri(playlist, SpotifyUri.parse(getCommandInput()));
            } else {
                if (argumentSet("list")) {
                    addList(playlist, getCommandInput());
                } else if (argumentSet("album")) {
                    addSpotifyAlbum(playlist, getCommandInput());
                } else {
                    addSpecificTrack(playlist, getCommandInput());
                }
            }
        }
    }

    protected void addToList(Playlist playlist, List<PlaylistItem> items) {
        if (items.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        Session session = getContext().getSession();
        invoke(() -> {
            checkSize(playlist, items.size());
            items.forEach(item -> {
                item.add();
                session.persist(item);
            });
        });
    }

    protected void addToList(Playlist playlist, PlaylistItem item) {
        addToList(playlist, Collections.singletonList(item));
    }

    private void addPlayables(Playlist playlist, List<Playable> playables) {
        Session session = getContext().getSession();
        List<PlaylistItem> items = Lists.newArrayList();
        invoke(() -> {
            playables.forEach(playable -> {
                if (playable instanceof HollowYouTubeVideo) {
                    HollowYouTubeVideo video = (HollowYouTubeVideo) playable;
                    video.awaitCompletion();
                    if (video.isCanceled()) {
                        return;
                    }
                }

                items.add(playable.export(playlist, getContext().getUser(), session));
            });
            addToList(playlist, items);
        });
    }

    private void addUrl(Playlist playlist, String url) throws IOException {
        AudioManager audioManager = Botify.get().getAudioManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
        List<Playable> playables = playableFactory.createPlayables(url, getContext().getSpotifyApi(), false, false);
        addPlayables(playlist, playables);
    }

    private void addSpotifyUri(Playlist playlist, SpotifyUri spotifyUri) throws Exception {
        AudioManager audioManager = Botify.get().getAudioManager();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild(), getSpotifyService());
        List<Playable> playables = spotifyUri.loadPlayables(playableFactory, getContext().getSpotifyService(), false, false);
        addPlayables(playlist, playables);
    }

    private void addList(Playlist playlist, String searchTerm) throws Exception {
        Source source = getSource();
        if (source.isSpotify()) {
            addSpotifyList(playlist, searchTerm);
        } else if (source.isYouTube()) {
            addYouTubeList(playlist, searchTerm);
        } else {
            addLocalList(playlist, searchTerm);
        }
    }

    private void addSpotifyList(Playlist playlist, String searchTerm) throws Exception {
        Callable<Void> callable = () -> {
            List<PlaylistSimplified> playlists;
            if (argumentSet("own")) {
                playlists = getSpotifyService().searchOwnPlaylist(searchTerm);
            } else {
                playlists = getSpotifyService().searchPlaylist(searchTerm);
            }

            if (playlists.size() == 1) {
                List<Track> playlistTracks = getSpotifyService().getPlaylistTracks(playlists.get(0));
                invoke(() -> {
                    List<PlaylistItem> songs = playlistTracks
                        .stream()
                        .map(t -> new Song(t, getContext().getUser(), playlist, getContext().getSession()))
                        .collect(Collectors.toList());
                    addToList(playlist, songs);
                });
            } else if (playlists.isEmpty()) {
                throw new NoSpotifyResultsFoundException(String.format("No Spotify playlists found for '%s'", searchTerm));
            } else {
                askQuestion(playlists, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
            }

            return null;
        };

        if (argumentSet("own")) {
            runWithLogin(callable);
        } else {
            runWithCredentials(callable);
        }
    }

    private void addYouTubeList(Playlist playlist, String searchTerm) throws IOException {
        YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, searchTerm);
            if (playlists.size() == 1) {
                YouTubePlaylist youTubePlaylist = playlists.get(0);
                loadYouTubeList(youTubePlaylist, playlist, youTubeService);
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube playlists found for '%s'", searchTerm));
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(searchTerm);
            loadYouTubeList(youTubePlaylist, playlist, youTubeService);
        }
    }

    private void addLocalList(Playlist playlist, String searchTerm) {
        Playlist targetList = SearchEngine.searchLocalList(getContext().getSession(), searchTerm, isPartitioned(), getContext().getGuild().getId());

        if (targetList == null) {
            throw new InvalidCommandException(String.format("No local playlist found for '%s'", searchTerm));
        }

        List<PlaylistItem> items = targetList.getItemsSorted().stream().map(item -> item.copy(playlist)).collect(Collectors.toList());
        addToList(playlist, items);
    }

    private void loadYouTubeList(YouTubePlaylist youTubePlaylist, Playlist playlist, YouTubeService youTubeService) {
        getContext().getGuildContext().getTrackLoadingExecutor().load(() -> {
            try {
                youTubeService.populateList(youTubePlaylist);
            } catch (IOException e) {
                youTubePlaylist.cancelLoading();
                throw e;
            }
        }, false);
        List<HollowYouTubeVideo> videos = youTubePlaylist.getVideos();
        List<PlaylistItem> items = Lists.newArrayList();
        for (HollowYouTubeVideo video : videos) {
            video.awaitCompletion();
            if (!video.isCanceled()) {
                items.add(new Video(video, getContext().getUser(), playlist));
            }
        }

        addToList(playlist, items);
    }

    private void addSpotifyAlbum(Playlist playlist, String searchTerm) throws Exception {
        Callable<List<AlbumSimplified>> albumLoadCallable = () -> getSpotifyService().searchAlbum(searchTerm, argumentSet("own"));
        List<AlbumSimplified> albums;
        if (argumentSet("own")) {
            albums = runWithLogin(albumLoadCallable);
        } else {
            albums = runWithCredentials(albumLoadCallable);
        }

        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(album.getId()));
            invoke(() -> {
                List<PlaylistItem> songs = tracks
                    .stream()
                    .map(t -> new Song(t, getContext().getUser(), playlist, getContext().getSession()))
                    .collect(Collectors.toList());
                addToList(playlist, songs);
            });
        } else if (albums.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No Spotify album found for '%s'", searchTerm));
        } else {
            askQuestion(
                albums,
                AlbumSimplified::getName,
                album -> String.valueOf(StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "))
            );
        }
    }

    private void addSpecificTrack(Playlist playlist, String searchTerm) throws Exception {
        Source source = getSource();
        if (source.isYouTube()) {
            YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
            if (argumentSet("limit")) {
                int limit = getArgumentValue("limit", Integer.class);
                if (!(limit > 0 && limit <= 10)) {
                    throw new InvalidCommandException("Limit must be between 1 and 10");
                }
                List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, searchTerm);
                if (youTubeVideos.size() == 1) {
                    addToList(playlist, new Video(youTubeVideos.get(0), getContext().getUser(), playlist));
                } else if (youTubeVideos.isEmpty()) {
                    throw new NoResultsFoundException(String.format("No YouTube videos found for '%s'", searchTerm));
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
                YouTubeVideo youTubeVideo = youTubeService.searchVideo(searchTerm);
                addToList(playlist, new Video(youTubeVideo, getContext().getUser(), playlist));
            }
        } else {
            Callable<List<Track>> trackLoadCallable = () -> getSpotifyService().searchTrack(searchTerm, argumentSet("own"));
            List<Track> tracks;
            if (argumentSet("own")) {
                tracks = runWithLogin(trackLoadCallable);
            } else {
                tracks = runWithCredentials(trackLoadCallable);
            }

            if (tracks.size() == 1) {
                invoke(() -> addToList(playlist, new Song(tracks.get(0), getContext().getUser(), playlist, getContext().getSession())));
            } else if (tracks.isEmpty()) {
                throw new NoSpotifyResultsFoundException(String.format("No Spotify track found for '%s'", searchTerm));
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
        if (!Strings.isNullOrEmpty(playlistSizeMax)) {
            int maxSize = Integer.parseInt(playlistSizeMax);
            if (playlist.getSize() + toAddSize > maxSize) {
                throw new InvalidCommandException("List exceeds maximum size of " + maxSize + " items!");
            }
        }
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }

    @Override
    public void withUserResponse(Object option) {
        Session session = getContext().getSession();
        String playlistName = getArgumentValue("to");

        Playlist playlist = SearchEngine.searchLocalList(session, playlistName, isPartitioned(), getContext().getGuild().getId());
        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local list found for '%s'", getToAddString()));
        }

        invoke(() -> {
            if (option instanceof Collection) {
                for (Object o : ((Collection) option)) {
                    handleOption(o, playlist, session);
                }
            } else {
                handleOption(option, playlist, session);
            }
        });
    }

    private void handleOption(Object option, Playlist playlist, Session session) throws Exception {
        if (option instanceof Track) {
            invoke(() -> addToList(playlist, new Song((Track) option, getContext().getUser(), playlist, session)));
        } else if (option instanceof YouTubeVideo) {
            addToList(playlist, new Video((YouTubeVideo) option, getContext().getUser(), playlist));
        } else if (option instanceof PlaylistSimplified) {
            List<Track> playlistTracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks((PlaylistSimplified) option));
            invoke(() -> {
                List<PlaylistItem> songs = playlistTracks.stream().map(t -> new Song(t, getContext().getUser(), playlist, session)).collect(Collectors.toList());
                addToList(playlist, songs);
            });
        } else if (option instanceof YouTubePlaylist) {
            YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
            loadYouTubeList((YouTubePlaylist) option, playlist, youTubeService);
        } else if (option instanceof AlbumSimplified) {
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(((AlbumSimplified) option).getId()));
            invoke(() -> {
                List<PlaylistItem> songs = tracks.stream().map(t -> new Song(t, getContext().getUser(), playlist, session)).collect(Collectors.toList());
                addToList(playlist, songs);
            });
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("to")
            .setDescription("Mandatory argument to define the playlist to which you want to add the tracks.");
        argumentContribution.map("youtube").excludesArguments("spotify")
            .setDescription("Add specific video from YouTube. Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("spotify").excludesArguments("youtube")
            .setDescription("Add specific Spotify track. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("queue").excludesArguments("youtube", "spotify")
            .setDescription("Add items from the current queue.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to tracks in your library. This requires a Spotify login.");
        argumentContribution.map("list")
            .setDescription("Add tracks from a Spotify, YouTube or local list to a list.");
        argumentContribution.map("local").needsArguments("list").excludesArguments("youtube", "spotify")
            .setDescription("Add items from a local list. This is the default option when adding lists.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of youtube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        argumentContribution.map("album").needsArguments("spotify").excludesArguments("list")
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
        return argumentContribution;
    }

    protected String getToAddString() {
        return getCommandInput();
    }

}
