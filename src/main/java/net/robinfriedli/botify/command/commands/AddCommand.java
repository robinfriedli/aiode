package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubePlaylist;
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

    public AddCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, true, identifier,
            "Add a specific song from spotify or youtube or the current queue to the specified local playlist.\n" +
                "Add a specific track like: $botify add $spotify $own from the inside $to my list.\n" +
                "Add tracks from current queue to list: $botify add my list", Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("youtube") || argumentSet("spotify") || argumentSet("list")) {
            if (argumentSet("list")) {
                addList();
            } else {
                addSpecificTrack();
            }
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

    private void addList() throws Exception {
        Pair<String, String> pair = splitInlineArgument("to");
        if (argumentSet("spotify")) {
            SpotifyApi spotifyApi = getManager().getSpotifyApi();
            Callable<Void> callable = () -> {
                List<PlaylistSimplified> playlists;
                if (argumentSet("own")) {
                    playlists = SearchEngine.searchOwnPlaylist(spotifyApi, pair.getLeft());
                } else {
                    playlists = SearchEngine.searchSpotifyPlaylist(spotifyApi, pair.getLeft());
                }

                if (playlists.size() == 1) {
                    List<XmlElement> songs = Lists.newArrayList();
                    List<Track> playlistTracks = SearchEngine.getPlaylistTracks(spotifyApi, playlists.get(0));
                    for (Track playlistTrack : playlistTracks) {
                        songs.add(new Song(playlistTrack, getContext().getUser(), getPersistContext()));
                    }

                    addToList(pair.getRight(), songs);
                } else if (playlists.isEmpty()) {
                    throw new NoResultsFoundException("No Spotify playlists found for " + pair.getLeft());
                } else {
                    askQuestion(playlists, PlaylistSimplified::getName, playlist -> playlist.getOwner().getDisplayName());
                }

                return null;
            };

            if (argumentSet("own")) {
                runWithLogin(getContext().getUser(), callable);
            } else {
                runWithCredentials(callable);
            }
        } else if (argumentSet("youtube")) {
            YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
            if (argumentSet("limit")) {
                int limit = getArgumentValue("limit", Integer.class);
                if (!(limit > 0 && limit <= 10)) {
                    throw new InvalidCommandException("Limit must be between 1 and 10");
                }

                List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, pair.getLeft());
                if (playlists.size() == 1) {
                    YouTubePlaylist youTubePlaylist = playlists.get(0);
                    addYouTubeList(youTubePlaylist, pair, youTubeService);
                } else if (playlists.isEmpty()) {
                    throw new NoResultsFoundException("No YouTube playlists found for " + pair.getLeft());
                } else {
                    askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
                }
            } else {
                YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(pair.getLeft());
                addYouTubeList(youTubePlaylist, pair, youTubeService);
            }
        } else {
            Playlist playlist = SearchEngine.searchLocalList(getPersistContext(), pair.getLeft());

            if (playlist == null) {
                throw new InvalidCommandException("No local playlist found for " + pair.getLeft());
            }

            List<XmlElement> elements = Lists.newArrayList();
            for (XmlElement subElement : playlist.getSubElements()) {
                elements.add(subElement.copy(false, true));
            }
            addToList(pair.getRight(), elements);
        }
    }

    private void addYouTubeList(YouTubePlaylist youTubePlaylist, Pair<String, String> pair, YouTubeService youTubeService) {
        youTubeService.populateList(youTubePlaylist);
        List<XmlElement> videos = Lists.newArrayList();
        for (HollowYouTubeVideo video : youTubePlaylist.getVideos()) {
            if (!video.isCanceled()) {
                videos.add(new Video(video, getContext().getUser(), getPersistContext()));
            }
        }
        addToList(pair.getRight(), videos);
    }

    private void addSpecificTrack() throws Exception {
        Pair<String, String> pair = splitInlineArgument("to");

        if (argumentSet("youtube")) {
            YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
            if (argumentSet("limit")) {
                int limit = getArgumentValue("limit", Integer.class);
                if (!(limit > 0 && limit <= 10)) {
                    throw new InvalidCommandException("Limit must be between 1 and 10");
                }
                List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, pair.getLeft());
                if (youTubeVideos.size() == 1) {
                    Video video = new Video(youTubeVideos.get(0), getContext().getUser(), getPersistContext());
                    addToList(pair.getRight(), video);
                } else if (youTubeVideos.isEmpty()) {
                    throw new NoResultsFoundException("No YouTube videos found for " + pair.getRight());
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
                YouTubeVideo youTubeVideo = youTubeService.searchVideo(pair.getLeft());
                Video video = new Video(youTubeVideo, getContext().getUser(), getPersistContext());
                addToList(pair.getRight(), video);
            }
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
    public void withUserResponse(Object option) throws Exception {
        if (option instanceof Track) {
            Pair<String, String> pair = splitInlineArgument("to");
            Song song = new Song((Track) option, getContext().getUser(), getPersistContext());
            addToList(pair.getRight(), song);
        } else if (option instanceof YouTubeVideo) {
            Pair<String, String> pair = splitInlineArgument("to");
            Video video = new Video((YouTubeVideo) option, getContext().getUser(), getPersistContext());
            addToList(pair.getRight(), video);
        } else if (option instanceof PlaylistSimplified) {
            Pair<String, String> pair = splitInlineArgument("to");
            SpotifyApi spotifyApi = getManager().getSpotifyApi();
            List<XmlElement> songs = Lists.newArrayList();
            List<Track> playlistTracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, (PlaylistSimplified) option));
            for (Track playlistTrack : playlistTracks) {
                songs.add(new Song(playlistTrack, getContext().getUser(), getPersistContext()));
            }

            addToList(pair.getRight(), songs);
        } else if (option instanceof YouTubePlaylist) {
            YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
            addYouTubeList((YouTubePlaylist) option, splitInlineArgument("to"), youTubeService);
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("youtube").excludesArguments("spotify")
            .setDescription("Add specific video from YouTube.");
        argumentContribution.map("spotify").excludesArguments("youtube")
            .setDescription("Add specific spotify track.");
        argumentContribution.map("queue").excludesArguments("youtube", "spotify")
            .setDescription("Add items from current queue. This is the default option.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to tracks in your library. This requires a spotify login.");
        argumentContribution.map("list")
            .setDescription("Add tracks from a Spotify, YouTube or local list to a list.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Add items from a local list. This is the default option when adding lists.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of youtube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        return argumentContribution;
    }
}
