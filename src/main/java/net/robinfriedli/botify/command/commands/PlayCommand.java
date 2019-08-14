package net.robinfriedli.botify.command.commands;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;

public class PlayCommand extends AbstractQueueLoadingCommand {

    public PlayCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK, true);
    }

    @Override
    public void doRun() throws Exception {
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        VoiceChannel channel = context.getVoiceChannel();
        AudioManager audioManager = Botify.get().getAudioManager();
        MessageChannel messageChannel = getContext().getChannel();
        AudioPlayback playbackForGuild = audioManager.getPlaybackForGuild(guild);
        playbackForGuild.setCommunicationChannel(messageChannel);

        if (getCommandInput().isBlank()) {
            if (playbackForGuild.isPaused() || !audioManager.getQueue(guild).isEmpty()) {
                audioManager.startOrResumePlayback(guild, channel);
            } else {
                throw new InvalidCommandException("Queue is empty. Specify a song you want to play.");
            }
        } else {
            super.doRun();
        }
    }

    @Override
    protected void handleResults(List<Playable> playables) {
        if (playables.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        Guild guild = getContext().getGuild();
        VoiceChannel channel = getContext().getVoiceChannel();
        AudioManager audioManager = Botify.get().getAudioManager();

        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        playback.getAudioQueue().set(playables);
        audioManager.startPlayback(guild, channel);
    }

    @Override
    public void onSuccess() {
        // current track notification sent by AudioManager
    }

    @Override
    public void withUserResponse(Object option) {
        AudioManager audioManager = Botify.get().getAudioManager();
        Guild guild = getContext().getGuild();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(guild, getSpotifyService());
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();

        List<Playable> playables = playableFactory.createPlayables(!argumentSet("preview"), option);
        queue.set(playables);

        audioManager.startPlayback(guild, getContext().getVoiceChannel());
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
