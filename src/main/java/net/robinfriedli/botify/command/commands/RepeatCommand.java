package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class RepeatCommand extends AbstractCommand {

    public RepeatCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
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

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("one").excludesArguments("all")
            .setDescription("Toggle repeat for the current track.");
        argumentContribution.map("all").excludesArguments("one")
            .setDescription("Toggle repeat for all tracks in the queue. This is the default option.");
        return argumentContribution;
    }

}
