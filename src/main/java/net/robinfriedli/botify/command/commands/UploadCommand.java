package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.SearchEngine;

public class UploadCommand extends AbstractCommand {

    public UploadCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, true, true, identifier,
            "Upload the items from a local list to a new Spotify playlist. This ignores youtube videos in the " +
                "list, except for those that are originally redirected Spotify tracks.", Category.SPOTIFY);
    }

    @Override
    public void doRun() throws Exception {
        SpotifyApi spotifyApi = getManager().getSpotifyApi();
        Playlist playlist = SearchEngine.searchLocalList(getPersistContext(), getCommandBody());

        if (playlist == null) {
            throw new InvalidCommandException("No local list found for " + getCommandBody());
        }

        List<Track> tracks = playlist.asTrackList(spotifyApi);
        String name = playlist.getAttribute("name").getValue();

        if (tracks.isEmpty()) {
            throw new InvalidCommandException("Playlist " + name + " has no Spotify tracks.");
        }

        String userId = spotifyApi.getCurrentUsersProfile().build().execute().getId();
        com.wrapper.spotify.model_objects.specification.Playlist spotifyPlaylist = spotifyApi.createPlaylist(userId, name).build().execute();
        String playlistId = spotifyPlaylist.getId();
        List<String> trackUris = tracks.stream().map(Track::getUri).collect(Collectors.toList());
        List<List<String>> sequences = Lists.partition(trackUris, 90);
        for (List<String> sequence : sequences) {
            spotifyApi.addTracksToPlaylist(playlistId, sequence.toArray(new String[0])).build().execute();
        }
    }

    @Override
    public void onSuccess() {
        sendMessage(getContext().getChannel(), "Created Spotify playlist " + getCommandBody());
    }
}
