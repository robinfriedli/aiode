package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
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

public class ListCommand extends AbstractCommand {

    public ListCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false,
            "List either the current queue or a specific spotify, youtube or local playlist.");
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("spotify")) {
            listSpotifyList();
        } else if (argumentSet("youtube")) {
            listYouTubePlaylist();
        } else if (argumentSet("local")) {
            listLocalList();
        } else {
            listQueue();
        }
    }

    private void listQueue() {
        StringBuilder responseBuilder = new StringBuilder();
        AudioQueue audioQueue = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild()).getAudioQueue();

        if (audioQueue.isEmpty()) {
            responseBuilder.append("Queue is empty");
        } else {
            Table table = Table.createNoBorder(50, 1, false);
            Playable current = audioQueue.getCurrent();
            int position = audioQueue.getPosition();
            List<Playable> tracks = audioQueue.getTracks();

            boolean startOverflow = false;
            boolean endOverflow = false;

            if (position > 0) {
                List<Playable> previous;
                if (position > 5) {
                    startOverflow = true;
                    previous = tracks.subList(position - 5, position);
                } else {
                    previous = tracks.subList(0, position);
                }
                previous.forEach(prev -> appendPlayable(table, prev));
            }
            table.addRow(
                table.createCell(">", 5),
                table.createCell(current.getDisplay()),
                table.createCell(Util.normalizeMillis(current.getDurationMs()), 10)
            );
            if (position < tracks.size() - 1) {
                List<Playable> next;
                if (tracks.size() > position + 6) {
                    endOverflow = true;
                    next = tracks.subList(position + 1, position + 6);
                } else {
                    next = tracks.subList(position + 1, tracks.size());
                }
                next.forEach(n -> appendPlayable(table, n));
            }

            if (startOverflow) {
                responseBuilder.append("...").append(System.lineSeparator());
            }
            responseBuilder.append(table.normalize());
            if (endOverflow) {
                responseBuilder.append(System.lineSeparator()).append("...");
            }
        }

        AlertService alertService = new AlertService();
        alertService.sendWrapped(responseBuilder.toString(), "```", getContext().getChannel());
    }

    private void appendPlayable(Table table, Playable playable) {
        table.addRow(
            table.createCell("", 5),
            table.createCell(playable.getDisplay()),
            table.createCell(Util.normalizeMillis(playable.getDurationMs()), 10)
        );
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
                String duration = Util.normalizeMillis(playlist.getAttribute("duration").getValue(Long.class));
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
            long duration = playlist.getAttribute("duration").getValue(Long.class);
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
                    String trackDuration = Util.normalizeMillis(item.getAttribute("duration").getValue(Long.class));
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

    private void listYouTubePlaylist() {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();

        if (getCommandBody().isBlank()) {
            throw new InvalidCommandException("Command body may not be empty when searching YouTube list");
        }

        StringBuilder responseBuilder = new StringBuilder();
        YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandBody());
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
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution();
        argumentContribution.map("spotify").excludesArguments("youtube", "queue", "local")
            .setDescription("List a spotify playlist.");
        argumentContribution.map("youtube").excludesArguments("spotify", "queue", "local")
            .setDescription("List a youtube playlist.");
        argumentContribution.map("local").excludesArguments("spotify", "youtube", "queue")
            .setDescription("List a local playlist or list all local playlists if no playlist is specified.");
        argumentContribution.map("queue").excludesArguments("spotify", "youtube", "local")
            .setDescription("List the current queue. This is default.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to spotify lists in the current user's library.");
        return argumentContribution;
    }
}
