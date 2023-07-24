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

public class RewindCommand extends AbstractCommand {

    public RewindCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        AudioManager audioManager = Aiode.get().getAudioManager();
        Guild guild = getContext().getGuild();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();
        AudioChannel channel = getContext().getAudioChannel();

        if (!queue.hasPrevious()) {
            throw new InvalidCommandException("No previous item in queue");
        }

        int queueSize = queue.getSize();
        if (getCommandInput().isBlank()) {
            queue.reverse();
        } else {
            int offset;
            try {
                offset = Integer.parseInt(getCommandInput());
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(getCommandInput() + " is not an integer");
            }

            if (offset < 1) {
                throw new InvalidCommandException("Expected a number grater than 0");
            }

            boolean deficient = queue.getPosition() - offset < 0;
            int newIndex;
            if (!playback.isRepeatAll() && deficient) {
                newIndex = 0;
            } else if (deficient) {
                // if the current index is 20 with 50 tracks in the queue and the user wants to rewind 24, the result should be 46
                int provisional = queue.getPosition() - offset;
                int page = provisional / queueSize * (-1) + 1;
                newIndex = page * queueSize + provisional;
            } else {
                newIndex = queue.getPosition() - offset;
            }

            queue.setPosition(newIndex);
        }
        audioManager.startPlayback(guild, channel);
    }

    @Override
    public void onSuccess() {
    }
}
