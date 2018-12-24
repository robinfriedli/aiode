package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.concurrent.Callable;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
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
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;

public class PlayCommand extends AbstractCommand {

    public PlayCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false,
            "Resume the paused playback, play the current track in the current queue or play specified track, " +
                "video or playlist. Can play youtube and spotify tracks or lists and local playlists. Local playlists, " +
                "like the queue, can contain both youtube videos and spotify tracks.\n" +
                "Usage examples:\n$botify play\n$botify play numb\n$botify play $youtube youtube rewind 2018\n" +
                "$botify play $youtube $list important videos\n$botify play $spotify $list $own goat");
    }

    @Override
    public void doRun() throws Exception {
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        Member member = guild.getMember(context.getUser());
        VoiceChannel channel = member.getVoiceState().getChannel();
        AudioManager audioManager = getManager().getAudioManager();
        MessageChannel messageChannel = getContext().getChannel();

        if (argumentSet("list")) {
            if (getCommandBody().isBlank()) {
                throw new InvalidCommandException("Command body cannot be null if list attribute is set");
            }
            if (argumentSet("spotify")) {
                playSpotifyList(channel);
            } else if (argumentSet("youtube")) {
                playYouTubePlaylist(channel, audioManager, guild, messageChannel);
            } else {
                playLocalList(channel, audioManager, guild, messageChannel);
            }
        } else {
            if (getCommandBody().isBlank()) {
                AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(guild);
                if (playbackForGuild.isPaused()) {
                    playbackForGuild.unpause();
                } else if (!audioManager.getQueue(guild).isEmpty()) {
                    audioManager.playTrack(guild, messageChannel, channel);
                } else {
                    throw new InvalidCommandException("Queue is empty. Specify a song you want to play.");
                }
            } else {
                if (argumentSet("youtube")) {
                    playYouTubeVideo(channel, audioManager, guild, messageChannel);
                } else {
                    playSpotifyTrack(channel, audioManager, guild, messageChannel);
                }
            }
        }
    }

    private void playSpotifyTrack(VoiceChannel channel, AudioManager audioManager, Guild guild, MessageChannel messageChannel) throws Exception {
        List<Track> found;
        SpotifyApi spotifyApi = getManager().getSpotifyApi();
        if (argumentSet("own")) {
            found = runWithLogin(getContext().getUser(), () -> SearchEngine.searchOwnTrack(spotifyApi, getCommandBody()));
        } else {
            found = runWithCredentials(() -> SearchEngine.searchTrack(spotifyApi, getCommandBody()));
        }

        if (found.size() == 1) {
            audioManager.getQueue(guild).set(audioManager.createPlayable(!argumentSet("preview"), found.get(0)));
            audioManager.playTrack(guild, messageChannel, channel);
        } else if (found.isEmpty()) {
            sendMessage(messageChannel, "No results found");
        } else {
            askQuestion(found, track -> {
                String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                return String.format("%s by %s", track.getName(), artistString);
            }, track -> track.getAlbum().getName());
        }
    }

    private void playYouTubeVideo(VoiceChannel channel, AudioManager audioManager, Guild guild, MessageChannel messageChannel) {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandBody());
        audioManager.getQueue(guild).set(audioManager.createPlayable(false, youTubeVideo));
        audioManager.playTrack(guild, messageChannel, channel);
    }

    private void playYouTubePlaylist(VoiceChannel channel, AudioManager audioManager, Guild guild, MessageChannel messageChannel) {
        YouTubeService youTubeService = getManager().getAudioManager().getYouTubeService();
        YouTubePlaylist youTubePlaylist = youTubeService.searchPlaylist(getCommandBody());
        audioManager.getQueue(guild).set(audioManager.createPlayables(false, youTubePlaylist.getVideos()));
        audioManager.playTrack(guild, messageChannel, channel);
    }

    private void playLocalList(VoiceChannel channel, AudioManager audioManager, Guild guild, MessageChannel messageChannel) throws Exception {
        Playlist playlist = SearchEngine.searchLocalList(getPersistContext(), getCommandBody());
        if (playlist == null) {
            throw new InvalidCommandException("No local playlist found for '" + getCommandBody() + "'");
        }

        List<Object> items = runWithCredentials(() -> playlist.getItems(getManager().getSpotifyApi()));

        if (items.isEmpty()) {
            throw new NoResultsFoundException("Playlist is empty");
        }

        audioManager.getQueue(guild).set(createPlayables(audioManager, items));
        audioManager.playTrack(guild, messageChannel, channel);
    }

    private void playSpotifyList(VoiceChannel channel) throws Exception {
        AudioManager audioManager = getManager().getAudioManager();
        Guild guild = getContext().getGuild();
        MessageChannel communicationChannel = getContext().getChannel();
        SpotifyApi spotifyApi = getManager().getSpotifyApi();

        Callable<Void> callable = () -> {
            List<PlaylistSimplified> playlists;
            if (argumentSet("own")) {
                playlists = SearchEngine.searchOwnPlaylist(spotifyApi, getCommandBody());
            } else {
                playlists = SearchEngine.searchSpotifyPlaylist(spotifyApi, getCommandBody());
            }

            if (playlists.size() == 1) {
                PlaylistSimplified playlist = playlists.get(0);
                List<Track> tracks = SearchEngine.getPlaylistTracks(spotifyApi, playlist);

                if (tracks.isEmpty()) {
                    throw new NoResultsFoundException("Playlist " + playlist.getName() + " has no tracks");
                }

                audioManager.getQueue(guild).set(createPlayables(audioManager, tracks));
                audioManager.playTrack(guild, communicationChannel, channel);
            } else if (playlists.isEmpty()) {
                sendMessage(communicationChannel, "No results found");
            } else {
                askQuestion(playlists, PlaylistSimplified::getName, p -> p.getOwner().getDisplayName());
            }

            return null;
        };

        if (argumentSet("own")) {
            runWithLogin(getContext().getUser(), callable);
        } else {
            runWithCredentials(callable);
        }
    }

    private List<Playable> createPlayables(AudioManager audioManager, List<?> objects) {
        if (!argumentSet("preview")) {
            long toConvertCount = objects.stream().filter(o -> o instanceof Track).count();
            if (toConvertCount > 10) {
                int secondsPer50 = 20;
                long secondsTotal = toConvertCount * secondsPer50 / 50;
                sendMessage(getContext().getChannel(),
                    "Have to find the YouTube video for " + toConvertCount + " Spotify tracks. This might take about " + secondsTotal + " seconds");
            }
        }
        return audioManager.createPlayables(!argumentSet("preview"), objects);
    }

    @Override
    public void onSuccess() {
        // current track notification sent by AudioManager
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        AudioManager audioManager = getManager().getAudioManager();
        Guild guild = getContext().getGuild();
        AudioQueue queue = audioManager.getQueue(guild);

        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo) {
            queue.set(audioManager.createPlayable(!argumentSet("preview"), chosenOption));
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            SpotifyApi spotifyApi = getManager().getSpotifyApi();
            List<Track> tracks = runWithCredentials(() -> SearchEngine.getPlaylistTracks(spotifyApi, playlist));
            audioManager.getQueue(guild).set(createPlayables(audioManager, tracks));
        }

        Member member = guild.getMember(getContext().getUser());
        VoiceChannel channel = member.getVoiceState().getChannel();
        audioManager.playTrack(guild, getContext().getChannel(), channel);
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution();
        argumentContribution.map("list")
            .setDescription("Search for a list.");
        argumentContribution.map("preview").excludesArguments("youtube")
            .setDescription("Play the short preview mp3 directly from spotify instead of the full track from youtube.");
        argumentContribution.map("spotify").excludesArguments("youtube")
            .setDescription("Play a spotify track or list.");
        argumentContribution.map("youtube").excludesArguments("spotify")
            .setDescription("Play a youtube video or playlist.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to spotify tracks or lists that are in the current user's library.");
        return argumentContribution;
    }
}
