package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RewindCommand extends AbstractCommand {

    public RewindCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        AudioManager audioManager = Botify.get().getAudioManager();
        Guild guild = getContext().getGuild();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();
        VoiceChannel channel = getContext().getVoiceChannel();

        if (!queue.hasPrevious()) {
            throw new InvalidCommandException("No previous item in queue");
        }

        int queueSize = queue.getTracks().size();
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
