package net.robinfriedli.aiode.command.commands.playback;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.AudioQueue;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.PlayableFactory;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.commands.AbstractQueueLoadingCommand;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;

public class PlayCommand extends AbstractQueueLoadingCommand {

    public PlayCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution,
            context,
            commandManager,
            commandString,
            requiresInput,
            identifier,
            description,
            category,
            true,
            context.getGuildContext().getReplaceableTrackLoadingExecutor());
    }

    @Override
    public void doRun() throws Exception {
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        VoiceChannel channel = context.getVoiceChannel();
        AudioManager audioManager = Aiode.get().getAudioManager();
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
        AudioManager audioManager = Aiode.get().getAudioManager();

        AudioPlayback playback = getContext().getGuildContext().getPlayback();
        AudioPlayer audioPlayer = playback.getAudioPlayer();
        if (audioPlayer.getPlayingTrack() != null) {
            audioPlayer.stopTrack();
        }
        playback.getAudioQueue().set(playables);
        audioManager.startPlayback(guild, channel);
    }

    @Override
    public void onSuccess() {
        // current track notification sent by AudioManager
    }

    @Override
    public void withUserResponse(Object option) {
        AudioManager audioManager = Aiode.get().getAudioManager();
        Guild guild = getContext().getGuild();
        PlayableFactory playableFactory = audioManager.createPlayableFactory(getSpotifyService(), getTrackLoadingExecutor());
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();

        List<Playable> playables = playableFactory.createPlayables(shouldRedirectSpotify(), option);
        AudioPlayer audioPlayer = playback.getAudioPlayer();
        if (audioPlayer.getPlayingTrack() != null) {
            audioPlayer.stopTrack();
        }
        queue.set(playables);

        audioManager.startPlayback(guild, getContext().getVoiceChannel());
    }

}
