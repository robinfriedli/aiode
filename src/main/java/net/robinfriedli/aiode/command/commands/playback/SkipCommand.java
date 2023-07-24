package net.robinfriedli.aiode.command.commands.playback;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;

public class SkipCommand extends AbstractCommand {

    public SkipCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioManager audioManager = Aiode.get().getAudioManager();
        Guild guild = getContext().getGuild();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();
        AudioChannel channel = getContext().getAudioChannel();

        if (!queue.hasNext()) {
            throw new InvalidCommandException("No next item in queue");
        }

        if (getCommandInput().isBlank()) {
            queue.iterate();
        } else {
            int offset;
            try {
                offset = Integer.parseInt(getCommandInput());
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(getCommandInput() + " is not an integer");
            }

            if (offset < 1) {
                throw new InvalidCommandException("Expected a number greater than 0");
            }

            int newIndex;
            int queueSize = queue.getSize();
            boolean overflow = queue.getPosition() + offset >= queueSize;
            if (!playback.isRepeatAll() && overflow) {
                newIndex = queueSize - 1;
            } else if (overflow) {
                // if the current index is 30 with 50 tracks in the queue and the user wants to skip 24, the result should be 4
                // if the user wants to skip 20, the result be 0
                int provisional = queue.getPosition() + offset;
                int page = provisional / queueSize;
                newIndex = provisional - (page * queueSize);
            } else {
                newIndex = queue.getPosition() + offset;
            }

            queue.setPosition(newIndex);
        }
        audioManager.startPlayback(guild, channel);
    }

    @Override
    public void onSuccess() {
    }
}
