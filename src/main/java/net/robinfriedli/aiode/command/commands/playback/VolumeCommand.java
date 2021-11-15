package net.robinfriedli.aiode.command.commands.playback;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;

public class VolumeCommand extends AbstractCommand {

    public VolumeCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());

        int volume;
        try {
            volume = Integer.parseInt(getCommandInput());
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("'" + getCommandInput() + "' is not an integer");
        }

        if (!(volume > 0 && volume <= 200)) {
            throw new InvalidCommandException("Expected a value between 1 and 200");
        }

        playback.setVolume(volume);
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        sendSuccess("Volume set to: " + playback.getVolume());
    }
}
