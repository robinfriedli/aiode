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
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.SearchEngine;

public class UploadCommand extends AbstractCommand {

    private String uploadedPlaylistName;

    public UploadCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.SPOTIFY);
    }

    @Override
    public void doRun() throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandBody(), isPartitioned(), getContext().getGuild().getId());

        if (playlist == null) {
            throw new InvalidCommandException("No local list found for " + getCommandBody());
        }

        runWithLogin(() -> {
            List<Track> tracks = playlist.asTrackList(spotifyApi);
            String name = playlist.getName();

            if (tracks.isEmpty()) {
                throw new InvalidCommandException("Playlist " + name + " has no Spotify tracks.");
            }

            String userId = spotifyApi.getCurrentUsersProfile().build().execute().getId();
            com.wrapper.spotify.model_objects.specification.Playlist spotifyPlaylist = spotifyApi.createPlaylist(userId, name).build().execute();
            uploadedPlaylistName = spotifyPlaylist.getName();
            String playlistId = spotifyPlaylist.getId();
            List<String> trackUris = tracks.stream().map(Track::getUri).collect(Collectors.toList());
            List<List<String>> sequences = Lists.partition(trackUris, 90);
            for (List<String> sequence : sequences) {
                spotifyApi.addTracksToPlaylist(playlistId, sequence.toArray(new String[0])).build().execute();
            }

            return null;
        });
    }

    @Override
    public void onSuccess() {
        sendSuccess("Created Spotify playlist " + uploadedPlaylistName);
    }
}
