package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class PlayCommand extends AbstractQueueLoadingCommand {

    public PlayCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK, true);
    }

    @Override
    public void doRun() throws Exception {
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        Member member = guild.getMember(context.getUser());
        VoiceChannel channel = member.getVoiceState().getChannel();
        AudioManager audioManager = Botify.get().getAudioManager();
        MessageChannel messageChannel = getContext().getChannel();
        AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(guild);
        playbackForGuild.setCommunicationChannel(messageChannel);

        if (getCommandBody().isBlank()) {
            if (playbackForGuild.isPaused() || !audioManager.getQueue(guild).isEmpty()) {
                audioManager.playTrack(guild, channel);
            } else {
                throw new InvalidCommandException("Queue is empty. Specify a song you want to play.");
            }
        } else {
            super.doRun();
        }
    }

    @Override
    protected void handleResults(List<Playable> playables) {
        Guild guild = getContext().getGuild();
        Member member = guild.getMember(getContext().getUser());
        VoiceChannel channel = member.getVoiceState().getChannel();
        AudioManager audioManager = Botify.get().getAudioManager();

        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        playback.getAudioQueue().set(playables);
        audioManager.playTrack(guild, channel);
    }

    @Override
    public void onSuccess() {
        // current track notification sent by AudioManager
    }

    @Override
    public void withUserResponse(Object chosenOption) throws Exception {
        AudioManager audioManager = Botify.get().getAudioManager();
        Guild guild = getContext().getGuild();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(guild);
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();

        if (chosenOption instanceof Track || chosenOption instanceof YouTubeVideo) {
            queue.set(playableFactory.createPlayable(!argumentSet("preview"), chosenOption));
        } else if (chosenOption instanceof PlaylistSimplified) {
            PlaylistSimplified playlist = (PlaylistSimplified) chosenOption;
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getPlaylistTracks(playlist));
            queue.set(playableFactory.createPlayables(!argumentSet("preview"), tracks));
        } else if (chosenOption instanceof YouTubePlaylist) {
            YouTubePlaylist youTubePlaylist = (YouTubePlaylist) chosenOption;
            queue.set(playableFactory.createPlayables(youTubePlaylist));
        } else if (chosenOption instanceof AlbumSimplified) {
            List<Track> tracks = runWithCredentials(() -> getSpotifyService().getAlbumTracks(((AlbumSimplified) chosenOption).getId()));
            queue.set(playableFactory.createPlayables(!argumentSet("preview"), tracks));
        }

        Member member = guild.getMember(getContext().getUser());
        VoiceChannel channel = member.getVoiceState().getChannel();
        audioManager.playTrack(guild, channel);
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("list").setRequiresInput(true)
            .setDescription("Search for a list.");
        argumentContribution.map("preview").excludesArguments("youtube")
            .setDescription("Play the short preview mp3 directly from Spotify instead of the full track from YouTube.");
        argumentContribution.map("spotify").setRequiresInput(true).excludesArguments("youtube")
            .setDescription("Search for a Spotify track or list. This supports Spotify query syntax (i.e. the filters \"artist:\", \"album:\", etc.). Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("youtube").setRequiresInput(true).excludesArguments("spotify")
            .setDescription("Play a YouTube video or playlist. Note that this argument is only required when searching, not when entering a URL.");
        argumentContribution.map("own").needsArguments("spotify")
            .setDescription("Limit search to Spotify tracks or lists that are in the current user's library. This requires a Spotify login.");
        argumentContribution.map("local").needsArguments("list").excludesArguments("spotify", "youtube")
            .setDescription("Play a local list.");
        argumentContribution.map("limit").needsArguments("youtube").setRequiresValue(true)
            .setDescription("Show a selection of YouTube playlists or videos to chose from. Requires value from 1 to 10: $limit=5");
        argumentContribution.map("album").needsArguments("spotify").excludesArguments("list").setRequiresInput(true)
            .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
        return argumentContribution;
    }
}
