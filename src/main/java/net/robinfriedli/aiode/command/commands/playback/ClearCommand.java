package net.robinfriedli.aiode.command.commands.playback;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public class ClearCommand extends AbstractCommand {

    public ClearCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        AudioQueue audioQueue = playback.getAudioQueue();
        audioQueue.clear(playback.isPlaying());
    }

    @Override
    public void onSuccess() {
        sendSuccess("Cleared queue");
    }
}
