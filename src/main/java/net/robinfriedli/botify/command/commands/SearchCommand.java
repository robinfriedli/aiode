package net.robinfriedli.botify.command.commands;

import java.awt.Color;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.EmbedBuilder;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class SearchCommand extends AbstractCommand {

    public SearchCommand(CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContext, commandManager, commandString, false, identifier, description, Category.SEARCH);
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("list")) {
            if (argumentSet("spotify")) {
                listSpotifyList();
            } else if (argumentSet("youtube")) {
                listYouTubePlaylists();
            } else {
                listLocalList();
            }
        } else {
            if (argumentSet("youtube")) {
                searchYouTubeVideo();
            } else {
                searchSpotifyTrack();
            }
        }
    }

    private void searchSpotifyTrack() throws Exception {
        if (getCommandBody().isBlank()) {
            throw new InvalidCommandException("No search term entered");
        }

        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, getCommandBody()));
        } else {
            found = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, getCommandBody()));
        }
        if (!found.isEmpty()) {
            EmbedBuilder embedBuilder = new EmbedBuilder();

            StringBuilder trackListBuilder = new StringBuilder();
            StringBuilder albumListBuilder = new StringBuilder();
            StringBuilder artistListBuilder = new StringBuilder();

            for (Track track : found) {
                trackListBuilder.append(track.getName()).append(System.lineSeparator());
                albumListBuilder.append(track.getAlbum().getName()).append(System.lineSeparator());
                String artist = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                artistListBuilder.append(artist).append(System.lineSeparator());
            }

            embedBuilder.addField("Track", trackListBuilder.toString(), true);
            embedBuilder.addField("Album", albumListBuilder.toString(), true);
            embedBuilder.addField("Artist", artistListBuilder.toString(), true);
            embedBuilder.setColor(Color.decode("#1DB954"));

            sendMessage(getContext().getChannel(), embedBuilder.build());
        } else {
            sendMessage(getContext().getChannel(), "No results found");
        }
    }

    private void searchYouTubeVideo() throws InterruptedException {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandBody());
            if (youTubeVideos.size() == 1) {
                listYouTubeVideo(youTubeVideos.get(0));
            } else if (youTubeVideos.isEmpty()) {
                throw new NoResultsFoundException("No YouTube videos found for " + getCommandBody());
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
            listYouTubeVideo(youTubeService.searchVideo(getCommandBody()));
        }
    }

    private void listYouTubeVideo(YouTubeVideo youTubeVideo) throws InterruptedException {
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Title: ").append(youTubeVideo.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Id: ").append(youTubeVideo.getId()).append(System.lineSeparator());
        responseBuilder.append("Link: ").append("https://www.youtube.com/watch?v=").append(youTubeVideo.getId()).append(System.lineSeparator());
        responseBuilder.append("Duration: ").append(Util.normalizeMillis(youTubeVideo.getDuration()));

        sendMessage(getContext().getChannel(), responseBuilder.toString());
    }

    private void listLocalList() throws IOException {
        if (getCommandBody().isBlank()) {
            Session session = getContext().getSession();
            List<Playlist> playlists;
            if (isPartitioned()) {
                playlists = session.createQuery("from " + Playlist.class.getName() + " where guild_id = '" + getContext().getGuild().getId() + "'", Playlist.class).getResultList();
            } else {
                playlists = session.createQuery("from " + Playlist.class.getName(), Playlist.class).getResultList();
            }
            if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No playlists");
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(Color.decode("#1DB954"));

            StringBuilder listBuilder = new StringBuilder();
            StringBuilder durationBuilder = new StringBuilder();
            StringBuilder itemBuilder = new StringBuilder();

            for (Playlist playlist : playlists) {
                listBuilder.append(playlist.getName()).append(System.lineSeparator());
                durationBuilder.append(Util.normalizeMillis(playlist.getDuration())).append(System.lineSeparator());
                itemBuilder.append(playlist.getSongCount()).append(System.lineSeparator());
            }

            embedBuilder.addField("Playlist", listBuilder.toString(), true);
            embedBuilder.addField("Duration", durationBuilder.toString(), true);
            embedBuilder.addField("Items", itemBuilder.toString(), true);

            sendMessage(getContext().getChannel(), embedBuilder.build());
        } else {
            Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandBody(), isPartitioned(), getContext().getGuild().getId());
            if (playlist == null) {
                throw new NoResultsFoundException("No local list found for " + getCommandBody());
            }

            String createdUserId = playlist.getCreatedUserId();
            String createdUser;
            if (createdUserId.equals("system")) {
                createdUser = playlist.getCreatedUser();
            } else {
                createdUser = getContext().getJda().getUserById(createdUserId).getName();
            }


            String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.addField("Name", playlist.getName(), true);
            embedBuilder.addField("Duration", Util.normalizeMillis(playlist.getDuration()), true);
            embedBuilder.addField("Created by", createdUser, true);
            embedBuilder.addField("Tracks", String.valueOf(playlist.getSongCount()), true);
            embedBuilder.addBlankField(false);

            String url = baseUri +
                String.format("/list?name=%s&guildId=%s", URLEncoder.encode(playlist.getName(), StandardCharsets.UTF_8), playlist.getGuildId());
            embedBuilder.addField("First tracks:", "[Full list](" + url + ")", false);

            StringBuilder trackListBuilder = new StringBuilder();
            StringBuilder durationListBuilder = new StringBuilder();
            List<PlaylistItem> items = playlist.getPlaylistItems();
            for (int i = 0; i < 5 && i < items.size(); i++) {
                PlaylistItem item = items.get(i);
                trackListBuilder.append(item.display()).append(System.lineSeparator());
                durationListBuilder.append(Util.normalizeMillis(item.getDuration())).append(System.lineSeparator());
            }

            embedBuilder.addField("Track", trackListBuilder.toString(), true);
            embedBuilder.addField("Duration", durationListBuilder.toString(), true);

            sendWithLogo(getContext().getChannel(), embedBuilder);
        }
    }

    private void listYouTubePlaylists() {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandBody());
            if (playlists.size() == 1) {
                listYouTubePlaylist(playlists.get(0));
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No YouTube playlist found for " + getCommandBody());
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            listYouTubePlaylist(youTubeService.searchPlaylist(getCommandBody()));
        }
    }

    private void listYouTubePlaylist(YouTubePlaylist youTubePlaylist) {
        if (getCommandBody().isBlank()) {
            throw new InvalidCommandException("Command body may not be empty when searching YouTube list");
        }

        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Title: ").append(youTubePlaylist.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Url: ").append(youTubePlaylist.getUrl()).append(System.lineSeparator());
        responseBuilder.append("Videos: ").append(youTubePlaylist.getVideos().size()).append(System.lineSeparator());
        responseBuilder.append("Owner: ").append(youTubePlaylist.getChannelTitle());

        sendMessage(getContext().getChannel(), responseBuilder.toString());
    }

    private void listSpotifyList() throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        String commandBody = getContext().getCommandBody();

        if (commandBody.isBlank()) {
            throw new InvalidCommandException("Command may not be empty when searching spotify lists");
        }

        List<PlaylistSimplified> playlists;
        if (argumentSet("own")) {
            playlists = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnPlaylist(spotifyApi, getCommandBody()));
        } else {
            playlists = runWithCredentials(() -> SearchEngine.searchSpotifyPlaylist(spotifyApi, getCommandBody()));
        }
        if (playlists.size() == 1) {
            PlaylistSimplified playlist = playlists.get(0);
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, playlist));
            listTracks(playlist, tracks);
        } else if (playlists.isEmpty()) {
            sendMessage(getContext().getChannel(), "No Spotify playlist found for " + getCommandBody());
        } else {
            askQuestion(playlists, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
        }
    }

    private void listTracks(PlaylistSimplified playlist, List<Track> tracks) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        long totalDuration = tracks.stream().mapToLong(Track::getDurationMs).sum();

        embedBuilder.addField("Name", playlist.getName(), true);
        embedBuilder.addField("Song count", String.valueOf(tracks.size()), true);
        embedBuilder.addField("Duration", Util.normalizeMillis(totalDuration), true);
        embedBuilder.addField("Owner", playlist.getOwner().getDisplayName(), true);

        if (!tracks.isEmpty()) {
            String url = "https://open.spotify.com/playlist/" + playlist.getId();
            embedBuilder.addField("First tracks:", "[Full list](" + url + ")", false);

            StringBuilder trackListBuilder = new StringBuilder();
            StringBuilder artistListBuilder = new StringBuilder();
            StringBuilder durationListBuilder = new StringBuilder();

            for (int i = 0; i < 5 && i < tracks.size(); i++) {
                Track track = tracks.get(i);
                trackListBuilder.append(track.getName()).append(System.lineSeparator());
                String artists = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                artistListBuilder.append(artists).append(System.lineSeparator());
                durationListBuilder.append(Util.normalizeMillis(track.getDurationMs())).append(System.lineSeparator());
            }

            embedBuilder.addField("Track", trackListBuilder.toString(), true);
            embedBuilder.addField("Artist", artistListBuilder.toString(), true);
            embedBuilder.addField("Duration", durationListBuilder.toString(), true);
        }

        embedBuilder.setColor(Color.decode("#1DB954"));
        sendMessage(getContext().getChannel(), embedBuilder.build());
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        if (chosenOption instanceof PlaylistSimplified) {
            SpotifyApi spotifyApi = getContext().getSpotifyApi();
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, playlist));
            listTracks(playlist, tracks);
        } else if (chosenOption instanceof YouTubePlaylist) {
            listYouTubePlaylist((YouTubePlaylist) chosenOption);
        } else if (chosenOption instanceof YouTubeVideo) {
            listYouTubeVideo((YouTubeVideo) chosenOption);
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("spotify").excludesArguments("youtube").setRequiresInput(true)
            .setDescription("Search for spotify track or playlist. This is the default option when searching for tracks.");
        argumentContribution.map("youtube").excludesArguments("spotify").setRequiresInput(true)
            .setDescription("Search for youtube video or playlist.");
        argumentContribution.map("list")
            .setDescription("Search for a playlist.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Search for a local playlist or list all of them. This is default when searching for lists.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to spotify tracks or playlists in the current user's library.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of youtube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        return argumentContribution;
    }

}
