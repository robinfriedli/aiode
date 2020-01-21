package net.robinfriedli.botify.command.commands.playback;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class ClearCommand extends AbstractCommand {

    public ClearCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        AudioQueue audioQueue = playback.getAudioQueue();
        audioQueue.clear(playback.isPlaying());
    }

    @Override
    public void onSuccess() {
        sendSuccess("Cleared queue");
    }
}
