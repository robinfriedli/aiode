package net.robinfriedli.botify.command.commands;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.exceptions.NoSpotifyResultsFoundException;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table2;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class SearchCommand extends AbstractSourceDecidingCommand {

    public SearchCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, false, identifier, description, Category.SEARCH);
    }

    @Override
    public void doRun() throws Exception {
        Source source = getSource();
        if (argumentSet("list")) {
            if (source.isSpotify()) {
                listSpotifyList();
            } else if (source.isYouTube()) {
                listYouTubePlaylists();
            } else {
                listLocalList();
            }
        } else if (argumentSet("album")) {
            listSpotifyAlbum();
        } else {
            if (source.isYouTube()) {
                searchYouTubeVideo();
            } else {
                searchSpotifyTrack();
            }
        }
    }

    private void searchSpotifyTrack() throws Exception {
        if (getCommandInput().isBlank()) {
            throw new InvalidCommandException("No search term entered");
        }

        int limit = argumentSet("select") ? getArgumentValue("select", Integer.class, 20) : 20;
        Callable<List<Track>> loadTrackCallable = () -> getSpotifyService().searchTrack(getCommandInput(), argumentSet("own"), limit);
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(loadTrackCallable);
        } else {
            found = runWithCredentials(loadTrackCallable);
        }
        if (!found.isEmpty()) {
            EmbedBuilder embedBuilder = new EmbedBuilder();

            Util.appendEmbedList(
                embedBuilder,
                found,
                track -> track.getName() + " - " + track.getAlbum().getName() + " - " +
                    StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
                "Track - Album - Artist"
            );

            sendMessage(embedBuilder);
        } else {
            throw new NoSpotifyResultsFoundException(String.format("No Spotify track found for '%s'", getCommandInput()));
        }
    }

    private void searchYouTubeVideo() throws UnavailableResourceException, IOException {
        YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
        if (argumentSet("select")) {
            int limit = getArgumentValue("select", Integer.class, 10);

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandInput());
            if (youTubeVideos.size() == 1) {
                listYouTubeVideo(youTubeVideos.get(0));
            } else if (youTubeVideos.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube videos found for '%s'", getCommandInput()));
            } else {
                askQuestion(youTubeVideos, youTubeVideo -> {
                    try {
                        return youTubeVideo.getTitle();
                    } catch (UnavailableResourceException e) {
                        // Unreachable since only HollowYouTubeVideos might get cancelled
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            listYouTubeVideo(youTubeService.searchVideo(getCommandInput()));
        }
    }

    private void listYouTubeVideo(YouTubeVideo youTubeVideo) throws UnavailableResourceException {
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Title: ").append(youTubeVideo.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Id: ").append(youTubeVideo.getVideoId()).append(System.lineSeparator());
        responseBuilder.append("Link: ").append("https://www.youtube.com/watch?v=").append(youTubeVideo.getVideoId()).append(System.lineSeparator());
        responseBuilder.append("Duration: ").append(Util.normalizeMillis(youTubeVideo.getDuration()));

        sendMessage(responseBuilder.toString());
    }

    private void listLocalList() {
        if (getCommandInput().isBlank()) {
            Session session = getContext().getSession();
            List<Playlist> playlists;
            if (isPartitioned()) {
                playlists = session.createQuery("from " + Playlist.class.getName() + " where guild_id = '" + getContext().getGuild().getId() + "'", Playlist.class).getResultList();
            } else {
                playlists = session.createQuery("from " + Playlist.class.getName(), Playlist.class).getResultList();
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();

            if (playlists.isEmpty()) {
                embedBuilder.setDescription("No playlists");
            } else {
                Table2 table = new Table2(embedBuilder);
                table.addColumn("Playlist", playlists, Playlist::getName);
                table.addColumn("Duration", playlists, playlist -> Util.normalizeMillis(playlist.getDuration()));
                table.addColumn("Items", playlists, playlist -> String.valueOf(playlist.getSize()));
                table.build();
            }

            sendMessage(embedBuilder);
        } else {
            Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandInput(), isPartitioned(), getContext().getGuild().getId());
            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", getCommandInput()));
            }

            String createdUserId = playlist.getCreatedUserId();
            String createdUser;
            if (createdUserId.equals("system")) {
                createdUser = playlist.getCreatedUser();
            } else {
                ShardManager shardManager = Botify.get().getShardManager();
                User userById = shardManager.getUserById(createdUserId);
                createdUser = userById != null ? userById.getName() : playlist.getCreatedUser();
            }


            String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.addField("Name", playlist.getName(), true);
            embedBuilder.addField("Duration", Util.normalizeMillis(playlist.getDuration()), true);
            embedBuilder.addField("Created by", createdUser, true);
            embedBuilder.addField("Tracks", String.valueOf(playlist.getSize()), true);
            embedBuilder.addBlankField(false);

            String url = baseUri +
                String.format("/list?name=%s&guildId=%s", URLEncoder.encode(playlist.getName(), StandardCharsets.UTF_8), playlist.getGuildId());
            embedBuilder.addField("First tracks:", "[Full list](" + url + ")", false);

            List<PlaylistItem> items = playlist.getItemsSorted();
            Util.appendEmbedList(
                embedBuilder,
                items.size() > 5 ? items.subList(0, 5) : items,
                item -> item.display() + " - " + Util.normalizeMillis(item.getDuration()),
                "Track - Duration"
            );

            sendWithLogo(embedBuilder);
        }
    }

    private void listYouTubePlaylists() throws IOException {
        YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
        if (argumentSet("select")) {
            int limit = getArgumentValue("select", Integer.class, 10);

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandInput());
            if (playlists.size() == 1) {
                listYouTubePlaylist(playlists.get(0));
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException(String.format("No YouTube playlist found for '%s'", getCommandInput()));
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            listYouTubePlaylist(youTubeService.searchPlaylist(getCommandInput()));
        }
    }

    private void listYouTubePlaylist(YouTubePlaylist youTubePlaylist) {
        if (getCommandInput().isBlank()) {
            throw new InvalidCommandException("Command body may not be empty when searching YouTube list");
        }

        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Title: ").append(youTubePlaylist.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Url: ").append(youTubePlaylist.getUrl()).append(System.lineSeparator());
        responseBuilder.append("Videos: ").append(youTubePlaylist.getVideos().size()).append(System.lineSeparator());
        responseBuilder.append("Owner: ").append(youTubePlaylist.getChannelTitle());

        sendMessage(responseBuilder.toString());
    }

    private void listSpotifyList() throws Exception {
        String commandBody = getCommandInput();

        if (commandBody.isBlank()) {
            throw new InvalidCommandException("Command may not be empty when searching spotify lists");
        }

        List<PlaylistSimplified> playlists;
        if (argumentSet("own")) {
            playlists = runWithLogin(() -> getSpotifyService().searchOwnPlaylist(getCommandInput()));
        } else {
            playlists = runWithCredentials(() -> getSpotifyService().searchPlaylist(getCommandInput()));
        }
        if (playlists.size() == 1) {
            PlaylistSimplified playlist = playlists.get(0);
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks(playlist));
            listTracks(tracks, playlist.getName(), playlist.getOwner().getDisplayName(), null, "playlist/" + playlist.getId());
        } else if (playlists.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No Spotify playlist found for '%s'", getCommandInput()));
        } else {
            askQuestion(playlists, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
        }
    }

    private void listSpotifyAlbum() throws Exception {
        Callable<List<AlbumSimplified>> loadAlbumsCallable = () -> getSpotifyService().searchAlbum(getCommandInput(), argumentSet("own"));
        List<AlbumSimplified> albums;
        if (argumentSet("own")) {
            albums = runWithLogin(loadAlbumsCallable);
        } else {
            albums = runWithCredentials(loadAlbumsCallable);
        }

        if (albums.size() == 1) {
            AlbumSimplified album = albums.get(0);
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(album.getId()));
            listTracks(
                tracks,
                album.getName(),
                null,
                StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
                "album/" + album.getId()
            );
        } else if (albums.isEmpty()) {
            throw new NoSpotifyResultsFoundException(String.format("No album found for '%s'", getCommandInput()));
        } else {
            askQuestion(albums, AlbumSimplified::getName, album -> StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "));
        }
    }

    private void listTracks(List<Track> tracks, String name, String owner, String artist, String path) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        long totalDuration = tracks.stream().mapToInt(track -> {
            Integer durationMs = track.getDurationMs();
            if (durationMs != null) {
                return durationMs;
            } else {
                return 0;
            }
        }).sum();

        embedBuilder.addField("Name", name, true);
        embedBuilder.addField("Song count", String.valueOf(tracks.size()), true);
        embedBuilder.addField("Duration", Util.normalizeMillis(totalDuration), true);
        if (owner != null) {
            embedBuilder.addField("Owner", owner, true);
        }
        if (artist != null) {
            embedBuilder.addField("Artist", artist, true);
        }

        if (!tracks.isEmpty()) {
            String url = "https://open.spotify.com/" + path;
            embedBuilder.addField("First tracks:", "[Full list](" + url + ")", false);

            Util.appendEmbedList(
                embedBuilder,
                tracks.size() > 5 ? tracks.subList(0, 5) : tracks,
                track -> track.getName() + " - " +
                    StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ") + " - " +
                    Util.normalizeMillis(track.getDurationMs() != null ? track.getDurationMs() : 0),
                "Track - Artist - Duration"
            );
        }

        sendMessage(embedBuilder);
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        if (chosenOption instanceof Collection) {
            throw new InvalidCommandException("Cannot select more than one result");
        }

        if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks(playlist));
            listTracks(tracks, playlist.getName(), playlist.getOwner().getDisplayName(), null, "playlist/" + playlist.getId());
        } else if (chosenOption instanceof YouTubePlaylist) {
            listYouTubePlaylist((YouTubePlaylist) chosenOption);
        } else if (chosenOption instanceof YouTubeVideo) {
            listYouTubeVideo((YouTubeVideo) chosenOption);
        } else if (chosenOption instanceof AlbumSimplified) {
            AlbumSimplified album = (AlbumSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(album.getId()));
            listTracks(tracks,
                album.getName(),
                null,
                StringListImpl.create(album.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
                "album/" + album.getId());
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("spotify").excludesArguments("youtube").setRequiresInput(true)
            .setDescription("Search for Spotify track or playlist. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). This is the default option when searching for tracks.");
        argumentContribution.map("youtube").excludesArguments("spotify").setRequiresInput(true)
            .setDescription("Search for YouTube video or playlist.");
        argumentContribution.map("list")
            .setDescription("Search for a playlist.");
        argumentContribution.map("local").needsArguments("list").excludesArguments("youtube", "spotify")
            .setDescription("Search for a local playlist or list all of them. This is default when searching for lists.");
        argumentContribution.map("own")
            .setDescription("Limit search to Spotify tracks or playlists in the current user's library. This requires a Spotify login.")
            .addRule(ac -> getSource().isSpotify(), "Argument 'own' may only be used with Spotify.");
        argumentContribution.map("select").excludesArguments("album")
            .setDescription("Show a selection of YouTube playlists / videos or Spotify tracks to chose from. May be assigned a value from 1 to 20: $select=5")
            .addRule(ac -> {
                Source source = getSource();
                if (ac.argumentSet("list")) {
                    return source.isYouTube();
                }

                return source.isYouTube() || source.isSpotify();
            }, "Argument 'select' may only be used with YouTube videos / playlists or Spotify tracks.")
            .verifyValue(Integer.class, limit -> limit > 0 && limit <= 20, "Limit must be between 1 and 20");
        argumentContribution.map("album").excludesArguments("list").setRequiresInput(true)
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.")
            .addRule(ac -> getSource().isSpotify(), "Argument 'album' may only be used with Spotify.");
        return argumentContribution;
    }

}
