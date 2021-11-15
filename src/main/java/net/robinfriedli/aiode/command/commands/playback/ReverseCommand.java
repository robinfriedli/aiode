package net.robinfriedli.aiode.command.commands.playback;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.util.Util;

public class ReverseCommand extends AbstractCommand {

    public ReverseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        AudioTrack playingTrack = playback.getAudioPlayer().getPlayingTrack();

        if (playingTrack == null) {
            throw new InvalidCommandException("No track is being played at the moment");
        }

        long toReverseMs;
        try {
            if (argumentSet("minutes")) {
                toReverseMs = Integer.parseInt(getCommandInput()) * 60000;
            } else {
                toReverseMs = Integer.parseInt(getCommandInput()) * 1000;
            }
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("'" + getCommandInput() + "' is not convertible to type integer. " +
                "Please enter a valid number.");
        }

        if (toReverseMs <= 0) {
            throw new InvalidCommandException("Expected 1 or greater");
        }

        long newPosition = playback.getCurrentPositionMs() - toReverseMs;
        if (newPosition < 0) {
            throw new InvalidCommandException("New position less than 0");
        }
        playback.setPosition(newPosition);
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        long currentPositionMs = playback.getCurrentPositionMs();
        sendSuccess("Set position to " + Util.normalizeMillis(currentPositionMs));
    }

}
