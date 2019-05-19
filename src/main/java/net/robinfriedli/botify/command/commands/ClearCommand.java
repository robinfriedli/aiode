package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;

public class ClearCommand extends AbstractCommand {

    public ClearCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        AudioQueue audioQueue = playback.getAudioQueue();
        audioQueue.clear(playback.isPlaying());
    }

    @Override
    public void onSuccess() {
        sendSuccess(getContext().getChannel(), "Cleared queue");
    }
}
