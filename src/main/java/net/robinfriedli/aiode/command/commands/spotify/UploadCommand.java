package net.robinfriedli.aiode.command.commands.spotify;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.util.SearchEngine;
import se.michaelthelin.spotify.SpotifyApi;

public class UploadCommand extends AbstractCommand {

    private String uploadedPlaylistName;

    public UploadCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() throws Exception {
        SpotifyApi spotifyApi = getContext().getSpotifyApi();
        Playlist playlist = SearchEngine.searchLocalList(getContext().getSession(), getCommandInput());

        if (playlist == null) {
            throw new InvalidCommandException(String.format("No local list found for '%s'", getCommandInput()));
        }

        runWithLogin(() -> {
            List<SpotifyTrack> tracks = playlist.asTrackList(spotifyApi);
            String name = playlist.getName();

            if (tracks.isEmpty()) {
                throw new InvalidCommandException("Playlist " + name + " has no Spotify tracks.");
            }

            String userId = spotifyApi.getCurrentUsersProfile().build().execute().getId();
            se.michaelthelin.spotify.model_objects.specification.Playlist spotifyPlaylist = spotifyApi.createPlaylist(userId, name).build().execute();
            uploadedPlaylistName = spotifyPlaylist.getName();
            String playlistId = spotifyPlaylist.getId();
            List<String> trackUris = tracks.stream().map(SpotifyTrack::getUri).collect(Collectors.toList());
            List<List<String>> sequences = Lists.partition(trackUris, 90);
            for (List<String> sequence : sequences) {
                spotifyApi.addItemsToPlaylist(playlistId, sequence.toArray(new String[0])).build().execute();
            }

            return null;
        });
    }

    @Override
    public void onSuccess() {
        sendSuccess("Created Spotify playlist " + uploadedPlaylistName);
    }
}
