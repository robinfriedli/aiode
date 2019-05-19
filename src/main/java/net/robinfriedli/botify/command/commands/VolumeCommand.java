package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class VolumeCommand extends AbstractCommand {

    public VolumeCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());

        int volume;
        try {
            volume = Integer.parseInt(getCommandBody());
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("'" + getCommandBody() + "' is not an integer");
        }

        if (!(volume > 0 && volume <= 200)) {
            throw new InvalidCommandException("Expected a value between 1 and 200");
        }

        playback.setVolume(volume);
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        sendSuccess(getContext().getChannel(), "Volume set to: " + playback.getVolume());
    }
}
