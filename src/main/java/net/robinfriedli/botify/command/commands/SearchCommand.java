package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.stringlist.StringListImpl;

public class SearchCommand extends AbstractCommand {

    public SearchCommand(CommandContext commandContext, CommandManager commandManager, String commandString, String identifier) {
        super(commandContext, commandManager, commandString, false, false, false, identifier,
            "Search for a youtube video or spotify track.", Category.SEARCH);
    }

    @Override
    public void doRun() throws Exception {
        AlertService alertService = new AlertService();
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
                searchSpotifyTrack(alertService);
            }
        }
    }

    private void searchSpotifyTrack(AlertService alertService) throws Exception {
        SpotifyApi spotifyApi = getManager().getSpotifyApi();
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, getCommandBody()));
        } else {
            found = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, getCommandBody()));
        }
        if (!found.isEmpty()) {
            Table table = Table.create(50, 1, false, "", "", "", "=");
            table.setTableHead(table.createCell("Track"), table.createCell("Album"), table.createCell("Artist"));
            for (int i = 0; i < found.size(); i++) {
                Track track = found.get(i);

                String album = track.getAlbum().getName();
                String artist = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");

                table.addRow(table.createCell(track.getName()), table.createCell(album), table.createCell(artist));
            }

            alertService.sendWrapped(table.normalize(), "```", getContext().getChannel());
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

    private void listLocalList() {
        if (getCommandBody().isBlank()) {
            Context persistContext = getPersistContext();
            List<Playlist> playlists = persistContext.getInstancesOf(Playlist.class);
            if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No playlists");
            }

            Table table = Table.create(50, 1, false, "", "", "-", "=");
            table.setTableHead(table.createCell("Playlist"), table.createCell("Duration", 10), table.createCell("Items", 8));

            for (Playlist playlist : playlists) {
                String name = playlist.getAttribute("name").getValue();
                String duration = Util.normalizeMillis(playlist.getAttribute("duration").getLong());
                String items = playlist.getAttribute("songCount").getValue();
                table.addRow(table.createCell(name), table.createCell(duration, 10), table.createCell(items, 8));
            }

            AlertService alertService = new AlertService();
            alertService.sendWrapped(table.normalize(), "```", getContext().getChannel());
        } else {
            Playlist playlist = SearchEngine.searchLocalList(getPersistContext(), getCommandBody());
            if (playlist == null) {
                throw new NoResultsFoundException("No local list found for " + getCommandBody());
            }

            StringBuilder responseBuilder = new StringBuilder();
            String name = playlist.getAttribute("name").getValue();
            long duration = playlist.getAttribute("duration").getLong();
            String createdUserId = playlist.getAttribute("createdUserId").getValue();
            String createdUser;
            if (createdUserId.equals("system")) {
                createdUser = playlist.getAttribute("createdUser").getValue();
            } else {
                createdUser = getContext().getJda().getUserById(createdUserId).getName();
            }
            List<XmlElement> subElements = playlist.getSubElements();
            int itemCount = subElements.size();

            Table table = Table.createNoBorder(50, 1, false);
            table.addRow(table.createCell("Name:", 15), table.createCell(name));
            table.addRow(table.createCell("Duration:", 15), table.createCell(Util.normalizeMillis(duration)));
            table.addRow(table.createCell("Created:", 15), table.createCell(createdUser));
            table.addRow(table.createCell("Tracks:", 15), table.createCell(String.valueOf(itemCount)));

            responseBuilder.append(table.normalize());

            if (itemCount > 0) {
                Table trackTable = Table.create(50, 1, false, "", "", "-", "=");
                trackTable.setTableHead(trackTable.createCell("Track"), trackTable.createCell("Duration", 10));

                for (int i = 0; i < 5 && i < itemCount; i++) {
                    XmlElement item = subElements.get(i);
                    String trackDuration = Util.normalizeMillis(item.getAttribute("duration").getLong());
                    String display;
                    if (item instanceof Song) {
                        String trackName = item.getAttribute("name").getValue();
                        String artistString = StringListImpl.create(item.getAttribute("artists").getValue(), ",").toSeparatedString(", ");
                        display = trackName + " by " + artistString;
                    } else {
                        display = item.getAttribute("title").getValue();
                    }

                    trackTable.addRow(trackTable.createCell(display), trackTable.createCell(trackDuration, 10));
                }

                responseBuilder.append(System.lineSeparator());
                responseBuilder.append("-".repeat(50));
                responseBuilder.append(System.lineSeparator());
                responseBuilder.append("First tracks:");
                responseBuilder.append(System.lineSeparator());
                responseBuilder.append("-".repeat(50));
                responseBuilder.append(System.lineSeparator());
                responseBuilder.append(trackTable.normalize());
            }

            AlertService alertService = new AlertService();
            alertService.sendWrapped(responseBuilder.toString(), "```", getContext().getChannel());
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
        SpotifyApi spotifyApi = getManager().getSpotifyApi();
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
        AlertService alertService = new AlertService();
        StringBuilder responseBuilder = new StringBuilder();

        Table overviewTable = Table.createNoBorder(50, 1, false);
        long totalDuration = tracks.stream().mapToLong(Track::getDurationMs).sum();
        overviewTable.addRow(overviewTable.createCell("Name:", 15), overviewTable.createCell(playlist.getName()));
        overviewTable.addRow(overviewTable.createCell("Song count:", 15), overviewTable.createCell(String.valueOf(tracks.size())));
        overviewTable.addRow(overviewTable.createCell("Duration:", 15), overviewTable.createCell(Util.normalizeMillis(totalDuration)));
        overviewTable.addRow(overviewTable.createCell("Owner:", 15), overviewTable.createCell(playlist.getOwner().getDisplayName()));

        responseBuilder.append(overviewTable.normalize());

        if (!tracks.isEmpty()) {
            Table table = Table.create(50, 1, false, "", "", "-", "=");
            table.setTableHead(table.createCell("Track"), table.createCell("Artist"), table.createCell("Duration", 10));

            for (int i = 0; i < 5 && i < tracks.size(); i++) {
                Track track = tracks.get(i);
                String artists = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                String duration = Util.normalizeMillis(track.getDurationMs());
                table.addRow(table.createCell(track.getName()), table.createCell(artists), table.createCell(duration, 10));
            }

            responseBuilder.append(System.lineSeparator());
            responseBuilder.append("-".repeat(50));
            responseBuilder.append(System.lineSeparator());
            responseBuilder.append("First tracks:");
            responseBuilder.append(System.lineSeparator());
            responseBuilder.append("-".repeat(50));
            responseBuilder.append(System.lineSeparator());
            responseBuilder.append(table.normalize());
        }

        alertService.sendWrapped(responseBuilder.toString(), "```", getContext().getChannel());
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        if (chosenOption instanceof PlaylistSimplified) {
            SpotifyApi spotifyApi = getManager().getSpotifyApi();
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
