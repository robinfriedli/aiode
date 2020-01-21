package net.robinfriedli.botify.command.commands.playback;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class ShuffleCommand extends AbstractCommand {

    public ShuffleCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        playback.setShuffle(!playback.isShuffle());
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        StringBuilder messageBuilder = new StringBuilder();

        if (playback.isShuffle()) {
            messageBuilder.append("Enabled ");
        } else {
            messageBuilder.append("Disabled ");
        }
        messageBuilder.append("shuffle");

        if (!playback.getAudioQueue().isEmpty()) {
            if (playback.isShuffle()) {
                messageBuilder.append(" and shuffled queue order.");
            } else {
                messageBuilder.append(" and returned queue back to normal order.");
            }
        }

        AudioQueue queue = playback.getAudioQueue();
        if (queue.hasNext()) {
            messageBuilder.append(" New next track: ").append(queue.getNext().display());
        }

        sendSuccess(messageBuilder.toString());
    }
}
