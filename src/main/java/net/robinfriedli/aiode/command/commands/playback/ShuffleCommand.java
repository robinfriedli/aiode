package net.robinfriedli.aiode.command.commands.playback;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public class ShuffleCommand extends AbstractCommand {

    public ShuffleCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        playback.setShuffle(!playback.isShuffle());
    }

    @Override
    public void onSuccess() {
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(getContext().getGuild());
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
        Playable next = queue.peekNext();
        if (next != null) {
            messageBuilder.append(" New next track: ").append(next.display());
        }

        sendSuccess(messageBuilder.toString());
    }
}
