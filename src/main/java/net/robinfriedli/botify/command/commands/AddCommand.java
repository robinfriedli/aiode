package net.robinfriedli.botify.command.commands;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.stringlist.StringListImpl;

public class AddCommand extends AbstractCommand {

    public AddCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, true,
            "Add a specific song from spotify or youtube or the current queue to the specified local playlist.\n" +
                "Add a specific track like: $botify add $spotify $own from the inside $to my list.\n" +
                "Add tracks from current queue to list: $botify add my list");
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("youtube") || argumentSet("spotify")) {
            addSpecificTrack();
        } else {
            AudioQueue queue = getManager().getAudioManager().getQueue(getContext().getGuild());
            if (queue.isEmpty()) {
                throw new InvalidCommandException("Queue is empty");
            }

            List<Playable> tracks = queue.getTracks();
            List<XmlElement> elements = Lists.newArrayList();
            for (Playable track : tracks) {
                Object item = track.delegate();
                if (item instanceof Track) {
                    elements.add(new Song((Track) item, getContext().getUser(), getPersistContext()));
                } else if (item instanceof YouTubeVideo) {
                    elements.add(new Video((YouTubeVideo) item, getContext().getUser(), getPersistContext()));
                }
            }

            addToList(getCommandBody(), elements);
        }
    }

    private void addSpecificTrack() throws Exception {
        Pair<String, String> pair = splitInlineArgument("to");

        if (argumentSet("youtube")) {
            YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
            YouTubeVideo youTubeVideo = youTubeService.searchVideo(pair.getLeft());

            Video video = new Video(youTubeVideo, getContext().getUser(), getPersistContext());
            addToList(pair.getRight(), video);
        } else {
            SpotifyApi spotifyApi = getManager().getSpotifyApi();
            List<Track> tracks;
            if (argumentSet("own")) {
                tracks = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, pair.getLeft()));
            } else {
                tracks = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, pair.getLeft()));
            }

            if (tracks.size() == 1) {
                Song song = new Song(tracks.get(0), getContext().getUser(), getPersistContext());
                addToList(pair.getRight(), song);
            } else if (tracks.isEmpty()) {
                throw new NoResultsFoundException("No spotify track found for " + pair.getLeft());
            } else {
                askQuestion(tracks, track -> {
                    String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                    return String.format("%s by %s", track.getName(), artistString);
                }, track -> track.getAlbum().getName());
            }
        }
    }

    private void addToList(String name, XmlElement element) {
        addToList(name, Lists.newArrayList(element));
    }

    private void addToList(String name, List<XmlElement> elements) {
        Context persistContext = getPersistContext();
        Playlist playlist = SearchEngine.searchLocalList(persistContext, name);

        if (playlist == null) {
            throw new InvalidCommandException("No local list found for " + name);
        }

        persistContext.invoke(true, true, () -> playlist.addSubElements(elements), getContext().getChannel());
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }

    @Override
    public void withUserResponse(Object option) {
        Pair<String, String> pair = splitInlineArgument("to");
        Song song = new Song((Track) option, getContext().getUser(), getPersistContext());
        addToList(pair.getRight(), song);
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution();
        argumentContribution.map("youtube").excludesArguments("spotify")
            .setDescription("Add specific video from YouTube.");
        argumentContribution.map("spotify").excludesArguments("youtube")
            .setDescription("Add specific spotify track.");
        argumentContribution.map("queue").excludesArguments("youtube", "spotify")
            .setDescription("Add items from current queue. This is the default option.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to tracks in your library. This requires a spotify login.");
        return argumentContribution;
    }
}
