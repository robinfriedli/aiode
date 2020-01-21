package net.robinfriedli.botify.command.commands.playback;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class RepeatCommand extends AbstractCommand {

    public RepeatCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        if (argumentSet("one")) {
            playback.setRepeatOne(!playback.isRepeatOne());
        } else {
            playback.setRepeatAll(!playback.isRepeatAll());
        }
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        if (argumentSet("one")) {
            sendSuccess("Repeat one set to " + playback.isRepeatOne());
        } else {
            sendSuccess("Repeat all set to " + playback.isRepeatAll());
        }
    }

}
