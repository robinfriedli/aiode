package net.robinfriedli.botify.command.commands;

import java.io.IOException;
import java.util.List;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringListImpl;

public class QueueCommand extends AbstractCommand {

    public QueueCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, true, false, true,
            "Add a youtube video or spotify track to the current queue.");
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("youtube")) {
            queueYouTubeVideo();
        } else {
            queueTrack();
        }
    }

    private void queueTrack() throws IOException, SpotifyWebApiException {
        List<Track> found = SearchEngine.searchTrack(getManager().getSpotifyApi(), getCommandBody());

        if (found.size() == 1) {
            AudioManager audioManager = getManager().getAudioManager();
            audioManager.getQueue(getContext().getGuild()).add(audioManager.createPlayable(!argumentSet("preview"), found.get(0)));
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

    private void queueYouTubeVideo() {
        AudioManager audioManager = getManager().getAudioManager();
        YouTubeService youTubeService = audioManager.getYouTubeService();
        YouTubeVideo youTubeVideo = youTubeService.searchVideo(getCommandBody());
        audioManager.getQueue(getContext().getGuild()).add(audioManager.createPlayable(false, youTubeVideo));
    }

    @Override
    public void onSuccess() {
        List<Playable> tracks = getManager().getAudioManager().getQueue(getContext().getGuild()).getTracks();
        Playable lastTrack = tracks.get(tracks.size() - 1);
        getContext().getChannel().sendMessage("Queued " + lastTrack.getDisplay()).queue();
    }

    @Override
    public void withUserResponse(Object chosenOption) {
        AudioManager audioManager = getManager().getAudioManager();
        audioManager.getQueue(getContext().getGuild()).add(audioManager.createPlayable(!argumentSet("preview"), chosenOption));
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution();
        argumentContribution.map("preview").excludesArguments("youtube")
            .setDescription("Queue the preview mp3 directly from spotify rather than the full track from youtube");
        argumentContribution.map("youtube").excludesArguments("preview")
            .setDescription("Queue a youtube video.");
        return argumentContribution;
    }

}
