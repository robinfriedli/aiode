package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;

public class RepeatCommand extends AbstractCommand {

    public RepeatCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false,
            "Toggles repeat for either the entire queue or the current track.");
    }

    @Override
    public void doRun() {
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        if (argumentSet("one")) {
            playback.setRepeatOne(!playback.isRepeatOne());
        } else {
            playback.setRepeatAll(!playback.isRepeatAll());
        }
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        if (argumentSet("one")) {
            sendMessage(getContext().getChannel(), "Repeat one set to " + playback.isRepeatOne());
        } else {
            sendMessage(getContext().getChannel(), "Repeat all set to " + playback.isRepeatAll());
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution();
        argumentContribution.map("one").excludesArguments("all")
            .setDescription("Toggle repeat for the current track.");
        argumentContribution.map("all").excludesArguments("one")
            .setDescription("Toggle repeat for all tracks in the queue. This is the default option.");
        return argumentContribution;
    }

}
