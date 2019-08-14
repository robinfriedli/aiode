package net.robinfriedli.botify.command.commands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.Util;

public class ReverseCommand extends AbstractCommand {

    public ReverseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
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
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        long currentPositionMs = playback.getCurrentPositionMs();
        sendSuccess("Set position to " + Util.normalizeMillis(currentPositionMs));
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("minutes")
            .setDescription("Reverse the given amount of minutes.");
        argumentContribution.map("seconds")
            .setDescription("Reverse the given amount of seconds. This is default.");
        return argumentContribution;
    }

}
