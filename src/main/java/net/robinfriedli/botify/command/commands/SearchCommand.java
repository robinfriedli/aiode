package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.stringlist.StringListImpl;

public class SearchCommand extends AbstractCommand {

    public SearchCommand(CommandContext commandContext, CommandManager commandManager, String commandString) {
        super(commandContext, commandManager, commandString, false, false, true,
            "Search for a youtube video or spotify track.");
    }

    @Override
    public void doRun() throws Exception {
        AlertService alertService = new AlertService();
        if (argumentSet("youtube")) {
            searchYouTubeVideo();
        } else {
            searchSpotifyTrack(alertService);
        }
    }

    private void searchSpotifyTrack(AlertService alertService) throws Exception {
        List<Track> found = runWithCredentials(() -> SearchEngine.searchTrack(getManager().getSpotifyApi(), getCommandBody()));
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

    private void searchYouTubeVideo() {
        StringBuilder responseBuilder = new StringBuilder();
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandBody());
        responseBuilder.append("Title: ").append(youTubeVideo.getTitle()).append(System.lineSeparator());
        responseBuilder.append("Id: ").append(youTubeVideo.getId()).append(System.lineSeparator());
        responseBuilder.append("Link: ").append("https://www.youtube.com/watch?v=").append(youTubeVideo.getId()).append(System.lineSeparator());
        responseBuilder.append("Duration: ").append(Util.normalizeMillis(youTubeVideo.getDuration()));

        sendMessage(getContext().getChannel(), responseBuilder.toString());
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution();
        argumentContribution.map("spotify").excludesArguments("youtube")
            .setDescription("Search for spotify track. This is the default option.");
        argumentContribution.map("youtube").excludesArguments("spotify")
            .setDescription("Search for youtube video.");
        return argumentContribution;
    }

}
