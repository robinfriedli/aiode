package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.concurrent.Callable;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubePlaylist;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.stringlist.StringListImpl;

public class QueueCommand extends AbstractCommand {

    private Playable queuedTrack;
    private Playlist queueLocalList;
    private PlaylistSimplified queuedSpotifyPlaylist;
    private YouTubePlaylist queuedYouTubePlaylist;

    public QueueCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, false, identifier,
            "Add a youtube video or spotify track to the current queue.", Category.PLAYBACK);
    }

    @Override
    public void doRun() throws Exception {
        if (getCommandBody().isBlank()) {
            listQueue();
        } else {
            AudioManager audioManager = getManager().getAudioManager();
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            playback.setCommunicationChannel(getContext().getChannel());

            if (argumentSet("list")) {
                if (argumentSet("spotify")) {
                    queueSpotifyList(audioManager, playback);
                } else if (argumentSet("youtube")) {
                    queueYouTubeList(audioManager, playback);
                } else {
                    queueLocalList(audioManager, playback);
                }
            } else {
                if (argumentSet("youtube")) {
                    queueYouTubeVideo(audioManager, playback);
                } else {
                    queueTrack(audioManager, playback);
                }
            }
        }
    }

    private void queueLocalList(AudioManager audioManager, AudioPlayback playback) throws Exception {
        Playlist playlist = SearchEngine.searchLocalList(getPersistContext(), getCommandBody());
        if (playlist == null) {
            throw new NoResultsFoundException("No local playlist found for " + getCommandBody());
        }

        List<Object> items = runWithCredentials(() -> playlist.getItems(getManager().getSpotifyApi()));

        if (items.isEmpty()) {
            throw new NoResultsFoundException("Playlist is empty");
        }

        List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), items, playback);
        playback.getAudioQueue().add(playables);
        queueLocalList = playlist;
    }

    private void queueYouTubeList(AudioManager audioManager, AudioPlayback playback) {
        YouTubeService youTubeService = audioManager.getYouTubeService();

        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubePlaylist> playlists = youTubeService.searchSeveralPlaylists(limit, getCommandBody());
            if (playlists.size() == 1) {
                YouTubePlaylist playlist = playlists.get(0);
                List<Playable> playables = audioManager.createPlayables(playlist, playback);
                playback.getAudioQueue().add(playables);
                queuedYouTubePlaylist = playlist;
            } else if (playlists.isEmpty()) {
                throw new NoResultsFoundException("No playlist found for " + getCommandBody());
            } else {
                askQuestion(playlists, YouTubePlaylist::getTitle, YouTubePlaylist::getChannelTitle);
            }
        } else {
            YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandBody());
            List<Playable> playables = audioManager.createPlayables(youTubePlaylist, playback);
            playback.getAudioQueue().add(playables);
            queuedYouTubePlaylist = youTubePlaylist;
        }
    }

    private void queueSpotifyList(AudioManager audioManager, AudioPlayback playback) throws Exception {
        SpotifyApi spotifyApi = getManager().getSpotifyApi();
        Callable<Void> callable = () -> {
            List<PlaylistSimplified> found;
            if (argumentSet("own")) {
                found = SearchEngine.searchOwnPlaylist(spotifyApi, getCommandBody());
            } else {
                found = SearchEngine.searchSpotifyPlaylist(spotifyApi, getCommandBody());
            }

            if (found.size() == 1) {
                PlaylistSimplified playlist = found.get(0);
                List<Track> playlistTracks = SearchEngine.getPlaylistTracks(spotifyApi, playlist);
                List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), playlistTracks, playback);
                playback.getAudioQueue().add(playables);
                queuedSpotifyPlaylist = playlist;
            } else if (found.isEmpty()) {
                throw new NoResultsFoundException("No playlist found for " + getCommandBody());
            } else {
                askQuestion(found, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
            }

            return null;
        };

        if (argumentSet("own")) {
            runWithLogin(getContext().getUser(), callable);
        } else {
            runWithCredentials(callable);
        }
    }

    private void queueTrack(AudioManager audioManager, AudioPlayback playback) throws Exception {
        SpotifyApi spotifyApi = getManager().getSpotifyApi();
        List<Track> found;
        if (argumentSet("own")) {
            found = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, getCommandBody()));
        } else {
            found = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, getCommandBody()));
        }

        if (found.size() == 1) {
            Playable track = audioManager.createPlayable(!argumentSet("preview"), found.get(0));
            playback.getAudioQueue().add(track);
            queuedTrack = track;
        } else if (found.isEmpty()) {
            setFailed(true);
            sendMessage(getContext().getChannel(), "No results found");
        } else {
            askQuestion(found, track -> {
                String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                return String.format("%s by %s", track.getName(), artistString);
            }, track -> track.getAlbum().getName());
        }
    }

    private void queueYouTubeVideo(AudioManager audioManager, AudioPlayback playback) {
        YouTubeService youTubeService = audioManager.getYouTubeService();
        if (argumentSet("limit")) {
            int limit = getArgumentValue("limit", Integer.class);
            if (!(limit > 0 && limit <= 10)) {
                throw new InvalidCommandException("Limit must be between 1 and 10");
            }

            List<YouTubeVideo> youTubeVideos = youTubeService.searchSeveralVideos(limit, getCommandBody());
            if (youTubeVideos.size() == 1) {
                Playable playable = audioManager.createPlayable(false, youTubeVideos.get(0));
                playback.getAudioQueue().add(playable);
                queuedTrack = playable;
            } else if (youTubeVideos.isEmpty()) {
                throw new NoResultsFoundException("No YouTube video found for " + getCommandBody());
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
            YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandBody());
            Playable video = audioManager.createPlayable(false, youTubeVideo);
            audioManager.getQueue(getContext().getGuild()).add(video);
            queuedTrack = video;
        }
    }

    private void listQueue() {
        StringBuilder responseBuilder = new StringBuilder();
        AudioPlayback playbackForGuild = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        AudioQueue audioQueue = playbackForGuild.getAudioQueue();

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
                }
                previous = audioQueue.listPrevious(5);
                for (Playable prev : previous) {
                    appendPlayable(table, prev, false);
                }
            }
            appendPlayable(table, current, true);
            if (position < tracks.size() - 1) {
                List<Playable> next;
                if (tracks.size() > position + 6) {
                    endOverflow = true;
                }
                next = audioQueue.listNext(5);
                for (Playable n : next) {
                    appendPlayable(table, n, false);
                }
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

    private void appendPlayable(Table table, Playable playable, boolean current) {
        String display;
        try {
            display = playable.getDisplay();
        } catch (InterruptedException e) {
            display = "[UNAVAILABLE]";
        }
        long durationMs = 0;
        try {
            durationMs = playable.getDurationMs();
        } catch (InterruptedException ignored) {
        }
        table.addRow(
            table.createCell(current ? ">" : "", 5),
            table.createCell(display),
            table.createCell(Util.normalizeMillis(durationMs), 10)
        );
    }

    @Override
    public void onSuccess() {
        if (queuedTrack != null) {
            try {
                sendMessage(getContext().getChannel(), "Queued " + queuedTrack.getDisplay());
            } catch (InterruptedException e) {
                // Unreachable since track has been loaded completely
                return;
            }
        }
        if (queueLocalList != null) {
            sendMessage(getContext().getChannel(), "Queued playlist " + queueLocalList.getAttribute("name").getValue());
        }
        if (queuedSpotifyPlaylist != null) {
            sendMessage(getContext().getChannel(), "Queued playlist " + queuedSpotifyPlaylist.getName());
        }
        if (queuedYouTubePlaylist != null) {
            sendMessage(getContext().getChannel(), "Queued playlist " + queuedYouTubePlaylist.getTitle());
        }
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        AudioManager audioManager = getManager().getAudioManager();
        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo) {
            Playable track = audioManager.createPlayable(!argumentSet("preview"), chosenOption);
            audioManager.getQueue(getContext().getGuild()).add(track);
            queuedTrack = track;
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(getManager().getSpotifyApi(), playlist));
            AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(getContext().getGuild());
            List<Playable> playables = audioManager.createPlayables(!argumentSet("preview"), tracks, playbackForGuild);
            playbackForGuild.getAudioQueue().add(playables);
            queuedSpotifyPlaylist = playlist;
        } else if (chosenOption instanceof YouTubePlaylist) {
            AudioPlayback playback = audioManager.getPlaybackForGuild(getContext().getGuild());
            YouTubePlaylist youTubePlaylist = (YouTubePlaylist) chosenOption;
            List<Playable> playables = audioManager.createPlayables(youTubePlaylist, playback);
            playback.getAudioQueue().add(playables);
            queuedYouTubePlaylist = youTubePlaylist;
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("preview").needsArguments("spotify").excludesArguments("youtube")
            .setDescription("Queue the preview mp3 directly from spotify rather than the full track from youtube");
        argumentContribution.map("youtube").setRequiresInput(true).excludesArguments("preview")
            .setDescription("Queue a youtube video.");
        argumentContribution.map("list").setRequiresInput(true)
            .setDescription("Add the elements from a Spotify, YouTube or local Playlist to the current queue (local is default).");
        argumentContribution.map("spotify").setRequiresInput(true).excludesArguments("youtube")
            .setDescription("Queue Spotify track or playlist.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to Spotify tracks and playlists in the current users library.");
        argumentContribution.map("local").needsArguments("list")
            .setDescription("Queue local playlist.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of youtube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        return argumentContribution;
    }

}
